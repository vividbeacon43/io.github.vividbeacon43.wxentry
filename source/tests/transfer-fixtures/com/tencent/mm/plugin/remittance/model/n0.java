package com.tencent.mm.plugin.remittance.model;
public class n0 extends com.tencent.mm.modelbase.n1 {
 public String d;public Object[] arguments;
 public n0(String tx,String transfer,int cents,String op,String talker,int invalid,String left,String group,int channel,String serial,java.util.Map map,long sub,String payer,String attach){d=op;arguments=new Object[]{tx,transfer,cents,op,talker,invalid,left,group,channel,serial,map,sub,payer,attach};}
 public void onGYNetEnd(int code,String message,org.json.JSONObject json){}
}
