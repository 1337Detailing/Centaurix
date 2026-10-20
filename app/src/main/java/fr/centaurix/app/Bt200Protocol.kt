package fr.centaurix.app

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Documented from Centaurus 1.0.9 and tested against a real BT200 status reply.
 * Frame: AA 55 | opcode | payload size LE16 | payload | CRC16/MODBUS.
 * The real BT200 returned its CRC high-byte first; the official app sends low-byte first.
 */
object Bt200Protocol {
    private const val MAX_PAYLOAD = 4096
    val SERVICE = java.util.UUID.fromString("0000ffa0-0000-1000-8000-00805f9b34fb")
    val WRITE = java.util.UUID.fromString("0000ffa1-0000-1000-8000-00805f9b34fb")
    val NOTIFY = java.util.UUID.fromString("0000ffa3-0000-1000-8000-00805f9b34fb")
    val CCCD = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    fun u8(a: ByteArray, i: Int) = a[i].toInt() and 255
    fun u16(a: ByteArray, i: Int) = u8(a,i) or (u8(a,i+1) shl 8)
    fun u32(a: ByteArray, i: Int): Long =
        u8(a,i).toLong() or (u8(a,i+1).toLong() shl 8) or (u8(a,i+2).toLong() shl 16) or (u8(a,i+3).toLong() shl 24)
    fun crc(a: ByteArray, start: Int = 0, end: Int = a.size): Int {
        var x=65535
        for (i in start until end) {
            x = x xor u8(a,i)
            repeat(8) { x = if ((x and 1)==1) (x ushr 1) xor 0xA001 else x ushr 1 }
        }
        return x and 65535
    }
    fun le16(v:Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    fun le32(v:Int) = byteArrayOf(v.toByte(),(v ushr 8).toByte(),(v ushr 16).toByte(),(v ushr 24).toByte())
    fun packet(op:Int, payload:ByteArray=byteArrayOf()):ByteArray {
        require(op in 0..255 && payload.size <= MAX_PAYLOAD)
        val buf=ByteArrayOutputStream()
        buf.write(byteArrayOf(0xAA.toByte(),0x55,op.toByte()))
        buf.write(le16(payload.size))
        buf.write(payload)
        val a=buf.toByteArray()
        buf.write(le16(crc(a)))
        return buf.toByteArray()
    }
    fun crcOrder(frame:ByteArray):String? {
        if (frame.size < 7 || u8(frame,0)!=0xAA || u8(frame,1)!=0x55 ||
            u16(frame,3)+7!=frame.size) return null
        val want=crc(frame,0,frame.size-2)
        val le=u16(frame,frame.size-2)
        val be=(u8(frame,frame.size-2) shl 8) or u8(frame,frame.size-1)
        return when (want) { le -> "LE"; be -> "BE"; else -> null }
    }
    fun readStatus()=packet(0x01)
    fun readProfile(slot:Int)=packet(0x03,byteArrayOf(slot.checkedSlot()))
    fun readTheme(slot:Int)=packet(0x07,byteArrayOf(slot.checkedSlot()))
    fun readMode(slot:Int,mode:Int)=packet(0x06,byteArrayOf(slot.checkedSlot(),mode.checkedMode()))
    fun resetProfile(slot:Int)=packet(0x04,byteArrayOf(slot.checkedSlot()))
    fun applyTheme(slot:Int,theme:Int):ByteArray {
        require(theme in 1..5)
        return packet(0x08,byteArrayOf(slot.checkedSlot(),1,theme.toByte()))
    }
    private fun Int.checkedSlot():Byte { require(this in 1..3); return toByte() }
    private fun Int.checkedMode():Byte { require(this in 1..7); return toByte() }
    val modes = listOf("Power","Turbo","SS316","TI","NI","Voltage","VPC")
    fun name(mode:Int)=modes.getOrElse(mode-1){"Inconnu ($mode)"}

    data class Status(val battery1:Int, val battery2:Int, val type:Int, val puffs:Long,
                      val useTime:Long, val slot:Int, val mode:Int, val power:Int)
    data class Profile(
        val slot:Int, val mode:Int, val coil:Double=0.0, val watts:Int=5,
        val temperature:Int=100, val fahrenheit:Boolean=false, val voltage:Double=3.7,
        val boost:Int=2, val curve:List<Int> = List(8){5}
    )
    data class Theme(val slot:Int,val type:Int,val variant:Int)
    sealed class Incoming {
        data class State(val value:Status):Incoming()
        data class ProfileValue(val value:Profile):Incoming()
        data class ThemeValue(val value:Theme):Incoming()
        data class Ack(val opcode:Int, val success:Boolean, val code:Int):Incoming()
        data class TransferAck(val opcode:Int, val index:Int, val success:Boolean):Incoming()
        data class Other(val opcode:Int, val payload:ByteArray):Incoming()
    }

    fun parse(frame:ByteArray):Incoming? {
        if(crcOrder(frame)==null) return null
        val opcode=u8(frame,2)
        val p=frame.copyOfRange(5,frame.size-2)
        when(opcode) {
            0x81 -> if(p.size>=18) {
                val slot=u8(p,12).coerceIn(1,3)
                val mode=u8(p,13)
                return Incoming.State(Status(u8(p,0),u8(p,1),u8(p,2),u32(p,3),u32(p,7),
                    slot,mode,if (p.size>=18) u16(p,16) else 0))
            }
            0x83,0x86 -> if(p.size>=2) {
                val slot=u8(p,0).coerceIn(1,3); val mode=u8(p,1)
                if(mode==7 && p.size>=18) return Incoming.ProfileValue(Profile(slot,mode,curve=(0 until 8).map { u16(p,2+it*2) }))
                if(p.size>=10) {
                    val temp=u16(p,6)
                    return Incoming.ProfileValue(Profile(slot,mode,u16(p,2)/100.0,u16(p,4),
                        temp and 0x7fff,temp and 0x8000 !=0,u8(p,8)/10.0,u8(p,9)))
                }
            }
            0x87 -> if(p.size>=3) return Incoming.ThemeValue(Theme(u8(p,0),u8(p,1),u8(p,2)))
            0x82,0x84,0x88,0xB0,0xB2 -> if(p.isNotEmpty())
                return Incoming.Ack(opcode,u8(p,0)==1,u8(p,0))
            0xB1 -> if(p.size>=3) return Incoming.TransferAck(opcode,u16(p,0),u8(p,2)==1)
        }
        return Incoming.Other(opcode,p)
    }

    /** The official app's Power/Turbo/TC/Voltage profile command: 02 with 10-byte payload. */
    fun saveProfile(profile:Profile):ByteArray {
        val slot=profile.slot.checkedSlot()
        val mode=profile.mode.checkedMode()
        require(profile.mode!=7) { "Use saveCurve for VPC" }
        require(profile.coil in 0.0..10.0)
        require(profile.watts in 5..200)
        // Fields unused in Power/TC can legitimately be zero in actual BT200 profile replies.
        // The old unconditional validation silently prevented saving Power profiles.
        require(profile.voltage in 0.0..8.0)
        require(profile.boost in 0..3)
        if (profile.mode==6) require(profile.voltage in 0.7..8.0)
        if (profile.mode==2) require(profile.boost in 1..3)
        if (profile.mode in 3..5) require(profile.watts<=100)
        if (profile.mode in 3..5) require(if (profile.fahrenheit) profile.temperature in 200..600 else profile.temperature in 100..315)
        val rawTemp=(profile.temperature and 0x7fff) or (if(profile.fahrenheit) 0x8000 else 0)
        val buf=ByteArrayOutputStream()
        buf.write(slot.toInt());buf.write(mode.toInt())
        buf.write(le16((profile.coil*100).roundToInt()))
        buf.write(le16(profile.watts))
        buf.write(le16(rawTemp))
        buf.write((profile.voltage*10).roundToInt())
        buf.write(profile.boost)
        return packet(0x02,buf.toByteArray())
    }
    fun saveCurve(profile:Profile):ByteArray {
        profile.slot.checkedSlot()
        require(profile.curve.size==8 && profile.curve.all { it in 5..200 })
        val buf=ByteArrayOutputStream()
        buf.write(profile.slot); buf.write(7)
        profile.curve.forEach { buf.write(le16(it)) }
        return packet(0x02,buf.toByteArray())
    }

    /** Two device images: 240x320 + 90x120, RGB565 big-endian pixel order. */
    fun uploadStart(pixelByteCount:Int,chunks:Int):ByteArray {
        require(pixelByteCount==((240*320+90*120)*2) && chunks in 1..65535)
        val buf=ByteArrayOutputStream()
        buf.write(le32(pixelByteCount));buf.write(le16(chunks))
        return packet(0x30,buf.toByteArray())
    }
    fun uploadChunk(index:Int, data:ByteArray):ByteArray {
        require(index in 0..65535 && data.size in 1..200)
        val buf=ByteArrayOutputStream()
        buf.write(le16(index));buf.write(data.size);buf.write(data)
        return packet(0x31,buf.toByteArray())
    }
    fun uploadFinish()=packet(0x32)
}
/** Retains partial BLE notifications and validates frame CRC before emitting packets. */
class Bt200Stream {
    private var pending=byteArrayOf()
    fun clear(){pending=byteArrayOf()}
    fun accept(bytes:ByteArray):List<ByteArray> {
        if(bytes.isEmpty()) return emptyList()
        pending+=bytes
        if(pending.size>16384){pending=byteArrayOf();return emptyList()}
        val frames=mutableListOf<ByteArray>()
        var offset=0
        while(offset+7<=pending.size){
            if(Bt200Protocol.u8(pending,offset)!=0xAA || Bt200Protocol.u8(pending,offset+1)!=0x55){offset++;continue}
            val size=Bt200Protocol.u16(pending,offset+3)
            if(size>4096){offset++;continue}
            if(offset+7+size>pending.size)break
            val f=pending.copyOfRange(offset,offset+7+size)
            if(Bt200Protocol.crcOrder(f)!=null){frames+=f;offset+=7+size}else offset++
        }
        if(offset>0) pending=pending.copyOfRange(offset,pending.size)
        return frames
    }
}
