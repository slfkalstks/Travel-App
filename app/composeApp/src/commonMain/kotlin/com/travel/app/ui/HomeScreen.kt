package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.travel.app.data.*
import com.travel.app.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

@Composable fun HomeScreen(data:Workspace,onTrip:(Int)->Unit,onFlights:()->Unit) {
    val completed=data.tasks.count{it.done}
    val remaining=data.tasks.size-completed
    val firstDay=data.events.filter{it.date==data.trip.startDate}.sortedBy{it.startTime}.take(3)
    val spent=data.expenses.sumOf{it.amountKrw}
    // The current Osaka itinerary uses JST (UTC+9), including its departure countdown.
    val today=(Clock.System.now()+9.hours).toString().take(10)
    val departureDays=runCatching {
        (Instant.parse(data.trip.startDate+"T00:00:00Z")-Instant.parse(today+"T00:00:00Z")).inWholeDays
    }.getOrNull()
    val duration=runCatching {
        (Instant.parse(data.trip.endDate+"T00:00:00Z")-Instant.parse(data.trip.startDate+"T00:00:00Z")).inWholeDays
    }.getOrDefault(0)
    val countdown=when {
        departureDays==null->"여행 일정"
        departureDays>0->"출발까지 ${departureDays}일"
        today<=data.trip.endDate->"여행 중"
        else->"지난 여행"
    }
    ScreenColumn {
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SectionTitle(if(today>data.trip.endDate)"지난 여행" else "다가오는 여행")
            Surface(onClick={onTrip(0)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),color=SignalInk,contentColor=Color.White) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(data.trip.title,fontSize=29.sp,lineHeight=37.sp,fontWeight=FontWeight.ExtraBold)
                            Text("${shortDate(data.trip.startDate)} — ${shortDate(data.trip.endDate)}",fontSize=17.sp,fontWeight=FontWeight.Medium)
                            Text("${duration}박 ${duration+1}일 · ${data.trip.travelers}명",fontSize=14.sp,color=Color(0xFFD0D5C9))
                            Text("일정 ${data.events.size}개",fontSize=14.sp,color=Color(0xFFD0D5C9))
                        }
                        Image(painterResource(Res.drawable.osaka),"오사카성",Modifier.width(104.dp).height(132.dp).clip(RoundedCornerShape(20.dp)),contentScale=ContentScale.Crop)
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            Surface(color=Signal,contentColor=SignalInk,shape=RoundedCornerShape(50)) {
                                Text(countdown,Modifier.padding(horizontal=12.dp,vertical=9.dp),fontSize=13.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                        Text("여행 열기",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                        Icon(Icons.Outlined.ArrowForward,null,Modifier.size(20.dp))
                    }
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("여행 준비",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
                TextButton(onClick={onTrip(1)}){Text("$completed / ${data.tasks.size} 완료",fontSize=14.sp,color=TextSecondary)}
            }
            Surface(onClick={onTrip(1)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=Grouped,contentColor=TextPrimary) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(when{data.tasks.isEmpty()->"준비물 확인하기";remaining==0->"준비를 마쳤어요";else->"${remaining}개만 더 챙기면 돼요"},Modifier.weight(1f),fontSize=19.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold)
                        Icon(Icons.Outlined.ArrowForward,null,Modifier.size(22.dp))
                    }
                    LinearProgressIndicator(progress={completed.toFloat()/data.tasks.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),color=Accent,trackColor=Canvas)
                    data.tasks.firstOrNull{!it.done}?.let{task->Text(task.title,fontSize=14.sp,color=TextSecondary,lineHeight=22.sp)}
                }
            }
        }
        Surface(onClick=onFlights,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Signal,contentColor=SignalInk) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("항공권 찾기",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.ExtraBold)
                    Icon(Icons.Outlined.ArrowOutward,null,Modifier.size(22.dp))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)){Text("ICN",fontSize=29.sp,fontWeight=FontWeight.ExtraBold);Text("인천",fontSize=14.sp)}
                    Icon(Icons.Outlined.FlightTakeoff,null,Modifier.size(28.dp))
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){Text("KIX",fontSize=29.sp,fontWeight=FontWeight.ExtraBold);Text("오사카 · 간사이",fontSize=14.sp)}
                }
                Text("${shortDate(data.trip.startDate)} 출발 · ${shortDate(data.trip.endDate)} 귀국",fontSize=14.sp)
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            SectionTitle("첫날 일정","전체 보기"){onTrip(0)}
            if(firstDay.isEmpty())Text("첫날에 갈 곳을 일정에 추가해 보세요",fontSize=15.sp,color=TextSecondary)
            firstDay.forEach { event ->
                Surface(onClick={onTrip(0)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Grouped,contentColor=TextPrimary) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(event.startTime,Modifier.width(52.dp),fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(event.title,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
                            Text(event.location,fontSize=13.sp,lineHeight=20.sp,color=TextSecondary)
                        }
                        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=TextSecondary)
                    }
                }
            }
        }
        Surface(onClick={onTrip(3)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Grouped,contentColor=TextPrimary) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("지금까지 쓴 경비",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.SemiBold);Icon(Icons.Outlined.ArrowForward,null,Modifier.size(22.dp))}
                Text(money(spent),fontSize=30.sp,lineHeight=38.sp,fontWeight=FontWeight.ExtraBold)
                Text("총 예산 ${money(data.trip.budgetKrw)}",fontSize=14.sp,color=TextSecondary)
            }
        }
    }
}
