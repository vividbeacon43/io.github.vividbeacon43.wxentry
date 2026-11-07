package com.zafiro.wxentry.kernel;

/** Cumulative edition rules and one stored choice for the two receive modes. */
final class RedPacketPolicy {
    enum Mode { FOREGROUND, SILENT }
    static int rank(String edition) {
        if("free".equals(edition))return 0;
        if("pro".equals(edition))return 1;
        if("plus".equals(edition))return 2;
        if("vip".equals(edition))return 3;
        return -1;
    }
    static boolean premium(String running,String authorized,boolean disabled) {
        int current=rank(running);
        return !disabled && current>=2 && rank(authorized)>=current;
    }
    static Mode configured(String stored,boolean oldSilentFlag) {
        if(stored==null)return oldSilentFlag?Mode.SILENT:Mode.FOREGROUND;
        return "silent".equals(stored.trim())?Mode.SILENT:Mode.FOREGROUND;
    }
    static Mode effective(Mode configured,boolean premium) {
        return premium && configured==Mode.SILENT?Mode.SILENT:Mode.FOREGROUND;
    }
    static String value(Mode mode) {
        if(mode==null)throw new IllegalArgumentException("Receive mode required");
        return mode==Mode.SILENT?"silent":"foreground";
    }
}
