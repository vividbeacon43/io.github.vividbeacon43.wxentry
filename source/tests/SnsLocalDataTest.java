package com.zafiro.wxentry.kernel;
import java.util.*;
public final class SnsLocalDataTest {
 static int checks;static final String KEY="wxid_author~ffffffffffffffef";
 interface Action{void run()throws Exception;}
 static void ok(boolean test){checks++;if(!test)throw new AssertionError("Check "+checks);}
 static void bad(Action a)throws Exception{boolean rejected=false;try{a.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected);}
 static SnsLocalData.Person person(int i){return new SnsLocalData.Person("wxid_friend_"+i,"好友"+i+"🙂");}
 static SnsLocalData.Comment entry(int i,String value){return new SnsLocalData.Comment("local_"+String.format("%032x",i),person(i),value);}
 public static void main(String[] args)throws Exception{
  SnsLocalData empty=SnsLocalData.empty();ok(!empty.likesOn&&!empty.commentsOn&&empty.posts.isEmpty());
  SnsLocalData d=empty.enabled(SnsLocalData.Feature.LIKES,true);ok(d.likesOn&&!d.commentsOn&&!empty.likesOn);
  d=d.enabled(SnsLocalData.Feature.COMMENTS,true).presets(Arrays.asList("拍得真好🙂","支持一下","支持一下"));ok(d.presets.size()==2);
  final List<SnsLocalData.Person> source=new ArrayList<>(Arrays.asList(person(1),person(2)));
  d=d.likes(KEY,source,123).addComments(KEY,Arrays.asList(entry(1,"第一行\n第二行 | = 😀"),entry(2,"另一条")),124);source.clear();ok(d.post(KEY).likes.size()==2);
  final SnsLocalData snapshot=d;
  bad(()->snapshot.likes(KEY,Arrays.asList(person(1),person(1)),0));
  boolean immutable=false;try{d.post(KEY).likes.clear();}catch(UnsupportedOperationException e){immutable=true;}ok(immutable);
  immutable=false;try{d.posts.clear();}catch(UnsupportedOperationException e){immutable=true;}ok(immutable);
  String encoded=d.encode();SnsLocalData decoded=SnsLocalData.decode(encoded);ok(encoded.equals(decoded.encode()));ok(decoded.post(KEY).comments.get(0).text.equals("第一行\n第二行 | = 😀"));
  SnsLocalData changed=d.edit(KEY,entry(1,"x").localId,"修改",125);ok(changed.post(KEY).comments.get(0).text.equals("修改")&&d.post(KEY).comments.get(0).text.startsWith("第一行"));
  changed=changed.edit(KEY,entry(1,"x").localId,null,126);ok(changed.post(KEY).comments.size()==1&&changed.post(KEY).likes.size()==2);
  bad(()->snapshot.edit(KEY,entry(4,"x").localId,"不存在",0));
  ok(d.clear(KEY).posts.isEmpty()&&d.clear(KEY).likesOn);ok(d.clear().posts.isEmpty()&&d.clear().commentsOn&&d.clear().presets.size()==2);
  ok(d.likes(KEY,Collections.emptyList(),0).post(KEY).comments.size()==2);ok(d.likes(KEY,Collections.emptyList(),0).edit(KEY,entry(1,"x").localId,null,0).edit(KEY,entry(2,"x").localId,null,0).posts.isEmpty());
  for(String id:new String[]{"","gh_official","filehelper","room@chatroom","../escape","a\nb"})bad(()->new SnsLocalData.Person(id,"x"));
  for(String key:new String[]{"bad","wxid_author~0","wxid_author~xyz","wxid_author~12345678901234567","wxid_author~1~2"})ok(!SnsLocalData.postKey(key));
  bad(()->new SnsLocalData.Comment("123",person(1),"x"));bad(()->entry(1," "));bad(()->entry(1,"x\0y"));bad(()->entry(1,String.join("",Collections.nCopies(501,"x"))));
  for(String raw:new String[]{"", "format=2\nlikes=1\ncomments=1\n", "format=1\nlikes=2\ncomments=1\n", "format=1\nlikes=1\ncomments=1\nunknown=x\n", encoded+"post="+KEY+"|3\n", encoded+"like="+KEY+"|"+SnsLocalData.hex("wxid_friend_1")+"|78\n", encoded+"template=78\n", "format=1\nlikes=0\ncomments=0\ntemplate=c080\n"}){
   boolean rejected=false;try{SnsLocalData.decode(raw);}catch(Exception e){rejected=true;}ok(rejected);
  }
  List<SnsLocalData.Person> many=new ArrayList<>();for(int i=0;i<5000;i++)many.add(person(i));SnsLocalData thousands=empty.likes(KEY,many,0);ok(thousands.post(KEY).likes.size()==5000);ok(SnsLocalData.decode(thousands.encode()).post(KEY).likes.size()==5000);ok(thousands.addComments(KEY,Arrays.asList(entry(1,"评论仍保留")),0).post(KEY).likes.size()==5000);
  List<SnsLocalData.Comment> comments=new ArrayList<>();for(int i=0;i<100;i++)comments.add(entry(i,"内容"));ok(empty.addComments(KEY,comments,0).post(KEY).comments.size()==100);comments.add(entry(100,"多一条"));bad(()->empty.addComments(KEY,comments,0));
  List<String> templates=new ArrayList<>();for(int i=0;i<51;i++)templates.add("预设"+i);bad(()->empty.presets(templates));
  SnsLocalData lru=empty;for(int i=1;i<=200;i++)lru=lru.likes("wxid_author~"+Integer.toHexString(i),Arrays.asList(person(1)),i);ok(lru.posts.size()==200);
  lru=lru.likes("wxid_author~1",Arrays.asList(person(2)),201).likes("wxid_author~c9",Arrays.asList(person(3)),202);ok(lru.posts.size()==200&&lru.posts.containsKey("wxid_author~1")&&!lru.posts.containsKey("wxid_author~2"));
  ok(SnsLocalData.decode(null).posts.isEmpty());ok(SnsLocalData.decode(lru.encode()).encode().equals(lru.encode()));
  System.out.println("PASS "+checks+" local Moments codec, immutable edits, identity and bounded retention checks");
 }
}
