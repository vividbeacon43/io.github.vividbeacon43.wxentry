package com.zafiro.wxentry.kernel;
/** Dependency fake for selector tests. Actual file/pipeline behavior has a separate test compilation. */
final class VoiceForwardSender {
    static int sends,downloads;static String owner="self",recipient;static int duration;static boolean fail;
    static void start(ClassLoader cl) {}
    static String account() { return owner; }
    static void requestDownload(Object msg) {
        downloads++;com.tencent.mm.ui.chatting.viewitems.sq.complete=true;q71.c1.bytes=12345;
    }
    static void send(Object msg,String to,int ms) {
        if(fail)throw new IllegalStateException("private source must not be logged");
        sends++;recipient=to;duration=ms;
    }
}
