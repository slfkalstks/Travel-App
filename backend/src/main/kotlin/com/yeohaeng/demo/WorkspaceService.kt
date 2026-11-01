package com.yeohaeng.demo

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class MissingItem : RuntimeException("항목을 찾을 수 없어요.")

@Service
class WorkspaceService(private val repository: WorkspaceRepository) {
    fun read(tripId: String? = null) = repository.read(tripId)
    fun setDone(id: String, done: Boolean, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        if (state.tasks.none { it.id == id }) throw MissingItem()
        state.copy(tasks = state.tasks.map { if (it.id == id) it.copy(done = done) else it })
    }
    fun addTask(request: TaskRequest, key: String? = null, tripId: String? = null) = repository.update(key, "POST /tasks", request, tripId) { state ->
        state.copy(tasks = state.tasks + Task(UUID.randomUUID().toString(), request.title.trim(), request.category, false))
    }
    fun editTask(id: String, request: TaskRequest, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        if (state.tasks.none { it.id == id }) throw MissingItem()
        state.copy(tasks = state.tasks.map {
            if (it.id == id) it.copy(title = request.title.trim(), category = request.category) else it
        })
    }
    fun deleteTask(id: String, tripId: String? = null) = repository.update(tripId = tripId) { state -> state.copy(tasks = state.tasks.filterNot { it.id == id }) }
    fun setSaved(id: String, saved: Boolean, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        if (state.favorites.none { it.id == id }) throw MissingItem()
        state.copy(favorites = state.favorites.map { if (it.id == id) it.copy(saved = saved) else it })
    }
    fun setEventNote(id: String, note: String, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        if (state.events.none { it.id == id }) throw MissingItem()
        state.copy(events = state.events.map { if (it.id == id) it.copy(note = note) else it })
    }
    fun editEvent(id: String, request: EventEditRequest, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        if (state.events.none { it.id == id }) throw MissingItem()
        eventWindow(request.date, request.startTime, request.endTime, state.trip)
        state.copy(events = state.events.map {
            if (it.id == id) it.copy(date = request.date, startTime = request.startTime, endTime = request.endTime,
                title = request.title.trim(), category = request.category, location = request.location.trim(), note = request.note)
            else it
        }.sortedWith(compareBy({ it.date }, { it.startTime })))
    }
    fun deleteEvent(id: String, tripId: String? = null) = repository.update(tripId = tripId) { state -> state.copy(events = state.events.filterNot { it.id == id }) }
    fun addExpense(request: ExpenseRequest, key: String? = null, tripId: String? = null) = repository.update(key, "POST /expenses", request, tripId) { state ->
        val date = validDate(request.date)
        require(!date.isAfter(LocalDate.parse(state.trip.endDate))) { "여행 종료일까지의 지출만 기록할 수 있어요." }
        state.copy(expenses = state.expenses + Expense(UUID.randomUUID().toString(), request.title.trim(),
            request.category, request.amountKrw, request.date, request.method.trim()))
    }
    fun addEvent(request: EventRequest, key: String? = null, tripId: String? = null) = repository.update(key, "POST /events", request, tripId) { state ->
        eventWindow(request.date, request.startTime, request.endTime, state.trip)
        state.copy(events = (state.events + TravelEvent(UUID.randomUUID().toString(), request.date,
            request.startTime, request.endTime, request.title.trim(), request.category, request.location.trim(),
            request.note.trim(), request.costKrw, request.latitude, request.longitude))
            .sortedWith(compareBy({ it.date }, { it.startTime })))
    }
    fun addJournal(request: JournalRequest, key: String? = null, tripId: String? = null) = repository.update(key, "POST /journals", request, tripId) { state ->
        validDate(request.date) // Preparation notes and post-trip reflections are valid journal entries.
        state.copy(journals = state.journals + Journal(UUID.randomUUID().toString(), request.date,
            request.title.trim(), request.body.trim(), request.mood.trim()))
    }
    fun trips() = repository.trips()
    fun createTrip(request: TripRequest, key: String? = null): Workspace {
        validateTripPeriod(request)
        return repository.createTrip(request, key) {
            Workspace(Trip(UUID.randomUUID().toString(), request.title.trim(), request.destination.trim(), request.country.trim(),
                request.startDate, request.endDate, request.travelers, request.budgetKrw, "", false),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
    fun editTrip(id: String, request: TripRequest) = repository.update(tripId = id) { state ->
        validateTripPeriod(request)
        require(state.events.all { it.date >= request.startDate && it.date <= request.endDate }) { "새 여행 기간 밖에 일정이 있어요. 일정을 먼저 변경해 주세요." }
        require(state.reservations.all { it.date >= request.startDate && it.date <= request.endDate }) { "새 여행 기간 밖에 예약이 있어요. 예약을 먼저 확인해 주세요." }
        require(state.expenses.all { it.date <= request.endDate }) { "새 여행 종료일 이후의 지출이 있어요. 지출을 먼저 확인해 주세요." }
        state.copy(trip = state.trip.copy(title = request.title.trim(), destination = request.destination.trim(), country = request.country.trim(),
            startDate = request.startDate, endDate = request.endDate, travelers = request.travelers, budgetKrw = request.budgetKrw))
    }
    fun archiveTrip(id: String, archived: Boolean) = repository.update(tripId = id) { it.copy(trip = it.trip.copy(archived = archived)) }
    fun deleteTrip(id: String) = repository.deleteTrip(id)
    private fun validateTripPeriod(request: TripRequest) {
        val start = validDate(request.startDate)
        val end = validDate(request.endDate)
        require(start.year in 1900..2100 && end.year in 1900..2100) { "여행 날짜는 1900년부터 2100년까지 선택해 주세요." }
        require(!end.isBefore(start)) { "여행 종료일은 시작일 이후여야 해요." }
        require(ChronoUnit.DAYS.between(start, end) + 1 <= 366) { "여행 기간은 최대 366일이에요." }
    }
    private fun validDate(value: String): LocalDate {
        require(value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "날짜는 YYYY-MM-DD 형식으로 입력해 주세요." }
        return LocalDate.parse(value)
    }
    private fun eventWindow(date: String, startTime: String, endTime: String, trip: Trip) {
        tripDate(date, trip)
        require(startTime.matches(Regex("\\d{2}:\\d{2}")) && endTime.matches(Regex("\\d{2}:\\d{2}"))) { "시간은 HH:mm 형식으로 입력해 주세요." }
        require(LocalTime.parse(endTime).isAfter(LocalTime.parse(startTime))) { "종료 시간은 시작 시간보다 늦어야 해요." }
    }
    private fun tripDate(value: String, trip: Trip) {
        val date = validDate(value)
        require(!date.isBefore(LocalDate.parse(trip.startDate)) && !date.isAfter(LocalDate.parse(trip.endDate))) {
            "여행 기간 안의 날짜를 선택해 주세요."
        }
    }
}
