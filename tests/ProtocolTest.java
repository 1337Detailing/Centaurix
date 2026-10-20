package fr.centaurix.app;

import java.util.Arrays;

public final class ProtocolTest {
    private static byte[] unhex(String h) {
        byte[] b = new byte[h.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16);
        return b;
    }
    private static void check(boolean value, String label) {
        if(!value)throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
    public static void main(String[] args) {
        byte[] request=unhex("AA550100007C14");
        check(Bt200FrameCodec.crc16(request,0,request.length-2)==0x147c,"Official status request CRC");
        check("LE".equals(Bt200FrameCodec.crcOrder(request)),"Official request little-endian CRC");
        byte[] reply=unhex("AA558116005C6101000000000000000001010100000500000000039480");
        check(reply.length==29,"BT200 real reply has 29 bytes");
        check(Bt200FrameCodec.expectedLength(reply,0,reply.length)==29,"Frame length");
        check(Bt200FrameCodec.crc16(reply,0,reply.length-2)==0x9480,"Real BT200 CRC 0x9480");
        check("BE".equals(Bt200FrameCodec.crcOrder(reply)),"Real BT200 reply CRC big-endian");
        check(Bt200FrameCodec.describe(reply).contains("Accu 1 : 92 % | Accu 2 : 97 %"),"Decode battery percentages");
        check(Bt200FrameCodec.describe(reply).contains("Puissance : 5 W"),"Decode active wattage");
        check(Bt200FrameCodec.describe(reply).contains("Profil : 1 | Mode : Power"),"Decode profile and mode");
        byte[] corrupted=reply.clone(); corrupted[10]^=1;
        check(Bt200FrameCodec.crcOrder(corrupted)==null,"Corruption detected");
        byte[] le=reply.clone();byte last=le[le.length-1];le[le.length-1]=le[le.length-2];le[le.length-2]=last;
        check("LE".equals(Bt200FrameCodec.crcOrder(le)),"Reverse byte order supported");
        check(Bt200FrameCodec.expectedLength(Arrays.copyOf(reply,4),0,4)==-1,"Partial header deferred");
    }
}
