package com.zafiro.wxentry.kernel;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Immutable, bounded local presentation data. Contains no native comment IDs or server operations. */
final class SnsLocalData {
    static final int MAX_POSTS=200,MAX_COMMENTS=100,MAX_PRESETS=50,MAX_TEXT=500,MAX_BYTES=8*1024*1024;
    enum Feature { LIKES, COMMENTS }
    static final class Person {
        final String id,name;
        Person(String id,String name){if(!friend(id)||name==null||name.trim().isEmpty()||name.length()>128)throw new IllegalArgumentException("Invalid local friend");this.id=id;this.name=name.trim();}
    }
    static final class Comment {
        final String localId,text;final Person person;
        Comment(String localId,Person person,String text){if(localId==null||!localId.matches("local_[0-9a-f]{32}")||person==null)throw new IllegalArgumentException("Invalid local comment");this.localId=localId;this.person=person;this.text=comment(text);}
    }
    static final class Post {
        final String key;final long updated;final List<Person> likes;final List<Comment> comments;
        Post(String key,long updated,List<Person> likes,List<Comment> comments){
            if(!postKey(key)||updated<0||likes==null||comments==null||comments.size()>MAX_COMMENTS)throw new IllegalArgumentException("Invalid local post");
            Set<String> ids=new HashSet<String>();for(Person p:likes)if(p==null||!ids.add(p.id))throw new IllegalArgumentException("Duplicate local like");
            ids.clear();for(Comment c:comments)if(c==null||!ids.add(c.localId))throw new IllegalArgumentException("Duplicate local comment");
            this.key=key;this.updated=updated;this.likes=Collections.unmodifiableList(new ArrayList<Person>(likes));this.comments=Collections.unmodifiableList(new ArrayList<Comment>(comments));
        }
        boolean empty(){return likes.isEmpty()&&comments.isEmpty();}
    }
    final boolean likesOn,commentsOn;final List<String> presets;final Map<String,Post> posts;
    private SnsLocalData(boolean likesOn,boolean commentsOn,List<String> presets,Map<String,Post> posts){
        if(presets.size()>MAX_PRESETS||posts.size()>MAX_POSTS)throw new IllegalArgumentException("Local data limit");
        List<String> cleaned=new ArrayList<String>();for(String p:presets){String v=comment(p);if(!cleaned.contains(v))cleaned.add(v);}
        this.likesOn=likesOn;this.commentsOn=commentsOn;this.presets=Collections.unmodifiableList(cleaned);this.posts=Collections.unmodifiableMap(new LinkedHashMap<String,Post>(posts));
    }
    static SnsLocalData empty(){return new SnsLocalData(false,false,Collections.<String>emptyList(),Collections.<String,Post>emptyMap());}
    static boolean friend(String value){return value!=null&&value.matches("[A-Za-z0-9_.-]{1,256}")&&!value.startsWith("gh_")&&!value.equals("filehelper");}
    static boolean postKey(String key){if(key==null)return false;int at=key.indexOf('~');return at>0&&at==key.lastIndexOf('~')&&ReceiptConfig.validId(key.substring(0,at))&&key.substring(at+1).matches("[0-9a-f]{1,16}")&&!key.substring(at+1).equals("0");}
    static String comment(String value){if(value==null||value.trim().isEmpty()||value.length()>MAX_TEXT||value.indexOf('\0')>=0)throw new IllegalArgumentException("Comment must contain 1-500 characters");return value.trim();}
    boolean enabled(Feature feature){return feature==Feature.LIKES?likesOn:commentsOn;}
    Post post(String key){Post p=posts.get(key);return p==null?new Post(key,0,Collections.<Person>emptyList(),Collections.<Comment>emptyList()):p;}
    SnsLocalData enabled(Feature feature,boolean value){return new SnsLocalData(feature==Feature.LIKES?value:likesOn,feature==Feature.COMMENTS?value:commentsOn,presets,posts);}
    SnsLocalData presets(List<String> values){return new SnsLocalData(likesOn,commentsOn,values,posts);}
    SnsLocalData clear(){return new SnsLocalData(likesOn,commentsOn,presets,Collections.<String,Post>emptyMap());}
    SnsLocalData clear(String key){Map<String,Post> copy=new LinkedHashMap<String,Post>(posts);copy.remove(key);return new SnsLocalData(likesOn,commentsOn,presets,copy);}
    private SnsLocalData put(Post post){
        LinkedHashMap<String,Post> copy=new LinkedHashMap<String,Post>(posts);copy.remove(post.key);if(!post.empty())copy.put(post.key,post);
        while(copy.size()>MAX_POSTS)copy.remove(copy.keySet().iterator().next());
        return new SnsLocalData(likesOn,commentsOn,presets,copy);
    }
    SnsLocalData likes(String key,List<Person> values,long now){Post old=post(key);return put(new Post(key,Math.max(0,now),values,old.comments));}
    SnsLocalData addComments(String key,List<Comment> values,long now){Post old=post(key);List<Comment> all=new ArrayList<Comment>(old.comments);all.addAll(values);return put(new Post(key,Math.max(0,now),old.likes,all));}
    SnsLocalData edit(String key,String localId,String text,long now){
        Post old=post(key);List<Comment> all=new ArrayList<Comment>();boolean found=false;
        for(Comment c:old.comments)if(c.localId.equals(localId)){found=true;if(text!=null)all.add(new Comment(c.localId,c.person,text));}else all.add(c);
        if(!found)throw new IllegalArgumentException("Local comment expired");return put(new Post(key,Math.max(0,now),old.likes,all));
    }
    SnsLocalData evict(String keep){for(String key:posts.keySet())if(!key.equals(keep))return clear(key);throw new IllegalArgumentException("Local data size limit");}
    static String hex(String value){byte[] b=value.getBytes(StandardCharsets.UTF_8);StringBuilder s=new StringBuilder();for(byte v:b){int n=v&255;s.append("0123456789abcdef".charAt(n>>>4)).append("0123456789abcdef".charAt(n&15));}return s.toString();}
    static String unhex(String value)throws Exception{
        if(value.length()%2!=0||!value.matches("[0-9a-f]*"))throw new IllegalArgumentException("Invalid local encoding");byte[] bytes=new byte[value.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    String encode(){
        StringBuilder s=new StringBuilder("format=1\nlikes=").append(likesOn?1:0).append("\ncomments=").append(commentsOn?1:0).append('\n');
        for(String p:presets)s.append("template=").append(hex(p)).append('\n');
        for(Post p:posts.values()){
            s.append("post=").append(p.key).append('|').append(p.updated).append('\n');
            for(Person f:p.likes)s.append("like=").append(p.key).append('|').append(hex(f.id)).append('|').append(hex(f.name)).append('\n');
            for(Comment c:p.comments)s.append("comment=").append(p.key).append('|').append(c.localId).append('|').append(hex(c.person.id)).append('|').append(hex(c.person.name)).append('|').append(hex(c.text)).append('\n');
        }
        return s.toString();
    }
    static SnsLocalData decode(String raw)throws Exception{
        if(raw==null)return empty();if(raw.length()>MAX_BYTES)throw new IllegalArgumentException("Local data too large");
        String[] lines=raw.split("\\n");if(lines.length<3||!lines[0].equals("format=1")||!lines[1].matches("likes=[01]")||!lines[2].matches("comments=[01]"))throw new IllegalArgumentException("Invalid local header");
        List<String> presets=new ArrayList<String>();Map<String,Long> times=new LinkedHashMap<String,Long>();Map<String,List<Person>> likes=new HashMap<String,List<Person>>();Map<String,List<Comment>> comments=new HashMap<String,List<Comment>>();
        for(int i=3;i<lines.length;i++){
            String line=lines[i];if(line.isEmpty())throw new IllegalArgumentException("Empty local record");int at=line.indexOf('=');if(at<1)throw new IllegalArgumentException("Invalid local record");String type=line.substring(0,at),value=line.substring(at+1);String[] f=value.split("\\|",-1);
            if(type.equals("template")){if(!times.isEmpty())throw new IllegalArgumentException("Late template");presets.add(comment(unhex(value)));if(presets.size()>MAX_PRESETS)throw new IllegalArgumentException("Too many presets");}
            else if(type.equals("post")&&f.length==2&&postKey(f[0])){long time=Long.parseLong(f[1]);if(time<0||times.put(f[0],time)!=null||times.size()>MAX_POSTS)throw new IllegalArgumentException("Invalid local post");likes.put(f[0],new ArrayList<Person>());comments.put(f[0],new ArrayList<Comment>());}
            else if(type.equals("like")&&f.length==3&&times.containsKey(f[0])){likes.get(f[0]).add(new Person(unhex(f[1]),unhex(f[2])));}
            else if(type.equals("comment")&&f.length==5&&times.containsKey(f[0])){comments.get(f[0]).add(new Comment(f[1],new Person(unhex(f[2]),unhex(f[3])),unhex(f[4])));if(comments.get(f[0]).size()>MAX_COMMENTS)throw new IllegalArgumentException("Too many local comments");}
            else throw new IllegalArgumentException("Unknown local record");
        }
        Map<String,Post> posts=new LinkedHashMap<String,Post>();for(String key:times.keySet()){Post p=new Post(key,times.get(key),likes.get(key),comments.get(key));if(!p.empty())posts.put(key,p);}
        return new SnsLocalData(lines[1].endsWith("1"),lines[2].endsWith("1"),presets,posts);
    }
}
