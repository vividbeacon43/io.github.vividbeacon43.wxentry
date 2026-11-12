package com.zafiro.wxentry.kernel;

import java.util.*;

/** Presentation indexes only. Never builds a native SNS object or a server notification. */
final class SnsLocalProjection {
    static int extraLikes(SnsLocalData data,SnsLocalData.Post post,Set<String> genuine){
        if(data==null||!data.likesOn||post==null||genuine==null)return 0;
        Set<String> added=new HashSet<String>();for(SnsLocalData.Person person:post.likes)if(SnsLocalSelection.selectable(person.id)&&!genuine.contains(person.id))added.add(person.id);return added.size();
    }
    static final class Entry {
        final String key,id;final long time;final SnsLocalData.Person person;final SnsLocalData.Comment comment;
        Entry(SnsLocalData.Post p,SnsLocalData.Person who,SnsLocalData.Comment comment){this(p,who,comment,Collections.<String,Long>emptyMap());}
        Entry(SnsLocalData.Post p,SnsLocalData.Person who,SnsLocalData.Comment comment,Map<String,Long> times){key=p.key;person=who;this.comment=comment;id=key+"/"+(comment==null?"like/"+who.id:comment.localId);Long at=times.get(id);time=at==null?p.updated:at;}
    }
    static final class Page {final List<Entry> entries;final int total;Page(List<Entry> entries,int total){this.entries=Collections.unmodifiableList(entries);this.total=total;}}
    static boolean own(String owner,String key){return ReceiptConfig.validId(owner)&&SnsLocalData.postKey(key)&&key.startsWith(owner+"~");}
    static Page messages(String owner,SnsLocalData data,Set<String> available,int limit){
        return messages(owner,data,available,limit,Collections.<String,Long>emptyMap());
    }
    static Page messages(String owner,SnsLocalData data,Set<String> available,int limit,Map<String,Long> times){
        if(!ReceiptConfig.validId(owner)||available==null||limit<1)return new Page(new ArrayList<Entry>(),0);
        List<SnsLocalData.Post> posts=new ArrayList<SnsLocalData.Post>();for(SnsLocalData.Post p:data.posts.values())if(own(owner,p.key)&&available.contains(p.key))posts.add(p);
        Collections.sort(posts,new Comparator<SnsLocalData.Post>(){public int compare(SnsLocalData.Post a,SnsLocalData.Post b){int c=Long.compare(b.updated,a.updated);return c==0?a.key.compareTo(b.key):c;}});
        List<Entry> out=new ArrayList<Entry>();int total=0;
        for(SnsLocalData.Post p:posts){if(data.commentsOn){total+=p.comments.size();for(SnsLocalData.Comment c:p.comments)out.add(new Entry(p,c.person,c,times));}if(data.likesOn){total+=p.likes.size();for(SnsLocalData.Person f:p.likes)out.add(new Entry(p,f,null,times));}}
        Collections.sort(out,new Comparator<Entry>(){public int compare(Entry a,Entry b){int c=Long.compare(b.time,a.time);return c==0?a.id.compareTo(b.id):c;}});
        return new Page(new ArrayList<Entry>(out.subList(0,Math.min(limit,out.size()))),total);
    }
    /** Prefix and suffix belong to the module; every native index is preserved by translation. */
    static final class Layout {
        final int before,nativeCount,after;
        Layout(int before,int nativeCount,int after){if(before<0||nativeCount<0||after<0)throw new IllegalArgumentException();this.before=before;this.nativeCount=nativeCount;this.after=after;}
        int count(){return before+nativeCount+after;}
        int nativePosition(int position){return position>=before&&position<before+nativeCount?position-before:-1;}
        int delegatedPosition(int position){if(position<0)return position;int n=nativePosition(position);if(n>=0)return n;return position>=count()?position-before-after:-1;}
    }
    /** Nonnegative references are exact native positions; -1-index references a private row. */
    static int[] merge(long[] genuine,List<Entry> local,boolean more){int[] order=new int[genuine.length+local.size()+(more?1:0)];int n=0,l=0,p=0;while(n<genuine.length||l<local.size()){if(l<local.size()&&(n>=genuine.length||local.get(l).time>=genuine[n]))order[p++]=-1-l++;else order[p++]=n++;}if(more)order[p]=-1-local.size();return order;}
}
