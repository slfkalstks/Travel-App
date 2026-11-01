package com.travel.app.data

import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

 data class TravelState(val workspace: Workspace? = null, val trips: List<Trip> = emptyList(), val loading: Boolean = true, val saving: Boolean = false, val error: String? = null, val message: String? = null)
class TravelStore(
    private val scope: CoroutineScope,
    private val client: HttpClient = createHttpClient(),
    private val baseUrl: String = demoBaseUrl(),
    private val initialTripId: String? = null
) {
    private val mutable = MutableStateFlow(TravelState())
    val state = mutable.asStateFlow()
    fun close() = client.close()
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private var refreshJob: Job? = null
    fun refresh() = loadTrip(mutable.value.workspace?.trip?.id ?: initialTripId)
    fun selectTrip(id: String, onSelected: () -> Unit) = loadTrip(id, onSelected)
    private fun loadTrip(preferredId: String?, onLoaded: () -> Unit = {}) {
        if(mutable.value.saving || refreshJob?.isActive == true) return
        mutable.update { it.copy(loading = true, error = null) }
        refreshJob = scope.launch {
            try {
                val catalogResponse = client.get("$baseUrl/api/demo/trips")
                check(catalogResponse.status.isSuccess())
                val trips = catalogResponse.body<List<Trip>>()
                val selected = trips.firstOrNull { it.id == preferredId }
                    ?: trips.firstOrNull { !it.archived } ?: trips.firstOrNull()
                val fresh = selected?.let { trip ->
                    val response = client.get("$baseUrl/api/demo/workspace") { header("X-Trip-Id", trip.id) }
                    check(response.status.isSuccess())
                    response.body<Workspace>().also { check(it.trip.id == trip.id) }
                }
                mutable.update { it.copy(workspace = fresh, trips = trips, loading = false, error = null) }
                onLoaded()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(loading = false, error = "여행을 불러오지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.") } }
        }
    }
    private inline fun <reified T> mutate(path: String, payload: T, patch: Boolean = false, success: String, operationId: String? = null, delete: Boolean = false, scopeToTrip: Boolean = true, selectSavedTrip: Boolean = true, crossinline onSaved: () -> Unit = {}) {
        if(mutable.value.saving || mutable.value.loading) return
        val tripId = mutable.value.workspace?.trip?.id
        if(scopeToTrip && tripId == null) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val response = client.request("$baseUrl/api/demo/$path") {
                    method = if(delete) HttpMethod.Delete else if(patch) HttpMethod.Patch else HttpMethod.Post
                    if(scopeToTrip) header("X-Trip-Id", tripId!!)
                    if(operationId != null) header("Idempotency-Key", operationId)
                    contentType(ContentType.Application.Json)
                    if(!delete) setBody(payload)
                }
                if(!response.status.isSuccess()) {
                    val message = try { response.body<ApiError>().message.takeIf { it.isNotBlank() } }
                        catch(e: CancellationException) { throw e }
                        catch(e: Exception) { null }
                    mutable.update { it.copy(saving = false, error = message ?: "저장하지 못했어요. 입력과 연결을 확인하고 다시 시도해 주세요.") }
                    return@launch
                }
                val fresh = response.body<Workspace>()
                if(scopeToTrip) check(fresh.trip.id == tripId)
                mutable.update { old ->
                    val catalog = if(old.trips.any { it.id == fresh.trip.id })
                        old.trips.map { if(it.id == fresh.trip.id) fresh.trip else it }
                        else old.trips + fresh.trip
                    val workspace = if(selectSavedTrip || old.workspace?.trip?.id == fresh.trip.id) fresh else old.workspace
                    old.copy(workspace = workspace, trips = catalog, saving = false, message = success)
                }
                onSaved()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving = false, error = "저장 결과를 확인하지 못했어요. 다시 불러와 저장 여부를 확인해 주세요.") } }
        }
    }
    fun addTrip(input: TripInput, operationId: String, onSaved: () -> Unit) = mutate("trips", input, success = "여행을 만들었어요", operationId = operationId, scopeToTrip = false, onSaved = onSaved)
    fun updateTrip(id: String, input: TripInput, onSaved: () -> Unit) = mutate("trips/$id", input, true, "여행을 수정했어요", scopeToTrip = false, selectSavedTrip = false, onSaved = onSaved)
    fun archiveTrip(id: String, archived: Boolean, onSaved: () -> Unit) = mutate("trips/$id/archive", ArchiveChange(archived), true, if(archived) "여행을 보관했어요" else "여행을 꺼냈어요", scopeToTrip = false, selectSavedTrip = false, onSaved = onSaved)
    fun deleteTrip(id: String, onSaved: () -> Unit) {
        if(mutable.value.saving || mutable.value.loading) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val response = client.delete("$baseUrl/api/demo/trips/$id")
                if(!response.status.isSuccess()) {
                    val message = try { response.body<ApiError>().message.takeIf { it.isNotBlank() } }
                        catch(e: CancellationException) { throw e }
                        catch(e: Exception) { null }
                    mutable.update { it.copy(saving = false, error = message ?: "여행을 삭제하지 못했어요. 다시 시도해 주세요.") }
                    return@launch
                }
                val trips = response.body<List<Trip>>()
                mutable.update { old -> old.copy(trips = trips, workspace = old.workspace?.takeIf { it.trip.id != id }, saving = false, message = "여행을 삭제했어요") }
                onSaved()
                if(mutable.value.workspace == null && trips.isNotEmpty()) refresh()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving = false, error = "삭제 결과를 확인하지 못했어요. 다시 시도해 주세요.") } }
        }
    }
    fun updateEvent(id: String, input: EventEditInput, onSaved: () -> Unit) = mutate("events/$id", input, true, "일정을 수정했어요", onSaved = onSaved)
    fun deleteEvent(id: String, onSaved: () -> Unit) = mutate("events/$id", Unit, success = "일정을 삭제했어요", delete = true, onSaved = onSaved)
    fun addTask(input: TaskInput, operationId: String, onSaved: () -> Unit) = mutate("tasks", input, success = "준비물을 추가했어요", operationId = operationId, onSaved = onSaved)
    fun updateTask(id: String, input: TaskInput, onSaved: () -> Unit) = mutate("tasks/$id/details", input, true, "준비물을 수정했어요", onSaved = onSaved)
    fun deleteTask(id: String, onSaved: () -> Unit) = mutate("tasks/$id", Unit, success = "준비물을 삭제했어요", delete = true, onSaved = onSaved)
    fun updateEventNote(id: String, note: String, onSaved: () -> Unit) = mutate("events/$id/note", EventNoteChange(note), true, "일정 메모를 저장했어요", onSaved = onSaved)
    fun toggleTask(task: TravelTask) = mutate("tasks/${task.id}", TaskChange(!task.done), true, "준비 상태를 저장했어요")
    fun toggleFavorite(favorite: Favorite) = mutate("favorites/${favorite.id}", FavoriteChange(!favorite.saved), true, if(favorite.saved) "저장한 장소에서 제외했어요" else "가고 싶은 곳에 저장했어요")
    fun addExpense(input: ExpenseInput, operationId: String, onSaved: () -> Unit) = mutate("expenses", input, success = "지출을 기록했어요", operationId = operationId, onSaved = onSaved)
    fun addEvent(input: EventInput, operationId: String, onSaved: () -> Unit) = mutate("events", input, success = "일정을 추가했어요", operationId = operationId, onSaved = onSaved)
    fun addJournal(input: JournalInput, operationId: String, onSaved: () -> Unit) = mutate("journals", input, success = "여행 노트를 저장했어요", operationId = operationId, onSaved = onSaved)
}
