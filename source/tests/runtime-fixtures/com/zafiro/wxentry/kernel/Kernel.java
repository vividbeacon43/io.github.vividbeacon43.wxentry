package com.zafiro.wxentry.kernel;
final class Kernel {
    static final String WXDIR=System.getProperty("wxentry.test.dir")+"/";
    static final String RP_ON_PATH=WXDIR+"redpacket.on",RP_TOAST_OFF_PATH=WXDIR+"redpacket.toast.off",RP_MODE_PATH=WXDIR+"redpacket.mode";
    static boolean MODULE_OFF,whitelisted=true;static int launched,clicked;static String lastToast;
    static String edition="plus",authorized="plus";
    static final String RP_WL_PATH=WXDIR+"redpacket.whitelist",RP_DELAY_PATH=WXDIR+"redpacket.delay";
    static String receiptAccount() { return w41.z1.u(); }
    static boolean freeRedPacketManualOnly() { return "free".equals(edition); }
    static boolean tryClickKai(android.app.Activity act) { if(!act.visible || !act.button)return false;clicked++;return true; }
    static Object db0() { return new Object(); }
    static android.database.Cursor cursor;
    static android.database.Cursor rawQuery(Object db,String sql,String[] args) { return cursor; }
    static boolean redPacketPremiumEnabled() { return RedPacketPolicy.premium(edition,authorized,MODULE_OFF); }
    static java.util.HashSet<String> RP_SEEN=new java.util.HashSet<String>();
    static java.util.List<String> logs=new java.util.ArrayList<String>();
    static void T(String value) { logs.add(value); }
    static String readText(String path) {
        try { return new String(java.nio.file.Files.readAllBytes(new java.io.File(path).toPath()),"UTF-8"); }
        catch(java.io.IOException failure) { return null; }
    }
    static boolean inWhitelist(String talker) { return whitelisted; }
    static long rpDelayRandom() { return 500; }
    static void openReceiveUI(String url,long id,String talker) { launched++;RP_SEEN.add(url); }
    static void clickKai(android.app.Activity act) { clicked++; }
    static String displayNameOf(String sender) { return "备注名称"; }
    static void toastMain(String message) { lastToast=message; }
}
