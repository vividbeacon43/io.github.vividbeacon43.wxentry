import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;import com.android.tools.smali.dexlib2.iface.instruction.*;import com.android.tools.smali.dexlib2.iface.reference.*;
public final class NativeRedPacketUiContractCheck extends NativeRedPacketContractCheck {
 static void literal(Method method,String value) {
  for(Instruction i:method.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof StringReference&&value.equals(((StringReference)r).getString())){checks++;return;}}
  throw new AssertionError("Native UI literal changed: "+value);
 }
 static void calls(Method method,String owner,String name) {
  for(Instruction i:method.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference&&((MethodReference)r).getDefiningClass().equals(type(owner))&&((MethodReference)r).getName().equals(name)){checks++;return;}}
  throw new AssertionError("Native receive routing changed: "+owner+"."+name);
 }
 public static void main(String[] args)throws Exception {
  String plugin="com.tencent.mm.plugin.wxpay.g",api="com.tencent.mm.plugin.wxpay.api.IPluginWxPay",context="Landroid/content/Context;",bundle="Landroid/os/Bundle;",intent="Landroid/content/Intent;",s="Ljava/lang/String;";
  Set<String> names=new HashSet<>(Arrays.asList(plugin,api,"fi5.n0","xx0.r","dk0.s3","yc5.l","com.tencent.mm.ui.chatting.viewitems.e4","com.tencent.mm.ui.chatting.viewitems.i4"));
  try(ZipFile z=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=z.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(z.getInputStream(e)));for(ClassDef c:d.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  method("xx0.r","v",type("xx0.r"),s);field("xx0.r","s1",s);field("xx0.r","r1",s);field("xx0.r","i","I");
  method("fi5.n0","c",type("fi5.m"),"Ljava/lang/Class;");method(api,"De","V",context,bundle,type("xx0.r"));
  Method entry=method(plugin,"De","V",context,bundle,type("xx0.r"));
  calls(entry,"fi5.n0","c");calls(entry,"dk0.s3","Oj");calls(entry,"yc5.l","j");
  literal(entry,".ui.LuckyMoneyNewReceiveUI");literal(entry,".ui.LuckyMoneyNotHookReceiveUI");
  for(String key:new String[]{"key_native_url","key_way","scene_id","key_invalidtime","key_receive_envelope_url","key_receive_envelope_dynamic_url","key_receive_envelope_atmosphere_dynamic_url","key_detail_envelope_url","key_material_flag"})literal(entry,key);
  for(Instruction i:entry.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)i).getReference();if(r instanceof MethodReference){MethodReference call=(MethodReference)r;String owner=call.getDefiningClass();if(owner.startsWith("Lcom/tencent/mm/modelbase/")||owner.equals(type("com.tencent.mm.plugin.luckymoney.model.h6")))throw new AssertionError("Native visible entry submits a direct receipt request");}}
  checks++;
  method("dk0.s3","Oj","Z");
  for(String name:new String[]{"com.tencent.mm.ui.chatting.viewitems.e4","com.tencent.mm.ui.chatting.viewitems.i4"}) {
   Method click=method(name,"f0","Z",type("wk5.d"),type("com.tencent.mm.storage.f9"));calls(click,"dk0.s3","Oj");literal(click,".ui.LuckyMoneyNewReceiveUI");literal(click,".ui.LuckyMoneyNotHookReceiveUI");
  }
  Method route=method("yc5.l","j","V",context,s,s,intent,bundle);calls(route,"yc5.l","l");
  Method launch=method("yc5.l","l","V",context,s,s,intent,"Z",bundle);calls(launch,"android.content.Intent","addFlags");calls(launch,"android.content.Context","startActivity");
  boolean newTask=false;for(Instruction i:launch.getImplementation().getInstructions())if(i instanceof NarrowLiteralInstruction&&((NarrowLiteralInstruction)i).getNarrowLiteral()==0x10000000)newTask=true;
  if(!newTask)throw new AssertionError("Native application route lacks NEW_TASK");checks++;
  System.out.println("PASS "+checks+" native complete red packet entry, page selector and application route contracts");
 }
}
