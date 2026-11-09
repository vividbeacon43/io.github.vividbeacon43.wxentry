package com.zafiro.wxentry.kernel;

import java.io.*;
import java.util.*;

/** Durable intent journal. A claimed task is never automatically resubmitted. */
final class SnsScheduleData {
    static final int MAX_TASKS=60,MAX_ACTIVE=20,MAX_TEXT=10000;
    enum State { WAITING, MISSED, CLAIMED, UPLOADING, PUBLISHED, FAILED, UNKNOWN, CANCELLED }
    static final class Photo {
        final String name,sha;final long size;
        Photo(String n,String h,long s){name=n;sha=h;size=s;if(!n.matches("[0-9]{2}\\.image")||!h.matches("[a-f0-9]{64}")||s<1||s>32L*1024*1024)throw new IllegalArgumentException("图片记录无效");}
    }
    static final class Task {
        final String id,owner;String text,message="",client="";long due,created,updated,serverId;int localId;boolean selfOnly;State state=State.WAITING;
        final List<Photo> photos=new ArrayList<Photo>();
        Task(String id,String owner,String text,long due,boolean selfOnly,long now){
            if(!id.matches("[a-f0-9]{32}")||owner==null||owner.isEmpty()||owner.length()>200||text==null||text.length()>MAX_TEXT||due<1)throw new IllegalArgumentException("任务内容无效");
            this.id=id;this.owner=owner;this.text=normalize(text);this.due=due;this.selfOnly=selfOnly;created=updated=now;
        }
        boolean active(){return state==State.WAITING||state==State.MISSED||state==State.CLAIMED||state==State.UPLOADING||state==State.UNKNOWN;}
        boolean editable(){return state==State.WAITING||state==State.MISSED;}
    }
    final String owner;boolean enabled;final List<Task> tasks=new ArrayList<Task>();
    SnsScheduleData(String owner){this.owner=owner;}
    Task find(String id){for(Task t:tasks)if(t.id.equals(id))return t;return null;}
    static String normalize(String text){return text.trim().replace("\r\n","\n").replaceAll("\n{3,}","\n\n");}
    static boolean due(Task t,long now){return t.state==State.WAITING&&now>=t.due;}
    static boolean eligible(Task t,long now,boolean enabled,boolean verified,boolean timer,boolean continuous){return enabled&&verified&&timer&&continuous&&due(t,now)&&now-t.due<=45000;}
    static void claim(Task t,long now){if(t.state!=State.WAITING&&t.state!=State.MISSED)throw new IllegalStateException("任务已提交");t.state=State.CLAIMED;t.updated=now;t.message="正在准备提交，恢复后不会自动重发";}
    void validate(){
        if(tasks.size()>MAX_TASKS)throw new IllegalArgumentException("任务过多");Set<String> ids=new HashSet<String>();int active=0;
        for(Task t:tasks){if(!owner.equals(t.owner)||!ids.add(t.id)||t.photos.size()>9||t.text.isEmpty()&&t.photos.isEmpty()||t.localId<0||t.client.length()>200||t.message.length()>1000)throw new IllegalArgumentException("任务记录损坏");if(t.active())active++;}
        if(active>MAX_ACTIVE)throw new IllegalArgumentException("最多保留 20 个待处理任务");
    }
    byte[] encode()throws IOException{
        validate();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x57585301);out.writeUTF(owner);out.writeBoolean(enabled);out.writeInt(tasks.size());
        for(Task t:tasks){out.writeUTF(t.id);out.writeUTF(t.text);out.writeLong(t.due);out.writeBoolean(t.selfOnly);out.writeLong(t.created);out.writeLong(t.updated);out.writeUTF(t.state.name());out.writeInt(t.localId);out.writeLong(t.serverId);out.writeUTF(t.client);out.writeUTF(t.message);out.writeInt(t.photos.size());for(Photo p:t.photos){out.writeUTF(p.name);out.writeUTF(p.sha);out.writeLong(p.size);}}
        out.close();return bytes.toByteArray();
    }
    static SnsScheduleData decode(String owner,byte[] bytes)throws IOException{
        if(bytes.length>1024*1024)throw new IOException("任务文件过大");DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=0x57585301||!owner.equals(in.readUTF()))throw new IOException("任务账号不匹配");SnsScheduleData data=new SnsScheduleData(owner);data.enabled=in.readBoolean();int count=in.readInt();if(count<0||count>MAX_TASKS)throw new IOException("任务数量无效");
        try{for(int i=0;i<count;i++){String id=in.readUTF(),text=in.readUTF();long due=in.readLong();boolean self=in.readBoolean();long created=in.readLong(),updated=in.readLong();Task t=new Task(id,owner,text,due,self,created);t.updated=updated;t.state=State.valueOf(in.readUTF());t.localId=in.readInt();t.serverId=in.readLong();t.client=in.readUTF();t.message=in.readUTF();int n=in.readInt();if(n<0||n>9)throw new IOException("图片数量无效");for(int j=0;j<n;j++)t.photos.add(new Photo(in.readUTF(),in.readUTF(),in.readLong()));data.tasks.add(t);}data.validate();}catch(IllegalArgumentException e){throw new IOException("任务文件无效",e);}
        if(in.available()!=0)throw new IOException("任务尾部无效");return data;
    }
}
