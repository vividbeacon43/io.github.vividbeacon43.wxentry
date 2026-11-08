package android.os;
public class Bundle {
    public final java.util.Map<String,Object> values=new java.util.HashMap<String,Object>();
    public void putString(String key,String value) { values.put(key,value); }
    public void putLong(String key,long value) { values.put(key,value); }
}
