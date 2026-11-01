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
class TravelStoreTest {
    private val original = Workspace(
        trip = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-12", "2026-11-15", 1, 1500000),
        tasks = listOf(TravelTask("passport", "여권 확인", "packing", false))
    )
    private val input = ExpenseInput("카페", "food", 7500, "2026-11-13")
    private val updated get() = original.copy(expenses = listOf(Expense("coffee", input.title, input.category, input.amountKrw, input.date, input.method)))
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun TestScope.client(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test fun refreshBlocksMutationBeforeDispatchAndWhileRequestIsPending() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gets = 0
        var posts = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) {
                gets++
                if (gets == 2) gate.await()
            } else posts++
            respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
        }, "http://test")
        try {
            store.refresh()
            advanceUntilIdle()
            assertEquals(original, store.state.value.workspace)
            store.refresh()
            assertTrue(store.state.value.loading)
            store.addExpense(input, "must-not-start") { fail("Blocked mutation closed the editor") }
            runCurrent()
            store.addExpense(input, "also-blocked") { fail("Blocked mutation closed the editor") }
            runCurrent()
            assertEquals(0, posts)
            assertEquals(2, gets)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(store.state.value.loading)
            assertEquals(original, store.state.value.workspace)
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun savingBlocksRefreshAndDuplicateMutationUntilResponseIsCommitted() = runTest {
        val gate = CompletableDeferred<Unit>()
        var gets = 0
        var posts = 0
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) {
                gets++
                respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
            } else {
                posts++
                gate.await()
                respond(Json.encodeToString(updated), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.addExpense(input, "save-one") { closed++ }
            assertTrue(store.state.value.saving)
            store.refresh()
            store.addExpense(input, "save-duplicate") { closed++ }
            runCurrent()
            store.refresh()
            runCurrent()
            assertEquals(1, gets)
            assertEquals(1, posts)
            assertEquals(original, store.state.value.workspace)
            assertEquals(0, closed)
            gate.complete(Unit); advanceUntilIdle()
            assertFalse(store.state.value.saving)
            assertEquals(updated, store.state.value.workspace)
            assertEquals(1, closed)
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun failedPostRetainsEditorAndRetrySendsTheSameOperationKey() = runTest {
        val keys = mutableListOf<String?>()
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
            else {
                keys += request.headers["Idempotency-Key"]
                if (keys.size == 1) respond("""{"code":"STORAGE_ERROR","message":"잠시 후 다시 저장해 주세요."}""", HttpStatusCode.InternalServerError, jsonHeaders)
                else respond(Json.encodeToString(updated), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.addExpense(input, "stable-editor-operation") { closed++ }; advanceUntilIdle()
            assertEquals(0, closed)
            assertEquals(original, store.state.value.workspace)
            assertEquals("잠시 후 다시 저장해 주세요.", store.state.value.error)
            assertFalse(store.state.value.saving)
            store.addExpense(input, "stable-editor-operation") { closed++ }; advanceUntilIdle()
            assertEquals(listOf<String?>("stable-editor-operation", "stable-editor-operation"), keys)
            assertEquals(1, closed)
            assertEquals(updated, store.state.value.workspace)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun conflictMalformedResponseAndTransportFailureNeverWipeWorkspaceOrCloseEditor() = runTest {
        var attempts = 0
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
            else when (++attempts) {
                1 -> respond("""{"code":"IDEMPOTENCY_CONFLICT","message":"저장 키가 다른 요청에 사용되었어요."}""", HttpStatusCode.Conflict, jsonHeaders)
                2 -> respond("not valid JSON", HttpStatusCode.OK, jsonHeaders)
                else -> throw IllegalStateException("Simulated disconnected transport")
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            repeat(3) { attempt ->
                store.addExpense(input, "operation-$attempt") { closed++ }; advanceUntilIdle()
                assertEquals(original, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                assertNotNull(store.state.value.error)
                assertEquals(0, closed)
                if (attempt == 0) assertEquals("저장 키가 다른 요청에 사용되었어요.", store.state.value.error)
            }
        } finally { store.close() }
    }

    @Test fun refreshFailurePreservesLastLoadedWorkspace() = runTest {
        var requests = 0
        val store = TravelStore(this, client {
            if (++requests > 1) throw IllegalStateException("Simulated offline refresh")
            respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.refresh(); advanceUntilIdle()
            assertEquals(original, store.state.value.workspace)
            assertFalse(store.state.value.loading)
            assertNotNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun eventNotePatchCommitsReturnedWorkspaceBeforeNotifyingEditor() = runTest {
        val event = TravelEvent("walk", "2026-11-13", "10:00", "11:00", "오사카성", "place", "오사카성 공원", "기존 메모")
        val before = original.copy(events = listOf(event))
        val after = before.copy(events = listOf(event.copy(note = "서쪽 입구에서 만나기")))
        var patches = 0
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Patch, request.method)
                assertEquals("/api/demo/events/walk/note", request.url.encodedPath)
                assertEquals(Json.parseToJsonElement("""{"note":"서쪽 입구에서 만나기"}"""), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                patches++
                respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateEventNote(event.id, "서쪽 입구에서 만나기") {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            assertTrue(store.state.value.saving)
            advanceUntilIdle()
            assertEquals(1, patches)
            assertEquals(1, closed)
            assertEquals(after, store.state.value.workspace)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun taskCreationSendsOperationKeyAndBlocksDuplicateWhileSaving() = runTest {
        val gate = CompletableDeferred<Unit>()
        val input = TaskInput("숙소 예약 확인", "booking")
        val after = original.copy(tasks = original.tasks + TravelTask("hotel", input.title, input.category, false))
        var requests = 0
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(original), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/demo/tasks", request.url.encodedPath)
                assertEquals("new-task-operation", request.headers["Idempotency-Key"])
                assertEquals(Json.parseToJsonElement(Json.encodeToString(input)), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                requests++
                gate.await()
                respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.addTask(input, "new-task-operation") {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            store.addTask(input, "duplicate") { fail("Duplicate task request completed") }
            runCurrent()
            store.addTask(input, "another-duplicate") { fail("Pending task request was duplicated") }
            runCurrent()
            assertEquals(1, requests)
            assertEquals(original, store.state.value.workspace)
            assertEquals(0, closed)
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, closed)
            assertEquals(after, store.state.value.workspace)
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun editingEventSendsAllEditableFieldsAndCommitsBeforeCallback() = runTest {
        val event = TravelEvent("walk", "2026-11-13", "10:00", "11:00", "오사카성", "place", "오사카성 공원", "기존 메모", 1000, 34.687, 135.526)
        val before = original.copy(events = listOf(event))
        val input = EventEditInput("2026-11-14", "12:00", "13:30", "점심 식사", "food", "난바", "예약 확인")
        val after = before.copy(events = listOf(event.copy(date = input.date, startTime = input.startTime, endTime = input.endTime, title = input.title, category = input.category, location = input.location, note = input.note)))
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Patch, request.method)
                assertEquals("/api/demo/events/walk", request.url.encodedPath)
                assertEquals(Json.parseToJsonElement(Json.encodeToString(input)), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateEvent(event.id, input) {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            advanceUntilIdle()
            assertEquals(1, closed)
            assertEquals(after, store.state.value.workspace)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun editingTaskUsesDetailsEndpointAndRetainsReturnedCompletionState() = runTest {
        val task = original.tasks.single().copy(done = true)
        val before = original.copy(tasks = listOf(task))
        val input = TaskInput("여권 유효기간 확인", "booking")
        val after = before.copy(tasks = listOf(task.copy(title = input.title, category = input.category)))
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Patch, request.method)
                assertEquals("/api/demo/tasks/passport/details", request.url.encodedPath)
                assertEquals(Json.parseToJsonElement(Json.encodeToString(input)), Json.parseToJsonElement(request.body.toByteArray().decodeToString()))
                respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateTask(task.id, input) {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            advanceUntilIdle()
            assertEquals(1, closed)
            assertTrue(store.state.value.workspace!!.tasks.single().done)
        } finally { store.close() }
    }

    @Test fun eventAndTaskDeletionUseEmptyBodyAndAcceptRepeatedSuccessfulDeletion() = runTest {
        val event = TravelEvent("walk", "2026-11-13", "10:00", "11:00", "오사카성", "place", "오사카성 공원")
        val before = original.copy(events = listOf(event))
        for (deleteEvent in listOf(true, false)) {
            val after = if (deleteEvent) before.copy(events = emptyList()) else before.copy(tasks = emptyList())
            var deletions = 0
            var closed = 0
            val store = TravelStore(this, client { request ->
                if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
                else {
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals(if (deleteEvent) "/api/demo/events/walk" else "/api/demo/tasks/passport", request.url.encodedPath)
                    assertTrue(request.body.toByteArray().isEmpty())
                    deletions++
                    respond(Json.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
                }
            }, "http://test")
            try {
                store.refresh(); advanceUntilIdle()
                repeat(2) {
                    val onSaved = {
                        assertEquals(after, store.state.value.workspace)
                        assertFalse(store.state.value.saving)
                        closed++
                        Unit
                    }
                    if (deleteEvent) store.deleteEvent(event.id, onSaved) else store.deleteTask("passport", onSaved)
                    advanceUntilIdle()
                }
                assertEquals(2, deletions)
                assertEquals(2, closed)
                assertNull(store.state.value.error)
            } finally { store.close() }
        }
    }

    @Test fun failedEventAndTaskDeletionPreservesWorkspaceAndKeepsEditorOpen() = runTest {
        val before = original.copy(events = listOf(TravelEvent("walk", "2026-11-13", "10:00", "11:00", "오사카성", "place", "오사카성 공원")))
        for (deleteEvent in listOf(true, false)) {
            var closed = 0
            val store = TravelStore(this, client { request ->
                if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
                else {
                    assertEquals(HttpMethod.Delete, request.method)
                    respond("""{"code":"STORAGE_ERROR","message":"삭제하지 못했어요."}""", HttpStatusCode.InternalServerError, jsonHeaders)
                }
            }, "http://test")
            try {
                store.refresh(); advanceUntilIdle()
                if (deleteEvent) store.deleteEvent("walk") { closed++ } else store.deleteTask("passport") { closed++ }
                advanceUntilIdle()
                assertEquals(before, store.state.value.workspace)
                assertEquals(0, closed)
                assertFalse(store.state.value.saving)
                assertEquals("삭제하지 못했어요.", store.state.value.error)
            } finally { store.close() }
        }
    }

    @Test fun failedEventNotePatchPreservesExistingNoteAndKeepsEditorOpenForRetry() = runTest {
        val event = TravelEvent("walk", "2026-11-13", "10:00", "11:00", "오사카성", "place", "오사카성 공원", "지우면 안 되는 메모")
        val before = original.copy(events = listOf(event))
        var closed = 0
        val store = TravelStore(this, client { request ->
            if (request.method == HttpMethod.Get) respond(Json.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
            else {
                assertEquals(HttpMethod.Patch, request.method)
                respond("""{"code":"STORAGE_ERROR","message":"메모를 저장하지 못했어요."}""", HttpStatusCode.InternalServerError, jsonHeaders)
            }
        }, "http://test")
        try {
            store.refresh(); advanceUntilIdle()
            store.updateEventNote(event.id, "새로 입력한 메모") { closed++ }
            advanceUntilIdle()
            assertEquals(before, store.state.value.workspace)
            assertEquals(0, closed)
            assertFalse(store.state.value.saving)
            assertEquals("메모를 저장하지 못했어요.", store.state.value.error)
        } finally { store.close() }
    }
}
