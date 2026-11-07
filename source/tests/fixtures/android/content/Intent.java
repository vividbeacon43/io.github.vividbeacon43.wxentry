package android.content;
public class Intent {
    public final Class<?> target;public java.util.Map<String,Object> extras=new java.util.HashMap<>();
    public Intent(android.app.Activity activity,Class<?> cls) { target=cls; }
    public Intent putExtra(String name,int value) { extras.put(name,value);return this; }
    public Intent putExtra(String name,long value) { extras.put(name,value);return this; }
    public Intent putExtra(String name,boolean value) { extras.put(name,value);return this; }
    public Intent putExtra(String name,String value) { extras.put(name,value);return this; }
    public boolean hasExtra(String name) { return extras.containsKey(name); }
    public String getStringExtra(String name) { return (String)extras.get(name); }
}
