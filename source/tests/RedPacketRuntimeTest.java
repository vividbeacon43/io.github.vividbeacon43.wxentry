package com.zafiro.wxentry.kernel;
import java.io.*;
import java.lang.reflect.*;
import android.os.Handler;
import com.tencent.mm.plugin.luckymoney.model.*;
import de.robv.android.xposed.*;
public final class RedPacketRuntimeTest {
    static int checks;
    static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    static void flag(String path,boolean on) throws Exception {
        File f=new File(path);if(on) { f.getParentFile().mkdirs();f.createNewFile(); }else f.delete();
    }
    static void callback(Object scene,int type,int code) throws Throwable {
        Method end=com.tencent.mm.modelbase.s1.class.getDeclaredMethod("onSceneEnd",int.class,int.class,String.class,com.tencent.mm.modelbase.n1.class);
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{type,code,"",scene};XposedBridge.hooks.get(end).invoke(p);Handler.advance(0);
    }
    static String url(String id) { return RedPacketEngineTest.packet(id).url; }
    static void receive(String id) { RedPacketRuntime.received(url(id),10,"room@chatroom",System.currentTimeMillis());Handler.advance(500); }
    static h6 queryThenOpen() throws Throwable {
        Object query=zp0.j1.holder.b.last;check(query instanceof n6,"background receive submits native query");
        callback(query,0,0);Object open=zp0.j1.holder.b.last;check(open instanceof h6,"native query acknowledgement submits open");return (h6)open;
    }
    static void success(String id) throws Throwable { receive(id);h6 open=queryThenOpen();open.h.n=id;callback(open,0,0); }
    static void constructed(RedPacketRuntime runtime,Object scene,Object[] arguments,Constructor<?> ctor) throws Throwable {
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=scene;p.args=arguments;
        XposedBridge.hooks.get(ctor).after(p);
    }
    static void legacySuccess(RedPacketRuntime runtime,String id) throws Throwable {
        receive(id);
        n6 q=new n6(1,1,id,url(id),0,"v1.0","room@chatroom");constructed(runtime,q,q.arguments,runtime.api.queryCtor);callback(q,0,0);
        h6 o=new h6(1,1,id,url(id),"head","nick","room@chatroom","v1.0","token","");o.h.n=id;
        constructed(runtime,o,o.arguments,runtime.api.openCtor);callback(o,0,0);
    }
    public static void main(String[] args) throws Throwable {
        new File(Kernel.WXDIR).mkdirs();flag(Kernel.RP_ON_PATH,true);flag(RedPacketRuntime.SILENT,true);
        RedPacketRuntime.start(RedPacketRuntimeTest.class.getClassLoader());check(RedPacketRuntime.ready(),"adapter starts once");
        ReceiptConfig config=ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET);config.delayEnabled=true;config.minDelayMs=config.maxDelayMs=500;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        String personalUrl=RedPacketEngine.messageUrl(469762097,0L,"<nativeurl><![CDATA["+url("personal-variant")+"]]></nativeurl>");
        RedPacketRuntime.received(personalUrl,11,"personal-peer",System.currentTimeMillis());Handler.advance(500);
        check(zp0.j1.holder.b.last instanceof n6&&((n6)zp0.j1.holder.b.last).arguments[6].equals("personal-peer"),"personal variant submits query in original personal chat");
        h6 personalOpen=queryThenOpen();check(personalOpen.arguments[6].equals("personal-peer"),"personal native open retains personal chat");personalOpen.h.n="personal-variant";callback(personalOpen,0,0);
        check(RedPacketRuntime.history().contains("（单聊）"),"personal variant confirmed receipt recorded");
        success("first");check(Kernel.launched==0 && Kernel.clicked==0,"silent flow never opens/clicks an Activity");
        check(Kernel.lastToast.contains("发送者") && Kernel.lastToast.contains("1.23"),"success toast names sender and amount");
        check(RedPacketRuntime.history().contains("领取") && RedPacketRuntime.history().contains("发送者"),"success recorded locally");
        int submitted=zp0.j1.holder.b.submitted;Handler.advance(2000);check(zp0.j1.holder.b.submitted==submitted,"automatic reply defaults off");
        receive("first");check(zp0.j1.holder.b.submitted==submitted,"duplicate DB insert never submitted twice");
        flag(RedPacketRuntime.REPLY,true);RedPacketRuntime.write(RedPacketRuntime.TEXT,"谢谢老板！\n好运连连！\n");
        success("second");Handler.advance(1501);Object outgoing=zp0.j1.holder.b.last;
        check(outgoing instanceof q61.s0,"successful receipt triggers native text send");
        check(((q61.s0)outgoing).arguments[0].equals("room@chatroom") && ((q61.s0)outgoing).arguments[1].equals("谢谢老板！"),"fixed template sends in original group");
        callback(outgoing,0,0);check(RedPacketRuntime.history().contains("自动回复发送成功"),"reply acknowledgement recorded");
        submitted=zp0.j1.holder.b.submitted;callback(outgoing,0,0);Handler.advance(1000);check(zp0.j1.holder.b.submitted==submitted,"duplicate reply callback never resends");
        flag(RedPacketRuntime.RANDOM,true);success("random");Handler.advance(1501);outgoing=zp0.j1.holder.b.last;
        check(outgoing instanceof q61.s0 && java.util.Arrays.asList("谢谢老板！","好运连连！").contains(((q61.s0)outgoing).arguments[1]),"random template stays inside saved list");
        callback(outgoing,4,-1);check(RedPacketRuntime.history().contains("自动回复发送失败"),"reply failure is not reported as sent");
        success("disable-reply");submitted=zp0.j1.holder.b.submitted;flag(RedPacketRuntime.REPLY,false);Handler.advance(1501);
        check(zp0.j1.holder.b.submitted==submitted,"reply switch checked again after delay");
        flag(RedPacketRuntime.REPLY,true);RedPacketRuntime.write(RedPacketRuntime.TEXT," \n");success("empty");submitted=zp0.j1.holder.b.submitted;Handler.advance(1501);
        check(zp0.j1.holder.b.submitted==submitted,"blank configuration never sends a blank message");
        flag(Kernel.RP_TOAST_OFF_PATH,true);Kernel.lastToast="unchanged";success("no-toast");
        check("unchanged".equals(Kernel.lastToast),"success toast can be disabled without disabling local records");flag(Kernel.RP_TOAST_OFF_PATH,false);
        RedPacketRuntime.write(RedPacketRuntime.TEXT,"谢谢！");success("account-before-reply");submitted=zp0.j1.holder.b.submitted;
        w41.z1.current="other";Handler.advance(1501);check(zp0.j1.holder.b.submitted==submitted,"account switch before reply never sends to another account");w41.z1.current="self";
        receive("mode-change");Object pending=zp0.j1.holder.b.last;flag(RedPacketRuntime.SILENT,false);submitted=zp0.j1.holder.b.submitted;callback(pending,0,0);
        check(zp0.j1.holder.b.submitted==submitted && Kernel.launched==0,"turning silent mode off does not open a pending request or fall back to UI");flag(RedPacketRuntime.SILENT,true);
        RedPacketRuntime.received(url("off"),10,"room@chatroom",System.currentTimeMillis());Handler.advance(0);Kernel.MODULE_OFF=true;submitted=zp0.j1.holder.b.submitted;Handler.advance(500);
        check(zp0.j1.holder.b.submitted==submitted,"module disabled during wait blocks query");Kernel.MODULE_OFF=false;
        RedPacketRuntime.received(url("account"),10,"room@chatroom",System.currentTimeMillis());Handler.advance(0);w41.z1.current="other";Handler.advance(500);
        check(zp0.j1.holder.b.submitted==submitted,"account switch during wait blocks query");
        check(!RedPacketRuntime.history().contains("发送者"),"history isolated by account");w41.z1.current="self";
        config=ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET);config.group.rule=ReceiptConfig.Rule.OFF;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        receive("group-filter");check(zp0.j1.holder.b.submitted==submitted,"personal/group rules independently applied");
        config.group.rule=ReceiptConfig.Rule.WHITE;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        receive("whitelist");check(zp0.j1.holder.b.submitted==submitted,"empty active whitelist blocks group");
        config.group.rule=ReceiptConfig.Rule.ALL;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        RedPacketRuntime.received(url("history"),10,"room@chatroom",System.currentTimeMillis()-3600000);Handler.advance(1000);
        check(zp0.j1.holder.b.submitted==submitted,"historical sync not claimed");
        receive("timeout");Object late=zp0.j1.holder.b.last;Handler.advance(45000);submitted=zp0.j1.holder.b.submitted;callback(late,0,0);
        check(zp0.j1.holder.b.submitted==submitted && RedPacketRuntime.history().contains("超时"),"late response after timeout does not open or reply");
        flag(RedPacketRuntime.SILENT,false);int launched=Kernel.launched;receive("legacy");check(Kernel.launched==launched+1,"explicit simulation mode retained");
        Field runtimeField=RedPacketRuntime.class.getDeclaredField("instance");runtimeField.setAccessible(true);RedPacketRuntime live=(RedPacketRuntime)runtimeField.get(null);
        n6 legacyQuery=new n6(1,1,"legacy",url("legacy"),0,"v1.0","room@chatroom");
        XC_MethodHook.MethodHookParam constructed=new XC_MethodHook.MethodHookParam();constructed.thisObject=legacyQuery;
        constructed.args=legacyQuery.arguments;XposedBridge.hooks.get(live.api.queryCtor).after(constructed);callback(legacyQuery,0,0);
        android.app.Activity automated=new android.app.Activity();automated.intent.url=url("legacy");RedPacketRuntime.legacyUi(automated);Handler.advance(500);
        check(Kernel.clicked==1,"legacy UI auto-click only for owned receivable packet");
        android.app.Activity manual=new android.app.Activity();manual.intent.url=url("manual");RedPacketRuntime.legacyUi(manual);Handler.advance(1000);check(Kernel.clicked==1,"manual UI never automatically clicked");
        check(RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT),"premium may choose silent");
        flag(RedPacketRuntime.SILENT,false);check(RedPacketRuntime.silentSelected(),"one stored silent mode overrides old flag");
        check(RedPacketRuntime.selectMode(RedPacketPolicy.Mode.FOREGROUND),"premium may switch to foreground");
        flag(RedPacketRuntime.SILENT,true);check(!RedPacketRuntime.silentSelected(),"one stored foreground mode overrides old silent flag");
        check("foreground".equals(RedPacketRuntime.read(RedPacketRuntime.RECEIVE_MODE).trim()),"only a single mode value persisted");
        RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT);receive("old-inflight");Object oldRequest=zp0.j1.holder.b.last;
        RedPacketRuntime.selectMode(RedPacketPolicy.Mode.FOREGROUND);RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT);
        submitted=zp0.j1.holder.b.submitted;callback(oldRequest,0,0);
        check(zp0.j1.holder.b.submitted==submitted,"rapid mode changes cannot revive old in-flight task");
        RedPacketRuntime.received(url("old-waiting"),10,"room@chatroom",System.currentTimeMillis());Handler.advance(0);
        RedPacketRuntime.selectMode(RedPacketPolicy.Mode.FOREGROUND);RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT);Handler.advance(500);
        check(zp0.j1.holder.b.submitted==submitted,"rapid mode changes cancel delayed tasks even after switching back");
        android.util.AtomicFile.fail=true;
        check(!RedPacketRuntime.selectMode(RedPacketPolicy.Mode.FOREGROUND) && RedPacketRuntime.silentSelected(),"failed mode write preserves old single selection");android.util.AtomicFile.fail=false;
        String storedMode=RedPacketRuntime.read(RedPacketRuntime.RECEIVE_MODE);
        for(String edition:new String[]{"pro"}) {
            Kernel.edition=Kernel.authorized=edition;
            check(!RedPacketRuntime.premiumEnabled() && !RedPacketRuntime.silentSelected(),"lower edition forced to foreground: "+edition);
            check(!RedPacketRuntime.selectMode(RedPacketPolicy.Mode.SILENT),"lower edition cannot select silent: "+edition);
            check(storedMode.equals(RedPacketRuntime.read(RedPacketRuntime.RECEIVE_MODE)),"denied selection cannot change saved mode: "+edition);
            String beforeHistory=RedPacketRuntime.history();launched=Kernel.launched;submitted=zp0.j1.holder.b.submitted;
            legacySuccess(live,"lower-"+edition);Handler.advance(1501);
            check(Kernel.launched==launched+1 && zp0.j1.holder.b.submitted==submitted,"lower edition retains only foreground and cannot auto-reply despite stale flags: "+edition);
            check(beforeHistory.equals(RedPacketRuntime.history()),"lower edition cannot write premium receipt history: "+edition);
        }
        Kernel.edition=Kernel.authorized="free";launched=Kernel.launched;submitted=zp0.j1.holder.b.submitted;int clicks=Kernel.clicked;
        receive("free-arrival");Handler.advance(1000);
        check(Kernel.launched==launched && Kernel.clicked==clicks && zp0.j1.holder.b.submitted==submitted,"free arrival never launches, clicks or submits native query");
        android.app.Activity freeManual=new android.app.Activity();freeManual.intent.url=url("free-manual");freeManual.button=false;
        RedPacketRuntime.legacyUi(freeManual);Handler.advance(500);check(Kernel.clicked==clicks,"manual view waits for visible open button");
        freeManual.button=true;Handler.advance(150);check(Kernel.clicked==clicks+1,"free manual page clicks without RP_SEEN ownership");
        Handler.advance(3000);check(Kernel.clicked==clicks+1,"free manual page clicked once");
        android.app.Activity closed=new android.app.Activity();closed.intent.url=url("free-closed");RedPacketRuntime.legacyUi(closed);closed.closing=true;Handler.advance(1000);
        check(Kernel.clicked==clicks+1,"free closed page cancels delayed click");
        android.app.Activity switched=new android.app.Activity();switched.intent.url=url("free-switch");RedPacketRuntime.legacyUi(switched);w41.z1.current="other";Handler.advance(1000);w41.z1.current="self";
        check(Kernel.clicked==clicks+1,"free account switch cancels delayed click");
        config=ReceiptStore.current(ReceiptConfig.Feature.RED_PACKET);config.enabled=false;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        android.app.Activity disabled=new android.app.Activity();disabled.intent.url=url("free-disabled");RedPacketRuntime.legacyUi(disabled);Handler.advance(1000);
        check(Kernel.clicked==clicks+1,"free disabled switch blocks manual auto-click");
        config.enabled=true;ReceiptStore.save(ReceiptConfig.Feature.RED_PACKET,"self",config);
        Kernel.edition=Kernel.authorized="vip";check(RedPacketRuntime.premiumEnabled() && RedPacketRuntime.silentSelected(),"VIP includes advanced silent mode");
        Kernel.edition="vip";Kernel.authorized="plus";
        check(!RedPacketRuntime.premiumEnabled() && !RedPacketRuntime.silentSelected(),"VIP kernel needs matching cumulative authorization");
        Kernel.edition=Kernel.authorized="plus";
        receive("downgrade-query");oldRequest=zp0.j1.holder.b.last;submitted=zp0.j1.holder.b.submitted;Kernel.authorized="pro";callback(oldRequest,0,0);
        check(zp0.j1.holder.b.submitted==submitted,"license downgrade blocks silent open without foreground fallback");Kernel.authorized="plus";
        success("downgrade-reply");submitted=zp0.j1.holder.b.submitted;Kernel.authorized="pro";Handler.advance(1501);
        check(zp0.j1.holder.b.submitted==submitted,"license downgrade blocks queued automatic reply");Kernel.authorized="plus";
        RedPacketRuntime r=new RedPacketRuntime();check(r.reserve("persist-test"),"first reservation committed");
        check(!new RedPacketRuntime().reserve("persist-test"),"reservation recovered across runtime recreation");
        File state=new File(Kernel.WXDIR+"redpacket.processed"),backup=new File(state+".bak");check(state.renameTo(backup),"simulate interrupted atomic write");
        check(!new RedPacketRuntime().reserve("persist-test") && state.exists(),"backup recovered before duplicate check");
        android.util.AtomicFile.fail=true;check(!r.reserve("write-failed"),"failed state commit prevents reservation");android.util.AtomicFile.fail=false;
        RedPacketRuntime.write(state.getPath(),"corrupted-state\n");check(!r.reserve("invalid"),"corrupt reservation state blocks automatic action");
        System.out.println("PASS "+checks+" red packet settings/persistence/reply-flow checks");
    }
}
