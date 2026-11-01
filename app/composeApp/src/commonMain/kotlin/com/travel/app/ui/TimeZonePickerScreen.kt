package com.travel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.platform.HandleBack
import kotlinx.datetime.TimeZone

@Composable
fun TimeZonePickerScreen(selected: String, enabled: Boolean, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val zoneIds = remember { TimeZone.availableZoneIds.sorted() }
    val deviceZone = remember { TimeZone.currentSystemDefault().id }
    val filtered = remember(query, zoneIds) {
        zoneIds.filter { it.replace('_', ' ').contains(query.trim().replace('_', ' '), ignoreCase = true) }
    }
    HandleBack(true) { if (enabled) onDismiss() }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = enabled) { Icon(Icons.Outlined.ArrowBack, "시간대 선택 닫기") }
                Text("시간대 선택", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("예약에 적힌 현지 시각의 도시·지역을 선택해 주세요.", color = TextSecondary, fontSize = 14.sp)
                OutlinedTextField(query, { query = it }, enabled = enabled, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("도시·지역 검색") },
                    placeholder = { Text("예: Seoul, Tokyo, New York") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                if (deviceZone in zoneIds) {
                    TextButton(onClick = { onSelected(deviceZone) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("기기 시간대 사용", fontWeight = FontWeight.SemiBold)
                            Text(zoneDisplayName(deviceZone), fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
                Label("${filtered.size}개 시간대")
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                if (filtered.isEmpty()) item { EmptyState("일치하는 시간대가 없어요", "Seoul, Tokyo처럼 도시의 영문 이름으로 검색해 보세요.") }
                items(filtered, key = { it }) { id ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(enabled = enabled) { onSelected(id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(zoneDisplayName(id), fontSize = 16.sp, fontWeight = if (id == selected) FontWeight.Bold else FontWeight.Medium)
                            Text(id, fontSize = 13.sp, color = TextSecondary)
                        }
                        if (id == selected) Icon(Icons.Outlined.Check, "선택한 시간대", tint = Accent)
                    }
                    Line()
                }
            }
        }
    }
}

internal fun zoneDisplayName(id: String): String = id.split('/').reversed().joinToString(" · ") { it.replace('_', ' ') }
