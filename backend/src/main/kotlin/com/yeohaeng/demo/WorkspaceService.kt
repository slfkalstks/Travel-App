package com.yeohaeng.demo

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class MissingItem : RuntimeException("항목을 찾을 수 없어요.")

@Service
class WorkspaceService(private val repository: WorkspaceRepository, private val documents: DocumentService? = null) {
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
    fun addReservation(request: ReservationRequest, key: String? = null, tripId: String? = null) =
        repository.update(key, "POST /reservations", request, tripId) { state ->
            state.copy(reservations = state.reservations + reservation(request, UUID.randomUUID().toString()))
        }
    fun editReservation(id: String, request: ReservationRequest, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        val existing = state.reservations.find { it.id == id } ?: throw MissingItem()
        val edited = reservation(request, existing.id, existing.source)
        state.copy(reservations = state.reservations.map { if (it.id == id) edited else it })
    }
    fun deleteReservation(id: String, tripId: String? = null) = repository.update(tripId = tripId) { state ->
        state.copy(reservations = state.reservations.filterNot { it.id == id },
            documents = state.documents.map { if (it.reservationId == id) it.copy(reservationId = "") else it })
    }
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
        require(state.expenses.all { it.date <= request.endDate }) { "새 여행 종료일 이후의 지출이 있어요. 지출을 먼저 확인해 주세요." }
        state.copy(trip = state.trip.copy(title = request.title.trim(), destination = request.destination.trim(), country = request.country.trim(),
            startDate = request.startDate, endDate = request.endDate, travelers = request.travelers, budgetKrw = request.budgetKrw))
    }
    fun archiveTrip(id: String, archived: Boolean) = repository.update(tripId = id) { it.copy(trip = it.trip.copy(archived = archived)) }
    fun deleteTrip(id: String) = documents?.deleteTrip(id) ?: repository.deleteTrip(id)
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
    private fun reservation(request: ReservationRequest, id: String, source: String = "manual"): Reservation {
        val startDate = reservationDate(request.date)
        val endDate = request.endDate.takeIf { it.isNotEmpty() }?.let { reservationDate(it) }
        require(request.type != "stay" || endDate != null) { "숙박은 체크아웃 날짜를 입력해 주세요." }
        val start = reservationMoment(request.date, request.time, request.timeZone, request.utcOffset, "시작")
        val end = reservationMoment(request.endDate, request.endTime, request.endTimeZone, request.endUtcOffset, "종료")
        if (start != null && end != null) {
            require(end.instant.isAfter(start.instant)) { "종료 시각은 시작 시각보다 늦어야 해요. 시간대와 날짜를 확인해 주세요." }
        } else if (endDate != null) {
            require(!endDate.isBefore(startDate)) { "종료 날짜는 시작 날짜 이후여야 해요." }
        }
        val cancellation = if (request.cancellationDeadline.isEmpty()) {
            reservationMoment("", "", request.cancellationTimeZone, request.cancellationUtcOffset, "취소 마감")
        } else {
            require(request.cancellationDeadline.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"))) { "취소 마감은 YYYY-MM-DDTHH:mm 형식으로 입력해 주세요." }
            reservationMoment(request.cancellationDeadline.substring(0, 10), request.cancellationDeadline.substring(11),
                request.cancellationTimeZone, request.cancellationUtcOffset, "취소 마감")
        }
        return Reservation(id, request.type, request.title.trim(), request.date, request.time, request.reference.trim(),
            request.detail, request.status, request.location.trim(), request.endLocation.trim(), request.provider.trim(),
            request.endDate, request.endTime, request.timeZone, request.endTimeZone, request.cancellationDeadline,
            request.cancellationTimeZone, request.cancellationPolicy, start?.offset ?: "", end?.offset ?: "",
            cancellation?.offset ?: "", source)
    }
    private fun reservationDate(value: String): LocalDate = validDate(value).also {
        require(it.year in 1900..2100) { "예약 날짜는 1900년부터 2100년까지 입력해 주세요." }
    }
    private data class ReservationMoment(val instant: Instant, val offset: String)
    private fun reservationMoment(date: String, time: String, zone: String, offset: String, label: String): ReservationMoment? {
        if (time.isEmpty()) {
            require(zone.isEmpty() && offset.isEmpty()) { "$label 시간이 미정이면 시간대와 오프셋을 비워 주세요." }
            return null
        }
        val day = reservationDate(date)
        require(time.matches(Regex("\\d{2}:\\d{2}"))) { "시간은 HH:mm 형식으로 입력해 주세요." }
        require(zone in ZoneId.getAvailableZoneIds()) { "$label 시간의 유효한 IANA 시간대를 선택해 주세요." }
        val local = LocalDateTime.of(day, LocalTime.parse(time))
        val offsets = ZoneId.of(zone).rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "$label 시각은 시간대 전환으로 존재하지 않아요. 다른 시각을 입력해 주세요." }
        require(offsets.size == 1 || offset.isNotEmpty()) { "$label 시각이 시간대 전환으로 겹쳐요. 해당 UTC 오프셋을 선택해 주세요." }
        val selected = if (offset.isEmpty()) offsets.single() else {
            require(offset.matches(Regex("Z|[+-](?:(?:0[0-9]|1[0-7]):[0-5][0-9](?::[0-5][0-9])?|18:00(?::00)?)"))) { "올바른 UTC 오프셋을 입력해 주세요." }
            ZoneOffset.of(offset).also { require(it in offsets) { "$label 시각의 시간대와 UTC 오프셋이 일치하지 않아요." } }
        }
        return ReservationMoment(local.toInstant(selected), selected.id)
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
