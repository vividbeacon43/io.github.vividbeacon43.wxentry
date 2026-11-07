package de.robv.android.xposed;
public class XC_MethodHook {
    public static class MethodHookParam {
        public Object[] args; public Object thisObject; public java.lang.reflect.Member method; public boolean stopped; public Object result; public Throwable failure;
        public void setResult(Object value) { stopped=true;result=value; }
        public boolean hasThrowable() { return failure!=null; }
    }
    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam p) throws Throwable {}
    public final void invoke(MethodHookParam p) throws Throwable { beforeHookedMethod(p); }
    public final void after(MethodHookParam p) throws Throwable { afterHookedMethod(p); }
}
