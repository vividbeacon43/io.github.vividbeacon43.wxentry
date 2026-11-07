package com.tencent.mm.storage;
public class g9 {
 public final com.zafiro.wxentry.kernel.FakeMessageDb db;public long counter;public int calls,notifications;public boolean fail,commitThenThrow,negative,mismatch;
 public g9(com.zafiro.wxentry.kernel.FakeMessageDb db){this.db=db;}
 public synchronized long Zb(f9 msg){calls++;msg.setMsgId(++counter);if(fail)throw new IllegalStateException("native insert failed");if(negative)return -1;db.insert(msg.values);notifications++;if(commitThenThrow)throw new IllegalStateException("commit outcome unknown");return mismatch?msg.getMsgId()+1:msg.getMsgId();}
 public synchronized long receive(String talker,String content,int type,long server,int sent){f9 msg=new f9();msg.values.put("talker",talker);msg.values.put("content",content);msg.values.put("type",type);msg.values.put("createTime",1000000L+counter*1000);msg.values.put("msgSvrId",server);msg.values.put("msgSeq",server+100);msg.values.put("isSend",sent);return Zb(msg);}
}
