import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public class NativeVoiceContractCheck {
    static Map<String,ClassDef> classes=new HashMap<>();static int checks;
    static Method method(String cls,String name,String result,String... params) {
        String type="L"+cls.replace('.','/')+";";ClassDef c=classes.get(type);
        if(c==null)throw new AssertionError("Missing "+cls);
        for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getReturnType().equals(result)
            &&m.getParameterTypes().equals(Arrays.asList(params))) { checks++;return m; }
        throw new AssertionError("Missing contract "+cls+"."+name);
    }
    static void calls(Method caller, Method callee) {
        for (Instruction instruction : caller.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (reference instanceof MethodReference && callee.equals(reference)) {
                checks++;
                return;
            }
        }
        throw new AssertionError(caller.getName() + " does not call " + callee.getName());
    }
    public static void main(String[] args)throws Exception {
        Set<String> names=new HashSet<>(Arrays.asList("com.tencent.mm.ui.transmit.MsgRetransmitUI","com.tencent.mm.ui.chatting.viewitems.gq","com.tencent.mm.ui.chatting.viewitems.nq","com.tencent.mm.ui.chatting.viewitems.rq","q71.n1","q71.c1","mc0.b","com.tencent.mm.vfs.z6","com.tencent.mm.vfs.x1","com.tencent.mm.vfs.k4","pj0.a","pj0.d","xj0.x","wj0.o","w41.z1","hr3.j0","hr3.m0","hr3.h0","hr3.o0","ak5.i4"));
        names.addAll(Arrays.asList("com.tencent.mm.ui.chatting.viewitems.sq", "com.tencent.mm.ui.chatting.viewitems.h0",
                "com.tencent.mm.ui.chatting.component.gp", "wk5.d", "w41.i9", "com.tencent.mm.modelbase.s1",
                "com.tencent.mm.ui.chatting.ChattingUIFragment", "com.tencent.mm.ui.chatting.BaseChattingUIFragment",
                "com.tencent.mm.ui.chatting.adapter.k", "com.tencent.mm.storage.g9", "yx0.k0",
                "sq.b", "ao5.sb", "com.tencent.mm.ui.zg", "com.tencent.mm.ui.FindMoreFriendsUI",
                "com.tencent.mm.autogen.events.VoipCheckIsDeviceUsingEvent", "com.tencent.mm.sdk.event.IEvent", "im.b10"));
        try(ZipFile zip=new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));
                for(ClassDef c:dex.getClasses())if(names.contains(c.getType().substring(1,c.getType().length()-1).replace('/','.')))classes.put(c.getType(),c);
            }
        }
        for(String cls:new String[]{"com.tencent.mm.ui.chatting.viewitems.gq","com.tencent.mm.ui.chatting.viewitems.nq"}) {
            method(cls,"S","Z","Lak5/h4;","Landroid/view/View;","Lqm5/d;");
            method(cls,"R","Z","Landroid/view/MenuItem;","Lwk5/d;","Lqm5/d;");
        }
        method("com.tencent.mm.ui.chatting.viewitems.nq","n","V","Lcom/tencent/mm/ui/chatting/viewitems/h0;","Lwk5/d;","Lqm5/d;","Ljava/lang/String;");
        String voiceItem = "com.tencent.mm.ui.chatting.viewitems.gq";
        String voiceActions = "com.tencent.mm.ui.chatting.viewitems.sq";
        String transcript = "com.tencent.mm.ui.chatting.component.gp";
        String message = "Lcom/tencent/mm/storage/f9;", chat = "Lwk5/d;";
        method(voiceItem, "n", "V", "Lcom/tencent/mm/ui/chatting/viewitems/h0;", chat, "Lqm5/d;", "Ljava/lang/String;");
        Method manual = method(voiceItem, "R", "Z", "Landroid/view/MenuItem;", chat, "Lqm5/d;");
        Method nativeRequest = method(voiceActions, "c", "Z", "I", chat, message, "Lqk5/n2;", "Z", "I");
        calls(manual, nativeRequest);
        calls(nativeRequest, method(transcript, "w0", "V", message, "Z", "I", "I", "I"));
        method(voiceActions, "p", "Z", message);
        method(voiceActions, "a", "Z", message, chat);
        String fragment = "com.tencent.mm.ui.chatting.ChattingUIFragment";
        for (String lifecycle : new String[]{"K0", "J0", "b1"}) method(fragment, lifecycle, "V");
        String adapter = "com.tencent.mm.ui.chatting.adapter.k";
        method(adapter, "n", "V", "Lyt3/r1;");
        method(adapter, "W0", message, "I");
        method(adapter, "getCount", "I");
        String storage = "com.tencent.mm.storage.g9";
        calls(method(storage, "Zb", "J", message), method(storage, "a", "J", message, "Z", "Z"));
        calls(method(voiceActions, "j", message, message),
                method("yx0.k0", "k2", message, "Ljava/lang/String;", "J"));
        method(transcript, "y0", "Z", "J");
        method(transcript, "C0", "Ljava/lang/String;", "J", "Ljava/lang/String;");
        method("com.tencent.mm.ui.chatting.viewitems.h0", "getCurrentMsgInfo", message, chat);
        method("com.tencent.mm.ui.chatting.viewitems.h0", "getAdapterPosition", "I");
        method("q71.c1", "g", "I", message, "Ljava/lang/String;");
        method("wk5.d", "x", "Ljava/lang/String;");
        method("w41.i9", "e", "Lcom/tencent/mm/modelbase/s1;");
        method("com.tencent.mm.modelbase.s1", "n", "I");
        String context = "Landroid/content/Context;", bundle = "Landroid/os/Bundle;";
        Method cameraGuard = method("sq.b", "i", "Z", context, "Z", bundle);
        Method videoGuard = method("sq.b", "C", "Z", context);
        Method otherCamera = method("sq.b", "m", "Z", context, "Z", bundle);
        calls(cameraGuard, otherCamera);
        calls(videoGuard, otherCamera);
        calls(videoGuard, method("sq.b", "q", "Z", context, "Z"));
        calls(videoGuard, method("sq.b", "B", "Z", context, "Z"));
        calls(videoGuard, method("sq.b", "s", "Z", context));
        calls(method("ao5.sb", "b", "V", message, "Ljava/lang/String;", "Ljava/lang/String;", "I", "Landroid/view/View;"), videoGuard);
        calls(method("com.tencent.mm.ui.zg", "onItemClick", "V", "Landroid/widget/AdapterView;", "Landroid/view/View;", "I", "J"), cameraGuard);
        calls(method("com.tencent.mm.ui.FindMoreFriendsUI", "w0", "Z", "Lcom/tencent/mm/ui/base/preference/q;",
                "Lcom/tencent/mm/ui/base/preference/Preference;", "Lcom/tencent/mm/ui/base/preference/m0;"), cameraGuard);
        Method callQuery = method("com.tencent.mm.autogen.events.VoipCheckIsDeviceUsingEvent", "<init>", "V");
        method("com.tencent.mm.sdk.event.IEvent", "e", "Z");
        Method voiceGate = method(voiceActions, "g", "Z", chat, message, "Lcom/tencent/mm/plugin/msgquote/model/MsgQuoteItem;");
        calls(voiceGate, callQuery);
        calls(voiceGate, method("sq.b", "q", "Z", context, "Z"));
        calls(voiceGate, method("sq.b", "v", "Z", context));
        calls(voiceGate, method("sq.b", "e", "Z", context));
        method("q71.n1","t1","Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/String;");
        method("q71.c1","c","Ljava/lang/String;","Ljava/lang/String;");
        method("mc0.b","gk","Ljava/lang/String;","Lcom/tencent/mm/storage/f9;","Ljava/lang/String;","Z");
        method("mc0.b","ik","Ljava/lang/String;","Ljv5/x;","Ljava/lang/String;","Z","Z");
        method("com.tencent.mm.vfs.z6","k","J","Ljava/lang/String;");
        method("com.tencent.mm.vfs.z6","d","J","Ljava/lang/String;","Ljava/lang/String;","Z");
        Method deleteFile=method("com.tencent.mm.vfs.z6","h","Z","Ljava/lang/String;");
        method("com.tencent.mm.vfs.z6","u","Z","Ljava/lang/String;");
        method("pj0.d","<init>","V","Ljava/lang/String;","Ljava/lang/String;");
        method("xj0.x","<init>","V","Lpj0/d;");
        method("wj0.o","Sj","V","Lhr3/f0;");
        method("pj0.a","<init>","V","Ljava/lang/String;","Ljava/lang/String;","J","J","J");
        method("wj0.o","Mj","V","Lpj0/a;");
        method("w41.z1","u","Ljava/lang/String;");
        for(String name:new String[]{"h","i"}) {
            boolean found=false;for(Field f:classes.get("Lpj0/d;").getFields())if(f.getName().equals(name)&&f.getType().equals("I"))found=true;
            if(!found)throw new AssertionError("Missing voice task field "+name);checks++;
        }
        // Verify semantics as well as signatures: k returns x1.c, populated from st_size.
        Method sizeMethod=method("com.tencent.mm.vfs.z6","k","J","Ljava/lang/String;");
        boolean returnsSize=false;
        for(Instruction ins:sizeMethod.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
            Reference ref=((ReferenceInstruction)ins).getReference();
            if(ref instanceof FieldReference && ((FieldReference)ref).getDefiningClass().equals("Lcom/tencent/mm/vfs/x1;")
                    && ((FieldReference)ref).getName().equals("c"))returnsSize=true;
        }
        if(!returnsSize)throw new AssertionError("VFS size field changed");checks++;
        Method stat=method("com.tencent.mm.vfs.k4","K","Lcom/tencent/mm/vfs/x1;","Ljava/io/File;");
        int sizeRegister=-1;boolean passesSize=false;
        for(Instruction ins:stat.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
            Reference ref=((ReferenceInstruction)ins).getReference();
            if(ref instanceof FieldReference && ((FieldReference)ref).getDefiningClass().equals("Landroid/system/StructStat;")
                    && ((FieldReference)ref).getName().equals("st_size"))sizeRegister=((TwoRegisterInstruction)ins).getRegisterA();
            if(ref instanceof MethodReference && ((MethodReference)ref).getDefiningClass().equals("Lcom/tencent/mm/vfs/x1;")
                    && ((MethodReference)ref).getName().equals("<init>") && ins instanceof RegisterRangeInstruction)
                passesSize=sizeRegister==((RegisterRangeInstruction)ins).getStartRegister()+4;
        }
        if(!passesSize)throw new AssertionError("VFS stat size mapping changed");checks++;
        boolean fileOnly=false;
        for(Instruction ins:deleteFile.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
            Reference ref=((ReferenceInstruction)ins).getReference();
            if(ref instanceof MethodReference && ((MethodReference)ref).getDefiningClass().equals("Lcom/tencent/mm/vfs/u2;")
                && ((MethodReference)ref).getName().equals("f") && ((MethodReference)ref).getParameterTypes().equals(Arrays.asList("Ljava/lang/String;")))fileOnly=true;
        }
        if(!fileOnly)throw new AssertionError("VFS delete must target one file");checks++;
        method("ak5.i4","setIcon","Landroid/view/MenuItem;","Landroid/graphics/drawable/Drawable;");
        method("ak5.i4","getIcon","Landroid/graphics/drawable/Drawable;");
        Map<String,String> fields=new LinkedHashMap<>();
        fields.put("Lcom/tencent/mm/ui/chatting/viewitems/rq;->c","Landroid/widget/TextView;");
        fields.put("Lcom/tencent/mm/ui/chatting/viewitems/rq;->p","Landroid/view/View;");
        fields.put("Lcom/tencent/mm/ui/chatting/component/gp;->t", "Ljava/util/LinkedHashMap;");
        fields.put("Lcom/tencent/mm/ui/chatting/component/gp;->m", "Z");
        fields.put("Lcom/tencent/mm/ui/chatting/BaseChattingUIFragment;->f", chat);
        fields.put("Lwk5/d;->i", "Z");
        fields.put("Lcom/tencent/mm/ui/chatting/adapter/k;->K", chat);
        fields.put("Lcom/tencent/mm/autogen/events/VoipCheckIsDeviceUsingEvent;->g", "Lim/b10;");
        for (String name : new String[]{"a", "b", "c"}) fields.put("Lim/b10;->" + name, "Z");
        fields.put("Lxj0/x;->i","Lpj0/d;");
        fields.put("Lpj0/d;->q","Lhr3/j0;");fields.put("Lpj0/d;->r","Lhr3/m0;");
        fields.put("Lpj0/d;->s","Lhr3/h0;");fields.put("Lpj0/d;->t","Lhr3/o0;");
        for(String type:new String[]{"j0","m0","h0","o0"})fields.put("Lhr3/"+type+";->a","I");
        fields.put("Lhr3/h0;->c","I");fields.put("Lhr3/h0;->d","I");
        for(Map.Entry<String,String> entry:fields.entrySet()) {
            String[] parts=entry.getKey().split("->");boolean found=false;
            for(Field f:classes.get(parts[0]).getFields())if(f.getName().equals(parts[1])&&f.getType().equals(entry.getValue()))found=true;
            if(!found)throw new AssertionError("Native result/icon contract missing "+entry.getKey());checks++;
        }
        System.out.println("PASS "+checks+" native voice and in-call chat media/scanner contracts");
    }
}
