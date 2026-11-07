package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** All-edition voice menu entry, silent loading and module-owned batch picker. */
final class VoiceForwardRuntime {
    static final int MENU_ID = 0x57584601;
    private static Class<?> voiceLogic, voiceFiles, voiceItems;
    private static volatile boolean ready;
    private VoiceForwardRuntime() {}

    static void start(ClassLoader loader) throws Throwable {
        voiceLogic = Class.forName("q71.e1", false, loader);
        voiceFiles = Class.forName("q71.c1", false, loader);
        voiceItems = Class.forName("com.tencent.mm.ui.chatting.viewitems.sq", false, loader);
        Class<?> message = Class.forName("com.tencent.mm.storage.f9", false, loader);
        Class<?> menu = Class.forName("ak5.h4", false, loader);
        Class<?> mvvm = Class.forName("qm5.d", false, loader);
        Class<?> chat = Class.forName("wk5.d", false, loader);
        VoiceForwardSender.start(loader);
        if (!Menu.class.isAssignableFrom(menu))
            throw new NoSuchMethodException("voice UI/menu contract");
        if (voiceLogic.getDeclaredMethod("k", String.class).getReturnType()
                != Class.forName("q71.d1", false, loader)
                || voiceFiles.getDeclaredMethod("g", message, String.class).getReturnType() != int.class
                || voiceItems.getDeclaredMethod("p", message).getReturnType() != boolean.class)
            throw new NoSuchMethodException("voice metadata contract");
        Class<?>[] items = {
            Class.forName("com.tencent.mm.ui.chatting.viewitems.gq", false, loader),
            Class.forName("com.tencent.mm.ui.chatting.viewitems.nq", false, loader)
        };
        Method[] create = new Method[items.length], select = new Method[items.length];
        for (int i=0; i<items.length; i++) {
            create[i] = items[i].getDeclaredMethod("S", menu, View.class, mvvm);
            select[i] = items[i].getDeclaredMethod("R", MenuItem.class, chat, mvvm);
            if (create[i].getReturnType()!=boolean.class || select[i].getReturnType()!=boolean.class)
                throw new NoSuchMethodException("voice menu callback contract");
        }
        // Resolve every contract before installing. Partial installation stays inactive.
        for (int i=0; i<items.length; i++) {
            XposedBridge.hookMethod(create[i], new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    if (!ready || Kernel.MODULE_OFF || p.hasThrowable()) return;
                    try {
                        Object msg = message(p.args[2]);
                        if (!isVoice(msg) || !(p.args[0] instanceof Menu)) return;
                        Menu menu = (Menu)p.args[0];
                        if (menu.findItem(MENU_ID)!=null) return;
                        int group = menu.size()>0 ? menu.getItem(0).getGroupId() : 0;
                        MenuItem entry=menu.add(group, MENU_ID, 0, "转发语音");
                        if(p.args[1] instanceof View)
                            entry.setIcon(new VoiceForwardIcon(((View)p.args[1]).getContext()));
                        Kernel.T("ARVF menu-added");
                    } catch (Throwable t) { error("menu",t); }
                }
            });
            XposedBridge.hookMethod(select[i], new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (!ready || !(p.args[0] instanceof MenuItem)
                            || ((MenuItem)p.args[0]).getItemId()!=MENU_ID) return;
                    p.setResult(Boolean.TRUE); // Our item is never passed to the native item-ID switch.
                    Activity activity = null;
                    try {
                        activity = (Activity)XposedHelpers.callMethod(p.args[1], "g");
                        open(activity, message(p.args[2]));
                    } catch (Throwable t) {
                        error("open",t);
                        if (activity!=null) showNotice(activity,"暂时无法转发这条语音");
                    }
                }
            });
        }
        ready = true;
        try { VoiceForwardLayout.start(loader); }
        catch(Throwable failure) { error("layout-start",failure); }
        Kernel.T("ARVF ready base=true itemHooks=4 native=VoiceMsgFeatureService");
    }

    private static Object message(Object mvvm) {
        if (mvvm==null) return null;
        Object wrapper = XposedHelpers.getObjectField(mvvm,"d");
        return wrapper==null ? null : XposedHelpers.getObjectField(wrapper,"b");
    }

    private static boolean isVoice(Object msg) {
        if (msg==null) return false;
        return (((Number)XposedHelpers.callMethod(msg,"getType")).intValue()&0xffff)==34;
    }

    static void open(Activity activity,Object msg) throws Throwable {
        if (activity==null || activity.isFinishing()) return;
        if (Kernel.MODULE_OFF) { showNotice(activity,"模块未启用"); return; }
        if (!isVoice(msg)) return;
        if (VoiceForwardPicker.showActive(activity)) return;
        long id=((Number)XposedHelpers.callMethod(msg,"getMsgId")).longValue();
        String file=(String)XposedHelpers.callMethod(msg,"z0");
        if (id<=0 || file==null || file.length()==0) { unavailable(activity); return; }
        if (!downloadReady(msg,file)) { loadBeforePicker(activity,msg);return; }
        int bytes=((Number)XposedHelpers.callStaticMethod(voiceFiles,"g",msg,file)).intValue();
        if (bytes<=0) { unavailable(activity); return; }
        Object info=XposedHelpers.callStaticMethod(voiceLogic,"k",file);
        if (info==null) { unavailable(activity); return; }
        int duration=XposedHelpers.getIntField(info,"l");
        if (duration<=0) { unavailable(activity); return; }

        String owner=VoiceForwardSender.account();
        if(owner==null || owner.length()==0) { unavailable(activity);return; }
        VoiceForwardPicker.show(activity,msg,owner,duration);
        Kernel.T("ARVF picker-opened module=true");
    }

    private static boolean downloadReady(Object msg,String file) {
        return Boolean.TRUE.equals(XposedHelpers.callStaticMethod(voiceItems,"p",msg))
                && ((Number)XposedHelpers.callStaticMethod(voiceFiles,"g",msg,file)).intValue()>0;
    }
    private static void loadBeforePicker(final Activity activity,final Object msg) throws Throwable {
        final String owner=VoiceForwardSender.account();
        final AtomicBoolean cancelled=new AtomicBoolean();
        final ProgressDialog progress=new ProgressDialog(activity);
        progress.setMessage("正在加载语音…");progress.setCancelable(true);
        progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) { cancelled.set(true); }
        });
        progress.show();
        try { VoiceForwardSender.requestDownload(msg); }
        catch(Throwable t) { progress.dismiss();throw t; }
        final Handler handler=new Handler(Looper.getMainLooper());
        final long until=System.currentTimeMillis()+30000;
        Kernel.T("ARVF download-requested");
        handler.postDelayed(new Runnable() {
            public void run() {
                if(cancelled.get() || !progress.isShowing() || activity.isFinishing())return;
                try {
                    if(Kernel.MODULE_OFF || !owner.equals(VoiceForwardSender.account())) { progress.dismiss();return; }
                    String file=(String)XposedHelpers.callMethod(msg,"z0");
                    if(file!=null && downloadReady(msg,file)) {
                        progress.dismiss();Kernel.T("ARVF download-ready");open(activity,msg);return;
                    }
                    if(System.currentTimeMillis()>=until) {
                        progress.dismiss();showNotice(activity,"语音加载超时，请检查网络后重试");return;
                    }
                    handler.postDelayed(this,500);
                } catch(Throwable t) { progress.dismiss();error("download",t);showNotice(activity,"语音加载失败，请稍后重试"); }
            }
        },300);
    }

    private static void unavailable(Activity activity) {
        showNotice(activity,"语音文件不可用，请先确认能够正常播放");
        Kernel.T("ARVF source-unavailable");
    }
    private static void error(String stage,Throwable failure) {
        while(failure.getCause()!=null && failure.getCause()!=failure)failure=failure.getCause();
        String code=failure.getMessage();
        Kernel.T("ARVF "+stage+"-error "+failure.getClass().getSimpleName()
                +(code!=null && code.matches("VF_[A-Z_]{1,40}")?" "+code:""));
    }
    private static void showNotice(Activity activity,String message) {
        XposedHelpers.callStaticMethod(Kernel.class,"toast",activity,message);
    }
}
