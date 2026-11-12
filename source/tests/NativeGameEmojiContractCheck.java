import java.io.*;import java.util.*;import java.util.zip.*;import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;
public final class NativeGameEmojiContractCheck extends NativeRedPacketContractCheck {
 public static void main(String[]args)throws Exception{
  Set<String> names=new HashSet<>(Arrays.asList("yr.p","vr.g","vr.u0","ow5.e","i82.h","wh5.l","com.tencent.mm.storage.n5","com.tencent.mm.storage.emotion.EmojiInfo","com.tencent.mm.storage.g9","com.tencent.mm.storage.f9","lm.d8","com.tencent.mm.ui.chatting.k8"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:d.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  String info=type("com.tencent.mm.api.IEmojiInfo"),emoji=type("com.tencent.mm.storage.emotion.EmojiInfo"),msg=type("com.tencent.mm.storage.f9"),view="Landroid/view/View;",s="Ljava/lang/String;";
  method("yr.p","b","V",view,"Landroid/content/Context;","I",type("vr.u0"));field("yr.p","d",type("com.tencent.mm.pluginsdk.ui.chat.xf"));
  method("vr.g","<init>","V",info,"I",s,"I");field("vr.g","b",info);field("vr.g","c","I");field("vr.g","d",s);field("vr.g","e","I");
  method("ow5.e","a","Z",view,"Ljava/lang/Object;");method("ow5.e","b","V",view,"Ljava/lang/Object;");
  method("i82.h","Z","V",s,emoji,msg,type("ga5.d"),"I");method("com.tencent.mm.storage.g9","Zb","J",msg);method("com.tencent.mm.storage.n5","f",type("com.tencent.mm.storage.n5"));method("com.tencent.mm.storage.n5","c",type("wh5.l"));method("wh5.l","D3","Landroid/database/Cursor;","I");
  for(String n:new String[]{"getName","getMd5","j"})method("com.tencent.mm.storage.emotion.EmojiInfo",n,s);method("com.tencent.mm.storage.emotion.EmojiInfo","k","Z");method("com.tencent.mm.ui.chatting.k8","b","V",info);
  method("com.tencent.mm.storage.f9","z0",s);method("lm.d8","A0","I");method("lm.d8","O0",s);
  System.out.println("PASS "+checks+" native game panel/chooser/outgoing-row contracts");
 }
}
