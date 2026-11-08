import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;

public final class NativeSnsForwardContractCheck extends NativeTabletContractCheck {
 public static void main(String[] args)throws Exception{
  String base="com.tencent.mm.plugin.sns.";
  Set<String> names=new HashSet<>(Arrays.asList(base+"ui.listener.h1",base+"ui.listener.c",base+"ui.dq",base+"ui.cl",base+"ui.j2",base+"ui.k9",base+"storage.SnsInfo",base+"storage.f2",base+"model.p4",base+"model.n0",base+"model.i0",base+"model.j1",base+"model.j6",base+"model.j7",base+"model.s7","dm4.h0",base+"data.a1",base+"ui.SnsUploadUI",base+"ui.an",base+"ui.t7",base+"ui.l6",base+"ui.e5",base+"ui.t2",base+"ui.u5","g71.k","g71.q","km4.b1","zk4.g","zk4.c","zk4.d","zk4.n0","cl4.p","com.tencent.mm.protocal.protobuf.TimeLineObject","gd5.na0","gd5.bs4","com.tencent.mm.storage.s7","com.tencent.mm.vfs.z6"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  String menu=type("android.view.MenuItem"),view=type("android.view.View"),context=type("android.view.ContextMenu"),menuInfo=type("android.view.ContextMenu$ContextMenuInfo"),sns=type(base+"storage.SnsInfo"),timeline=type("com.tencent.mm.protocal.protobuf.TimeLineObject"),media=type("gd5.bs4"),scene=type("com.tencent.mm.storage.s7");
  Method create=method(base+"ui.listener.h1","onCreateContextMenu","V",context,view,menuInfo);calls(create,base+"ui.listener.h1","a");calls(create,base+"ui.listener.h1","b");
  for(String c:new String[]{base+"ui.listener.c","zk4.c","zk4.d","zk4.n0"})method(c,"onMMMenuItemSelected","V",menu,"I");
  method("zk4.g","c",type("cl4.p"));method("cl4.p","Y0",sns);
  field(base+"ui.dq","a","Ljava/lang/String;");field(base+"ui.dq","b","Ljava/lang/String;");field(base+"ui.cl","a","Ljava/lang/String;");field(base+"ui.j2","b","Ljava/lang/String;");
  for(String m:new String[]{"I2","X1"})method(base+"storage.f2",m,sns,"Ljava/lang/String;");
  method(base+"storage.f2","V1",sns,"J");field(base+"ui.k9","a",timeline);
  method(base+"model.p4","mk",type(base+"storage.f2"));method(base+"model.p4","jk",type(base+"model.n0"));
  method(base+"model.p4","Hj","Ljava/lang/String;");method(base+"model.p4","Ij","Ljava/lang/String;");method(base+"model.p4","Rj",type(base+"model.j1"));
  method(base+"storage.SnsInfo","getTimeLine",timeline);method(base+"storage.SnsInfo","isAd","Z");method(base+"storage.SnsInfo","isNoCopy","Z");
  field("com.tencent.mm.protocal.protobuf.TimeLineObject","ContentDesc","Ljava/lang/String;");field("com.tencent.mm.protocal.protobuf.TimeLineObject","ContentObj",type("gd5.na0"));field("com.tencent.mm.protocal.protobuf.TimeLineObject","Id","Ljava/lang/String;");
  field("gd5.na0","e","I");field("gd5.na0","f","Ljava/lang/String;");field("gd5.na0","g","Ljava/lang/String;");field("gd5.na0","h","Ljava/util/LinkedList;");field("gd5.bs4","d","Ljava/lang/String;");
  method(base+"model.n0","d","Z",media,"I",type(base+"data.s0"),scene);method(base+"model.n0","l","Z","Ljava/lang/String;");field(base+"model.n0","n","Ljava/util/Map;");
  method(base+"model.n0","c","V",type(base+"model.i0"));method(base+"model.n0","v","V",type(base+"model.i0"));field(base+"model.n0","a",type("com.tencent.mm.sdk.platformtools.n3"));
  method(base+"model.i0","onImageFinish","V","Ljava/lang/String;","I","Z");method(base+"model.i0","onSightFinish","V","Ljava/lang/String;","Z");
  for(String m:new String[]{"l","t"}){Method path=method(base+"model.j1",m,"Ljava/lang/String;",media);if((path.getAccessFlags()&8)==0)throw new AssertionError("SNS image path must be static");checks++;}Method videoReady=method(base+"model.j1","D","Z",media);if((videoReady.getAccessFlags()&8)!=0)throw new AssertionError("SNS video readiness needs Rj instance");checks++;
  method(base+"model.j6","d","Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;");method(base+"data.a1","R","Ljava/lang/String;",media);method(base+"data.a1","Y","Ljava/lang/String;",media);method(base+"data.a1","G","Ljava/lang/String;","I","Ljava/lang/String;");
  method(base+"storage.SnsInfo","getLocalid","Ljava/lang/String;");
  Method resolved=method(base+"model.j7","i","Ljava/lang/String;","Ljava/lang/String;",media);calls(resolved,"n71.u1","k");calls(resolved,"com.tencent.mm.vfs.z6","j");
  field("com.tencent.mm.protocal.protobuf.TimeLineObject","CreateTime","I");method(base+"model.p4","wk",type(base+"model.s7"));method(base+"model.j7","c","Ljava/lang/String;","I",media);
  Method videoTask=method(base+"model.s7","m","Z",media,"I","Ljava/lang/String;","Z","Z","I","Ljava/lang/String;");calls(method("dm4.h0","start","Z"),base+"model.s7","m");calls(method("dm4.h0","isFinished","Z"),base+"model.j7","i");
  Method text=method(base+"ui.an","onClick","V",view);literal(text,"Ksnsupload_type");literal(text,"KSnsPostManu");
  literal(method(base+"ui.t7","k","V","Landroid/os/Bundle;"),"sns_kemdia_path_list");
  Method video=method(base+"ui.l6","k","V","Landroid/os/Bundle;");literal(video,"KSightPath");literal(video,"KSightThumbPath");
  Method link=method(base+"ui.e5","k","V","Landroid/os/Bundle;");for(String key:new String[]{"Ksnsupload_link","Ksnsupload_title","KsnsUpload_imgPath"})literal(link,key);
  field("gd5.na0","o",type("gd5.z23"));field("gd5.na0","q",type("gd5.se"));field("gd5.bs4","e","I");field("gd5.bs4","i","Ljava/lang/String;");
  field("g71.k","a",type("gd5.z23"));method("g71.k","a","Ljava/lang/String;");field("g71.q","a",type("gd5.se"));method("g71.q","a","Ljava/lang/String;");
  literal(method(base+"ui.t2","k","V","Landroid/os/Bundle;"),"ksnsupload_finder_object_xml");literal(method(base+"ui.u5","k","V","Landroid/os/Bundle;"),"ksnsupload_mp_video_xml");
  Method multi=method("km4.b1","h","Z","Landroid/os/Bundle;");literal(multi,"sns_kemdia_path");literal(multi,"sns_kemdia_path_list");calls(multi,"android.content.Intent","getStringArrayListExtra");
  System.out.println("PASS "+checks+" native SNS long-press, source, downloads and composer contracts");
 }
}
