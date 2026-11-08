import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;

public class NativeMediaContractCheck extends NativeTabletContractCheck {
 public static void main(String[] args)throws Exception{
  Set<String> names=new HashSet<>(Arrays.asList("w41.u","n71.b2","n71.u1","rb5.q","fj0.d","ql5.g","com.tencent.mm.pluginsdk.model.app.v5","com.tencent.mm.pluginsdk.model.app.e","com.tencent.mm.pluginsdk.model.app.d","lm.s","xx0.r","yx0.k0","g61.a0","g61.b0","g61.k0","g61.j","mc0.b","jv5.e0","com.tencent.mm.vfs.z6","com.tencent.mm.ui.chatting.gallery.u1","com.tencent.mm.ui.chatting.gallery.d3","com.tencent.mm.ui.chatting.gallery.e","com.tencent.mm.ui.chatting.gallery.ImageGalleryUI"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  String msg=type("com.tencent.mm.storage.f9"),info=type("n71.u1"),image=type("g61.a0"),id=type("com.tencent.mm.plugin.msg.MsgIdTalker"),callback=type("ma0.w");
  Method revoke=method("w41.u","c","V","Ljava/lang/String;","J",type("com.tencent.mm.modelbase.p0"),"Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;");
  calls(revoke,"com.tencent.mm.storage.g9","Sd");calls(revoke,"com.tencent.mm.sdk.event.IEvent","e");
  method("n71.b2","h",info,"Ljava/lang/String;");method("n71.b2","E","I","Ljava/lang/String;");method("n71.b2","F","I","Ljava/lang/String;");
  method("n71.u1","k","Z");method("n71.u1","l","Z");method("n71.u1","i","Ljava/lang/String;");
  for(String name:new String[]{"c","n"})field("n71.u1",name,"J");for(String name:new String[]{"f","i"})field("n71.u1",name,"I");
  calls(method("fj0.d","Fj","Ljava/lang/String;",msg,"Z"),"fj0.d","Gj");
  method("com.tencent.mm.pluginsdk.model.app.v5","Ej",type("com.tencent.mm.pluginsdk.model.app.e"));
  method("com.tencent.mm.pluginsdk.model.app.e","M1",type("com.tencent.mm.pluginsdk.model.app.d"),"J","Ljava/lang/String;");
  method("com.tencent.mm.pluginsdk.model.app.d","m0","Z");field("lm.s","field_fileFullPath","Ljava/lang/String;");field("lm.s","field_msgInfoTalker","Ljava/lang/String;");field("lm.s","field_isUpload","Z");
  for(String name:new String[]{"field_msgInfoId","field_totalLen","field_offset"})field("lm.s",name,"J");
  field("ql5.g","a",type("ql5.g"));Method download=method("ql5.g","j","V",msg,"Ljava/lang/String;");calls(download,"com.tencent.mm.pluginsdk.model.app.e","M1");calls(download,"com.tencent.mm.modelbase.s1","g");
  method("xx0.r","v",type("xx0.r"),"Ljava/lang/String;");field("xx0.r","i","I");
  method("yx0.k0","cj",msg,"Ljava/lang/String;","J");method("com.tencent.mm.vfs.z6","i","Ljava/lang/String;","Ljava/lang/String;","Z");
  method("g61.j","b","I","J",id,"I","Ljava/lang/Object;","I",callback,"I","Z");method("g61.j","g","Z","J",id,"I",callback);
  field("g61.a0","a","J");field("g61.a0","q","J");field("g61.a0","d","I");method("g61.a0","k","Z");method("g61.a0","l","Z");
  Method original=method("g61.b0","d",image,image);calls(original,"g61.a0","k");calls(original,"g61.k0","u3");
  method("g61.k0","s4","Ljava/lang/String;","Ljava/lang/String;",image);field("jv5.e0","g",type("jv5.e0"));method("mc0.b","Mj","Ljava/lang/String;",msg,type("jv5.e0"),"Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;");
  Method viewer=method("com.tencent.mm.ui.chatting.gallery.u1","b0","V","I","Z");calls(viewer,"g61.j","b");calls(method("com.tencent.mm.ui.chatting.gallery.d3","x",image,msg,"Z"),"g61.k0","K3");
  method("com.tencent.mm.ui.chatting.gallery.d3","x",image,msg,"Z");
  field("com.tencent.mm.ui.chatting.gallery.e","d",type("com.tencent.mm.ui.chatting.gallery.u1"));
  field("com.tencent.mm.ui.chatting.gallery.u1","g",type("com.tencent.mm.ui.chatting.gallery.ImageGalleryUI"));method("com.tencent.mm.ui.chatting.gallery.u1","v",msg);
  field("com.tencent.mm.ui.chatting.gallery.ImageGalleryUI","D","J");field("com.tencent.mm.ui.chatting.gallery.ImageGalleryUI","E","Ljava/lang/String;");
  method("com.tencent.mm.ui.chatting.gallery.ImageGalleryUI","A9","Z");method("com.tencent.mm.ui.chatting.gallery.ImageGalleryUI","Ba","V","Z");
  System.out.println("PASS "+checks+" native video/file revoke, download, path and original image contracts");
 }
}
