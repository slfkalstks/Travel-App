package com.travel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DocumentDetailScreen(
    document: DocumentRecord?,
    reservations: List<Reservation>,
    cacheStatus: DocumentCacheStatus,
    busy: Boolean,
    error: String?,
    offline: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onUpdate: (name: String, reservationId: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(document?.id) { mutableStateOf(document?.name.orEmpty()) }
    var reservationId by rememberSaveable(document?.id) { mutableStateOf(document?.reservationId.orEmpty()) }
    var nameError by rememberSaveable(document?.id) { mutableStateOf<String?>(null) }
    var showReservations by rememberSaveable(document?.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(document?.id) { mutableStateOf(false) }
    var deleteAttempted by rememberSaveable(document?.id) { mutableStateOf(false) }
    var showDiscard by rememberSaveable(document?.id) { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val changed = document != null && (name.trim() != document.name || reservationId != document.reservationId)
    val dismiss = { if (!busy) { if (changed) showDiscard = true else onDismiss() } }
    HandleBack(true) {
        if (!busy) when {
            showReservations -> showReservations = false
            showDelete -> showDelete = false
            showDiscard -> showDiscard = false
            else -> dismiss()
        }
    }
    if (document == null) {
        DocumentFrame("서류 상세", busy, onDismiss) {
            EmptyState("서류를 찾을 수 없어요", "서류 목록으로 돌아가 다시 확인해 주세요")
            if (error != null) DocumentError(error)
            ActionButton("목록으로 돌아가기", onDismiss, enabled = !busy)
        }
        return
    }
    DocumentFrame("서류 상세", busy, dismiss, bottom = {
        ActionButton(
            "변경 저장", onClick = {
                nameError = documentNameError(name)
                if (nameError != null) nameFocus.requestFocus() else onUpdate(name.trim(), reservationId)
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !offline && changed, busy = busy,
        )
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(document.name, fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
            Text("${documentTypeLabel(document.mediaType, document.name)} · ${documentSizeLabel(document.sizeBytes)}", fontSize = 14.sp, color = TextSecondary)
            documentCreatedLabel(document.createdAt)?.let { Text(it, fontSize = 13.sp, color = TextSecondary) }
        }
        Group {
            DocumentCacheLabel(cacheStatus)
            Spacer(Modifier.height(16.dp))
            ActionButton(
                if (cacheStatus == DocumentCacheStatus.READY) "파일 열기" else if (cacheStatus == DocumentCacheStatus.DOWNLOADING) "다운로드 중…" else "다운로드 후 열기",
                onOpen, Modifier.fillMaxWidth(), blue = true,
                enabled = !busy && cacheStatus != DocumentCacheStatus.DOWNLOADING && (!offline || cacheStatus == DocumentCacheStatus.READY),
            )
            if (cacheStatus != DocumentCacheStatus.READY) {
                TextButton(onClick = onDownload, enabled = !busy && !offline && cacheStatus != DocumentCacheStatus.DOWNLOADING, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (cacheStatus == DocumentCacheStatus.ERROR) "다운로드 다시 시도" else "이 기기에 다운로드")
                }
            }
        }
        if (offline) DocumentNotice("오프라인이에요. 저장된 파일을 열 수 있으며, 이름·예약 연결 변경과 삭제는 인터넷 연결 후 이용할 수 있어요.")
        if (error != null && !showDelete) DocumentError(error)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("파일 정보", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name, onValueChange = { name = it; nameError = null },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                label = { Text("파일 이름") }, enabled = !busy && !offline, singleLine = true,
                isError = nameError != null || name.trim().length > 200,
                supportingText = { Text(nameError ?: if (name.trim().length > 200) "파일 이름은 200자까지 입력할 수 있어요" else "${name.trim().length} / 200") },
                shape = RoundedCornerShape(18.dp),
            )
            ReservationLinkField(reservations, reservationId, enabled = !busy && !offline, onClick = { showReservations = true })
            Text("이름과 예약 연결은 ‘변경 저장’을 눌러야 반영돼요.", fontSize = 13.sp, lineHeight = 21.sp, color = TextSecondary)
        }
        TextButton(
            onClick = { deleteAttempted = false; showDelete = true }, enabled = !busy && !offline,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("이 서류 삭제")
        }
    }
    if (showReservations) DocumentReservationPicker(reservations, reservationId, { reservationId = it; showReservations = false }, { showReservations = false })
    if (showDiscard) DocumentDiscardDialog("저장하지 않은 변경을 버릴까요?", "파일 이름과 예약 연결 변경은 저장되지 않아요.", { showDiscard = false; onDismiss() }, { showDiscard = false })
    if (showDelete) AlertDialog(
        onDismissRequest = { if (!busy) showDelete = false },
        title = { Text("이 서류를 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(document.name, fontWeight = FontWeight.SemiBold)
                Text("앱의 서류 기록, 서버에 저장한 파일, 이 기기에 다운로드한 사본을 삭제해요. 처음 선택한 원본 파일과 실제 예약은 그대로예요. 삭제한 서류는 되돌릴 수 없어요.")
                if (deleteAttempted && error != null) DocumentError(error)
            }
        },
        confirmButton = { TextButton(onClick = { deleteAttempted = true; onDelete() }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (busy) "삭제 중…" else "서류 삭제") } },
        dismissButton = { TextButton(onClick = { showDelete = false }, enabled = !busy) { Text("취소") } },
    )
}

@Composable
fun DocumentUploadScreen(
    pickedName: String,
    pickedMediaType: String,
    pickedSize: Long,
    reservations: List<Reservation>,
    initialReservationId: String,
    busy: Boolean,
    error: String?,
    onSave: (name: String, reservationId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(pickedName, pickedSize) { mutableStateOf(pickedName) }
    var reservationId by rememberSaveable(pickedName, pickedSize) { mutableStateOf(initialReservationId) }
    var nameError by rememberSaveable(pickedName, pickedSize) { mutableStateOf<String?>(null) }
    var showReservations by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val dismiss = { if (!busy) showDiscard = true }
    HandleBack(true) { if (!busy) { if (showReservations) showReservations = false else if (showDiscard) showDiscard = false else dismiss() } }
    DocumentFrame("서류 추가", busy, dismiss, bottom = {
        ActionButton(
            if (busy) "업로드 중…" else "파일 업로드", onClick = {
                nameError = documentNameError(name)
                if (nameError != null) nameFocus.requestFocus() else onSave(name.trim(), reservationId)
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy, busy = busy, blue = true,
        )
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("선택한 파일 확인", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("파일 이름과 연결할 예약을 확인한 뒤 업로드해 주세요.", fontSize = 15.sp, lineHeight = 24.sp, color = TextSecondary)
        }
        Group {
            Icon(if (pickedMediaType.startsWith("image/")) Icons.Outlined.Image else Icons.Outlined.Description, null, Modifier.size(28.dp), tint = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text(pickedName, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
            Spacer(Modifier.height(8.dp))
            Text("${documentTypeLabel(pickedMediaType, pickedName)} · ${documentSizeLabel(pickedSize)}", fontSize = 14.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Text("PDF·JPEG·PNG · 파일당 최대 20MB", fontSize = 13.sp, color = TextSecondary)
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it; nameError = null }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            label = { Text("파일 이름") }, enabled = !busy, singleLine = true, isError = nameError != null || name.trim().length > 200,
            supportingText = { Text(nameError ?: if (name.trim().length > 200) "파일 이름은 200자까지 입력할 수 있어요" else "${name.trim().length} / 200") }, shape = RoundedCornerShape(18.dp),
        )
        ReservationLinkField(reservations, reservationId, !busy) { showReservations = true }
        if (error != null) DocumentError(error)
        if (busy) Text("업로드가 끝날 때까지 화면을 열어 두세요.", fontSize = 14.sp, color = TextSecondary)
    }
    if (showReservations) DocumentReservationPicker(reservations, reservationId, { reservationId = it; showReservations = false }, { showReservations = false })
    if (showDiscard) DocumentDiscardDialog("파일 추가를 취소할까요?", "선택한 파일은 업로드하지 않아요. 원본 파일은 그대로예요.", { showDiscard = false; onDismiss() }, { showDiscard = false })
}

@Composable
private fun DocumentFrame(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    bottom: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "닫기") }
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { bottom() }
        }
    }
}

@Composable
private fun ReservationLinkField(reservations: List<Reservation>, reservationId: String, enabled: Boolean, onClick: () -> Unit) {
    val reservation = reservations.firstOrNull { it.id == reservationId }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("연결할 예약")
        Surface(Modifier.fillMaxWidth(), color = Grouped, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (reservationId.isBlank()) "예약에 연결하지 않음" else reservation?.title ?: "연결된 예약을 찾을 수 없어요", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (reservation != null) Text("${reservationTypeName(reservation.type)} · ${reservation.date}", fontSize = 13.sp, color = TextSecondary)
                    else if (reservationId.isNotBlank()) Text("다른 예약을 선택하거나 연결을 해제해 주세요", fontSize = 13.sp, color = TextSecondary)
                }
                Icon(Icons.Outlined.ExpandMore, "예약 선택", Modifier.size(22.dp), tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun DocumentReservationPicker(reservations: List<Reservation>, selectedId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = remember(search, reservations) {
        reservations.filter { search.isBlank() || it.title.contains(search.trim(), true) || it.reference.contains(search.trim(), true) || it.date.contains(search.trim()) }.sortedWith(compareBy<Reservation> { it.date }.thenBy { it.time })
    }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.ArrowBack, "예약 선택 닫기") }
                Text("예약 연결", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), singleLine = true, label = { Text("예약 이름·번호·날짜 검색") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(18.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { DocumentReservationRow("예약에 연결하지 않음", "서류만 따로 저장해요", selectedId.isBlank()) { onSelect("") } }
                items(visible, key = { it.id }) { reservation ->
                    DocumentReservationRow(reservation.title, "${reservationTypeName(reservation.type)} · ${reservation.date}${if (reservation.reference.isBlank()) "" else " · ${reservation.reference}"}", reservation.id == selectedId) { onSelect(reservation.id) }
                }
                if (visible.isEmpty()) item { EmptyState(if (search.isBlank()) "저장한 예약이 없어요" else "일치하는 예약이 없어요", if (search.isBlank()) "예약에 연결하지 않고 파일을 저장할 수 있어요" else "다른 이름이나 번호로 검색해 주세요") }
            }
        }
    }
}

@Composable
private fun DocumentReservationRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Grouped, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                Text(subtitle, fontSize = 13.sp, color = TextSecondary, lineHeight = 21.sp)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, "선택됨", Modifier.size(22.dp), tint = Accent)
        }
    }
}

@Composable
private fun DocumentDiscardDialog(title: String, text: String, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onDiscard) { Text("나가기") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("계속 작성") } })
}

internal fun documentNameError(value: String): String? {
    val name = value.trim()
    return when {
        name.isBlank() -> "파일 이름을 입력해 주세요"
        name.length > 200 -> "파일 이름은 200자까지 입력할 수 있어요"
        name == "." || name == ".." -> "다른 파일 이름을 입력해 주세요"
        name.any { it == '/' || it == '\\' || it.code in 0..31 || it.code in 127..159 || it == '\u2028' || it == '\u2029' } -> "파일 이름에 슬래시나 줄바꿈·제어 문자를 사용할 수 없어요"
        else -> null
    }
}

private fun documentCreatedLabel(value: String): String? = runCatching {
    val local = kotlin.time.Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault())
    "추가 ${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')} · 이 기기 시간"
}.getOrNull()
