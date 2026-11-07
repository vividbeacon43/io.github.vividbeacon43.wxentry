import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;import com.android.tools.smali.dexlib2.iface.instruction.*;import com.android.tools.smali.dexlib2.iface.reference.*;
public final class NativeRecallTipContractCheck extends NativeRedPacketContractCheck {
 static void calls(Method m,String owner,String name){
  for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof MethodReference){MethodReference ref=(MethodReference)r;if(ref.getDefiningClass().equals(type(owner))&&ref.getName().equals(name)){checks++;return;}}}
  throw new AssertionError("Native call changed "+owner+"."+name);
 }
 static void string(Method m,String text){for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof StringReference&&((StringReference)r).getString().equals(text)){checks++;return;}}throw new AssertionError("Native key changed "+text);}
 static void literal(Method m,int value){for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof NarrowLiteralInstruction&&((NarrowLiteralInstruction)ins).getNarrowLiteral()==value){checks++;return;}throw new AssertionError("Native system-tip value changed "+value);}
 public static void main(String[] args)throws Exception{
  Set<String> names=new HashSet<>(Arrays.asList("zp0.j1","com.tencent.mm.plugin.messenger.foundation.h2","com.tencent.mm.storage.f9","com.tencent.mm.storage.g9","lm.d8","ip3.k0","zx3.l","nm5.g","qw5.s0","qw5.r0","androidx.recyclerview.widget.k3","qm5.d","xn5.a","com.tencent.mm.ui.chatting.viewitems.h0","com.tencent.mm.ui.widget.MMNeat7extView","com.tencent.neattextview.textview.view.NeatTextView"));
  try(ZipFile zip=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=zip.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  String msg=type("com.tencent.mm.storage.f9"),store="com.tencent.mm.storage.g9",s="Ljava/lang/String;";
  method("zp0.j1","v",type("eq0.a"),"Ljava/lang/Class;");method("com.tencent.mm.plugin.messenger.foundation.h2","Oj",type(store));method("com.tencent.mm.storage.f9","<init>","V");
  Method convert=method("lm.d8","convertFrom","V","Landroid/content/ContentValues;","Z");method("lm.d8","getMsgId","J");
  for(String key:new String[]{"msgId","msgSvrId","msgSeq","flag","type","createTime","isSend","content"})string(convert,key);
  Method insert=method(store,"Zb","J",msg);calls(insert,store,"a");
  Method path=method(store,"a","J",msg,"Z","Z");calls(path,"ip3.k0","b");calls(path,store,"q1");calls(path,"gg5.s0","doNotify");
  int index=0,allocate=-1,write=Integer.MAX_VALUE;
  for(Instruction ins:path.getImplementation().getInstructions()){
   if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof MethodReference){MethodReference ref=(MethodReference)r;if(ref.getDefiningClass().equals(type("ip3.k0"))&&ref.getName().equals("b"))allocate=index;if((ref.getDefiningClass().equals(type("gg5.k0"))&&Arrays.asList("l","v").contains(ref.getName()))||(ref.getDefiningClass().equals(type("xx0.c3"))&&ref.getName().equals("p1")))write=Math.min(write,index);}}
   index++;
  }
  if(allocate<0||allocate>=write)throw new AssertionError("Native id allocation must precede storage insertion");checks++;
  Method counter=method("ip3.k0","b","V",msg);calls(counter,"lm.d8","setMsgId");calls(counter,"com.tencent.mm.sdk.platformtools.o4","putLong");
  boolean synchronizedCounter=false,counterUpdate=false;for(Instruction ins:counter.getImplementation().getInstructions()){
   synchronizedCounter|=ins.getOpcode()==Opcode.MONITOR_ENTER;
   if(ins instanceof ReferenceInstruction&&ins.getOpcode()==Opcode.IPUT_WIDE){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof FieldReference&&((FieldReference)r).getDefiningClass().equals(type("ip3.k0"))&&((FieldReference)r).getName().equals("a"))counterUpdate=true;}
  }
  if(!synchronizedCounter||!counterUpdate)throw new AssertionError("Native allocator is no longer a separate synchronized counter");checks++;
  Method initialize=method(store,"Ce","V",type("ip3.k0"));string(initialize,"select max(msgid) from ");calls(initialize,"java.lang.Math","max");
  Method localTip=method("zx3.l","a","V",s,s,s);literal(localTip,10000);literal(localTip,6);literal(localTip,8);calls(localTip,store,"Zb");calls(localTip,"lm.d8","j1");
  Method bind=method("nm5.g","h","V",type("qw5.s0"),type("qw5.c"),"I","I","Z","Ljava/util/List;");
  calls(bind,"com.tencent.mm.ui.chatting.viewitems.b0","n");calls(bind,"com.tencent.mm.ui.chatting.viewitems.b0","m");calls(bind,"android.view.View","getTag");
  field("androidx.recyclerview.widget.k3","itemView","Landroid/view/View;");field("qm5.d","d",type("xn5.a"));field("xn5.a","b",msg);field("com.tencent.mm.ui.chatting.viewitems.h0","clickArea","Landroid/view/View;");
  method("lm.d8","G0","J");method("lm.d8","O0",s);method("lm.d8","A0","I");method("com.tencent.mm.storage.f9","getCreateTime","J");method("com.tencent.mm.storage.f9","getType","I");method("com.tencent.mm.storage.f9","j",s);
  method("com.tencent.neattextview.textview.view.NeatTextView","a","Ljava/lang/CharSequence;");method("com.tencent.mm.ui.widget.MMNeat7extView","c","V","Ljava/lang/CharSequence;","Landroid/widget/TextView$BufferType;","Ljava/lang/Boolean;");
  if(!classes.get(type("qw5.s0")).getSuperclass().equals(type("qw5.r0"))||!classes.get(type("qw5.r0")).getSuperclass().equals(type("androidx.recyclerview.widget.k3")))throw new AssertionError("Native holder inheritance changed");checks++;
  System.out.println("PASS "+checks+" native recall allocator and inline row/text/identity contracts");
 }
}
