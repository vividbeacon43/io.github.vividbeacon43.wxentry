import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;
public final class NativeSnsLocalContractCheck extends NativeTabletContractCheck {
 public static void main(String[] args)throws Exception{
  String interaction="com.tencent.mm.plugin.sns.ui.improve.view.ImproveInteractionLayout";
  Set<String> names=new HashSet<>(Arrays.asList("yk4.o2","yk4.d2","cl4.p",interaction,"com.tencent.mm.pluginsdk.ui.u","yc5.l","com.tencent.mm.plugin.sns.storage.SnsInfo","zk4.g"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  Method q=method("yk4.o2","q","V");calls(q,interaction,"setupLayout");calls(q,"sl4.e","b");calls(q,"yk4.d2","getCommentContainer");
  method("yk4.o2","h","Z",type("qw5.s0"),type("el4.b"),"I");method("yk4.o2","getData",type("el4.b"));method("yk4.o2","getCommentView",type(interaction));method("yk4.d2","getCommentContainer","Landroid/widget/LinearLayout;");
  method("cl4.p","Y0",type("com.tencent.mm.plugin.sns.storage.SnsInfo"));method("cl4.p","a1",type("com.tencent.mm.protocal.protobuf.SnsObject"));
  method(interaction,"getLikeLayout",type("com.tencent.mm.plugin.sns.ui.SnsLikesTextView"));method(interaction,"getCommentLayout","Landroid/widget/LinearLayout;");method(interaction,"reset","V");
  Method avatar=method("com.tencent.mm.pluginsdk.ui.u","a","V","Landroid/widget/ImageView;","Ljava/lang/String;");if((avatar.getAccessFlags()&8)==0)throw new AssertionError("Avatar route not static");checks++;
  Method profile=method("yc5.l","j","V","Landroid/content/Context;","Ljava/lang/String;","Ljava/lang/String;","Landroid/content/Intent;","Landroid/os/Bundle;");if((profile.getAccessFlags()&8)==0)throw new AssertionError("Profile route not static");checks++;
  method("zk4.g","c",type("cl4.p"));method("com.tencent.mm.plugin.sns.storage.SnsInfo","getUserName","Ljava/lang/String;");method("com.tencent.mm.plugin.sns.storage.SnsInfo","isAd","Z");
  System.out.println("PASS "+checks+" native local Moments post binding, view pooling, avatar and profile contracts");
 }
}
