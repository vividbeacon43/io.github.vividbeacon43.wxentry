package com.zafiro.wxentry.kernel;
import java.util.*;
public class ReceiptTest {
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void invalid(String s){try{ReceiptConfig.seconds(s);throw new AssertionError("accepted "+s);}catch(IllegalArgumentException good){checks++;}}
 public static void main(String[] args)throws Throwable{
  check(ReceiptConfig.seconds("1.125")==1125 && ReceiptConfig.seconds("0")==0 && ReceiptConfig.seconds("86400")==86400000,"seconds convert exactly");
  for(String s:new String[]{"-1","NaN","1e3","1.0001","86400.001","999999999999999999999","",".5"})invalid(s);
  ReceiptConfig c=new ReceiptConfig();check(c.delay(new Random())==0,"delay defaults off");
  c.delayEnabled=true;c.minDelayMs=1000;c.maxDelayMs=1002;Set<Long> samples=new HashSet<>();
  for(int i=0;i<200;i++)samples.add(c.delay(new Random(i)));
  check(samples.equals(new HashSet<Long>(Arrays.asList(1000L,1001L,1002L))),"inclusive random delay range");
  c.minDelayMs=c.maxDelayMs=99999;check(c.delay(new Random())==99999,"equal endpoints fixed above 45 second timeout");
  c.person.rule=ReceiptConfig.Rule.WHITE;c.person.white.add("peer");c.group.rule=ReceiptConfig.Rule.BLACK;c.group.black.add("blocked@chatroom");
  check(c.permits("peer")&&!c.permits("other")&&c.permits("ok@chatroom")&&!c.permits("blocked@chatroom"),"personal and group rules independent");
  c.person.white.clear();check(!c.permits("peer"),"empty active white list denies all");
  c.person.rule=ReceiptConfig.Rule.OFF;check(!c.permits("peer"),"scope off denies all");
  ReceiptConfig.ListRule exclusive=new ReceiptConfig.ListRule();exclusive.white.add("peer");exclusive.black.add("peer");
  exclusive.setList(true,true);check(exclusive.rule==ReceiptConfig.Rule.WHITE&&exclusive.permits("peer")&&!exclusive.permits("other"),"enable white only uses white even if black retains same person");
  exclusive.setList(false,true);check(exclusive.rule==ReceiptConfig.Rule.BLACK&&!exclusive.permits("peer")&&exclusive.permits("other"),"enable black automatically disables white");
  exclusive.setList(true,false);check(exclusive.rule==ReceiptConfig.Rule.BLACK,"disabling inactive white keeps black enabled");
  exclusive.setList(false,false);check(exclusive.rule==ReceiptConfig.Rule.ALL&&exclusive.permits("peer"),"both switches off removes list restriction");
  exclusive.setList(true,true);exclusive.setList(true,true);check(exclusive.rule==ReceiptConfig.Rule.WHITE,"repeated desired state does not toggle twice");
  exclusive.setEnabled(false);check(!exclusive.permits("peer"),"category switch off denies all");exclusive.setList(false,false);check(exclusive.rule==ReceiptConfig.Rule.OFF,"inactive list off cannot enable disabled category");
  exclusive.setEnabled(true);check(exclusive.rule==ReceiptConfig.Rule.ALL&&exclusive.white.contains("peer")&&exclusive.black.contains("peer"),"reopening category preserves both saved lists with no active filter");
  String encoded=c.encode();check(ReceiptConfig.decode(encoded).encode().equals(encoded),"atomic config roundtrip");
  try{ReceiptConfig.decode(encoded+"enabled=1\n");throw new AssertionError("duplicates accepted");}catch(IllegalArgumentException expected){checks++;}
  RedPacketRuntime.write(Kernel.RP_ON_PATH,"");RedPacketRuntime.write(Kernel.RP_WL_PATH,"old@chatroom\n");RedPacketRuntime.write(Kernel.RP_DELAY_PATH,"500-1500");
  c=ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET);
  check(c.enabled&&c.group.rule==ReceiptConfig.Rule.WHITE&&c.group.white.contains("old@chatroom"),"old group white list preserved");
  check(c.delayEnabled&&c.minDelayMs==500&&c.maxDelayMs==1500,"old milliseconds migrate exactly into second settings");
  ReceiptConfig transfer=ReceiptStore.current(ReceiptConfig.Feature.TRANSFER);
  check(!transfer.enabled && !transfer.delayEnabled && transfer.group.rule==ReceiptConfig.Rule.ALL,"new transfer defaults off with independent rules/delay");
  transfer.enabled=true;transfer.person.rule=ReceiptConfig.Rule.BLACK;transfer.person.black.add("peer");ReceiptStore.save(ReceiptConfig.Feature.TRANSFER,"self",transfer);
  check(ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET).group.white.contains("old@chatroom"),"transfer edit cannot modify red list");
  ReceiptConfig originals=ReceiptStore.current(ReceiptConfig.Feature.ORIGINAL_IMAGE);
  check(!originals.enabled&&originals.person.rule==ReceiptConfig.Rule.ALL,"original image automation defaults off independently");
  originals.enabled=true;originals.person.rule=ReceiptConfig.Rule.WHITE;originals.person.white.add("peer");originals.group.rule=ReceiptConfig.Rule.BLACK;originals.group.black.add("blocked@chatroom");ReceiptStore.save(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self",originals);
  check(ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","peer")&&!ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","other"),"original personal whitelist filters download eligibility");
  check(ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","allowed@chatroom")&&!ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","blocked@chatroom"),"original group blacklist separate from personal whitelist");
  check(ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET).group.white.contains("old@chatroom")&&ReceiptStore.current(ReceiptConfig.Feature.TRANSFER).person.black.contains("peer"),"original rules do not modify red packet or transfer lists");
  originals.person.setList(false,true);originals.person.black.add("peer");ReceiptStore.save(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self",originals);
  check(!ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","peer")&&ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","other")&&ReceiptStore.current(ReceiptConfig.Feature.ORIGINAL_IMAGE).person.rule==ReceiptConfig.Rule.BLACK,"original black switch replaces white while preserving both saved lists");
  w41.z1.current="other";check(!ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET).enabled&&!ReceiptStore.current(ReceiptConfig.Feature.TRANSFER).enabled&&!ReceiptStore.current(ReceiptConfig.Feature.ORIGINAL_IMAGE).enabled,"new account does not inherit enabled flags or rules");w41.z1.current="self";
  RedPacketRuntime.write(ReceiptStore.path(ReceiptConfig.Feature.TRANSFER,"self"),"broken");check(!ReceiptStore.enabled(ReceiptConfig.Feature.TRANSFER),"corrupt setting disables automation");
  RedPacketRuntime.write(ReceiptStore.path(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self"),"broken");check(!ReceiptStore.enabled(ReceiptConfig.Feature.ORIGINAL_IMAGE)&&!ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,"self","peer"),"corrupt original rules deny downloads");
  List<ReceiptTargets.Target> all=Arrays.asList(new ReceiptTargets.Target("a","A",10),new ReceiptTargets.Target("b","B",200),new ReceiptTargets.Target("r@chatroom","R",500));
  Set<String> saved=new HashSet<>(Arrays.asList("missing","a"));List<ReceiptTargets.Target> people=ReceiptTargets.withSaved(all,saved,ReceiptConfig.Scope.PERSON);
  check(people.size()==3&&people.get(0).id.equals("b")&&people.get(2).id.equals("missing"),"categories sorted recent first and saved unavailable contacts retained");
  check(ReceiptTargets.filter(people,"A").size()==1 && saved.contains("missing"),"search does not discard hidden selections");
  check(!ReceiptTargets.eligible("gh_service",1,0,"self")&&!ReceiptTargets.eligible("self",1,0,"self")&&ReceiptTargets.eligible("r@chatroom",0,0,"self"),"system and self excluded, groups retained");
  final Object[][] rows={{"a","备注A","昵称A",1,0,10L},{"r@chatroom","","群R",0,0,300L},{"a","备注A","昵称A",1,0,400L},{"gh_service","","服务",1,0,500L}};
  final boolean[] closed={false};Kernel.cursor=new android.database.Cursor(){int index=-1;public boolean moveToFirst(){index=0;return true;}public boolean moveToNext(){return ++index<rows.length;}public String getString(int col){return (String)rows[index][col];}public int getInt(int col){return ((Number)rows[index][col]).intValue();}public long getLong(int col){return ((Number)rows[index][col]).longValue();}public void close(){closed[0]=true;}};
  List<ReceiptTargets.Target> loaded=ReceiptTargets.load("self");check(loaded.size()==2&&loaded.get(0).id.equals("a")&&loaded.get(0).name.equals("备注A")&&loaded.get(0).lastChat==400,"database names, deduplication and recent chat ordering");check(closed[0],"database cursor closed");
  Kernel.cursor=null;try{ReceiptTargets.load("self");throw new AssertionError("missing database silently accepted");}catch(IllegalStateException expected){checks++;}
  System.out.println("PASS "+checks+" receipt list/seconds/migration checks");
 }
}
