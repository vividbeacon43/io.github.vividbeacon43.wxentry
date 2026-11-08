package com.zafiro.wxentry.kernel;
import android.os.Handler;import org.json.*;import de.robv.android.xposed.*;import java.lang.reflect.*;
import com.tencent.mm.plugin.remittance.model.*;
public class TransferTest {
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static String xml(String id){return "<msg><appmsg><type>2000</type><wcpayinfo><paysubtype>1</paysubtype><transcationid>tx_"+id+"</transcationid><transferid>"+id+"</transferid><payer_username>peer</payer_username><receiver_username>self</receiver_username><total_fee>123</total_fee><fee_type>CNY</fee_type><invalidtime>100</invalidtime><attach>extra</attach></wcpayinfo></appmsg></msg>";}
 static JSONObject query(int status){return new JSONObject().put("trans_status",status).put("fee",123).put("fee_type","CNY").put("payer_name","peer").put("receiver_name","self").put("is_payer",false);}
 static void callback(Object scene,JSONObject json,int error)throws Throwable{
  if(json!=null){Method parsed=scene.getClass().getDeclaredMethod("onGYNetEnd",int.class,String.class,JSONObject.class);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=scene;p.args=new Object[]{0,"",json};XposedBridge.hooks.get(parsed).after(p);}
  RedPacketRuntimeTest.callback(scene,error,0);
 }
 static Object last(){return zp0.j1.holder.b.last;}
 static void receive(String id){TransferRuntime.received(xml(id),"peer",System.currentTimeMillis());Handler.advance(1000);}
 public static void main(String[] args)throws Throwable{
  String xml=xml("id");check(TransferEngine.parse("self","peer",xml,false)!=null,"ordinary incoming transfer parsed");
  check(TransferEngine.parse("self","peer",xml,true)==null,"outgoing ignored");
  check(TransferEngine.parse("self","peer",xml.replace("<paysubtype>1","<paysubtype>3"),false)==null,"receipt/refund notices ignored");
  for(String bad:new String[]{xml.replace("<total_fee>123","<total_fee>0"),xml.replace("<receiver_username>self","<receiver_username>other"),xml.replace("<payer_username>peer","<payer_username>other"),xml.replace("<fee_type>CNY","<fee_type>USD"),xml.replace("<transferid>id</transferid>","<transferid>id</transferid><transferid>x</transferid>"),"<!DOCTYPE msg [<!ENTITY x SYSTEM 'file:///invalid'>]>"+xml})check(TransferEngine.parse("self","peer",bad,false)==null,"invalid identity/XML rejected");
  check(TransferEngine.parse("self","room@chatroom","peer:\n"+xml,false)!=null,"explicit intended group recipient supported");
  check(TransferEngine.parse("self","room@chatroom","other:\n"+xml,false)==null,"group sender prefix crosschecked");
  RedPacketRuntime.start(TransferTest.class.getClassLoader());TransferRuntime.start(TransferTest.class.getClassLoader());check(TransferRuntime.ready(),"native adapter ready alongside red packet bus");
  receive("default-off");check(zp0.j1.holder.b.submitted==0,"transfer defaults disabled");
  ReceiptConfig c=ReceiptStore.current(ReceiptConfig.Feature.TRANSFER);c.enabled=true;c.delayEnabled=true;c.minDelayMs=c.maxDelayMs=1000;ReceiptStore.save(ReceiptConfig.Feature.TRANSFER,"self",c);
  TransferRuntime.received(xml("ok"),"peer",System.currentTimeMillis());Handler.advance(999);check(zp0.j1.holder.b.submitted==0,"no query before selected seconds");Handler.advance(1);
  check(last() instanceof h1,"query before confirm");h1 initial=(h1)last();check(initial.arguments[1].equals("tx_ok")&&initial.arguments[2].equals("ok"),"query transaction/transfer IDs exact");
  callback(initial,query(2000),0);check(last() instanceof n0,"receivable native status triggers confirmation");n0 confirm=(n0)last();
  check(confirm.arguments[3].equals("confirm")&&confirm.arguments[4].equals("peer")&&confirm.arguments[8].equals(1)&&confirm.arguments[12].equals("peer"),"confirm original talker/current balance channel/payer");
  int submitted=zp0.j1.holder.b.submitted;callback(initial,query(2000),0);check(zp0.j1.holder.b.submitted==submitted,"duplicate callback not confirmed twice");
  callback(confirm,new JSONObject(),0);check(last() instanceof h1 && ((h1)last()).arguments[0].equals(1),"confirmation is followed by independent status query");
  check(!RedPacketRuntime.history().contains("已接收"),"confirmation callback alone never claims success");callback(last(),query(2001),0);
  check(RedPacketRuntime.history().contains("已接收")&&Kernel.lastToast.contains("1.23"),"only verified received status shown as success");
  submitted=zp0.j1.holder.b.submitted;receive("ok");check(zp0.j1.holder.b.submitted==submitted,"duplicate message remains reserved");
  for(int variant=0;variant<10;variant++){
   receive("bad"+variant);Object request=last();JSONObject q=query(2000);
   if(variant==0)q.put("receiver_name","other");if(variant==1)q.put("payer_name","other");if(variant==2)q.put("fee",999);if(variant==3)q.put("is_payer",true);if(variant==4)q.put("trans_status",2001);if(variant==5)q.put("delay_confirm_flag",1);if(variant==6)q.put("intercept_win",new JSONObject().put("text","guide"));if(variant==7)q.put("recv_account_info",new JSONObject().put("recv_channel",new JSONArray().put(new JSONObject().put("recv_channel_type",2).put("recv_channel_avail_state",1))));
   submitted=zp0.j1.holder.b.submitted;callback(request,variant==8?null:q,variant==9?4:0);
   check(zp0.j1.holder.b.submitted==submitted,"mismatch/manual guidance/missing parse/error blocks confirmation "+variant);
  }
  receive("confirm-guide");callback(last(),query(2000),0);submitted=zp0.j1.holder.b.submitted;
  callback(last(),new JSONObject().put("real_name_info",new JSONObject().put("guide_flag","1")),0);check(zp0.j1.holder.b.submitted==submitted,"confirmation guidance stops follow-up");
  TransferRuntime.received(xml("switch"),"peer",System.currentTimeMillis());Handler.advance(0);submitted=zp0.j1.holder.b.submitted;w41.z1.current="other";Handler.advance(1000);w41.z1.current="self";check(zp0.j1.holder.b.submitted==submitted,"account change during delay cancels");
  receive("list-change");Object blocked=last();c.person.rule=ReceiptConfig.Rule.BLACK;c.person.black.add("peer");ReceiptStore.save(ReceiptConfig.Feature.TRANSFER,"self",c);submitted=zp0.j1.holder.b.submitted;callback(blocked,query(2000),0);check(zp0.j1.holder.b.submitted==submitted,"blacklist rechecked before confirmation");
  c.person.rule=ReceiptConfig.Rule.ALL;ReceiptStore.save(ReceiptConfig.Feature.TRANSFER,"self",c);
  for(String edition:new String[]{"free","pro"}){Kernel.edition=Kernel.authorized=edition;receive("tier-"+edition);check(zp0.j1.holder.b.submitted==submitted,"transfer inaccessible to "+edition);}
  Kernel.edition=Kernel.authorized="plus";
  String actual=xml("actual-card").replace("<total_fee>123</total_fee>","<feedesc><![CDATA[￥1.23]]></feedesc>").replace("<fee_type>CNY</fee_type>","");
  TransferEngine.Packet actualPacket=TransferEngine.parse("self","peer",actual,false);
  check(actualPacket!=null&&actualPacket.cents==-1,"native card without total_fee or fee_type uses unknown amount until native query");
  TransferRuntime.received(actual,"peer",System.currentTimeMillis());Handler.advance(1000);check(last() instanceof h1,"native shape card reaches query");
  callback(last(),query(2000),0);check(last() instanceof n0&&((n0)last()).arguments[2].equals(123),"confirmation uses checked native amount, never missing card sentinel");
  callback(last(),new JSONObject(),0);callback(last(),query(2001),0);check(Kernel.lastToast.contains("1.23"),"native query amount used in verified receipt");
  String changed=actual.replace("actual-card","changed-amount");TransferRuntime.received(changed,"peer",System.currentTimeMillis());Handler.advance(1000);
  callback(last(),query(2000),0);callback(last(),new JSONObject(),0);submitted=zp0.j1.holder.b.submitted;
  callback(last(),query(2001).put("fee",124),0);check(zp0.j1.holder.b.submitted==submitted&&RedPacketRuntime.history().contains("不匹配"),"verification rejects changed native amount after confirmation");
  for(long amount:new long[]{0,-1,2147483648L}){String badCard=actual.replace("actual-card","bad-amount-"+amount);TransferRuntime.received(badCard,"peer",System.currentTimeMillis());Handler.advance(1000);submitted=zp0.j1.holder.b.submitted;callback(last(),query(2000).put("fee",amount),0);check(zp0.j1.holder.b.submitted==submitted,"missing card amount never bypasses invalid native amount "+amount);}
  String noFee=actual.replace("actual-card","bad-currency");TransferRuntime.received(noFee,"peer",System.currentTimeMillis());Handler.advance(1000);submitted=zp0.j1.holder.b.submitted;callback(last(),query(2000).put("fee_type","USD"),0);check(zp0.j1.holder.b.submitted==submitted,"native currency still verified when card omits it");
  c.minDelayMs=c.maxDelayMs=60000;ReceiptStore.save(ReceiptConfig.Feature.TRANSFER,"self",c);TransferRuntime.received(xml("long"),"peer",System.currentTimeMillis());Handler.advance(59999);check(zp0.j1.holder.b.submitted==submitted,"long seconds delay not shortened by response timeout");Handler.advance(1);check(zp0.j1.holder.b.submitted==submitted+1,"long delay starts query at 60 seconds");
  Handler.advance(45000);submitted=zp0.j1.holder.b.submitted;callback(last(),query(2000),0);check(zp0.j1.holder.b.submitted==submitted,"late query callback never confirms");
  RedPacketEngine.Attempt red=new RedPacketEngine.Attempt(RedPacketEngineTest.packet("bus"),true,Long.MAX_VALUE);
  final int[] calls={0};RedPacketNative adapter=new RedPacketNative(TransferTest.class.getClassLoader(),new RedPacketNativeTest.Sink(){public void query(RedPacketEngine.Attempt a,RedPacketEngine.Query q){calls[0]++;}});adapter.query(red);RedPacketRuntimeTest.callback(last(),0,0);check(calls[0]==1,"shared queue callback preserves red packet listener after transfer initialization");
  System.out.println("PASS "+checks+" transfer parser/native/receipt-flow checks");
 }
}
