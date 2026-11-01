package com.travel.app.data

import com.travel.app.platform.LocalFiles
import com.travel.app.platform.documentSha256
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentCacheRecoveryTest {
    private val first = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-12", "2026-11-15", 1, 1500000)
    private val second = first.copy(id = "tokyo", title = "도쿄 여행", destination = "도쿄")
    private val catalog = listOf(first, second)
    private val firstBytes = "%PDF-1.7\nOsaka recovery file\n%%EOF".encodeToByteArray()
    private val secondBytes = "%PDF-1.7\nTokyo recovery file\n%%EOF".encodeToByteArray()
    private val firstDocument = record("ticket", firstBytes)
    private val secondDocument = record("ticket", secondBytes)
    private val missing = record("not-downloaded", "%PDF-1.7\nMissing file\n%%EOF".encodeToByteArray())
    private val firstWorkspace = Workspace(first, documents = listOf(firstDocument, missing))
    private val secondWorkspace = Workspace(second, documents = listOf(secondDocument))
    private val unreadable = "{interrupted document index".encodeToByteArray()
    private val indexKey = "document-index-v1.json"
    private val backupKey = "document-index-unreadable-backup.json"

    private fun record(id: String, bytes: ByteArray) = DocumentRecord(
        id, "예약 확인서", "application/pdf", bytes.size.toLong(), documentSha256(bytes), "2026-11-01T09:00:00Z",
    )

    private class MemoryLocalFiles : LocalFiles {
        val contents = linkedMapOf<String, ByteArray>()
        val previews = mutableSetOf<String>()
        var rejectWrite: (String) -> Boolean = { false }
        override suspend fun read(key: String): ByteArray? = contents[key]?.copyOf()
        override suspend fun write(key: String, bytes: ByteArray) {
            check(!rejectWrite(key)) { "Simulated local storage failure" }
            contents[key] = bytes.copyOf()
        }
        override suspend fun remove(key: String) { contents.remove(key); previews.remove(key) }
        fun contentKeys() = contents.keys.filter { it.startsWith("document-") && it.endsWith(".pdf") }.toSet()
        fun indexedEntries(): Set<Pair<String, String>> = Json.parseToJsonElement(contents.getValue("document-index-v1.json").decodeToString())
            .jsonObject.getValue("entries").jsonArray.map { entry ->
                val value = entry.jsonObject
                value.getValue("tripId").jsonPrimitive.content to value.getValue("key").jsonPrimitive.content
            }.toSet()
    }

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }
    }

    private suspend fun DocumentCache.awaitIdle() {
        withContext(Dispatchers.Default) { withTimeout(10_000) { state.first { !it.busy } } }
    }

    private data class Seed(val files: MemoryLocalFiles, val firstKey: String, val secondKey: String)

    // Capture keys from actual writes instead of reimplementing the production identity formula.
    private suspend fun TestScope.seed(): Seed {
        val files = MemoryLocalFiles()
        val initial = DocumentCache(this, files, client { fail("Seeding local files made a network request") }, "http://test")
        val firstKey: String
        val secondKey: String
        try {
            initial.rememberUpload(first.id, firstDocument, firstBytes)
            firstKey = files.contentKeys().single()
            initial.rememberUpload(second.id, secondDocument, secondBytes)
            secondKey = (files.contentKeys() - firstKey).single()
        } finally { initial.close() }
        val snapshots = TravelCache(files)
        snapshots.save(catalog, firstWorkspace)
        snapshots.save(catalog, secondWorkspace)
        files.contents[indexKey] = unreadable.copyOf()
        return Seed(files, firstKey, secondKey)
    }

    @Test fun damagedIndexRecoversAllTripEntriesButReadinessStillRequiresVerifiedBytes() = runTest {
        val (files, firstKey, secondKey) = seed()
        files.contents[secondKey] = secondBytes.copyOf().also { it[0] = 0 }
        var requests = 0
        val cache = DocumentCache(this, files, client { request ->
            requests++
            assertEquals(second.id, request.headers["X-Trip-Id"])
            assertEquals("/api/demo/documents/${secondDocument.id}/content", request.url.encodedPath)
            respond(secondBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/pdf"), HttpHeaders.ContentLength to listOf(secondBytes.size.toString())))
        }, "http://test")
        try {
            cache.reconcile(firstWorkspace, authoritative = true)
            assertContentEquals(unreadable, files.read(backupKey))
            assertEquals(setOf(first.id to firstKey, second.id to secondKey), files.indexedEntries())
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[firstDocument.id])
            assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[missing.id])
            cache.reconcile(secondWorkspace, authoritative = false)
            assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[secondDocument.id])
            cache.open(second.id, secondDocument, offline = true) { fail("Recovered index trusted corrupted bytes") }
            cache.awaitIdle()
            assertEquals(0, requests)
            assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[secondDocument.id])
            cache.download(second.id, secondDocument)
            cache.awaitIdle()
            assertEquals(1, requests)
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[secondDocument.id])
            assertContentEquals(secondBytes, files.read(secondKey))
            assertContentEquals(firstBytes, files.read(firstKey))
            assertContentEquals(unreadable, files.read(backupKey))
            assertEquals(setOf(first.id to firstKey, second.id to secondKey), files.indexedEntries())
            assertNull(cache.state.value.error)
        } finally { cache.close() }
    }

    @Test fun authoritativePurgeAfterIndexRecoveryRemovesOnlyDeletedTripDocumentAndPreview() = runTest {
        val (files, firstKey, secondKey) = seed()
        files.previews += setOf(firstKey, secondKey)
        val cache = DocumentCache(this, files, client { fail("Recovery and purge made a network request") }, "http://test")
        try {
            cache.reconcile(Workspace(first), authoritative = false)
            assertContentEquals(firstBytes, files.read(firstKey))
            assertContentEquals(secondBytes, files.read(secondKey))
            cache.reconcile(Workspace(first), authoritative = true)
            assertNull(files.read(firstKey))
            assertFalse(firstKey in files.previews)
            assertContentEquals(secondBytes, files.read(secondKey))
            assertTrue(secondKey in files.previews)
            assertEquals(setOf(second.id to secondKey), files.indexedEntries())
            assertContentEquals(unreadable, files.read(backupKey))
            assertTrue(cache.state.value.statuses.isEmpty())
        } finally { cache.close() }
    }

    @Test fun recoveryIndexWriteFailureKeepsOriginalBackupAndContentForSuccessfulRetry() = runTest {
        val (files, firstKey, secondKey) = seed()
        files.rejectWrite = { it == indexKey }
        val cache = DocumentCache(this, files, client { fail("Index recovery made a network request") }, "http://test")
        try {
            val failure = runCatching { cache.reconcile(firstWorkspace, authoritative = true) }.exceptionOrNull()
            assertNotNull(failure)
            assertContentEquals(unreadable, files.read(backupKey))
            assertContentEquals(unreadable, files.read(indexKey))
            assertContentEquals(firstBytes, files.read(firstKey))
            assertContentEquals(secondBytes, files.read(secondKey))
            files.rejectWrite = { false }
            cache.reconcile(firstWorkspace, authoritative = true)
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[firstDocument.id])
            assertEquals(setOf(first.id to firstKey, second.id to secondKey), files.indexedEntries())
            assertContentEquals(unreadable, files.read(backupKey))
        } finally { cache.close() }
    }

    @Test fun failedContentWriteAfterRecoveryKeepsBackupAndOtherTripFilesThenAllowsRedownload() = runTest {
        val (files, firstKey, secondKey) = seed()
        val bytes = "%PDF-1.7\nNew file after recovery\n%%EOF".encodeToByteArray()
        val document = record("new-document", bytes)
        files.rejectWrite = { key -> key.startsWith("document-") && key.endsWith(".pdf") }
        var requests = 0
        val cache = DocumentCache(this, files, client {
            requests++
            respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/pdf"), HttpHeaders.ContentLength to listOf(bytes.size.toString())))
        }, "http://test")
        try {
            cache.reconcile(firstWorkspace.copy(documents = firstWorkspace.documents + document), authoritative = false)
            cache.download(first.id, document)
            cache.awaitIdle()
            assertEquals(1, requests)
            assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
            assertEquals(setOf(firstKey, secondKey), files.contentKeys())
            assertContentEquals(firstBytes, files.read(firstKey))
            assertContentEquals(secondBytes, files.read(secondKey))
            assertContentEquals(unreadable, files.read(backupKey))
            files.rejectWrite = { false }
            cache.download(first.id, document)
            cache.awaitIdle()
            assertEquals(2, requests)
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[document.id])
            val newKey = (files.contentKeys() - setOf(firstKey, secondKey)).single()
            assertContentEquals(bytes, files.read(newKey))
            assertEquals(setOf(first.id to firstKey, second.id to secondKey, first.id to newKey), files.indexedEntries())
            assertContentEquals(unreadable, files.read(backupKey))
            assertNull(cache.state.value.error)
        } finally { cache.close() }
    }
}
