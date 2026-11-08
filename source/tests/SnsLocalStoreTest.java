package com.zafiro.wxentry.kernel;
import java.nio.file.*;import java.nio.charset.*;import java.lang.reflect.*;import java.util.*;
public final class SnsLocalStoreTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("Store check "+checks);}
 static void restart()throws Exception{Field f=SnsLocalStore.class.getDeclaredField("cachedOwner");f.setAccessible(true);f.set(null,null);}
 public static void main(String[] args)throws Exception{
  Path dir=Paths.get(args[0],"sns-local-store");Files.createDirectories(dir);Kernel.WXDIR=dir.toString()+java.io.File.separator;
  String a=Kernel.account,b="wxid_owner_b",key="wxid_author~1234";
  ok(!SnsLocalStore.edit(null,null,d->d.clear()));ok(!SnsLocalStore.reset(null));ok(!SnsLocalStore.edit("../invalid",null,d->d.clear()));
  ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));
  Path existing=dir.resolve("moments.settings.existing");Files.write(existing,"format=1\nforward=1\nad=1\n".getBytes(StandardCharsets.UTF_8));byte[] untouched=Files.readAllBytes(existing);
  ok(SnsLocalStore.edit(a,null,d->d.enabled(SnsLocalData.Feature.LIKES,true).presets(Arrays.asList("预设"))));
  ok(SnsLocalStore.edit(a,key,d->d.likes(key,Arrays.asList(SnsLocalDataTest.person(1)),1)));ok(SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));
  restart();ok(SnsLocalStore.load(a).post(key).likes.size()==1);
  Kernel.account=b;ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));ok(!SnsLocalStore.edit(a,key,d->d.clear()));ok(SnsLocalStore.edit(b,null,d->d.enabled(SnsLocalData.Feature.COMMENTS,true)));ok(!SnsLocalStore.load(b).likesOn&&SnsLocalStore.load(b).commentsOn);
  Kernel.account=a;ok(SnsLocalStore.load(a).likesOn&&!SnsLocalStore.load(a).commentsOn&&SnsLocalStore.load(a).post(key).likes.size()==1);
  byte[] before=Files.readAllBytes(Paths.get(SnsLocalStore.path(a)));
  for(String edition:new String[]{"free","unactivated"}){Kernel.edition=edition;ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));ok(!SnsLocalStore.edit(a,null,d->d.clear()));ok(!SnsLocalStore.reset(a));}
  Kernel.edition="plus";Kernel.verified=false;ok(!SnsLocalStore.edit(a,null,d->d.clear()));ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));Kernel.verified=true;Kernel.enabled=false;ok(!SnsLocalStore.reset(a));ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));Kernel.enabled=true;
  ok(Arrays.equals(before,Files.readAllBytes(Paths.get(SnsLocalStore.path(a)))));
  RedPacketRuntime.failWrite=true;ok(!SnsLocalStore.edit(a,null,d->d.enabled(SnsLocalData.Feature.LIKES,false)));ok(SnsLocalStore.load(a).likesOn);RedPacketRuntime.failWrite=false;
  ok(!SnsLocalStore.edit(a,null,d->{Kernel.account=b;return d.clear();}));Kernel.account=a;ok(SnsLocalStore.load(a).post(key).likes.size()==1);
  Kernel.edition="vip";ok(SnsLocalStore.edit(a,null,d->d.enabled(SnsLocalData.Feature.COMMENTS,true)));Kernel.edition="plus";
  // Editing a stale/corrupt file must not silently erase it. Explicit clear is the recovery path.
  Files.write(Paths.get(SnsLocalStore.path(a)),"broken".getBytes(StandardCharsets.UTF_8));restart();ok(!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));ok(!SnsLocalStore.edit(a,null,d->d.enabled(SnsLocalData.Feature.LIKES,true)));ok(new String(Files.readAllBytes(Paths.get(SnsLocalStore.path(a))),StandardCharsets.UTF_8).equals("broken"));ok(SnsLocalStore.reset(a));ok(SnsLocalStore.load(a).posts.isEmpty());
  // A full bounded collection is pruned by post, preserving the post currently edited.
  String longText=String.join("",Collections.nCopies(500,"测"));
  ok(SnsLocalStore.edit(a,key,d->{SnsLocalData out=d.enabled(SnsLocalData.Feature.LIKES,true);for(int p=1;p<=30;p++){List<SnsLocalData.Comment> cs=new ArrayList<>();for(int c=0;c<100;c++)cs.add(SnsLocalDataTest.entry(c,longText));out=out.addComments(p==30?key:"wxid_author~"+Integer.toHexString(p),cs,p);}return out;}));
  ok(Files.size(Paths.get(SnsLocalStore.path(a)))<=SnsLocalData.MAX_BYTES);ok(SnsLocalStore.load(a).posts.containsKey(key)&&SnsLocalStore.load(a).posts.size()<30);
  ok(SnsLocalStore.reset(a));ok(SnsLocalStore.load(a).posts.isEmpty()&&SnsLocalStore.load(a).likesOn);
  byte[] huge=new byte[SnsLocalData.MAX_BYTES+1];Files.write(Paths.get(SnsLocalStore.path(a)),huge);restart();ok(!SnsLocalStore.edit(a,null,d->d.clear()));ok(SnsLocalStore.reset(a));
  ok(Arrays.equals(untouched,Files.readAllBytes(existing)));ok(SnsLocalStore.load(b).commentsOn);
  System.out.println("PASS "+checks+" local Moments account isolation, paid grants, atomic failure, corrupt recovery and size cleanup checks");
 }
}
