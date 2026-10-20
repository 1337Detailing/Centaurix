package fr.centaurix.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalDate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gold get()=CentaurixUi.palette().primary
private val GoldLight get()=CentaurixUi.palette().primary
private val Back get()=CentaurixUi.palette().background
private val Card get()=CentaurixUi.palette().surface
private val Raised get()=CentaurixUi.palette().raised
private val Line get()=CentaurixUi.palette().border
private val TextMain get()=CentaurixUi.palette().text
private val Muted get()=CentaurixUi.palette().muted
private val Mint get()=CentaurixUi.palette().success
private val Danger get()=CentaurixUi.palette().error
private fun tr(text:String)=CentaurixUi.text(text)
private val ShapeCard=RoundedCornerShape(24.dp)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(11,12,17)
        window.navigationBarColor=android.graphics.Color.rgb(11,12,17)
        setContent {
            CentaurixUi.initialize(applicationContext)
            val ble=remember { CentaurixBle(applicationContext) }
            DisposableEffect(ble){onDispose { ble.onClosed() }}
            LaunchedEffect(ble) { ble.restoreConnection() }
            CentaurixApp(ble)
        }
    }
}

@Composable fun CentaurixApp(ble:CentaurixBle){
    val context=LocalContext.current
    CentaurixUi.systemDark=isSystemInDarkTheme()
    val dark=CentaurixUi.dark
    val activity=context as? ComponentActivity
    val baseScheme=if(CentaurixUi.dynamicColors && Build.VERSION.SDK_INT>=31) {
        if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }else if(dark)darkColorScheme() else lightColorScheme()
    SideEffect { CentaurixUi.systemAccentColor=baseScheme.primary }
    val scheme=baseScheme.copy(primary=Gold,onPrimary=if(Gold.luminance()>.48f)Color.Black else Color.White,
        primaryContainer=Gold.copy(alpha=.25f),background=Back,onBackground=TextMain,
        surface=Card,onSurface=TextMain,surfaceVariant=Raised,onSurfaceVariant=Muted,
        outline=Line,error=Danger)
    SideEffect {
        activity?.window?.apply {
            statusBarColor=Back.toArgb()
            navigationBarColor=Back.toArgb()
            WindowCompat.getInsetsController(this,decorView).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    MaterialTheme(colorScheme=scheme,shapes=Shapes(medium=RoundedCornerShape(18.dp),large=ShapeCard)) {
        var page by remember { mutableIntStateOf(0) }
        var currentDate by remember { mutableStateOf(LocalDate.now()) }
        LaunchedEffect(Unit) {
            while(true) {
                currentDate=LocalDate.now()
                ble.refreshDailyStats()
                delay(15_000)
            }
        }
        var deviceChooser by remember{mutableStateOf(false)}
        var settingsPermission by remember { mutableStateOf(false) }
        val permissions=if(Build.VERSION.SDK_INT>=31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.ACCESS_FINE_LOCATION)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            if(ble.permissionReady()){ble.startScan();deviceChooser=true}
            else settingsPermission=true
        }
        fun chooseDevice() {
            deviceChooser=true
            if(ble.permissionReady())ble.startScan() else permissionLauncher.launch(permissions)
        }
        val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
            if(uri!=null)ble.upload(uri)
        }
        val headerTitles=listOf("Accueil","Profils","VPC","Bouffées","Thèmes","Plus")
        Scaffold(
            containerColor=Back,
            topBar={
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=20.dp,top=12.dp,bottom=14.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(16.dp)).background(
                        Brush.linearGradient(listOf(Gold.copy(alpha=.18f),Raised))),contentAlignment=Alignment.Center){
                        Image(painterResource(R.drawable.centaurix_launcher),null,modifier=Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)){
                        Text(tr("CENTAURIX"),fontSize=18.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.7.sp)
                        Text(tr(headerTitles[page]).uppercase(CentaurixUi.locale()),color=Muted,fontSize=11.sp,letterSpacing=1.8.sp)
                    }
                    Surface(onClick={if(ble.connected)ble.refresh() else chooseDevice()},
                        color=if(ble.connected)Mint.copy(alpha=.12f) else Raised,shape=RoundedCornerShape(16.dp)){
                        Row(Modifier.padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if(ble.connected)Mint else Muted))
                            Spacer(Modifier.width(7.dp))
                            Text(if(ble.connected)tr("Connectée").uppercase(CentaurixUi.locale()) else tr("Connecter").uppercase(CentaurixUi.locale()),
                                fontSize=11.sp,color=if(ble.connected)Mint else Gold,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            },
            bottomBar={
                NavigationBar(containerColor=Card,tonalElevation=0.dp,
                    modifier=Modifier.navigationBarsPadding().border(.5.dp,Line, RoundedCornerShape(topStart=22.dp,topEnd=22.dp))) {
                    val tabs=listOf(
                        Triple("Accueil",Icons.Rounded.Home,0),
                        Triple("Profils",Icons.Rounded.Tune,1),
                        Triple("VPC",Icons.Rounded.ShowChart,2),
                        Triple("Bouffées",Icons.Rounded.BarChart,3),
                        Triple("Thèmes",Icons.Rounded.Palette,4),
                        Triple("Plus",Icons.Rounded.MoreHoriz,5)
                    )
                    tabs.forEach{(label,icon,index)->
                        NavigationBarItem(selected=page==index,onClick={page=index},
                            icon={Icon(icon,label,modifier=Modifier.size(20.dp))},
                            label={Text(tr(label),fontSize=9.sp,maxLines=1)},
                            colors=NavigationBarItemDefaults.colors(
                                selectedIconColor=Gold,selectedTextColor=Gold,
                                unselectedIconColor=Muted,unselectedTextColor=Muted,
                                indicatorColor=Gold.copy(alpha=.12f)))
                    }
                }
            }
        ){pad->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal=18.dp)) {
                AnimatedContent(targetState=page,label="page",transitionSpec={
                    (fadeIn(tween(220))+slideInHorizontally(tween(250)){it/7}) togetherWith
                    (fadeOut(tween(120))+slideOutHorizontally(tween(200)){-it/7})
                }){tab->
                    Column {
                        Spacer(Modifier.height(8.dp))
                        when(tab){
                            0 -> HomePage(ble,onConnect={chooseDevice()},onProfiles={page=1},onThemes={page=4},onPuffs={page=3})
                            1 -> ProfilesPage(ble,onConnect={chooseDevice()})
                            2 -> VpcPage(ble,onConnect={chooseDevice()})
                            3 -> PuffsPage(ble,currentDate)
                            4 -> ThemesPage(ble,onPickImage={imagePicker.launch("image/*")},onConnect={chooseDevice()})
                            else -> MorePage(ble,onConnect={chooseDevice()})
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
        if(deviceChooser) DeviceDialog(ble,onClose={deviceChooser=false},onSelect={
            ble.connectDevice(it);deviceChooser=false
        })
        if(settingsPermission)AlertDialog(onDismissRequest={settingsPermission=false},
            title={Text(tr("Permissions nécessaires"))},
            text={Text(tr("Autorise Appareils à proximité et Localisation précise dans Android pour détecter la BT200."))},
            confirmButton={TextButton(onClick={settingsPermission=false}){Text(tr("Compris"))}})
    }
}

@Composable private fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),color=Card,shape=ShapeCard,
        border=BorderStroke(1.dp,Line.copy(alpha=.75f))){
        Column(Modifier.padding(19.dp),content=content)
    }
}
@Composable private fun Label(title:String,eyebrow:String?=null){
    Column(Modifier.padding(bottom=13.dp)){
        eyebrow?.let{Text(it.uppercase(),fontSize=10.sp,letterSpacing=2.sp,color=Gold,fontWeight=FontWeight.Bold)}
        Text(tr(title),fontSize=23.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
    }
}
@Composable private fun Micro(label:String,value:String,accent:Color=TextMain,modifier:Modifier=Modifier){
    Column(modifier){
        Text(tr(label).uppercase(CentaurixUi.locale()),fontSize=10.sp,letterSpacing=1.sp,color=Muted)
        Spacer(Modifier.height(6.dp))
        Text(value,fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=accent)
    }
}
@Composable private fun GoldButton(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Rounded.ArrowForward,
                                  enabled:Boolean=true,onClick:()->Unit){
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().height(54.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=if(Gold.luminance()>.48f)Color.Black else Color.White,
            disabledContainerColor=Raised,disabledContentColor=Muted)){
        Icon(icon,null,modifier=Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(tr(text),fontSize=14.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun GhostButton(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Rounded.Refresh,
                                   enabled:Boolean=true,onClick:()->Unit){
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(51.dp),
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Line)){
        Icon(icon,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(9.dp))
        Text(tr(text),fontWeight=FontWeight.SemiBold,fontSize=13.sp)
    }
}
@Composable private fun Pill(label:String,active:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier,
        shape=RoundedCornerShape(14.dp),color=if(active)Gold.copy(alpha=.16f) else Raised,
        border=BorderStroke(1.dp,if(active)Gold.copy(alpha=.6f) else Line)){
        Text(tr(label),Modifier.padding(horizontal=14.dp,vertical=12.dp),
            color=if(active)GoldLight else Muted,fontSize=13.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun Hero(ble:CentaurixBle){
    val status=ble.status
    val transition=rememberInfiniteTransition(label="hero-glow")
    val glow by transition.animateFloat(initialValue=.25f,targetValue=.52f,infiniteRepeatable(tween(2200),RepeatMode.Reverse),label="glow")
    Surface(color=Card,shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth(),
        border=BorderStroke(1.dp,Gold.copy(alpha=.20f))){
        Box(Modifier.fillMaxWidth().height(255.dp).background(
            Brush.radialGradient(listOf(Gold.copy(alpha=glow*.17f),Card,Back),radius=600f))){
            Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.SpaceBetween){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Rounded.BluetoothConnected,null,tint=Gold,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(ble.connected)tr("BT200 • EN LIGNE") else tr("BT200 • HORS LIGNE"),
                        color=GoldLight,fontSize=11.sp,letterSpacing=1.8.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if(ble.connected) Text(tr("${ble.strength} dBm"),fontSize=11.sp,color=Muted)
                }
                Row(verticalAlignment=Alignment.Bottom){
                    Text(if(ble.connected)"${status?.power?: "—"}" else "—",fontSize=81.sp,
                        fontWeight=FontWeight.Light,letterSpacing=(-4).sp,lineHeight=86.sp)
                    Text(tr("W"),fontSize=29.sp,color=Gold,modifier=Modifier.padding(start=9.dp,bottom=14.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment=Alignment.End){
                        Text(if(ble.connected)Bt200Protocol.name(status?.mode?:1).uppercase() else "—",
                            fontWeight=FontWeight.ExtraBold,color=Gold,fontSize=15.sp)
                        Text("${tr("Profil").uppercase(CentaurixUi.locale())} ${status?.slot ?: "—"}",color=Muted,fontSize=11.sp,letterSpacing=1.sp)
                    }
                }
                Text(if(ble.connected)tr("Connectée") else tr("Connecter"),
                    fontSize=12.sp,color=Muted)
            }
        }
    }
}
@Composable private fun BatteryBar(label:String,percent:Int){
    Column {
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.BatteryFull,null,tint=Gold,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label,fontSize=12.sp,color=Muted)
            Spacer(Modifier.weight(1f))
            Text(tr("${percent.coerceIn(0,100)}%"),fontSize=14.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress={percent.coerceIn(0,100)/100f},
            modifier=Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color=Gold,trackColor=Raised)
    }
}
@Composable private fun HomePage(ble:CentaurixBle,onConnect:()->Unit,onProfiles:()->Unit,onThemes:()->Unit,onPuffs:()->Unit){
    Label("Tableau de bord","BT200")
    Hero(ble)
    Spacer(Modifier.height(14.dp))
    if(!ble.connected) {
        GoldButton("Connecter la Centaurus BT200",Icons.Rounded.Bluetooth,onClick=onConnect)
    }else {
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(tr("Autonomie"),fontSize=16.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.BatteryChargingFull,null,tint=Gold)
            }
            Spacer(Modifier.height(16.dp))
            BatteryBar("Accu 01",ble.status?.battery1?:0)
            Spacer(Modifier.height(15.dp))
            BatteryBar("Accu 02",ble.status?.battery2?:0)
        }
        Spacer(Modifier.height(14.dp))
        Panel {
            Text(tr("En un coup d'œil"),fontWeight=FontWeight.Bold,fontSize=16.sp)
            Spacer(Modifier.height(15.dp))
            Row {
                Micro("Bouffées","${ble.status?.puffs ?:0}",modifier=Modifier.weight(1f))
                Micro("Mode",Bt200Protocol.name(ble.status?.mode?:1),Gold,Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Micro("Temps brut","${ble.status?.useTime?:0}",modifier=Modifier.weight(1f))
                Micro("Profil actif","${ble.status?.slot ?: "—"} / 3",modifier=Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(onClick=onPuffs,color=Card,shape=ShapeCard,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,Line)){
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Rounded.BarChart,null,tint=Gold,modifier=Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text(tr("Bouffées aujourd'hui"),color=Muted,fontSize=12.sp)
                    Text(tr("${ble.todayPuffs} mesurées"),fontSize=20.sp,fontWeight=FontWeight.Bold)
                }
                Icon(Icons.Rounded.ChevronRight,null,tint=Gold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(onClick=onProfiles,color=Card,shape=ShapeCard,modifier=Modifier.weight(1f),border=BorderStroke(1.dp,Line)){
                Column(Modifier.padding(18.dp)){
                    Icon(Icons.Rounded.Tune,null,tint=Gold)
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Profils"),fontWeight=FontWeight.Bold)
                    Text(tr("Puissance & modes"),fontSize=11.sp,color=Muted)
                }
            }
            Surface(onClick=onThemes,color=Card,shape=ShapeCard,modifier=Modifier.weight(1f),border=BorderStroke(1.dp,Line)){
                Column(Modifier.padding(18.dp)){
                    Icon(Icons.Rounded.Palette,null,tint=Gold)
                    Spacer(Modifier.height(20.dp))
                    Text(tr("Thèmes"),fontWeight=FontWeight.Bold)
                    Text(tr("Personnalisation"),fontSize=11.sp,color=Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GhostButton("Actualiser les données",enabled=ble.connected,onClick=ble::refresh)
    }

}
@Composable private fun ConnectPrompt(onConnect:()->Unit){
    Panel{
        Icon(Icons.Rounded.BluetoothSearching,null,Modifier.size(34.dp),tint=Gold)
        Spacer(Modifier.height(12.dp))
        Text(tr("Ta BT200 n'est pas connectée"),fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text(tr("Active le Bluetooth dans le menu de la box, puis confirme avec le bouton central."),color=Muted,fontSize=13.sp)
        Spacer(Modifier.height(18.dp))
        GoldButton("Rechercher la BT200",Icons.Rounded.Bluetooth,onClick=onConnect)
    }
}
@Composable private fun EditorSlider(title:String,value:Float,onValue:(Float)->Unit,range:ClosedFloatingPointRange<Float>,suffix:String,steps:Int=0){
    Column {
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(title,color=Muted,fontSize=13.sp)
            Spacer(Modifier.weight(1f))
            Text("${if(value%1f==0f)value.toInt().toString() else String.format(Locale.US,"%.1f",value)} $suffix",
                color=GoldLight,fontWeight=FontWeight.Bold)
        }
        Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onValue,valueRange=range,steps=steps,
            colors=SliderDefaults.colors(thumbColor=Gold,activeTrackColor=Gold,inactiveTrackColor=Raised))
    }
}
@Composable private fun ModePicker(mode:Int,onMode:(Int)->Unit){
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=3.dp)){
        items((1..7).toList()){index->
            Pill(Bt200Protocol.name(index),active=index==mode,onClick={onMode(index)})
        }
    }
}
@Composable private fun ProfilesPage(ble:CentaurixBle,onConnect:()->Unit){
    Label("Profils","BT200")
    if(!ble.connected) {ConnectPrompt(onConnect);return}
    var slot by remember{mutableIntStateOf(1)}
    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
        (1..3).forEach{i->Pill("Profil 0$i",i==slot,Modifier.weight(1f)){slot=i}}
    }
    Spacer(Modifier.height(15.dp))
    val actual=ble.profiles[slot]
    var edit by remember(actual,slot){
        mutableStateOf(actual ?: Bt200Protocol.Profile(slot=slot,mode=1))
    }
    if(ble.pendingAction.isNotBlank() || ble.notice.contains("refus") ||
       ble.notice.contains("Échec") || ble.notice.contains("confirm") ||
       ble.notice.contains("réglage") || ble.notice.contains("Puissance") || ble.notice.contains("VPC")) {
        Surface(color=if(ble.notice.contains("✅"))Mint.copy(alpha=.13f) else Gold.copy(alpha=.10f),
            shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(if(ble.notice.contains("✅"))Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                    null,tint=if(ble.notice.contains("✅"))Mint else Gold)
                Spacer(Modifier.width(9.dp))
                Text(tr(ble.notice),fontSize=12.sp,color=TextMain)
            }
        }
        Spacer(Modifier.height(13.dp))
    }
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(tr("Profil $slot"),fontSize=21.sp,fontWeight=FontWeight.Bold)
                Text(if(actual!=null)tr("Synchronisé depuis la box") else tr("Lecture en attente…"),
                    fontSize=11.sp,color=if(actual!=null)Mint else Muted)
            }
            Icon(Icons.Rounded.Equalizer,null,tint=Gold,modifier=Modifier.size(29.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(tr("MODE"),color=Muted,letterSpacing=1.6.sp,fontSize=11.sp)
        Spacer(Modifier.height(7.dp))
        ModePicker(edit.mode){ mode->
            edit=edit.copy(mode=mode,watts=if(mode in 3..5)edit.watts.coerceAtMost(100) else edit.watts,
                boost=if(mode==1)edit.boost.coerceIn(1,3) else edit.boost,
                voltage=if(mode==6 && edit.voltage<0.7)3.7 else edit.voltage)
        }
        Spacer(Modifier.height(14.dp))
        if(edit.mode==7){
            Text(tr("Cette courbe répartit la puissance sur 8 étapes."),fontSize=12.sp,color=Muted)
            Spacer(Modifier.height(10.dp))
            edit.curve.forEachIndexed{index,v->
                EditorSlider("Segment ${index+1} / 8",v.toFloat(),{new->
                    edit=edit.copy(curve=edit.curve.toMutableList().also{it[index]=new.roundToInt()})
                },5f..200f,"W")
            }
        }else {
            EditorSlider("Puissance",edit.watts.toFloat(),{edit=edit.copy(watts=it.roundToInt())},
                5f..(if(edit.mode in 3..5)100f else 200f),"W")
            EditorSlider("Résistance réglée",(edit.coil.toFloat()).coerceIn(0f,3f),
                {edit=edit.copy(coil=(it*100).roundToInt()/100.0)},0f..3f,"Ω")
            if(edit.mode in 3..5){
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    Pill("°C",!edit.fahrenheit){edit=edit.copy(fahrenheit=false,temperature=200)}
                    Pill("°F",edit.fahrenheit){edit=edit.copy(fahrenheit=true,temperature=390)}
                }
                EditorSlider("Température",edit.temperature.toFloat(),{edit=edit.copy(temperature=it.roundToInt())},
                    if(edit.fahrenheit) 200f..600f else 100f..315f,if(edit.fahrenheit)"°F" else "°C")
            }
            if(edit.mode==6)EditorSlider("Tension",edit.voltage.toFloat(),
                {edit=edit.copy(voltage=(it*10).roundToInt()/10.0)},.7f..8f,"V")
            if(edit.mode==1){
                Text(tr("Réponse Power"),color=Muted,fontSize=13.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    listOf("HARD","NORM","SOFT").forEachIndexed { index,title ->
                        Pill(title,edit.boost==index+1){ edit=edit.copy(boost=index+1) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        var confirm by remember{mutableStateOf(false)}
        GoldButton("Envoyer le profil sur la box",Icons.Rounded.CheckCircle,
            enabled=actual!=null && !ble.busy){confirm=true}
        if(confirm)AlertDialog(onDismissRequest={confirm=false},
            title={Text(tr("Enregistrer sur la BT200 ?"))},
            text={Text("${tr("Profil")} $slot · ${Bt200Protocol.name(edit.mode)} · ${if(edit.mode==7)tr("Courbe VPC") else "${edit.watts} W"}")},
            confirmButton={TextButton(onClick={confirm=false;ble.saveProfile(edit)}){Text(tr("Confirmer"))}},
            dismissButton={TextButton(onClick={confirm=false}){Text(tr("Annuler"))}})
        Spacer(Modifier.height(11.dp))
        GhostButton("Relire le profil",Icons.Rounded.Refresh){ble.refresh()}
        Spacer(Modifier.height(8.dp))
        var reset by remember{mutableStateOf(false)}
        OutlinedButton(onClick={reset=true},enabled=!ble.busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
            Text(tr("Réinitialiser ce profil"),color=Danger)
        }
        if(reset)AlertDialog(onDismissRequest={reset=false},title={Text(tr("Réinitialiser le profil $slot ?"))},
            text={Text(tr("Cette opération effacera ses réglages enregistrés sur la BT200."))},
            confirmButton={TextButton(onClick={reset=false;ble.resetProfile(slot)}){Text(tr("Réinitialiser"),color=Danger)}},
            dismissButton={TextButton(onClick={reset=false}){Text(tr("Annuler"))}})
    }
    Spacer(Modifier.height(12.dp))

}
@Composable private fun VpcPage(ble:CentaurixBle,onConnect:()->Unit){
    Label("Courbe VPC","BT200")
    if(!ble.connected){ConnectPrompt(onConnect);return}
    var slot by remember{mutableIntStateOf(1)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        (1..3).forEach{i->Pill("P$i",slot==i,Modifier.weight(1f)){slot=i}}
    }
    Spacer(Modifier.height(15.dp))
    val actual=ble.profiles[slot]
    var points by remember(actual,slot) {
        mutableStateOf(actual?.curve ?: List(8){15})
    }
    if(ble.pendingAction.isNotBlank() || ble.notice.contains("VPC") || ble.notice.contains("Échec")) {
        Surface(color=Gold.copy(alpha=.09f),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
            Text(tr(ble.notice),color=GoldLight,fontSize=12.sp,
                modifier=Modifier.padding(13.dp))
        }
        Spacer(Modifier.height(10.dp))
    }
    Panel{
        Text(tr("Courbe de puissance"),fontWeight=FontWeight.Bold,fontSize=18.sp)
        Text("5–200 W",fontSize=12.sp)
        Spacer(Modifier.height(22.dp))
        Canvas(Modifier.fillMaxWidth().height(160.dp)){
            val max=200f
            val gap=size.width/7f
            for(i in 0..4){
                val y=i*size.height/4f
                drawLine(Line.copy(alpha=.75f),Offset(0f,y),Offset(size.width,y),strokeWidth=1.5f)
            }
            val path=Path()
            points.forEachIndexed{i,v->
                val x=i*gap
                val y=size.height-(v/max*size.height)
                if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            drawPath(path,Gold,style=androidx.compose.ui.graphics.drawscope.Stroke(width=4.dp.toPx(),
                cap=StrokeCap.Round,join=StrokeJoin.Round))
            points.forEachIndexed{i,v->drawCircle(GoldLight,5.dp.toPx(),
                Offset(i*gap,size.height-(v/max*size.height)))}
        }
        Spacer(Modifier.height(10.dp))
        points.forEachIndexed{i,v->
            EditorSlider("${(i+1)*0.5}s • Point ${i+1}",v.toFloat(),{
                points=points.toMutableList().also {arr->arr[i]=it.roundToInt()}
            },5f..200f,"W")
        }
        Spacer(Modifier.height(9.dp))
        var confirm by remember{mutableStateOf(false)}
        GoldButton("Enregistrer la courbe VPC",Icons.Rounded.Save,
            enabled=actual!=null && !ble.busy){confirm=true}
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Activer la courbe VPC ?"))},
            text={Text(tr("Les huit points seront écrits dans le profil $slot de ta box."))},
            confirmButton={TextButton(onClick={
                confirm=false;ble.saveProfile(Bt200Protocol.Profile(slot,7,curve=points))
            }){Text(tr("Enregistrer"))}},
            dismissButton={TextButton(onClick={confirm=false}){Text(tr("Annuler"))}})
    }
}
@Composable private fun ThemesPage(ble:CentaurixBle,onPickImage:()->Unit,onConnect:()->Unit){
    Label("Thèmes de la box","BT200")
    if(!ble.connected){ConnectPrompt(onConnect);return}
    var slot by remember{mutableIntStateOf(1)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        (1..3).forEach{i->Pill("P$i",slot==i,Modifier.weight(1f)){slot=i}}
    }
    Spacer(Modifier.height(14.dp))
    Panel{
        Text(tr("Thèmes de la box"),fontSize=18.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        var selected by remember(slot){mutableIntStateOf(ble.themes[slot]?.variant?.coerceIn(1,5) ?: 1)}
        LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            items((1..5).toList()){id->
                Pill("Thème $id",selected==id){selected=id}
            }
        }
        Spacer(Modifier.height(18.dp))
        GoldButton(if(CentaurixUi.locale().language=="fr")"Appliquer le thème $selected" else "Apply theme $selected",Icons.Rounded.Palette,
            enabled=!ble.busy){ble.applyTheme(slot,selected)}
        Spacer(Modifier.height(8.dp))

    }
    Spacer(Modifier.height(15.dp))
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.Wallpaper,null,tint=Gold,modifier=Modifier.size(28.dp))
            Spacer(Modifier.width(11.dp))
            Column {
                Text(tr("Mon propre fond d'écran"),fontSize=17.sp,fontWeight=FontWeight.Bold)

            }
        }
        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(16.dp))
        if(ble.transferOngoing){
            val p by animateFloatAsState(ble.transferProgress,label="upload")
            Text(tr("Transfert en cours… ${(p*100).roundToInt()}%"),color=Gold,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color=Gold,trackColor=Raised)
        }else GoldButton("Choisir et transférer une image",Icons.Rounded.UploadFile,
            enabled=!ble.busy && ble.mtu>=213,onClick=onPickImage)
        Spacer(Modifier.height(9.dp))

    }
}

@Composable private fun SettingsLine(
    icon:androidx.compose.ui.graphics.vector.ImageVector,
    title:String,subtitle:String,onClick:()->Unit
) {
    Surface(onClick=onClick,color=Raised,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Card),
                contentAlignment=Alignment.Center) {
                Icon(icon,null,tint=Gold,modifier=Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(tr(title),fontWeight=FontWeight.SemiBold,fontSize=15.sp)
                Text(tr(subtitle),fontSize=12.sp,color=Muted)
            }
            Icon(Icons.Rounded.ChevronRight,null,tint=Gold)
        }
    }
}
@Composable private fun AppearanceSettings() {
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language,null,tint=Gold)
            Spacer(Modifier.width(11.dp))
            Text(tr("Langue"),fontWeight=FontWeight.Bold,fontSize=17.sp)
        }
        Spacer(Modifier.height(13.dp))
        val languages=listOf("system" to tr("Automatique"),"fr" to "Français",
            "en" to "English","de" to "Deutsch","ru" to "Русский","pl" to "Polski")
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(languages){(code,label)->Pill(label,CentaurixUi.language==code){
                CentaurixUi.chooseLanguage(code)
            }}
        }
    }
    Spacer(Modifier.height(14.dp))
    Panel {
        Text(tr("Apparence"),fontWeight=FontWeight.Bold,fontSize=21.sp)
        Spacer(Modifier.height(15.dp))
        var open by remember { mutableStateOf("") }
        val themes=listOf("system" to tr("Système"),"dark" to tr("Sombre"),
            "light" to tr("Clair"),"amoled" to "AMOLED")
        val surfaces=listOf("subtle" to tr("Teinte légère"),
            "normal" to tr("Normale"),"strong" to tr("Prononcée"))
        val accents=listOf(
            Triple("gold","Or",Color(0xFFD8B65A)),
            Triple("pistachio",tr("Pistache"),Color(0xFFA6B987)),
            Triple("blue","Bleu",Color(0xFF5796E5)),
            Triple("purple","Violet",Color(0xFFAE8CE8)),
            Triple("teal","Turquoise",Color(0xFF51B5B7)),
            Triple("coral","Corail",Color(0xFFE68C76)),
            Triple("green","Vert",Color(0xFF7BC9A2)),
            Triple("custom","HEX",Gold)
        )
        SettingsLine(Icons.Rounded.DarkMode,"Thème de l'application",
            themes.firstOrNull{it.first==CentaurixUi.theme}?.second ?: tr("Système")){open="theme"}
        Spacer(Modifier.height(10.dp))
        SettingsLine(Icons.Rounded.Layers,"Intensité des surfaces",
            surfaces.firstOrNull{it.first==CentaurixUi.surfaceIntensity}?.second ?: tr("Teinte légère")){open="surface"}
        Spacer(Modifier.height(10.dp))
        SettingsLine(Icons.Rounded.ColorLens,"Couleur d'accent",
            if(CentaurixUi.dynamicColors)"Material You" else accents.firstOrNull{it.first==CentaurixUi.accent}?.second ?: "Or"){open="accent"}
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome,null,tint=Gold,modifier=Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)){
                Text(tr("Material You dynamique"),fontWeight=FontWeight.SemiBold)
                Text(tr("Couleurs du système"),fontSize=12.sp,color=Muted)
            }
            Switch(checked=CentaurixUi.dynamicColors,
                onCheckedChange={CentaurixUi.chooseDynamicColors(it)},
                enabled=Build.VERSION.SDK_INT>=31)
        }
        if(open.isNotBlank()) {
            var hex by remember(open){ mutableStateOf(CentaurixUi.customHex) }
            var invalid by remember(open){mutableStateOf(false)}
            val title=when(open){
                "theme"->"Thème de l'application"
                "surface"->"Intensité des surfaces"
                else->"Couleur d'accent"
            }
            AlertDialog(onDismissRequest={open=""},title={Text(tr(title))},
                text={
                    Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        when(open){
                            "theme"->themes.forEach{(key,label)->
                                Pill(label,CentaurixUi.theme==key){
                                    CentaurixUi.chooseTheme(key);open=""
                                }
                            }
                            "surface"->surfaces.forEach{(key,label)->
                                Pill(label,CentaurixUi.surfaceIntensity==key){
                                    CentaurixUi.chooseSurfaceIntensity(key);open=""
                                }
                            }
                            else->{
                                accents.forEach{(key,label,color)->
                                    Surface(onClick={if(key=="custom"){} else {
                                        CentaurixUi.chooseAccent(key);open=""
                                    }},shape=RoundedCornerShape(12.dp),
                                        color=if(CentaurixUi.accent==key)Gold.copy(alpha=.15f) else Raised) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                                            Box(Modifier.size(20.dp).clip(CircleShape).background(color))
                                            Spacer(Modifier.width(11.dp))
                                            Text(label,modifier=Modifier.weight(1f))
                                            if(CentaurixUi.accent==key)Icon(Icons.Rounded.Check,null,tint=Gold)
                                        }
                                    }
                                }
                                OutlinedTextField(value=hex,onValueChange={hex=it;invalid=false},
                                    isError=invalid,label={Text("HEX #RRGGBB")},singleLine=true)
                                TextButton(onClick={
                                    invalid=!CentaurixUi.setCustomHex(hex)
                                    if(!invalid)open=""
                                }){Text(tr("Confirmer"))}
                            }
                        }
                    }
                },confirmButton={TextButton(onClick={open=""}){Text(tr("Annuler"))}})
        }
    }
}

@Composable private fun MorePage(ble:CentaurixBle,onConnect:()->Unit){
    val ctx=LocalContext.current
    Label("Réglages","CENTAURIX")
    AppearanceSettings()
    Spacer(Modifier.height(14.dp))
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.BluetoothAudio,null,tint=Gold,modifier=Modifier.size(25.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)){
                Text(ble.deviceName,fontWeight=FontWeight.Bold,fontSize=16.sp)
                Text(ble.connection,color=if(ble.connected)Mint else Muted,fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(15.dp))
        Micro("MTU / Bluetooth","${ble.mtu} octets")
        Spacer(Modifier.height(14.dp))
        GhostButton("Synchroniser la box",enabled=ble.connected,onClick=ble::refresh)
        Spacer(Modifier.height(7.dp))
        GhostButton("Reconnecter",Icons.Rounded.Bluetooth,onClick=ble::reconnect)
        Spacer(Modifier.height(7.dp))
        GhostButton("Déconnecter",Icons.Rounded.LinkOff,enabled=ble.connected,onClick=ble::disconnect)
    }
    Spacer(Modifier.height(15.dp))
    Panel{
        Text(tr("Journal & confidentialité"),fontSize=17.sp,fontWeight=FontWeight.Bold)

        Spacer(Modifier.height(15.dp))
        GhostButton("Partager le diagnostic",Icons.Rounded.Share){
            val intent=Intent(Intent.ACTION_SEND)
            intent.type="text/plain"
            intent.putExtra(Intent.EXTRA_TEXT,ble.exportedLog())
            ctx.startActivity(Intent.createChooser(intent,"Exporter le diagnostic Centaurix"))
        }
        Spacer(Modifier.height(7.dp))
        val recent=ble.logs.takeLast(12).joinToString("\n")
        Text(tr(recent.ifBlank{"Aucune activité"}),fontSize=10.sp,lineHeight=17.sp,color=Muted)
    }
    Spacer(Modifier.height(15.dp))
    Panel {
        Text(tr("Diagnostics : lectures du compteur"),fontWeight=FontWeight.Bold,fontSize=17.sp)
        Text(tr("Valeurs cumulatives brutes (pas les puffs de chaque jour). Voir Puffs pour le suivi quotidien."),fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(12.dp))
        ble.history.takeLast(8).reversed().forEach{p->
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                Text(SimpleDateFormat("dd/MM HH:mm",Locale.FRANCE).format(Date(p.timestamp)),
                    color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                Text(tr("${p.count} bouffées"),fontSize=12.sp,fontWeight=FontWeight.Bold)
            }
            HorizontalDivider(color=Line.copy(alpha=.5f))
        }
        Spacer(Modifier.height(12.dp))
        var confirm by remember{mutableStateOf(false)}
        OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
            Text(tr("Effacer l'historique local"),color=Danger)
        }
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Effacer l'historique ?"))},
            text={Text(tr("Les données enregistrées sur le téléphone seront supprimées, sans modifier la BT200."))},
            confirmButton={TextButton(onClick={confirm=false;ble.clearHistory()}){Text(tr("Effacer"),color=Danger)}},
            dismissButton={TextButton(onClick={confirm=false}){Text(tr("Annuler"))}})
    }

}

@Composable private fun PuffsPage(ble:CentaurixBle,currentDate:LocalDate) {
    Label("Statistiques","BOUFFÉES")
    val now=currentDate
    val days=ble.puffDays.toList()
    val map=days.associateBy { it.date }
    val today=map[now.toString()]
    val last7=(6L downTo 0L).map { now.minusDays(it).toString() }
    val total7=last7.sumOf { map[it]?.puffs ?: 0L }
    val last30=(29L downTo 0L).map { now.minusDays(it).toString() }
    val total30=last30.sumOf { map[it]?.puffs ?: 0L }
    val formatter=java.time.format.DateTimeFormatter.ofPattern("EEE",CentaurixUi.locale())
    val max7=last7.maxOfOrNull { map[it]?.puffs ?: 0L }?.coerceAtLeast(1L) ?: 1L
    var selectedDay by remember(now) { mutableStateOf(now.toString()) }
    var monthOffset by remember { mutableIntStateOf(0) }
    val month=java.time.YearMonth.from(now).plusMonths(monthOffset.toLong())

    Panel {
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(tr("AUJOURD'HUI"),fontSize=11.sp,letterSpacing=1.6.sp,color=Gold)
                Spacer(Modifier.height(6.dp))
                Text(ble.todayPuffs.toString(),fontSize=69.sp,fontWeight=FontWeight.Light,
                    letterSpacing=(-2).sp,lineHeight=72.sp)
                Text(tr("bouffées observées"),color=Muted,fontSize=13.sp)
            }
            Surface(shape=RoundedCornerShape(22.dp),color=Gold.copy(alpha=.12f)){
                Icon(Icons.Rounded.ShowChart,null,tint=Gold,
                    modifier=Modifier.padding(18.dp).size(35.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color=Line)
        Spacer(Modifier.height(17.dp))
        Row{
            Micro("7 jours","$total7",Gold,Modifier.weight(1f))
            Micro("30 jours","$total30",TextMain,Modifier.weight(1f))
            Micro("Box (total)","${ble.status?.puffs ?: ble.devicePuffCounter}",TextMain,Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(15.dp))
    Panel {
        Text(tr("Cette semaine"),fontSize=18.sp,fontWeight=FontWeight.Bold)

        Spacer(Modifier.height(25.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.Bottom){
            last7.forEach { iso->
                val day=map[iso]
                val value=day?.puffs ?: 0L
                val isSelected=selectedDay==iso
                val norm=(value.toFloat()/max7).coerceIn(0f,1f)
                Column(Modifier.weight(1f).clickable{selectedDay=iso},
                    horizontalAlignment=Alignment.CenterHorizontally){
                    Text(if(day!=null)value.toString() else "—",
                        color=if(isSelected)GoldLight else Muted,fontSize=11.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(138.dp),contentAlignment=Alignment.BottomCenter){
                        Box(Modifier.fillMaxWidth(.65f)
                            .fillMaxHeight(if(day!=null) (.08f+.92f*norm) else .025f)
                            .clip(RoundedCornerShape(topStart=7.dp,topEnd=7.dp))
                            .background(if(isSelected)Gold else if(day==null)Line else Gold.copy(alpha=.48f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(java.time.LocalDate.parse(iso).format(formatter).take(3),
                        fontSize=10.sp,color=if(isSelected)Gold else Muted)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val selected=map[selectedDay]
        Surface(color=Raised,shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()){
            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                Text(java.time.LocalDate.parse(selectedDay).format(
                    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",CentaurixUi.locale())),
                    color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                Text(if(selected!=null)tr("${selected.puffs} bouffées") else tr("Pas de mesure"),
                    color=GoldLight,fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(15.dp))
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(tr("Historique quotidien"),fontSize=18.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.weight(1f))
            IconButton(onClick={monthOffset--}){Icon(Icons.Rounded.ChevronLeft,null,tint=Gold)}
            Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMM yy",CentaurixUi.locale())),
                fontSize=12.sp,color=GoldLight)
            IconButton(onClick={if(monthOffset<0)monthOffset++},enabled=monthOffset<0){
                Icon(Icons.Rounded.ChevronRight,null,tint=Gold)
            }
        }
        val monthDays=days.filter {it.date.startsWith(month.toString())}.sortedByDescending{it.date}
        if(monthDays.isEmpty()){
            Text(tr("Aucune lecture enregistrée ce mois-là."),color=Muted,fontSize=13.sp,
                modifier=Modifier.padding(vertical=17.dp))
        } else {
            monthDays.forEach {day->
                Row(Modifier.fillMaxWidth().padding(vertical=11.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Text(java.time.LocalDate.parse(day.date).format(
                        java.time.format.DateTimeFormatter.ofPattern("EEE dd MMM",CentaurixUi.locale())),
                        color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f))
                    Text(tr("${day.puffs} bouffées"),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                }
                HorizontalDivider(color=Line.copy(alpha=.55f))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Panel {
        Icon(Icons.Rounded.Info,null,tint=Gold,modifier=Modifier.size(22.dp))
        Spacer(Modifier.height(10.dp))
        Text(if(CentaurixUi.locale().language=="fr")
            "Classement automatique par date."
            else "Automatically grouped by local date.",
            fontSize=12.sp,color=Muted)
        if(ble.missingDayPuffs>0L){
            Spacer(Modifier.height(8.dp))
            Text(if(CentaurixUi.locale().language=="fr") "${ble.missingDayPuffs} bouffées sans date fiable." else "${ble.missingDayPuffs} puffs without a reliable date.",
                fontSize=12.sp,color=GoldLight)
        }
        Spacer(Modifier.height(13.dp))
        var wipe by remember { mutableStateOf(false) }
        GhostButton("Effacer les statistiques locales",Icons.Rounded.DeleteSweep){
            wipe=true
        }
        if(wipe)AlertDialog(onDismissRequest={wipe=false},
            title={Text(tr("Supprimer le suivi ?"))},
            text={Text(tr("Les relevés journaliers locaux seront supprimés. Le compteur de la BT200 ne sera pas modifié."))},
            confirmButton={TextButton(onClick={wipe=false;ble.clearPuffTracking()}){Text(tr("Supprimer"),color=Danger)}},
            dismissButton={TextButton(onClick={wipe=false}){Text(tr("Annuler"))}})
    }
}

@Composable private fun DeviceDialog(ble:CentaurixBle,onClose:()->Unit,onSelect:(FoundDevice)->Unit){
    Dialog(onDismissRequest={ble.stopScan();onClose()}){
        Surface(shape=RoundedCornerShape(27.dp),color=Card,border=BorderStroke(1.dp,Line),
            modifier=Modifier.fillMaxWidth().heightIn(max=600.dp)){
            Column(Modifier.padding(18.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Rounded.BluetoothSearching,null,tint=Gold)
                    Spacer(Modifier.width(9.dp))
                    Text(tr("Associer la BT200"),fontSize=18.sp,fontWeight=FontWeight.Bold,
                        modifier=Modifier.weight(1f))
                    IconButton(onClick={ble.stopScan();onClose()}){Icon(Icons.Rounded.Close,null)}
                }
                Text(if(ble.searching)tr("Recherche en cours…") else tr("Recherche terminée"),
                    color=Muted,fontSize=12.sp)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())){
                    ble.discovered.sortedWith(compareByDescending<FoundDevice>{it.name.contains("BT200",true)}
                        .thenByDescending { it.rssi }).forEach{d->
                        Surface(onClick={onSelect(d)},color=if(d.name.contains("BT200",true))Gold.copy(alpha=.09f) else Raised,
                            shape=RoundedCornerShape(14.dp),
                            modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)){
                            Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                Icon(Icons.Rounded.Bluetooth,null,tint=if(d.name.contains("BT200",true))Gold else Muted)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)){
                                    Text(d.name,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(tr("${d.rssi} dBm"),fontSize=11.sp,color=Muted)
                                }
                                Icon(Icons.Rounded.ChevronRight,null,tint=Gold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                GhostButton("Relancer le scan",Icons.Rounded.Refresh,onClick=ble::startScan)
                Spacer(Modifier.height(6.dp))
                Text(tr("Sur la box : Bluetooth ON + validation avec le bouton central."),
                    color=Muted,fontSize=11.sp)
            }
        }
    }
}
