package com.zafiro.wxentry.kernel;
import android.content.ContentValues;import com.tencent.mm.storage.g9;import com.tencent.mm.plugin.messenger.foundation.h2;
public final class RecallTipRuntimeTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static ContentValues update(){ContentValues cv=new ContentValues();cv.put("content","对方撤回了一条消息");cv.put("type",10002);cv.put("msgId",999999L);cv.put("talker","WRONG_SESSION");return cv;}
 static g9 setup(String account){Kernel.account=account;Kernel.off=Kernel.MODULE_OFF=Kernel.NO_TIP=false;g9 s=new g9(new FakeMessageDb());h2.current=s;return s;}
 static boolean tip(g9 s,long id){return RecallTipRuntime.insert(s.db,Long.toString(id),update());}
 static RecallMarkStore.Entry identity(g9 s,long id){ContentValues v=s.db.rows.get(id);return new RecallMarkStore.Entry(Kernel.account,v.getAsString("talker"),id,v.getAsLong("msgSvrId"),v.getAsLong("createTime"),v.getAsInteger("type"));}
 public static void main(String[] args)throws Exception {
  g9 old=setup("old-reproducer");long original=old.receive("room@chatroom","source",1,100,0);
  ContentValues raw=new ContentValues(old.db.rows.get(original));raw.put("msgId",original+1);old.db.insert(raw);boolean collision=false;
  try{old.receive("room@chatroom","next",1,101,0);}catch(IllegalStateException expected){collision=true;}
  check(collision,"legacy max(msgId)+1 reproduces following-message collision");
  g9 s=setup("fixed");original=s.receive("room@chatroom","你叫什么名字",1,200,0);ContentValues before=new ContentValues(s.db.rows.get(original));int calls=s.calls;
  check(tip(s,original),"recall records original identity");check(s.counter==1&&s.calls==calls&&s.notifications==1&&s.db.rows.size()==1,"annotation allocates no message id, row or native notification");
  check(RecallMarkStore.find(identity(s,original))!=null,"source metadata can be found");
  long next=s.receive("room@chatroom","今年多大了",1,201,0),after=s.receive("room@chatroom","后续消息",1,202,0);
  check(next==2&&after==3&&s.db.rows.size()==3,"first and second subsequent real messages retain sequential ids");
  check(s.db.rows.get(original).values.equals(before.values),"original body and all database fields remain untouched");
  int writes=RedPacketRuntime.writes;check(!tip(s,original)&&RedPacketRuntime.writes==writes,"duplicate callback neither rewrites file nor inserts row");
  check(tip(s,next)&&RecallMarkStore.find(identity(s,next))!=null,"another independently recalled source is recordable");
  check(s.db.queries==s.db.closed,"all source cursors closed");
  for(int type:new int[]{34,3,43,49}) {long id=s.receive("room@chatroom","source",type,300+type,0);calls=s.calls;check(tip(s,id),"media source is recordable "+type);check(s.calls==calls&&s.receive("peer","next",1,400+type,0)==id+1,"media annotation consumes no next id "+type);}
  for(int type:new int[]{10000,10002}){long id=s.receive("peer","system",type,500+type,0);calls=s.calls;check(!tip(s,id)&&s.calls==calls,"system rows are not annotated "+type);}
  long own=s.receive("peer","own",1,500,1);calls=s.calls;check(!tip(s,own)&&s.calls==calls,"self recall is not annotated");
  long pending=s.receive("peer","settings source",1,501,0);calls=s.calls;
  Kernel.NO_TIP=true;check(!tip(s,pending)&&s.calls==calls,"no-tip preference honored");Kernel.NO_TIP=false;
  Kernel.MODULE_OFF=true;check(!tip(s,pending)&&s.calls==calls,"module off honored");Kernel.MODULE_OFF=false;
  Kernel.off=true;check(!tip(s,pending)&&s.calls==calls,"anti-recall off honored");Kernel.off=false;
  check(!RecallTipRuntime.insert(s.db,"1 OR 1=1",update()),"invalid local id rejected");check(!tip(s,9999999),"missing source rejected");
  s.db.onQuery=new Runnable(){public void run(){Kernel.account="other-account";}};check(!tip(s,pending),"account change while reading stops record");s.db.onQuery=null;Kernel.account="fixed";
  RedPacketRuntime.fail=true;check(!tip(s,pending)&&RecallMarkStore.find(identity(s,pending))==null,"failed atomic save does not poison memory cache");RedPacketRuntime.fail=false;check(tip(s,pending),"failed save can be retried safely");
  RecallMarkStore.Entry entry=identity(s,pending);String data=RedPacketRuntime.files.get(RecallMarkStore.path("fixed"));
  check(!data.contains("settings source")&&!data.contains("你叫什么名字"),"ledger stores no message body");
  RecallMarkStore.cached=null;check(RecallMarkStore.find(entry)!=null,"ledger survives process cache reset");
  check(RecallMarkStore.find(new RecallMarkStore.Entry("fixed","different",entry.local,entry.server,entry.created,1))==null,"talker isolation");
  check(RecallMarkStore.find(new RecallMarkStore.Entry("fixed",entry.talker,entry.local,entry.server+1,entry.created,1))==null,"server-id isolation");
  check(RecallMarkStore.find(new RecallMarkStore.Entry("fixed",entry.talker,entry.local,entry.server,entry.created+1,1))==null,"creation-time isolation");
  check(RecallMarkStore.find(new RecallMarkStore.Entry("other",entry.talker,entry.local,entry.server,entry.created,1))==null,"account isolation");
  check(RecallMarkStore.find(entry)!=null,"switching back reloads own ledger");
  for(String invalid:new String[]{"format=2\nowner=x\n","format=1\nowner=WRONG\n",data+"broken\n",data+entry.line()+"\n"}){RedPacketRuntime.files.put(RecallMarkStore.path("fixed"),invalid);RecallMarkStore.cached=null;check(RecallMarkStore.find(entry)==null,"malformed/wrong-owner/duplicate records fail closed");}
  RedPacketRuntime.files.put(RecallMarkStore.path("fixed"),data);RecallMarkStore.cached=null;
  check(!RecallMarkStore.mark(new RecallMarkStore.Entry("fixed","bad\ntalker",1,1,1,1)),"invalid metadata rejected before persistence");
  final g9 concurrent=setup("concurrent");final long repeated=concurrent.receive("room@chatroom","source",1,700,0);writes=RedPacketRuntime.writes;
  java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(8);java.util.List<java.util.concurrent.Future<Boolean>> futures=new java.util.ArrayList<>();
  for(int i=0;i<16;i++)futures.add(pool.submit(new java.util.concurrent.Callable<Boolean>(){public Boolean call(){return tip(concurrent,repeated);}}));
  int successes=0;for(java.util.concurrent.Future<Boolean> future:futures)if(future.get())successes++;pool.shutdown();
  check(successes==1&&RedPacketRuntime.writes==writes+1,"concurrent callbacks persist once");check(concurrent.calls==1&&concurrent.db.rows.size()==1&&concurrent.receive("room@chatroom","next",1,701,0)==2,"concurrent callbacks do not consume following id");
  System.out.println("PASS "+checks+" inline recall metadata/following-message regression checks");
 }
}
