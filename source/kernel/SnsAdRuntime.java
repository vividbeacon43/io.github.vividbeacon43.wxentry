package com.zafiro.wxentry.kernel;
import java.util.List;
import de.robv.android.xposed.*;

/** Filters the native improved timeline page after its ordinary/ad rows are merged. */
final class SnsAdRuntime {
    private static boolean ready;
    static void start(ClassLoader loader)throws Throwable{
        if(ready)return;
        Class<?> store=Class.forName("el4.g",false,loader),request=Class.forName("zt3.c",false,loader);
        final Class<?> model=Class.forName("cl4.p",false,loader);model.getDeclaredMethod("isAd");
        XposedBridge.hookMethod(store.getDeclaredMethod("X7",request),new XC_MethodHook(){
            protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()||!Kernel.snsAdBlockEnabled()||!(p.getResult() instanceof List))return;
                try{
                    List<Object> source=(List<Object>)p.getResult();
                    List<Object> kept=SnsAdPolicy.filter(source,true,new SnsAdPolicy.Marker<Object>(){public boolean isAd(Object row){return model.isInstance(row)&&Boolean.TRUE.equals(XposedHelpers.callMethod(row,"isAd"));}});
                    if(kept!=source){p.setResult(kept);Kernel.T("ARSNSADS filtered="+(source.size()-kept.size())+" remaining="+kept.size());}
                }catch(Throwable failure){Kernel.T("ARSNSADS filter-error "+failure.getClass().getSimpleName());}
            }
        });
        ready=true;Kernel.T("ARSNSADS ready nativeMarker=true databaseUnchanged=true");
    }
}
