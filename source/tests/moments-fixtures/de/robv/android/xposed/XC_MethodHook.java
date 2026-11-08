package de.robv.android.xposed;
public class XC_MethodHook {
 public static class MethodHookParam {public Object[] args;public Object thisObject;public Object result;public boolean thrown;public boolean hasThrowable(){return thrown;}public Object getResult(){return result;}public void setResult(Object value){result=value;}}
 protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public void after(MethodHookParam p)throws Throwable{afterHookedMethod(p);}
}
