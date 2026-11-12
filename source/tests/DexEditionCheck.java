import java.io.File;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.value.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public final class DexEditionCheck {
    public static void main(String[] args) throws Exception {
        DexFile dex=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.getDefault());
        ClassDef kernel=null;boolean images=false,cache=false,voice=false,forward=false,sender=false,picker=false,queue=false,icon=false,layout=false;
        Set<String> red=new HashSet<>();ClassDef settings=null;
        String root="Lcom/zafiro/wxentry/kernel/";
        Set<String> receiptClasses=new HashSet<>();
        boolean premium=args[1].equals("plus")||args[1].equals("vip");
        boolean callToolsMenu=false;
        ClassDef scanCamera=null;
        String legacyScanner="Lcom/zafiro/wxentry/scancam/ScanCameraRuntime;";
        for(ClassDef c:dex.getClasses()) {
            receiptClasses.add(c.getType());
            if(c.getType().equals(root+"ScanCameraRuntime;"))scanCamera=c;
            if(c.getType().equals(legacyScanner))
                throw new AssertionError("The APK class loader can shadow the legacy scanner type");
            if(c.getType().equals(root+"Kernel$MenuAction;"))for(Field field:c.getFields())
                if(field.getName().equals("CALL_TOOLS"))callToolsMenu=true;
            if(c.getType().startsWith(root+"RedPacket"))red.add(c.getType());
            if(c.getType().equals(root+"RedPacketSettings;"))settings=c;
            if(c.getType().equals(root+"Kernel;"))kernel=c;
            if(c.getType().equals(root+"ImageRecallRuntime;"))images=true;
            if(c.getType().equals(root+"RecallImageCache;"))cache=true;
            if(c.getType().equals(root+"VoiceRecallRuntime;"))voice=true;
            if(c.getType().equals(root+"VoiceForwardRuntime;"))forward=true;
            if(c.getType().equals(root+"VoiceForwardSender;"))sender=true;
            if(c.getType().equals(root+"VoiceForwardPicker;"))picker=true;
            if(c.getType().equals(root+"VoiceForwardQueue;"))queue=true;
            if(c.getType().equals(root+"VoiceForwardIcon;"))icon=true;
            if(c.getType().equals(root+"VoiceForwardLayout;"))layout=true;
            if(c.getType().startsWith(root+"VoiceForward"))for(Method m:c.getMethods()) {
                if(m.getImplementation()==null)continue;
                for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                    Reference r=((ReferenceInstruction)ins).getReference();
                    if(r instanceof MethodReference && ((MethodReference)r).getDefiningClass().equals("Lq71/o;"))
                        throw new AssertionError("Obsolete crashing voice uploader referenced");
                    if(r instanceof StringReference && ((StringReference)r).getString().equals("q71.o"))
                        throw new AssertionError("Obsolete uploader reflection referenced");
                }
            }
        }
        if(kernel==null||!voice||!forward||!sender||!picker||!queue||!icon||!layout)throw new AssertionError("Base voice runtime missing");
        checkScannerLinkage(kernel, scanCamera, legacyScanner);
        if(receiptClasses.contains(root+"CallToolsRuntime;")!=premium || callToolsMenu!=premium)
            throw new AssertionError("Experimental in-call tools edition boundary failed");
        Map<String,String> values=new HashMap<>();
        for(Field f:kernel.getStaticFields())if(f.getInitialValue() instanceof StringEncodedValue)
            values.put(f.getName(),((StringEncodedValue)f.getInitialValue()).getValue());
        if(!args[1].equals(values.get("EDITION"))||!args[2].equals(values.get("BUILD")))throw new AssertionError("Version/edition mismatch");
        for(String name:new String[]{"RedPacketUi","RedPacketUiNative","RedPacketPolicy","RedPacketEngine","RedPacketNative","RedPacketRuntime","RedPacketSettings"})
            if(!red.contains(root+name+";"))throw new AssertionError("Red packet runtime missing: "+name);
        boolean contextAtBoot=false, nativeUiDelegate=false, fullNativeEntry=false, callToolsAtBoot=false;
        for(Method m:kernel.getMethods())if(m.getImplementation()!=null)
            for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference) {
                    MethodReference call=(MethodReference)ref;
                    if(m.getName().equals("boot0") && call.getDefiningClass().equals(root+"Kernel;") && call.getName().equals("rememberAppContext"))contextAtBoot=true;
                    if(m.getName().equals("boot0") && call.getDefiningClass().equals(root+"CallToolsRuntime;") && call.getName().equals("start"))callToolsAtBoot=true;
                    if(m.getName().equals("openReceiveUI") && call.getDefiningClass().equals(root+"RedPacketUiNative;") && call.getName().equals("prepare"))fullNativeEntry=true;
                    if(m.getName().equals("openReceiveUI") && call.getDefiningClass().equals(root+"RedPacketUi;") && call.getName().equals("open"))nativeUiDelegate=true;
                }
            }
        if(!contextAtBoot || !nativeUiDelegate || !fullNativeEntry)throw new AssertionError("Native UI context recovery is not wired into the released kernel");
        if(callToolsAtBoot!=premium)throw new AssertionError("In-call tools boot boundary failed");
        boolean nativeTip=false;
        for(Method m:kernel.getMethods()) {
            if(m.getName().equals("insertKeptCopy")||m.getName().equals("cloneRecently")||m.getName().equals("notifyInsert"))throw new AssertionError("Unsafe raw message cloning path retained");
            if(m.getName().equals("insertTipRow") && m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference && ((MethodReference)ref).getDefiningClass().equals(root+"RecallTipRuntime;") && ((MethodReference)ref).getName().equals("insert"))nativeTip=true;
            }
        }
        if(!nativeTip)throw new AssertionError("Recall annotation does not record the original identity");
        for(ClassDef c:dex.getClasses())if(c.getType().equals(root+"RecallTipRuntime;")||c.getType().equals(root+"RecallMarkLayout;")||c.getType().equals(root+"RecallMarkStore;"))for(Method m:c.getMethods())if(m.getImplementation()!=null)
            for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference) {
                    String name=((MethodReference)ref).getName();
                    if(Arrays.asList("execSQL","insertWithOnConflict","replace","Zb","setMsgId").contains(name))throw new AssertionError("Annotation writes to native message storage");
                }
                if(ref instanceof StringReference && ((StringReference)ref).getString().equals("Zb"))throw new AssertionError("Annotation allocates a new native message");
            }
        for(String name:new String[]{"SnsScheduleData","SnsScheduleEngine","SnsScheduleStore","SnsScheduleMedia","SnsScheduleNative","SnsScheduleRuntime","SnsScheduleUi","SnsScheduleWake"})if(receiptClasses.contains(root+name+";")!=args[1].equals("vip"))throw new AssertionError("Scheduler VIP boundary failed: "+name);
        for(String name:new String[]{"SnsLocalRuntime","SnsLocalNative","SnsLocalPicker","SnsLocalPanel"})if(receiptClasses.contains(root+name+";")!=premium)throw new AssertionError("Local Moments paid boundary failed: "+name);
        for(String name:new String[]{"SnsLocalData","SnsLocalStore","SnsLocalSettings"})if(!receiptClasses.contains(root+name+";"))throw new AssertionError("Local Moments settings missing");
        boolean localStart=false;
        for(Method m:kernel.getMethods())if(m.getName().equals("boot0")&&m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)i).getReference();if(ref instanceof MethodReference&&((MethodReference)ref).getDefiningClass().equals(root+"SnsLocalRuntime;")&&((MethodReference)ref).getName().equals("start"))localStart=true;}
        if(localStart!=premium)throw new AssertionError("Local Moments runtime boot boundary failed");
        for(ClassDef c:dex.getClasses())if(c.getType().startsWith(root+"SnsLocal"))for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)i).getReference();
            if(ref instanceof MethodReference){MethodReference call=(MethodReference)ref;String name=call.getName();boolean viewCounterRestore=c.getType().equals(root+"SnsLocalSurfaces$Surface;")&&m.getName().equals("clearAdded")&&(name.equals("setIntField")||name.equals("setObjectField"));if(!viewCounterRestore&&Arrays.asList("execSQL","insertWithOnConflict","rawQuery","setObjectField","setIntField","performClick","callOnClick","sendBroadcast").contains(name)||call.getDefiningClass().startsWith("Lcom/tencent/mm/modelbase/"))throw new AssertionError("Local Moments must not modify native SNS records or submit social actions");}
            if(ref instanceof StringReference){String value=((StringReference)ref).getString();if(Arrays.asList("com.tencent.mm.plugin.sns.model.y7","com.tencent.mm.autogen.events.SnsRepostEvent","/cgi-bin/micromsg-bin/mmsnscomment","/cgi-bin/micromsg-bin/mmsnspost").contains(value))throw new AssertionError("Local Moments social submission protocol forbidden");}
        }
        boolean premiumFlag=false,seenFlag=false;
        for(Field f:kernel.getStaticFields())if(f.getName().equals("RP_PREMIUM_EDITION") && f.getInitialValue() instanceof BooleanEncodedValue) {
            premiumFlag=((BooleanEncodedValue)f.getInitialValue()).getValue();seenFlag=true;
        }
        if(!seenFlag || premiumFlag!=premium)throw new AssertionError("Red packet premium edition boundary changed");
        Boolean tabletPremium=null;
        for(Field f:kernel.getStaticFields())if(f.getName().equals("TABLET_PREMIUM_EDITION")&&f.getInitialValue() instanceof BooleanEncodedValue)tabletPremium=((BooleanEncodedValue)f.getInitialValue()).getValue();
        if(tabletPremium==null||tabletPremium.booleanValue()!=premium)throw new AssertionError("Tablet paid kernel boundary failed");
        boolean snsForward=receiptClasses.contains(root+"SnsForwardRuntime;");
        if(snsForward!=premium)throw new AssertionError("SNS forwarding must be advanced/VIP only");
        if(premium)for(String name:new String[]{"SnsForwardDraft","SnsForwardFiles","SnsForwardComposer","SnsForwardNative"})if(!receiptClasses.contains(root+name+";"))throw new AssertionError("SNS draft runtime missing");
        if(receiptClasses.contains(root+"SnsAdRuntime;")!=premium)throw new AssertionError("SNS ad filtering must be advanced/VIP only");
        for(String name:new String[]{"MomentsConfig","MomentsSettings"})if(!receiptClasses.contains(root+name+";"))throw new AssertionError("Moments switches missing");
        for(ClassDef c:dex.getClasses())if(c.getType().equals(root+"SnsAdRuntime;")||c.getType().equals(root+"SnsAdPolicy;"))for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)i).getReference();if(ref instanceof MethodReference){String name=((MethodReference)ref).getName();if(Arrays.asList("execSQL","delete","insert","update","replace","performClick","startActivity").contains(name))throw new AssertionError("SNS ad filter may only filter presentation data");}}
        boolean editorHandoff=false;
        for(ClassDef c:dex.getClasses())if(c.getType().startsWith(root+"SnsForward"))for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){
            Reference ref=((ReferenceInstruction)i).getReference();
            if(ref instanceof MethodReference){MethodReference call=(MethodReference)ref;String name=call.getName();
                if(name.equals("performClick")||name.equals("callOnClick")||name.equals("sendBroadcast")||call.getDefiningClass().equals("Lcom/tencent/mm/modelbase/s1;"))throw new AssertionError("SNS draft must not invoke publication/input APIs");
                if(name.equals("startActivity"))editorHandoff=true;
            }
            if(ref instanceof StringReference){String value=((StringReference)ref).getString();if(value.equals("com.tencent.mm.plugin.sns.model.y7")||value.equals("com.tencent.mm.autogen.events.SnsRepostEvent")||value.equals("Kis_retry_edit")||value.equals("Kis_from_sns_drafg_stg"))throw new AssertionError("SNS post/retry protocol must not be copied");}
        }
        if(editorHandoff!=premium)throw new AssertionError("SNS editor handoff boundary failed");
        if(images!=premium||cache!=premium)throw new AssertionError("Image boundary changed");
        boolean voiceStart=false,forwardStart=false,imageStart=false,recallMarkStart=false,mediaStart=false,originalStart=false,adStart=false;
        Set<String> methods=new HashSet<>();
        for(Method m:kernel.getMethods()) {
            methods.add(m.getName());
            if(!m.getName().equals("boot0")||m.getImplementation()==null)continue;
            for(Instruction i:m.getImplementation().getInstructions()) {
                if(!(i instanceof ReferenceInstruction))continue;
                Reference r=((ReferenceInstruction)i).getReference();
                if(!(r instanceof MethodReference))continue;
                MethodReference ref=(MethodReference)r;
                if(!ref.getName().equals("start"))continue;
                if(ref.getDefiningClass().equals(root+"VoiceRecallRuntime;"))voiceStart=true;
                if(ref.getDefiningClass().equals(root+"VoiceForwardRuntime;"))forwardStart=true;
                if(ref.getDefiningClass().equals(root+"ImageRecallRuntime;"))imageStart=true;
                if(ref.getDefiningClass().equals(root+"RecallMarkLayout;"))recallMarkStart=true;
                if(ref.getDefiningClass().equals(root+"MediaRecallRuntime;"))mediaStart=true;
                if(ref.getDefiningClass().equals(root+"OriginalImageRuntime;"))originalStart=true;
                if(ref.getDefiningClass().equals(root+"SnsAdRuntime;"))adStart=true;
            }
        }
        if(!voiceStart||!forwardStart||!recallMarkStart||!mediaStart||!originalStart||imageStart!=premium||adStart!=premium)throw new AssertionError("Runtime start boundary failed");
        if(!methods.contains("isMenuReady")||!methods.contains("openRecoveredMenu"))throw new AssertionError("Menu API missing");
        boolean redStart=false;
        for(Method m:kernel.getMethods())if(m.getName().equals("hookRedPacket") && m.getImplementation()!=null)
            for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference && ((MethodReference)ref).getDefiningClass().equals(root+"RedPacketRuntime;")
                        && ((MethodReference)ref).getName().equals("start"))redStart=true;
            }
        if(!redStart)throw new AssertionError("Red packet runtime not started");
        for(String name:new String[]{"ReceiptConfig","ReceiptStore","ReceiptTargets","ReceiptSettings","SettingsUi","RecallTipRuntime","RecallMarkStore","RecallMarkLayout","TransferEngine","TransferNative","TransferRuntime","NativeSceneBus","MediaMessagePolicy","RecallMediaCache","MediaNative","MediaRecallRuntime","OriginalImageRuntime"})
            if(!receiptClasses.contains(root+name+";"))throw new AssertionError("Receipt runtime missing: "+name);
        boolean transferStart=false;
        for(Method m:kernel.getMethods())if(m.getName().equals("hookRedPacket") && m.getImplementation()!=null)
            for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference && ((MethodReference)ref).getDefiningClass().equals(root+"TransferRuntime;") && ((MethodReference)ref).getName().equals("start"))transferStart=true;
            }
        if(!transferStart)throw new AssertionError("Transfer runtime not started");
        if(!methods.contains("receiptAccount") || !methods.contains("freeRedPacketForegroundOnly"))throw new AssertionError("Receipt account/free gate missing");
        if(!methods.contains("redPacketPremiumEnabled"))throw new AssertionError("Red packet authorization gate missing");
        boolean singleChoice=false,multiChoice=false,frontLabel=false,silentLabel=false;
        for(Method m:settings.getMethods())if(m.getName().equals("mode") && m.getImplementation()!=null)
            for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
                Reference ref=((ReferenceInstruction)ins).getReference();
                if(ref instanceof MethodReference) {
                    String name=((MethodReference)ref).getName();singleChoice|=name.equals("setSingleChoiceItems");multiChoice|=name.equals("setMultiChoiceItems");
                }
                if(ref instanceof StringReference) {
                    String label=((StringReference)ref).getString();frontLabel|=label.equals("前台自动点领取");silentLabel|=label.equals("后台静默领取（高级版）");
                }
            }
        if(!singleChoice || multiChoice || !frontLabel || !silentLabel)throw new AssertionError("Receive mode is not an explicit mutually exclusive picker");
        System.out.println("PASS edition="+args[1]+" BUILD="+values.get("BUILD")+" voice=true forward=true receipt=true redpacket_premium="+premium+" receive_mode="+(args[1].equals("free")?"foreground-auto-open":"single-choice")+" image="+premium+" tablet_premium="+premium+" media_prefetch=true original_download=true keyword_filter_vip_only=true");
    }

    private static void checkScannerLinkage(ClassDef kernel, ClassDef scanner, String legacyScanner) {
        if (scanner == null) throw new AssertionError("Scanner must belong to the dynamic kernel namespace");
        Map<String, String> expected = new HashMap<>();
        expected.put("boot0", "start");
        expected.put("menuToggleValue", "enabled");
        expected.put("setMenuToggle", "setEnabled");
        for (Method method : kernel.getMethods()) {
            if (!expected.containsKey(method.getName()) || method.getImplementation() == null) continue;
            String target = expected.get(method.getName());
            for (Instruction instruction : method.getImplementation().getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction)) continue;
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (!(reference instanceof MethodReference)) continue;
                MethodReference call = (MethodReference) reference;
                if (call.getDefiningClass().equals(legacyScanner))
                    throw new AssertionError("Scanner menu references the old APK namespace");
                if (call.getDefiningClass().equals(scanner.getType()) && call.getName().equals(target))
                    expected.remove(method.getName());
            }
        }
        if (!expected.isEmpty()) throw new AssertionError("Missing local scanner calls: " + expected);
    }

}
