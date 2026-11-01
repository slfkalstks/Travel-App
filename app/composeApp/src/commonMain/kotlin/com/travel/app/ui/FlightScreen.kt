package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.travel.app.data.*
import com.travel.app.platform.rememberExternalUrlLauncher
import io.ktor.http.encodeURLQueryComponent

private data class Airport(val code:String,val city:String,val name:String)
private val airports=listOf(Airport("ICN","서울","인천국제공항"),Airport("GMP","서울","김포국제공항"),Airport("PUS","부산","김해국제공항"),Airport("KIX","오사카","간사이국제공항"),Airport("NRT","도쿄","나리타국제공항"),Airport("HND","도쿄","하네다공항"),Airport("FUK","후쿠오카","후쿠오카공항"),Airport("CTS","삿포로","신치토세공항"))

@Composable fun FlightScreen(data:Workspace) {
    var originCode by rememberSaveable(data.trip.id){mutableStateOf("ICN")}
    var destinationCode by rememberSaveable(data.trip.id){mutableStateOf(airports.firstOrNull{it.city==canonicalDestinationCity(data.trip.destination)}?.code.orEmpty())}
    val origin=airports.firstOrNull{it.code==originCode};val destination=airports.firstOrNull{it.code==destinationCode}
    var mode by rememberSaveable(data.trip.id){mutableIntStateOf(0)};var picking by rememberSaveable(data.trip.id){mutableStateOf<String?>(null)}
    var departure by rememberSaveable(data.trip.id){mutableStateOf(data.trip.startDate)};var arrival by rememberSaveable(data.trip.id){mutableStateOf(data.trip.endDate)}
    var error by rememberSaveable(data.trip.id){mutableStateOf<String?>(null)}
    val open=rememberExternalUrlLauncher()
    ScreenColumn(gap=16.dp) {
        Tabs(listOf("왕복","편도"),mode){mode=it;error=null}
        Surface(color=Signal,shape=RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)){AirportRow("출발",origin){picking="origin"}}
                IconButton(onClick={val old=originCode;originCode=destinationCode;destinationCode=old;error=null},enabled=origin!=null&&destination!=null){Icon(Icons.Outlined.SwapHoriz,"출발지와 도착지 바꾸기",tint=SignalInk)}
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)){AirportRow("도착",destination){picking="destination"}}
            }
        }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(destination==null)Text("${data.trip.destination} 여행의 도착 공항을 선택해 주세요",fontSize=14.sp,lineHeight=21.sp,color=TextSecondary)
            CalendarInput("가는 날",departure,{departure=it;error=null})
            if(mode==0)CalendarInput("오는 날",arrival,{arrival=it;error=null})
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.PersonOutline,null,Modifier.size(20.dp),tint=TextSecondary);Spacer(Modifier.width(8.dp));Text("성인 1명 · 일반석",fontSize=15.sp)}
            if(error!=null)Text(error!!,fontSize=14.sp,color=MaterialTheme.colorScheme.error)
            ActionButton("항공권 보기",modifier=Modifier.fillMaxWidth(),blue=true,onClick={
                error=when{origin==null->"출발 공항을 선택해 주세요";destination==null->"도착 공항을 선택해 주세요";origin.code==destination.code->"출발지와 도착지를 다르게 선택해 주세요";!isIsoDate(departure)->"가는 날을 YYYY-MM-DD 형식으로 입력해 주세요";mode==0&&!isIsoDate(arrival)->"오는 날을 YYYY-MM-DD 형식으로 입력해 주세요";mode==0&&arrival<departure->"오는 날은 가는 날 이후로 선택해 주세요";else->null}
                if(error==null&&origin!=null&&destination!=null){val query=if(mode==0)"Flights from ${origin.code} to ${destination.code} on $departure through $arrival" else "One way flights from ${origin.code} to ${destination.code} on $departure";if(!open("https://www.google.com/travel/flights?q=${query.encodeURLQueryComponent()}&hl=ko&curr=KRW"))error="항공권 검색을 열지 못했어요. 다시 시도해 주세요"}
            })
            Text("Google 항공편으로 연결돼요. 인원·좌석은 검색 결과에서 변경할 수 있어요.",Modifier.fillMaxWidth(),fontSize=13.sp,color=TextSecondary)
        }
    }
    picking?.let { which ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (which == "origin") "출발 공항" else "도착 공항") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("현재 선택 가능한 공항 ${airports.size}곳",fontSize=13.sp,color=TextSecondary,modifier=Modifier.padding(bottom=8.dp))
                    airports.forEach { airport ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (which == "origin") originCode = airport.code else destinationCode = airport.code
                                picking = null
                                error = null
                            }.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(airport.city, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(airport.name, fontSize = 13.sp, color = TextSecondary)
                            }
                            Text(airport.code, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (airport.code == (if (which == "origin") originCode else destinationCode)) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Accent)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("닫기") } },
        )
    }
}
@Composable private fun AirportRow(label:String,airport:Airport?,onClick:()->Unit){Column(Modifier.fillMaxWidth().heightIn(min=80.dp).clickable(onClick=onClick).padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,fontSize=13.sp,color=SignalInk.copy(alpha=.7f));Text(airport?.city?:"공항 선택",fontSize=if(airport==null)20.sp else 25.sp,fontWeight=FontWeight.ExtraBold,color=SignalInk);Row(verticalAlignment=Alignment.CenterVertically){Text(airport?.code?:"선택 필요",fontSize=15.sp,color=SignalInk);Icon(Icons.Outlined.ExpandMore,null,Modifier.size(18.dp),tint=SignalInk)}}}

fun isIsoDate(value:String):Boolean {
    if(!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))return false
    val y=value.take(4).toInt();val m=value.substring(5,7).toInt();val d=value.takeLast(2).toInt()
    if(y<1900||m !in 1..12)return false
    val days=listOf(31,if(y%400==0||(y%4==0&&y%100!=0))29 else 28,31,30,31,30,31,31,30,31,30,31)
    return d in 1..days[m-1]
}
