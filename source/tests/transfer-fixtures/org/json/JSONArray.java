package org.json;
public class JSONArray {
 final java.util.List<Object> values=new java.util.ArrayList<>();
 public JSONArray put(Object v){values.add(v);return this;}
 public int length(){return values.size();}
 public JSONObject optJSONObject(int i){Object v=values.get(i);return v instanceof JSONObject?(JSONObject)v:null;}
 public String toString(){return values.isEmpty()?"[]":values.toString();}
}
