package com.zafiro.wxentry;

/** Produce a canonically indexed runtime copy; never changes signed download/cache bytes. */
public final class FrameworkDexCompat {
    static final String API="de.robv.android.xposed.";
    private FrameworkDexCompat() {}
    public static byte[] adapt(byte[] input,String bridge) throws Exception {
        if(bridge==null||!bridge.endsWith(".XposedBridge"))throw new IllegalArgumentException("Unrecognized framework API class name");
        String namespace=bridge.substring(0,bridge.length()-"XposedBridge".length());
        if(API.equals(namespace))return input;
        if(namespace.length()>256||!namespace.matches("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+"))throw new IllegalArgumentException("Invalid framework API namespace");
        if(input==null||input.length<112||input[0]!='d'||input[1]!='e'||input[2]!='x'||input[3]!='\n'||input[7]!=0||u32(input,32)!=input.length||u32(input,36)!=112||u32(input,40)!=0x12345678)
            throw new IllegalArgumentException("Invalid DEX header");
        long size=u32(input,56),offset=u32(input,60);
        if(size<0||offset<112||offset+size*4>input.length)throw new IllegalArgumentException("Invalid DEX string table");
        return DexRewriteEngine.rewrite(input,namespace);
    }
    static int u32(byte[] b,int at) { return (b[at]&255)|((b[at+1]&255)<<8)|((b[at+2]&255)<<16)|((b[at+3]&255)<<24); }
}
