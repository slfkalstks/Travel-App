package com.travel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.*
import com.travel.app.platform.HandleBack

@Composable
fun ReservationDetailScreen(id: String, state: TravelState, store: TravelStore, onEdit: (String) -> Unit, onDismiss: () -> Unit, onDocuments: (reservationId: String) -> Unit = {}) {
    var deletePrompt by rememberSaveable(id) { mutableStateOf(false) }
    val busy = state.loading || state.saving
    HandleBack(true) { if (!busy) { if (deletePrompt) deletePrompt = false else onDismiss() } }
    val reservation = state.workspace?.reservations?.firstOrNull { it.id == id }
    if (reservation == null) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (state.loading) "예약을 불러오고 있어요" else "예약을 찾을 수 없어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                ActionButton("닫기", onDismiss, enabled = !busy)
            }
        }
        return
    }
    val moving = reservation.type == "flight" || reservation.type == "transport"
    val startLabel = when (reservation.type) { "stay" -> "체크인"; "activity" -> "시작"; else -> "출발" }
    val endLabel = when (reservation.type) { "stay" -> "체크아웃"; "activity" -> "종료"; else -> "도착" }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "닫기") }
                Text("예약 상세", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { store.clearError(); deletePrompt = true }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "예약 기록 삭제", tint = TextSecondary) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("${reservationTypeName(reservation.type)} · ${reservationStatusName(reservation.status)}", accent = true)
                    Text(reservation.title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Label(if (reservation.source == "manual") "직접 저장한 예약" else "저장한 예약")
                }
                Group {
                    ReservationMoment(startLabel, reservation.date, reservation.time, reservation.timeZone, reservation.utcOffset)
                    if (moving && reservation.location.isNotBlank()) {
                        Spacer(Modifier.height(8.dp)); Text(reservation.location, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    if (reservation.endDate.isNotBlank()) {
                        Spacer(Modifier.height(20.dp)); Line(); Spacer(Modifier.height(20.dp))
                        ReservationMoment(endLabel, reservation.endDate, reservation.endTime, reservation.endTimeZone, reservation.endUtcOffset)
                        if (moving && reservation.endLocation.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(reservation.endLocation, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    } else if (moving && reservation.endLocation.isNotBlank()) {
                        Spacer(Modifier.height(20.dp)); Label("도착 장소"); Spacer(Modifier.height(6.dp)); Text(reservation.endLocation, fontSize = 16.sp)
                    }
                }
                if (!moving && reservation.location.isNotBlank()) ReservationDetailField(if (reservation.type == "stay") "숙소 주소" else "장소", reservation.location)
                if (reservation.provider.isNotBlank() || reservation.reference.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (reservation.provider.isNotBlank()) ReservationDetailField("예약처·운영사", reservation.provider)
                        if (reservation.reference.isNotBlank()) ReservationDetailField("예약번호", reservation.reference)
                    }
                }
                if (reservation.detail.isNotBlank()) ReservationDetailField("메모", reservation.detail)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("첨부 서류", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    val attachmentCount = state.workspace?.documents.orEmpty().count { it.reservationId == id }
                    Group {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.AttachFile, null, Modifier.size(22.dp), tint = TextSecondary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (attachmentCount == 0) "연결된 서류가 없어요" else "연결된 서류 ${attachmentCount}개", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("예약 확인서와 티켓을 모아 보세요", fontSize = 13.sp, color = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { onDocuments(id) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("서류 보기·추가"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                        }
                    }
                }
                if (reservation.cancellationDeadline.isNotBlank() || reservation.cancellationPolicy.isNotBlank()) {
                    Line()
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("취소·환불 정보", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        if (reservation.cancellationDeadline.isNotBlank()) ReservationMoment("기록한 취소 마감", reservation.cancellationDeadline.substringBefore('T'), reservation.cancellationDeadline.substringAfter('T', ""), reservation.cancellationTimeZone, reservation.cancellationUtcOffset)
                        if (reservation.cancellationPolicy.isNotBlank()) ReservationDetailField("규정 메모", reservation.cancellationPolicy)
                        Text("취소·환불 조건은 예약처에서 최종 확인해 주세요.", fontSize = 13.sp, color = TextSecondary)
                    }
                }
                if (state.error != null && !deletePrompt) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                OutlinedButton(onClick = { store.clearError(); onEdit("reservation-edit|$id") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("예약 수정", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (deletePrompt) {
        AlertDialog(onDismissRequest = { if (!busy) deletePrompt = false }, title = { Text("예약 기록을 삭제할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(reservation.title, fontWeight = FontWeight.SemiBold)
                    Text("${reservation.date} · ${reservation.time.ifBlank { "시각 미정" }}", color = TextSecondary)
                    Text("앱에 저장한 예약 기록과 메모만 삭제돼요. 예약처의 실제 예약은 취소되지 않아요. 삭제한 기록은 되돌릴 수 없어요.")
                    if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { store.clearError(); store.deleteReservation(id) { deletePrompt = false; onDismiss() } }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (state.saving) "삭제 중…" else "기록 삭제") } },
            dismissButton = { TextButton(onClick = { deletePrompt = false }, enabled = !busy) { Text("취소") } })
    }
}

@Composable private fun ReservationDetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label)
        Text(value, fontSize = 16.sp, lineHeight = 25.sp)
    }
}

@Composable private fun ReservationMoment(label: String, date: String, time: String, zone: String, offset: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(label)
        Text("$date · ${time.ifBlank { "시각 미정" }}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (time.isNotBlank()) {
            Text(if (zone.isBlank()) "시간대 미설정" else zoneDisplayName(zone) + if (offset.isBlank()) "" else " · ${reservationOffsetLabel(offset)}", fontSize = 13.sp, color = TextSecondary)
        }
    }
}
