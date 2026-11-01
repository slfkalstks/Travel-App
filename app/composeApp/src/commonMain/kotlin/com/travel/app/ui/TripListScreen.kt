package com.travel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.Trip

@Composable fun TripListScreen(
    trips: List<Trip>, selectedId: String?, busy: Boolean,
    onSelect: (String) -> Unit, onCreate: () -> Unit, onEdit: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = trips.filter { it.archived == (filter == 1) }
    val results = filtered.filter { (it.title + " " + it.destination + " " + it.country).contains(query.trim(), ignoreCase = true) }
    ScreenColumn(gap = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("내 여행", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("계획 중인 여행부터 다녀온 여행까지", fontSize = 15.sp, color = TextSecondary)
        }
        ActionButton("새 여행", onCreate, Modifier.fillMaxWidth(), enabled = !busy)
        Tabs(listOf("여행", "보관"), filter) { filter = it }
        if (trips.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("여행 찾기") }, placeholder = { Text("여행 이름 또는 목적지") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "검색 지우기") } },
                shape = RoundedCornerShape(20.dp),
            )
        }
        if (results.isEmpty()) {
            when {
                query.isNotBlank() -> EmptyState("검색 결과가 없어요", "다른 여행 이름이나 목적지로 찾아보세요")
                filter == 1 -> EmptyState("보관한 여행이 없어요", "여행 수정 화면에서 다녀온 여행을 보관할 수 있어요")
                else -> EmptyState("새로운 여행을 시작해요", "목적지와 날짜를 정하고 일정을 채워보세요")
            }
        }
        results.forEach { trip ->
            val isSelected = trip.id == selectedId
            val foreground = if (isSelected) Color.White else TextPrimary
            val secondary = if (isSelected) Color(0xFFD0D5C9) else TextSecondary
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = if (isSelected) SignalInk else Grouped, contentColor = foreground,
                border = if (isSelected) BorderStroke(1.dp, Signal) else null,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                    Column(
                        Modifier.weight(1f).clickable(enabled = !busy, role = Role.Button, onClickLabel = "${trip.title} 열기") { onSelect(trip.id) }
                            .semantics { selected = isSelected }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (trip.archived) Icons.Outlined.Inventory2 else Icons.Outlined.Luggage, null, Modifier.size(22.dp), tint = if (isSelected) Signal else TextPrimary)
                            Text(if (isSelected) "선택한 여행" else if (trip.archived) "보관한 여행" else trip.country, fontSize = 13.sp, color = if (isSelected) Signal else TextSecondary)
                        }
                        Text(trip.title, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                        Text(trip.destination, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("${trip.startDate.replace('-', '.')} — ${trip.endDate.replace('-', '.')}", fontSize = 13.sp, lineHeight = 20.sp, color = secondary)
                        Text("${trip.travelers}명", fontSize = 14.sp, color = secondary)
                    }
                    IconButton(onClick = { onEdit(trip.id) }, enabled = !busy) {
                        Icon(Icons.Outlined.Edit, "${trip.title} 수정", tint = foreground)
                    }
                }
            }
        }
    }
}
