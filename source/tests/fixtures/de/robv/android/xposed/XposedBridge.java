package de.robv.android.xposed;
public class XposedBridge {
    public static XC_MethodHook hook; public static java.lang.reflect.Method method;
    public static java.util.Map<java.lang.reflect.Member,XC_MethodHook> hooks=new java.util.HashMap<>();
    public static Object hookMethod(java.lang.reflect.Member member,XC_MethodHook callback) {
        method=member instanceof java.lang.reflect.Method?(java.lang.reflect.Method)member:null;hook=callback;hooks.put(member,callback);return null;
    }
}
