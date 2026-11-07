import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;import com.android.tools.smali.dexlib2.iface.*;import com.android.tools.smali.dexlib2.iface.instruction.*;import com.android.tools.smali.dexlib2.iface.reference.*;
public class NativeTabletContractCheck extends NativeRedPacketContractCheck {
 static void calls(Method method,String owner,String name){for(Instruction ins:method.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof MethodReference&&((MethodReference)r).getDefiningClass().equals(type(owner))&&((MethodReference)r).getName().equals(name)){checks++;return;}}throw new AssertionError("Missing native call "+owner+"."+name);}
 static void literal(Method method,String value){for(Instruction ins:method.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof StringReference&&((StringReference)r).getString().equals(value)){checks++;return;}}throw new AssertionError("Missing native literal "+value);}
 public static void main(String[] args)throws Exception{
  String account="com.tencent.mm.plugin.account.ui.",qr="com.tencent.mm.plugin.login_exdevice.ui.LoginAsExDeviceUI";
  Set<String> names=new HashSet<>(Arrays.asList("com.tencent.mm.ui.ok",account+"LoginUI",account+"LoginHistoryUI",account+"MMBaseAccountActivity",qr,"cj3.b","com.tencent.mm.plugin.login_exdevice.ui.g","com.tencent.mm.app.s7"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  Method tablet=method("com.tencent.mm.ui.ok","C","Z");if((tablet.getAccessFlags()&8)==0)throw new AssertionError("Tablet predicate no longer static");checks++;
  calls(tablet,"com.tencent.mm.ui.ok","b");
  for(String page:new String[]{"LoginUI","LoginHistoryUI"})calls(method(account+page,"initView","V"),"com.tencent.mm.ui.ok","C");
  calls(method(account+"MMBaseAccountActivity","H7","V"),"com.tencent.mm.ui.ok","C");
  method(qr,"initView","V");calls(method(qr,"onCreate","V","Landroid/os/Bundle;"),qr,"initView");
  method(qr,"onSceneEnd","V","I","I","Ljava/lang/String;",type("com.tencent.mm.modelbase.n1"));field(qr,"r","Ljava/lang/String;");field(qr,"s","[B");
  literal(method("cj3.b","<init>","V"),"/cgi-bin/micromsg-bin/getloginqrcode");method("cj3.b","getType","I");
  calls(method("com.tencent.mm.plugin.login_exdevice.ui.g","run","V"),"cj3.b","<init>");
  Method device=method("com.tencent.mm.app.s7","b","V","I");calls(device,"com.tencent.mm.ui.ok","C");literal(device,"pad-android-");
  System.out.println("PASS "+checks+" native tablet/login/QR contracts");
 }
}
