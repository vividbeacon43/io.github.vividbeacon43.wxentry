package android.content;
public class ContentValues {
 public final java.util.Map<String,Object> values=new java.util.LinkedHashMap<>();public ContentValues(){}public ContentValues(ContentValues copy){values.putAll(copy.values);}
 public void put(String k,String v){values.put(k,v);}public void put(String k,Integer v){values.put(k,v);}public void put(String k,Long v){values.put(k,v);}public void put(String k,Boolean v){values.put(k,v);}public void put(String k,byte[] v){values.put(k,v);}
 public String getAsString(String k){Object v=values.get(k);return v==null?null:String.valueOf(v);}public Long getAsLong(String k){Object v=values.get(k);return v==null?null:((Number)v).longValue();}public Integer getAsInteger(String k){Long v=getAsLong(k);return v==null?null:v.intValue();}public boolean containsKey(String k){return values.containsKey(k);}
}
