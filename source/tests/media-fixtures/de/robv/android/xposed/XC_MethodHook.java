package de.robv.android.xposed;
public class XC_MethodHook {
 public static class MethodHookParam {public Object[] args;public Object thisObject,result;public java.lang.reflect.Member method;public boolean stopped;public Throwable failure;public void setResult(Object o){result=o;stopped=true;}public Object getResult(){return result;}public boolean hasThrowable(){return failure!=null;}}
 protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public final void invoke(MethodHookParam p)throws Throwable{beforeHookedMethod(p);}public final void after(MethodHookParam p)throws Throwable{afterHookedMethod(p);}
}
