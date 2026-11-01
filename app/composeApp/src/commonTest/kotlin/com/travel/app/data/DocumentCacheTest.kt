package com.travel.app.data

import com.travel.app.platform.LocalFiles
import com.travel.app.platform.MAX_DOCUMENT_BYTES
import com.travel.app.platform.documentSha256
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentCacheTest {
    private val trip = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-02", "2026-11-06", 1, 1500000)
    private val otherTrip = trip.copy(id = "tokyo", title = "도쿄 여행")
    private val pdf = "%PDF-1.7\nDocument cache test\n%%EOF".encodeToByteArray()
    private val document = record(pdf)

    private fun record(bytes: ByteArray, id: String = "document-1") = DocumentRecord(
        id = id,
        name = "예약 확인서",
        mediaType = "application/pdf",
        sizeBytes = bytes.size.toLong(),
        sha256 = documentSha256(bytes),
        createdAt = "2026-11-01T00:00:00Z",
    )

    /** remove models the platform contract: stored bytes and its native-preview copy are purged together. */
    private class MemoryLocalFiles : LocalFiles {
        val contents = linkedMapOf<String, ByteArray>()
        val previews = mutableSetOf<String>()
        val removed = mutableListOf<String>()
        var rejectWrite: (String) -> Boolean = { false }
        override suspend fun read(key: String): ByteArray? = contents[key]?.copyOf()
        override suspend fun write(key: String, bytes: ByteArray) {
            check(!rejectWrite(key)) { "로컬 저장 실패" }
            contents[key] = bytes.copyOf()
        }
        override suspend fun remove(key: String) {
            removed += key
            contents.remove(key)
            previews.remove(key)
        }
        fun contentKeys(): Set<String> = contents.keys.filterNot { it == "document-index-v1.json" }.toSet()
        fun indexedTrips(): Set<String> = contents["document-index-v1.json"]?.let { bytes ->
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject.getValue("entries").jsonArray
                .map { it.jsonObject.getValue("tripId").jsonPrimitive.content }.toSet()
        } ?: emptySet()
    }

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }

    private fun responseHeaders(mediaType: String = document.mediaType, size: Long? = document.sizeBytes) =
        if (size == null) headersOf(HttpHeaders.ContentType, mediaType)
        else headersOf(HttpHeaders.ContentType to listOf(mediaType), HttpHeaders.ContentLength to listOf(size.toString()))

    // Hash verification intentionally runs on Dispatchers.Default in production. Await the
    // observable completion on a real dispatcher instead of assuming advanceUntilIdle joins it.
    private suspend fun DocumentCache.awaitIdle() {
        withContext(Dispatchers.Default) { withTimeout(10_000) { state.first { !it.busy } } }
    }

    @Test
    fun downloadUsesTripScopeAndOnlyMarksVerifiedContentReady() = runTest {
        val files = MemoryLocalFiles()
        var requests = 0
        val cache = DocumentCache(this, files, client { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/demo/documents/${document.id}/content", request.url.encodedPath)
            assertEquals(trip.id, request.headers["X-Trip-Id"])
            respond(pdf, HttpStatusCode.OK, responseHeaders())
        }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = true)
            assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[document.id])
            cache.download(trip.id, document)
            assertTrue(cache.state.value.busy)
            cache.awaitIdle()
            assertEquals(1, requests)
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[document.id])
            assertNull(cache.state.value.error)
            val key = files.contentKeys().single()
            assertContentEquals(pdf, files.contents[key])
            assertEquals(setOf(trip.id), files.indexedTrips())
        } finally { cache.close() }
    }

    @Test
    fun mismatchedMimeDeclaredSizeAndDigestNeverReachReadyOrNativeOpen() = runTest {
        val altered = pdf.copyOf().also { it[it.lastIndex] = '!'.code.toByte() }
        val cases = listOf(
            Triple(pdf, "image/png", document.sizeBytes),
            Triple(pdf, document.mediaType, document.sizeBytes + 1),
            Triple(altered, document.mediaType, document.sizeBytes),
        )
        for ((body, mime, size) in cases) {
            val files = MemoryLocalFiles()
            var opened = false
            val cache = DocumentCache(this, files, client { respond(body, HttpStatusCode.OK, responseHeaders(mime, size)) }, "http://test")
            try {
                cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = true)
                cache.open(trip.id, document, offline = false) { opened = true }
                cache.awaitIdle()
                assertFalse(opened)
                assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
                assertNotNull(cache.state.value.error)
                assertTrue(files.contentKeys().isEmpty())
            } finally { cache.close() }
        }
    }

    @Test
    fun oversizedBodyWithoutContentLengthIsRejectedBeforeItCanBeCached() = runTest {
        val files = MemoryLocalFiles()
        val body = ByteArray(MAX_DOCUMENT_BYTES + 1) { 7 }
        val cache = DocumentCache(this, files, client { respond(body, HttpStatusCode.OK, responseHeaders(size = null)) }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = true)
            cache.open(trip.id, document, offline = false) { fail("Oversized download reached the native opener") }
            cache.awaitIdle()
            assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
            assertNotNull(cache.state.value.error)
            assertTrue(files.contentKeys().isEmpty())
        } finally { cache.close() }
    }

    @Test
    fun downloadedFileSurvivesCacheRecreationAndOpensOfflineWithoutNetwork() = runTest {
        val files = MemoryLocalFiles()
        val first = DocumentCache(this, files, client { respond(pdf, HttpStatusCode.OK, responseHeaders()) }, "http://test")
        val firstKey: String
        try {
            first.reconcile(Workspace(trip, documents = listOf(document)), authoritative = true)
            first.download(trip.id, document)
            first.awaitIdle()
            firstKey = files.contentKeys().single()
        } finally { first.close() }
        var networkRequests = 0
        val recreated = DocumentCache(this, files, client {
            networkRequests++
            error("Network is unavailable")
        }, "http://test")
        try {
            recreated.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            assertEquals(DocumentCacheStatus.READY, recreated.state.value.statuses[document.id])
            var nativeKey: String? = null
            recreated.open(trip.id, document, offline = true) { nativeKey = it }
            recreated.awaitIdle()
            assertEquals(firstKey, nativeKey)
            assertContentEquals(pdf, files.read(checkNotNull(nativeKey)))
            assertEquals(0, networkRequests)
            assertNull(recreated.state.value.error)
        } finally { recreated.close() }
    }

    @Test
    fun offlineUncachedFileReportsAnErrorWithoutMakingARequest() = runTest {
        val files = MemoryLocalFiles()
        var requests = 0
        val cache = DocumentCache(this, files, client { requests++; error("Offline must never make a request") }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            cache.open(trip.id, document, offline = true) { fail("Missing file reached the native opener") }
            cache.awaitIdle()
            assertEquals(0, requests)
            assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
            assertTrue(cache.state.value.error.orEmpty().contains("인터넷"))
            assertTrue(files.contents.isEmpty())
        } finally { cache.close() }
    }

    @Test
    fun corruptedPersistentBytesAreNotReadyAndCannotOpenOffline() = runTest {
        val files = MemoryLocalFiles()
        val cache = DocumentCache(this, files, client { fail("Offline corruption check made a network request") }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            cache.rememberUpload(trip.id, document, pdf)
            val key = files.contentKeys().single()
            files.contents[key] = pdf.copyOf().also { it[0] = 0 }
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[document.id])
            cache.open(trip.id, document, offline = true) { fail("Corrupted bytes reached the native opener") }
            cache.awaitIdle()
            assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
        } finally { cache.close() }
    }

    @Test
    fun identicalDocumentIdsFromDifferentTripsKeepIndependentBytesAndOpenKeys() = runTest {
        val files = MemoryLocalFiles()
        val otherPdf = "%PDF-1.7\nAnother trip file\n%%EOF".encodeToByteArray()
        val otherDocument = record(otherPdf, document.id)
        val cache = DocumentCache(this, files, client { fail("Cached trip isolation made a network request") }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = true)
            cache.rememberUpload(trip.id, document, pdf)
            val firstKey = files.contentKeys().single()
            cache.reconcile(Workspace(otherTrip, documents = listOf(otherDocument)), authoritative = true)
            assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[otherDocument.id])
            cache.rememberUpload(otherTrip.id, otherDocument, otherPdf)
            val secondKey = (files.contentKeys() - firstKey).single()
            assertNotEquals(firstKey, secondKey)
            var opened: String? = null
            cache.open(trip.id, document, offline = true) { opened = it }
            cache.awaitIdle()
            assertEquals(firstKey, opened)
            assertContentEquals(pdf, files.read(checkNotNull(opened)))
            cache.open(otherTrip.id, otherDocument, offline = true) { opened = it }
            cache.awaitIdle()
            assertEquals(secondKey, opened)
            assertContentEquals(otherPdf, files.read(checkNotNull(opened)))
            assertEquals(setOf(trip.id, otherTrip.id), files.indexedTrips())
        } finally { cache.close() }
    }

    @Test
    fun authoritativeDocumentRemovalPurgesStoredAndPreviewBytesOnlyForThatTrip() = runTest {
        val files = MemoryLocalFiles()
        val cache = DocumentCache(this, files, client { fail("Reconciliation made a request") }, "http://test")
        try {
            cache.rememberUpload(trip.id, document, pdf)
            val firstKey = files.contentKeys().single()
            cache.rememberUpload(otherTrip.id, document, pdf)
            val secondKey = (files.contentKeys() - firstKey).single()
            files.previews += setOf(firstKey, secondKey)
            cache.reconcile(Workspace(trip), authoritative = false)
            assertTrue(firstKey in files.contents)
            assertTrue(firstKey in files.previews)
            cache.reconcile(Workspace(trip), authoritative = true)
            assertFalse(firstKey in files.contents)
            assertFalse(firstKey in files.previews)
            assertEquals(listOf(firstKey), files.removed)
            assertTrue(secondKey in files.contents)
            assertTrue(secondKey in files.previews)
            assertEquals(setOf(otherTrip.id), files.indexedTrips())
            assertTrue(cache.state.value.statuses.isEmpty())
        } finally { cache.close() }
    }

    @Test
    fun removingATripPurgesItsStoredAndPreviewBytesAndKeepsRetainedTrip() = runTest {
        val files = MemoryLocalFiles()
        val cache = DocumentCache(this, files, client { fail("Trip purge made a request") }, "http://test")
        try {
            cache.rememberUpload(trip.id, document, pdf)
            val removedKey = files.contentKeys().single()
            cache.rememberUpload(otherTrip.id, document, pdf)
            val retainedKey = (files.contentKeys() - removedKey).single()
            files.previews += setOf(removedKey, retainedKey)
            cache.retainTrips(setOf(otherTrip.id))
            assertFalse(removedKey in files.contents)
            assertFalse(removedKey in files.previews)
            assertEquals(listOf(removedKey), files.removed)
            assertContentEquals(pdf, files.contents[retainedKey])
            assertTrue(retainedKey in files.previews)
            assertEquals(setOf(otherTrip.id), files.indexedTrips())
        } finally { cache.close() }
    }

    @Test
    fun explicitRemovalClearsReadyStatusAndNativePreviewCopy() = runTest {
        val files = MemoryLocalFiles()
        val cache = DocumentCache(this, files, client { fail("Local removal made a request") }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            cache.rememberUpload(trip.id, document, pdf)
            val key = files.contentKeys().single()
            files.previews += key
            cache.remove(trip.id, document)
            assertTrue(files.contentKeys().isEmpty())
            assertTrue(files.previews.isEmpty())
            assertEquals(listOf(key), files.removed)
            assertFalse(document.id in cache.state.value.statuses)
            assertTrue(files.indexedTrips().isEmpty())
        } finally { cache.close() }
    }

    @Test
    fun contentOrIndexWriteFailureCannotProduceReadyEvenAfterReconciliation() = runTest {
        for (failIndex in listOf(false, true)) {
            val files = MemoryLocalFiles().apply {
                rejectWrite = { key -> (key == "document-index-v1.json") == failIndex }
            }
            val cache = DocumentCache(this, files, client { respond(pdf, HttpStatusCode.OK, responseHeaders()) }, "http://test")
            try {
                cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
                cache.open(trip.id, document, offline = false) { fail("Failed local write reached the native opener") }
                cache.awaitIdle()
                assertEquals(DocumentCacheStatus.ERROR, cache.state.value.statuses[document.id])
                assertNotNull(cache.state.value.error)
                assertTrue(files.contentKeys().isEmpty())
                cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
                assertEquals(DocumentCacheStatus.NOT_DOWNLOADED, cache.state.value.statuses[document.id])
            } finally { cache.close() }
        }
    }

    @Test
    fun busyDownloadBlocksRepeatedDownloadsAndNativeOpenCallbacks() = runTest {
        val files = MemoryLocalFiles()
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        var duplicatesOpened = 0
        val cache = DocumentCache(this, files, client {
            requests++
            gate.await()
            respond(pdf, HttpStatusCode.OK, responseHeaders())
        }, "http://test")
        try {
            cache.reconcile(Workspace(trip, documents = listOf(document)), authoritative = false)
            cache.download(trip.id, document)
            cache.download(trip.id, document)
            cache.open(trip.id, document, offline = false) { duplicatesOpened++ }
            runCurrent()
            assertTrue(cache.state.value.busy)
            assertEquals(1, requests)
            cache.download(trip.id, document)
            cache.open(trip.id, document, offline = false) { duplicatesOpened++ }
            runCurrent()
            assertEquals(1, requests)
            gate.complete(Unit)
            cache.awaitIdle()
            assertEquals(1, requests)
            assertEquals(0, duplicatesOpened)
            assertEquals(DocumentCacheStatus.READY, cache.state.value.statuses[document.id])
            assertNull(cache.state.value.error)
        } finally { gate.complete(Unit); cache.close() }
    }
}
