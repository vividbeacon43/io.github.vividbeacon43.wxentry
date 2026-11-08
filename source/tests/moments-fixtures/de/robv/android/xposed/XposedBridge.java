package de.robv.android.xposed;
import java.lang.reflect.Method;
public final class XposedBridge {public static Method method;public static XC_MethodHook hook;public static void hookMethod(Method m,XC_MethodHook h){method=m;hook=h;}}
