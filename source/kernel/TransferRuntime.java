package com.zafiro.wxentry.kernel;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class TransferRuntime implements TransferEngine.Host,TransferNative.Listener {
    static volatile TransferRuntime instance;static boolean attempted;
    static String unavailable="自动接收转账尚未初始化";
    final Handler handler=new Handler(Looper.getMainLooper());
    final TransferEngine engine=new TransferEngine(this);
    TransferNative api;
    static synchronized void start(ClassLoader cl) {
        if(attempted)return;attempted=true;
        try {
            TransferRuntime runtime=new TransferRuntime();runtime.api=new TransferNative(cl,runtime);instance=runtime;unavailable="";
            Kernel.T("transfer: native adapter ready");
        } catch(Throwable failure) { unavailable="当前微信接口不匹配，自动接收转账未启用";Kernel.T("transfer: adapter unavailable "+failure.getClass().getSimpleName()); }
    }
    static boolean ready() { return instance!=null; }
    static String unavailable() { return unavailable; }
    static void received(final String content,final String talker,final Long created) {
        final TransferRuntime runtime=instance;
        if(runtime==null || !Kernel.redPacketPremiumEnabled() || content==null || !content.contains("wcpayinfo"))return;
        runtime.handler.post(new Runnable() { public void run() {
            try {
                if(!ReceiptStore.enabled(ReceiptConfig.Feature.TRANSFER))return;
                if(created!=null && created.longValue()>0) {
                    long time=created.longValue();if(time<100000000000L)time*=1000;
                    long age=System.currentTimeMillis()-time;if(age>300000 || age < -60000)return;
                }
                TransferEngine.Packet p=TransferEngine.parse(runtime.api.account(),talker,content,false);
                if(p!=null)runtime.engine.accept(p);
            } catch(Throwable failure) { runtime.error(failure); }
        } });
    }
    public long now() { return SystemClock.elapsedRealtime(); }
    public boolean allowed(TransferEngine.Packet p) {
        try { return Kernel.redPacketPremiumEnabled() && p.account.equals(api.account()) && ReceiptStore.permits(ReceiptConfig.Feature.TRANSFER,p.account,p.talker); }
        catch(Throwable failure) { return false; }
    }
    public boolean reserve(String key) { return RedPacketRuntime.reserveKey(key); }
    public long delay(TransferEngine.Packet p) { return ReceiptStore.delay(ReceiptConfig.Feature.TRANSFER,p.account); }
    public void later(long ms,Runnable r) { handler.postDelayed(r,ms); }
    public void query(TransferEngine.Attempt a,boolean verify) throws Throwable { api.query(a,verify); }
    public void confirm(TransferEngine.Attempt a,TransferEngine.Query q) throws Throwable { api.confirm(a,q); }
    public void queried(final TransferEngine.Attempt a,final TransferEngine.Query q) { handler.post(new Runnable() { public void run() { engine.queried(a,q); } }); }
    public void confirmed(final TransferEngine.Attempt a,final int error,final boolean action) { handler.post(new Runnable() { public void run() { engine.confirmed(a,error,action); } }); }
    public void finished(TransferEngine.Attempt a,String status,boolean success) {
        api.forget(a);
        if(success) {
            String name=Kernel.displayNameOf(a.packet.sender);if(RedPacketEngine.empty(name))name=a.packet.sender;
            String message="已接收「"+RedPacketRuntime.clean(name)+"」的转账，"+RedPacketEngine.amount(a.packet.cents)+" 元";
            RedPacketRuntime.record(a.packet.account,"转账："+message);
            if(!RedPacketRuntime.flag(Kernel.RP_TOAST_OFF_PATH))Kernel.toastMain(message);
        } else RedPacketRuntime.record(a.packet.account,"转账："+status);
    }
    public void error(Throwable failure) { Kernel.T("transfer: adapter error "+failure.getClass().getSimpleName()); }
}
