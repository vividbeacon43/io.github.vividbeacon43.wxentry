package de.robv.android.xposed;
public class XposedHelpers {
    public static Object callMethod(Object o,String name,Object... args) { return call(o.getClass(),o,name,args); }
    public static Object callStaticMethod(Class<?> c,String name,Object... args) { return call(c,null,name,args); }
    static Object call(Class<?> c,Object o,String name,Object[] args) {
        try {
            for(Class<?> k=c;k!=null;k=k.getSuperclass())for(java.lang.reflect.Method m:k.getDeclaredMethods()) {
                if(m.getName().equals(name) && m.getParameterTypes().length==args.length) { m.setAccessible(true);return m.invoke(o,args); }
            }
            throw new NoSuchMethodException(name);
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    public static Object getObjectField(Object o,String name) {
        try { java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    public static int getIntField(Object o,String name) { return ((Integer)getObjectField(o,name)).intValue(); }
    public static Object getStaticObjectField(Class<?> c,String name) {
        try { java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}
