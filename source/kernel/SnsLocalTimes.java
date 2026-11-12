package com.zafiro.wxentry.kernel;
import java.util.*;

/** Stable, private presentation times; independent of native SNS records and the v1 local codec. */
final class SnsLocalTimes {
    static final int MAX_BYTES=8*1024*1024;
    static final class Stamp {final long published,created,time;Stamp(long published,long created,long time){if(published<0||created<published||time<published||time>created)throw new IllegalArgumentException("Invalid local time bounds");this.published=published;this.created=created;this.time=time;}}
    final String owner;final Map<String,Stamp> values=new LinkedHashMap<String,Stamp>();
    SnsLocalTimes(String owner){if(owner==null||!owner.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid time owner");this.owner=owner;}
    static String event(String post,String person,String comment){if(!SnsLocalData.postKey(post)||!SnsLocalData.friend(person)||comment!=null&&!comment.matches("local_[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid time identity");return post+"/"+(comment==null?"like/"+person:comment);}
    static long sample(long published,long created,Random random){if(published<0||created<published||created-published==Long.MAX_VALUE)throw new IllegalArgumentException("Invalid time interval");long bound=created-published+1,mask=bound-1,value=random.nextLong()>>>1;if((bound&mask)==0)return published+(value&mask);long candidate=value%bound;while(value+mask-candidate<0){value=random.nextLong()>>>1;candidate=value%bound;}return published+candidate;}
    long resolve(String id,long published,long created,Random random){if(!id.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();Stamp old=values.get(id);if(old!=null&&old.published==published&&old.time<=created)return old.time;long time=sample(published,created,random);values.put(id,new Stamp(published,created,time));return time;}
    String encode(){StringBuilder out=new StringBuilder("format=1\nowner=").append(owner).append('\n');for(Map.Entry<String,Stamp> e:values.entrySet()){Stamp s=e.getValue();out.append(e.getKey()).append('|').append(s.published).append('|').append(s.created).append('|').append(s.time).append('\n');}if(out.length()>MAX_BYTES)throw new IllegalArgumentException("Time cache too large");return out.toString();}
    static SnsLocalTimes decode(String raw,String owner){SnsLocalTimes data=new SnsLocalTimes(owner);if(raw==null)return data;if(raw.length()>MAX_BYTES)throw new IllegalArgumentException();String[] lines=raw.split("\n");if(lines.length<2||!lines[0].equals("format=1")||!lines[1].equals("owner="+owner))throw new IllegalArgumentException("Time owner changed");for(int i=2;i<lines.length;i++){String[] f=lines[i].split("\\|",-1);if(f.length!=4||!f[0].matches("[0-9a-f]{64}")||data.values.containsKey(f[0]))throw new IllegalArgumentException("Invalid time record");data.values.put(f[0],new Stamp(Long.parseLong(f[1]),Long.parseLong(f[2]),Long.parseLong(f[3])));}return data;}
}
