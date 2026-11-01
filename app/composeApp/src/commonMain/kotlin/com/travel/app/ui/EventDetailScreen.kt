package com.travel.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
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

@Composable fun EventDetailScreen(id:String,state:TravelState,store:TravelStore,onEdit:(String)->Unit,onDismiss:()->Unit) {
    var deletePrompt by rememberSaveable(id){mutableStateOf(false)}
    HandleBack(true){if(!state.saving){if(deletePrompt)deletePrompt=false else onDismiss()}}
    val event=state.workspace?.events?.firstOrNull{it.id==id}
    if(event==null) {
        Surface(Modifier.fillMaxSize(),color=Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("일정을 찾을 수 없어요",fontSize=22.sp,fontWeight=FontWeight.Bold)
                ActionButton("닫기",onDismiss,enabled=!state.saving)
            }
        }
        return
    }
    var note by rememberSaveable(id){mutableStateOf(event.note)}
    val tooLong=note.length>2000
    val noteChanged=note!=event.note
    val busy=state.saving||state.loading
    Surface(Modifier.fillMaxSize(),color=Canvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onDismiss,enabled=!state.saving){Icon(Icons.Outlined.Close,"닫기")}
                Text("일정 상세",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold)
                IconButton(onClick={store.clearError();deletePrompt=true},enabled=!busy){Icon(Icons.Outlined.DeleteOutline,"일정 삭제",tint=TextSecondary)}
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Label(categoryName(event.category))
                    Text(event.title,fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.ExtraBold)
                    Text(if(event.category=="flight") "${shortDate(event.date)} · ${event.startTime} 출발 — ${event.endTime} 도착" else "${shortDate(event.date)} · ${event.startTime}–${event.endTime}",fontSize=14.sp,color=TextSecondary)
                    if(event.category!="flight"&&event.location!=event.title)Text(event.location,fontSize=15.sp,color=TextSecondary)
                    if(event.costKrw>0)Text("예상 비용 ${money(event.costKrw)}",fontSize=15.sp)
                }
                OutlinedButton(
                    onClick={
                        store.clearError()
                        if(noteChanged)store.updateEventNote(id,note){onEdit("event-edit|$id")}
                        else onEdit("event-edit|$id")
                    },
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                    enabled=!busy&&!tooLong,shape=RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.Edit,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(noteChanged)"메모 저장 후 일정 수정" else "일정 수정",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value=note,onValueChange={note=it;store.clearError()},
                    modifier=Modifier.fillMaxWidth(),enabled=!state.saving,
                    label={Text("메모")},placeholder={Text("예약 정보나 챙길 것을 적어두세요")},
                    minLines=6,maxLines=12,shape=RoundedCornerShape(20.dp),isError=tooLong,
                    supportingText={Text(if(tooLong)"메모는 2,000자까지 입력할 수 있어요" else "${note.length} / 2,000")},
                )
                if(state.error!=null&&!deletePrompt)Text(state.error,color=MaterialTheme.colorScheme.error,fontSize=14.sp)
            }
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                ActionButton("메모 저장",onClick={store.updateEventNote(id,note,onDismiss)},modifier=Modifier.fillMaxWidth(),enabled=!busy&&!tooLong&&noteChanged,blue=true,busy=state.saving)
            }
        }
    }
    if(deletePrompt) {
        AlertDialog(
            onDismissRequest={if(!state.saving)deletePrompt=false},
            title={Text("일정을 삭제할까요?")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(event.title,fontWeight=FontWeight.SemiBold)
                Text("${event.date} · ${event.startTime}–${event.endTime}",color=TextSecondary)
                Text("일정과 메모가 함께 삭제돼요. 삭제한 일정은 되돌릴 수 없어요.")
                if(state.error!=null)Text(state.error,color=MaterialTheme.colorScheme.error)
            }},
            confirmButton={TextButton(
                enabled=!busy,
                onClick={store.clearError();store.deleteEvent(id){deletePrompt=false;onDismiss()}},
                colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),
            ){Text(if(state.saving)"삭제 중…" else "삭제")}},
            dismissButton={TextButton(onClick={deletePrompt=false},enabled=!state.saving){Text("취소")}},
        )
    }
}
