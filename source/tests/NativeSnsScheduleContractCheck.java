import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;

/** Checks both signatures and actual native composer -> media ingestion -> upload call edges. */
public final class NativeSnsScheduleContractCheck extends NativeTabletContractCheck {
 public static void main(String[] args)throws Exception{
  String b="com.tencent.mm.plugin.sns.",builder=b+"model.i8",info=b+"storage.SnsInfo";
  Set<String> names=new HashSet<>(Arrays.asList(builder,info,b+"model.p4",b+"model.y7",b+"storage.f2",b+"storage.o2",b+"data.w0",b+"ui.t7",b+"ui.a8",b+"ui.o1","com.tencent.mm.protocal.protobuf.TimeLineObject","com.tencent.mm.protocal.protobuf.SnsObject","gd5.cs4","t56.sj0","t56.vj0","com.tencent.mm.sdk.storage.ua"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  method(builder,"<init>","V","I","Landroid/content/Context;");method(builder,"v",type(builder),"Ljava/lang/String;");Method images=method(builder,"Y","V","Ljava/util/List;");calls(images,b+"model.p4","ok");calls(images,b+"storage.o2","x3");
  method(b+"data.w0","<init>","V","Ljava/lang/String;","I");field(b+"data.w0","a","Ljava/lang/String;");field(b+"data.w0","f","I");field(b+"data.w0","e","I");
  Method commit=method(builder,"i","I");calls(commit,b+"storage.f2","y5");calls(method(b+"ui.t7","s","V",type(builder)),builder,"i");calls(method(b+"ui.t7","s","V",type(builder)),b+"model.y7","d");method(b+"model.y7","d","V");
  method(b+"model.p4","uk",type(b+"model.y7"));method(b+"model.p4","mk",type(b+"storage.f2"));method(b+"storage.f2","D2",type(info),"I");
  method(info,"getPostInfo",type("gd5.cs4"));method(info,"getTimeLine",type("com.tencent.mm.protocal.protobuf.TimeLineObject"));method(info,"getUserName","Ljava/lang/String;");method(info,"isDieItem","Z");method(info,"getLocalFlag","I");
  for(String m:new String[]{"setLocalPrivate","unSetExtFlag"})method(info,m,"V");for(String m:new String[]{"setPravited","removeSourceFlag"})method(info,m,"V","I");
  field(builder,"g",type("gd5.cs4"));field(builder,"h",type(info));field(builder,"f",type("com.tencent.mm.protocal.protobuf.TimeLineObject"));field(builder,"i",type("com.tencent.mm.protocal.protobuf.SnsObject"));field(builder,"p","Z");field(builder,"c","I");field("gd5.cs4","g","Ljava/lang/String;");field("gd5.cs4","f","I");field("gd5.cs4","T",type("t56.sj0"));method("t56.sj0","f",type("t56.sj0"),type("t56.vj0"));method("t56.vj0","a",type("t56.vj0"),"I");method("t56.vj0","getNumber","I");literal(method("t56.vj0","<clinit>","V"),"SNS_VISIBILITY_PRIVATE");field("com.tencent.mm.protocal.protobuf.SnsObject","VisibilityType","I");
  calls(method(b+"ui.o1","o","V",type(builder)),info,"setLocalPrivate");
  System.out.println("PASS "+checks+" native scheduled text/photo builder, visibility, ordered ingestion, commit and result contracts");
 }
}
