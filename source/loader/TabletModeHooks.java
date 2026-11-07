package com.zafiro.wxentry;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import de.robv.android.xposed.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Opt-in native tablet classification, installed before login/activation. */
final class TabletModeHooks {
    static TabletModeStore store;
    static boolean initialized,requested;
    static volatile boolean active,authorized;
    static String status="off";
    static final Set<ClassLoader> bound=Collections.newSetFromMap(new WeakHashMap<ClassLoader,Boolean>());
    static synchronized void start(ClassLoader ignored,File directory)throws Throwable{
        if(initialized)return;initialized=true;store=new TabletModeStore(directory);
        try{requested=store.read();}catch(Exception failure){status="setting-unreadable";Main.log("tablet setting unreadable; disabled");return;}
        if(!requested){Main.log("tablet mode off");return;}
        if(!refreshAuthorization()){Main.log("tablet premium authorization required; disabled");return;}
        Method attach=Application.class.getDeclaredMethod("attach",Context.class);
        XposedBridge.hookMethod(attach,new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){bind((Context)p.args[0]);}
        });
        status="waiting-context";Main.log("tablet early lifecycle ready");
    }
    static void observeQr(final Class<?> qr,ClassLoader loader){
        try{
            Class<?> scene=Class.forName("com.tencent.mm.modelbase.n1",false,loader);
            final Method type=scene.getMethod("getType");
            Method ended=qr.getDeclaredMethod("onSceneEnd",int.class,int.class,String.class,scene);
            final Field id=qr.getDeclaredField("r"),key=qr.getDeclaredField("s");id.setAccessible(true);key.setAccessible(true);
            XposedBridge.hookMethod(ended,new XC_MethodHook(){
                protected void afterHookedMethod(MethodHookParam p){
                    try{
                        int kind=((Number)type.invoke(p.args[3])).intValue();
                        if(kind!=502&&kind!=503)return;
                        Object value=id.get(p.thisObject),secret=key.get(p.thisObject);
                        boolean present=value instanceof String&&!((String)value).isEmpty(),hasKey=secret instanceof byte[]&&((byte[])secret).length>0;
                        Main.log("tablet qr callback scene="+kind+" error="+p.args[0]+"/"+p.args[1]+" hasId="+present+" hasKey="+hasKey+" threw="+p.hasThrowable());
                    }catch(Throwable failure){Main.log("tablet qr observation skipped "+failure.getClass().getSimpleName());}
                }
            });
        }catch(Throwable unavailable){Main.log("tablet qr observer unavailable "+unavailable.getClass().getSimpleName());}
    }
    static synchronized boolean refreshAuthorization(){
        authorized=Main.tabletFeatureAllowed();
        if(!authorized){active=false;status="premium-required";}
        return authorized;
    }
    static boolean configured(){try{return Main.tabletFeatureAllowed()&&store!=null&&store.read();}catch(Exception failure){return false;}}
    static synchronized void bind(Context context){
        if(!requested||context==null||!"com.tencent.mm".equals(context.getPackageName()))return;
        if(!refreshAuthorization())return;
        ClassLoader loader=context.getClassLoader();if(bound.contains(loader)){active=true;status="native-tablet";return;}
        try{
            PackageInfo pkg=context.getPackageManager().getPackageInfo("com.tencent.mm",0);
            if(pkg.versionCode!=3220||!"8.0.80".equals(pkg.versionName)){status="unsupported-version";return;}
            Method tablet=Class.forName("com.tencent.mm.ui.ok",false,loader).getDeclaredMethod("C");
            if(tablet.getReturnType()!=boolean.class||!Modifier.isStatic(tablet.getModifiers()))throw new NoSuchMethodException("Native tablet contract");
            Class<?> qr=Class.forName("com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI",false,loader);
            qr.getDeclaredMethod("initView");qr.getDeclaredMethod("onCreate",android.os.Bundle.class);
            Class<?> request=Class.forName("cj3.b",false,loader);request.getDeclaredConstructor();
            if(request.getDeclaredMethod("getType").getReturnType()!=int.class)throw new NoSuchMethodException("Native QR contract");
            XposedBridge.hookMethod(tablet,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(authorized)p.setResult(Boolean.TRUE);}});
            bound.add(loader);active=true;status="native-tablet";Main.log("tablet native classification active before login");
            observeQr(qr,loader);
        }catch(Throwable failure){status="unsupported-api";Main.log("tablet native adapter unavailable "+failure.getClass().getSimpleName());}
    }
}
