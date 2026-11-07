import java.io.*;import java.util.*;import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;import com.android.tools.smali.dexlib2.iface.instruction.*;import com.android.tools.smali.dexlib2.iface.reference.*;
public class NativeTransferContractCheck extends NativeRedPacketContractCheck {
 static void string(Method m,String value){for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof StringReference&&((StringReference)r).getString().equals(value)){checks++;return;}}throw new AssertionError("Missing native string "+value);}
 public static void main(String[] args)throws Exception{
  String model="com.tencent.mm.plugin.remittance.model.",ui="com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI",redUi="com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI";
  Set<String> names=new HashSet<>(Arrays.asList(model+"h1",model+"n0",ui,redUi,"xx0.r"));
  try(ZipFile zip=new ZipFile(args[0])){Enumeration<? extends ZipEntry> es=zip.entries();while(es.hasMoreElements()){ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);}}
  String s="Ljava/lang/String;",i="I",v="V",j="Lorg/json/JSONObject;";
  Method qc=method(model+"h1","<init>",v,i,s,s,i,s,s),cc=method(model+"n0","<init>",v,s,s,i,s,s,i,s,s,i,s,"Ljava/util/Map;","J",s,s);
  for(String key:new String[]{"transfer_id","trans_id","invalid_time","group_username","transfer_attach"})string(qc,key);
  for(String key:new String[]{"transaction_id","trans_id","op","username","total_fee","recv_account_type","bind_serial","group_username"})string(cc,key);
  Method query=method(model+"h1","onGYNetEnd",v,i,s,j);method(model+"n0","onGYNetEnd",v,i,s,j);
  mapping(query,"trans_status",model+"h1","h");mapping(query,"payer_name",model+"h1","m");mapping(query,"receiver_name",model+"h1","i");mapping(query,"is_payer",model+"h1","n");mapping(query,"fee_type",model+"h1","g");mapping(query,"delay_confirm_flag",model+"h1","t");string(query,"recv_account_info");field(model+"h1","D",type(model+"x0"));
  string(query,"fee");string(method(model+"h1","getUri",s),"/cgi-bin/mmpay-bin/transferquery");string(method(model+"n0","getUri",s),"/cgi-bin/mmpay-bin/transferoperation");
  Method after=method(ui,"O7",v,type(model+"n0"));string(after,"confirm");boolean refresh=false;for(Instruction ins:after.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof MethodReference&&((MethodReference)r).getName().equals("N7"))refresh=true;}if(!refresh)throw new AssertionError("Native confirmation no longer refreshes status");checks++;
  field(redUi,"o","Landroid/widget/Button;");Method create=method(redUi,"onCreate",v,"Landroid/os/Bundle;");string(create,"key_native_url");
  boolean session=false;for(Method m:classes.get(type(redUi)).getMethods())if(m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof StringReference&&((StringReference)r).getString().equals("key_username"))session=true;}if(!session)throw new AssertionError("Red packet session key changed");checks++;
  Set<String> xmlKeys=new HashSet<>();for(Method m:classes.get(type("xx0.r")).getMethods())if(m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){Reference r=((ReferenceInstruction)ins).getReference();if(r instanceof StringReference)xmlKeys.add(((StringReference)r).getString());}
  for(String key:new String[]{"paysubtype","transcationid","transferid","total_fee","fee_type","payer_username","receiver_username"}){boolean found=false;for(String value:xmlKeys)if(value.endsWith(".wcpayinfo."+key))found=true;if(!found)throw new AssertionError("Native transfer XML field changed "+key);checks++;}
  System.out.println("PASS "+checks+" native transfer and manual red packet UI contracts");
 }
}

