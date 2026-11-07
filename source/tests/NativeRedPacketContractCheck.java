import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public class NativeRedPacketContractCheck {
    static Map<String,ClassDef> classes=new HashMap<>();static int checks;
    static String type(String c) { return "L"+c.replace('.','/')+";"; }
    static Method method(String cls,String name,String result,String... args) {
        ClassDef c=classes.get(type(cls));if(c==null)throw new AssertionError("Missing class "+cls);
        for(Method m:c.getMethods())if(m.getName().equals(name) && m.getReturnType().equals(result) && m.getParameterTypes().equals(Arrays.asList(args))) { checks++;return m; }
        throw new AssertionError("Missing method "+cls+"."+name);
    }
    static void field(String cls,String name,String expected) {
        for(Field f:classes.get(type(cls)).getFields())if(f.getName().equals(name) && f.getType().equals(expected)) { checks++;return; }
        throw new AssertionError("Missing field "+cls+"."+name);
    }
    static void mapping(Method m,String key,String cls,String field) {
        int remaining=0;
        for(Instruction ins:m.getImplementation().getInstructions()) {
            if(ins instanceof ReferenceInstruction) {
                Reference r=((ReferenceInstruction)ins).getReference();
                if(r instanceof StringReference && ((StringReference)r).getString().equals(key))remaining=8;
                if(remaining>0 && r instanceof FieldReference) {
                    FieldReference f=(FieldReference)r;
                    if(f.getDefiningClass().equals(type(cls)) && f.getName().equals(field)) { checks++;return; }
                }
            }
            remaining--;
        }
        throw new AssertionError("JSON mapping changed: "+key+" -> "+cls+"."+field);
    }
    public static void main(String[] args) throws Exception {
        String model="com.tencent.mm.plugin.luckymoney.model.";
        Set<String> names=new HashSet<>(Arrays.asList(model+"n6",model+"h6",model+"e1",model+"q5",model+"i5",model+"o5","q61.s0","w41.z1","zp0.j1","zp0.y","com.tencent.mm.modelbase.s1","com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI"));
        try(ZipFile zip=new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();if(!entry.getName().matches("classes[0-9]*\\.dex"))continue;
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(entry)));
                for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);
            }
        }
        String s="Ljava/lang/String;",i="I",v="V";
        method(model+"n6","<init>",v,i,i,s,s,i,s,s);
        method(model+"h6","<init>",v,i,i,s,s,s,s,s,s,s,s);
        method(model+"h6","<init>",v,i,i,s,s,s,s,s,s,s);
        method("q61.s0","<init>",v,s,s,i,i,"J",s);field("q61.s0","f","J");
        method("w41.z1","u",s);method("w41.z1","m",s);method(model+"o5","l",s);
        method("zp0.j1","q",type("zp0.y"));field("zp0.y","b",type("com.tencent.mm.modelbase.s1"));
        method("com.tencent.mm.modelbase.s1","g","Z",type("com.tencent.mm.modelbase.n1"));
        method("com.tencent.mm.modelbase.s1","onSceneEnd",v,i,i,s,type("com.tencent.mm.modelbase.n1"));
        method(model+"q5","isJumpRemind","Z");
        for(String f:new String[]{"s","t"})field(model+"n6",f,i);
        for(String f:new String[]{"P","o","z","C"})field(model+"n6",f,s);
        field(model+"h6","h",type(model+"e1"));field(model+"h6","i",s);field(model+"h6","m",s);field(model+"e1","A",i);field(model+"e1","q","J");
        for(String f:new String[]{"n","i","Q"})field(model+"e1",f,s);
        Method query=method(model+"n6","onGYNetEnd",v,i,s,"Lorg/json/JSONObject;");
        mapping(query,"hbStatus",model+"n6","s");mapping(query,"receiveStatus",model+"n6","t");
        mapping(query,"timingIdentifier",model+"n6","P");mapping(query,"sendNick",model+"n6","o");
        mapping(query,"sendUserName",model+"n6","z");mapping(query,"agreed_flag",model+"n6","C");
        Method parse=method(model+"i5","e",type(model+"e1"),"Lorg/json/JSONObject;");
        mapping(parse,"amount",model+"e1","q");mapping(parse,"receiveStatus",model+"e1","A");
        mapping(parse,"sendId",model+"e1","n");mapping(parse,"sendNick",model+"e1","i");mapping(parse,"sendUserName",model+"e1","Q");
        method(model+"h6","onGYNetEnd",v,i,s,"Lorg/json/JSONObject;");
        Method opened=method("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI","I7",v,type(model+"h6"));
        mapping(opened,"key_sendid",model+"h6","i");mapping(opened,"key_native_url",model+"h6","m");
        System.out.println("PASS "+checks+" native red packet request/result/text contracts");
    }
}

