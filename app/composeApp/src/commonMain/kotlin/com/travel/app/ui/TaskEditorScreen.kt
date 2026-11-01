package com.travel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travel.app.data.*
import com.travel.app.platform.HandleBack
import kotlin.random.Random

@Composable fun TaskEditorScreen(route: String, state: TravelState, store: TravelStore, onDismiss: () -> Unit) {
    val editing = route.startsWith("task-edit|")
    val task = if (editing) state.workspace?.tasks?.firstOrNull { it.id == route.substringAfter('|') } else null
    val busy = state.saving || state.loading
    val dismiss = { if (!state.saving) onDismiss() }
    HandleBack(true, dismiss)

    if (editing && task == null) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("준비 항목을 찾을 수 없어요", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            ActionButton("닫기", dismiss)
        }
        return
    }

    var title by rememberSaveable(route) { mutableStateOf(task?.title.orEmpty()) }
    var category by rememberSaveable(route) { mutableIntStateOf(if (task?.category == "booking") 1 else 0) }
    var titleError by rememberSaveable(route) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(route) { mutableStateOf(false) }
    val operationId = rememberSaveable(route) {
        Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    val titleFocus = remember { FocusRequester() }
    val categoryValue = if (category == 1) "booking" else "packing"
    val changed = task == null || title.trim() != task.title || categoryValue != task.category

    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = dismiss, enabled = !state.saving) { Icon(Icons.Outlined.Close, "닫기") }
                Text(if (editing) "준비 항목 수정" else "준비 항목 추가", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = null; store.clearError() },
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                    enabled = !busy, singleLine = true,
                    label = { Text("항목 이름") },
                    placeholder = { Text("예: 여권 챙기기") },
                    isError = titleError != null || title.length > 120,
                    supportingText = { Text(titleError ?: if (title.length > 120) "항목 이름은 120자까지 입력할 수 있어요" else "${title.length} / 120") },
                    shape = RoundedCornerShape(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("카테고리")
                    Tabs(listOf("준비물", "예약 확인"), category, enabled = !busy) { category = it; store.clearError() }
                }
                if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                if (task != null) {
                    TextButton(
                        onClick = { store.clearError(); confirmDelete = true },
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("이 항목 삭제", fontSize = 15.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                ActionButton(
                    if (editing) "변경 저장" else "항목 추가",
                    onClick = {
                        titleError = when {
                            title.isBlank() -> "항목 이름을 입력해 주세요"
                            title.length > 120 -> "항목 이름은 120자까지 입력할 수 있어요"
                            else -> null
                        }
                        if (titleError != null) titleFocus.requestFocus()
                        else {
                            val input = TaskInput(title = title.trim(), category = categoryValue)
                            if (task == null) store.addTask(input, operationId, onDismiss)
                            else store.updateTask(task.id, input, onDismiss)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy && changed,
                    busy = state.saving,
                )
            }
        }
    }

    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) confirmDelete = false },
            title = { Text("준비 항목을 삭제할까요?") },
            text = { Text("‘${task.title}’ 항목을 삭제합니다. 삭제한 항목은 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmDelete = false; store.deleteTask(task.id, onDismiss) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
