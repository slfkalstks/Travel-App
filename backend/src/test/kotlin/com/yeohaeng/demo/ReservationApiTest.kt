package com.yeohaeng.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var repository: WorkspaceRepository
    private lateinit var trip: Workspace
    companion object {
        val dataPath = Files.createTempDirectory("yeohaeng-reservations-").resolve("workspace.json")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("travel.data-file") { dataPath.toString() }
        }
    }
    @BeforeEach fun freshTrip() {
        repository.trips().forEach { repository.deleteTrip(it.id) }
        trip = newTrip()
    }
    private fun newTrip() = WorkspaceService(repository).createTrip(TripRequest("여행", "도쿄", "일본", "2026-11-12", "2026-11-15", 1, 0))
    private fun input() = ReservationRequest("activity", "  직접 기록  ", "2026-11-03")
    private fun add(request: ReservationRequest = input(), key: String = UUID.randomUUID().toString(), tripId: String = trip.trip.id): Workspace =
        mapper.readValue(mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", tripId).header("Idempotency-Key", key)
            .contentType("application/json").content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk).andReturn().response.contentAsString)
    private fun invalid(request: ReservationRequest) {
        mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).contentType("application/json")
            .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest)
    }
    private fun restarted() = WorkspaceService(WorkspaceRepository(mapper, dataPath.toString()))

    @Test fun `minimal unknown time is retained empty and outside trip dates remain visible after reload`() {
        val body = """{"type":"activity","title":"시간 미정","date":"2026-10-01"}"""
        val saved = mapper.readValue<Workspace>(mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id)
            .contentType("application/json").content(body)).andExpect(status().isOk).andReturn().response.contentAsString)
        val row = saved.reservations.single()
        assertEquals("manual", row.source); assertEquals("planned", row.status)
        assertEquals("", row.time); assertEquals("", row.utcOffset); assertEquals("", row.timeZone)
        assertEquals("", row.endTime); assertEquals("", row.endDate); assertEquals("", row.cancellationDeadline)
        assertEquals(saved, restarted().read(trip.trip.id))
    }

    @Test fun `full edit preserves server identity source and list order while all editable data persist`() {
        val request = ReservationRequest("stay", "  호텔  ", "2026-11-11", "15:00", "  REF-1  ", "상세\n  원문", "confirmed",
            "  도쿄 호텔  ", "  로비  ", "  숙박 판매처  ", "2026-11-16", "11:00", "Asia/Tokyo", "Asia/Tokyo",
            "2026-11-10T18:00", "Asia/Seoul", "정책\n  원문")
        val created = add(request).reservations.single()
        val second = add(input()).reservations.last()
        val edited = request.copy(title = "수정한 호텔", reference = "REF-2", detail = "", status = "cancelled", cancellationPolicy = "정책 변경")
        repeat(2) {
            mvc.perform(patch("/api/demo/reservations/${created.id}").header("X-Trip-Id", trip.trip.id).contentType("application/json")
                .content(mapper.writeValueAsString(edited))).andExpect(status().isOk)
        }
        val saved = restarted().read(trip.trip.id)
        assertEquals(listOf(created.id, second.id), saved.reservations.map { it.id })
        assertEquals(created.copy(title = edited.title, reference = edited.reference, detail = "", status = "cancelled", cancellationPolicy = "정책 변경"), saved.reservations.first())
        assertEquals("manual", saved.reservations.first().source)
        assertEquals("도쿄 호텔", created.location); assertEquals("숙박 판매처", created.provider)
        assertEquals("상세\n  원문", created.detail); assertEquals("정책\n  원문", created.cancellationPolicy)
        assertEquals("+09:00", created.utcOffset); assertEquals("+09:00", created.endUtcOffset); assertEquals("+09:00", created.cancellationUtcOffset)
    }

    @Test fun `creation receipts are per trip survive restart and reject different payloads`() {
        val key = "reservation-shared-key"; val request = input()
        val first = add(request, key)
        assertEquals(first, add(request, key))
        val other = newTrip()
        assertEquals(1, add(request, key, other.trip.id).reservations.size)
        val loaded = restarted()
        assertEquals(first, loaded.addReservation(request, key, trip.trip.id))
        mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", key)
            .contentType("application/json").content(mapper.writeValueAsString(request.copy(title = "다른 내용"))))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
        mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", "invalid key")
            .contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest)
        assertEquals(first, repository.read(trip.trip.id))
    }

    @Test fun `deletion only removes selected trip record and delayed POST cannot resurrect it`() {
        val key = "delete-retry"; val request = input(); val created = add(request, key).reservations.single()
        val other = newTrip(); val otherBefore = add(request, key, other.trip.id)
        mvc.perform(patch("/api/demo/reservations/${created.id}").header("X-Trip-Id", other.trip.id).contentType("application/json")
            .content(mapper.writeValueAsString(request))).andExpect(status().isNotFound)
        repeat(2) { mvc.perform(delete("/api/demo/reservations/${created.id}").header("X-Trip-Id", trip.trip.id))
            .andExpect(status().isOk).andExpect(jsonPath("$.reservations.length()").value(0)) }
        mvc.perform(delete("/api/demo/reservations/absent").header("X-Trip-Id", trip.trip.id)).andExpect(status().isOk)
        val loaded = restarted()
        assertTrue(loaded.addReservation(request, key, trip.trip.id).reservations.isEmpty())
        assertEquals(otherBefore, loaded.read(other.trip.id))
        assertEquals(trip.trip, loaded.read(trip.trip.id).trip)
    }

    @Test fun `missing trip or reservation returns 404 and omitted trip header uses existing default rule`() {
        mvc.perform(post("/api/demo/reservations").contentType("application/json").content(mapper.writeValueAsString(input())))
            .andExpect(status().isOk).andExpect(jsonPath("$.trip.id").value(trip.trip.id))
        listOf("absent", "").forEach { id ->
            mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", id).contentType("application/json")
                .content(mapper.writeValueAsString(input()))).andExpect(status().isNotFound)
            mvc.perform(delete("/api/demo/reservations/absent").header("X-Trip-Id", id)).andExpect(status().isNotFound)
        }
        mvc.perform(patch("/api/demo/reservations/absent").header("X-Trip-Id", trip.trip.id).contentType("application/json")
            .content(mapper.writeValueAsString(input()))).andExpect(status().isNotFound)
    }

    @Test fun `normal times infer canonical offsets while midnight is a known time`() {
        val row = add(input().copy(time = "00:00", timeZone = "UTC", utcOffset = "+00:00",
            endDate = "2026-11-03", endTime = "01:00", endTimeZone = "UTC",
            cancellationDeadline = "2026-11-01T00:00", cancellationTimeZone = "Asia/Seoul")).reservations.single()
        assertEquals("00:00", row.time); assertEquals("Z", row.utcOffset); assertEquals("Z", row.endUtcOffset)
        assertEquals("+09:00", row.cancellationUtcOffset)
        invalid(input().copy(time = "12:00", timeZone = "Asia/Tokyo", utcOffset = "+08:00"))
        invalid(input().copy(time = "12:00", timeZone = "+09:00"))
        invalid(input().copy(time = "12:00", timeZone = "No/Such_Zone"))
    }

    @Test fun `DST gaps are rejected for start end and cancellation without mutating data or receipts`() {
        val gapDate = "2026-03-08"
        val requests = listOf(input().copy(date = gapDate, time = "02:30", timeZone = "America/New_York"),
            input().copy(date = "2026-03-07", endDate = gapDate, endTime = "02:30", endTimeZone = "America/New_York"),
            input().copy(cancellationDeadline = "${gapDate}T02:30", cancellationTimeZone = "America/New_York"))
        val before = Files.readAllBytes(dataPath)
        requests.forEach { invalid(it); invalid(it.copy(utcOffset = if (it.time.isEmpty()) "" else "-05:00")) }
        assertArrayEquals(before, Files.readAllBytes(dataPath))
    }

    @Test fun `DST overlaps require a matching explicit offset independently for all three moments`() {
        val start = input().copy(date = "2026-11-01", time = "01:30", timeZone = "America/New_York")
        val end = input().copy(date = "2026-10-31", endDate = "2026-11-01", endTime = "01:30", endTimeZone = "America/New_York")
        val cancel = input().copy(cancellationDeadline = "2026-11-01T01:30", cancellationTimeZone = "America/New_York")
        listOf(start, end, cancel).forEach { invalid(it) }
        invalid(start.copy(utcOffset = "-06:00")); invalid(end.copy(endUtcOffset = "-06:00")); invalid(cancel.copy(cancellationUtcOffset = "-06:00"))
        assertEquals("-04:00", add(start.copy(utcOffset = "-04:00")).reservations.last().utcOffset)
        assertEquals("-05:00", add(end.copy(endUtcOffset = "-05:00")).reservations.last().endUtcOffset)
        assertEquals("-04:00", add(cancel.copy(cancellationUtcOffset = "-04:00")).reservations.last().cancellationUtcOffset)
        val crossing = start.copy(utcOffset = "-04:00", endDate = "2026-11-01", endTime = "01:15", endTimeZone = "America/New_York", endUtcOffset = "-05:00")
        add(crossing)
        invalid(crossing.copy(utcOffset = "-05:00", endUtcOffset = "-04:00"))
    }

    @Test fun `date line arrival may be earlier local date but end instant must be strictly later`() {
        val flight = input().copy(type = "flight", date = "2026-11-03", time = "00:30", timeZone = "Asia/Tokyo",
            endDate = "2026-11-02", endTime = "17:30", endTimeZone = "America/Los_Angeles")
        val created = add(flight).reservations.single()
        assertEquals("2026-11-02", created.endDate); assertEquals("-08:00", created.endUtcOffset)
        invalid(flight.copy(endTime = "07:30"))
        invalid(flight.copy(endTime = "07:29"))
        invalid(flight.copy(time = "", timeZone = ""))
        invalid(flight.copy(endTime = "", endTimeZone = ""))
    }

    @Test fun `temporal dependency violations are rejected and unknown checkout time is permitted`() {
        val requests = listOf(input().copy(time = "10:00"), input().copy(timeZone = "UTC"), input().copy(utcOffset = "Z"),
            input().copy(endTime = "10:00", endTimeZone = "UTC"), input().copy(endDate = "2026-11-04", endTime = "10:00"),
            input().copy(endTimeZone = "UTC"), input().copy(endUtcOffset = "Z"), input().copy(type = "stay"),
            input().copy(endDate = "2026-11-02"), input().copy(cancellationDeadline = "2026-11-01T10:00"),
            input().copy(cancellationTimeZone = "UTC"), input().copy(cancellationUtcOffset = "Z"))
        requests.forEach { invalid(it) }
        val stay = add(input().copy(type = "stay", endDate = "2026-11-04")).reservations.single()
        assertEquals("", stay.time); assertEquals("", stay.endTime)
        assertTrue(stay.timeZone.isEmpty() && stay.endTimeZone.isEmpty())
        add(input().copy(type = "stay", endDate = "2026-11-03"))
    }

    @Test fun `invalid dates lengths categories and every nonstring JSON token fail without writes`() {
        val requests = listOf(input().copy(date = "2026-02-30"), input().copy(date = "1900-02-29"), input().copy(date = "2100-02-29"),
            input().copy(date = "1899-12-31"), input().copy(date = "2101-01-01"), input().copy(date = "2026-1-01"),
            input().copy(date = "2026-11-03\n"), input().copy(endDate = "2026-02-30"),
            input().copy(cancellationDeadline = "2101-01-01T10:00", cancellationTimeZone = "UTC"),
            input().copy(time = "24:00", timeZone = "UTC"), input().copy(time = "10:00\n", timeZone = "UTC"),
            input().copy(type = "unknown"), input().copy(status = "unknown"), input().copy(title = " "),
            input().copy(title = "가".repeat(121)), input().copy(reference = "x".repeat(121)), input().copy(detail = "x".repeat(4001)),
            input().copy(location = "x".repeat(201)), input().copy(endLocation = "x".repeat(201)), input().copy(provider = "x".repeat(121)),
            input().copy(cancellationPolicy = "x".repeat(2001)), input().copy(time = "12:00", timeZone = "UTC", utcOffset = "+18:01"))
        val before = Files.readAllBytes(dataPath)
        requests.forEach { invalid(it) }
        val tokens = listOf("null", "123", "true", "[]", "{}")
        val names = mapper.valueToTree<ObjectNode>(input()).fieldNames().asSequence().toList()
        names.forEach { field -> tokens.forEach { token ->
            val node = mapper.valueToTree<ObjectNode>(input()).set<ObjectNode>(field, mapper.readTree(token))
            mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).contentType("application/json")
                .content(mapper.writeValueAsString(node))).andExpect(status().isBadRequest)
        } }
        listOf("id", "source", "unknown").forEach { field -> listOf("\"manual\"", "null").forEach { token ->
            val node = mapper.valueToTree<ObjectNode>(input()).set<ObjectNode>(field, mapper.readTree(token))
            mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).contentType("application/json")
                .content(mapper.writeValueAsString(node))).andExpect(status().isBadRequest)
        } }
        listOf("type", "title", "date").forEach { field ->
            val node = mapper.valueToTree<ObjectNode>(input()).also { it.remove(field) }
            mvc.perform(post("/api/demo/reservations").header("X-Trip-Id", trip.trip.id).contentType("application/json")
                .content(mapper.writeValueAsString(node))).andExpect(status().isBadRequest)
        }
        assertArrayEquals(before, Files.readAllBytes(dataPath))
        add(input().copy(date = "2000-02-29"))
        add(input().copy(date = "1900-01-01")); add(input().copy(date = "2100-12-31"))
    }

    @Test fun `invalid edit leaves persisted reservation and other collections unchanged`() {
        val created = add().reservations.single(); val before = Files.readAllBytes(dataPath)
        mvc.perform(patch("/api/demo/reservations/${created.id}").header("X-Trip-Id", trip.trip.id).contentType("application/json")
            .content(mapper.writeValueAsString(input().copy(time = "02:30", date = "2026-03-08", timeZone = "America/New_York"))))
            .andExpect(status().isBadRequest)
        assertArrayEquals(before, Files.readAllBytes(dataPath))
    }

    @Test fun `legacy eight fields gain defaults without reset and richer records survive migration and unrelated writes`() {
        val seed = javaClass.getResourceAsStream("/seed-workspace.json")!!.use { mapper.readTree(it) as ObjectNode }
        val oldFields = setOf("id", "type", "title", "date", "time", "reference", "detail", "status")
        seed.withArray("reservations").forEach { row -> (row as ObjectNode).retain(oldFields) }
        val path = Files.createTempDirectory("yeohaeng-reservation-legacy-").resolve("workspace.json")
        val original = mapper.writeValueAsBytes(seed); Files.write(path, original)
        val migrated = WorkspaceRepository(mapper, path.toString()); val first = migrated.read()
        first.reservations.forEach { row ->
            assertEquals("manual", row.source); assertEquals("", row.location); assertEquals("", row.timeZone)
            assertEquals("", row.utcOffset); assertEquals("", row.endDate); assertEquals("", row.cancellationPolicy)
        }
        assertEquals(seed.withArray("reservations").map { it["id"].asText() }, first.reservations.map { it.id })
        assertEquals(seed.withArray("reservations").map { it["time"].asText() }, first.reservations.map { it.time })
        val backups = Files.list(path.parent).use { stream -> stream.filter { it.fileName.toString().contains(".pre-schema2-") }.toList() }
        assertArrayEquals(original, Files.readAllBytes(backups.single()))
        val rich = first.reservations.first().copy(provider = "기존 공급자", location = "기존 장소", endDate = "2026-11-20", cancellationPolicy = "기존 정책", utcOffset = "+09:00")
        migrated.update { it.copy(reservations = listOf(rich) + it.reservations.drop(1)) }
        WorkspaceService(migrated).addTask(TaskRequest("여권 확인", "packing"))
        assertEquals(listOf(rich) + first.reservations.drop(1), WorkspaceRepository(mapper, path.toString()).read().reservations)
        val edited = WorkspaceService(migrated).editReservation(rich.id, input()).reservations.first()
        assertEquals(rich.id, edited.id); assertEquals(rich.source, edited.source)
    }
}
