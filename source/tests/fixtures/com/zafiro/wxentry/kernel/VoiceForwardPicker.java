package com.zafiro.wxentry.kernel;
final class VoiceForwardPicker {
    static int shows,duration;static Object message;static String owner;
    static boolean showActive(android.app.Activity a){return false;}
    static void show(android.app.Activity a,Object m,String o,int d){shows++;message=m;owner=o;duration=d;}
}
