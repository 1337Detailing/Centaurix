package fr.centaurix.app

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.min

data class FoundDevice(val address:String,val name:String,val rssi:Int)
data class HistoryPoint(val timestamp:Long,val count:Long,val time:Long)
class CentaurixBle(private val context:Context) {
    private val main=Handler(Looper.getMainLooper())
    private val prefs=context.getSharedPreferences("centaurix-settings",Context.MODE_PRIVATE)
    private val manager=context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get()=manager.adapter
    val discovered=mutableStateListOf<FoundDevice>()
    val logs=mutableStateListOf<String>()
    val history=mutableStateListOf<HistoryPoint>()
    val profiles=mutableStateMapOf<Int,Bt200Protocol.Profile>()
    val themes=mutableStateMapOf<Int,Bt200Protocol.Theme>()
    var connection by mutableStateOf("Déconnectée"); private set
    var deviceName by mutableStateOf("BT200"); private set
    var connected by mutableStateOf(false); private set
    var searching by mutableStateOf(false); private set
    var strength by mutableIntStateOf(-90); private set
    var status by mutableStateOf<Bt200Protocol.Status?>(null); private set
    var notice by mutableStateOf("Prête à connecter ta box"); private set
    var busy by mutableStateOf(false); private set
    var pendingAction by mutableStateOf(""); private set
    var transferProgress by mutableFloatStateOf(0f); private set
    var transferOngoing by mutableStateOf(false); private set
    var mtu by mutableIntStateOf(23); private set

    private var scanner:BluetoothLeScanner?=null
    private var scanTimeout:Runnable?=null
    private var gatt:BluetoothGatt?=null
    private var tx:BluetoothGattCharacteristic?=null
    private var rx:BluetoothGattCharacteristic?=null
    private val stream=Bt200Stream()
    private val txQueue=ArrayDeque<ByteArray>()
    private var writeBusy=false
    private var currentWriteType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    private var writeEpoch=0
    private var requestedProfile:Bt200Protocol.Profile?=null
    private var confirmedWriteAck=false
    private var modeActivationQueued=false
    private val puffStore=PuffHistoryStore(context)
    private var puffLedger=puffStore.load(prefs.getString("device","") ?: "")
    val puffDays=mutableStateListOf<PuffLedger.Day>()
    var todayPuffs by mutableLongStateOf(0L); private set
    var missingDayPuffs by mutableLongStateOf(0L); private set
    var devicePuffCounter by mutableLongStateOf(0L); private set
    var trackingSince by mutableStateOf(""); private set
    private var savedPuffs=Long.MIN_VALUE
    private var savedUseTime=Long.MIN_VALUE
    private var savedDay=""
    init { refreshPuffScreen() }
    fun refreshDailyStats() { refreshPuffScreen() }
    private fun refreshPuffScreen() {
        puffDays.clear();puffDays.addAll(puffLedger.history())
        val today=puffStore.date()
        todayPuffs=puffLedger.forDay(today).puffs
        missingDayPuffs=puffLedger.unassignedPuffs
        trackingSince=puffLedger.history().firstOrNull()?.date ?: ""
    }
    private fun recordPuffSnapshot(v:Bt200Protocol.Status){
        val time=System.currentTimeMillis()
        val day=puffStore.date(time)
        val result=puffLedger.accept(day,v.puffs,v.useTime,time)
        devicePuffCounter=v.puffs
        if(result.reset)log("Compteur bouffées réinitialisé par la box : nouvelle référence.")
        if(savedPuffs!=v.puffs || savedUseTime!=v.useTime || savedDay!=day || result.reset) {
            puffStore.store(lastSeenId,puffLedger)
            savedPuffs=v.puffs;savedUseTime=v.useTime;savedDay=day
        }
        refreshPuffScreen()
    }
    fun clearPuffTracking() {
        puffLedger.clear()
        puffStore.store(lastSeenId,puffLedger)
        savedPuffs=Long.MIN_VALUE;savedUseTime=Long.MIN_VALUE;savedDay=""
        refreshPuffScreen()
        say("Suivi local des bouffées effacé")
    }

    private var scanVersion=0
    private var notificationSetup=false
    private var pollActive=false
    private var pollGeneration=0
    private var retryAllowed=false
    private var reconnectFailures=0
    private var retryTask:Runnable?=null
    private val packetsToSave=mutableListOf<ByteArray>()
    private var uploading:List<ByteArray>?=null
    private var chunkIndex=0
    private var uploadDeadline=0L
    private var uploadSession=0
    private var lastSeenId=prefs.getString("device","") ?: ""
    private var lastPoll=0L
    private val MAX_LOGS=280
    private var lastHistoryTime=0L
    private var lastHistoryCount=-1L

    init {
        try {
            val raw=prefs.getString("history","[]") ?: "[]"
            val records=org.json.JSONArray(raw)
            for(i in 0 until records.length()) {
                val obj=records.optJSONObject(i) ?: continue
                history.add(HistoryPoint(obj.optLong("ts"),obj.optLong("count"),obj.optLong("time")))
            }
        } catch (_:Exception){}
    }
    private fun ui(update:()->Unit) { main.post(update) }
    private fun say(t:String) { ui {
        notice=t
        log(t)
    }}
    private fun log(t:String) {
        logs.add("• "+t)
        while(logs.size>MAX_LOGS) logs.removeAt(0)
    }
    fun exportedLog():String = buildString {
        append("Centaurix diagnostic 1.0\n")
        append("Android ").append(Build.VERSION.SDK_INT).append(" | ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        append("BLE ").append(connection).append(" | MTU ").append(mtu).append("\n")
        for(s in logs)append(s).append("\n")
    }
    fun permissionReady():Boolean {
        if (Build.VERSION.SDK_INT>=31 && (
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)) return false
        return Build.VERSION.SDK_INT>=31 || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    }
    fun startScan() {
        stopScan()
        if(!permissionReady()){say("Autorise l'accès Bluetooth dans les paramètres");return}
        if(adapter?.isEnabled!=true){say("Active le Bluetooth Android");return}
        try {
            ui { discovered.clear(); searching=true; connection="Recherche…" }
            scanner=adapter.bluetoothLeScanner
            val current=++scanVersion
            scanner?.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
            val timeout=Runnable { if(current==scanVersion) stopScan() }
            scanTimeout=timeout
            main.postDelayed(timeout,20000)
            say("Recherche des appareils Bluetooth à proximité")
        } catch(e:Exception){say("Erreur scan : "+e.message);stopScan()}
    }
    fun stopScan(){
        scanVersion++
        scanTimeout?.let { main.removeCallbacks(it) }
        scanTimeout=null
        try{scanner?.stopScan(scanCallback)}catch(_:Exception){}
        scanner=null
        ui { searching=false; if(!connected && connection=="Recherche…") connection="Déconnectée" }
    }
    private val scanCallback=object:ScanCallback(){
        override fun onScanResult(type:Int,result:ScanResult){
            val d=result.device
            val name=try{result.scanRecord?.deviceName ?: d.name ?: "Appareil sans nom"}catch(_:Exception){"Appareil sans nom"}
            val addr=d.address ?: return
            ui {
                val existing=discovered.indexOfFirst { it.address==addr }
                if(existing<0)discovered.add(FoundDevice(addr,name,result.rssi))
                else discovered[existing]=FoundDevice(addr,name,result.rssi)
                if(addr==lastSeenId) strength=result.rssi
            }
        }
        override fun onScanFailed(errorCode:Int) {
            say("Échec scanner BLE ($errorCode)")
            stopScan()
        }
    }
    fun connectDevice(d:FoundDevice){
        retryAllowed=true
        reconnectFailures=0
        connectRemembered(d)
    }
    fun restoreConnection() {
        // Never start scanning without user interaction; reconnect only a known box.
        if(permissionReady() && BluetoothAdapter.checkBluetoothAddress(lastSeenId)) reconnect()
    }
    fun reconnect(){
        if(connected || gatt!=null)return
        val address=prefs.getString("device","") ?: ""
        if(!BluetoothAdapter.checkBluetoothAddress(address)) {
            startScan()
            return
        }
        retryAllowed=true
        connectRemembered(FoundDevice(address,deviceName,-70))
    }
    private fun cancelRetry() {
        retryTask?.let(main::removeCallbacks)
        retryTask=null
    }
    private fun scheduleReconnect(){
        if(!retryAllowed || !BluetoothAdapter.checkBluetoothAddress(lastSeenId) || retryTask!=null)return
        val seconds=(3 shl reconnectFailures.coerceAtMost(3)).coerceAtMost(30)
        reconnectFailures=(reconnectFailures+1).coerceAtMost(6)
        ui { connection="Reconnexion automatique dans ${seconds}s…" }
        val task=Runnable {
            retryTask=null
            if(!connected && retryAllowed && gatt==null) reconnect()
        }
        retryTask=task
        main.postDelayed(task,seconds*1000L)
    }
    private fun connectRemembered(d:FoundDevice) {
        cancelRetry()
        if(!permissionReady() || adapter?.isEnabled!=true){
            say("Bluetooth indisponible : nouvelle tentative automatique")
            scheduleReconnect()
            return
        }
        stopScan()
        clearGatt()
        try{
            ui{connection="Connexion…";deviceName=d.name;strength=d.rssi}
            gatt=adapter.getRemoteDevice(d.address).connectGatt(context,false,gattCallback,BluetoothDevice.TRANSPORT_LE)
                ?: throw IllegalStateException("Impossible d'ouvrir GATT")
            lastSeenId=d.address
            puffLedger=puffStore.load(lastSeenId)
            savedPuffs=Long.MIN_VALUE
            savedUseTime=Long.MIN_VALUE
            savedDay=""
            ui { refreshPuffScreen() }
            prefs.edit().putString("device",lastSeenId).apply()
            say("Connexion à ${d.name}")
        }catch(e:Exception){
            say("Connexion impossible : "+e.message)
            clearGatt()
            scheduleReconnect()
        }
    }
    private fun unexpectedDisconnect(reason:String) {
        if(gatt==null)return
        say(reason)
        clearGatt()
        scheduleReconnect()
    }
    private fun clearGatt() {
        pollActive=false
        pollGeneration++
        uploadSession++
        cancelUploadState()
        notificationSetup=false
        tx=null;rx=null;stream.clear()
        synchronized(txQueue){txQueue.clear();writeBusy=false}
        writeEpoch++
        requestedProfile=null;confirmedWriteAck=false;modeActivationQueued=false
        val old=gatt;gatt=null
        try{old?.disconnect();old?.close()}catch(_:Exception){}
        ui{connected=false;connection="Déconnectée";busy=false;pendingAction=""}
    }
    fun disconnect() {
        retryAllowed=false
        reconnectFailures=0
        cancelRetry()
        stopScan()
        clearGatt()
    }
    private fun subscribe(g:BluetoothGatt) {
        if(g!==gatt || notificationSetup)return
        notificationSetup=true
        try {
            val service=g.getService(Bt200Protocol.SERVICE)
            tx=service?.getCharacteristic(Bt200Protocol.WRITE)
            rx=service?.getCharacteristic(Bt200Protocol.NOTIFY)
            if(tx==null || rx==null){unexpectedDisconnect("Service FFA0 incomplet : vérifier le firmware");return}
            if(!g.setCharacteristicNotification(rx,true)){unexpectedDisconnect("Notifications BLE indisponibles");return}
            val descriptor=rx!!.getDescriptor(Bt200Protocol.CCCD)
            if(descriptor==null){unexpectedDisconnect("Descripteur de notifications absent");return}
            val success=if(Build.VERSION.SDK_INT>=33)
                g.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)==BluetoothStatusCodes.SUCCESS
            else{
                @Suppress("DEPRECATION")
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                @Suppress("DEPRECATION")
                val yes=g.writeDescriptor(descriptor)
                yes
            }
            if(!success) unexpectedDisconnect("Échec de l'abonnement BLE")
        }catch(e:Exception){unexpectedDisconnect("GATT : "+e.message)}
    }
    private fun notificationReady():Boolean = connected
    private val gattCallback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(g:BluetoothGatt,stat:Int,newState:Int) {
            if(g!==gatt){g.close();return}
            if(stat!=BluetoothGatt.GATT_SUCCESS){
                unexpectedDisconnect("Connexion perdue (GATT $stat)");return
            }
            if(newState==BluetoothProfile.STATE_CONNECTED){
                ui{connection="Découverte GATT…"}
                try{g.discoverServices()}catch(e:Exception){say("Découverte impossible : "+e.message)}
            }else if(newState==BluetoothProfile.STATE_DISCONNECTED){
                unexpectedDisconnect("BT200 déconnectée")
            }
        }
        override fun onServicesDiscovered(g:BluetoothGatt,stat:Int){
            if(g!==gatt)return
            if(stat!=0){unexpectedDisconnect("Services GATT introuvables ($stat)");return}
            if(g.getService(Bt200Protocol.SERVICE)==null){unexpectedDisconnect("BT200 : service FFA0 absent");return}
            ui{connection="Négociation BLE…"}
            try {
                if(!g.requestMtu(247))subscribe(g)
                // Some BLE firmwares do not call onMtuChanged even when requestMtu() returns true.
                main.postDelayed({if(g===gatt && !notificationReady())subscribe(g)},2500)
            }catch(_:Exception){subscribe(g)}
        }
        override fun onMtuChanged(g:BluetoothGatt,newMtu:Int,stat:Int){
            if(g!==gatt)return
            ui{mtu=if(stat==0)newMtu else 23}
            subscribe(g)
        }
        override fun onDescriptorWrite(g:BluetoothGatt,desc:BluetoothGattDescriptor,stat:Int){
            if(g!==gatt)return
            if(stat==0){
                cancelRetry()
                reconnectFailures=0
                ui{connected=true;connection="Connectée";notice="Connectée à la BT200"}
                pollActive=true
                enqueue(Bt200Protocol.readStatus())
                (1..3).forEach { enqueue(Bt200Protocol.readProfile(it));enqueue(Bt200Protocol.readTheme(it)) }
                schedulePoll()
            }else unexpectedDisconnect("Abonnement aux notifications refusé ($stat)")
        }
        override fun onCharacteristicWrite(g:BluetoothGatt,ch:BluetoothGattCharacteristic,stat:Int){
            if(g!==gatt)return
            if(currentWriteType==BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) return
            if(stat!=0) say("Échec écriture BLE : $stat")
            writeEpoch++
            synchronized(txQueue){writeBusy=false}
            main.postDelayed({drainQueue()},90)
        }
        override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic,value:ByteArray){
            if(g===gatt)received(value)
        }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g:BluetoothGatt,ch:BluetoothGattCharacteristic){
            if(g===gatt && Build.VERSION.SDK_INT<33)received(ch.value)
        }
    }
    private fun schedulePoll(){
        val generation=pollGeneration
        main.postDelayed({
            if(generation==pollGeneration && pollActive) {
                if(connected && !transferOngoing)enqueue(Bt200Protocol.readStatus())
                schedulePoll()
            }
        },15000)
    }
    private fun enqueue(bytes:ByteArray){
        synchronized(txQueue){txQueue.addLast(bytes)}
        main.post {drainQueue()}
    }
    private fun drainQueue(){
        val g=gatt ?: return
        val c=tx ?: return
        val value:ByteArray
        synchronized(txQueue){
            if(writeBusy || txQueue.isEmpty())return
            value=txQueue.removeFirst()
            writeBusy=true
        }
        try{
            val properties=c.properties
            // Centaurus 1.0.9 uses writeNoResponse on Android. Prefer it when FFA1 supports both.
            val type=if(properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE!=0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            currentWriteType=type
            val epoch=++writeEpoch
            if(Bt200Protocol.u8(value,2) in listOf(2,3,6,1)) ui { log("TX 0x"+Bt200Protocol.u8(value,2).toString(16).uppercase()+" / "+value.size+" octets") }
            val ok=if(Build.VERSION.SDK_INT>=33)
                g.writeCharacteristic(c,value,type)==BluetoothStatusCodes.SUCCESS
            else{
                @Suppress("DEPRECATION")
                c.writeType=type
                @Suppress("DEPRECATION")
                c.value=value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            if(!ok){
                synchronized(txQueue){writeBusy=false}
                say("Écriture refusée par Android (0x"+Bt200Protocol.u8(value,2).toString(16)+")")
                main.postDelayed({drainQueue()},150)
            } else if(type==BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                // WRITE_NO_RESPONSE is not guaranteed to invoke onCharacteristicWrite.
                main.postDelayed({
                    if(epoch==writeEpoch){
                        synchronized(txQueue){writeBusy=false}
                        drainQueue()
                    }
                },110)
            }
        }catch(e:Exception){
            synchronized(txQueue){writeBusy=false}
            say("Écriture Bluetooth : "+e.message)
        }
    }
    private fun received(bytes:ByteArray){
        if(bytes.isEmpty())return
        val frames=stream.accept(bytes)
        for(frame in frames) {
            val incoming=Bt200Protocol.parse(frame) ?: continue
            ui {
                when(incoming) {
                    is Bt200Protocol.Incoming.State -> {
                        status=incoming.value; connected=true
                        recordPuffSnapshot(incoming.value)
                        if(System.currentTimeMillis()-lastHistoryTime>60000L ||
                            incoming.value.puffs!=lastHistoryCount) saveHistory(incoming.value)
                    }
                    is Bt200Protocol.Incoming.ProfileValue -> {
                        profiles[incoming.value.slot]=incoming.value
                        verifyProfileReadback(incoming.value,incoming.responseOpcode)
                    }
                    is Bt200Protocol.Incoming.ThemeValue -> themes[incoming.value.slot]=incoming.value
                    is Bt200Protocol.Incoming.Ack -> receiveAck(incoming)
                    is Bt200Protocol.Incoming.TransferAck -> receiveTransferAck(incoming)
                    is Bt200Protocol.Incoming.Other -> log("Réponse protocole 0x"+incoming.opcode.toString(16))
                }
            }
        }
    }
    private fun verifyProfileReadback(actual:Bt200Protocol.Profile,responseOpcode:Int) {
        val requested=requestedProfile ?: return
        // 0x86 may simply echo a mode-selection command; only a true 0x83 read is evidence.
        if(pendingAction!="save" || responseOpcode!=0x83 || actual.slot!=requested.slot) return
        val ok=actual.mode==requested.mode && if(requested.mode==7){
            actual.curve==requested.curve
        } else {
            actual.watts==requested.watts
        }
        if(ok) {
            notice=if(requested.mode==7)"Courbe VPC enregistrée et relue ✅"
                else "Puissance ${actual.watts} W confirmée par la box ✅"
            log(notice)
            pendingAction="";busy=false;requestedProfile=null;confirmedWriteAck=false;modeActivationQueued=false
            enqueue(Bt200Protocol.readStatus())
        } else {
            log("Relecture différente pour profil ${actual.slot}: envoyé mode=${requested.mode}, W=${requested.watts}; lu mode=${actual.mode}, W=${actual.watts}")
            // Wait for delayed apply/read before determining failure.
        }
    }
    private fun receiveAck(ack:Bt200Protocol.Incoming.Ack){
        when(ack.opcode){
            0x82 -> {
                if(pendingAction!="save" || requestedProfile==null) {
                    log("Accusé 0x82 inattendu (code=${ack.code})")
                } else if(!ack.success) {
                    notice="La BT200 a refusé le réglage (code ${ack.code})"
                    log(notice);busy=false;pendingAction="";requestedProfile=null;confirmedWriteAck=false;modeActivationQueued=false
                } else {
                    confirmedWriteAck=true
                    val wanted=requestedProfile!!
                    notice="Réglage accepté. Vérification sur la box…"
                    log("ACK 0x82 positif • lecture de contrôle du profil ${wanted.slot}")
                    // Apply mode once; an ACK is not required to begin later readback.
                    if(!modeActivationQueued) {
                        modeActivationQueued=true
                        enqueue(Bt200Protocol.readMode(wanted.slot,wanted.mode))
                    }
                    main.postDelayed({
                        if(pendingAction=="save" && requestedProfile===wanted) enqueue(Bt200Protocol.readProfile(wanted.slot))
                    },550)
                }
            }
            0x84 -> {
                notice=if(ack.success)"Profil réinitialisé" else "Réinitialisation refusée"
                busy=false; pendingAction=""
                if(ack.success)(1..3).forEach {enqueue(Bt200Protocol.readProfile(it))}
            }
            0x88 -> {
                notice=if(ack.success)"Thème appliqué" else "Thème refusé (code ${ack.code})"
                busy=false;pendingAction=""
                if(ack.success) (1..3).forEach {enqueue(Bt200Protocol.readTheme(it))}
            }
            0xB0 -> if(transferOngoing) {
                if(ack.success) sendNextChunk() else stopUpload("Transfert refusé par la box")
            }
            0xB2 -> if(transferOngoing) {
                if(ack.success) {
                    transferProgress=1f;notice="Image transférée sur la BT200";transferOngoing=false;busy=false
                    log(notice); (1..3).forEach {enqueue(Bt200Protocol.readTheme(it))}
                }else stopUpload("Échec à la fin du transfert")
            }
        }
    }
    private fun receiveTransferAck(ack:Bt200Protocol.Incoming.TransferAck){
        if(!transferOngoing)return
        if(!ack.success){stopUpload("Paquet image refusé à l'index ${ack.index}");return}
        val data=uploading ?: return
        if(ack.index!=chunkIndex){log("ACK image inattendu : ${ack.index}, attendu ${chunkIndex}");return}
        chunkIndex++
        transferProgress=chunkIndex.toFloat()/data.size
        sendNextChunk()
    }
    private fun sendNextChunk(){
        val chunks=uploading ?:return
        if(chunkIndex>=chunks.size){
            enqueue(Bt200Protocol.uploadFinish())
            log("Envoi terminé, confirmation de la box en attente")
            watchdog(uploadSession)
            return
        }
        enqueue(Bt200Protocol.uploadChunk(chunkIndex,chunks[chunkIndex]))
        watchdog(uploadSession)
    }
    private fun watchdog(id:Int) {
        uploadDeadline=System.currentTimeMillis()+8000
        main.postDelayed({
            if(id==uploadSession && transferOngoing && System.currentTimeMillis()>=uploadDeadline)
                stopUpload("Délai dépassé pendant le transfert d'image")
        },8200)
    }
    private fun cancelUploadState() {
        uploading=null; transferOngoing=false;transferProgress=0f;uploadSession++
    }
    private fun stopUpload(reason:String){
        cancelUploadState();busy=false; notice=reason;log(reason)
        // Never send a cancel/finish frame on unknown transfer state.
    }
    fun refresh(){
        if(!connected)return
        enqueue(Bt200Protocol.readStatus())
        (1..3).forEach{enqueue(Bt200Protocol.readProfile(it));enqueue(Bt200Protocol.readTheme(it))}
        say("Synchronisation en cours…")
    }
    fun selectMode(slot:Int,mode:Int) {
        if(!connected || transferOngoing)return
        try{enqueue(Bt200Protocol.readMode(slot,mode));main.postDelayed({enqueue(Bt200Protocol.readProfile(slot))},500)}
        catch(e:Exception){say("Mode refusé : "+e.message)}
    }
    fun saveProfile(profile:Bt200Protocol.Profile) {
        if(!connected || transferOngoing || busy)return
        try {
            val frame=if(profile.mode==7)Bt200Protocol.saveCurve(profile) else Bt200Protocol.saveProfile(profile)
            requestedProfile=profile
            confirmedWriteAck=false
            modeActivationQueued=false
            busy=true;pendingAction="save";notice="Envoi du profil • attente de confirmation"
            // Write 0x02, select the saved mode via 0x06, then verify the actual 0x83 profile reply.
            // Some firmware builds never emit 0x82: don't depend solely on that acknowledgment.
            enqueue(frame)
            main.postDelayed({
                if(connected && pendingAction=="save" && requestedProfile===profile && !modeActivationQueued) {
                    modeActivationQueued=true
                    enqueue(Bt200Protocol.readMode(profile.slot,profile.mode))
                }
            },450)
            listOf(1050L,2500L,4200L).forEach { delay->
                main.postDelayed({
                    if(connected && pendingAction=="save" && requestedProfile===profile){
                        enqueue(Bt200Protocol.readProfile(profile.slot))
                        if(delay==1050L)enqueue(Bt200Protocol.readStatus())
                    }
                },delay)
            }
            main.postDelayed({
                if(busy && pendingAction=="save" && requestedProfile===profile) {
                    val hadAck=confirmedWriteAck
                    busy=false;pendingAction="";requestedProfile=null;confirmedWriteAck=false;modeActivationQueued=false
                    notice=if(hadAck)"Échec de vérification : les valeurs relues diffèrent (voir journal)"
                        else "Aucun accusé de sauvegarde reçu (voir journal)"
                    log(notice)
                }
            },8000)
        }catch(e:Exception){say("Réglage invalide : "+e.message)}
    }
    fun resetProfile(slot:Int) {
        if(!connected || transferOngoing || busy)return
        ui{busy=true;pendingAction="reset";notice="Réinitialisation en cours…"}
        enqueue(Bt200Protocol.resetProfile(slot))
        main.postDelayed({if(busy && pendingAction=="reset"){busy=false;pendingAction="";notice="Aucune confirmation de la box"}},6000)
    }
    fun applyTheme(slot:Int,theme:Int) {
        if(!connected || transferOngoing || busy)return
        try{
            ui{busy=true;pendingAction="theme";notice="Application du thème…"}
            enqueue(Bt200Protocol.applyTheme(slot,theme))
            main.postDelayed({if(busy && pendingAction=="theme"){busy=false;pendingAction="";notice="Aucune confirmation du thème"}},6000)
        }catch(e:Exception){say("Thème non valide : "+e.message)}
    }
    fun upload(uri:android.net.Uri){
        if(!connected || busy || transferOngoing) {say("Connecte ta BT200 avant d'envoyer une image");return}
        if(mtu<213) {say("MTU Bluetooth $mtu insuffisant pour les paquets de 200 octets");return}
        ui{busy=true;transferProgress=0f;notice="Conversion de l'image en RGB565…"}
        Thread {
            try{
                val input=context.contentResolver.openInputStream(uri) ?: error("Image inaccessible")
                val source=input.use {BitmapFactory.decodeStream(it)} ?: error("Format d'image illisible")
                val full=makeRgb565(source,240,320)
                val small=makeRgb565(source,90,120)
                source.recycle()
                val pixels=full+small
                require(pixels.size==175200)
                val chunks=pixels.toList().chunked(200).map{it.toByteArray()}
                ui {
                    if(!connected){busy=false;return@ui}
                    uploading=chunks;chunkIndex=0;transferOngoing=true;uploadSession++
                    notice="Transfert image : 0%"
                    enqueue(Bt200Protocol.uploadStart(pixels.size,chunks.size))
                    watchdog(uploadSession)
                }
            }catch(e:Exception){ui{busy=false;notice="Image impossible : "+e.message}}
        }.start()
    }
    private fun makeRgb565(source:Bitmap,w:Int,h:Int):ByteArray {
        val ratio=kotlin.math.max(w.toFloat()/source.width,h.toFloat()/source.height)
        val sw=(source.width*ratio).toInt().coerceAtLeast(w)
        val sh=(source.height*ratio).toInt().coerceAtLeast(h)
        val scaled=Bitmap.createScaledBitmap(source,sw,sh,true)
        val cropped=Bitmap.createBitmap(scaled,(sw-w)/2,(sh-h)/2,w,h)
        val pixels=IntArray(w*h);cropped.getPixels(pixels,0,w,0,0,w,h)
        val out=ByteArray(w*h*2)
        for(i in pixels.indices){
            val v=pixels[i]
            val a=(v ushr 24) and 255
            val rr= if(a<100)255 else (v ushr 16) and 255
            val gg= if(a<100)255 else (v ushr 8) and 255
            val bb= if(a<100)255 else v and 255
            val rgb=((rr ushr 3) shl 11) or ((gg ushr 2) shl 5) or (bb ushr 3)
            out[i*2]=(rgb ushr 8).toByte();out[i*2+1]=rgb.toByte()
        }
        if(cropped!==scaled)cropped.recycle()
        if(scaled!==source)scaled.recycle()
        return out
    }
    private fun saveHistory(v:Bt200Protocol.Status){
        val now=System.currentTimeMillis()
        lastHistoryTime=now;lastHistoryCount=v.puffs
        history.add(HistoryPoint(now,v.puffs,v.useTime))
        while(history.size>500) history.removeAt(0)
        val arr=org.json.JSONArray()
        history.forEach {
            val obj=org.json.JSONObject()
            obj.put("ts",it.timestamp);obj.put("count",it.count);obj.put("time",it.time);arr.put(obj)
        }
        prefs.edit().putString("history",arr.toString()).apply()
    }
    fun clearHistory(){
        history.clear();lastHistoryCount=-1
        prefs.edit().remove("history").apply()
        say("Historique local effacé")
    }
    fun onClosed(){disconnect()}
}
