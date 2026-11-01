package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.travel.app.data.*
import com.travel.app.platform.rememberExternalUrlLauncher
import io.ktor.http.encodeURLQueryComponent

@Composable
fun ExploreScreen(data: Workspace, busy: Boolean, onFavorite: (Favorite) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val open = rememberExternalUrlLauncher()
    var linkError by remember { mutableStateOf(false) }
    val savedCount = data.favorites.count { it.saved }
    val results = data.favorites
        .filter { (it.title + it.location + it.note).contains(query, ignoreCase = true) }
        .filter { when (filter) { 1 -> it.saved; 2 -> it.category == "food"; 3 -> it.category == "place"; else -> true } }

    ScreenColumn(gap = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${data.trip.destination}에서\n가볼 만한 곳", fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold)
                Text("마음에 드는 장소는 저장해두세요", color = TextSecondary, fontSize = 14.sp)
            }
            Surface(color = Signal, contentColor = SignalInk, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(savedCount.toString(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("저장한 곳", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "장소 또는 지역 검색" },
                singleLine = true, placeholder = { Text("장소나 지역을 검색해보세요", fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = TextPrimary) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "검색 지우기") } },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Grouped, focusedContainerColor = Grouped,
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = Accent
                )
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("전체", "저장한 곳", "식사", "명소").forEachIndexed { index, title ->
                    FilterChip(
                        selected = filter == index, onClick = { filter = index },
                        label = { Text(title, fontSize = 14.sp, fontWeight = if (filter == index) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = if (index == 1) {{ Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(17.dp)) }} else null,
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalInk, selectedLabelColor = Signal,
                            selectedLeadingIconColor = Signal, containerColor = Canvas,
                            labelColor = TextSecondary, iconColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == index, borderColor = Hairline, selectedBorderColor = SignalInk)
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (filter == 1) "저장한 장소" else "장소 목록", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("${results.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Accent)
            }
            if (results.isEmpty()) EmptyState("찾는 장소가 없어요", "다른 검색어를 입력하거나 필터를 바꿔보세요")
            results.forEach { place ->
                Surface(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Hairline)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(categoryIcon(place.category), null, Modifier.size(24.dp), tint = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(categoryName(place.category), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Surface(color = if (place.saved) Signal else Grouped, shape = CircleShape) {
                                IconButton(onClick = { onFavorite(place) }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        if (place.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                        if (place.saved) "${place.title} 저장 해제" else "${place.title} 저장",
                                        tint = if (place.saved) SignalInk else TextSecondary
                                    )
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(place.title, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                            Text(place.location, fontSize = 14.sp, color = TextSecondary)
                        }
                        if (place.note.isNotBlank()) Text(place.note, fontSize = 15.sp, lineHeight = 23.sp, color = TextSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { linkError = !open("https://www.google.com/maps/search/?api=1&query=" + (place.title + " " + place.location).encodeURLQueryComponent()) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${place.title} 지도에서 보기" }
                            ) {
                                Text("지도에서 보기", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.NorthEast, null, Modifier.size(18.dp), tint = TextPrimary)
                            }
                        }
                    }
                }
            }
            if (linkError) Text("지도를 열지 못했어요. 다시 시도해 주세요", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
fun JournalScreen(data: Workspace, onAdd: () -> Unit) {
    ScreenColumn(gap = 24.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("내 여행 메모", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Surface(color = Grouped, shape = CircleShape) {
                    Text("${data.journals.size}", Modifier.padding(horizontal = 15.dp, vertical = 9.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("예약 정보부터 준비할 것까지 한곳에", fontSize = 15.sp, color = TextSecondary)
            Button(
                onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = SignalInk)
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(22.dp))
                Spacer(Modifier.width(9.dp))
                Text("메모 작성", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (data.journals.isEmpty()) EmptyState("아직 메모가 없어요", "예약 정보나 기억할 내용을 적어보세요")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            data.journals.sortedByDescending { it.date }.forEach { note ->
                Surface(
                    color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                Modifier.widthIn(min = 44.dp).clearAndSetSemantics { contentDescription = note.date.replace('-', '.') },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("${note.date.substring(5, 7).toIntOrNull() ?: ""}월", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(note.date.takeLast(2), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(Modifier.width(1.dp).height(45.dp).background(Hairline))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (note.mood.isNotBlank()) Text(note.mood, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(note.title, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(note.body, fontSize = 16.sp, lineHeight = 27.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, reduced: Boolean, onReduced: (Boolean) -> Unit, busy: Boolean, onRefresh: () -> Unit) {
    ScreenColumn(gap = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("앱 설정", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("편한 화면으로 바꿔보세요", fontSize = 15.sp, color = TextSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("화면", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Hairline)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                    SettingRow("다크 모드", "어두운 화면 사용", leading = { Icon(Icons.Outlined.DarkMode, null, Modifier.size(24.dp)) }) {
                        Switch(dark, onCheckedChange = onDark, modifier = Modifier.semantics { contentDescription = "다크 모드" })
                    }
                    Line()
                    SettingRow("동작 줄이기", "화면 전환 효과 줄이기", leading = { Icon(Icons.Outlined.Animation, null, Modifier.size(24.dp)) }) {
                        Switch(reduced, onCheckedChange = onReduced, modifier = Modifier.semantics { contentDescription = "동작 줄이기" })
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("여행 데이터", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Surface(color = Grouped, shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("저장한 여행 다시 불러오기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("다른 화면에서 바뀐 내용도 확인할 수 있어요", fontSize = 14.sp, lineHeight = 22.sp, color = TextSecondary)
                    }
                    OutlinedButton(
                        onClick = onRefresh, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Hairline), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextSecondary)
                        else Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "불러오는 중" else "여행 새로고침", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("여정 0.2.0\n사진 Ale · Unsplash / 글꼴 Noto Sans KR", fontSize = 12.sp, lineHeight = 21.sp, color = TextSecondary)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, leading: @Composable () -> Unit, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, lineHeight = 20.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}
