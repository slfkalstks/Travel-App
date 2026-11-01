package com.travel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.*
import com.travel.app.platform.HandleBack
import kotlin.random.Random

@Composable
fun ReservationEditorScreen(route: String, state: TravelState, store: TravelStore, onDismiss: () -> Unit) {
    val editing = route.startsWith("reservation-edit|")
    val reservation = if (editing) state.workspace?.reservations?.firstOrNull { it.id == route.substringAfter('|') } else null
    val busy = state.saving || state.loading
    var picker by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val dismiss = { if (!busy) onDismiss() }
    HandleBack(true) { if (!busy) { if (picker != null) picker = null else onDismiss() } }
    if (editing && reservation == null) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (state.loading) "예약을 불러오고 있어요" else "예약을 찾을 수 없어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                ActionButton("닫기", dismiss, enabled = !busy)
            }
        }
        return
    }
    var type by rememberSaveable(route) { mutableStateOf(reservation?.type ?: "flight") }
    var title by rememberSaveable(route) { mutableStateOf(reservation?.title.orEmpty()) }
    var date by rememberSaveable(route) { mutableStateOf(reservation?.date ?: state.workspace?.trip?.startDate.orEmpty()) }
    var time by rememberSaveable(route) { mutableStateOf(reservation?.time.orEmpty()) }
    var timeUnknown by rememberSaveable(route) { mutableStateOf(reservation?.time.isNullOrBlank()) }
    var timeZone by rememberSaveable(route) { mutableStateOf(reservation?.timeZone.orEmpty()) }
    var utcOffset by rememberSaveable(route) { mutableStateOf(reservation?.utcOffset.orEmpty()) }
    var endDate by rememberSaveable(route) { mutableStateOf(reservation?.endDate.orEmpty()) }
    var endTime by rememberSaveable(route) { mutableStateOf(reservation?.endTime.orEmpty()) }
    var endTimeUnknown by rememberSaveable(route) { mutableStateOf(reservation?.endTime.isNullOrBlank()) }
    var endTimeZone by rememberSaveable(route) { mutableStateOf(reservation?.endTimeZone.orEmpty()) }
    var endUtcOffset by rememberSaveable(route) { mutableStateOf(reservation?.endUtcOffset.orEmpty()) }
    var location by rememberSaveable(route) { mutableStateOf(reservation?.location.orEmpty()) }
    var endLocation by rememberSaveable(route) { mutableStateOf(reservation?.endLocation.orEmpty()) }
    var reference by rememberSaveable(route) { mutableStateOf(reservation?.reference.orEmpty()) }
    var provider by rememberSaveable(route) { mutableStateOf(reservation?.provider.orEmpty()) }
    var status by rememberSaveable(route) { mutableStateOf(reservation?.status ?: "planned") }
    var detail by rememberSaveable(route) { mutableStateOf(reservation?.detail.orEmpty()) }
    var cancellationEnabled by rememberSaveable(route) { mutableStateOf(!reservation?.cancellationDeadline.isNullOrBlank()) }
    var cancellationDate by rememberSaveable(route) { mutableStateOf(reservation?.cancellationDeadline?.substringBefore('T').orEmpty()) }
    var cancellationTime by rememberSaveable(route) { mutableStateOf(reservation?.cancellationDeadline?.substringAfter('T', "").orEmpty()) }
    var cancellationTimeZone by rememberSaveable(route) { mutableStateOf(reservation?.cancellationTimeZone.orEmpty()) }
    var cancellationUtcOffset by rememberSaveable(route) { mutableStateOf(reservation?.cancellationUtcOffset.orEmpty()) }
    var cancellationPolicy by rememberSaveable(route) { mutableStateOf(reservation?.cancellationPolicy.orEmpty()) }
    var invalid by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var validationMessage by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val formScroll = rememberScrollState()
    val operationId = rememberSaveable(route) { Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } }
    val fieldIds = listOf("title", "date", "time", "timeZone", "endDate", "endTime", "endTimeZone", "location", "endLocation", "reference", "provider", "detail", "cancellationDate", "cancellationTime", "cancellationTimeZone", "cancellationPolicy")
    val requesters = remember { fieldIds.associateWith { FocusRequester() } }
    fun clear(field: String) { if (invalid == field) { invalid = null; validationMessage = null }; store.clearError() }
    fun reject(field: String, message: String) { invalid = field; validationMessage = message; requesters[field]?.requestFocus() }
    fun dateValid(value: String) = isIsoDate(value) && (value.take(4).toIntOrNull() ?: 0) in 1900..2100
    fun offsets(d: String, t: String, z: String) = runCatching { validReservationOffsets(d, t, z) }.getOrDefault(emptyList())
    fun validateClock(d: String, t: String, z: String, o: String, timeKey: String, zoneKey: String): Boolean {
        if (!Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(t)) { reject(timeKey, "시각을 HH:mm 형식으로 입력해 주세요"); return false }
        if (z.isBlank()) { reject(zoneKey, "현지 시각의 시간대를 선택해 주세요"); return false }
        val options = runCatching { validReservationOffsets(d, t, z) }.getOrNull()
        if (options == null) { reject(zoneKey, "사용할 수 있는 시간대를 다시 선택해 주세요"); return false }
        if (options.isEmpty()) { reject(timeKey, "서머타임 변경으로 존재하지 않는 시각이에요. 예약에 적힌 시각을 확인해 주세요"); return false }
        if (options.size > 1 && o !in options) { reject(timeKey, "이 시각이 두 번 있어요. 아래에서 적용할 UTC 차이를 선택해 주세요"); return false }
        return true
    }
    if (picker != null) {
        val selected = when (picker) { "endTimeZone" -> endTimeZone; "cancellationTimeZone" -> cancellationTimeZone; else -> timeZone }
        TimeZonePickerScreen(selected, !busy, onSelected = {
            when (picker) {
                "endTimeZone" -> { endTimeZone = it; endUtcOffset = "" }
                "cancellationTimeZone" -> { cancellationTimeZone = it; cancellationUtcOffset = "" }
                else -> { timeZone = it; utcOffset = "" }
            }
            clear(picker.orEmpty()); picker = null
        }, onDismiss = { if (!busy) picker = null })
        return
    }
    val moving = type == "flight" || type == "transport"
    val startLabel = when (type) { "stay" -> "체크인"; "activity" -> "시작"; else -> "출발" }
    val endLabel = when (type) { "stay" -> "체크아웃"; "activity" -> "종료"; else -> "도착" }
    val trip = state.workspace?.trip
    val outsideTrip = trip != null && ((dateValid(date) && (date < trip.startDate || date > trip.endDate)) || (dateValid(endDate) && (endDate < trip.startDate || endDate > trip.endDate)))
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "닫기") }
                Text(if (editing) "예약 수정" else "예약 추가", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(formScroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("예약 종류")
                    val types = listOf("flight", "stay", "transport", "activity")
                    Tabs(types.map(::reservationTypeName), types.indexOf(type).coerceAtLeast(0), !busy) { type = types[it]; clear("type") }
                }
                ReservationField("예약 이름", title, { title = it; clear("title") }, "title", requesters, invalid, validationMessage, !busy, "예약 내역에 적힌 이름", limit = 120)
                if (moving) {
                    ReservationField("출발 장소", location, { location = it; clear("location") }, "location", requesters, invalid, validationMessage, !busy, "공항·역·터미널", limit = 200)
                    ReservationField("도착 장소", endLocation, { endLocation = it; clear("endLocation") }, "endLocation", requesters, invalid, validationMessage, !busy, "공항·역·터미널", limit = 200)
                } else ReservationField(if (type == "stay") "숙소 주소" else "장소", location, { location = it; clear("location") }, "location", requesters, invalid, validationMessage, !busy, limit = 200)
                ReservationSectionTitle("$startLabel 일시")
                CalendarInput("$startLabel 날짜", date, { date = it; utcOffset = ""; clear("date") }, Modifier.focusRequester(requesters.getValue("date")), error = if (invalid == "date") validationMessage else null, enabled = !busy)
                ReservationUnknownSwitch("$startLabel 시각 미정", timeUnknown, !busy) { timeUnknown = it; clear("time"); clear("timeZone") }
                if (!timeUnknown) {
                    ReservationField("$startLabel 시각", time, { time = it; utcOffset = ""; clear("time") }, "time", requesters, invalid, validationMessage, !busy, "HH:mm")
                    ReservationZoneField("$startLabel 시간대", timeZone, "timeZone", requesters, invalid, validationMessage, !busy) { picker = "timeZone" }
                    ReservationOffsetChooser(date, time, timeZone, utcOffset, !busy) { utcOffset = it; clear("time") }
                }
                ReservationSectionTitle("$endLabel 일시${if (type == "stay") "" else " · 선택"}")
                CalendarInput("$endLabel 날짜${if (type == "stay") " · 필수" else ""}", endDate, { endDate = it; endUtcOffset = ""; clear("endDate") }, Modifier.focusRequester(requesters.getValue("endDate")), error = if (invalid == "endDate") validationMessage else null, enabled = !busy)
                ReservationUnknownSwitch("$endLabel 시각 미정", endTimeUnknown, !busy) { endTimeUnknown = it; clear("endTime"); clear("endTimeZone") }
                if (!endTimeUnknown) {
                    ReservationField("$endLabel 시각", endTime, { endTime = it; endUtcOffset = ""; clear("endTime") }, "endTime", requesters, invalid, validationMessage, !busy, "HH:mm")
                    ReservationZoneField("$endLabel 시간대", endTimeZone, "endTimeZone", requesters, invalid, validationMessage, !busy) { picker = "endTimeZone" }
                    ReservationOffsetChooser(endDate, endTime, endTimeZone, endUtcOffset, !busy) { endUtcOffset = it; clear("endTime") }
                }
                if (outsideTrip) Group { Text("여행 기간 밖의 예약이에요", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("여행 전후의 이동·숙박도 그대로 저장할 수 있어요.", fontSize = 14.sp, color = TextSecondary) }
                Line()
                ReservationSectionTitle("예약 정보")
                ReservationField("예약처·운영사 · 선택", provider, { provider = it; clear("provider") }, "provider", requesters, invalid, validationMessage, !busy, limit = 120)
                ReservationField("예약번호 · 선택", reference, { reference = it; clear("reference") }, "reference", requesters, invalid, validationMessage, !busy, limit = 120)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("직접 기록한 예약 상태")
                    val statuses = listOf("planned", "confirmed", "cancelled")
                    Tabs(statuses.map(::reservationStatusName), statuses.indexOf(status).coerceAtLeast(0), !busy) { status = statuses[it]; clear("status") }
                }
                ReservationField("메모 · 선택", detail, { detail = it; clear("detail") }, "detail", requesters, invalid, validationMessage, !busy, "탑승·입장 안내, 챙길 것 등을 적어두세요", limit = 4000, multiline = true)
                Line()
                ReservationSectionTitle("취소·환불 정보 · 선택")
                ReservationUnknownSwitch("취소 마감 기록", cancellationEnabled, !busy) { cancellationEnabled = it; clear("cancellationDate"); clear("cancellationTime"); clear("cancellationTimeZone") }
                if (cancellationEnabled) {
                    CalendarInput("취소 마감 날짜", cancellationDate, { cancellationDate = it; cancellationUtcOffset = ""; clear("cancellationDate") }, Modifier.focusRequester(requesters.getValue("cancellationDate")), error = if (invalid == "cancellationDate") validationMessage else null, enabled = !busy)
                    ReservationField("취소 마감 시각", cancellationTime, { cancellationTime = it; cancellationUtcOffset = ""; clear("cancellationTime") }, "cancellationTime", requesters, invalid, validationMessage, !busy, "HH:mm")
                    ReservationZoneField("취소 마감 시간대", cancellationTimeZone, "cancellationTimeZone", requesters, invalid, validationMessage, !busy) { picker = "cancellationTimeZone" }
                    ReservationOffsetChooser(cancellationDate, cancellationTime, cancellationTimeZone, cancellationUtcOffset, !busy) { cancellationUtcOffset = it; clear("cancellationTime") }
                }
                ReservationField("취소·환불 규정 메모", cancellationPolicy, { cancellationPolicy = it; clear("cancellationPolicy") }, "cancellationPolicy", requesters, invalid, validationMessage, !busy, "예약처에서 확인한 조건을 적어두세요", limit = 2000, multiline = true)
                if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                ActionButton(if (editing) "변경 저장" else "예약 저장", onClick = {
                    val lengths = listOf(Triple("title", title, 120), Triple("location", location, 200), Triple("endLocation", if (moving) endLocation else "", 200), Triple("provider", provider, 120), Triple("reference", reference, 120), Triple("detail", detail, 4000), Triple("cancellationPolicy", cancellationPolicy, 2000))
                    if (title.isBlank()) { reject("title", "예약 이름을 입력해 주세요"); return@ActionButton }
                    lengths.firstOrNull { it.second.length > it.third }?.let { reject(it.first, "${it.third}자까지 입력할 수 있어요"); return@ActionButton }
                    if (!dateValid(date)) { reject("date", "1900~2100년의 날짜를 YYYY-MM-DD 형식으로 입력해 주세요"); return@ActionButton }
                    if (type == "stay" && endDate.isBlank()) { reject("endDate", "체크아웃 날짜를 입력해 주세요"); return@ActionButton }
                    if (endDate.isNotBlank() && !dateValid(endDate)) { reject("endDate", "1900~2100년의 날짜를 YYYY-MM-DD 형식으로 입력해 주세요"); return@ActionButton }
                    if (!timeUnknown && !validateClock(date, time, timeZone, utcOffset, "time", "timeZone")) return@ActionButton
                    if (!endTimeUnknown && endDate.isBlank()) { reject("endDate", "$endLabel 시각을 저장하려면 날짜를 입력해 주세요"); return@ActionButton }
                    if (!endTimeUnknown && !validateClock(endDate, endTime, endTimeZone, endUtcOffset, "endTime", "endTimeZone")) return@ActionButton
                    val startOffset = if (timeUnknown) "" else offsets(date, time, timeZone).singleOrNull() ?: utcOffset
                    val finishOffset = if (endTimeUnknown) "" else offsets(endDate, endTime, endTimeZone).singleOrNull() ?: endUtcOffset
                    if (!timeUnknown && !endTimeUnknown) {
                        val start = runCatching { reservationInstant(date, time, timeZone, startOffset) }.getOrNull()
                        val end = runCatching { reservationInstant(endDate, endTime, endTimeZone, finishOffset) }.getOrNull()
                        if (start == null || end == null || end <= start) { reject("endTime", "$endLabel 일시는 $startLabel 일시보다 늦어야 해요. 각 지역의 날짜와 시간대를 확인해 주세요"); return@ActionButton }
                    } else if (endDate.isNotBlank() && endDate < date) { reject("endDate", "$endLabel 날짜는 $startLabel 날짜보다 빠를 수 없어요. 날짜가 달라지는 이동이라면 양쪽 시각과 시간대를 입력해 주세요"); return@ActionButton }
                    if (cancellationEnabled) {
                        if (!dateValid(cancellationDate)) { reject("cancellationDate", "1900~2100년의 날짜를 YYYY-MM-DD 형식으로 입력해 주세요"); return@ActionButton }
                        if (!validateClock(cancellationDate, cancellationTime, cancellationTimeZone, cancellationUtcOffset, "cancellationTime", "cancellationTimeZone")) return@ActionButton
                    }
                    val input = ReservationInput(type = type, title = title.trim(), date = date, time = if (timeUnknown) "" else time,
                        reference = reference.trim(), detail = detail, status = status, location = location.trim(), endLocation = endLocation.trim(), provider = provider.trim(),
                        endDate = endDate, endTime = if (endTimeUnknown) "" else endTime, timeZone = if (timeUnknown) "" else timeZone, endTimeZone = if (endTimeUnknown) "" else endTimeZone,
                        cancellationDeadline = if (cancellationEnabled) "${cancellationDate}T$cancellationTime" else "", cancellationTimeZone = if (cancellationEnabled) cancellationTimeZone else "", cancellationPolicy = cancellationPolicy,
                        utcOffset = startOffset, endUtcOffset = finishOffset, cancellationUtcOffset = if (cancellationEnabled) offsets(cancellationDate, cancellationTime, cancellationTimeZone).singleOrNull() ?: cancellationUtcOffset else "")
                    store.clearError()
                    if (reservation == null) store.addReservation(input, operationId, onDismiss) else store.updateReservation(reservation.id, input, onDismiss)
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy, busy = state.saving, blue = true)
            }
        }
    }
}

@Composable private fun ReservationSectionTitle(text: String) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold) }

@Composable private fun ReservationField(label: String, value: String, onChange: (String) -> Unit, id: String, requesters: Map<String, FocusRequester>, invalid: String?, error: String?, enabled: Boolean, hint: String = "", limit: Int? = null, multiline: Boolean = false) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(id)), enabled = enabled,
        label = { Text(label) }, placeholder = { if (hint.isNotBlank()) Text(hint) }, singleLine = !multiline,
        minLines = if (multiline) 4 else 1, maxLines = if (multiline) 10 else 1,
        isError = invalid == id || (limit != null && value.length > limit), shape = RoundedCornerShape(18.dp),
        supportingText = if (invalid == id || limit != null) { { Text(if (invalid == id) error.orEmpty() else if (limit != null && value.length > limit) "${limit}자까지 입력할 수 있어요" else "${value.length} / $limit") } } else null)
}

@Composable private fun ReservationUnknownSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable private fun ReservationZoneField(label: String, value: String, id: String, requesters: Map<String, FocusRequester>, invalid: String?, error: String?, enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(requesters.getValue(id)), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, fontSize = 12.sp, color = TextSecondary); Text(if (value.isBlank()) "도시·지역 선택" else zoneDisplayName(value), fontSize = 15.sp) }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
        }
        if (invalid == id) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

@Composable private fun ReservationOffsetChooser(date: String, time: String, zone: String, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val options = remember(date, time, zone) { runCatching { validReservationOffsets(date, time, zone) }.getOrNull() }
    if (options != null && options.isEmpty()) Text("서머타임 변경으로 존재하지 않는 시각이에요.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    if (options != null && options.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("서머타임 변경으로 이 시각이 두 번 있어요. 예약에 해당하는 UTC 차이를 선택해 주세요.", fontSize = 13.sp, color = TextSecondary)
            options.forEachIndexed { index, offset ->
                OutlinedButton(onClick = { onSelect(offset) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    RadioButton(selected == offset, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp)); Text("${if (index == 0) "첫 번째" else "두 번째"} $time · ${reservationOffsetLabel(offset)}")
                }
            }
        }
    }
}

internal fun reservationTypeName(type: String): String = when (type) { "flight" -> "항공"; "stay" -> "숙소"; "transport" -> "교통"; "activity" -> "활동"; else -> "예약" }
internal fun reservationStatusName(status: String): String = when (status) { "confirmed" -> "확정"; "cancelled" -> "취소"; else -> "계획" }
internal fun reservationOffsetLabel(offset: String): String = if (offset == "Z" || offset == "+00:00") "UTC+00:00" else "UTC$offset"
