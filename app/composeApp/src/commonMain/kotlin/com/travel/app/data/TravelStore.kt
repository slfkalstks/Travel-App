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

 data class TravelState(val workspace: Workspace? = null, val loading: Boolean = true, val saving: Boolean = false, val error: String? = null, val message: String? = null)
class TravelStore(
    private val scope: CoroutineScope,
    private val client: HttpClient = createHttpClient(),
    private val baseUrl: String = demoBaseUrl()
) {
    private val mutable = MutableStateFlow(TravelState())
    val state = mutable.asStateFlow()
    fun close() = client.close()
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private var refreshJob: Job? = null
    fun refresh() {
        if(mutable.value.saving || refreshJob?.isActive == true) return
        // Close the scheduling gap before launch so a same-frame mutation cannot race the GET.
        mutable.update { it.copy(loading = true, error = null) }
        refreshJob = scope.launch {
            try {
                val response = client.get("$baseUrl/api/demo/workspace")
                check(response.status.isSuccess())
                val fresh = response.body<Workspace>()
                mutable.update { it.copy(workspace = fresh, loading = false) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(loading = false, error = "여행을 불러오지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.") } }
        }
    }
    private inline fun <reified T> mutate(path: String, payload: T, patch: Boolean = false, success: String, operationId: String? = null, crossinline onSaved: () -> Unit = {}) {
        if(mutable.value.saving || mutable.value.loading) return
        mutable.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val response = client.request("$baseUrl/api/demo/$path") {
                    method = if(patch) HttpMethod.Patch else HttpMethod.Post
                    if(operationId != null) header("Idempotency-Key", operationId)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                if(!response.status.isSuccess()) {
                    val message = try { response.body<ApiError>().message.takeIf { it.isNotBlank() } }
                        catch(e: CancellationException) { throw e }
                        catch(e: Exception) { null }
                    mutable.update { it.copy(saving = false, error = message ?: "저장하지 못했어요. 입력과 연결을 확인하고 다시 시도해 주세요.") }
                    return@launch
                }
                val fresh = response.body<Workspace>()
                mutable.update { it.copy(workspace = fresh, saving = false, message = success) }
                onSaved()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(saving = false, error = "저장 결과를 확인하지 못했어요. 다시 불러와 저장 여부를 확인해 주세요.") } }
        }
    }
    fun updateEventNote(id: String, note: String, onSaved: () -> Unit) = mutate("events/$id/note", EventNoteChange(note), true, "일정 메모를 저장했어요", onSaved = onSaved)
    fun toggleTask(task: TravelTask) = mutate("tasks/${task.id}", TaskChange(!task.done), true, "준비 상태를 저장했어요")
    fun toggleFavorite(favorite: Favorite) = mutate("favorites/${favorite.id}", FavoriteChange(!favorite.saved), true, if(favorite.saved) "저장한 장소에서 제외했어요" else "가고 싶은 곳에 저장했어요")
    fun addExpense(input: ExpenseInput, operationId: String, onSaved: () -> Unit) = mutate("expenses", input, success = "지출을 기록했어요", operationId = operationId, onSaved = onSaved)
    fun addEvent(input: EventInput, operationId: String, onSaved: () -> Unit) = mutate("events", input, success = "일정을 추가했어요", operationId = operationId, onSaved = onSaved)
    fun addJournal(input: JournalInput, operationId: String, onSaved: () -> Unit) = mutate("journals", input, success = "여행 노트를 저장했어요", operationId = operationId, onSaved = onSaved)
}
