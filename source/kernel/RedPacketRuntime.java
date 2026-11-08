package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Random;

final class RedPacketRuntime implements RedPacketEngine.Host,RedPacketNative.Listener {
    static final String SILENT=Kernel.WXDIR+"redpacket.silent.on";
    static final String RECEIVE_MODE=Kernel.WXDIR+"redpacket.receive.mode";
    static final String REPLY=Kernel.WXDIR+"redpacket.reply.on";
    static final String RANDOM=Kernel.WXDIR+"redpacket.reply.random.on";
    static final String TEXT=Kernel.WXDIR+"redpacket.reply.text";
    static final String DEFAULT_TEXT="谢谢红包！\n感谢，祝你天天开心！\n谢谢，祝你好运连连！";
    private static volatile RedPacketRuntime instance;
    private static boolean attempted;
    private static String unavailable="后台领取尚未初始化";
    final Handler handler=new Handler(Looper.getMainLooper());
    final RedPacketEngine engine=new RedPacketEngine(this);
    final Random random=new Random();
    final java.util.Set<RedPacketEngine.Attempt> replying=new java.util.HashSet<RedPacketEngine.Attempt>();
    RedPacketNative api;

    static synchronized void start(ClassLoader cl) {
        if(attempted)return;attempted=true;
        try {
            RedPacketRuntime runtime=new RedPacketRuntime();
            runtime.api=new RedPacketNative(cl,runtime);
            instance=runtime;unavailable="";
            Kernel.T("rp: native callback adapter ready");
        } catch(Throwable failure) {
            unavailable="当前微信接口不匹配，静默领取未启用";
            Kernel.T("rp: native adapter unavailable "+failure.getClass().getSimpleName());
        }
    }
    static boolean ready() { return instance!=null; }
    static String unavailable() { return unavailable; }
    static boolean flag(String path) { return new File(path).exists(); }
    static String text() { String s=read(TEXT);return s==null?DEFAULT_TEXT:s; }
    static boolean premiumEnabled() { return Kernel.redPacketPremiumEnabled(); }
    static RedPacketPolicy.Mode mode() {
        return RedPacketPolicy.effective(RedPacketPolicy.configured(read(RECEIVE_MODE),flag(SILENT)),premiumEnabled());
    }
    static boolean silentSelected() { return mode()==RedPacketPolicy.Mode.SILENT; }
    static boolean selectMode(RedPacketPolicy.Mode selected) {
        if(selected==null || (selected==RedPacketPolicy.Mode.SILENT && (!premiumEnabled() || !ready())))return false;
        if(!write(RECEIVE_MODE,RedPacketPolicy.value(selected)+"\n"))return false;
        // One atomic value controls the choice; the old flag is only read before migration.
        RedPacketRuntime runtime=instance;
        if(runtime!=null)synchronized(runtime.engine) {
            for(RedPacketEngine.Attempt a:new java.util.ArrayList<RedPacketEngine.Attempt>(runtime.engine.pending.values()))
                if(a.silent!=(selected==RedPacketPolicy.Mode.SILENT))runtime.engine.finish(a,"领取方式已切换，停止旧任务",null);
        }
        return true;
    }
    boolean modeValid(RedPacketEngine.Attempt a) {
        if(a.silent==silentSelected())return true;
        engine.finish(a,"领取方式或权限已变更，停止旧任务",null);return false;
    }

    static void received(final String url,final long msgId,final String talker,final Long created) {
        final RedPacketRuntime runtime=instance;
        if(runtime==null) {
            // Preserve the existing simulation when the native adapter is unsupported.
            if(!silentSelected() && !Kernel.MODULE_OFF && ReceiptStore.permits(ReceiptConfig.Feature.RED_PACKET,Kernel.receiptAccount(),talker))Kernel.openReceiveUI(url,msgId,talker);
            else Kernel.T("rp: silent request skipped; native adapter unavailable");
            return;
        }
        runtime.handler.post(new Runnable() { public void run() {
            try {
                if(created!=null && created.longValue()>0) {
                    long time=created.longValue();if(time<100000000000L)time*=1000;
                    long age=System.currentTimeMillis()-time;
                    if(age>300000 || age < -60000)return; // Do not claim historical sync/replay traffic.
                }
                RedPacketEngine.Packet packet=RedPacketEngine.parse(runtime.api.account(),talker,url,msgId);
                if(packet==null) { Kernel.T("rp: unsupported red packet URL");return; }
                ReceiptConfig rules=ReceiptStore.load(ReceiptConfig.Feature.RED_PACKET,packet.account);
                if(!rules.enabled||!rules.permits(packet.talker)) {
                    ReceiptConfig.Scope scope=ReceiptConfig.Scope.of(packet.talker);
                    Kernel.T("rp: policy skip scope="+scope+" rule="+rules.scope(scope).rule+" enabled="+rules.enabled);return;
                }
                runtime.engine.accept(packet,silentSelected());
            } catch(Throwable failure) { runtime.error(failure); }
        } });
    }
    static boolean legacyClickAllowed(String url) {
        RedPacketRuntime runtime=instance;
        if(silentSelected())return false;
        if(runtime==null)return false;
        synchronized(runtime.engine) {
            for(RedPacketEngine.Attempt a:new java.util.ArrayList<RedPacketEngine.Attempt>(runtime.engine.pending.values()))
                if(!a.silent && a.packet.url.equals(url) && a.stage==RedPacketEngine.Stage.OPEN && runtime.engine.live(a))return true;
        }
        return false;
    }
    static void legacyUi(final Activity act) {
        final String url=act.getIntent().getStringExtra("key_native_url");
        if(url==null){Kernel.T("rp ui skip missing native URL page="+act.getClass().getSimpleName());return;}
        final boolean manual=Kernel.freeRedPacketForegroundOnly();
        final boolean owned;synchronized(Kernel.RP_SEEN) { owned=Kernel.RP_SEEN.contains(url); }
        if(!manual&&!owned)return;
        final String account=Kernel.receiptAccount(),talker=act.getIntent().getStringExtra("key_username");
        if(manual && (!ReceiptConfig.validId(account) || !ReceiptStore.permits(ReceiptConfig.Feature.RED_PACKET,account,talker)))return;
        Kernel.T("rp ui scheduled manual="+manual+" page="+act.getClass().getSimpleName());
        final java.lang.ref.WeakReference<Activity> activity=new java.lang.ref.WeakReference<Activity>(act);
        final Handler main=new Handler(Looper.getMainLooper());
        final long delay=manual&&!owned?ReceiptStore.delay(ReceiptConfig.Feature.RED_PACKET,account):0;
        final long deadline=SystemClock.elapsedRealtime()+delay+12000;
        main.postDelayed(new Runnable() { public void run() {
            Activity current=activity.get();
            if(current==null || current.isFinishing() || current.isDestroyed() || Kernel.MODULE_OFF || silentSelected())return;
            if(manual && (!account.equals(Kernel.receiptAccount()) || !ReceiptStore.permits(ReceiptConfig.Feature.RED_PACKET,account,talker)))return;
            if((manual || legacyClickAllowed(url)) && Kernel.tryClickKai(current))return;
            if(SystemClock.elapsedRealtime()<deadline)main.postDelayed(this,150);
        } },delay);
    }
    public long now() { return SystemClock.elapsedRealtime(); }
    public boolean allowed(RedPacketEngine.Packet p) {
        try {
            return !Kernel.MODULE_OFF && p.account.equals(api.account())
                && ReceiptStore.permits(ReceiptConfig.Feature.RED_PACKET,p.account,p.talker);
        } catch(Throwable failure) { return false; }
    }
    public boolean reserve(String key) { return reserveKey(key); }
    static synchronized boolean reserveKey(String key) {
        String digest=hash(key),path=Kernel.WXDIR+"redpacket.processed";
        LinkedHashSet<String> saved=new LinkedHashSet<String>();
        String old=read(path);
        if(old!=null)for(String line:old.split("\n")) {
            if(line.trim().isEmpty())continue;
            if(!line.matches("[0-9a-f]{64}")) { Kernel.T("rp: reservation state invalid; no request submitted");return false; }
            saved.add(line);
        }
        if(saved.contains(digest))return false;
        saved.add(digest);
        while(saved.size()>2048)saved.remove(saved.iterator().next());
        StringBuilder content=new StringBuilder();for(String id:saved)content.append(id).append('\n');
        return write(path,content.toString());
    }
    static String hash(String key) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return out.toString();
        } catch(Throwable failure) { throw new IllegalStateException(failure); }
    }
    static synchronized boolean write(String path,String value) {
        AtomicFile file=new AtomicFile(new File(path));FileOutputStream stream=null;
        try {
            new File(path).getParentFile().mkdirs();
            stream=file.startWrite();stream.write(value.getBytes("UTF-8"));file.finishWrite(stream);return true;
        } catch(Throwable failure) {
            if(stream!=null)file.failWrite(stream);
            Kernel.T("rp: settings/record write failed");return false;
        }
    }
    static synchronized String read(String path) {
        try {
            java.io.InputStream stream=new AtomicFile(new File(path)).openRead();
            try {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096];int count;
                while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
                return bytes.toString("UTF-8");
            } finally { stream.close(); }
        } catch(java.io.FileNotFoundException absent) { return null; }
        catch(Throwable failure) { throw new IllegalStateException("Cannot read red packet state",failure); }
    }
    public long delay() { return ReceiptStore.delay(ReceiptConfig.Feature.RED_PACKET,Kernel.receiptAccount()); }
    public void later(long ms,Runnable r) { handler.postDelayed(r,ms); }
    public void query(RedPacketEngine.Attempt a) throws Throwable {
        if(!premiumEnabled() || !silentSelected())throw new IllegalStateException("Silent mode or entitlement changed");api.query(a);
    }
    public void open(RedPacketEngine.Attempt a,String timing) throws Throwable {
        if(!premiumEnabled() || !silentSelected())throw new IllegalStateException("Silent mode or entitlement changed");api.open(a,timing);
    }
    public void legacy(RedPacketEngine.Attempt a) {
        if(silentSelected()) { engine.finish(a,"领取模式已变更",null);return; }
        Kernel.openReceiveUI(a.packet.url,a.packet.msgId,a.packet.talker);
    }
    public RedPacketEngine.Attempt legacy(String url,String sendId) { return engine.owned(url,sendId); }
    public void query(final RedPacketEngine.Attempt a,final RedPacketEngine.Query q) {
        Kernel.T("rp: query result scope="+ReceiptConfig.Scope.of(a.packet.talker)+" error="+(q==null?"missing":q.error)+" hb="+(q==null?-1:q.hbStatus)+" receive="+(q==null?-1:q.receiveStatus)+" timing="+(q!=null&&!RedPacketEngine.empty(q.timing))+" needsAction="+(q!=null&&q.needsAction));
        handler.post(new Runnable() { public void run() { if(modeValid(a))engine.queried(a,q); } });
    }
    public void opened(final RedPacketEngine.Attempt a,final RedPacketEngine.Result r) {
        Kernel.T("rp: open result scope="+ReceiptConfig.Scope.of(a.packet.talker)+" network="+(r==null?"missing":r.networkType+"/"+r.networkCode)+" detail="+(r!=null&&r.detailPresent)+" receive="+(r==null?-1:r.receiveStatus)+" positiveAmount="+(r!=null&&r.cents>0)+" request="+(r!=null&&r.requestVerified)+" responseId="+(r==null?"missing":r.responseIdState)+" requestFallback="+(r!=null&&r.idFromRequest)+" verdict="+RedPacketEngine.openFailure(a.packet,r));
        handler.post(new Runnable() { public void run() { if(modeValid(a))engine.opened(a,r); } });
    }
    public void replied(final RedPacketEngine.Attempt a,final boolean success) {
        handler.post(new Runnable() { public void run() {
            if(replying.remove(a))record(a.packet.account,success?"自动回复发送成功":"自动回复发送失败，未重试");
        } });
    }
    public void error(Throwable failure) { Kernel.T("rp: adapter error "+failure.getClass().getSimpleName()); }
    public void finished(final RedPacketEngine.Attempt a,String status,RedPacketEngine.Result success) {
        api.forget(a);
        if(!premiumEnabled()) {
            if(success!=null && !flag(Kernel.RP_TOAST_OFF_PATH))Kernel.toastMain("抢到红包！");
            return;
        }
        if(success==null) { Kernel.T("rp: finished scope="+ReceiptConfig.Scope.of(a.packet.talker)+" status="+status);record(a.packet.account,status+"（"+(a.packet.talker.endsWith("@chatroom")?"群聊":"单聊")+"）");return; }
        String name=success.sender;
        if(RedPacketEngine.empty(name) && !RedPacketEngine.empty(success.senderId))name=Kernel.displayNameOf(success.senderId);
        if(RedPacketEngine.empty(name))name="红包发送者";
        String message="已领取「"+clean(name)+"」的红包，"+RedPacketEngine.amount(success.cents)+" 元";
        record(a.packet.account,message+"（"+(a.packet.talker.endsWith("@chatroom")?"群聊":"单聊")+"）");
        if(!flag(Kernel.RP_TOAST_OFF_PATH))Kernel.toastMain(message);
        if(!flag(REPLY))return;
        later(800+random.nextInt(701),new Runnable() { public void run() {
            if(!premiumEnabled() || !allowed(a.packet) || !flag(REPLY) || a.silent!=silentSelected())return;
            String reply=RedPacketEngine.reply(text(),flag(RANDOM),random);
            if(reply==null || !reserve("reply|"+a.packet.key()))return;
            try {
                replying.add(a);api.reply(a,reply);
                record(a.packet.account,"自动回复已提交到原会话");
                later(RedPacketEngine.TIMEOUT_MS,new Runnable() { public void run() {
                    if(replying.remove(a)) { api.forget(a);record(a.packet.account,"自动回复结果未知，未重试"); }
                } });
            } catch(Throwable failure) { replying.remove(a);api.forget(a);record(a.packet.account,"自动回复提交失败，未重试"); }
        } });
    }
    static String historyPath(String account) { return Kernel.WXDIR+"redpacket.history."+hash(account).substring(0,16); }
    static String clean(String text) {
        text=text.replace('\r',' ').replace('\n',' ').replace('\t',' ');
        return text.length()>100?text.substring(0,100):text;
    }
    static synchronized void record(String account,String message) {
        String path=historyPath(account),old=read(path);
        java.util.List<String> lines=new java.util.ArrayList<String>();
        if(old!=null)for(String line:old.split("\n"))if(!line.trim().isEmpty())lines.add(line);
        lines.add(new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.CHINA).format(new java.util.Date())+"  "+message);
        while(lines.size()>200)lines.remove(0);
        StringBuilder text=new StringBuilder();for(String line:lines)text.append(line).append('\n');
        write(path,text.toString());
    }
    static String history() {
        try {
            String account=instance==null?"":instance.api.account();
            if(RedPacketEngine.empty(account))return "当前账号尚未就绪";
            String text=read(historyPath(account));
            return RedPacketEngine.empty(text)?"暂无领取记录":text;
        } catch(Throwable failure) { return "暂时无法读取领取记录"; }
    }
}
