package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import androidx.compose.ui.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.travel.app.data.*

@Composable fun TripScreen(data:Workspace,tab:Int,onTab:(Int)->Unit,busy:Boolean,onTask:(TravelTask)->Unit,onAdd:(String)->Unit,onFlights:()->Unit) {
    var selectedDay by rememberSaveable(data.trip.id){mutableStateOf(data.trip.startDate)}
    ScreenColumn {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(data.trip.title,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text("${data.trip.startDate.take(4)}. ${shortDate(data.trip.startDate)} — ${shortDate(data.trip.endDate)} · ${data.trip.travelers}명",fontSize=14.sp,color=TextSecondary)
        }
        Tabs(listOf("일정","준비물","예약","경비"),tab,onSelect=onTab)
        Column { when(tab){0->Schedule(data,selectedDay,{selectedDay=it},onAdd);1->Checklist(data,busy,onTask);2->Bookings(data,onFlights);else->Budget(data){onAdd("expense")}} }
    }
}
@Composable private fun Schedule(data:Workspace,day:String,onDay:(String)->Unit,onAdd:(String)->Unit) {
    val days=remember(data.trip.startDate,data.trip.endDate){generateSequence(Instant.parse(data.trip.startDate+"T00:00:00Z")){it+1.days}.map{it.toString().take(10)}.takeWhile{it<=data.trip.endDate}.take(366).toList()}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        days.forEachIndexed{i,date->
            val active=day==date
            Surface(shape=RoundedCornerShape(20.dp),color=if(active)Signal else Grouped) {
                Column(Modifier.width(76.dp).selectable(active,role=Role.Tab,onClick={onDay(date)}).padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(shortDate(date),fontSize=12.sp,color=if(active)SignalInk else TextSecondary);Spacer(Modifier.height(5.dp));Text("${i+1}일차",fontSize=22.sp,fontWeight=FontWeight.ExtraBold,color=if(active)SignalInk else TextPrimary)}
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val events=data.events.filter{it.date==day}.sortedBy{it.startTime}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${events.size}개 일정 · 현지 시간",Modifier.weight(1f),fontSize=13.sp,color=TextSecondary);TextButton(onClick={onAdd("event|$day")}){Icon(Icons.Outlined.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("일정 추가")}}
    if(events.isEmpty())EmptyState("일정을 추가해 보세요","가고 싶은 장소와 시간을 정리할 수 있어요")
    events.forEachIndexed { i,event ->
        Row(Modifier.fillMaxWidth().padding(bottom=14.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.width(60.dp).padding(top=17.dp),horizontalAlignment=Alignment.Start) {
                Text(event.startTime,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(event.endTime,fontSize=12.sp,color=TextSecondary)
                if(i<events.lastIndex){Spacer(Modifier.height(12.dp));Box(Modifier.padding(start=18.dp).width(2.dp).height(32.dp).background(Hairline))}
            }
            Surface(onClick={onAdd("event-detail|${event.id}")},modifier=Modifier.weight(1f),color=Grouped,contentColor=TextPrimary,shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Label(categoryName(event.category));Spacer(Modifier.weight(1f));Icon(Icons.Outlined.NorthEast,null,Modifier.size(18.dp),tint=TextSecondary)}
                    Text(event.title,fontSize=18.sp,fontWeight=FontWeight.Bold,lineHeight=26.sp)
                    if(event.category!="flight"&&event.location!=event.title)Text(event.location,fontSize=14.sp,color=TextSecondary)
                    if(event.note.isNotBlank()) {
                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Top) {
                            Icon(Icons.Outlined.Notes,null,Modifier.padding(top=3.dp).size(15.dp),tint=TextSecondary)
                            Text(event.note,fontSize=13.sp,lineHeight=20.sp,color=TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

}
@Composable private fun Checklist(data:Workspace,busy:Boolean,onTask:(TravelTask)->Unit) {
    val done=data.tasks.count{it.done}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom){Text("$done",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Accent);Text(" / ${data.tasks.size}개 완료",Modifier.padding(bottom=5.dp,start=6.dp),fontSize=15.sp,color=TextSecondary)}
    Spacer(Modifier.height(16.dp));LinearProgressIndicator(progress={done.toFloat()/data.tasks.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(8.dp),color=Accent,trackColor=Grouped);Spacer(Modifier.height(24.dp))
    data.tasks.groupBy{it.category}.forEach{(category,tasks)->
        Text(categoryName(category),fontSize=18.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
        tasks.forEach{task->Row(Modifier.fillMaxWidth().heightIn(min=60.dp).toggleable(task.done,enabled=!busy,role=Role.Checkbox,onValueChange={onTask(task)}),verticalAlignment=Alignment.CenterVertically){Checkbox(task.done,onCheckedChange=null,enabled=!busy,colors=CheckboxDefaults.colors(checkedColor=TextPrimary));Spacer(Modifier.width(8.dp));Text(task.title,Modifier.weight(1f),fontSize=16.sp,color=if(task.done)TextSecondary else TextPrimary,textDecoration=if(task.done)TextDecoration.LineThrough else TextDecoration.None,lineHeight=24.sp)}}
        Spacer(Modifier.height(24.dp))
    }
}
@Composable private fun Bookings(data:Workspace,onFlights:()->Unit) {
    var detail by remember{mutableStateOf<Reservation?>(null)}
    ActionButton("항공권 검색",onFlights,Modifier.fillMaxWidth(),blue=true)
    Spacer(Modifier.height(24.dp))
    data.reservations.forEachIndexed{i,item->
        Row(Modifier.fillMaxWidth().clickable{detail=item}.padding(vertical=20.dp),verticalAlignment=Alignment.Top){Icon(categoryIcon(item.type),null,Modifier.size(24.dp),tint=TextSecondary);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(item.title,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("${shortDate(item.date)} · ${item.time}",fontSize=14.sp,color=TextSecondary);Label(if(item.status=="confirmed")"예약 확정" else "직접 입력한 계획")};Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp),tint=TextSecondary)}
        if(i<data.reservations.lastIndex)Line()
    }
    detail?.let { item ->
        AlertDialog(
            onDismissRequest={detail=null},
            title={Text(item.title,fontWeight=FontWeight.Bold)},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${shortDate(item.date)} · ${item.time}${if(item.type=="flight") " 출발" else ""}",fontSize=14.sp,color=TextSecondary)
                if(item.type!="flight"&&item.detail.isNotBlank())Text(item.detail,fontSize=15.sp)
                if(item.type=="flight")Label(if(item.status=="confirmed")"예약 확정" else "항공편 선택 전")
                if(item.reference.isNotBlank())Text("예약번호 ${item.reference}",fontSize=15.sp)
            }},
            confirmButton={TextButton(onClick={detail=null}){Text("닫기")}},
        )
    }
}
@Composable private fun Budget(data:Workspace,onAdd:()->Unit) {
    val spent=data.expenses.sumOf{it.amountKrw};val left=data.trip.budgetKrw-spent
    Surface(color=Signal,contentColor=SignalInk,shape=RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text("지금까지 쓴 금액",fontSize=14.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(money(spent),fontSize=34.sp,fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(progress={(spent.toFloat()/data.trip.budgetKrw.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),color=SignalInk,trackColor=SignalInk.copy(alpha=.12f))
            Spacer(Modifier.height(12.dp))
            Text("예산 ${money(data.trip.budgetKrw)}",fontSize=13.sp)
            Spacer(Modifier.height(4.dp))
            Text(if(left>=0)"${money(left)} 남았어요" else "${money(-left)} 초과했어요",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(24.dp));ActionButton("지출 추가",onAdd,Modifier.fillMaxWidth());Spacer(Modifier.height(24.dp))
    Text("카테고리별 지출",fontSize=18.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp))
    data.expenses.groupBy{it.category}.entries.sortedByDescending{it.value.sumOf{e->e.amountKrw}}.forEach{(category,items)->Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){Text(categoryName(category),Modifier.weight(1f),fontSize=15.sp,color=TextSecondary);Text(money(items.sumOf{it.amountKrw}),fontSize=15.sp,fontWeight=FontWeight.Medium)}}
    Spacer(Modifier.height(20.dp));Line();Spacer(Modifier.height(20.dp));Text("지출 내역",fontSize=18.sp,fontWeight=FontWeight.Bold)
    data.expenses.sortedByDescending{it.date}.forEach{item->Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.title,fontSize=15.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(5.dp));Label("${shortDate(item.date)} · ${item.method}")};Spacer(Modifier.width(12.dp));Text(money(item.amountKrw),fontSize=15.sp,fontWeight=FontWeight.SemiBold)}}
}
