package fr.centaurix.app;

import java.util.Locale;

/**
 * Read-only Centaurus BT200 notification protocol decoder.
 *
 * Frames use AA 55, opcode, little-endian payload length, payload, CRC16/MODBUS.
 * The tested BT200 status reply encodes CRC high byte first, while the official
 * read request encodes the CRC low byte first. Accept both for compatibility.
 */
public final class Bt200FrameCodec {
    private Bt200FrameCodec() {}

    public static int crc16(byte[] data, int start, int endExclusive) {
        int crc = 0xffff;
        for (int i = start; i < endExclusive; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 1) != 0 ? ((crc >>> 1) ^ 0xa001) : (crc >>> 1);
            }
        }
        return crc & 0xffff;
    }

    public static int expectedLength(byte[] frame, int offset, int available) {
        if (available < 5) return -1;
        if ((frame[offset] & 255) != 0xaa || (frame[offset+1] & 255) != 0x55) return -1;
        int payloadLength = (frame[offset+3] & 255) | ((frame[offset+4] & 255) << 8);
        if (payloadLength > 4096) return -1;
        return 7 + payloadLength;
    }

    public static String crcOrder(byte[] frame) {
        if (frame == null || frame.length < 7) return null;
        if (expectedLength(frame, 0, frame.length) != frame.length) return null;
        int actual = crc16(frame, 0, frame.length-2);
        int crcBigEndian = ((frame[frame.length-2]&255) << 8) | (frame[frame.length-1] &255);
        int crcLittleEndian = (frame[frame.length-2]&255) | ((frame[frame.length-1]&255) << 8);
        if (actual == crcBigEndian) return "BE";
        if (actual == crcLittleEndian) return "LE";
        return null;
    }

    public static String describe(byte[] frame) {
        if (crcOrder(frame) == null) return "Trame non validée";
        int opcode = frame[2] & 255;
        if (opcode == 0x81 && frame.length >= 29) {
            int a = frame[5] & 255;
            int b = frame[6] & 255;
            int type = frame[7] & 255;
            long puffCounter = uint32le(frame, 8);
            long puffTime = uint32le(frame, 12);
            int profile = frame[17] & 255;
            int modeCode = frame[18] & 255;
            String mode = modeName(modeCode);
            int powerWatts = uint16le(frame, 21);
            StringBuilder sb = new StringBuilder();
            sb.append("Accu 1 : ").append(a).append(" % | Accu 2 : ").append(b).append(" %");
            sb.append("\nProfil : ").append(profile).append(" | Mode : ").append(mode);
            if (modeCode == 1 || modeCode == 2) sb.append(" | Puissance : ").append(powerWatts).append(" W");
            sb.append("\nBouffées : ").append(puffCounter).append(" | Temps de chauffe : ").append(puffTime).append(" (unité firmware)");
            sb.append("\nCode type : ").append(type);
            return sb.toString();
        }
        return String.format(Locale.US, "Réponse 0x%02X • longueur utile %d octets", opcode, frame.length - 7);
    }

    private static int uint16le(byte[] bytes, int at) {
        return (bytes[at] & 0xff) | ((bytes[at+1] & 0xff) << 8);
    }
    private static long uint32le(byte[] bytes, int at) {
        return ((long)bytes[at] & 255) | (((long)bytes[at+1] & 255) << 8) |
            (((long)bytes[at+2] & 255) << 16) | (((long)bytes[at+3] & 255) << 24);
    }
    private static String modeName(int code) {
        switch(code) {
            case 1: return "Power";
            case 2: return "Turbo";
            case 3: return "SS316";
            case 4: return "TI";
            case 5: return "NI";
            case 6: return "Voltage";
            case 7: return "VPC";
            default: return "Inconnu (" + code + ")";
        }
    }
}
