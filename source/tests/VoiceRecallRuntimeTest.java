package com.zafiro.wxentry.kernel;
import de.robv.android.xposed.*;
public final class VoiceRecallRuntimeTest {
    static int checks;
    static void check(boolean yes,String reason) { checks++;if(!yes)throw new AssertionError(reason); }
    static XC_MethodHook.MethodHookParam invoke(String talker,long server) throws Throwable {
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();
        p.method=XposedBridge.method;
        p.args=new Object[]{talker,Long.valueOf(server),new com.tencent.mm.modelbase.p0(),"test recall",null,null};
        XposedBridge.hook.invoke(p);return p;
    }
    static boolean retained() throws Throwable { return invoke("peer",123).stopped; }
    public static void main(String[] args) throws Throwable {
        VoiceRecallRuntime.start(VoiceRecallRuntimeTest.class.getClassLoader());
        check(XposedBridge.method.getReturnType()==void.class,"exact native revoke contract");
        for(String edition:new String[]{"free","pro","plus","vip"}) {
            Kernel.reset();Kernel.edition=edition;
            boolean stopped=retained();
            // Native consumer mutates the row then publishes the media-cleanup event unless stopped.
            boolean voiceFileExists=true;int rowType=34;
            if(!stopped) { voiceFileExists=false;rowType=10000; }
            check(voiceFileExists && rowType==34,"voice survives in "+edition);
            check(Kernel.tips==1 && Kernel.closed==1,"tip and cursor in "+edition);
        }
        Kernel.reset();Kernel.off=true;
        check(!retained() && Kernel.queries==0,"anti-recall off uses native behavior");
        Kernel.reset();Kernel.MODULE_OFF=true;
        check(!retained() && Kernel.queries==0,"module off uses native behavior");
        Kernel.reset();Kernel.sent=1;
        check(!retained() && Kernel.tips==0 && Kernel.closed==1,"own recall stays native");
        for(int type:new int[]{1,3,43,49,10000,10002}) {
            Kernel.reset();Kernel.type=type;
            check(!retained() && Kernel.tips==0 && Kernel.closed==1,"unrelated message type "+type);
        }
        Kernel.reset();Kernel.type=0x10022;
        check(retained(),"voice low type flags");
        Kernel.reset();Kernel.NO_TIP=true;
        check(retained() && Kernel.tips==0,"no-tip still retains voice");
        Kernel.reset();Kernel.failTip=true;
        check(retained(),"tip failure must not delete retained voice");
        check(!Kernel.logs.toString().contains("private content"),"no exception message leak");
        Kernel.reset();Kernel.failQuery=true;
        check(!retained(),"unresolved message fails open");
        Kernel.reset();Kernel.failRead=true;
        check(!retained() && Kernel.closed==1,"failed read closes cursor");
        Kernel.reset();check(!invoke("other chat",123).stopped && Kernel.closed==1,"same server ID other chat is native");
        Kernel.reset();check(!invoke("peer",124).stopped && Kernel.closed==1,"other server ID is native");
        Kernel.reset();Kernel.local=0;check(!retained(),"missing local message is native");
        Kernel.reset();check(!invoke("",123).stopped && Kernel.queries==0,"empty talker rejected");
        Kernel.reset();check(!invoke("peer",0).stopped && Kernel.queries==0,"invalid server ID rejected");
        System.out.println("PASS "+checks+" voice callback behavior checks");
    }
}
