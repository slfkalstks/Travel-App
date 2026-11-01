package com.travel.app.data

import com.travel.app.platform.LocalFiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TravelOfflineCacheTest {
    private val first = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-12", "2026-11-15", 1, 1500000)
    private val second = Trip("tokyo", "도쿄 여행", "도쿄", "일본", "2026-12-01", "2026-12-04", 2, 2400000)
    private val catalog = listOf(first, second)
    private val firstWorkspace = workspace(first, "osaka-ticket", "오사카 항공권")
    private val secondWorkspace = workspace(second, "tokyo-ticket", "도쿄 항공권")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun workspace(trip: Trip, documentId: String, name: String): Workspace = Workspace(
        trip = trip,
        tasks = listOf(TravelTask("${trip.id}-passport", "여권 확인", "packing", true)),
        reservations = listOf(Reservation("${trip.id}-flight", "flight", name, trip.startDate, "09:00", "REF-${trip.id}", "탑승 터미널 확인", "confirmed")),
        documents = listOf(DocumentRecord(documentId, name, "application/pdf", 1024, "a".repeat(64), "2026-11-01T09:00:00Z", "${trip.id}-flight")),
    )

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private class MemoryLocalFiles : LocalFiles {
        val contents = mutableMapOf<String, ByteArray>()
        var failWrites = false
        override suspend fun read(key: String): ByteArray? = contents[key]?.copyOf()
        override suspend fun write(key: String, bytes: ByteArray) {
            check(!failWrites) { "Simulated full local storage" }
            contents[key] = bytes.copyOf()
        }
        override suspend fun remove(key: String) { contents.remove(key) }
    }

    @Test fun editedWorkspacesAndDocumentsSurviveRestartAndRemainScopedWhenSelectingOffline() = runTest {
        val files = MemoryLocalFiles()
        val cache = TravelCache(files)
        val server = mutableMapOf(first.id to firstWorkspace, second.id to secondWorkspace)
        val afterFirst = firstWorkspace.copy(documents = firstWorkspace.documents.map { it.copy(name = "수정한 오사카 항공권") })
        val afterSecond = secondWorkspace.copy(documents = secondWorkspace.documents.map { it.copy(name = "수정한 도쿄 항공권", reservationId = "") })
        var saved = 0
        val online = TravelStore(this, client { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/api/demo/workspace" -> respond(Json.encodeToString(server.getValue(request.headers["X-Trip-Id"]!!)), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Patch -> {
                    val tripId = request.headers["X-Trip-Id"]
                    val updated = if (tripId == first.id) afterFirst else afterSecond
                    assertEquals("/api/demo/documents/${updated.documents.single().id}", request.url.encodedPath)
                    server[updated.trip.id] = updated
                    respond(Json.encodeToString(updated), HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }, "http://test", cache = cache)
        try {
            online.refresh(); advanceUntilIdle()
            online.updateDocument("osaka-ticket", "수정한 오사카 항공권", "osaka-flight") { saved++ }; advanceUntilIdle()
            online.selectTrip(second.id) {}; advanceUntilIdle()
            online.updateDocument("tokyo-ticket", "수정한 도쿄 항공권", "") { saved++ }; advanceUntilIdle()
            assertEquals(2, saved)
            assertEquals(afterSecond, online.state.value.workspace)
            assertFalse(online.state.value.offline)
            val snapshot = cache.read()
            assertEquals(second.id, snapshot.lastTripId)
            assertEquals(catalog, snapshot.trips)
            assertEquals(mapOf(first.id to afterFirst, second.id to afterSecond), snapshot.workspaces)
        } finally { online.close() }

        var attempts = 0
        var selected = 0
        val offline = TravelStore(this, client { attempts++; error("Simulated disconnected server") }, "http://test", cache = TravelCache(files))
        try {
            offline.refresh(); advanceUntilIdle()
            assertTrue(offline.state.value.offline)
            assertFalse(offline.state.value.loading)
            assertEquals(catalog, offline.state.value.trips)
            assertEquals(afterSecond, offline.state.value.workspace)
            assertNotNull(offline.state.value.error)
            offline.selectTrip(first.id) {
                assertEquals(afterFirst, offline.state.value.workspace)
                assertFalse(offline.state.value.loading)
                selected++
            }; advanceUntilIdle()
            assertEquals(1, selected)
            assertEquals(afterFirst.documents, offline.state.value.workspace!!.documents)
            assertEquals(first.id, offline.state.value.workspace!!.trip.id)
            assertEquals(2, attempts)
            assertEquals(first.id, cache.read().lastTripId)
        } finally { offline.close() }
        val restartedOffline = TravelStore(this, client { error("Still offline after another restart") }, "http://test", cache = TravelCache(files))
        try {
            restartedOffline.refresh(); advanceUntilIdle()
            assertEquals(afterFirst, restartedOffline.state.value.workspace)
            assertTrue(restartedOffline.state.value.offline)
            assertEquals(afterSecond, cache.read().workspaces[second.id])
        } finally { restartedOffline.close() }
    }

    @Test fun selectingAnUncachedTripOfflinePreservesCurrentWorkspaceAndDoesNotNavigate() = runTest {
        val files = MemoryLocalFiles()
        val cache = TravelCache(files)
        cache.save(catalog, firstWorkspace)
        var selected = 0
        val store = TravelStore(this, client { error("Offline") }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            store.selectTrip(second.id) { selected++ }; advanceUntilIdle()
            assertEquals(firstWorkspace, store.state.value.workspace)
            assertEquals(catalog, store.state.value.trips)
            assertEquals(0, selected)
            assertTrue(store.state.value.offline)
            assertFalse(store.state.value.loading)
            assertTrue(store.state.value.error.orEmpty().contains("아직 기기에 보관되지"))
            assertEquals(mapOf(first.id to firstWorkspace), cache.read().workspaces)
        } finally { store.close() }
    }

    @Test fun offlineWritesDoNotSendRequestsModifySnapshotsOrCloseEditors() = runTest {
        val files = MemoryLocalFiles()
        val cache = TravelCache(files)
        cache.save(catalog, firstWorkspace)
        val before = cache.read()
        var requests = 0
        var saved = 0
        val store = TravelStore(this, client { requests++; error("Offline") }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(1, requests)
            store.addTask(TaskInput("새 준비물", "packing"), "offline-task") { saved++ }
            store.updateDocument("osaka-ticket", "작성 중인 이름", "") { saved++ }
            store.deleteDocument("osaka-ticket") { saved++ }
            store.addTrip(TripInput("새 여행", "서울", "대한민국", "2026-11-05", "2026-11-06", 1, 500000), "offline-trip") { saved++ }
            store.deleteTrip(first.id) { saved++ }
            advanceUntilIdle()
            assertEquals(1, requests)
            assertEquals(0, saved)
            assertEquals(firstWorkspace, store.state.value.workspace)
            assertEquals(before, cache.read())
            assertFalse(store.state.value.saving)
            assertNotNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun serverSuccessRemainsCommittedWhenLocalSnapshotWritesFail() = runTest {
        val files = MemoryLocalFiles()
        val cache = TravelCache(files)
        cache.save(catalog, firstWorkspace)
        val before = cache.read()
        files.failWrites = true
        val after = firstWorkspace.copy(tasks = firstWorkspace.tasks + TravelTask("hotel", "숙소 확인", "booking", false))
        var saved = 0
        var disconnected = false
        val store = TravelStore(this, client { request ->
            if (disconnected) error("Network disconnected after server commit")
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> respond(Json.encodeToString(firstWorkspace), HttpStatusCode.OK, jsonHeaders)
                else -> respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test", cache = cache)
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(firstWorkspace, store.state.value.workspace)
            assertFalse(store.state.value.offline)
            assertTrue(store.state.value.error.orEmpty().contains("여행은 불러왔지만"))
            store.addTask(TaskInput("숙소 확인", "booking"), "saved-on-server") {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                saved++
            }; advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(after, store.state.value.workspace)
            assertEquals("준비물을 추가했어요", store.state.value.message)
            assertTrue(store.state.value.error.orEmpty().contains("서버에는 저장했지만 기기 보관에 실패"))
            assertFalse(store.state.value.offline)
            assertEquals(before, cache.read())
            disconnected = true
            store.refresh(); advanceUntilIdle()
            assertEquals(after, store.state.value.workspace)
            assertTrue(store.state.value.offline)
            assertEquals(before, cache.read())
            var selected = 0
            store.selectTrip(first.id) {
                assertEquals(after, store.state.value.workspace)
                selected++
            }; advanceUntilIdle()
            assertEquals(1, selected)
            assertEquals(after, store.state.value.workspace)
            files.failWrites = false
            store.refresh(); advanceUntilIdle()
            assertEquals(after, store.state.value.workspace)
            assertEquals(after, cache.read().workspaces[first.id])
            assertEquals(first.id, cache.read().lastTripId)
        } finally { store.close() }
    }

    @Test fun malformedSnapshotRecoversFromNetworkAndIsUsableAfterAnotherRestartOffline() = runTest {
        val files = MemoryLocalFiles()
        val unreadable = "{incomplete snapshot".encodeToByteArray()
        files.contents["travel-snapshot-v1.json"] = unreadable
        val cache = TravelCache(files)
        val online = TravelStore(this, client { request ->
            if (request.url.encodedPath == "/api/demo/trips") respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
            else respond(Json.encodeToString(firstWorkspace), HttpStatusCode.OK, jsonHeaders)
        }, "http://test", cache = cache)
        try {
            online.refresh(); advanceUntilIdle()
            assertEquals(firstWorkspace, online.state.value.workspace)
            assertFalse(online.state.value.offline)
            assertNull(online.state.value.error)
            assertEquals(firstWorkspace, cache.read().workspaces[first.id])
            assertContentEquals(unreadable, files.read("travel-snapshot-unreadable-backup.json"))
        } finally { online.close() }
        val restarted = TravelStore(this, client { error("Offline after recovery") }, "http://test", cache = TravelCache(files))
        try {
            restarted.refresh(); advanceUntilIdle()
            assertEquals(firstWorkspace, restarted.state.value.workspace)
            assertEquals(catalog, restarted.state.value.trips)
            assertTrue(restarted.state.value.offline)
            assertContentEquals(unreadable, files.read("travel-snapshot-unreadable-backup.json"))
        } finally { restarted.close() }
    }

    @Test fun workspaceFetchFailureUsesCachedMetadataEvenWhenTripCatalogRequestSucceeds() = runTest {
        val cache = TravelCache(MemoryLocalFiles())
        cache.save(catalog, firstWorkspace)
        cache.save(catalog, secondWorkspace)
        var selected = 0
        val store = TravelStore(this, client { request ->
            if (request.url.encodedPath == "/api/demo/trips") respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
            else respond("unavailable", HttpStatusCode.ServiceUnavailable)
        }, "http://test", cache = cache)
        try {
            store.selectTrip(first.id) { selected++ }; advanceUntilIdle()
            assertEquals(1, selected)
            assertEquals(firstWorkspace, store.state.value.workspace)
            assertEquals(firstWorkspace.documents, store.state.value.workspace!!.documents)
            assertTrue(store.state.value.offline)
            assertEquals(secondWorkspace, cache.read().workspaces[second.id])
        } finally { store.close() }
    }

    @Test fun cacheRemainsOptionalForOnlineRefreshAndMutation() = runTest {
        val after = firstWorkspace.copy(documents = emptyList())
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                request.method == HttpMethod.Get -> respond(Json.encodeToString(firstWorkspace), HttpStatusCode.OK, jsonHeaders)
                else -> respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(firstWorkspace, store.state.value.workspace)
            assertNull(store.state.value.error)
            store.deleteDocument("osaka-ticket") { saved++ }; advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(after, store.state.value.workspace)
            assertFalse(store.state.value.offline)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }
}
