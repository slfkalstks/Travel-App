package com.yeohaeng.demo

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class MissingItem : RuntimeException("항목을 찾을 수 없어요.")

@Service
class WorkspaceService(private val repository: WorkspaceRepository) {
    fun read() = repository.read()
    fun setDone(id: String, done: Boolean) = repository.update { state ->
        if (state.tasks.none { it.id == id }) throw MissingItem()
        state.copy(tasks = state.tasks.map { if (it.id == id) it.copy(done = done) else it })
    }
    fun setSaved(id: String, saved: Boolean) = repository.update { state ->
        if (state.favorites.none { it.id == id }) throw MissingItem()
        state.copy(favorites = state.favorites.map { if (it.id == id) it.copy(saved = saved) else it })
    }
    fun setEventNote(id: String, note: String) = repository.update { state ->
        if (state.events.none { it.id == id }) throw MissingItem()
        state.copy(events = state.events.map { if (it.id == id) it.copy(note = note) else it })
    }
    fun addExpense(request: ExpenseRequest, key: String? = null) = repository.update(key, "POST /expenses", request) { state ->
        val date = validDate(request.date)
        require(!date.isAfter(LocalDate.parse(state.trip.endDate))) { "여행 종료일까지의 지출만 기록할 수 있어요." }
        state.copy(expenses = state.expenses + Expense(UUID.randomUUID().toString(), request.title.trim(),
            request.category, request.amountKrw, request.date, request.method.trim()))
    }
    fun addEvent(request: EventRequest, key: String? = null) = repository.update(key, "POST /events", request) { state ->
        tripDate(request.date, state.trip)
        require(request.startTime.matches(Regex("\\d{2}:\\d{2}")) && request.endTime.matches(Regex("\\d{2}:\\d{2}"))) { "시간은 HH:mm 형식으로 입력해 주세요." }
        val start = LocalTime.parse(request.startTime)
        val end = LocalTime.parse(request.endTime)
        require(end.isAfter(start)) { "종료 시간은 시작 시간보다 늦어야 해요." }
        state.copy(events = (state.events + TravelEvent(UUID.randomUUID().toString(), request.date,
            request.startTime, request.endTime, request.title.trim(), request.category, request.location.trim(),
            request.note.trim(), request.costKrw, request.latitude, request.longitude))
            .sortedWith(compareBy({ it.date }, { it.startTime })))
    }
    fun addJournal(request: JournalRequest, key: String? = null) = repository.update(key, "POST /journals", request) { state ->
        validDate(request.date) // Preparation notes and post-trip reflections are valid journal entries.
        state.copy(journals = state.journals + Journal(UUID.randomUUID().toString(), request.date,
            request.title.trim(), request.body.trim(), request.mood.trim()))
    }
    private fun validDate(value: String): LocalDate {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "날짜는 YYYY-MM-DD 형식으로 입력해 주세요." }
        return LocalDate.parse(value)
    }
    private fun tripDate(value: String, trip: Trip) {
        val date = validDate(value)
        require(!date.isBefore(LocalDate.parse(trip.startDate)) && !date.isAfter(LocalDate.parse(trip.endDate))) {
            "여행 기간 안의 날짜를 선택해 주세요."
        }
    }
}
