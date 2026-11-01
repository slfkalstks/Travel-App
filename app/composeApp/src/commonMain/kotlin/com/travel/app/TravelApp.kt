package com.travel.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import com.travel.app.platform.HandleBack
import androidx.compose.ui.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.travel.app.data.*
import com.travel.app.ui.*
import kotlinx.coroutines.delay

@Composable fun TravelApp() {
    val scope=rememberCoroutineScope()
    var selectedTripId by rememberSaveable{mutableStateOf<String?>(null)}
    val store=remember{TravelStore(scope,initialTripId=selectedTripId)}
    val state by store.state.collectAsState()
    var page by rememberSaveable{mutableIntStateOf(0)}
    var tripTab by rememberSaveable{mutableIntStateOf(0)}
    var editor by rememberSaveable{mutableStateOf<String?>(null)}
    var dark by rememberSaveable{mutableStateOf(false)}
    var reduced by rememberSaveable{mutableStateOf(false)}
    var previousPage by rememberSaveable{mutableIntStateOf(0)}
    val pages=rememberSaveableStateHolder()
    HandleBack(page>=4&&editor==null){page=if(page==4)previousPage else 0}
    LaunchedEffect(Unit){store.refresh()}
    LaunchedEffect(state.workspace?.trip?.id,state.loading){if(!state.loading)selectedTripId=state.workspace?.trip?.id}
    DisposableEffect(Unit){onDispose{store.close()}}
    LaunchedEffect(state.message){if(state.message!=null){delay(2500);store.clearMessage()}}
    TravelTheme(dark,reduced) {
        Surface(Modifier.fillMaxSize(),color=Canvas) {
            val showEditor=editor!=null&&(editor!!.startsWith("trip-")||state.workspace!=null)
            if(!showEditor) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().widthIn(max=640.dp)) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page>=4) IconButton(onClick={page=if(page==4)previousPage else 0}){Icon(Icons.Outlined.ArrowBack,"이전 화면")}
                    Text(when(page){0->"여정";1->"항공권";2->"장소";3->"기록";4->"설정";6->"여행 목록";else->"내 여행"},Modifier.weight(1f).padding(start=if(page<4)8.dp else 0.dp),fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                    if(page==0)IconButton(enabled=!state.loading&&!state.saving,onClick={page=6}){Icon(Icons.Outlined.CollectionsBookmark,"여행 목록 열기",tint=TextSecondary)}
                    if(page==5&&state.workspace!=null)IconButton(enabled=!state.loading&&!state.saving,onClick={editor="trip-edit|${state.workspace!!.trip.id}"}){Icon(Icons.Outlined.Edit,"여행 수정",tint=TextSecondary)}
                    if(page!=4)IconButton(onClick={previousPage=page;page=4}){Icon(Icons.Outlined.Tune,"설정 열기",tint=TextSecondary)}
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val data=state.workspace
                    if(page==6) {
                        pages.SaveableStateProvider("trip-list") {
                            TripListScreen(state.trips,data?.trip?.id,state.loading||state.saving,
                                onSelect={id->store.selectTrip(id){tripTab=0;page=0}},
                                onCreate={editor="trip-new"},onEdit={editor="trip-edit|$it"})
                        }
                    } else if(page==4) {
                        SettingsScreen(dark,{dark=it},reduced,{reduced=it},state.loading||state.saving,store::refresh)
                    } else if(data==null) {
                        Column(Modifier.align(Alignment.Center).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                            if(state.loading)CircularProgressIndicator(color=Accent)
                            else if(state.error!=null) {
                                Text("여행을 불러오지 못했어요",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                Text("연결을 확인하고 다시 시도해 주세요",color=TextSecondary)
                                ActionButton("다시 시도",store::refresh)
                            } else {
                                Icon(Icons.Outlined.Luggage,null,Modifier.size(48.dp),tint=Accent)
                                Text("어디로 떠날까요?",fontSize=28.sp,fontWeight=FontWeight.ExtraBold)
                                Text("목적지와 날짜를 정하고, 나만의 여행을 만들어 보세요.",color=TextSecondary)
                                ActionButton("새 여행 만들기",{editor="trip-new"})
                            }
                        }
                    } else {
                        Crossfade(page,animationSpec=tween(if(reduced)0 else 180)) { current ->
                            pages.SaveableStateProvider("${data.trip.id}:$current") { when(current) {
                                0->HomeScreen(data,{tripTab=it;previousPage=0;page=5},{page=1})
                                1->FlightScreen(data)
                                2->ExploreScreen(data,state.loading||state.saving,store::toggleFavorite)
                                3->JournalScreen(data){editor="journal"}
                                else->TripScreen(data,tripTab,{tripTab=it},state.loading||state.saving,store::toggleTask,{editor=it},{page=1})
                            } }
                        }
                    }
                    if(state.error!=null&&(data!=null||page==6||page==4)&&editor==null)Surface(Modifier.align(Alignment.TopCenter).padding(20.dp),color=MaterialTheme.colorScheme.errorContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(state.error!!,Modifier.weight(1f),fontSize=14.sp);IconButton(onClick=store::clearError){Icon(Icons.Outlined.Close,"오류 닫기")}}
                    }
                    androidx.compose.animation.AnimatedVisibility(state.message!=null,Modifier.align(Alignment.BottomCenter).padding(16.dp),enter=fadeIn(tween(if(reduced)0 else 180)),exit=fadeOut(tween(if(reduced)0 else 180))) {
                        Surface(color=TextPrimary,contentColor=Canvas,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){Text(state.message.orEmpty(),Modifier.padding(horizontal=20.dp,vertical=14.dp),fontSize=14.sp)}
                    }
                }
                if(page!=4) {
                    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),color=SignalInk,shape=RoundedCornerShape(30.dp)) {
                    Row(Modifier.fillMaxWidth().height(68.dp).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        val names=listOf("여행","항공권","장소","기록")
                        val icons=listOf(Icons.Outlined.Luggage,Icons.Outlined.Flight,Icons.Outlined.BookmarkBorder,Icons.Outlined.EditNote)
                        names.forEachIndexed { i,name ->
                            val selected=page==i||((page==5||page==6)&&i==0)
                            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(if(selected)Signal else Color.Transparent).selectable(selected,role=Role.Tab,onClick={page=i}),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(icons[i],null,Modifier.size(24.dp),tint=if(selected)SignalInk else Color(0xFFDEE2D8));Spacer(Modifier.height(4.dp));Text(name,fontSize=12.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,color=if(selected)SignalInk else Color(0xFFDEE2D8))}
                        }
                    } }
                }
            }
            if(showEditor) {
                val dismiss={ if(!store.state.value.saving){editor=null;store.clearError()} }
                if(editor!!.startsWith("trip-")) TripEditorScreen(editor!!,state,store,dismiss)
                else if(editor!!.startsWith("reservation-detail|")) ReservationDetailScreen(editor!!.substringAfter('|'),state,store,onEdit={store.clearError();editor=it},onDismiss=dismiss)
                else if(editor!!.startsWith("reservation-")) ReservationEditorScreen(editor!!,state,store,dismiss)
                else if(editor!!.startsWith("event-detail|")) EventDetailScreen(editor!!.substringAfter('|'),state,store,onEdit={store.clearError();editor=it},onDismiss=dismiss)
                else if(editor!!.startsWith("task-")) TaskEditorScreen(editor!!,state,store,dismiss)
                else EditorDialog(editor!!,state,store,dismiss)
            }
        }
    }
}
