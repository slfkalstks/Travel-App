package com.travel.app.data

import com.travel.app.platform.LocalFiles
import com.travel.app.platform.PickedDocument
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentUploadStoreTest {
    private val first = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-12", "2026-11-15", 1, 1500000)
    private val second = Trip("tokyo", "도쿄 여행", "도쿄", "일본", "2026-12-01", "2026-12-04", 2, 2400000)
    private val catalog = listOf(first, second)
    private val original = Workspace(first, tasks = listOf(TravelTask("passport", "여권 확인", "packing", true)))
    private val other = Workspace(second, documents = listOf(DocumentRecord("other-ticket", "도쿄 확인서", "application/pdf", 19, "b".repeat(64), "2026-11-01T09:00:00Z")))
    private val picked = PickedDocument("provider file.png", "application/pdf", "%PDF-1.7\noriginal file bytes\n".encodeToByteArray())
    private val displayName = "항공권 확인서"
    private val record = DocumentRecord("uploaded-ticket", displayName, picked.mediaType, picked.bytes.size.toLong(), "a".repeat(64), "2026-11-01T10:00:00Z", "flight-osaka")
    private val uploaded = original.copy(documents = listOf(record))
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private class MemoryLocalFiles : LocalFiles {
        private val contents = mutableMapOf<String, ByteArray>()
        override suspend fun read(key: String): ByteArray? = contents[key]?.copyOf()
        override suspend fun write(key: String, bytes: ByteArray) { contents[key] = bytes.copyOf() }
        override suspend fun remove(key: String) { contents.remove(key) }
    }

    private suspend fun seededCache(): TravelCache = TravelCache(MemoryLocalFiles()).also {
        it.save(catalog, other)
        it.save(catalog, original)
    }

    private data class MultipartPart(val headers: String, val body: String)

    // Decode the actual transport body. The fixture's file bytes are ASCII, so equality below
    // detects a missing, duplicated, renamed, or modified file without platform-specific I/O.
    private suspend fun assertUploadBody(request: HttpRequestData, name: String, reservationId: String) {
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/demo/documents", request.url.encodedPath)
        assertEquals(first.id, request.headers["X-Trip-Id"])
        val contentType = assertNotNull(request.body.contentType)
        assertTrue(contentType.match(ContentType.MultiPart.FormData))
        val boundary = assertNotNull(contentType.parameter("boundary"))
        val wire = request.body.toByteArray().decodeToString()
        assertTrue(wire.startsWith("--$boundary\r\n"))
        assertTrue(wire.endsWith("--$boundary--\r\n"))
        val parts = wire.split("--$boundary").drop(1).dropLast(1).map { raw ->
            val part = raw.removePrefix("\r\n").removeSuffix("\r\n")
            assertTrue(part.contains("\r\n\r\n"))
            MultipartPart(part.substringBefore("\r\n\r\n"), part.substringAfter("\r\n\r\n"))
        }
        assertEquals(3, parts.size)
        fun field(fieldName: String) = parts.single { it.headers.contains("name=\"$fieldName\"") }
        assertEquals(name, field("name").body)
        assertEquals(reservationId, field("reservationId").body)
        val file = field("file")
        assertTrue(file.headers.contains("filename=\"document.pdf\""))
        assertFalse(file.headers.contains(picked.name))
        assertFalse(file.headers.contains(name))
        assertTrue(file.headers.lineSequence().any { it.trim().equals("Content-Type: application/pdf", ignoreCase = true) })
        assertContentEquals(picked.bytes, file.body.encodeToByteArray())
        assertEquals(1, parts.count { it.headers.contains("filename=") })
    }

    @Test fun multipartUploadBlocksDuplicateAndTripSwitchUntilServerAndCacheCommit() = runTest {
        val cache = seededCache()
        val before = cache.read()
        val gate = CompletableDeferred<Unit>()
        var uploads = 0
        var workspaces = 0
        var saved = 0
        var selected = 0
        val store = TravelStore(this, client { request ->
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> { workspaces++; respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders) }
                else -> {
                    uploads++
                    assertUploadBody(request, displayName, "flight-osaka")
                    assertEquals("one-picked-file-operation", request.headers["Idempotency-Key"])
                    gate.await()
                    respond(Json.encodeToString(uploaded), HttpStatusCode.Created, jsonHeaders)
                }
            }
        }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            store.uploadDocument(picked, displayName, "flight-osaka", "one-picked-file-operation") {
                assertEquals(uploaded, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                saved++
            }
            assertTrue(store.state.value.saving)
            store.uploadDocument(picked, displayName, "flight-osaka", "duplicate-before-dispatch") { saved++ }
            store.selectTrip(second.id) { selected++ }
            runCurrent()
            store.uploadDocument(picked, displayName, "flight-osaka", "duplicate-while-pending") { saved++ }
            runCurrent()
            assertEquals(1, uploads)
            assertEquals(1, workspaces)
            assertEquals(0, saved)
            assertEquals(0, selected)
            assertEquals(original, store.state.value.workspace)
            assertEquals(before, cache.read())
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(uploaded, cache.read().workspaces[first.id])
            assertEquals(other, cache.read().workspaces[second.id])
            assertNull(store.state.value.error)
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun failedUploadKeepsDraftCallbackOpenAndRetryReusesSameOperationKeyAndFile() = runTest {
        val cache = seededCache()
        val before = cache.read()
        val keys = mutableListOf<String?>()
        var saved = 0
        val unlinked = uploaded.copy(documents = listOf(record.copy(reservationId = "")))
        val store = TravelStore(this, client { request ->
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
                else -> {
                    assertUploadBody(request, displayName, "")
                    keys += request.headers["Idempotency-Key"]
                    if (keys.size == 1) respond("""{"code":"STORAGE_ERROR","message":"파일을 저장하지 못했어요."}""", HttpStatusCode.InternalServerError, jsonHeaders)
                    else respond(Json.encodeToString(unlinked), HttpStatusCode.Created, jsonHeaders)
                }
            }
        }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            store.uploadDocument(picked, displayName, "", "stable-upload-operation") { saved++ }; advanceUntilIdle()
            assertEquals(0, saved)
            assertEquals(original, store.state.value.workspace)
            assertEquals(before, cache.read())
            assertEquals("파일을 저장하지 못했어요.", store.state.value.error)
            assertFalse(store.state.value.saving)
            store.uploadDocument(picked, displayName, "", "stable-upload-operation") { saved++ }; advanceUntilIdle()
            assertEquals(listOf<String?>("stable-upload-operation", "stable-upload-operation"), keys)
            assertEquals(1, saved)
            assertEquals(unlinked, store.state.value.workspace)
            assertEquals(unlinked, cache.read().workspaces[first.id])
            assertEquals(other, cache.read().workspaces[second.id])
        } finally { store.close() }
    }

    @Test fun interruptedMalformedAndCrossTripUploadResponsesNeverCommitSnapshotsOrCloseDraft() = runTest {
        val cache = seededCache()
        val before = cache.read()
        var uploads = 0
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
                else -> when (++uploads) {
                    1 -> error("Transport interrupted before response")
                    2 -> respond("not a workspace", HttpStatusCode.OK, jsonHeaders)
                    else -> respond(Json.encodeToString(other), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            repeat(3) {
                store.uploadDocument(picked, displayName, "", "unconfirmed-operation") { saved++ }; advanceUntilIdle()
                assertEquals(0, saved)
                assertEquals(original, store.state.value.workspace)
                assertEquals(catalog, store.state.value.trips)
                assertEquals(before, cache.read())
                assertFalse(store.state.value.saving)
                assertNotNull(store.state.value.error)
            }
            assertEquals(3, uploads)
        } finally { store.close() }
    }

    @Test fun documentPatchAndDeleteSendScopedContractAndUpdateOnlySelectedTripSnapshot() = runTest {
        val cache = seededCache()
        val renamed = uploaded.copy(documents = listOf(record.copy(name = "이름 변경", reservationId = "")))
        var patches = 0
        var deletes = 0
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> respond(Json.encodeToString(uploaded), HttpStatusCode.OK, jsonHeaders)
                else -> {
                    assertEquals("/api/demo/documents/${record.id}", request.url.encodedPath)
                    assertEquals(first.id, request.headers["X-Trip-Id"])
                    assertNull(request.headers["Idempotency-Key"])
                    if (request.method == HttpMethod.Patch) {
                        patches++
                        assertEquals(Json.parseToJsonElement("""{"name":"이름 변경","reservationId":""}"""), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                        respond(Json.encodeToString(renamed), HttpStatusCode.OK, jsonHeaders)
                    } else {
                        assertEquals(HttpMethod.Delete, request.method)
                        assertTrue(request.body.toByteArray().isEmpty())
                        deletes++
                        respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
                    }
                }
            }
        }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            store.updateDocument(record.id, "이름 변경", "") { assertEquals(renamed, store.state.value.workspace); saved++ }; advanceUntilIdle()
            assertEquals(1, patches)
            assertEquals(renamed, cache.read().workspaces[first.id])
            assertEquals(other, cache.read().workspaces[second.id])
            store.deleteDocument(record.id) { assertEquals(original, store.state.value.workspace); saved++ }; advanceUntilIdle()
            assertEquals(1, deletes)
            assertEquals(2, saved)
            assertEquals(original, cache.read().workspaces[first.id])
            assertEquals(other, cache.read().workspaces[second.id])
            assertEquals(first.id, cache.read().lastTripId)
            assertFalse(store.state.value.saving)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }
}
