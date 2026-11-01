package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.travel.app.data.*
import com.travel.app.platform.HandleBack
import kotlin.random.Random
import kotlin.time.Clock

@Composable fun EditorDialog(route:String,state:TravelState,store:TravelStore,onDismiss:()->Unit) {
    val operation=route.substringBefore('|')
    val editing=operation=="event-edit"
    val kind=if(editing)"event" else operation
    val trip=state.workspace!!.trip
    val existing=if(editing)state.workspace.events.firstOrNull{it.id==route.substringAfter('|',"")} else null
    if(editing&&existing==null) {
        HandleBack(!state.saving,onDismiss)
        Surface(Modifier.fillMaxSize(),color=Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text("일정을 찾을 수 없어요",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("일정 목록으로 돌아가 다시 확인해 주세요",color=TextSecondary)
                ActionButton("닫기",onDismiss,enabled=!state.saving)
            }
        }
        return
    }
    val baseCategories=if(kind=="expense")listOf("food","transport","shopping","flight","stay","other") else listOf("place","food","transport","stay","flight")
    val categories=if(existing!=null&&existing.category !in baseCategories)baseCategories+existing.category else baseCategories
    val key=rememberSaveable(route){Random.nextBytes(16).joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}}
    var title by rememberSaveable(route){mutableStateOf(existing?.title.orEmpty())}
    var date by rememberSaveable(route){mutableStateOf(existing?.date ?: route.substringAfter('|',if(kind=="journal")Clock.System.now().toString().take(10) else trip.startDate))}
    var amount by rememberSaveable(route){mutableStateOf("")}
    var location by rememberSaveable(route){mutableStateOf(existing?.location.orEmpty())}
    var note by rememberSaveable(route){mutableStateOf(existing?.note.orEmpty())}
    var start by rememberSaveable(route){mutableStateOf(existing?.startTime ?: "10:00")}
    var end by rememberSaveable(route){mutableStateOf(existing?.endTime ?: "11:00")}
    var category by rememberSaveable(route){mutableIntStateOf(categories.indexOf(existing?.category).coerceAtLeast(0))}
    var invalid by rememberSaveable(route){mutableStateOf<String?>(null)}
    var error by rememberSaveable(route){mutableStateOf<String?>(null)}
    val focus=remember{listOf("title","date","amount","location","start","end","note").associateWith{FocusRequester()}}
    fun reject(field:String,message:String){invalid=field;error=message;focus[field]?.requestFocus()}
    HandleBack(true){if(!state.saving)onDismiss()}
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(),color=Canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onDismiss,enabled=!state.saving){Icon(Icons.Outlined.Close,"닫기")};Text(when(kind){"event"->if(editing)"일정 수정" else "일정 추가";"expense"->"지출 추가";else->"메모 작성"},fontSize=21.sp,fontWeight=FontWeight.Bold)}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    EditField("제목",title,{title=it},"title",focus,invalid,error,enabled=!state.saving)
                    CalendarInput("날짜",date,{date=it},Modifier.focusRequester(focus.getValue("date")),if(invalid=="date")error else null,enabled=!state.saving)
                    if(kind!="journal"){Label("카테고리");Tabs(categories.map(::categoryName),category,enabled=!state.saving){category=it}}
                    if(kind=="expense")EditField("금액 (원)",amount,{amount=it},"amount",focus,invalid,error,keyboard=KeyboardType.Number,enabled=!state.saving)
                    if(kind=="event"){
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.weight(1f)){EditField("시작",start,{start=it},"start",focus,invalid,error,hint="HH:mm",enabled=!state.saving)};Box(Modifier.weight(1f)){EditField("종료",end,{end=it},"end",focus,invalid,error,hint="HH:mm",enabled=!state.saving)}}
                        EditField("장소",location,{location=it},"location",focus,invalid,error,enabled=!state.saving)
                    }
                    if(kind!="expense")EditField(if(kind=="journal")"내용" else "메모 (선택)",note,{note=it},"note",focus,invalid,error,multiline=true,enabled=!state.saving)
                    if(state.error!=null)Text(state.error, fontSize=14.sp,color=MaterialTheme.colorScheme.error)
                }
                Box(Modifier.fillMaxWidth().padding(20.dp)) {
                    ActionButton(if(editing)"변경 사항 저장" else "저장",modifier=Modifier.fillMaxWidth(),enabled=!state.saving&&!state.loading,busy=state.saving,onClick={
                        invalid=null;error=null;store.clearError()
                        if(title.isBlank()){reject("title","제목을 입력해 주세요");return@ActionButton}
                        if(title.length>120){reject("title","제목은 120자까지 입력할 수 있어요");return@ActionButton}
                        if(!isIsoDate(date)){reject("date","날짜를 YYYY-MM-DD 형식으로 입력해 주세요");return@ActionButton}
                        when(kind){
                            "expense"->{val value=amount.toLongOrNull();if(value==null||value !in 1..1000000000L)reject("amount","1원부터 10억 원까지 정수로 입력해 주세요") else store.addExpense(ExpenseInput(title.trim(),categories[category],value,date),key,onDismiss)}
                            "event"->{when{date<trip.startDate||date>trip.endDate->reject("date","여행 기간 안의 날짜를 선택해 주세요");!Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(start)->reject("start","시작 시각을 HH:mm 형식으로 입력해 주세요");!Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(end)||end<=start->reject("end","종료 시각은 시작 시각 이후여야 해요");location.isBlank()->reject("location","장소를 입력해 주세요");note.length>2000->reject("note","메모는 2,000자까지 입력할 수 있어요");else->{
                                if(editing&&existing!=null)store.updateEvent(existing.id,EventEditInput(date,start,end,title.trim(),categories[category],location.trim(),note),onDismiss)
                                else store.addEvent(EventInput(date,start,end,title.trim(),categories[category],location.trim(),note),key,onDismiss)
                            }}}
                            else->{if(note.isBlank())reject("note","내용을 입력해 주세요")else store.addJournal(JournalInput(date,title.trim(),note),key,onDismiss)}
                        }
                    })
                }
            }
        }
    }
}
@Composable private fun EditField(label:String,value:String,onChange:(String)->Unit,id:String,focus:Map<String,FocusRequester>,invalid:String?,error:String?,hint:String="",multiline:Boolean=false,keyboard:KeyboardType=KeyboardType.Text,enabled:Boolean=true){OutlinedTextField(value,onChange,enabled=enabled,modifier=Modifier.fillMaxWidth().focusRequester(focus.getValue(id)),label={Text(label)},placeholder={if(hint.isNotEmpty())Text(hint)},singleLine=!multiline,minLines=if(multiline)5 else 1,isError=invalid==id,supportingText=if(invalid==id){{Text(error.orEmpty())}}else null,keyboardOptions=KeyboardOptions(keyboardType=keyboard),shape=RoundedCornerShape(14.dp))}
