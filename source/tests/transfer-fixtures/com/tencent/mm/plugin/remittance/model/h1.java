package com.tencent.mm.plugin.remittance.model;
public class h1 extends com.tencent.mm.modelbase.n1 {
 public String d;public Object[] arguments;
 public h1(int op,String tx,String transfer,int invalid,String group,String attach){arguments=new Object[]{op,tx,transfer,invalid,group,attach};}
 public void onGYNetEnd(int code,String message,org.json.JSONObject json){}
}
