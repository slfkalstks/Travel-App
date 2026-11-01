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
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationStoreTest {
    private val first = Trip("osaka", "오사카 여행", "오사카", "일본", "2026-11-02", "2026-11-06", 1, 1500000)
    private val selected = first.copy(id = "tokyo", title = "도쿄 여행", destination = "도쿄")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val responseJson = Json { encodeDefaults = true }

    // Same 20 fields as contracts/examples/reservation-stay-input.json; confirmed is
    // deliberately nondefault so the wire request exercises every schema property.
    private val fullRequest = Json.parseToJsonElement("""{
        "type":"stay","title":"도쿄 숙소","date":"2026-11-03","time":"15:00",
        "reference":"DEMO-STAY-001","detail":"직접 기록한 숙박 예시","status":"confirmed",
        "location":"도쿄","endLocation":"도쿄","provider":"사용자 입력 숙소",
        "endDate":"2026-11-06","endTime":"11:00","timeZone":"Asia/Tokyo",
        "endTimeZone":"Asia/Tokyo","cancellationDeadline":"2026-11-01T18:00",
        "cancellationTimeZone":"Asia/Tokyo","cancellationPolicy":"예시: 취소 가능 시각은 공급자에게 확인하세요.",
        "utcOffset":"+09:00","endUtcOffset":"+09:00","cancellationUtcOffset":"+09:00"
    }""").jsonObject
    private val input = Json.decodeFromJsonElement<ReservationInput>(fullRequest)
    private val record = Json.decodeFromJsonElement<Reservation>(JsonObject(fullRequest + mapOf(
        "id" to JsonPrimitive("res-demo-stay"), "source" to JsonPrimitive("manual"),
    )))
    private val otherRecord = record.copy(id = "keep-other-reservation", title = "남겨 둘 숙소")

    private fun TestScope.client(before: Workspace, handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                when {
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/demo/trips" ->
                        respond(responseJson.encodeToString(listOf(first, selected)), HttpStatusCode.OK, jsonHeaders)
                    request.method == HttpMethod.Get && request.url.encodedPath == "/api/demo/workspace" -> {
                        assertEquals(selected.id, request.headers["X-Trip-Id"])
                        respond(responseJson.encodeToString(before), HttpStatusCode.OK, jsonHeaders)
                    }
                    else -> {
                        assertEquals(selected.id, request.headers["X-Trip-Id"])
                        handler(request)
                    }
                }
            }
        }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun assertFullRequest(body: ByteArray, expected: JsonObject = fullRequest) {
        val actual = Json.parseToJsonElement(body.decodeToString()).jsonObject
        assertEquals(20, expected.size)
        assertEquals(expected.keys, actual.keys)
        assertEquals(expected, actual)
        assertFalse("id" in actual)
        assertFalse("source" in actual)
    }

    @Test fun creationSendsAllTwentyFieldsAndTripOperationHeadersWhileBlockingBusyDuplicates() = runTest {
        val gate = CompletableDeferred<Unit>()
        val before = Workspace(selected)
        val after = before.copy(reservations = listOf(record))
        var requests = 0
        var closed = 0
        val store = TravelStore(this, client(before) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/demo/reservations", request.url.encodedPath)
            assertEquals("stable-reservation-operation", request.headers["Idempotency-Key"])
            assertFullRequest(request.body.toByteArray())
            requests++
            gate.await()
            respond(responseJson.encodeToString(after), HttpStatusCode.Created, jsonHeaders)
        }, "http://test", initialTripId = selected.id)
        try {
            store.refresh(); advanceUntilIdle()
            store.addReservation(input, "stable-reservation-operation") {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                assertEquals("res-demo-stay", store.state.value.workspace!!.reservations.single().id)
                assertEquals("manual", store.state.value.workspace!!.reservations.single().source)
                closed++
            }
            assertTrue(store.state.value.saving)
            store.addReservation(input, "duplicate-before-dispatch") { fail("Busy creation closed the editor") }
            runCurrent()
            store.addReservation(input, "duplicate-while-pending") { fail("Pending creation was duplicated") }
            store.updateReservation(record.id, input) { fail("Busy save dispatched an edit") }
            store.deleteReservation(record.id) { fail("Busy save dispatched a deletion") }
            runCurrent()
            assertEquals(1, requests)
            assertEquals(0, closed)
            assertEquals(before, store.state.value.workspace)
            gate.complete(Unit); advanceUntilIdle()
            assertEquals(1, requests)
            assertEquals(1, closed)
            assertEquals(after, store.state.value.workspace)
            assertNull(store.state.value.error)
        } finally { gate.complete(Unit); store.close() }
    }

    @Test fun editUsesNamedReservationRouteAndCommitsReturnedIdSourceAndFieldsBeforeCallback() = runTest {
        val before = Workspace(selected, reservations = listOf(record, otherRecord))
        val editedInput = input.copy(title = "수정한 도쿄 숙소", reference = "EDITED-REFERENCE", detail = "변경한 메모")
        val expected = JsonObject(fullRequest + mapOf("title" to JsonPrimitive(editedInput.title), "reference" to JsonPrimitive(editedInput.reference), "detail" to JsonPrimitive(editedInput.detail)))
        val edited = record.copy(title = editedInput.title, reference = editedInput.reference, detail = editedInput.detail)
        val after = before.copy(reservations = listOf(edited, otherRecord))
        var requests = 0
        var closed = 0
        val store = TravelStore(this, client(before) { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/demo/reservations/res-demo-stay", request.url.encodedPath)
            assertNull(request.headers["Idempotency-Key"])
            assertFullRequest(request.body.toByteArray(), expected)
            requests++
            respond(responseJson.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
        }, "http://test", initialTripId = selected.id)
        try {
            store.refresh(); advanceUntilIdle()
            store.updateReservation(record.id, editedInput) {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            advanceUntilIdle()
            assertEquals(1, requests)
            assertEquals(1, closed)
            val saved = store.state.value.workspace!!.reservations.first()
            assertEquals(record.id, saved.id)
            assertEquals(record.source, saved.source)
            assertEquals(editedInput.title, saved.title)
            assertEquals(editedInput.reference, saved.reference)
            assertEquals(editedInput.detail, saved.detail)
            assertEquals(otherRecord, store.state.value.workspace!!.reservations.last())
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun deletionHasEmptyBodyAndSelectedTripScopeAndOnlyRemovesReturnedTarget() = runTest {
        val before = Workspace(selected, reservations = listOf(record, otherRecord))
        val after = before.copy(reservations = listOf(otherRecord))
        var deletions = 0
        var closed = 0
        val store = TravelStore(this, client(before) { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/demo/reservations/res-demo-stay", request.url.encodedPath)
            assertNull(request.headers["Idempotency-Key"])
            assertTrue(request.body.toByteArray().isEmpty())
            deletions++
            respond(responseJson.encodeToString(after), HttpStatusCode.OK, jsonHeaders)
        }, "http://test", initialTripId = selected.id)
        try {
            store.refresh(); advanceUntilIdle()
            assertEquals(selected.id, store.state.value.workspace!!.trip.id)
            store.deleteReservation(record.id) {
                assertEquals(after, store.state.value.workspace)
                assertFalse(store.state.value.saving)
                closed++
            }
            advanceUntilIdle()
            assertEquals(1, deletions)
            assertEquals(1, closed)
            assertEquals(listOf(otherRecord), store.state.value.workspace!!.reservations)
            assertEquals(listOf(first, selected), store.state.value.trips)
            assertNull(store.state.value.error)
        } finally { store.close() }
    }

    @Test fun failedDeletionKeepsSelectedTripRecordAndEditorOpenForRetry() = runTest {
        val before = Workspace(selected, reservations = listOf(record, otherRecord))
        var deletions = 0
        var closed = 0
        val store = TravelStore(this, client(before) { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/demo/reservations/res-demo-stay", request.url.encodedPath)
            assertTrue(request.body.toByteArray().isEmpty())
            deletions++
            respond("""{"code":"STORAGE_ERROR","message":"예약 기록을 삭제하지 못했어요."}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }, "http://test", initialTripId = selected.id)
        try {
            store.refresh(); advanceUntilIdle()
            store.deleteReservation(record.id) { closed++ }; advanceUntilIdle()
            assertEquals(1, deletions)
            assertEquals(0, closed)
            assertEquals(before, store.state.value.workspace)
            assertEquals(record.id, store.state.value.workspace!!.reservations.first().id)
            assertEquals("manual", store.state.value.workspace!!.reservations.first().source)
            assertEquals(listOf(first, selected), store.state.value.trips)
            assertFalse(store.state.value.saving)
            assertEquals("예약 기록을 삭제하지 못했어요.", store.state.value.error)
        } finally { store.close() }
    }
}
