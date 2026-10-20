package fr.centaurix.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class CentaurixUpdate(val versionCode:Int, val versionName:String, val apkUrl:String, val sha256:String)

/**
 * Checks versioned metadata published with signed GitHub Actions APKs.
 * Android's package installer verifies the signing certificate; a SHA-256
 * check prevents installing a corrupted or substituted download.
 */
class CentaurixUpdater(private val context:Context) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    var update by mutableStateOf<CentaurixUpdate?>(null); private set
    var dismissed by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    private var closed=false

    fun close(){closed=true;worker.shutdownNow()}
    fun dismiss(){dismissed=true}

    fun check(silent:Boolean=false){
        if(closed || busy)return
        if(BuildConfig.UPDATE_CHANNEL=="none"){
            message="Les mises à jour automatiques nécessitent un APK GitHub Actions."
            return
        }
        busy=true
        if(!silent)message="Recherche de mises à jour…"
        worker.execute {
            try{
                val releases=JSONArray(readUtf8(
                    "https://api.github.com/repos/1337Detailing/Centaurix/releases?per_page=20"))
                var best:CentaurixUpdate?=null
                for(i in 0 until releases.length()){
                    val assets=releases.getJSONObject(i).optJSONArray("assets") ?: continue
                    for(j in 0 until assets.length()){
                        val asset=assets.getJSONObject(j)
                        if(asset.optString("name")!="update.json")continue
                        val meta=JSONObject(readUtf8(asset.getString("browser_download_url")))
                        if(meta.optString("channel")!=BuildConfig.UPDATE_CHANNEL)continue
                        val code=meta.optInt("versionCode",0)
                        if(code<=BuildConfig.VERSION_CODE || (best!=null && code<=best.versionCode))continue
                        val url=meta.optString("apkUrl")
                        val sha=meta.optString("sha256")
                        if(!isTrustedApkUrl(url) || !Regex("[0-9a-fA-F]{64}").matches(sha))continue
                        best=CentaurixUpdate(code,meta.optString("versionName","nouvelle version"),
                            url,sha.lowercase())
                    }
                }
                main.post {
                    if(closed)return@post
                    busy=false
                    if(best!=null){
                        update=best
                        dismissed=false
                        message="Mise à jour ${best.versionName} disponible"
                    }else if(!silent)message="Centaurix est à jour."
                }
            }catch(e:Exception){
                main.post {
                    if(!closed){
                        busy=false
                        if(!silent)message="Impossible de vérifier les mises à jour : ${e.message}"
                    }
                }
            }
        }
    }

    fun install(){
        val candidate=update ?: return
        if(closed || busy)return
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()){
            val settings=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            message="Autorise Centaurix à installer des mises à jour, puis réessaie."
            return
        }
        busy=true
        message="Téléchargement de la mise à jour…"
        worker.execute {
            val destination=File(context.cacheDir,"updates/Centaurix-update.apk")
            try{
                destination.parentFile?.mkdirs()
                val digest=MessageDigest.getInstance("SHA-256")
                var total=0L
                val connection=URL(candidate.apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout=12000
                connection.readTimeout=30000
                connection.setRequestProperty("User-Agent","Centaurix-Android")
                try{
                    if(connection.responseCode !in 200..299)error("HTTP ${connection.responseCode}")
                    connection.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            val buffer=ByteArray(16*1024)
                            while(true){
                                val n=input.read(buffer)
                                if(n<0)break
                                total+=n
                                if(total>150L*1024*1024)error("APK trop volumineux")
                                digest.update(buffer,0,n)
                                output.write(buffer,0,n)
                            }
                        }
                    }
                }finally{connection.disconnect()}
                val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 0xff)}
                if(total==0L || actual!=candidate.sha256)error("Empreinte SHA-256 incorrecte")
                main.post {
                    if(closed)return@post
                    busy=false
                    message="Téléchargement vérifié. Ouverture de l'installation…"
                    try{
                        val uri=FileProvider.getUriForFile(context,
                            "${context.packageName}.fileprovider",destination)
                        val intent=Intent(Intent.ACTION_VIEW).apply{
                            setDataAndType(uri,"application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }catch(e:Exception){
                        message="Installation impossible : ${e.message}"
                    }
                }
            }catch(e:Exception){
                destination.delete()
                main.post { if(!closed){busy=false;message="Mise à jour impossible : ${e.message}"} }
            }
        }
    }

    private fun isTrustedApkUrl(value:String):Boolean = try{
        val url=URL(value)
        url.protocol=="https" && url.host=="github.com" &&
            url.path.startsWith("/1337Detailing/Centaurix/releases/download/") &&
            url.path.endsWith(".apk")
    }catch(_:Exception){false}

    private fun readUtf8(url:String):String{
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=10000
        connection.readTimeout=12000
        connection.setRequestProperty("User-Agent","Centaurix-Android")
        connection.setRequestProperty("Accept","application/vnd.github+json")
        return try{
            if(connection.responseCode !in 200..299)error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }finally{connection.disconnect()}
    }
}
