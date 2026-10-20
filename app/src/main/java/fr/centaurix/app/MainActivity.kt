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

private val Gold=Color(0xFFD8B65A)
private val GoldLight=Color(0xFFF1DCA3)
private val Back=Color(0xFF0B0C11)
private val Card=Color(0xFF171920)
private val Raised=Color(0xFF22242D)
private val Line=Color(0xFF30313B)
private val TextMain=Color(0xFFF8F6FC)
private val Muted=Color(0xFFA9A7B3)
private val Mint=Color(0xFF80D5A4)
private val Danger=Color(0xFFF29B91)
private val ShapeCard=RoundedCornerShape(24.dp)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(11,12,17)
        window.navigationBarColor=android.graphics.Color.rgb(11,12,17)
        setContent {
            val ble=remember { CentaurixBle(applicationContext) }
            DisposableEffect(ble){onDispose { ble.onClosed() }}
            CentaurixApp(ble)
        }
    }
}

@Composable fun CentaurixApp(ble:CentaurixBle){
    val context=LocalContext.current
    val scheme=darkColorScheme(primary=Gold,onPrimary=Color(0xFF1B160C),
        primaryContainer=Color(0xFF42371F),background=Back,onBackground=TextMain,
        surface=Card,onSurface=TextMain,surfaceVariant=Raised,onSurfaceVariant=Muted,
        outline=Line,error=Danger)
    MaterialTheme(colorScheme=scheme,shapes=Shapes(medium=RoundedCornerShape(18.dp),large=ShapeCard)) {
        var page by remember { mutableIntStateOf(0) }
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
        val headerTitles=listOf("Accueil","Profils","VPC","Thèmes","Plus")
        Scaffold(
            containerColor=Back,
            topBar={
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=20.dp,end=20.dp,top=12.dp,bottom=14.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(16.dp)).background(
                        Brush.linearGradient(listOf(Color(0xFF352E1C),Color(0xFF1B1D25)))),contentAlignment=Alignment.Center){
                        Icon(Icons.Rounded.Bolt,null,tint=Gold,modifier=Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)){
                        Text("CENTAURIX",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.7.sp)
                        Text(headerTitles[page].uppercase(Locale.FRANCE),color=Muted,fontSize=11.sp,letterSpacing=1.8.sp)
                    }
                    Surface(onClick={if(ble.connected)ble.refresh() else chooseDevice()},
                        color=if(ble.connected)Mint.copy(alpha=.12f) else Raised,shape=RoundedCornerShape(16.dp)){
                        Row(Modifier.padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if(ble.connected)Mint else Muted))
                            Spacer(Modifier.width(7.dp))
                            Text(if(ble.connected)"LIÉE" else "CONNECTER",
                                fontSize=11.sp,color=if(ble.connected)Mint else Gold,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            },
            bottomBar={
                NavigationBar(containerColor=Color(0xFF14151B),tonalElevation=0.dp,
                    modifier=Modifier.navigationBarsPadding().border(.5.dp,Line, RoundedCornerShape(topStart=22.dp,topEnd=22.dp))) {
                    val tabs=listOf(
                        Triple("Accueil",Icons.Rounded.Home,0),
                        Triple("Profils",Icons.Rounded.Tune,1),
                        Triple("VPC",Icons.Rounded.ShowChart,2),
                        Triple("Thèmes",Icons.Rounded.Palette,3),
                        Triple("Plus",Icons.Rounded.MoreHoriz,4)
                    )
                    tabs.forEach{(label,icon,index)->
                        NavigationBarItem(selected=page==index,onClick={page=index},
                            icon={Icon(icon,label,modifier=Modifier.size(23.dp))},
                            label={Text(label,fontSize=10.sp,maxLines=1)},
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
                            0 -> HomePage(ble,onConnect={chooseDevice()},onProfiles={page=1},onThemes={page=3})
                            1 -> ProfilesPage(ble,onConnect={chooseDevice()})
                            2 -> VpcPage(ble,onConnect={chooseDevice()})
                            3 -> ThemesPage(ble,onPickImage={imagePicker.launch("image/*")},onConnect={chooseDevice()})
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
            title={Text("Permissions nécessaires")},
            text={Text("Autorise Appareils à proximité et Localisation précise dans Android pour détecter la BT200.")},
            confirmButton={TextButton(onClick={settingsPermission=false}){Text("Compris")}})
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
        Text(title,fontSize=23.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
    }
}
@Composable private fun Micro(label:String,value:String,accent:Color=TextMain,modifier:Modifier=Modifier){
    Column(modifier){
        Text(label.uppercase(),fontSize=10.sp,letterSpacing=1.sp,color=Muted)
        Spacer(Modifier.height(6.dp))
        Text(value,fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=accent)
    }
}
@Composable private fun GoldButton(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Rounded.ArrowForward,
                                  enabled:Boolean=true,onClick:()->Unit){
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().height(54.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Color(0xFF1C170D),
            disabledContainerColor=Raised,disabledContentColor=Muted)){
        Icon(icon,null,modifier=Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(text,fontSize=14.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun GhostButton(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Rounded.Refresh,
                                   enabled:Boolean=true,onClick:()->Unit){
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(51.dp),
        shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Line)){
        Icon(icon,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(9.dp))
        Text(text,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
    }
}
@Composable private fun Pill(label:String,active:Boolean,modifier:Modifier=Modifier,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier,
        shape=RoundedCornerShape(14.dp),color=if(active)Gold.copy(alpha=.16f) else Raised,
        border=BorderStroke(1.dp,if(active)Gold.copy(alpha=.6f) else Line)){
        Text(label,Modifier.padding(horizontal=14.dp,vertical=12.dp),
            color=if(active)GoldLight else Muted,fontSize=13.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun Hero(ble:CentaurixBle){
    val status=ble.status
    val transition=rememberInfiniteTransition(label="hero-glow")
    val glow by transition.animateFloat(initialValue=.25f,targetValue=.52f,infiniteRepeatable(tween(2200),RepeatMode.Reverse),label="glow")
    Surface(color=Color(0xFF171820),shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth(),
        border=BorderStroke(1.dp,Gold.copy(alpha=.20f))){
        Box(Modifier.fillMaxWidth().height(255.dp).background(
            Brush.radialGradient(listOf(Gold.copy(alpha=glow*.23f),Card,Color(0xFF15161C)),radius=600f))){
            Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.SpaceBetween){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Rounded.BluetoothConnected,null,tint=Gold,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if(ble.connected)"BT200 • EN LIGNE" else "BT200 • HORS LIGNE",
                        color=GoldLight,fontSize=11.sp,letterSpacing=1.8.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if(ble.connected) Text("${ble.strength} dBm",fontSize=11.sp,color=Muted)
                }
                Row(verticalAlignment=Alignment.Bottom){
                    Text(if(ble.connected)"${status?.power?: "—"}" else "—",fontSize=81.sp,
                        fontWeight=FontWeight.Light,letterSpacing=(-4).sp,lineHeight=86.sp)
                    Text("W",fontSize=29.sp,color=Gold,modifier=Modifier.padding(start=9.dp,bottom=14.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment=Alignment.End){
                        Text(if(ble.connected)Bt200Protocol.name(status?.mode?:1).uppercase() else "—",
                            fontWeight=FontWeight.ExtraBold,color=Gold,fontSize=15.sp)
                        Text("PROFIL ${status?.slot ?: "—"}",color=Muted,fontSize=11.sp,letterSpacing=1.sp)
                    }
                }
                Text(if(ble.connected)"La box répond aux commandes de lecture."
                    else "Ton tableau de bord prend vie à la connexion.",
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
            Text("${percent.coerceIn(0,100)}%",fontSize=14.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress={percent.coerceIn(0,100)/100f},
            modifier=Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color=Gold,trackColor=Raised)
    }
}
@Composable private fun HomePage(ble:CentaurixBle,onConnect:()->Unit,onProfiles:()->Unit,onThemes:()->Unit){
    Label("Ta box, autrement.","TABLEAU DE BORD")
    Hero(ble)
    Spacer(Modifier.height(14.dp))
    if(!ble.connected) {
        GoldButton("Connecter la Centaurus BT200",Icons.Rounded.Bluetooth,onClick=onConnect)
    }else {
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Autonomie",fontSize=16.sp,fontWeight=FontWeight.Bold)
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
            Text("En un coup d'œil",fontWeight=FontWeight.Bold,fontSize=16.sp)
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
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(onClick=onProfiles,color=Card,shape=ShapeCard,modifier=Modifier.weight(1f),border=BorderStroke(1.dp,Line)){
                Column(Modifier.padding(18.dp)){
                    Icon(Icons.Rounded.Tune,null,tint=Gold)
                    Spacer(Modifier.height(20.dp))
                    Text("Profils",fontWeight=FontWeight.Bold)
                    Text("Puissance & modes",fontSize=11.sp,color=Muted)
                }
            }
            Surface(onClick=onThemes,color=Card,shape=ShapeCard,modifier=Modifier.weight(1f),border=BorderStroke(1.dp,Line)){
                Column(Modifier.padding(18.dp)){
                    Icon(Icons.Rounded.Palette,null,tint=Gold)
                    Spacer(Modifier.height(20.dp))
                    Text("Thèmes",fontWeight=FontWeight.Bold)
                    Text("Personnalisation",fontSize=11.sp,color=Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GhostButton("Actualiser les données",enabled=ble.connected,onClick=ble::refresh)
    }
    Spacer(Modifier.height(18.dp))
    Text("CENTАURIX · CONNECTÉ EN LOCAL · SANS CLOUD",fontSize=10.sp,color=Muted,letterSpacing=1.2.sp,
        modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
}
@Composable private fun ConnectPrompt(onConnect:()->Unit){
    Panel{
        Icon(Icons.Rounded.BluetoothSearching,null,Modifier.size(34.dp),tint=Gold)
        Spacer(Modifier.height(12.dp))
        Text("Ta BT200 n'est pas connectée",fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text("Active le Bluetooth dans le menu de la box, puis confirme avec le bouton central.",color=Muted,fontSize=13.sp)
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
    Label("Réglages précis.","TES TROIS PROFILS")
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
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Profil $slot",fontSize=21.sp,fontWeight=FontWeight.Bold)
                Text(if(actual!=null)"Synchronisé depuis la box" else "Lecture en attente…",
                    fontSize=11.sp,color=if(actual!=null)Mint else Muted)
            }
            Icon(Icons.Rounded.Equalizer,null,tint=Gold,modifier=Modifier.size(29.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("MODE",color=Muted,letterSpacing=1.6.sp,fontSize=11.sp)
        Spacer(Modifier.height(7.dp))
        ModePicker(edit.mode){ mode->
            edit=edit.copy(mode=mode,watts=if(mode in 3..5)edit.watts.coerceAtMost(100) else edit.watts)
        }
        Spacer(Modifier.height(14.dp))
        if(edit.mode==7){
            Text("Cette courbe répartit la puissance sur 8 étapes.",fontSize=12.sp,color=Muted)
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
            if(edit.mode==2){
                Text("Réponse Turbo",color=Muted,fontSize=13.sp)
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
            title={Text("Enregistrer sur la BT200 ?")},
            text={Text("Profil $slot · ${Bt200Protocol.name(edit.mode)} · ${if(edit.mode==7)"Courbe VPC" else "${edit.watts} W"}. Tu peux revenir aux réglages précédents en relisant la box.")},
            confirmButton={TextButton(onClick={confirm=false;ble.saveProfile(edit)}){Text("Confirmer")}},
            dismissButton={TextButton(onClick={confirm=false}){Text("Annuler")}})
        Spacer(Modifier.height(11.dp))
        GhostButton("Relire le profil",Icons.Rounded.Refresh){ble.refresh()}
        Spacer(Modifier.height(8.dp))
        var reset by remember{mutableStateOf(false)}
        OutlinedButton(onClick={reset=true},enabled=!ble.busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
            Text("Réinitialiser ce profil",color=Danger)
        }
        if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Réinitialiser le profil $slot ?")},
            text={Text("Cette opération effacera ses réglages enregistrés sur la BT200.")},
            confirmButton={TextButton(onClick={reset=false;ble.resetProfile(slot)}){Text("Réinitialiser",color=Danger)}},
            dismissButton={TextButton(onClick={reset=false}){Text("Annuler")}})
    }
    Spacer(Modifier.height(12.dp))
    Text("Les réglages sont envoyés uniquement après confirmation, puis relus après l'accusé de réception du firmware.",
        color=Muted,fontSize=12.sp,modifier=Modifier.padding(horizontal=5.dp))
}
@Composable private fun VpcPage(ble:CentaurixBle,onConnect:()->Unit){
    Label("Sculpte ta courbe.","VPC STUDIO")
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
    Panel{
        Text("Courbe de puissance",fontWeight=FontWeight.Bold,fontSize=18.sp)
        Text("Huit points de réglage • 5 à 200 watts",fontSize=12.sp,color=Muted)
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
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Activer la courbe VPC ?")},
            text={Text("Les huit points seront écrits dans le profil $slot de ta box.")},
            confirmButton={TextButton(onClick={
                confirm=false;ble.saveProfile(Bt200Protocol.Profile(slot,7,curve=points))
            }){Text("Enregistrer")}},
            dismissButton={TextButton(onClick={confirm=false}){Text("Annuler")}})
    }
}
@Composable private fun ThemesPage(ble:CentaurixBle,onPickImage:()->Unit,onConnect:()->Unit){
    Label("L'écran à ton image.","THÈMES & FONDS")
    if(!ble.connected){ConnectPrompt(onConnect);return}
    var slot by remember{mutableIntStateOf(1)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        (1..3).forEach{i->Pill("P$i",slot==i,Modifier.weight(1f)){slot=i}}
    }
    Spacer(Modifier.height(14.dp))
    Panel{
        Text("Collections officielles",fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text("Cinq variantes intégrées reconnues par l'application d'origine.",
            fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(16.dp))
        val previews=listOf("Aube","Brume","Nébuleuse","Magma","Minuit")
        var selected by remember(slot){mutableIntStateOf(1)}
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items((1..5).toList()){id->
                Column(Modifier.width(115.dp)){
                    val colors=listOf(
                        listOf(Color(0xFF443126),Color(0xFFEBC07E)),
                        listOf(Color(0xFF233F50),Color(0xFF76BBD0)),
                        listOf(Color(0xFF433056),Color(0xFFAF82D3)),
                        listOf(Color(0xFF5D2E29),Color(0xFFD38158)),
                        listOf(Color(0xFF222630),Color(0xFF64768F))
                    )
                    Box(Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(colors[id-1]))
                        .clickable{selected=id}
                        .border(if(selected==id)2.dp else 0.dp,
                            if(selected==id)Gold else Color.Transparent,RoundedCornerShape(18.dp)),
                        contentAlignment=Alignment.Center){
                        Icon(Icons.Rounded.Bolt,null,tint=Color.White.copy(alpha=.7f),modifier=Modifier.size(50.dp))
                        Text(id.toString(),Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            fontSize=11.sp,color=Color.White)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(previews[id-1],fontSize=12.sp,fontWeight=FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        GoldButton("Appliquer la variante $selected",Icons.Rounded.Palette,
            enabled=!ble.busy){ble.applyTheme(slot,selected)}
        Spacer(Modifier.height(8.dp))
        Text("La sélection est envoyée au firmware avec accusé de réception.",
            color=Muted,fontSize=11.sp)
    }
    Spacer(Modifier.height(15.dp))
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.Wallpaper,null,tint=Gold,modifier=Modifier.size(28.dp))
            Spacer(Modifier.width(11.dp))
            Column {
                Text("Mon propre fond d'écran",fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text("Ton image, directement sur la BT200",color=Muted,fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("L'image est recadrée automatiquement aux formats 240×320 et 90×120, puis encodée en RGB565.",
            fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(16.dp))
        if(ble.transferOngoing){
            val p by animateFloatAsState(ble.transferProgress,label="upload")
            Text("Transfert en cours… ${(p*100).roundToInt()}%",color=Gold,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress={p},modifier=Modifier.fillMaxWidth().height(9.dp).clip(CircleShape),
                color=Gold,trackColor=Raised)
        }else GoldButton("Choisir et transférer une image",Icons.Rounded.UploadFile,
            enabled=!ble.busy && ble.mtu>=213,onClick=onPickImage)
        Spacer(Modifier.height(9.dp))
        Text("Transfert expérimental : ne coupe pas la connexion pendant l'envoi. MTU : ${ble.mtu}.",
            color=Muted,fontSize=11.sp)
    }
}
@Composable private fun MorePage(ble:CentaurixBle,onConnect:()->Unit){
    val ctx=LocalContext.current
    Label("Tout est sous contrôle.","CENTRE DE CONTRÔLE")
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
        Text("Journal & confidentialité",fontSize=17.sp,fontWeight=FontWeight.Bold)
        Text("Aucun compte, aucune télémétrie externe. Les données restent sur ton téléphone.",
            color=Muted,fontSize=12.sp)
        Spacer(Modifier.height(15.dp))
        GhostButton("Partager le diagnostic",Icons.Rounded.Share){
            val intent=Intent(Intent.ACTION_SEND)
            intent.type="text/plain"
            intent.putExtra(Intent.EXTRA_TEXT,ble.exportedLog())
            ctx.startActivity(Intent.createChooser(intent,"Exporter le diagnostic Centaurix"))
        }
        Spacer(Modifier.height(7.dp))
        val recent=ble.logs.takeLast(12).joinToString("\n")
        Text(recent.ifBlank{"Aucune activité"},fontSize=10.sp,lineHeight=17.sp,color=Muted)
    }
    Spacer(Modifier.height(15.dp))
    Panel {
        Text("Historique local",fontWeight=FontWeight.Bold,fontSize=17.sp)
        Text("Dernières valeurs lues depuis la box",fontSize=12.sp,color=Muted)
        Spacer(Modifier.height(12.dp))
        ble.history.takeLast(8).reversed().forEach{p->
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                Text(SimpleDateFormat("dd/MM HH:mm",Locale.FRANCE).format(Date(p.timestamp)),
                    color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                Text("${p.count} bouffées",fontSize=12.sp,fontWeight=FontWeight.Bold)
            }
            HorizontalDivider(color=Line.copy(alpha=.5f))
        }
        Spacer(Modifier.height(12.dp))
        var confirm by remember{mutableStateOf(false)}
        OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
            Text("Effacer l'historique local",color=Danger)
        }
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Effacer l'historique ?")},
            text={Text("Les données enregistrées sur le téléphone seront supprimées, sans modifier la BT200.")},
            confirmButton={TextButton(onClick={confirm=false;ble.clearHistory()}){Text("Effacer",color=Danger)}},
            dismissButton={TextButton(onClick={confirm=false}){Text("Annuler")}})
    }
    Spacer(Modifier.height(15.dp))
    Text("CENTAURIX · 1.0 BETA\nInspiré du langage visuel Cloudix\nProjet indépendant, non affilié à Lost Vape",
        textAlign=TextAlign.Center,color=Muted,fontSize=11.sp,lineHeight=18.sp,
        modifier=Modifier.fillMaxWidth())
}
@Composable private fun DeviceDialog(ble:CentaurixBle,onClose:()->Unit,onSelect:(FoundDevice)->Unit){
    Dialog(onDismissRequest={ble.stopScan();onClose()}){
        Surface(shape=RoundedCornerShape(27.dp),color=Card,border=BorderStroke(1.dp,Line),
            modifier=Modifier.fillMaxWidth().heightIn(max=600.dp)){
            Column(Modifier.padding(18.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Rounded.BluetoothSearching,null,tint=Gold)
                    Spacer(Modifier.width(9.dp))
                    Text("Associer la BT200",fontSize=18.sp,fontWeight=FontWeight.Bold,
                        modifier=Modifier.weight(1f))
                    IconButton(onClick={ble.stopScan();onClose()}){Icon(Icons.Rounded.Close,null)}
                }
                Text(if(ble.searching)"Recherche en cours…" else "Recherche terminée",
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
                                    Text("${d.rssi} dBm",fontSize=11.sp,color=Muted)
                                }
                                Icon(Icons.Rounded.ChevronRight,null,tint=Gold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                GhostButton("Relancer le scan",Icons.Rounded.Refresh,onClick=ble::startScan)
                Spacer(Modifier.height(6.dp))
                Text("Sur la box : Bluetooth ON + validation avec le bouton central.",
                    color=Muted,fontSize=11.sp)
            }
        }
    }
}
