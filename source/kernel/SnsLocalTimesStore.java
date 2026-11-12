package com.zafiro.wxentry.kernel;
import java.io.File;
import java.util.*;

final class SnsLocalTimesStore {
    static final java.security.SecureRandom random=new java.security.SecureRandom();
    static synchronized Map<String,Long> resolve(String owner,SnsLocalData data,Map<String,Long> published){
        Map<String,Long> out=new HashMap<String,Long>();if(!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount()))return out;
        String hash=RedPacketRuntime.hash(owner),path=Kernel.WXDIR+"moments.local.times."+hash.substring(0,16);try{
            File file=new File(path);if(file.length()>SnsLocalTimes.MAX_BYTES)return out;String raw=RedPacketRuntime.read(path);if(file.exists()&&raw==null)return out;SnsLocalTimes times=SnsLocalTimes.decode(raw,hash);
            Set<String> active=new HashSet<String>();for(SnsLocalData.Post post:data.posts.values()){for(SnsLocalData.Person person:post.likes)active.add(RedPacketRuntime.hash(SnsLocalTimes.event(post.key,person.id,null)));for(SnsLocalData.Comment comment:post.comments)active.add(RedPacketRuntime.hash(SnsLocalTimes.event(post.key,comment.person.id,comment.localId)));}times.values.keySet().retainAll(active);
            for(SnsLocalData.Post post:data.posts.values()){Long lower=published.get(post.key);if(lower==null||lower<0||post.updated<lower)continue;for(SnsLocalData.Person person:post.likes){String event=SnsLocalTimes.event(post.key,person.id,null);out.put(event,times.resolve(RedPacketRuntime.hash(event),lower,post.updated,random));}for(SnsLocalData.Comment comment:post.comments){String event=SnsLocalTimes.event(post.key,comment.person.id,comment.localId);out.put(event,times.resolve(RedPacketRuntime.hash(event),lower,post.updated,random));}}
            String next=times.encode();if(!next.equals(raw)){if(!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount())||!RedPacketRuntime.write(path,next))return Collections.emptyMap();}return out;
        }catch(Throwable failure){Kernel.T("ARSNSLOCAL time-cache-unavailable "+failure.getClass().getSimpleName());return Collections.emptyMap();}
    }
}
