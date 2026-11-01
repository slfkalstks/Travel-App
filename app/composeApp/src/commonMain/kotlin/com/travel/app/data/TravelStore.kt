package com.travel.app.data

import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.request.forms.*
import com.travel.app.platform.PickedDocument
import com.travel.app.platform.documentExtension
import com.travel.app.platform.MAX_DOCUMENT_BYTES
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

 data class TravelState(val workspace: Workspace? = null, val trips: List<Trip> = emptyList(), val loading: Boolean = true, val saving: Boolean = false, val error: String? = null, val message: String? = null, val offline: Boolean = false)
class TravelStore(
    private val scope: CoroutineScope,
    private val client: HttpClient = createHttpClient(),
    private val baseUrl: String = demoBaseUrl(),
    private val initialTripId: String? = null,
    private val cache: TravelCache? = null
) {
    private val mutable = MutableStateFlow(TravelState())
    val state = mutable.asStateFlow()
    fun close() = client.close()
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private var refreshJob: Job? = null
    private class WorkspaceMismatch : Exception()
    fun refresh() = loadTrip(mutable.value.workspace?.trip?.id ?: initialTripId)
    fun selectTrip(id: String, onSelected: () -> Unit) = loadTrip(id, onSelected, explicitSelection = true)
    private suspend fun cacheSnapshot(trips: List<Trip>, workspace: Workspace?): Boolean = try {
        cache?.save(trips, workspace); true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    private fun loadTrip(preferredId: String?, onLoaded: () -> Unit = {}, explicitSelection: Boolean = false) {
        if(mutable.value.saving || refreshJob?.isActive == true) return
        mutable.update { it.copy(loading = true, error = null) }
        refreshJob = scope.launch {
            val saved = try { cache?.read() } catch(e: CancellationException) { throw e } catch (_: Exception) { null }
            val savedId = preferredId ?: saved?.lastTripId
            val cached = saved?.workspaces?.get(savedId)
                ?: if (!explicitSelection) saved?.workspaces?.values?.firstOrNull() else null
            if (mutable.value.workspace == null && cached != null) {
                mutable.update { it.copy(workspace = cached, trips = saved!!.trips) }
            }
            try {
                val result = withTimeout(5_000) {
                    val catalogResponse = client.get("$baseUrl/api/demo/trips")
                    check(catalogResponse.status.isSuccess())
                    val trips = catalogResponse.body<List<Trip>>()
                    val selected = trips.firstOrNull { it.id == (preferredId ?: saved?.lastTripId) }
                        ?: trips.firstOrNull { !it.archived } ?: trips.firstOrNull()
                    val fresh = selected?.let { trip ->
                        val response = client.get("$baseUrl/api/demo/workspace") { header("X-Trip-Id", trip.id) }
                        check(response.status.isSuccess())
                        response.body<Workspace>().also { if(it.trip.id != trip.id) throw WorkspaceMismatch() }
                    }
                    trips to fresh
                }
                val (trips, fresh) = result
                val cachedOk = cacheSnapshot(trips, fresh)
                mutable.update { it.copy(workspace = fresh, trips = trips, loading = false, offline = false,
                    error = if(cachedOk) null else "여행은 불러왔지만 기기에 보관하지 못했어요. 저장 공간을 확인해 주세요.") }
                onLoaded()
            } catch(e: CancellationException) {
                if(e !is TimeoutCancellationException) { mutable.update { it.copy(loading = false) }; throw e }
                offlineFallback(saved, cached, explicitSelection, preferredId, onLoaded)
            } catch(_: WorkspaceMismatch) {
                mutable.update { it.copy(loading = false, error = "선택한 여행과 응답이 일치하지 않아요. 다시 불러와 주세요.") }
            } catch(_: Exception) { offlineFallback(saved, cached, explicitSelection, preferredId, onLoaded) }
        }
    }
    private suspend fun offlineFallback(saved: TravelSnapshot?, cached: Workspace?, explicitSelection: Boolean, requestedId: String?, onLoaded: () -> Unit) {
        val current = mutable.value.workspace
        // A successful server write may be newer than the disk replica after a cache I/O failure.
        val available = if(explicitSelection) current?.takeIf { it.trip.id == requestedId } ?: cached
            else current ?: cached
        if(available != null) cacheSnapshot(saved?.trips ?: mutable.value.trips, available)
        mutable.update { it.copy(workspace = available ?: it.workspace,
            trips = saved?.trips?.takeIf { catalog -> catalog.isNotEmpty() } ?: it.trips,
            loading = false, offline = true,
            error = if(available != null) "오프라인이에요. 이 기기에 보관한 여행을 보고 있어요."
                else if(explicitSelection) "이 여행은 아직 기기에 보관되지 않았어요. 인터넷 연결 후 열어 주세요."
                else "여행을 불러오지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.") }
        if(available != null) onLoaded()
    }
    private fun performChange(success: String, scopeToTrip: Boolean = true, selectSavedTrip: Boolean = true,
        onSaved: () -> Unit = {}, request: suspend (String?) -> HttpResponse) {
        if(mutable.value.saving || mutable.value.loading) return
        if(mutable.value.offline) { mutable.update { it.copy(error = "인터넷 연결 후 저장할 수 있어요. 작성 중인 내용은 그대로 남아 있어요.") }; return }
        val tripId = mutable.value.workspace?.trip?.id
        if(scopeToTrip && tripId == null) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val response = request(tripId)
                if(!response.status.isSuccess()) {
                    val message = try { response.body<ApiError>().message.takeIf { it.isNotBlank() } }
                        catch(e: CancellationException) { throw e }
                        catch(e: Exception) { null }
                    mutable.update { it.copy(saving = false, error = message ?: "저장하지 못했어요. 입력과 연결을 확인하고 다시 시도해 주세요.") }
                    return@launch
                }
                val fresh = response.body<Workspace>()
                if(scopeToTrip) check(fresh.trip.id == tripId)
                val old = mutable.value
                val catalog = if(old.trips.any { it.id == fresh.trip.id })
                    old.trips.map { if(it.id == fresh.trip.id) fresh.trip else it } else old.trips + fresh.trip
                val workspace = if(selectSavedTrip || old.workspace?.trip?.id == fresh.trip.id) fresh else old.workspace
                // Store both an edited non-selected workspace and the currently selected one.
                val cachedOk = cacheSnapshot(catalog, fresh) && cacheSnapshot(catalog, workspace)
                mutable.update { it.copy(workspace = workspace, trips = catalog, saving = false, message = success,
                    error = if(cachedOk) null else "서버에는 저장했지만 기기 보관에 실패했어요. 연결된 상태에서 다시 불러와 주세요.") }
                onSaved()
            } catch(e: CancellationException) { mutable.update { it.copy(saving = false) }; throw e }
            catch(e: Exception) { mutable.update { it.copy(saving = false, error = "저장 결과를 확인하지 못했어요. 다시 불러와 저장 여부를 확인해 주세요.") } }
        }
    }
    private inline fun <reified T> mutate(path: String, payload: T, patch: Boolean = false, success: String, operationId: String? = null, delete: Boolean = false, scopeToTrip: Boolean = true, selectSavedTrip: Boolean = true, crossinline onSaved: () -> Unit = {}) =
        performChange(success, scopeToTrip, selectSavedTrip, { onSaved() }) { tripId ->
            client.request("$baseUrl/api/demo/$path") {
                method = if(delete) HttpMethod.Delete else if(patch) HttpMethod.Patch else HttpMethod.Post
                if(scopeToTrip) header("X-Trip-Id", tripId!!)
                if(operationId != null) header("Idempotency-Key", operationId)
                contentType(ContentType.Application.Json)
                if(!delete) setBody(payload)
            }
        }
    fun uploadDocument(picked: PickedDocument, name: String, reservationId: String, operationId: String, onSaved: () -> Unit) {
        if (picked.bytes.size !in 1..MAX_DOCUMENT_BYTES) { mutable.update { it.copy(error = "PDF·JPEG·PNG 파일을 20MB 이하로 선택해 주세요.") }; return }
        performChange("파일을 저장했어요", onSaved = onSaved) { tripId ->
            client.post("$baseUrl/api/demo/documents") {
                header("X-Trip-Id", tripId!!); header("Idempotency-Key", operationId)
                setBody(MultiPartFormDataContent(formData {
                    append("name", name); append("reservationId", reservationId)
                    append("file", picked.bytes, Headers.build {
                        append(HttpHeaders.ContentType, picked.mediaType)
                        // A canonical transport filename avoids multipart header injection. The display name is a separate part.
                        append(HttpHeaders.ContentDisposition, "filename=\"document.${documentExtension(picked.mediaType)}\"")
                    })
                }))
            }
        }
    }
    fun updateDocument(id: String, name: String, reservationId: String, onSaved: () -> Unit) = mutate("documents/$id", DocumentUpdateInput(name, reservationId), true, "파일 정보를 저장했어요", onSaved = onSaved)
    fun deleteDocument(id: String, onSaved: () -> Unit) = mutate("documents/$id", Unit, success = "파일을 삭제했어요", delete = true, onSaved = onSaved)
    fun addTrip(input: TripInput, operationId: String, onSaved: () -> Unit) = mutate("trips", input, success = "여행을 만들었어요", operationId = operationId, scopeToTrip = false, onSaved = onSaved)
    fun updateTrip(id: String, input: TripInput, onSaved: () -> Unit) = mutate("trips/$id", input, true, "여행을 수정했어요", scopeToTrip = false, selectSavedTrip = false, onSaved = onSaved)
    fun archiveTrip(id: String, archived: Boolean, onSaved: () -> Unit) = mutate("trips/$id/archive", ArchiveChange(archived), true, if(archived) "여행을 보관했어요" else "여행을 꺼냈어요", scopeToTrip = false, selectSavedTrip = false, onSaved = onSaved)
    fun deleteTrip(id: String, onSaved: () -> Unit) {
        if(mutable.value.saving || mutable.value.loading) return
        if(mutable.value.offline) { mutable.update { it.copy(error = "인터넷 연결 후 삭제할 수 있어요.") }; return }
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
                val remaining = mutable.value.workspace?.takeIf { it.trip.id != id }
                val cachedOk = cacheSnapshot(trips, remaining)
                mutable.update { it.copy(trips = trips, workspace = remaining, saving = false, message = "여행을 삭제했어요",
                    error = if(cachedOk) null else "여행은 삭제했지만 기기 보관 목록을 갱신하지 못했어요.") }
                onSaved()
                if(mutable.value.workspace == null && trips.isNotEmpty()) refresh()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving = false, error = "삭제 결과를 확인하지 못했어요. 다시 시도해 주세요.") } }
        }
    }
    fun addReservation(input: ReservationInput, operationId: String, onSaved: () -> Unit) = mutate("reservations", input, success = "예약을 저장했어요", operationId = operationId, onSaved = onSaved)
    fun updateReservation(id: String, input: ReservationInput, onSaved: () -> Unit) = mutate("reservations/$id", input, true, "예약을 수정했어요", onSaved = onSaved)
    fun deleteReservation(id: String, onSaved: () -> Unit) = mutate("reservations/$id", Unit, success = "예약 기록을 삭제했어요", delete = true, onSaved = onSaved)
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
