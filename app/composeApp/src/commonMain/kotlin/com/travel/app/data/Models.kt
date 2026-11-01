package com.travel.app.data

import kotlinx.serialization.Serializable

@Serializable data class Workspace(val trip: Trip, val events: List<TravelEvent> = emptyList(), val tasks: List<TravelTask> = emptyList(), val expenses: List<Expense> = emptyList(), val reservations: List<Reservation> = emptyList(), val favorites: List<Favorite> = emptyList(), val journals: List<Journal> = emptyList())
@Serializable data class Trip(val id: String, val title: String, val destination: String, val country: String, val startDate: String, val endDate: String, val travelers: Int, val budgetKrw: Long, val coverImage: String = "", val archived: Boolean = false)
@Serializable data class TravelEvent(val id: String, val date: String, val startTime: String, val endTime: String, val title: String, val category: String, val location: String, val note: String = "", val costKrw: Long = 0, val latitude: Double? = null, val longitude: Double? = null)
@Serializable data class TravelTask(val id: String, val title: String, val category: String, val done: Boolean)
@Serializable data class Expense(val id: String, val title: String, val category: String, val amountKrw: Long, val date: String, val method: String)
@Serializable data class Reservation(val id: String, val type: String, val title: String, val date: String, val time: String, val reference: String, val detail: String, val status: String)
@Serializable data class Favorite(val id: String, val title: String, val category: String, val location: String, val note: String, val saved: Boolean, val latitude: Double? = null, val longitude: Double? = null)
@Serializable data class Journal(val id: String, val date: String, val title: String, val body: String, val mood: String)
@Serializable data class TripInput(val title: String, val destination: String, val country: String, val startDate: String, val endDate: String, val travelers: Int, val budgetKrw: Long)
@Serializable data class ArchiveChange(val archived: Boolean)
@Serializable data class TaskInput(val title: String, val category: String)
@Serializable data class EventEditInput(val date: String, val startTime: String, val endTime: String, val title: String, val category: String, val location: String, val note: String)
@Serializable data class EventNoteChange(val note: String)
@Serializable data class TaskChange(val done: Boolean)
@Serializable data class FavoriteChange(val saved: Boolean)
@Serializable data class ExpenseInput(val title: String, val category: String, val amountKrw: Long, val date: String, val method: String = "카드")
@Serializable data class EventInput(val date: String, val startTime: String, val endTime: String, val title: String, val category: String, val location: String, val note: String = "", val costKrw: Long = 0)
@Serializable data class JournalInput(val date: String, val title: String, val body: String, val mood: String = "설렘")

fun money(value: Long): String = value.toString().reversed().chunked(3).joinToString(",").reversed() + "원"
fun shortDate(value: String): String = value.takeIf { it.length == 10 }?.let { "${it.substring(5,7).toInt()}.${it.substring(8,10).toInt()}" } ?: value
fun categoryName(value: String): String = when(value) { "flight" -> "항공"; "stay" -> "숙소"; "food" -> "식사"; "transport" -> "교통"; "shopping" -> "쇼핑"; "place" -> "산책·명소"; "packing" -> "준비물"; "booking" -> "예약 확인"; else -> "기타" }

@Serializable data class ApiError(val code: String = "", val message: String = "")

// Preserve catalog display names; only known airport/home presentation aliases use a shared canonical label.
fun canonicalDestinationCity(value: String): String = when(val name = value.trim()) {
    "오사카 시" -> "오사카"
    "후쿠오카 시" -> "후쿠오카"
    "삿포로 시" -> "삿포로"
    "서울특별시" -> "서울"
    "부산광역시" -> "부산"
    else -> name
}
