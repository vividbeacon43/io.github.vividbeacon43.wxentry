package de.robv.android.xposed;
public final class XposedHelpers {public static Object callMethod(Object o,String name,Object...args){try{return o.getClass().getMethod(name).invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}}
