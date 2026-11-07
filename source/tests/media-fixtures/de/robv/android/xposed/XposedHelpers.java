package de.robv.android.xposed;public class XposedHelpers{
 public static Object callMethod(Object o,String name,Object...args){try{for(java.lang.reflect.Method m:o.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(name);}catch(Exception e){throw new RuntimeException(e);}}
 public static long getLongField(Object o,String name){try{return o.getClass().getField(name).getLong(o);}catch(Exception e){throw new RuntimeException(e);}}
 public static Object newInstance(Class<?> c,Object...args){try{for(java.lang.reflect.Constructor<?> x:c.getConstructors())if(x.getParameterTypes().length==args.length)return x.newInstance(args);throw new NoSuchMethodException();}catch(Exception e){throw new RuntimeException(e);}}
}
