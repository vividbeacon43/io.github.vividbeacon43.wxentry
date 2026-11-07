package com.zafiro.wxentry.kernel;

/** Shared eligibility and time rules, without edition or UI dependencies. */
final class MediaMessagePolicy {
    static final long WINDOW_MS=180000L;
    static boolean media(int type,int appType,int sent) {
        int low=type&0xffff;
        return sent==0&&(low==43||low==62||(low==49&&appType==6));
    }
    static boolean live(long received,long now){return received>=0&&now>=received&&now-received<WINDOW_MS;}
    static boolean original(boolean moduleOff,boolean selected,int type,int sent,boolean available){
        return !moduleOff&&selected&&(type&0xffff)==3&&sent==0&&available;
    }
}
