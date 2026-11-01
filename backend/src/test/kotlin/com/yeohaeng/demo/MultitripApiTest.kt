package com.yeohaeng.demo

import com.fasterxml.jackson.databind.ObjectMapper
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
class MultitripApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var repository: WorkspaceRepository
    companion object {
        val dataPath = Files.createTempDirectory("yeohaeng-multitrip-").resolve("workspace.json")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("travel.data-file") { dataPath.toString() }
        }
    }
    @BeforeEach fun emptyTestCollection() { repository.trips().forEach { repository.deleteTrip(it.id) } }
    private fun input(title: String = "도쿄 여행") = TripRequest(title, "도쿄", "일본", "2026-11-12", "2026-11-15", 2, 1500000)
    private fun create(request: TripRequest = input(), key: String = UUID.randomUUID().toString()): Workspace {
        val response = mvc.perform(post("/api/demo/trips").header("Idempotency-Key", key).contentType("application/json")
            .content(mapper.writeValueAsString(request))).andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readValue(response)
    }
    private fun getWorkspace(id: String): Workspace = mapper.readValue(mvc.perform(get("/api/demo/workspace").header("X-Trip-Id", id))
        .andExpect(status().isOk).andReturn().response.contentAsString)

    @Test fun `new trips have no copied seed data and selection is local to each request`() {
        val a = create(input("첫 여행")); val b = create(input("둘째 여행"))
        assertEquals("", a.trip.coverImage)
        assertFalse(a.trip.archived)
        assertTrue(a.events.isEmpty() && a.tasks.isEmpty() && a.expenses.isEmpty() && a.reservations.isEmpty() && a.favorites.isEmpty() && a.journals.isEmpty())
        assertEquals(b, getWorkspace(b.trip.id))
        mvc.perform(get("/api/demo/workspace")).andExpect(status().isOk).andExpect(jsonPath("$.trip.id").value(a.trip.id))
        mvc.perform(get("/api/demo/workspace").header("X-Trip-Id", "unknown")).andExpect(status().isNotFound)
        mvc.perform(get("/api/demo/workspace").header("X-Trip-Id", "")).andExpect(status().isNotFound)
        mvc.perform(get("/api/demo/trips")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(2))
    }

    @Test fun `trip creation receipt survives restart and conflicts with a different payload`() {
        val request = input(); val key = UUID.randomUUID().toString()
        val created = create(request, key)
        assertEquals(created.trip.id, create(request, key).trip.id)
        val restarted = WorkspaceService(WorkspaceRepository(mapper, dataPath.toString()))
        assertEquals(created, restarted.createTrip(request, key))
        assertEquals(1, restarted.trips().size)
        mvc.perform(post("/api/demo/trips").header("Idempotency-Key", key).contentType("application/json")
            .content(mapper.writeValueAsString(request.copy(title = "다른 이름"))))
            .andExpect(status().isConflict)
    }

    @Test fun `trip editing and archiving preserve hidden identity cover and nested data`() {
        val created = create(); val id = created.trip.id
        repository.update(tripId = id) { it.copy(trip = it.trip.copy(coverImage = "user-cover"), tasks = listOf(Task("check", "여권", "packing", true))) }
        repeat(2) { mvc.perform(patch("/api/demo/trips/$id/archive").contentType("application/json").content("""{"archived":true}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.trip.archived").value(true)) }
        val request = input("  수정한 여행  ").copy(destination = "  교토  ", country = "  일본  ", travelers = 3, budgetKrw = 0)
        mvc.perform(patch("/api/demo/trips/$id").contentType("application/json").content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk)
        val saved = getWorkspace(id)
        assertEquals(Trip(id, "수정한 여행", "교토", "일본", request.startDate, request.endDate, 3, 0, "user-cover", true), saved.trip)
        assertEquals(listOf(Task("check", "여권", "packing", true)), saved.tasks)
        mvc.perform(patch("/api/demo/trips/$id/archive").contentType("application/json").content("""{"archived":false}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.trip.archived").value(false))
        mvc.perform(patch("/api/demo/trips/unknown/archive").contentType("application/json").content("""{"archived":true}"""))
            .andExpect(status().isNotFound)
        mvc.perform(patch("/api/demo/trips/$id/archive").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest)
    }

    @Test fun `trip validation rejects invalid fields without changing collection`() {
        val created = create(); val request = input(); val before = repository.trips()
        val invalid = listOf(request.copy(title = " "), request.copy(title = "가".repeat(121)), request.copy(destination = ""),
            request.copy(destination = "가".repeat(81)), request.copy(country = " "), request.copy(country = "가".repeat(81)),
            request.copy(travelers = 0), request.copy(travelers = 21), request.copy(budgetKrw = -1), request.copy(budgetKrw = 1000000001),
            request.copy(startDate = "2026-02-30"), request.copy(startDate = "2026-1-12"), request.copy(startDate = "1899-12-31", endDate = "1900-01-01"),
            request.copy(startDate = "2100-12-31", endDate = "2101-01-01"), request.copy(startDate = "2026-11-16"),
            request.copy(startDate = "2024-01-01", endDate = "2025-01-01"))
        invalid.forEach { body ->
            mvc.perform(post("/api/demo/trips").contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest)
            mvc.perform(patch("/api/demo/trips/${created.trip.id}").contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest)
        }
        mvc.perform(patch("/api/demo/trips/unknown").contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isNotFound)
        val hiddenField = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(request).put("archived", true)
        mvc.perform(patch("/api/demo/trips/${created.trip.id}").contentType("application/json").content(mapper.writeValueAsString(hiddenField))).andExpect(status().isBadRequest)
        assertEquals(before, repository.trips())
        assertEquals(created, repository.read(created.trip.id))
        assertEquals("2024-12-31", create(request.copy(startDate = "2024-01-01", endDate = "2024-12-31")).trip.endDate)
        assertEquals("1900-01-01", create(request.copy(startDate = "1900-01-01", endDate = "1900-01-01", travelers = 20)).trip.startDate)
    }

    @Test fun `contracting trip dates protects events reservations and expenses but allows pretrip expenses`() {
        val request = input(); val trip = create(request); val id = trip.trip.id
        val event = TravelEvent("event", "2026-11-12", "10:00", "11:00", "산책", "place", "교토", "", 0, 0.0, 0.0)
        val reservation = Reservation("reservation", "stay", "숙소", "2026-11-15", "10:00", "", "", "planned")
        val expense = Expense("expense", "교통", "transport", 1000, "2026-11-15", "카드")
        val contraction = request.copy(startDate = "2026-11-13", endDate = "2026-11-14")
        listOf(trip.copy(events = listOf(event)), trip.copy(reservations = listOf(reservation)), trip.copy(expenses = listOf(expense))).forEach { fixture ->
            repository.update(tripId = id) { fixture }
            mvc.perform(patch("/api/demo/trips/$id").contentType("application/json").content(mapper.writeValueAsString(contraction)))
                .andExpect(status().isBadRequest)
            assertEquals(fixture, repository.read(id))
        }
        repository.update(tripId = id) { trip.copy(expenses = listOf(expense.copy(date = "2026-10-01")), journals = listOf(Journal("memo", "2026-12-01", "회고", "기록", "좋음"))) }
        mvc.perform(patch("/api/demo/trips/$id").contentType("application/json").content(mapper.writeValueAsString(contraction)))
            .andExpect(status().isOk)
        assertEquals("2026-10-01", getWorkspace(id).expenses.single().date)
    }

    @Test fun `all nested operations use trip header and receipt keys are independent between trips`() {
        val a = create(input("A")); val b = create(input("B"))
        val commonEvent = TravelEvent("same-event", "2026-11-13", "10:00", "11:00", "원래 일정", "place", "도쿄", "원래 메모", 4000, 35.0, 139.0)
        val commonTask = Task("same-task", "원래 준비물", "packing", false)
        val commonFavorite = Favorite("same-favorite", "공원", "place", "도쿄", "", false, 35.0, 139.0)
        listOf(a, b).forEach { trip -> repository.update(tripId = trip.trip.id) { it.copy(events = listOf(commonEvent), tasks = listOf(commonTask), favorites = listOf(commonFavorite)) } }
        mvc.perform(patch("/api/demo/events/same-event/note").header("X-Trip-Id", a.trip.id).contentType("application/json").content("""{"note":"A메모"}"""))
            .andExpect(status().isOk)
        val eventEdit = EventEditRequest("2026-11-14", "12:00", "13:00", "A일정", "food", "교토", "A메모")
        mvc.perform(patch("/api/demo/events/same-event").header("X-Trip-Id", a.trip.id).contentType("application/json").content(mapper.writeValueAsString(eventEdit))).andExpect(status().isOk)
        mvc.perform(patch("/api/demo/tasks/same-task").header("X-Trip-Id", a.trip.id).contentType("application/json").content("""{"done":true}"""))
            .andExpect(status().isOk)
        mvc.perform(patch("/api/demo/tasks/same-task/details").header("X-Trip-Id", a.trip.id).contentType("application/json").content("""{"title":"A준비물","category":"booking"}"""))
            .andExpect(status().isOk)
        mvc.perform(patch("/api/demo/favorites/same-favorite").header("X-Trip-Id", a.trip.id).contentType("application/json").content("""{"saved":true}"""))
            .andExpect(status().isOk)
        listOf(a, b).forEach { trip ->
            val requests = mapOf<String, Any>("tasks" to TaskRequest("새 준비물", "packing"),
                "events" to EventRequest("2026-11-14", "14:00", "15:00", trip.trip.title + "새일정", "place"),
                "expenses" to ExpenseRequest(trip.trip.title + "지출", "food", 5000, "2026-11-14"),
                "journals" to JournalRequest("2026-11-01", trip.trip.title + "기록", "여행 준비"))
            requests.forEach { (route, body) -> repeat(2) {
                mvc.perform(post("/api/demo/$route").header("X-Trip-Id", trip.trip.id).header("Idempotency-Key", "shared-$route")
                    .contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isOk)
            } }
        }
        val other = getWorkspace(b.trip.id)
        assertEquals(commonEvent, other.events.single { it.id == commonEvent.id })
        assertEquals(commonTask, other.tasks.single { it.id == commonTask.id })
        assertEquals(commonFavorite, other.favorites.single())
        assertEquals(2, other.tasks.size); assertEquals(2, other.events.size); assertEquals(1, other.expenses.size); assertEquals(1, other.journals.size)
        val onlyA = getWorkspace(a.trip.id).tasks.single { it.id != commonTask.id }.id
        mvc.perform(patch("/api/demo/tasks/$onlyA").header("X-Trip-Id", b.trip.id).contentType("application/json").content("""{"done":true}"""))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/demo/tasks/same-task").header("X-Trip-Id", a.trip.id)).andExpect(status().isOk)
        mvc.perform(delete("/api/demo/events/same-event").header("X-Trip-Id", a.trip.id)).andExpect(status().isOk)
        assertEquals(other, getWorkspace(b.trip.id))
        assertEquals(other, WorkspaceRepository(mapper, dataPath.toString()).read(b.trip.id))
    }

    @Test fun `deleting last trip remains empty on restart and replayed creation cannot resurrect it`() {
        val request = input(); val key = UUID.randomUUID().toString(); val a = create(request, key)
        val b = create(input("다른 여행"))
        mvc.perform(delete("/api/demo/trips/${b.trip.id}")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(1))
        repeat(2) { mvc.perform(delete("/api/demo/trips/${a.trip.id}")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0)) }
        mvc.perform(get("/api/demo/trips")).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/api/demo/workspace")).andExpect(status().isNotFound)
        val restarted = WorkspaceService(WorkspaceRepository(mapper, dataPath.toString()))
        assertTrue(restarted.trips().isEmpty())
        assertThrows(MissingItem::class.java) { restarted.read() }
        assertThrows(IdempotencyConflict::class.java) { restarted.createTrip(request, key) }
        mvc.perform(post("/api/demo/trips").header("Idempotency-Key", key).contentType("application/json").content(mapper.writeValueAsString(request)))
            .andExpect(status().isConflict)
        assertEquals(1, create(request).let { repository.trips().size })
    }

    @Test fun `schema one migration preserves all data receipts and exact original backup bytes`() {
        val path = Files.createTempDirectory("yeohaeng-schema1-").resolve("workspace.json")
        val source = WorkspaceRepository(mapper, path.toString())
        val service = WorkspaceService(source)
        val expense = ExpenseRequest("기존 지출", "food", 8000, "2026-11-13")
        service.addExpense(expense, "legacy-receipt")
        val collection = mapper.readValue<StoredCollection>(path.toFile())
        val legacy = collection.workspaces.values.single()
        val originalBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(legacy)
        Files.write(path, originalBytes)
        val migrated = WorkspaceRepository(mapper, path.toString())
        assertEquals(legacy.workspace, migrated.read())
        assertEquals(legacy.workspace, WorkspaceService(migrated).addExpense(expense, "legacy-receipt"))
        val stored = mapper.readValue<StoredCollection>(path.toFile())
        assertEquals(2, stored.schemaVersion)
        assertEquals(legacy.receipts, stored.workspaces.values.single().receipts)
        val backups = Files.list(path.parent).use { stream -> stream.filter { it.fileName.toString().contains(".pre-schema2-") }.toList() }
        assertEquals(1, backups.size)
        assertArrayEquals(originalBytes, Files.readAllBytes(backups.single()))
    }

    @Test fun `unknown corrupt and inconsistent schemas fail without overwriting original files`() {
        val valid = WorkspaceRepository(mapper, Files.createTempDirectory("yeohaeng-valid-").resolve("workspace.json").toString()).read()
        val mismatched = mapper.writeValueAsString(StoredCollection(mapOf("wrong-key" to StoredWorkspace(valid))))
        val invalid = listOf("{broken", "[]", """{"schemaVersion":99,"workspaces":{}}""", """{"schemaVersion":"2","workspaces":{}}""",
            """{"schemaVersion":4294967298,"workspaces":{}}""", """{"schemaVersion":2}""", mismatched)
        invalid.forEach { body ->
            val path = Files.createTempDirectory("yeohaeng-invalid-").resolve("workspace.json")
            Files.writeString(path, body)
            assertThrows(Exception::class.java) { WorkspaceRepository(mapper, path.toString()) }
            assertEquals(body, Files.readString(path))
            assertEquals(1L, Files.list(path.parent).use { it.count() })
        }
    }
}
