package fr.centaurix.app

import org.junit.Assert.*
import org.junit.Test

class Bt200ProtocolTest {
    private fun hex(s:String)=s.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
    private val real=hex("AA558116005C6101000000000000000001010100000500000000039480")

    @Test fun powerWritesAcceptZeroUnusedFields() {
        // Hardware profile readbacks may use 0 for voltage/boost in Power mode.
        val p=Bt200Protocol.Profile(slot=1,mode=1,coil=.90,watts=69,temperature=0,
            voltage=0.0,boost=0)
        val encoded=Bt200Protocol.saveProfile(p)
        assertEquals(10,Bt200Protocol.u16(encoded,3))
        assertEquals(69,Bt200Protocol.u16(encoded,9))
        assertEquals("LE",Bt200Protocol.crcOrder(encoded))
    }
    @Test fun turboAndVPCModeSpecificPayloads() {
        val turbo=Bt200Protocol.saveProfile(Bt200Protocol.Profile(1,2,coil=.9,watts=69,
            voltage=0.0,boost=1))
        assertEquals(0x02,Bt200Protocol.u8(turbo,2))
        assertEquals(10,Bt200Protocol.u16(turbo,3))
        assertEquals(0,Bt200Protocol.u8(turbo,turbo.size-3)) // No response profile in Turbo
        val curve=Bt200Protocol.saveCurve(Bt200Protocol.Profile(1,7,
            curve=listOf(50,55,60,65,70,75,80,85)))
        assertEquals(18,Bt200Protocol.u16(curve,3))
        assertEquals("LE",Bt200Protocol.crcOrder(curve))
    }
    @Test fun statusRequestMatchesOfficialApp() =
        assertArrayEquals(hex("AA550100007C14"),Bt200Protocol.readStatus())

    @Test fun realBt200ResponseUsesBigEndianCrc() {
        assertEquals("BE",Bt200Protocol.crcOrder(real))
        assertEquals(0x9480,Bt200Protocol.crc(real,0,real.size-2))
    }
    @Test fun realDeviceStateIsCorrectlyParsed() {
        val state=Bt200Protocol.parse(real) as Bt200Protocol.Incoming.State
        assertEquals(92,state.value.battery1)
        assertEquals(97,state.value.battery2)
        assertEquals(1,state.value.slot)
        assertEquals(1,state.value.mode)
        assertEquals(5,state.value.power)
        assertEquals(0L,state.value.puffs)
    }
    @Test fun fragmentedNotificationsAreReassembled() {
        val stream=Bt200Stream()
        assertTrue(stream.accept(real.copyOfRange(0,20)).isEmpty())
        val frames=stream.accept(real.copyOfRange(20,real.size))
        assertEquals(1,frames.size)
        assertArrayEquals(real,frames[0])
    }
    @Test fun invalidCrcIsRejected() {
        val altered=real.clone();altered[10]=(altered[10].toInt() xor 0x01).toByte()
        assertNull(Bt200Protocol.crcOrder(altered))
        assertTrue(Bt200Stream().accept(altered).isEmpty())
    }
    @Test fun readProfileMatchesOfficialCommand() {
        assertArrayEquals(hex("AA55030100018459"),Bt200Protocol.readProfile(1))
    }
    @Test fun parsedSavedProfileRoundTrip() {
        val p=Bt200Protocol.Profile(2,1,.45,55,150,false,4.2,2)
        val sent=Bt200Protocol.saveProfile(p)
        assertEquals(0x02,Bt200Protocol.u8(sent,2))
        assertEquals(10,Bt200Protocol.u16(sent,3))
        assertEquals("LE",Bt200Protocol.crcOrder(sent))
        val received=Bt200Protocol.packet(0x83,sent.copyOfRange(5,sent.size-2))
        val restored=(Bt200Protocol.parse(received) as Bt200Protocol.Incoming.ProfileValue).value
        assertEquals(p.slot,restored.slot)
        assertEquals(p.mode,restored.mode)
        assertEquals(p.watts,restored.watts)
        assertEquals(p.coil,restored.coil,0.001)
        assertEquals(2,restored.boost) // Response level belongs to Power
    }
    @Test fun vpcRoundtrip() {
        val p=Bt200Protocol.Profile(slot=3,mode=7,curve=listOf(5,20,40,60,70,100,90,5))
        val sent=Bt200Protocol.saveCurve(p)
        assertEquals(18,Bt200Protocol.u16(sent,3))
        val received=Bt200Protocol.packet(0x83,sent.copyOfRange(5,sent.size-2))
        val restored=(Bt200Protocol.parse(received) as Bt200Protocol.Incoming.ProfileValue).value
        assertEquals(p.curve,restored.curve)
    }
    @Test fun invalidPowerIsBlocked(){
        try{Bt200Protocol.saveProfile(Bt200Protocol.Profile(1,1,watts=250)); fail("Expected require()")}
        catch(_:IllegalArgumentException){}
    }
    @Test fun imagePacketBounds() {
        val start=Bt200Protocol.uploadStart(175200,876)
        assertEquals(0x30,Bt200Protocol.u8(start,2))
        assertEquals(6,Bt200Protocol.u16(start,3))
        assertEquals("LE",Bt200Protocol.crcOrder(start))
        val chunk=Bt200Protocol.uploadChunk(13,ByteArray(200){0x55})
        assertEquals(0x31,Bt200Protocol.u8(chunk,2))
        assertEquals(203,Bt200Protocol.u16(chunk,3))
    }
}
