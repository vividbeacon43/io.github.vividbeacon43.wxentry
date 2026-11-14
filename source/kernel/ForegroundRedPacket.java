package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.ref.WeakReference;

final class ForegroundRedPacket {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ForegroundRedPacket() {}

    static void received(String url, long messageId, String talker, Long created, String content) {
        if (created != null && created > 0) {
            long sentAt = created < 100000000000L ? created * 1000 : created;
            long age = System.currentTimeMillis() - sentAt;
            if (age > 300000 || age < -60000) return;
        }
        String account = Kernel.receiptAccount();
        if (!allowed(account, talker)) return;
        MAIN.postDelayed(() -> {
            if (allowed(account, talker)) Kernel.openReceiveUI(url, messageId, talker, content);
        }, ReceiptStore.delay(ReceiptConfig.Feature.RED_PACKET, account));
    }

    private static boolean allowed(String account, String talker) {
        return !Kernel.MODULE_OFF && ReceiptConfig.validId(account)
                && account.equals(Kernel.receiptAccount())
                && ReceiptStore.permits(ReceiptConfig.Feature.RED_PACKET, account, talker);
    }

    static void onReceivePage(Activity activity) {
        String url = activity.getIntent().getStringExtra("key_native_url");
        if (url == null) return;
        String account = Kernel.receiptAccount();
        String talker = activity.getIntent().getStringExtra("key_username");
        if (!allowed(account, talker)) return;
        boolean openedByModule;
        synchronized (Kernel.RP_SEEN) {
            openedByModule = Kernel.RP_SEEN.contains(url);
        }
        long delay = openedByModule ? 0 : ReceiptStore.delay(ReceiptConfig.Feature.RED_PACKET, account);
        long deadline = SystemClock.elapsedRealtime() + delay + 12000;
        WeakReference<Activity> page = new WeakReference<>(activity);
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                Activity current = page.get();
                if (current == null || current.isFinishing() || current.isDestroyed() || !allowed(account, talker)) return;
                if (Kernel.tryClickKai(current)) return;
                if (SystemClock.elapsedRealtime() < deadline) MAIN.postDelayed(this, 150);
                else Kernel.T("rp ui timeout page=" + current.getClass().getSimpleName()
                        + " state=" + Kernel.redPacketUiState(current));
            }
        }, delay);
    }

    static void showMode(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("免费版红包领取")
                .setMessage("收到符合名单设置的红包后，自动拉起微信领取页面并点击拆包。")
                .setPositiveButton("知道了", null).show();
    }
}
