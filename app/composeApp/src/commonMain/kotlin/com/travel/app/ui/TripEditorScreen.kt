package com.travel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.*
import com.travel.app.platform.HandleBack
import kotlin.random.Random
import kotlin.time.Instant

@Composable
fun TripEditorScreen(route: String, state: TravelState, store: TravelStore, onDismiss: () -> Unit) {
    val editing = route.startsWith("trip-edit|")
    val id = route.substringAfter('|', "")
    val trip = if (editing) state.trips.firstOrNull { it.id == id }
        ?: state.workspace?.trip?.takeIf { it.id == id } else null
    val busy = state.loading || state.saving
    var confirmation by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var managementMenuExpanded by remember(route) { mutableStateOf(false) }
    var destinationPicker by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val dismiss = { if (!state.saving) onDismiss() }
    HandleBack(true) {
        if (!state.saving) {
            if (destinationPicker != null) destinationPicker = null
            else if (managementMenuExpanded) managementMenuExpanded = false
            else if (confirmation != null) confirmation = null else onDismiss()
        }
    }
    if (editing && trip == null) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (state.loading) "여행을 불러오고 있어요" else "여행을 찾을 수 없어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (state.loading) CircularProgressIndicator(color = Accent)
                ActionButton("닫기", dismiss, enabled = !state.saving)
            }
        }
        return
    }

    var title by rememberSaveable(route) { mutableStateOf(trip?.title.orEmpty()) }
    var destination by rememberSaveable(route) { mutableStateOf(trip?.destination.orEmpty()) }
    var country by rememberSaveable(route) { mutableStateOf(trip?.country.orEmpty()) }
    var countryCode by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var destinationId by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var startDate by rememberSaveable(route) { mutableStateOf(trip?.startDate.orEmpty()) }
    var endDate by rememberSaveable(route) { mutableStateOf(trip?.endDate.orEmpty()) }
    var travelers by rememberSaveable(route) { mutableStateOf(trip?.travelers?.toString() ?: "1") }
    var budget by rememberSaveable(route) { mutableStateOf(trip?.budgetKrw?.toString() ?: "0") }
    var invalid by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var validationMessage by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val requesters = remember { listOf("title", "destination", "country", "startDate", "endDate", "travelers", "budget").associateWith { FocusRequester() } }
    val operationId = rememberSaveable(route) {
        Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    val changed = trip == null || title.trim() != trip.title || destination.trim() != trip.destination ||
        country.trim() != trip.country || startDate != trip.startDate || endDate != trip.endDate ||
        travelers.toIntOrNull() != trip.travelers || budget.toLongOrNull() != trip.budgetKrw
    fun clearError(field: String) {
        if (invalid == field) { invalid = null; validationMessage = null }
        store.clearError()
    }
    fun reject(field: String, message: String) {
        invalid = field
        validationMessage = message
        requesters.getValue(field).requestFocus()
    }
    fun dateValid(value: String) = isIsoDate(value) && (value.take(4).toIntOrNull() ?: 0) in 1900..2100

    // Keep every form state above this branch so opening the picker preserves the draft.
    if (destinationPicker != null) {
        DestinationPickerScreen(
            initialCountryCode = countryCode,
            initialCountryValue = country,
            selectedPlaceId = destinationId,
            selectedPlaceValue = destination,
            startWithCountries = destinationPicker == "country",
            enabled = !busy,
            onCountrySelected = { selected, changedCountry ->
                country = selected.name
                countryCode = selected.code
                clearError("country")
                if (changedCountry) {
                    destination = ""
                    destinationId = null
                    clearError("destination")
                }
            },
            onPlaceSelected = { selected ->
                destination = selected.name
                destinationId = selected.id
                clearError("destination")
                destinationPicker = null
            },
            onCustomPlace = { name ->
                destination = name
                destinationId = null
                clearError("destination")
                destinationPicker = null
            },
            onDismiss = { if (!state.saving) destinationPicker = null },
        )
        return
    }

    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = dismiss, enabled = !state.saving) { Icon(Icons.Outlined.Close, "닫기") }
                Text(if (editing) "여행 수정" else "새 여행", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (editing && trip != null) {
                    Box {
                        IconButton(onClick = { managementMenuExpanded = true }, enabled = !busy) {
                            Icon(Icons.Outlined.MoreVert, "여행 관리")
                        }
                        DropdownMenu(expanded = managementMenuExpanded, onDismissRequest = { managementMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (trip.archived) "보관 해제" else "보관") },
                                leadingIcon = { Icon(if (trip.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                                enabled = !busy,
                                onClick = { managementMenuExpanded = false; store.clearError(); confirmation = "archive" },
                            )
                            DropdownMenuItem(
                                text = { Text("여행 삭제", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                enabled = !busy,
                                onClick = { managementMenuExpanded = false; store.clearError(); confirmation = "delete" },
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TripField("여행 이름", title, { title = it; clearError("title") }, "title", requesters, invalid, validationMessage, !busy, "예: 오사카 여행")
                DestinationSelectorField("국가", country, "국가 선택", "country", requesters, invalid, validationMessage, !busy) { destinationPicker = "country" }
                DestinationSelectorField("도시·지역", destination, "도시·지역 선택", "destination", requesters, invalid, validationMessage, !busy) { destinationPicker = "place" }
                CalendarInput("시작일", startDate, { startDate = it; clearError("startDate") }, Modifier.focusRequester(requesters.getValue("startDate")), if (invalid == "startDate") validationMessage else null, enabled = !busy)
                CalendarInput("종료일", endDate, { endDate = it; clearError("endDate") }, Modifier.focusRequester(requesters.getValue("endDate")), if (invalid == "endDate") validationMessage else null, enabled = !busy)
                TripField("여행 인원", travelers, { travelers = it; clearError("travelers") }, "travelers", requesters, invalid, validationMessage, !busy, "1명부터 20명", KeyboardType.Number)
                TripField("총예산 (원)", budget, { budget = it; clearError("budget") }, "budget", requesters, invalid, validationMessage, !busy, "예산을 정하지 않았다면 0", KeyboardType.Number)
                if (state.error != null && confirmation == null) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                ActionButton(if (editing) "변경 사항 저장" else "여행 만들기", modifier = Modifier.fillMaxWidth(), enabled = !busy && changed, busy = state.saving, blue = true, onClick = {
                    invalid = null; validationMessage = null; store.clearError()
                    when {
                        title.isBlank() -> reject("title", "여행 이름을 입력해 주세요")
                        title.trim().length > 120 -> reject("title", "여행 이름은 120자까지 입력할 수 있어요")
                        country.isBlank() -> reject("country", "국가를 선택해 주세요")
                        country.trim().length > 80 -> reject("country", "국가는 80자까지 입력할 수 있어요")
                        destination.isBlank() -> reject("destination", "도시나 지역을 선택해 주세요")
                        destination.trim().length > 80 -> reject("destination", "도시·지역은 80자까지 입력할 수 있어요")
                        !dateValid(startDate) -> reject("startDate", "1900~2100년의 날짜를 YYYY-MM-DD 형식으로 입력해 주세요")
                        !dateValid(endDate) -> reject("endDate", "1900~2100년의 날짜를 YYYY-MM-DD 형식으로 입력해 주세요")
                        endDate < startDate -> reject("endDate", "종료일은 시작일과 같거나 이후여야 해요")
                        (Instant.parse(endDate + "T00:00:00Z") - Instant.parse(startDate + "T00:00:00Z")).inWholeDays >= 366 -> reject("endDate", "여행 기간은 시작일을 포함해 366일까지 설정할 수 있어요")
                        (travelers.toIntOrNull() ?: 0) !in 1..20 -> reject("travelers", "여행 인원은 1명부터 20명까지 입력해 주세요")
                        (budget.toLongOrNull() ?: -1L) !in 0L..1_000_000_000L -> reject("budget", "예산은 0원부터 10억 원까지 정수로 입력해 주세요")
                        else -> {
                            val input = TripInput(title = title.trim(), destination = destination.trim(), country = country.trim(), startDate = startDate, endDate = endDate, travelers = travelers.toInt(), budgetKrw = budget.toLong())
                            if (trip == null) store.addTrip(input, operationId, onDismiss) else store.updateTrip(trip.id, input, onDismiss)
                        }
                    }
                })
            }
        }
    }
    if (confirmation != null && trip != null) {
        val deleting = confirmation == "delete"
        val matchingWorkspace = state.workspace?.takeIf { it.trip.id == trip.id }
        AlertDialog(
            onDismissRequest = { if (!state.saving) confirmation = null },
            title = { Text(if (deleting) "여행을 삭제할까요?" else if (trip.archived) "보관을 해제할까요?" else "여행을 보관할까요?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(trip.title, fontWeight = FontWeight.SemiBold)
                    Text("${trip.startDate} — ${trip.endDate}", color = TextSecondary)
                    if (deleting) {
                        Text("이 여행의 일정·준비물·지출·예약·저장 장소·기록이 함께 삭제돼요. 삭제한 내용은 되돌릴 수 없어요.")
                        if (matchingWorkspace != null) Text("일정 ${matchingWorkspace.events.size}개 · 준비물 ${matchingWorkspace.tasks.size}개 · 지출 ${matchingWorkspace.expenses.size}개 · 예약 ${matchingWorkspace.reservations.size}개 · 장소 ${matchingWorkspace.favorites.size}개 · 기록 ${matchingWorkspace.journals.size}개", fontSize = 14.sp, color = TextSecondary)
                    } else {
                        Text(if (trip.archived) "여행을 보관 목록에서 꺼냅니다. 저장된 일정과 기록은 그대로 유지돼요." else "여행을 보관 목록으로 옮깁니다. 일정과 기록은 삭제되지 않아요.")
                        if (changed) Text("이 화면에서 입력한 변경 사항은 저장되지 않아요. 변경 내용을 남기려면 취소한 뒤 먼저 저장해 주세요.", fontSize = 14.sp, color = TextSecondary)
                    }
                    if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = if (deleting) MaterialTheme.colorScheme.error else Accent), onClick = {
                    store.clearError()
                    val finish = { confirmation = null; onDismiss() }
                    if (deleting) store.deleteTrip(trip.id, finish) else store.archiveTrip(trip.id, !trip.archived, finish)
                }) { Text(if (state.saving) "처리 중…" else if (deleting) "여행 삭제" else if (trip.archived) "보관 해제" else "보관") }
            },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmation = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun TripField(label: String, value: String, onChange: (String) -> Unit, id: String, requesters: Map<String, FocusRequester>, invalid: String?, error: String?, enabled: Boolean, hint: String = "", keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(id)), enabled = enabled, label = { Text(label) }, placeholder = { Text(hint) }, singleLine = true, isError = invalid == id, supportingText = if (invalid == id) {{ Text(error.orEmpty()) }} else null, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(18.dp))
}


@Composable
private fun DestinationSelectorField(
    label: String,
    value: String,
    hint: String,
    id: String,
    requesters: Map<String, FocusRequester>,
    invalid: String?,
    error: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val isError = invalid == id
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(id)).semantics(mergeDescendants = true) { role = Role.Button },
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
            colors = CardDefaults.outlinedCardColors(containerColor = Canvas),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, fontSize = 12.sp, color = if (isError) MaterialTheme.colorScheme.error else TextSecondary)
                    Text(value.ifBlank { hint }, fontSize = 16.sp, color = if (value.isBlank() || !enabled) TextSecondary else TextPrimary)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextSecondary)
            }
        }
        if (isError) Text(error.orEmpty(), Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}
