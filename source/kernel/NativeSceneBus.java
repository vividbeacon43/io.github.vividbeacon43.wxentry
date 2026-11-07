package com.zafiro.wxentry.kernel;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/** One native queue hook dispatches to independent, identity-owned receipt adapters. */
final class NativeSceneBus {
    interface Listener { void ended(Object scene,int errorType,int errorCode) throws Throwable; }
    static final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<Listener>();
    static Method hooked;
    static synchronized void register(ClassLoader loader,Listener listener) throws Throwable {
        if(hooked==null) {
            Class<?> queue=Class.forName("com.tencent.mm.modelbase.s1",false,loader),base=Class.forName("com.tencent.mm.modelbase.n1",false,loader);
            Method end=queue.getDeclaredMethod("onSceneEnd",int.class,int.class,String.class,base);
            XposedBridge.hookMethod(end,new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    for(Listener l:listeners)try { l.ended(p.args[3],((Number)p.args[0]).intValue(),((Number)p.args[1]).intValue()); }
                    catch(Throwable ignored) { }
                }
            });
            hooked=end;
        }
        listeners.addIfAbsent(listener);
    }
}
