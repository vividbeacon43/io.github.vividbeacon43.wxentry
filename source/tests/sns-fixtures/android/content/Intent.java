package android.content;
public class Intent {
 public String pkg,component;public java.util.Map<String,Object> extras=new java.util.LinkedHashMap<>();
 public Intent setClassName(String p,String c){pkg=p;component=c;return this;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}public Intent putExtra(String k,int v){extras.put(k,v);return this;}
 public Intent putStringArrayListExtra(String k,java.util.ArrayList<String> v){extras.put(k,v);return this;}
}
