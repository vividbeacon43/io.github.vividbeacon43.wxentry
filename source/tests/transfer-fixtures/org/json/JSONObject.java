package org.json;
import java.util.*;
public class JSONObject {
 public static final Object NULL=new Object();final Map<String,Object> data=new LinkedHashMap<>();
 public JSONObject put(String k,Object v){data.put(k,v);return this;}
 public Object opt(String k){return data.get(k);}
 public String optString(String k,String d){Object v=opt(k);return v==null||v==NULL?d:String.valueOf(v);}
 public int optInt(String k,int d){try{return Integer.parseInt(optString(k,""));}catch(Exception e){return d;}}
 public long optLong(String k,long d){try{return Long.parseLong(optString(k,""));}catch(Exception e){return d;}}
 public boolean optBoolean(String k,boolean d){String v=optString(k,"");return v.equals("true")?true:v.equals("false")?false:d;}
 public JSONObject optJSONObject(String k){Object v=opt(k);return v instanceof JSONObject?(JSONObject)v:null;}
 public JSONArray optJSONArray(String k){Object v=opt(k);return v instanceof JSONArray?(JSONArray)v:null;}
 public String toString(){return data.isEmpty()?"{}":data.toString();}
}
