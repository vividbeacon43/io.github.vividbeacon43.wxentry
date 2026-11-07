package com.zafiro.wxentry.kernel;
import android.app.Activity;
import android.content.Intent;
import de.robv.android.xposed.*;
public class VoiceForwardRuntimeTest {
    static int checks;
    static void check(boolean result,String why) { checks++;if(!result)throw new AssertionError(why); }
    static void reset() { Kernel.reset();q71.e1.info=new q71.d1();q71.c1.bytes=12345;com.tencent.mm.ui.chatting.viewitems.sq.complete=true;VoiceForwardSender.sends=VoiceForwardSender.downloads=0;VoiceForwardSender.owner="self";VoiceForwardSender.fail=false;android.os.Handler.queue.clear();VoiceForwardPicker.shows=0; }
    static XC_MethodHook.MethodHookParam param(java.lang.reflect.Method m,Object... args) { XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.method=m;p.args=args;return p; }
    public static void main(String[] args)throws Throwable {
        VoiceForwardRuntime.start(VoiceForwardRuntimeTest.class.getClassLoader());
        check(XposedBridge.hooks.size()==4,"both directions menu hooks only; no native selector interception");
        for(String item:new String[]{"gq","nq"}) {
            reset();Class<?> cls=Class.forName("com.tencent.mm.ui.chatting.viewitems."+item);
            java.lang.reflect.Method create=cls.getDeclaredMethod("S",ak5.h4.class,android.view.View.class,qm5.d.class);
            java.lang.reflect.Method select=cls.getDeclaredMethod("R",android.view.MenuItem.class,wk5.d.class,qm5.d.class);
            ak5.h4 menu=new ak5.h4();menu.add(42,1,0,"native");qm5.d mvvm=new qm5.d();
            XC_MethodHook.MethodHookParam p=param(create,menu,new android.view.View(),mvvm);
            XposedBridge.hooks.get(create).after(p);XposedBridge.hooks.get(create).after(p);
            check(menu.size()==2 && menu.icons==1 && menu.findItem(VoiceForwardRuntime.MENU_ID).getGroupId()==42,"one correctly grouped item "+item);
            wk5.d chat=new wk5.d();
            p=param(select,menu.findItem(VoiceForwardRuntime.MENU_ID),chat,mvvm);XposedBridge.hooks.get(select).invoke(p);
            check(p.stopped && Boolean.TRUE.equals(p.result) && VoiceForwardPicker.shows==1,"custom item consumed and selector opened "+item);
            p=param(select,menu.getItem(0),chat,mvvm);XposedBridge.hooks.get(select).invoke(p);
            check(!p.stopped && VoiceForwardPicker.shows==1,"native menu stays native "+item);
            mvvm.d.b.type=1;menu=new ak5.h4();p=param(create,menu,new android.view.View(),mvvm);XposedBridge.hooks.get(create).after(p);
            check(menu.size()==0,"text item never injected "+item);
            mvvm.d.b.type=34;Kernel.MODULE_OFF=true;p=param(create,menu,new android.view.View(),mvvm);XposedBridge.hooks.get(create).after(p);
            check(menu.size()==0,"disabled module never injects "+item);
            Kernel.MODULE_OFF=false;p.failure=new IllegalStateException();XposedBridge.hooks.get(create).after(p);check(menu.size()==0,"failed native menu untouched "+item);
        }
        for(String edition:new String[]{"free","pro","plus","vip"}) {
            reset();Kernel.edition=edition;Kernel.off=true; // anti-recall switch is independent.
            Activity a=new Activity();com.tencent.mm.storage.f9 msg=new com.tencent.mm.storage.f9();
            VoiceForwardRuntime.open(a,msg);
            check(VoiceForwardPicker.shows==1 && a.launches==0,"all-edition module-owned popup "+edition);
            check(VoiceForwardPicker.message==msg && VoiceForwardPicker.duration==7350,"source and duration preserved "+edition);
            check("self".equals(VoiceForwardPicker.owner),"account binding "+edition);
            check(VoiceForwardSender.sends==0,"opening picker never sends "+edition);
            check(!Kernel.logs.toString().contains("private-voice-file"),"no private filename in log "+edition);
        }
        reset();Activity a=new Activity();Kernel.MODULE_OFF=true;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0,"module disabled");
        reset();a=new Activity();a.finishing=true;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0,"stale activity");
        reset();a=new Activity();com.tencent.mm.storage.f9 msg=new com.tencent.mm.storage.f9();msg.type=3;VoiceForwardRuntime.open(a,msg);check(a.launches==0,"image never enters voice flow");
        reset();a=new Activity();com.tencent.mm.ui.chatting.viewitems.sq.complete=false;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0 && VoiceForwardSender.downloads==1,"incomplete voice requests native download");
        android.os.Handler.next();check(VoiceForwardPicker.shows==1 && VoiceForwardSender.sends==0,"download opens selector without playing or sending");
        reset();a=new Activity();q71.c1.bytes=0;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0 && VoiceForwardSender.downloads==1,"missing audio requests download");
        android.app.ProgressDialog.last.cancel();android.os.Handler.next();check(a.launches==0 && VoiceForwardSender.sends==0,"cancelling load never forwards");
        reset();a=new Activity();q71.c1.bytes=0;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());VoiceForwardSender.owner="different";android.os.Handler.next();check(a.launches==0 && !android.app.ProgressDialog.last.isShowing(),"account switch while loading stops selector");
        reset();a=new Activity();q71.e1.info=null;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0,"missing metadata cannot forward");
        reset();a=new Activity();q71.e1.info.l=0;VoiceForwardRuntime.open(a,new com.tencent.mm.storage.f9());check(a.launches==0,"zero duration cannot forward");
        reset();a=new Activity();msg=new com.tencent.mm.storage.f9();msg.path="";VoiceForwardRuntime.open(a,msg);check(a.launches==0,"missing filename");
        reset();a=new Activity();msg=new com.tencent.mm.storage.f9();msg.id=0;VoiceForwardRuntime.open(a,msg);check(a.launches==0,"missing local message");
        System.out.println("PASS "+checks+" voice forwarding behavior checks");
    }
}
