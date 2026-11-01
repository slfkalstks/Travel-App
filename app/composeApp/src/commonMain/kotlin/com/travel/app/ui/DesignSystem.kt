package com.travel.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.travel.app.generated.resources.*
import org.jetbrains.compose.resources.Font

val LocalMotionReduced = staticCompositionLocalOf { false }
val Signal = Color(0xFFD7FF4F)
val SignalInk = Color(0xFF181B17)
val TextPrimary @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Canvas @Composable get() = MaterialTheme.colorScheme.background
val Grouped @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Accent @Composable get() = MaterialTheme.colorScheme.primary
val Hairline @Composable get() = MaterialTheme.colorScheme.outlineVariant

@Composable fun TravelTheme(dark:Boolean,reduced:Boolean=false,content:@Composable ()->Unit) {
    val font=FontFamily(Font(Res.font.noto_sans_kr))
    val t=Typography()
    val colors=if(dark) darkColorScheme(primary=Signal,onPrimary=SignalInk,background=Color(0xFF111111),surface=Color(0xFF111111),surfaceVariant=Color(0xFF202020),onSurface=Color(0xFFF5F5F5),onSurfaceVariant=Color(0xFFB6BBC3),outlineVariant=Color(0xFF323232),surfaceContainer=Color(0xFF202020),surfaceContainerHigh=Color(0xFF262626),surfaceContainerHighest=Color(0xFF303030),surfaceContainerLow=Color(0xFF191919),surfaceContainerLowest=Color(0xFF0C0C0C),secondary=Color(0xFFB6BBC3),secondaryContainer=Color(0xFF303030),onSecondaryContainer=Color(0xFFF5F5F5)) else lightColorScheme(primary=Color(0xFF365300),onPrimary=Color.White,background=Color(0xFFFAFBF6),surface=Color(0xFFFAFBF6),surfaceVariant=Color(0xFFEFF1E8),onSurface=SignalInk,onSurfaceVariant=Color(0xFF5D6658),outlineVariant=Color(0xFFDFE3D8),surfaceContainer=Color(0xFFF1F3EA),surfaceContainerHigh=Color(0xFFEFF1E8),surfaceContainerHighest=Color(0xFFE7EADD),surfaceContainerLow=Color(0xFFF6F7F0),surfaceContainerLowest=Color.White,secondary=Color(0xFF5D6658),secondaryContainer=Color(0xFFE9EDDE),onSecondaryContainer=Color(0xFF181B17))
    MaterialTheme(colorScheme=colors,typography=Typography(bodyLarge=t.bodyLarge.copy(fontFamily=font),bodyMedium=t.bodyMedium.copy(fontFamily=font),bodySmall=t.bodySmall.copy(fontFamily=font),titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),titleSmall=t.titleSmall.copy(fontFamily=font),headlineSmall=t.headlineSmall.copy(fontFamily=font),labelLarge=t.labelLarge.copy(fontFamily=font),labelMedium=t.labelMedium.copy(fontFamily=font),labelSmall=t.labelSmall.copy(fontFamily=font)),content={CompositionLocalProvider(LocalMotionReduced provides reduced,content=content)})
}
@Composable fun ScreenColumn(gap:Dp=24.dp,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=8.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(gap),content=content)
}
@Composable fun SectionTitle(title:String,action:String?=null,onAction:()->Unit={}) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(action!=null) TextButton(onClick=onAction){Text(action,color=TextSecondary,fontSize=14.sp)}
    }
}
@Composable fun Group(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),color=Grouped,contentColor=TextPrimary,shape=RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp),content=content) }
}
@Composable fun ActionButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,blue:Boolean=false,busy:Boolean=false) {
    val source=remember{MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val reduced=LocalMotionReduced.current
    val scale by animateFloatAsState(if(pressed&&!reduced).98f else 1f,animationSpec=if(reduced)tween(0)else spring(dampingRatio=.75f,stiffness=600f))
    Button(onClick=onClick,interactionSource=source,enabled=enabled,modifier=modifier.heightIn(min=52.dp).graphicsLayer{scaleX=scale;scaleY=scale},shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=if(blue)Signal else TextPrimary,contentColor=if(blue)SignalInk else Canvas)) {
        if(busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=LocalContentColor.current);Spacer(Modifier.width(10.dp))}
        Text(text,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
    }
}
@Composable fun Line()=HorizontalDivider(color=Hairline)
@Composable fun Label(text:String,accent:Boolean=false) { Text(text,fontSize=13.sp,color=if(accent)Accent else TextSecondary,fontWeight=FontWeight.Medium) }
@Composable fun EmptyState(title:String,body:String) { Column(Modifier.fillMaxWidth().padding(vertical=40.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text(body,color=TextSecondary,fontSize=14.sp,lineHeight=22.sp)} }
@Composable fun Tabs(labels:List<String>,selected:Int,enabled:Boolean=true,onSelect:(Int)->Unit) {
    val scroll=rememberScrollState()
    val widths=remember(labels){mutableStateMapOf<Int,Int>()}
    var viewportWidth by remember{mutableIntStateOf(0)}
    val spacing=with(LocalDensity.current){8.dp.roundToPx()}
    val reduced=LocalMotionReduced.current
    val measured=labels.indices.map{widths[it]?:0}
    LaunchedEffect(selected,measured,viewportWidth,scroll.maxValue,reduced) {
        if(selected !in labels.indices||viewportWidth<=0||measured.take(selected+1).any{it<=0})return@LaunchedEffect
        val left=measured.take(selected).sum()+selected*spacing
        val right=left+measured[selected]
        val target=when {
            left<scroll.value->left
            right>scroll.value+viewportWidth->right-viewportWidth
            else->scroll.value
        }.coerceIn(0,scroll.maxValue)
        // Reveal only this strip horizontally. Parent forms must retain their vertical position.
        if(reduced)scroll.scrollTo(target) else scroll.animateScrollTo(target)
    }
    Row(Modifier.fillMaxWidth().onSizeChanged{viewportWidth=it.width}.horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i,label ->
            Surface(modifier=Modifier.onSizeChanged{if(widths[i]!=it.width)widths[i]=it.width},color=if(i==selected)SignalInk else Grouped,shape=RoundedCornerShape(24.dp)) {
                Box(Modifier.widthIn(min=52.dp).heightIn(min=48.dp).selectable(i==selected,enabled=enabled,role=Role.Tab,onClick={onSelect(i)}).padding(horizontal=18.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                    Text(label,fontSize=15.sp,fontWeight=if(i==selected)FontWeight.Bold else FontWeight.Medium,color=if(i==selected)Signal else TextSecondary)
                }
            }
        }
    }
}

fun categoryIcon(category:String):ImageVector=when(category){"flight"->Icons.Outlined.FlightTakeoff;"stay"->Icons.Outlined.Hotel;"food"->Icons.Outlined.Restaurant;"transport"->Icons.Outlined.Train;"shopping"->Icons.Outlined.ShoppingBag;else->Icons.Outlined.Place}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CalendarInput(label:String,value:String,onChange:(String)->Unit,modifier:Modifier=Modifier,error:String?=null,enabled:Boolean=true) {
    var show by remember{mutableStateOf(false)}
    OutlinedTextField(value,onChange,modifier.fillMaxWidth(),label={Text(label)},enabled=enabled,singleLine=true,placeholder={Text("YYYY-MM-DD")},isError=error!=null,supportingText=if(error!=null){{Text(error)}}else null,trailingIcon={IconButton(onClick={show=true},enabled=enabled){Icon(Icons.Outlined.CalendarToday,"$label 달력 열기")}},shape=RoundedCornerShape(18.dp))
    if(show&&enabled){
        val millis=runCatching{kotlin.time.Instant.parse(value+"T00:00:00Z").toEpochMilliseconds()}.getOrNull().takeIf{(value.take(4).toIntOrNull()?:0) in 1900..2100}
        val picker=rememberDatePickerState(initialSelectedDateMillis=millis)
        DatePickerDialog(onDismissRequest={show=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let{onChange(kotlin.time.Instant.fromEpochMilliseconds(it).toString().take(10))};show=false},enabled=picker.selectedDateMillis!=null){Text("선택")}},dismissButton={TextButton(onClick={show=false}){Text("취소")}}){DatePicker(state=picker,showModeToggle=false)}
    }
}
