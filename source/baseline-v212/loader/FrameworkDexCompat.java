package com.zafiro.wxentry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.Adler32;

/** Adapts an already verified DEX to the API namespace of the active framework.
 * The verified download/cache stays unchanged. Only the runtime copy is edited.
 */
public final class FrameworkDexCompat {
    private static final String API = "de.robv.android.xposed.";
    private FrameworkDexCompat() {}

    public static byte[] adapt(byte[] verifiedDex, String actualBridgeName) throws Exception {
        String suffix = "XposedBridge";
        if (actualBridgeName == null || !actualBridgeName.endsWith("." + suffix))
            throw new IllegalArgumentException("Unrecognized framework API class name");
        String actual = actualBridgeName.substring(0, actualBridgeName.length() - suffix.length());
        if (API.equals(actual)) return verifiedDex;
        if (actual.length() != API.length() || !actual.matches("[A-Za-z_$][A-Za-z0-9_$.]*\\."))
            throw new IllegalArgumentException("Unsupported framework API namespace length or format");
        byte[] fromSlash = API.replace('.', '/').getBytes(StandardCharsets.US_ASCII);
        byte[] toSlash = actual.replace('.', '/').getBytes(StandardCharsets.US_ASCII);
        byte[] fromDot = API.getBytes(StandardCharsets.US_ASCII);
        byte[] toDot = actual.getBytes(StandardCharsets.US_ASCII);
        checkHeader(verifiedDex);
        int count = u32(verifiedDex, 56), table = u32(verifiedDex, 60);
        if (count < 0 || table < 112 || (long)table + (long)count * 4 > verifiedDex.length)
            throw new IllegalArgumentException("Invalid DEX string table");
        byte[] output = null;
        for (int i = 0; i < count; i++) {
            int at = u32(verifiedDex, table + i * 4);
            if (at < 112 || at >= verifiedDex.length) throw new IllegalArgumentException("Invalid DEX string offset");
            int prefixBytes = 0;
            while (true) {
                if (at >= verifiedDex.length || ++prefixBytes > 5)
                    throw new IllegalArgumentException("Invalid DEX string length");
                if ((verifiedDex[at++] & 128) == 0) break;
            }
            int end = at;
            while (end < verifiedDex.length && verifiedDex[end] != 0) end++;
            if (end == verifiedDex.length) throw new IllegalArgumentException("Unterminated DEX string");
            for (int p = at; p + fromSlash.length <= end; p++) {
                byte[] replacement = null;
                if (matches(verifiedDex, p, fromSlash)) replacement = toSlash;
                else if (matches(verifiedDex, p, fromDot)) replacement = toDot;
                if (replacement != null) {
                    if (output == null) output = verifiedDex.clone();
                    System.arraycopy(replacement, 0, output, p, replacement.length);
                    p += replacement.length - 1;
                }
            }
        }
        if (output == null) return verifiedDex;
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        sha.update(output, 32, output.length - 32);
        System.arraycopy(sha.digest(), 0, output, 12, 20);
        Adler32 adler = new Adler32();
        adler.update(output, 12, output.length - 12);
        put32(output, 8, (int)adler.getValue());
        return output;
    }

    private static boolean matches(byte[] data, int at, byte[] needle) {
        for (int i = 0; i < needle.length; i++) if (data[at + i] != needle[i]) return false;
        return true;
    }

    private static void checkHeader(byte[] dex) {
        if (dex == null || dex.length < 112 || dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x'
                || dex[3] != '\n' || dex[4] != '0' || dex[7] != 0
                || u32(dex, 32) != dex.length || u32(dex, 36) != 112 || u32(dex, 40) != 0x12345678)
            throw new IllegalArgumentException("Unsupported or invalid DEX header");
        int version = (dex[5] - '0') * 10 + dex[6] - '0';
        if (version < 35 || version > 40) throw new IllegalArgumentException("Unsupported DEX version");
    }

    private static int u32(byte[] b, int at) {
        return (b[at] & 255) | ((b[at+1] & 255) << 8) | ((b[at+2] & 255) << 16) | ((b[at+3] & 255) << 24);
    }
    private static void put32(byte[] b, int at, int n) {
        for (int i = 0; i < 4; i++) b[at+i] = (byte)(n >>> (i*8));
    }
}
