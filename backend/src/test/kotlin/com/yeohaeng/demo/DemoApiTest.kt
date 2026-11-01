package com.yeohaeng.demo

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
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

@SpringBootTest
@AutoConfigureMockMvc
class DemoApiTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    companion object {
        val dataPath = Files.createTempDirectory("yeohaeng-test-").resolve("workspace.json")
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("travel.data-file") { dataPath.toString() }
        }
    }
    @Test fun `seed contract has complete itinerary and coherent budget`() {
        mvc.perform(get("/api/demo/workspace")).andExpect(status().isOk)
            .andExpect(jsonPath("$.trip.startDate").value("2026-11-12"))
            .andExpect(jsonPath("$.trip.endDate").value("2026-11-15"))
            .andExpect(jsonPath("$.events[0].id").value("ev-01"))
            .andExpect(jsonPath("$.tasks.length()").value(10))
    }
    @Test fun `explicit task state is idempotent and survives repository restart`() {
        repeat(2) {
            mvc.perform(patch("/api/demo/tasks/task-05").contentType("application/json").content("""{"done":true}"""))
                .andExpect(status().isOk)
        }
        val reloaded = WorkspaceRepository(mapper, dataPath.toString()).read()
        assertTrue(reloaded.tasks.single { it.id == "task-05" }.done)
        assertEquals(10, reloaded.tasks.size)
    }
    @Test fun `adding expense persists and rejects negative amount without writing`() {
        val before = WorkspaceRepository(mapper, dataPath.toString()).read()
        mvc.perform(post("/api/demo/expenses").contentType("application/json")
            .content("""{"title":"카페","category":"food","amountKrw":7500,"date":"2026-11-13","method":"카드"}"""))
            .andExpect(status().isOk)
        val saved = WorkspaceRepository(mapper, dataPath.toString()).read()
        assertEquals(before.expenses.size + 1, saved.expenses.size)
        assertEquals(7500L, saved.expenses.last().amountKrw)
        mvc.perform(post("/api/demo/expenses").contentType("application/json")
            .content("""{"title":"잘못된 비용","category":"food","amountKrw":-1,"date":"2026-11-13","method":"카드"}"""))
            .andExpect(status().isBadRequest)
        assertEquals(saved, WorkspaceRepository(mapper, dataPath.toString()).read())
    }
    @Test fun `invalid calendar day and event outside trip return 400`() {
        listOf("2026-02-30", "2026-11-20").forEach { date ->
            mvc.perform(post("/api/demo/events").contentType("application/json")
                .content("""{"date":"$date","startTime":"10:00","endTime":"11:00","title":"산책","category":"place"}"""))
                .andExpect(status().isBadRequest)
        }
    }
    @Test fun `reversed time and unknown category return 400`() {
        mvc.perform(post("/api/demo/events").contentType("application/json")
            .content("""{"date":"2026-11-13","startTime":"11:00","endTime":"10:00","title":"산책","category":"place"}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/api/demo/events").contentType("application/json")
            .content("""{"date":"2026-11-13","startTime":"10:00","endTime":"11:00","title":"산책","category":"unknown"}"""))
            .andExpect(status().isBadRequest)
    }
    @Test fun `unknown identifier and malformed update cannot mutate state`() {
        mvc.perform(patch("/api/demo/tasks/absent").contentType("application/json").content("""{"done":true}"""))
            .andExpect(status().isNotFound)
        mvc.perform(patch("/api/demo/tasks/task-01").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest)
    }
    @Test fun `untrusted web origin cannot write`() {
        mvc.perform(patch("/api/demo/tasks/task-01").header("Origin", "https://example.com")
            .contentType("application/json").content("""{"done":false}"""))
            .andExpect(status().isForbidden)
    }
    @Test fun `fractional won is rejected rather than silently truncated`() {
        mvc.perform(post("/api/demo/expenses").contentType("application/json")
            .content("""{"title":"커피","category":"food","amountKrw":7500.9,"date":"2026-11-13","method":"카드"}"""))
            .andExpect(status().isBadRequest)
    }
    @Test fun `event journal and favorite changes persist with full contract`() {
        mvc.perform(post("/api/demo/events").contentType("application/json")
            .content("""{"date":"2026-11-13","startTime":"20:00","endTime":"21:00","title":"저녁 산책","category":"place","location":"난바"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.trip.title").value("오사카 여행"))
        mvc.perform(post("/api/demo/journals").contentType("application/json")
            .content("""{"date":"2026-11-13","title":"산책 기록","body":"강변의 불빛을 기록했다.","mood":"행복"}"""))
            .andExpect(status().isOk)
        repeat(2) {
            mvc.perform(patch("/api/demo/favorites/fav-05").contentType("application/json").content("""{"saved":true}"""))
                .andExpect(status().isOk)
        }
        val reloaded = WorkspaceRepository(mapper, dataPath.toString()).read()
        assertEquals("20:00", reloaded.events.single { it.title == "저녁 산책" }.startTime)
        assertEquals("강변의 불빛을 기록했다.", reloaded.journals.single { it.title == "산책 기록" }.body)
        assertTrue(reloaded.favorites.single { it.id == "fav-05" }.saved)
        assertEquals(5, reloaded.favorites.size)
    }
    @Test fun `POST retry with same key returns one item and conflicting request is 409`() {
        val key = "expense-retry-test"
        val body = """{"title":"멱등성 커피","category":"food","amountKrw":7500,"date":"2026-11-13","method":"카드"}"""
        repeat(2) {
            mvc.perform(post("/api/demo/expenses").header("Idempotency-Key", key)
                .contentType("application/json").content(body)).andExpect(status().isOk)
        }
        val saved = WorkspaceRepository(mapper, dataPath.toString()).read()
        assertEquals(1, saved.expenses.count { it.title == "멱등성 커피" })
        mvc.perform(post("/api/demo/expenses").header("Idempotency-Key", key)
            .contentType("application/json").content(body.replace("7500", "8000")))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
        assertEquals(saved, WorkspaceRepository(mapper, dataPath.toString()).read())
    }
    @Test fun `POST receipts survive restart and retry returns fresh workspace`() {
        val path = Files.createTempDirectory("yeohaeng-restart-").resolve("workspace.json").toString()
        val first = WorkspaceService(WorkspaceRepository(mapper, path))
        val expense = ExpenseRequest("재시도 영수증", "food", 9000, "2026-11-13", "카드")
        val event = EventRequest("2026-11-13", "20:00", "21:00", "재시도 일정", "place")
        val journal = JournalRequest("2026-11-13", "재시도 기록", "한 번만 저장", "행복")
        first.addExpense(expense, "restart-expense")
        first.addEvent(event, "restart-event")
        first.addJournal(journal, "restart-journal")
        val restarted = WorkspaceService(WorkspaceRepository(mapper, path))
        restarted.setDone("task-05", true)
        val retry = restarted.addExpense(expense, "restart-expense")
        assertTrue(retry.tasks.single { it.id == "task-05" }.done)
        restarted.addEvent(event, "restart-event")
        val final = restarted.addJournal(journal, "restart-journal")
        assertEquals(1, final.expenses.count { it.title == expense.title })
        assertEquals(1, final.events.count { it.title == event.title })
        assertEquals(1, final.journals.count { it.title == journal.title })
        assertThrows(IdempotencyConflict::class.java) { restarted.addJournal(journal, "restart-expense") }
    }
    @Test fun `legacy flat store migration keeps all data and corrects insurance task narrowly`() {
        val path = Files.createTempDirectory("yeohaeng-legacy-").resolve("workspace.json")
        val seed = javaClass.getResourceAsStream("/seed-workspace.json")!!.use { mapper.readValue(it, Workspace::class.java) }
        val legacy = seed.copy(tasks = seed.tasks.map {
            if (it.id == "task-04") it.copy(title = "여행자 보험 비교하기", done = true) else it
        })
        mapper.writeValue(path.toFile(), legacy)
        val migrated = WorkspaceRepository(mapper, path.toString()).read()
        assertTrue(migrated.tasks.single { it.id == "task-04" }.done)
        assertEquals("여행자 보험 증권 확인", migrated.tasks.single { it.id == "task-04" }.title)
        assertEquals(legacy.expenses, migrated.expenses)
        assertEquals(legacy.events, migrated.events)
    }
    @Test fun `invalid idempotency key does not create record`() {
        mvc.perform(post("/api/demo/journals").header("Idempotency-Key", "invalid key")
            .contentType("application/json")
            .content("""{"date":"2026-11-13","title":"저장 안 됨","body":"기록","mood":"행복"}"""))
            .andExpect(status().isBadRequest)
        assertTrue(WorkspaceRepository(mapper, dataPath.toString()).read().journals.none { it.title == "저장 안 됨" })
    }
    @Test fun `journal permits pretrip and posttrip notes but rejects invalid calendar dates`() {
        listOf("2026-11-01", "2026-11-30").forEach { date ->
            mvc.perform(post("/api/demo/journals").contentType("application/json")
                .content("""{"date":"$date","title":"여행 생각","body":"여행 전후의 기록","mood":"설렘"}"""))
                .andExpect(status().isOk)
        }
        mvc.perform(post("/api/demo/journals").contentType("application/json")
            .content("""{"date":"2026-02-30","title":"잘못된 날짜","body":"기록","mood":"설렘"}"""))
            .andExpect(status().isBadRequest)
    }
    @Test fun `event note update persists without changing itinerary and retries are idempotent`() {
        val before = WorkspaceRepository(mapper, dataPath.toString()).read()
        val original = before.events.single { it.id == "ev-02" }
        val note = "난바행 승차장 확인\n  교통카드는 외투 주머니에"
        repeat(2) {
            mvc.perform(patch("/api/demo/events/ev-02/note").contentType("application/json")
                .content(mapper.writeValueAsString(EventNoteRequest(note))))
                .andExpect(status().isOk).andExpect(jsonPath("$.trip.title").value("오사카 여행"))
        }
        val reloaded = WorkspaceRepository(mapper, dataPath.toString()).read()
        assertEquals(original.copy(note = note), reloaded.events.single { it.id == "ev-02" })
        assertEquals(before.events.size, reloaded.events.size)
        assertEquals(before.copy(events = before.events.map { if (it.id == "ev-02") it.copy(note = note) else it }), reloaded)
    }
    @Test fun `empty event note clears an existing note and survives reload`() {
        mvc.perform(patch("/api/demo/events/ev-03/note").contentType("application/json")
            .content("""{"note":"체크인 안내 확인"}"""))
            .andExpect(status().isOk)
        mvc.perform(patch("/api/demo/events/ev-03/note").contentType("application/json")
            .content("""{"note":""}"""))
            .andExpect(status().isOk)
        assertEquals("", WorkspaceRepository(mapper, dataPath.toString()).read().events.single { it.id == "ev-03" }.note)
    }
    @Test fun `missing event or malformed note request cannot alter persisted data`() {
        val before = WorkspaceRepository(mapper, dataPath.toString()).read()
        mvc.perform(patch("/api/demo/events/absent/note").contentType("application/json")
            .content("""{"note":"메모"}"""))
            .andExpect(status().isNotFound)
        listOf("{}", """{"note":null}""", """{"note":"메모","title":"바꾸기"}""").forEach { body ->
            mvc.perform(patch("/api/demo/events/ev-04/note").contentType("application/json").content(body))
                .andExpect(status().isBadRequest)
        }
        assertEquals(before, WorkspaceRepository(mapper, dataPath.toString()).read())
    }
    @Test fun `note accepts 2000 characters but rejects 2001 without overwriting saved value`() {
        val accepted = "가".repeat(2000)
        mvc.perform(patch("/api/demo/events/ev-05/note").contentType("application/json")
            .content(mapper.writeValueAsString(EventNoteRequest(accepted))))
            .andExpect(status().isOk)
        mvc.perform(patch("/api/demo/events/ev-05/note").contentType("application/json")
            .content(mapper.writeValueAsString(EventNoteRequest(accepted + "나"))))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        assertEquals(accepted, WorkspaceRepository(mapper, dataPath.toString()).read().events.single { it.id == "ev-05" }.note)
    }
}
