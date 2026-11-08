package com.zafiro.wxentry.kernel;
import java.util.*;
import android.os.Handler;
public class ReceiptKeywordTest {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("accepted invalid keyword settings");}catch(IllegalArgumentException ok){checks++;}}
 static String url(String id){return RedPacketEngineTest.packet(id).url;}
 static String red(String id,String text){return "<msg><appmsg><title>ignored-title</title><type>2001</type><wcpayinfo><sendertitle><![CDATA["+text+"]]></sendertitle><nativeurl><![CDATA["+url(id)+"]]></nativeurl></wcpayinfo></appmsg></msg>";}
 static String transfer(String id,String text){return TransferTest.xml(id).replace("</wcpayinfo>","<pay_memo><![CDATA["+text+"]]></pay_memo></wcpayinfo>");}
 static ReceiptConfig config(ReceiptConfig.Feature f,ReceiptConfig.KeywordAction a,String text){ReceiptConfig c=new ReceiptConfig();c.enabled=true;c.delayEnabled=true;c.minDelayMs=c.maxDelayMs=100;c.keywords.action=a;c.keywords.setWords(text);ReceiptStore.save(f,"self",c);return c;}
 static void receive(String id,String text){RedPacketRuntime.received(url(id),1,"peer",System.currentTimeMillis(),red(id,text));Handler.advance(100);}
 public static void main(String[] args)throws Throwable{
  final ReceiptConfig.KeywordRule k=new ReceiptConfig.KeywordRule();
  check(k.action==ReceiptConfig.KeywordAction.OFF&&k.permits(null),"default off leaves old behavior unchanged");
  k.setWords(" 拒收 \n测试\n拒收\n\n");check(k.words.equals(Arrays.asList("拒收","测试")),"trim blank lines and deduplicate without losing order");
  k.select(ReceiptConfig.KeywordAction.SKIP,true);check(!k.permits("请勿拒收")&&k.permits("恭喜发财"),"fuzzy skip matches any word as a literal substring");
  k.select(ReceiptConfig.KeywordAction.ONLY,true);check(k.action==ReceiptConfig.KeywordAction.ONLY&&k.permits("这是测试红包")&&!k.permits("普通祝福"),"only action replaces skip, any keyword can match");
  k.select(ReceiptConfig.KeywordAction.SKIP,false);check(k.action==ReceiptConfig.KeywordAction.ONLY,"disabling inactive action preserves other action");
  k.match=ReceiptConfig.KeywordMatch.EXACT;check(k.permits(" 测试 ")&&!k.permits("这是测试红包"),"exact means whole trimmed greeting or memo");
  k.select(ReceiptConfig.KeywordAction.ONLY,false);check(k.action==ReceiptConfig.KeywordAction.OFF&&k.permits(null),"both actions off disables filtering");
  k.setWords("A.*\n100%+ & =\n红包🧧");k.select(ReceiptConfig.KeywordAction.ONLY,true);k.match=ReceiptConfig.KeywordMatch.FUZZY;
  check(k.permits("AA.*B")&&!k.permits("ABBB")&&k.permits("红包🧧"),"regex-like symbols and Unicode treated literally");
  check(!k.permits("a.*"),"English comparison case-sensitive as shown in UI");
  check(!k.permits(null),"active missing source never auto-receives");k.select(ReceiptConfig.KeywordAction.SKIP,true);check(!k.permits(null)&&k.permits(""),"known empty text differs from unknown source");
  ReceiptConfig c=new ReceiptConfig();c.keywords.setWords(k.text());c.keywords.action=k.action;c.keywords.match=k.match;
  String encoded=c.encode();check(ReceiptConfig.decode(encoded).encode().equals(encoded),"percent encoded multiline punctuation keywords roundtrip");
  String old=encoded.replaceAll("(?m)^keyword\\.[^\\n]*\\n","");ReceiptConfig prior=ReceiptConfig.decode(old);
  check(prior.keywords.action==ReceiptConfig.KeywordAction.OFF&&prior.keywords.words.isEmpty(),"v254 file migrates with keyword switches off");
  bad(()->ReceiptConfig.decode(old+"keyword.rule=ONLY\n"));
  bad(()->ReceiptConfig.decode(encoded.replace("keyword.match=FUZZY","keyword.match=invalid")));
  bad(()->ReceiptConfig.decode(encoded.replace("keyword.words=","keyword.words=%ZZ")));
  bad(()->{ReceiptConfig x=new ReceiptConfig();x.keywords.action=ReceiptConfig.KeywordAction.ONLY;x.encode();});
  bad(()->k.setWords(new String(new char[201]).replace('\0','a')));
  bad(()->{StringBuilder x=new StringBuilder();for(int i=0;i<101;i++)x.append(i).append('\n');k.setWords(x.toString());});
  check(k.words.size()==3,"invalid input does not partially replace prior words");
  ReceiptConfig.Feature rp=ReceiptConfig.Feature.RED_PACKET,tr=ReceiptConfig.Feature.TRANSFER;
  check("祝你开心🧧".equals(ReceiptConfig.messageText(rp,red("parse","祝你开心🧧"))),"reads native red greeting CDATA");
  check("A&B".equals(ReceiptConfig.messageText(rp,red("entities","unused").replace("<![CDATA[unused]]>","A&amp;B"))),"XML entities decoded before text matching");
  check("备注".equals(ReceiptConfig.messageText(tr,transfer("memo","备注"))),"reads transfer memo only");
  check("".equals(ReceiptConfig.messageText(tr,TransferTest.xml("no-memo"))),"ordinary transfer without memo is known empty");
  check(ReceiptConfig.messageText(rp,red("absent","x").replace("<sendertitle><![CDATA[x]]></sendertitle>",""))==null,"missing greeting cannot search a card title or URL instead");
  check(ReceiptConfig.messageText(rp,red("dup","x").replace("</wcpayinfo>","<sendertitle>other</sendertitle></wcpayinfo>"))==null,"ambiguous duplicate greeting rejected");
  check(ReceiptConfig.messageText(rp,red("nested","x").replace("<![CDATA[x]]>","<name>test</name>"))==null,"structured elements not treated as greeting text");
  check(ReceiptConfig.messageText(rp,"<!DOCTYPE msg [<!ENTITY x SYSTEM 'file:///invalid'>]>"+red("dtd","x"))==null,"external entity definitions rejected");
  check(ReceiptConfig.messageText(rp,transfer("wrong-type","x"))==null&&ReceiptConfig.messageText(tr,"bad")==null,"wrong card type and invalid XML unknown");
  Kernel.edition=Kernel.authorized="vip";
  config(rp,ReceiptConfig.KeywordAction.ONLY,"允许");config(tr,ReceiptConfig.KeywordAction.SKIP,"拒收");
  check(ReceiptStore.permitsMessage(rp,"self","peer",red("a","允许"))&&!ReceiptStore.permitsMessage(rp,"self","peer",red("b","拒收")),"red action applied independently");
  check(ReceiptStore.permitsMessage(tr,"self","peer",transfer("a","允许"))&&!ReceiptStore.permitsMessage(tr,"self","peer",transfer("b","拒收")),"transfer has separate list and action");
  ReceiptConfig rules=ReceiptStore.current(rp);rules.person.rule=ReceiptConfig.Rule.BLACK;rules.person.black.add("peer");ReceiptStore.save(rp,"self",rules);
  check(!ReceiptStore.permitsMessage(rp,"self","peer",red("a","允许")),"keyword hit cannot override personal blacklist");
  rules.person.rule=ReceiptConfig.Rule.WHITE;rules.person.white.clear();ReceiptStore.save(rp,"self",rules);
  check(ReceiptStore.permitsMessage(rp,"self","peer",red("a","允许"))&&!ReceiptStore.permitsMessage(rp,"self","peer",red("a","其他")),"empty whitelist still applies keyword filter");
  rules.group.rule=ReceiptConfig.Rule.OFF;ReceiptStore.save(rp,"self",rules);
  check(!ReceiptStore.permitsMessage(rp,"self","room@chatroom",red("a","允许")),"disabled group cannot be allowed by keyword");
  check(!ReceiptStore.permitsMessage(rp,"other","peer",red("a","允许")),"another account does not inherit switches or keywords");
  for(String edition:new String[]{"free","plus","pro"}){Kernel.edition=Kernel.authorized=edition;check(!Kernel.receiptKeywordsEnabled()&&ReceiptStore.permitsMessage(rp,"self","peer",red("tier","其他")),"nonVIP does not run saved VIP filter "+edition);}
  Kernel.edition="vip";Kernel.authorized="plus";check(!Kernel.receiptKeywordsEnabled(),"VIP binary cannot unlock keywords for advanced grant");Kernel.authorized="vip";
  RedPacketRuntime.start(ReceiptKeywordTest.class.getClassLoader());TransferRuntime.start(ReceiptKeywordTest.class.getClassLoader());
  RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT);config(rp,ReceiptConfig.KeywordAction.SKIP,"拒收");
  int n=zp0.j1.holder.b.submitted;receive("blocked","拒收这个红包");
  check(zp0.j1.holder.b.submitted==n&&Kernel.launched==0&&Kernel.clicked==0,"blocked red makes no query, UI launch, click or payment operation");
  config(rp,ReceiptConfig.KeywordAction.ONLY,"允许");receive("not-hit","其他");check(zp0.j1.holder.b.submitted==n,"only mode rejects unmatched red before request");
  rules=config(rp,ReceiptConfig.KeywordAction.SKIP,"拒收");RedPacketRuntime.received(url("delay"),1,"peer",System.currentTimeMillis(),red("delay","允许"));Handler.advance(0);
  rules.keywords.setWords("允许");ReceiptStore.save(rp,"self",rules);Handler.advance(100);check(zp0.j1.holder.b.submitted==n,"red filter rechecked after delay");
  config(rp,ReceiptConfig.KeywordAction.ONLY,"允许");receive("before-open","允许");Object query=zp0.j1.holder.b.last;n=zp0.j1.holder.b.submitted;
  config(rp,ReceiptConfig.KeywordAction.SKIP,"允许");RedPacketRuntimeTest.callback(query,0,0);check(zp0.j1.holder.b.submitted==n,"changed red filter prevents open after native query");
  config(rp,ReceiptConfig.KeywordAction.OFF,"");receive("blocked","拒收这个红包");check(zp0.j1.holder.b.submitted==n+1,"skipped red was never reserved and can process after filter disabled");
  RedPacketRuntime.selectMode(RedPacketPolicy.Mode.FOREGROUND);
  config(rp,ReceiptConfig.KeywordAction.ONLY,"允许");receive("fg","允许");android.app.Activity page=new android.app.Activity();page.intent.url=url("fg");page.intent.talker="peer";
  int clicks=Kernel.clicked;config(rp,ReceiptConfig.KeywordAction.SKIP,"允许");RedPacketRuntime.legacyUi(page);Handler.advance(200);check(Kernel.clicked==clicks,"owned visible page never auto-clicks after keyword rule changes");
  config(rp,ReceiptConfig.KeywordAction.ONLY,"允许");receive("fg-session","允许");
  java.lang.reflect.Field instance=RedPacketRuntime.class.getDeclaredField("instance");instance.setAccessible(true);RedPacketRuntime live=(RedPacketRuntime)instance.get(null);
  com.tencent.mm.plugin.luckymoney.model.n6 nativeQuery=new com.tencent.mm.plugin.luckymoney.model.n6(1,1,"fg-session",url("fg-session"),0,"v1.0","peer");
  RedPacketRuntimeTest.constructed(live,nativeQuery,nativeQuery.arguments,live.api.queryCtor);RedPacketRuntimeTest.callback(nativeQuery,0,0);
  page.intent.url=url("fg-session");page.intent.talker=null;RedPacketRuntime.legacyUi(page);Handler.advance(200);
  check(Kernel.clicked==clicks+1,"owned native page without session extra uses validated original packet session");
  config(tr,ReceiptConfig.KeywordAction.SKIP,"拒收");n=zp0.j1.holder.b.submitted;TransferRuntime.received(transfer("t-block","拒收"),"peer",System.currentTimeMillis());Handler.advance(100);
  check(zp0.j1.holder.b.submitted==n,"blocked transfer never queries or confirms");
  config(tr,ReceiptConfig.KeywordAction.ONLY,"允许");TransferRuntime.received(transfer("t-only","别的"),"peer",System.currentTimeMillis());Handler.advance(100);check(zp0.j1.holder.b.submitted==n,"only transfer rejects unmatched memo");
  rules=config(tr,ReceiptConfig.KeywordAction.SKIP,"拒收");TransferRuntime.received(transfer("t-delay","允许"),"peer",System.currentTimeMillis());Handler.advance(0);
  rules.keywords.setWords("允许");ReceiptStore.save(tr,"self",rules);Handler.advance(100);check(zp0.j1.holder.b.submitted==n,"transfer keyword rechecked after delay");
  config(tr,ReceiptConfig.KeywordAction.ONLY,"允许");TransferRuntime.received(transfer("t-confirm","允许"),"peer",System.currentTimeMillis());Handler.advance(100);query=zp0.j1.holder.b.last;n=zp0.j1.holder.b.submitted;
  config(tr,ReceiptConfig.KeywordAction.SKIP,"允许");TransferTest.callback(query,TransferTest.query(2000),0);check(zp0.j1.holder.b.submitted==n,"transfer filter rechecked before monetary confirmation");
  check(!String.join("\n",Kernel.logs).contains("拒收这个红包"),"diagnostics omit actual memo and configured keywords");
  System.out.println("PASS "+checks+" VIP keyword parsing, exclusive rules, isolation, deferred receipt and confirmation checks");
 }
}
