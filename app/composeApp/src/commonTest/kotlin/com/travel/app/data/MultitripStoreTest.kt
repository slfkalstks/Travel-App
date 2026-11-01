package com.travel.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MultitripStoreTest {
    private val first = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-12", "2026-11-15", 1, 1500000)
    private val second = first.copy(id = "tokyo", title = "도쿄 여행", destination = "도쿄")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val tripInput = TripInput("도쿄 여행", "도쿄", "일본", "2026-11-12", "2026-11-15", 1, 1500000)
    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine { dispatcher = StandardTestDispatcher(testScheduler); addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test fun selectionUsesPreferredTripAndScopesMutationWhileBlockingTripSwitchDuringSave() = runTest {
        val gate = CompletableDeferred<Unit>()
        val before = Workspace(second)
        val after = before.copy(tasks = listOf(TravelTask("passport", "여권", "packing", false)))
        val workspaceIds = mutableListOf<String?>()
        var selected = 0
        var saved = 0
        val store = TravelStore(this, client { request ->
            when (request.url.encodedPath) {
                "/api/demo/trips" -> respond(Json.encodeToString(listOf(first, second)), HttpStatusCode.OK, jsonHeaders)
                "/api/demo/workspace" -> {
                    workspaceIds += request.headers["X-Trip-Id"]
                    respond(Json.encodeToString(if (request.headers["X-Trip-Id"] == second.id) before else Workspace(first)), HttpStatusCode.OK, jsonHeaders)
                }
                "/api/demo/tasks" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(second.id, request.headers["X-Trip-Id"])
                    gate.await()
                    respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("Unexpected request ${request.url}")
            }
        }, "http://test", initialTripId = second.id)
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(before, store.state.value.workspace)
            store.addTask(TaskInput("여권", "packing"), "task-key") {
                assertEquals(after, store.state.value.workspace)
                saved++
            }
            store.selectTrip(first.id) { selected++ }
            runCurrent()
            assertEquals(listOf<String?>(second.id), workspaceIds)
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(0, selected)
            store.selectTrip(first.id) { assertEquals(first, store.state.value.workspace?.trip); selected++ }
            advanceUntilIdle()
            assertEquals(listOf<String?>(second.id, first.id), workspaceIds)
            assertEquals(1, selected)
            assertEquals(setOf(first.id, second.id), store.state.value.trips.map { it.id }.toSet())
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun defaultSelectionSkipsArchivedTripsAndRejectsMismatchedWorkspaceWithoutReplacingData() = runTest {
        val archived = first.copy(archived = true)
        var wrongResponse = false
        var callbacks = 0
        val store = TravelStore(this, client { request ->
            if (request.url.encodedPath == "/api/demo/trips") respond(Json.encodeToString(listOf(archived, second)), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(second.id, request.headers["X-Trip-Id"])
                respond(Json.encodeToString(Workspace(if (wrongResponse) first else second)), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(second, store.state.value.workspace?.trip)
            wrongResponse = true
            store.selectTrip(second.id) { callbacks++ }; advanceUntilIdle()
            assertEquals(second, store.state.value.workspace?.trip)
            assertEquals(listOf(archived, second), store.state.value.trips)
            assertEquals(0, callbacks)
            assertNotNull(store.state.value.error)
            assertFalse(store.state.value.loading)
        } finally { store.close() }
    }

    @Test fun emptyCatalogSkipsWorkspaceAndAllowsCreatingFirstTripWithoutScopeHeader() = runTest {
        var requests = 0
        var saved = 0
        val store = TravelStore(this, client { request ->
            requests++
            assertEquals("/api/demo/trips", request.url.encodedPath)
            if (request.method == HttpMethod.Get) respond("[]", HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Post, request.method)
                assertNull(request.headers["X-Trip-Id"])
                assertEquals("first-trip-key", request.headers["Idempotency-Key"])
                assertEquals(Json.parseToJsonElement(Json.encodeToString(tripInput)), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                respond(Json.encodeToString(Workspace(second)), HttpStatusCode.Created, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            assertNull(store.state.value.workspace)
            assertTrue(store.state.value.trips.isEmpty())
            assertFalse(store.state.value.loading)
            store.addTask(TaskInput("여권", "packing"), "no-trip") { fail("Tripless mutation succeeded") }
            advanceUntilIdle()
            assertEquals(1, requests)
            store.addTrip(tripInput, "first-trip-key") {
                assertEquals(second, store.state.value.workspace?.trip)
                assertEquals(listOf(second), store.state.value.trips)
                assertFalse(store.state.value.saving)
                saved++
            }
            advanceUntilIdle()
            assertEquals(2, requests)
            assertEquals(1, saved)
        } finally { store.close() }
    }

    @Test fun failedTripCreationPreservesEmptyCatalogAndKeepsEditorOpen() = runTest {
        var saved = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond("[]", HttpStatusCode.OK, jsonHeaders)
            else respond("""{"message":"여행 저장에 실패했어요"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.addTrip(tripInput, "retry-key") { saved++ }; advanceUntilIdle()
            assertNull(store.state.value.workspace)
            assertTrue(store.state.value.trips.isEmpty())
            assertEquals(0, saved)
            assertFalse(store.state.value.saving)
            assertEquals("여행 저장에 실패했어요", store.state.value.error)
        } finally { store.close() }
    }

    @Test fun tripEditAndArchiveUseUnscopedPatchAndReplaceCatalogEntry() = runTest {
        val edited = first.copy(title = "변경한 여행")
        val archived = edited.copy(archived = true)
        var saved = 0
        val store = TravelStore(this, client { request ->
            when (request.url.encodedPath) {
                "/api/demo/trips" -> respond(Json.encodeToString(listOf(first, second)), HttpStatusCode.OK, jsonHeaders)
                "/api/demo/workspace" -> respond(Json.encodeToString(Workspace(first)), HttpStatusCode.OK, jsonHeaders)
                else -> {
                    assertEquals(HttpMethod.Patch, request.method)
                    assertNull(request.headers["X-Trip-Id"])
                    val archive = request.url.encodedPath.endsWith("/archive")
                    assertEquals(if (archive) "/api/demo/trips/osaka/archive" else "/api/demo/trips/osaka", request.url.encodedPath)
                    val expected = if (archive) Json.encodeToString(ArchiveChange(true)) else Json.encodeToString(tripInput.copy(title = edited.title))
                    assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                    respond(Json.encodeToString(Workspace(if (archive) archived else edited)), HttpStatusCode.OK, jsonHeaders)
                }
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateTrip(first.id, tripInput.copy(title = edited.title)) { assertEquals(edited, store.state.value.workspace?.trip); saved++ }
            advanceUntilIdle()
            store.archiveTrip(first.id, true) { assertEquals(archived, store.state.value.workspace?.trip); saved++ }
            advanceUntilIdle()
            assertEquals(2, saved)
            assertEquals(2, store.state.value.trips.size)
            assertEquals(archived, store.state.value.trips.single { it.id == first.id })
            assertEquals(second, store.state.value.trips.single { it.id == second.id })
        } finally { store.close() }
    }

    @Test fun editingAndArchivingAnotherTripPreservesSelectionAndCatalogOrder() = runTest {
        val selected = Workspace(first, tasks = listOf(TravelTask("passport", "여권", "packing", true)))
        val edited = second.copy(title = "수정한 도쿄 여행")
        val archived = edited.copy(archived = true)
        var saved = 0
        val store = TravelStore(this, client { request ->
            when (request.url.encodedPath) {
                "/api/demo/trips" -> respond(Json.encodeToString(listOf(first, second)), HttpStatusCode.OK, jsonHeaders)
                "/api/demo/workspace" -> respond(Json.encodeToString(selected), HttpStatusCode.OK, jsonHeaders)
                "/api/demo/trips/tokyo", "/api/demo/trips/tokyo/archive" -> {
                    assertEquals(HttpMethod.Patch, request.method)
                    assertNull(request.headers["X-Trip-Id"])
                    val trip = if(request.url.encodedPath.endsWith("/archive")) archived else edited
                    respond(Json.encodeToString(Workspace(trip)), HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("Unexpected request ${request.url}")
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateTrip(second.id, tripInput.copy(title = edited.title)) {
                assertEquals(selected, store.state.value.workspace)
                assertEquals(listOf(first, edited), store.state.value.trips)
                saved++
            }
            advanceUntilIdle()
            assertEquals(1, saved)
            store.archiveTrip(second.id, true) {
                assertEquals(selected, store.state.value.workspace)
                assertEquals(listOf(first, archived), store.state.value.trips)
                saved++
            }
            advanceUntilIdle()
            assertEquals(2, saved)
            assertEquals(selected, store.state.value.workspace)
            assertEquals(listOf(first, archived), store.state.value.trips)
            assertFalse(store.state.value.saving)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun deletingSelectedTripLoadsAnotherTripAndDeletingLastTripLeavesCleanEmptyState() = runTest {
        var catalog = listOf(first, second)
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.method == HttpMethod.Delete -> {
                    assertNull(request.headers["X-Trip-Id"])
                    assertTrue(request.body.toByteArray().isEmpty())
                    catalog = catalog.filterNot { it.id == request.url.encodedPath.substringAfterLast('/') }
                    respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(catalog), HttpStatusCode.OK, jsonHeaders)
                else -> respond(Json.encodeToString(Workspace(catalog.single { it.id == request.headers["X-Trip-Id"] })), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.deleteTrip(first.id) { assertFalse(store.state.value.saving); saved++ }; advanceUntilIdle()
            assertEquals(second, store.state.value.workspace?.trip)
            assertEquals(listOf(second), store.state.value.trips)
            store.deleteTrip(second.id) { saved++ }; advanceUntilIdle()
            assertEquals(2, saved)
            assertNull(store.state.value.workspace)
            assertTrue(store.state.value.trips.isEmpty())
            assertFalse(store.state.value.loading)
            assertFalse(store.state.value.saving)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun mismatchedScopedMutationResponseCannotReplaceSelectedTrip() = runTest {
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.method == HttpMethod.Post -> {
                    assertEquals(first.id, request.headers["X-Trip-Id"])
                    respond(Json.encodeToString(Workspace(second)), HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(listOf(first, second)), HttpStatusCode.OK, jsonHeaders)
                else -> respond(Json.encodeToString(Workspace(first)), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.addTask(TaskInput("여권", "packing"), "task-key") { saved++ }; advanceUntilIdle()
            assertEquals(Workspace(first), store.state.value.workspace)
            assertEquals(listOf(first, second), store.state.value.trips)
            assertEquals(0, saved)
            assertFalse(store.state.value.saving)
            assertNotNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun failedTripDeletionPreservesCatalogAndSelectedWorkspace() = runTest {
        var saved = 0
        val store = TravelStore(this, client { request ->
            when {
                request.method == HttpMethod.Delete -> respond("""{"message":"여행을 삭제하지 못했어요"}""", HttpStatusCode.InternalServerError, jsonHeaders)
                request.url.encodedPath == "/api/demo/trips" -> respond(Json.encodeToString(listOf(first, second)), HttpStatusCode.OK, jsonHeaders)
                else -> respond(Json.encodeToString(Workspace(first)), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.deleteTrip(first.id) { saved++ }; advanceUntilIdle()
            assertEquals(Workspace(first), store.state.value.workspace)
            assertEquals(listOf(first, second), store.state.value.trips)
            assertEquals(0, saved)
            assertFalse(store.state.value.saving)
            assertEquals("여행을 삭제하지 못했어요", store.state.value.error)
        } finally { store.close() }
    }
}
