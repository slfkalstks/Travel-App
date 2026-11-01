package com.travel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.*
import com.travel.app.platform.HandleBack

@Composable
fun DocumentsScreen(
    documents: List<DocumentRecord>,
    reservations: List<Reservation>,
    cacheStates: Map<String, DocumentCacheStatus>,
    busy: Boolean,
    error: String?,
    offline: Boolean,
    onAdd: () -> Unit,
    onOpen: (DocumentRecord) -> Unit,
    onDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    initialReservationId: String = "",
) {
    var search by rememberSaveable { mutableStateOf("") }
    var reservationOnly by rememberSaveable(initialReservationId) { mutableStateOf(initialReservationId.isNotBlank()) }
    val scroll = rememberLazyListState()
    val linkedReservation = reservations.firstOrNull { it.id == initialReservationId }
    val reservationTitles = remember(reservations) { reservations.associate { it.id to it.title } }
    val scoped = remember(documents, reservationOnly, initialReservationId) {
        if (reservationOnly) documents.filter { it.reservationId == initialReservationId } else documents
    }
    val visible = remember(scoped, search, reservationTitles) {
        val query = search.trim()
        scoped.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                documentTypeLabel(it.mediaType, it.name).contains(query, ignoreCase = true) ||
                reservationTitles[it.reservationId].orEmpty().contains(query, ignoreCase = true)
        }.sortedByDescending { it.createdAt }
    }
    HandleBack(true) { if (!busy) onDismiss() }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "닫기") }
                Text("여행 서류", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                Modifier.weight(1f), state = scroll,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("필요한 서류를\n한곳에", fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold)
                        ActionButton("파일 추가", onAdd, Modifier.fillMaxWidth(), enabled = !busy && !offline, blue = true)
                        Text("PDF·JPEG·PNG · 파일당 최대 20MB", fontSize = 13.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, label = { Text("파일 이름·종류·예약 검색") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = if (search.isNotEmpty()) {{ IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "검색어 지우기") } }} else null,
                            shape = RoundedCornerShape(18.dp),
                        )
                        if (initialReservationId.isNotBlank()) {
                            Tabs(listOf("전체 서류", "이 예약의 서류"), if (reservationOnly) 1 else 0) { reservationOnly = it == 1 }
                            if (reservationOnly) Text(linkedReservation?.title ?: "연결된 예약", fontSize = 14.sp, color = TextSecondary)
                        }
                        if (offline) DocumentNotice("오프라인이에요. 이 기기에 저장된 파일은 열 수 있어요. 파일 추가와 변경은 인터넷 연결 후 이용해 주세요.")
                        if (error != null) DocumentError(error)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (search.isBlank()) "저장한 서류" else "검색 결과", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("${visible.size}개", fontSize = 14.sp, color = TextSecondary)
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            if (search.isNotBlank()) "일치하는 서류가 없어요" else if (reservationOnly) "연결된 서류가 없어요" else "아직 저장한 서류가 없어요",
                            if (search.isNotBlank()) "다른 파일 이름이나 예약 이름으로 검색해 주세요" else "예약 확인서나 티켓 파일을 추가해 보세요",
                        )
                    }
                }
                items(visible, key = { it.id }) { document ->
                    val cacheStatus = cacheStates[document.id] ?: DocumentCacheStatus.NOT_DOWNLOADED
                    Surface(Modifier.fillMaxWidth(), color = Grouped, shape = RoundedCornerShape(24.dp)) {
                        Column {
                            Column(Modifier.fillMaxWidth().clickable(enabled = !busy) { onDetail(document.id) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(if (document.mediaType.startsWith("image/")) Icons.Outlined.Image else Icons.Outlined.Description, null, Modifier.size(22.dp), tint = TextSecondary)
                                    Text(document.name, Modifier.weight(1f), fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Outlined.ChevronRight, "서류 상세", Modifier.size(20.dp), tint = TextSecondary)
                                }
                                Text("${documentTypeLabel(document.mediaType, document.name)} · ${documentSizeLabel(document.sizeBytes)}", fontSize = 13.sp, color = TextSecondary)
                                if (document.reservationId.isNotBlank()) Text(reservationTitles[document.reservationId] ?: "연결된 예약을 찾을 수 없어요", fontSize = 14.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                DocumentCacheLabel(cacheStatus)
                            }
                            TextButton(
                                onClick = { onOpen(document) },
                                enabled = !busy && cacheStatus != DocumentCacheStatus.DOWNLOADING && (!offline || cacheStatus == DocumentCacheStatus.READY),
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp).heightIn(min = 48.dp),
                            ) {
                                Icon(if (cacheStatus == DocumentCacheStatus.READY) Icons.Outlined.OpenInNew else Icons.Outlined.Download, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(when (cacheStatus) { DocumentCacheStatus.READY -> "파일 열기"; DocumentCacheStatus.DOWNLOADING -> "다운로드 중…"; DocumentCacheStatus.ERROR -> "다운로드 다시 시도"; else -> "다운로드 후 열기" })
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun documentTypeLabel(mediaType: String, name: String): String = when {
    mediaType == "application/pdf" -> "PDF"
    mediaType == "image/jpeg" -> "JPEG 이미지"
    mediaType == "image/png" -> "PNG 이미지"
    mediaType == "image/heic" || mediaType == "image/heif" -> "HEIF 이미지"
    mediaType.startsWith("image/") -> "이미지"
    name.substringAfterLast('.', "").isNotBlank() -> name.substringAfterLast('.').uppercase() + " 파일"
    else -> "파일"
}

internal fun documentSizeLabel(bytes: Long): String {
    if (bytes < 0) return "크기 정보 없음"
    if (bytes < 1024) return "$bytes B"
    val unit = if (bytes < 1024L * 1024L) 1024L else if (bytes < 1024L * 1024L * 1024L) 1024L * 1024L else 1024L * 1024L * 1024L
    val tenths = (bytes.toDouble() / unit * 10).toLong()
    return "${tenths / 10}.${tenths % 10} ${if (unit == 1024L) "KB" else if (unit == 1024L * 1024L) "MB" else "GB"}"
}

@Composable internal fun DocumentCacheLabel(status: DocumentCacheStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (status == DocumentCacheStatus.DOWNLOADING) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        else Icon(if (status == DocumentCacheStatus.READY) Icons.Outlined.CheckCircle else if (status == DocumentCacheStatus.ERROR) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudDownload, null, Modifier.size(16.dp), tint = if (status == DocumentCacheStatus.ERROR) MaterialTheme.colorScheme.error else TextSecondary)
        Text(when (status) { DocumentCacheStatus.READY -> "이 기기에 저장됨 · 오프라인 열기 가능"; DocumentCacheStatus.DOWNLOADING -> "다운로드 중…"; DocumentCacheStatus.ERROR -> "다운로드 실패 · 다시 시도해 주세요"; else -> "이 기기에 다운로드하지 않음" }, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable internal fun DocumentNotice(text: String) {
    Group { Text(text, fontSize = 14.sp, lineHeight = 23.sp, color = TextSecondary) }
}

@Composable internal fun DocumentError(error: String) {
    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
