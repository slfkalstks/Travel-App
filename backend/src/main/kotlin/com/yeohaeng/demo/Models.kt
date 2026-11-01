package com.yeohaeng.demo

import jakarta.validation.constraints.*

data class Trip(val id: String, val title: String, val destination: String, val country: String,
    val startDate: String, val endDate: String, val travelers: Int, val budgetKrw: Long, val coverImage: String, val archived: Boolean = false)
data class TravelEvent(val id: String, val date: String, val startTime: String, val endTime: String,
    val title: String, val category: String, val location: String, val note: String,
    val costKrw: Long, val latitude: Double, val longitude: Double)
data class Task(val id: String, val title: String, val category: String, val done: Boolean)
data class Expense(val id: String, val title: String, val category: String, val amountKrw: Long,
    val date: String, val method: String)
data class Reservation(val id: String, val type: String, val title: String, val date: String,
    val time: String, val reference: String, val detail: String, val status: String)
data class Favorite(val id: String, val title: String, val category: String, val location: String,
    val note: String, val saved: Boolean, val latitude: Double, val longitude: Double)
data class Journal(val id: String, val date: String, val title: String, val body: String, val mood: String)
data class Workspace(val trip: Trip, val events: List<TravelEvent>, val tasks: List<Task>,
    val expenses: List<Expense>, val reservations: List<Reservation>, val favorites: List<Favorite>,
    val journals: List<Journal>)

data class TripRequest(
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:NotBlank @field:Size(max=80) val destination: String,
    @field:NotBlank @field:Size(max=80) val country: String,
    val startDate: String, val endDate: String,
    @field:Min(1) @field:Max(20) val travelers: Int,
    @field:Min(0) @field:Max(1000000000) val budgetKrw: Long)
data class ArchiveRequest(val archived: Boolean)
data class DoneRequest(val done: Boolean)
data class SavedRequest(val saved: Boolean)
data class EventNoteRequest(@field:Size(max=2000) val note: String)
data class TaskRequest(
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:Pattern(regexp="packing|booking") val category: String)
data class EventEditRequest(
    val date: String, val startTime: String, val endTime: String,
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:Pattern(regexp="flight|stay|food|place|transport") val category: String,
    @field:Size(max=200) val location: String,
    @field:Size(max=2000) val note: String)
data class ExpenseRequest(
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:Pattern(regexp="flight|stay|food|transport|shopping|other") val category: String,
    @field:Min(1) @field:Max(1000000000) val amountKrw: Long,
    val date: String,
    @field:NotBlank @field:Size(max=60) val method: String = "직접 입력")
data class EventRequest(
    val date: String, val startTime: String, val endTime: String,
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:Pattern(regexp="flight|stay|food|place|transport") val category: String,
    @field:Size(max=200) val location: String = "",
    @field:Size(max=2000) val note: String = "",
    @field:Min(0) @field:Max(1000000000) val costKrw: Long = 0,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double = 0.0,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double = 0.0)
data class JournalRequest(
    val date: String,
    @field:NotBlank @field:Size(max=120) val title: String,
    @field:NotBlank @field:Size(max=10000) val body: String,
    @field:NotBlank @field:Size(max=40) val mood: String = "설렘")
