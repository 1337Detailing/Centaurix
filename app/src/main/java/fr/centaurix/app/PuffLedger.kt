package fr.centaurix.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Counts only differences between confirmed cumulative counters read ON THE SAME local calendar day.
 * The device does not timestamp individual puffs, so samples crossing midnight are not assigned
 * arbitrarily to one date. First reading is only a baseline, not today's activity.
 * Per-device isolation is handled by PuffHistoryStore.
 */
class PuffLedger {
    data class Day(val date:String,val puffs:Long,val time:Long,val samples:Int)
    data class State(
        val date:String,
        val counter:Long,
        val time:Long,
        val recordedAt:Long
    )
    data class Outcome(val dailyPuffs:Long,val dailyTime:Long,
                       val unassignedPuffs:Long,val unassignedTime:Long,
                       val reset:Boolean,val changed:Boolean)
    private val days=linkedMapOf<String,Day>()
    var baseline:State?=null; private set
    var unassignedPuffs:Long=0;private set
    var unassignedTime:Long=0;private set
    fun history():List<Day> = days.values.sortedBy { it.date }
    fun forDay(day:String):Day=days[day] ?: Day(day,0,0,0)
    fun lastSeven(today:String, clock:(String,Int)->String):List<Day> =
        (6 downTo 0).map { forDay(clock(today,-it)) }
    fun sumPuffs()=days.values.sumOf { it.puffs }
    fun load(records:List<Day>,last:State?,unassigned:Long=0,unassignedSeconds:Long=0){
        days.clear()
        records.filter{it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))}.forEach{
            days[it.date]=it.copy(puffs=it.puffs.coerceAtLeast(0),time=it.time.coerceAtLeast(0),samples=it.samples.coerceAtLeast(0))
        }
        baseline=last;unassignedPuffs=unassigned.coerceAtLeast(0);unassignedTime=unassignedSeconds.coerceAtLeast(0)
    }
    fun accept(date:String,counter:Long,time:Long,now:Long):Outcome {
        require(counter >= 0 && time >= 0)
        val previous=baseline
        if(previous!=null && now<previous.recordedAt) {
            baseline=State(date,counter,time,now)
            return Outcome(forDay(date).puffs,forDay(date).time,unassignedPuffs,unassignedTime,false,true)
        }
        val reset=previous!=null && counter<previous.counter
        val delta=if(previous==null || reset)0L else (counter-previous.counter).coerceAtLeast(0)
        val deltaTime=if(previous==null || time<previous.time)0L else (time-previous.time).coerceAtLeast(0)
        val sameDay=previous!=null && previous.date==date
        if(previous!=null && !sameDay && !reset) {
            unassignedPuffs+=delta;unassignedTime+=deltaTime
        }
        val today=forDay(date)
        val updated=today.copy(
            puffs=today.puffs + if(sameDay)delta else 0,
            time=today.time + if(sameDay)deltaTime else 0,
            samples=today.samples + 1
        )
        days[date]=updated
        baseline=State(date,counter,time,now)
        return Outcome(updated.puffs,updated.time,unassignedPuffs,unassignedTime,reset,true)
    }
    fun clear(){days.clear();baseline=null;unassignedPuffs=0;unassignedTime=0}
}

class PuffHistoryStore(context:android.content.Context) {
    private val prefs=context.getSharedPreferences("centaurix-puffs-v1",android.content.Context.MODE_PRIVATE)
    private fun key(device:String):String="puffs_"+device.replace(":","").uppercase(Locale.ROOT).take(24)
    fun load(device:String):PuffLedger {
        val ledger=PuffLedger()
        val raw=prefs.getString(key(device),null) ?: return ledger
        try{
            val doc=org.json.JSONObject(raw)
            val arr=doc.optJSONArray("days")
            val list=mutableListOf<PuffLedger.Day>()
            if(arr!=null)for(i in 0 until arr.length()){
                val day=arr.optJSONObject(i) ?: continue
                list+=PuffLedger.Day(day.optString("date"),day.optLong("puffs"),day.optLong("time"),day.optInt("samples"))
            }
            val last=doc.optJSONObject("baseline")?.let{
                PuffLedger.State(it.optString("date"),it.optLong("counter"),it.optLong("time"),it.optLong("at"))
            }
            ledger.load(list,last,doc.optLong("unassignedPuffs"),doc.optLong("unassignedTime"))
        }catch(_:Exception){}
        return ledger
    }
    fun store(device:String,ledger:PuffLedger){
        val doc=org.json.JSONObject()
        val records=org.json.JSONArray()
        ledger.history().forEach{
            records.put(org.json.JSONObject().put("date",it.date).put("puffs",it.puffs)
                .put("time",it.time).put("samples",it.samples))
        }
        doc.put("days",records)
        ledger.baseline?.let{
            doc.put("baseline",org.json.JSONObject().put("date",it.date).put("counter",it.counter)
                .put("time",it.time).put("at",it.recordedAt))
        }
        doc.put("unassignedPuffs",ledger.unassignedPuffs)
        doc.put("unassignedTime",ledger.unassignedTime)
        prefs.edit().putString(key(device),doc.toString()).apply()
    }
    fun date(time:Long=System.currentTimeMillis()):String =
        SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Date(time))
    fun dayLabel(iso:String):String = try{
        val df=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
        val date=df.parse(iso)!!
        SimpleDateFormat("EEE dd MMM",Locale.FRANCE).format(date)
    }catch(_:Exception){iso}
    fun shiftDay(iso:String,by:Int):String {
        val formatter=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
        val ms=formatter.parse(iso)?.time ?: return iso
        val cal=java.util.Calendar.getInstance()
        cal.timeInMillis=ms
        cal.add(java.util.Calendar.DAY_OF_YEAR,by)
        return formatter.format(cal.time)
    }
}
