package com.zafiro.wxentry.kernel;
import android.content.Intent;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class SnsForwardDraftTest {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Editor implements SnsForwardDraft.Editor {String owner="self";boolean paid=true,fail;int opens;public boolean permitted(){return paid;}public String account(){return owner;}public void open(SnsForwardDraft draft)throws Exception{opens++;if(fail)throw new IOException("test");}}
 static SnsForwardDraft draft(SnsForwardDraft.Kind k,String text,int count,List<String> files,String thumb){return new SnsForwardDraft(k,text,"Link title","https://example.test/path",count,files,thumb,"0123456789abcdef0123456789abcdef");}
 static File source(Path root,String name,boolean video)throws Exception{byte[] data=new byte[16384];new Random(5).nextBytes(data);if(video){data[4]='f';data[5]='t';data[6]='y';data[7]='p';}else{data[0]=(byte)255;data[1]=(byte)216;data[2]=(byte)255;}File f=root.resolve(name).toFile();Files.write(f.toPath(),data);return f;}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory(Paths.get(args[0]),"sns-draft-");
  ok(!SnsForwardDraft.premium("free")&&!SnsForwardDraft.premium("pro")&&SnsForwardDraft.premium("plus")&&SnsForwardDraft.premium("vip"),"advanced/VIP only");
  ok(SnsForwardDraft.kind(1,3,"text",null)==SnsForwardDraft.Kind.PHOTOS,"photo post classified");
  ok(SnsForwardDraft.kind(2,0,"\n原文🙂\n",null)==SnsForwardDraft.Kind.TEXT,"unicode and whitespace preserved");
  ok(SnsForwardDraft.kind(15,1,"video",null)==SnsForwardDraft.Kind.VIDEO,"native video classified");
  ok(SnsForwardDraft.kind(3,1,"link","https://example.test/a")==SnsForwardDraft.Kind.LINK,"link classified");
  ok(SnsForwardDraft.kind(1,0,"",null)==null&&SnsForwardDraft.kind(1,21,"",null)==null&&SnsForwardDraft.kind(4,1,"",null)==null,"empty/oversized/special post not silently degraded");
  ok(!draft(SnsForwardDraft.Kind.FINDER,"caption",0,Collections.<String>emptyList(),null).complete(),"Finder needs complete native metadata before editor handoff");
  ok(!SnsForwardDraft.http("javascript:alert(1)")&&!SnsForwardDraft.http("file:///private/a")&&!SnsForwardDraft.http("https:///missing-host"),"non-web card URLs rejected");
  SnsForwardDraft text=draft(SnsForwardDraft.Kind.TEXT,"\n原文🙂\n",0,Collections.<String>emptyList(),null);Editor editor=new Editor();SnsForwardDraft.Handoff h=new SnsForwardDraft.Handoff("self");
  ok(text.complete()&&h.open(editor,text)&&editor.opens==1,"ready text opens only the editor");ok(!h.open(editor,text)&&editor.opens==1,"duplicate callback cannot open twice");
  h=new SnsForwardDraft.Handoff("self");h.cancel();ok(!h.open(editor,text),"cancellation stops handoff");
  h=new SnsForwardDraft.Handoff("self");editor.paid=false;ok(!h.open(editor,text),"downgrade after preparation stops handoff");
  h=new SnsForwardDraft.Handoff("self");editor.paid=true;editor.owner="other";ok(!h.open(editor,text),"account switch stops handoff");editor.owner="self";
  h=new SnsForwardDraft.Handoff("self");ok(!h.open(editor,draft(SnsForwardDraft.Kind.PHOTOS,"images",2,Collections.<String>emptyList(),null)),"missing media never becomes a text-only editor");
  File one=source(root,"one.jpg",false),two=source(root,"two.jpg",false),video=source(root,"video.mp4",true);
  List<String> photos=new ArrayList<>(Arrays.asList(two.getPath(),one.getPath()));SnsForwardDraft photo=draft(SnsForwardDraft.Kind.PHOTOS,"caption",2,photos,null);photos.clear();ok(photo.complete()&&photo.media.get(0).equals(two.getPath()),"source list mutation cannot change media order");
  SnsForwardDraft invalidVideo=draft(SnsForwardDraft.Kind.VIDEO,"video",1,Collections.singletonList(video.getPath()),null);ok(!invalidVideo.complete(),"video requires a usable cover and complete video");
  SnsForwardDraft sight=draft(SnsForwardDraft.Kind.VIDEO,"caption",1,Collections.singletonList(video.getPath()),one.getPath());ok(sight.complete(),"complete video and cover accepted");
  for(SnsForwardDraft d:new SnsForwardDraft[]{text,photo,sight,draft(SnsForwardDraft.Kind.LINK,"note",0,Collections.<String>emptyList(),null)}){
   Intent intent=SnsForwardComposer.intent(d);ok(intent.pkg.equals("com.tencent.mm")&&intent.component.equals("com.tencent.mm.plugin.sns.ui.SnsUploadUI"),"explicit native editor component "+d.kind);
   ok(intent.extras.get("Kdescription").equals(d.description),"original caption preserved "+d.kind);
   ok(Boolean.TRUE.equals(intent.extras.get("KSnsPostManu"))&&Boolean.FALSE.equals(intent.extras.get("Kis_take_photo")),"manual editor flags "+d.kind);
   ok(!intent.extras.containsKey("Kis_retry_edit")&&!intent.extras.containsKey("Kis_from_sns_drafg_stg")&&!intent.extras.containsKey("auto_send")&&!intent.extras.containsKey("send"),"retry/publish commands not copied "+d.kind);
  }
  Intent intent=SnsForwardComposer.intent(photo);ok(((List<?>)intent.extras.get("sns_kemdia_path_list")).equals(photo.media)&&intent.extras.get("Ksnsupload_type").equals(0),"native photos extra order and type");
  ok(!intent.extras.containsKey("sns_kemdia_path"),"modern MultiPic widget cannot prefer scalar first image over complete list");
  intent=SnsForwardComposer.intent(sight);ok(intent.extras.get("Ksnsupload_type").equals(14)&&intent.extras.get("KSightPath").equals(video.getPath())&&intent.extras.get("KSightThumbPath").equals(one.getPath()),"native video extras use full file and cover");
  ok(SnsForwardComposer.intent(text).extras.get("Ksnsupload_type").equals(9),"native pure-text type");
  SnsForwardFiles copies=new SnsForwardFiles(root.resolve("cache").toFile());File retained=copies.copy(one,false,0,new SnsForwardFiles.Guard(){public boolean valid(){return true;}});
  ok(!retained.equals(one)&&retained.isFile()&&Arrays.equals(Files.readAllBytes(one.toPath()),Files.readAllBytes(retained.toPath())),"editor gets byte-exact owned copy");
  copies.discard();ok(!retained.exists()&&one.exists(),"cancel cleanup affects only this draft, not original SNS media");
  copies=new SnsForwardFiles(root.resolve("cache").toFile());retained=copies.copy(two,false,0,new SnsForwardFiles.Guard(){public boolean valid(){return true;}});copies.handoff();copies.discard();ok(retained.exists(),"handed-off editor draft remains readable");
  final SnsForwardFiles cancelled=new SnsForwardFiles(root.resolve("cache").toFile());boolean rejected=false;try{cancelled.copy(one,false,0,new SnsForwardFiles.Guard(){public boolean valid(){return false;}});}catch(IOException expected){rejected=true;}ok(rejected,"cancel before copying writes no editor media");cancelled.discard();
  final SnsForwardFiles interrupted=new SnsForwardFiles(root.resolve("cache").toFile());rejected=false;try{interrupted.copy(one,false,0,new SnsForwardFiles.Guard(){int calls;public boolean valid(){return ++calls<=2;}});}catch(IOException expected){rejected=true;}ok(rejected,"cancellation during staging cannot commit a ready asset");interrupted.discard();ok(one.isFile(),"interrupted stage leaves source intact");
  h=new SnsForwardDraft.Handoff("self");editor.fail=true;boolean failed=false;try{h.open(editor,text);}catch(IOException expected){failed=true;}ok(failed&&h.failed&&!h.open(editor,text),"failed launch cannot retry implicitly");
  ok(SnsForwardDraft.kind(54,9,"grid","")==SnsForwardDraft.Kind.PHOTOS,"new style54 nine-grid retained as ordered images");
  ok(SnsForwardDraft.kind(15,1,"video","")==SnsForwardDraft.Kind.VIDEO,"native short sight remains supported");
  String finderXml="<finderFeed><objectId>synthetic</objectId><thumbUrl>https://example.test/cover.jpg</thumbUrl></finderFeed>";
  SnsForwardDraft finder=new SnsForwardDraft(SnsForwardDraft.Kind.FINDER,"caption","","",0,Collections.<String>emptyList(),null,null,finderXml);
  ok(finder.complete(),"Finder metadata accepted without inventing local video files");Intent finderIntent=SnsForwardComposer.intent(finder);
  ok(finderIntent.extras.get("Ksnsupload_type").equals(17)&&finderIntent.extras.get("ksnsupload_finder_object_xml").equals(finderXml),"Finder uses native card editor type and exact metadata");
  ok(SnsForwardCard.preview(finderXml,"finderFeed").equals("https://example.test/cover.jpg"),"Finder cover selected for preload");
  ok(!SnsForwardCard.valid("<!DOCTYPE finderFeed [<!ENTITY x SYSTEM 'file:///invalid'>]>"+finderXml,"finderFeed"),"card rejects external entities");
  ok(!SnsForwardCard.valid("<wrong/>","finderFeed"),"wrong native card kind cannot reach editor");
  SnsForwardDraft mp=new SnsForwardDraft(SnsForwardDraft.Kind.MPVIDEO,"caption","","",0,Collections.<String>emptyList(),null,null,"<mmbrandmpvideo><mpUrl>https://example.test/video</mpUrl></mmbrandmpvideo>");
  ok(mp.complete()&&SnsForwardComposer.intent(mp).extras.get("Ksnsupload_type").equals(19),"native MP video share card supported");
  Path expiry=root.resolve("expiry");SnsForwardFiles expiring=new SnsForwardFiles(expiry.toFile());File expiringFile=expiring.copy(one,false,0,new SnsForwardFiles.Guard(){public boolean valid(){return true;}});expiring.handoff();
  long stamp=new File(expiring.directory,".handed").lastModified();SnsForwardFiles.cleanup(expiry.toFile(),stamp+SnsForwardFiles.KEEP_MS-1);ok(expiringFile.isFile(),"active editor assets retained until 24-hour boundary");
  SnsForwardFiles.cleanup(expiry.toFile(),stamp+SnsForwardFiles.KEEP_MS);ok(!expiring.directory.exists()&&one.exists(),"expired draft copies cleaned without touching original cache");
  SnsForwardFiles orphan=new SnsForwardFiles(expiry.toFile());orphan.copy(one,false,0,new SnsForwardFiles.Guard(){public boolean valid(){return true;}});stamp=orphan.directory.lastModified();
  SnsForwardFiles.cleanup(expiry.toFile(),stamp+SnsForwardFiles.ORPHAN_MS);ok(!orphan.directory.exists(),"crashed unhanded preparation expires after three minutes");
  ok(SnsForwardFiles.MAX_ROOT==512L*1024*1024,"preload cache limited to 512 MiB");
  ok(SnsForwardDraft.serverId("18446744073709551615")==-1L,"unsigned native SNS id uses native long bit pattern");
  ok(SnsForwardDraft.serverId("42")==42L,"numeric SNS id uses V1-compatible value, not stringSeq");
  ok(SnsForwardDraft.serverId("18446744073709551616")==null&&SnsForwardDraft.serverId("0")==null&&SnsForwardDraft.serverId("sns_table_42")==null,"invalid server IDs never guess another post");
  System.out.println("PASS "+checks+" SNS draft eligibility, native editor extras, cancellation, account and owned-file behavior checks");
 }
}
