package com.zafiro.wxentry.scancam;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 相册选图 -> 虚拟相机帧（wxentry 内核附加模块，不依赖 Kernel 的字段/方法）。
 *
 * 微信「相册」识别和「相机」识别是两条通道，PC 登录码在相册通道会被服务端拒绝。
 * 这里把用户选中的图片转成 NV21 预览帧，在 onPreviewFrame 里覆盖相机数据，
 * 微信就以为自己在用摄像头扫码。
 *
 * 默认开启；WXDIR/scan.cam.off 存在 = 关闭。
 * 附加：WXDIR/scan.cam.fast 存在 = 吞掉相册回调（不回落微信相册识别）。
 */
public final class ScanCameraRuntime {
    static final String TAG = "scancam ";

    static String wxdir() {
        try {
            Class<?> proc = Class.forName("android.os.Process");
            int uid = ((Integer) proc.getMethod("myUid").invoke(null)).intValue();
            int user = uid / 100000;
            if (user != 0) return "/data/user/" + user + "/com.tencent.mm/files/WechatXposed/";
        } catch (Throwable t) {}
        return "/data/data/com.tencent.mm/files/WechatXposed/";
    }
    static final String DIR = wxdir();
    static final String OFF = DIR + "scan.cam.off";
    static final String FAST = DIR + "scan.cam.fast";
    static final String PIN = DIR + "scan.cam.img";
    static final long WINDOW_MS = 120000L;
    static final long PIN_TTL = 240000L;   // 固定图 4 分钟内有效，过期不再干扰正常扫码
    static volatile boolean CARRY = false;   // 选图后跨会话保留一次
    static int GEN = 0;                        // 扫码会话代数（每新建一个扫码界面 +1）
    static int SRC_MAX_GEN = -1;               // 图片允许被使用的最大代数
    static boolean SRC_USED = false;           // 本次图片是否已经真正注入过
    static final java.util.WeakHashMap<Object, Boolean> SEEN = new java.util.WeakHashMap<Object, Boolean>();

    static volatile String SRC = null;
    static volatile long SRC_AT = 0L;
    static final java.util.HashSet<String> DONE = new java.util.HashSet<String>();

    static byte[] NV = null;
    static int NVW = 0, NVH = 0;
    static String NVP = null;
    static long NVT = 0L;
    static volatile boolean LOGGED = false;

    static void L(String s) {
        try { XposedBridge.log("WXT: " + TAG + s); } catch (Throwable t) {}
        try {
            File f = new File(DIR + "trace.log");
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) + "  " + TAG + s + "\n");
            w.close();
        } catch (Throwable t) {}
    }

    public static boolean enabled() {
        try { return !new File(OFF).exists(); } catch (Throwable t) { return true; }
    }
    static boolean fast() {
        try { return new File(FAST).exists(); } catch (Throwable t) { return false; }
    }

    /** 内核 boot0 末尾调用；只装一个 Activity.onResume 钩子。 */
    public static void start() {
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    try { onActivity((Activity) p.thisObject); } catch (Throwable t) {}
                }
            });
            L("ready off=" + new File(OFF).exists());
        } catch (Throwable t) {
            L("start err " + t);
        }
    }

    static void onActivity(Activity act) {
        if (act == null || !enabled()) return;
        Class<?> c = act.getClass();
        String n = c.getName();
        if (n == null || n.indexOf("plugin.scanner") < 0) return;
        if (!isScanUi(c)) return;
        synchronized (SEEN) {
            if (SEEN.put(act, Boolean.TRUE) == null) {
                GEN++;
                if (GEN > SRC_MAX_GEN) clearImage("new session gen=" + GEN);
                L("scan session gen=" + GEN + " allow<=" + SRC_MAX_GEN);
            } else if (SRC_USED) {
                // 同一个扫码界面被再次拉起（微信没有重建实例）也算新的一次，用过的图一律作废
                clearImage("resume after used gen=" + GEN);
            }
        }
        synchronized (DONE) {
            if (DONE.contains(n)) return;
            DONE.add(n);
        }
        hookResult(act, c);
        hookFrames(c.getClassLoader());
        L("hooks ok " + n);
    }

    static boolean isScanUi(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            String n = k.getName();
            if (n == null) continue;
            if (n.equals("com.tencent.mm.plugin.scanner.ui.BaseScanUI")) return true;
            if (n.equals("java.lang.Object")) break;
        }
        String n = c.getName();
        return n != null && n.endsWith("ScanUI");
    }

    static void hookResult(final Activity act, Class<?> c) {
        try {
            XposedHelpers.findAndHookMethod(c, "onActivityResult", int.class, int.class, Intent.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!enabled()) return;
                        int req = ((Integer) p.args[0]).intValue();
                        int res = ((Integer) p.args[1]).intValue();
                        Intent data = (Intent) p.args[2];
                        L("ar req=0x" + Integer.toHexString(req) + " res=" + res + " data=" + (data != null));
                        if (res != Activity.RESULT_OK || data == null) return;
                        if (req != 0x1234 && req != 0x1235) return;
                        String path = resolve(act, data);
                        if (path == null) { L("pick path null"); return; }
                        File f = new File(path);
                        if (!f.exists() || f.length() <= 0) { L("pick missing " + f.getName()); return; }
                        SRC = path; SRC_AT = System.currentTimeMillis();
                        NV = null; NVW = 0; NVH = 0; NVP = null; LOGGED = false;
                        L("pick " + f.getName() + " " + (f.length() / 1024) + "KB");
                        CARRY = true;
                        SRC_USED = false;
                        SRC_MAX_GEN = GEN + 1;
                        persist(path);
                        toast(act, "已选图，正在重新打开扫一扫…");
                        autoReopen(act);
                    } catch (Throwable t) { L("result err " + t); }
                }
            });
            XposedHelpers.findAndHookMethod(c, "onDestroy", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    if (CARRY) {
                        CARRY = false;
                        L("destroy carry: keep image for next scan");
                        return;
                    }
                    if (SRC != null) L("clear destroy");
                    SRC = null; SRC_AT = 0L;
                    NV = null; NVW = 0; NVH = 0; NVP = null;
                    try {
                        File pin = new File(PIN);
                        if (pin.exists()) { pin.delete(); L("pin cleared"); }
                    } catch (Throwable t) {}
                }
            });
        } catch (Throwable t) { L("hookResult err " + t); }
    }

    static void clearImage(String why) {
        if (SRC != null || new File(PIN).exists()) L("image cleared: " + why);
        SRC = null; SRC_AT = 0L;
        NV = null; NVW = 0; NVH = 0; NVP = null;
        SRC_USED = false; SRC_MAX_GEN = -1;
        try { File pin = new File(PIN); if (pin.exists()) pin.delete(); } catch (Throwable t) {}
    }

    static String wechatPath(Activity act, Intent data) {
        String[] names = {"com.tencent.mm.ui.tools.p0", "com.tencent.mm.ui.tools.q0", "com.tencent.mm.ui.tools.r0"};
        for (int i = 0; i < names.length; i++) {
            try {
                Class<?> k = XposedHelpers.findClass(names[i], act.getClassLoader());
                java.lang.reflect.Method m = k.getMethod("b", android.content.Context.class, Intent.class, String.class);
                Object r = m.invoke(null, act, data, "");
                if (r instanceof String) {
                    String s = (String) r;
                    if (s != null && s.length() > 0 && new File(s).exists()) return s;
                }
            } catch (Throwable t) {}
        }
        return null;
    }

    static String resolve(Activity act, Intent data) {
        try {
            String p = fromUri(act, data.getData());
            if (p != null) return p;
        } catch (Throwable t) {}
        try {
            String w = wechatPath(act, data);
            if (w != null) return w;
        } catch (Throwable t) {}
        try {
            android.os.Bundle b = data.getExtras();
            if (b != null) {
                for (String k : b.keySet()) {
                    Object v;
                    try { v = b.get(k); } catch (Throwable t) { continue; }
                    if (v instanceof Uri) {
                        String p = fromUri(act, (Uri) v);
                        if (p != null) return p;
                    } else if (v instanceof String) {
                        String s = (String) v;
                        if (s != null && s.length() > 0 && new File(s).exists()) return s;
                    }
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    static String fromUri(Activity act, Uri u) {
        if (u == null) return null;
        try {
            String sch = u.getScheme();
            if (sch == null || "file".equalsIgnoreCase(sch)) {
                String p = u.getPath();
                if (p != null && new File(p).exists()) return p;
                return null;
            }
            String dest = DIR + "vcam_pick.bin";
            try {
                ContentResolver cr = act.getContentResolver();
                InputStream in = cr.openInputStream(u);
                if (in == null) return null;
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close(); in.close();
                return new File(dest).length() > 0 ? dest : null;
            } catch (Throwable t) {
                L("copy err " + t);
                return null;
            }
        } catch (Throwable t) { return null; }
    }

    static void hookFrames(ClassLoader cl) {
        String[] names = {"com.tencent.mm.plugin.scanner.ui.ScanUIRectView",
                          "com.tencent.scanlib.ui.ScanView",
                          "com.tencent.mm.plugin.scanner.view.ScanTranslationMaskView"};
        for (int i = 0; i < names.length; i++) {
            try {
                Class<?> k = XposedHelpers.findClass(names[i], cl);
                XposedHelpers.findAndHookMethod(k, "onPreviewFrame", byte[].class, android.hardware.Camera.class, FRAME);
                L("frame hook " + names[i]);
            } catch (Throwable t) {}
        }
    }

    static String active() {
        try {
            String s = SRC;
            if (s != null && System.currentTimeMillis() - SRC_AT < WINDOW_MS && new File(s).exists()) return s;
            File pin = new File(PIN);
            if (pin.exists() && pin.length() > 0
                    && System.currentTimeMillis() - pin.lastModified() < PIN_TTL) return pin.getAbsolutePath();
        } catch (Throwable t) {}
        return null;
    }

    /** 把选中的图另存为固定图：微信退出扫码界面后，下次开扫还能直接注入 */
    static void persist(final String path) {
        try {
            new Thread(new Runnable() { public void run() {
                try {
                    File src = new File(path);
                    File dst = new File(PIN);
                    if (src.getAbsolutePath().equals(dst.getAbsolutePath())) {
                        dst.setLastModified(System.currentTimeMillis());
                        return;
                    }
                    java.io.FileInputStream in = new java.io.FileInputStream(src);
                    FileOutputStream out = new FileOutputStream(dst);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.close(); in.close();
                    dst.setLastModified(System.currentTimeMillis());
                    L("pin saved " + (dst.length() / 1024) + "KB");
                } catch (Throwable t) { L("pin err " + t); }
            }}).start();
        } catch (Throwable t) {}
    }

    static final java.util.HashSet<String> FRAME_SEEN = new java.util.HashSet<String>();

    static final XC_MethodHook FRAME = new XC_MethodHook() {
        protected void beforeHookedMethod(MethodHookParam p) {
            try {
                if (!enabled()) return;
                String path = active();
                if (path == null) return;
                // 固定图一旦被真正用上就立刻删文件并转为内存态：绝不让下一次扫码再撞见旧码
                if (SRC == null) {
                    try {
                        File pin = new File(PIN);
                        if (pin.exists() && pin.getAbsolutePath().equals(path)) {
                            SRC = path; SRC_AT = System.currentTimeMillis();
                            if (pin.delete()) L("pin consumed");
                        }
                    } catch (Throwable t) {}
                }
                byte[] data = (byte[]) p.args[0];
                if (data == null || data.length < 1024) return;
                try {
                    String cn = p.thisObject.getClass().getName();
                    synchronized (FRAME_SEEN) {
                        if (!FRAME_SEEN.contains(cn)) {
                            FRAME_SEEN.add(cn);
                            L("frame " + cn + " len=" + data.length);
                        }
                    }
                } catch (Throwable t) {}
                int w = 0, h = 0;
                try {
                    android.hardware.Camera cam = (android.hardware.Camera) p.args[1];
                    android.hardware.Camera.Parameters pm = cam.getParameters();
                    android.hardware.Camera.Size sz = pm.getPreviewSize();
                    w = sz.width; h = sz.height;
                } catch (Throwable t) {}
                if (w <= 0 || h <= 0 || w * h * 3 / 2 != data.length) {
                    int[][] cand = {{1920, 1080}, {1280, 720}, {960, 540}, {1440, 1080}, {1600, 1200}, {640, 480}, {800, 600}};
                    int n = data.length * 2 / 3;
                    w = 0; h = 0;
                    for (int i = 0; i < cand.length; i++) {
                        if (cand[i][0] * cand[i][1] == n) { w = cand[i][0]; h = cand[i][1]; break; }
                    }
                }
                if (w <= 0) return;
                byte[] nv = build(path, w, h);
                if (nv == null || nv.length != data.length) return;
                System.arraycopy(nv, 0, data, 0, nv.length);
                if (!LOGGED) { LOGGED = true; L("inject " + w + "x" + h + " <- " + new File(path).getName()); }
                if (!SRC_USED) {
                    SRC_USED = true;
                    SRC_MAX_GEN = GEN;   // 已经用掉的图，后续任何新会话都不再使用
                    L("image used, lock gen=" + GEN);
                }
            } catch (Throwable t) {}
        }
    };

    static byte[] build(String path, int w, int h) {
        try {
            long now = System.currentTimeMillis();
            if (NV != null && NVW == w && NVH == h && path.equals(NVP) && now - NVT < 30000L) return NV;
            Bitmap src = decode(path, Math.max(w, h));
            if (src == null) return null;
            Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(dst);
            cv.drawColor(0xFFFFFFFF);
            int sw = src.getWidth(), sh = src.getHeight();
            float sc = Math.min((float) w / sw, (float) h / sh);
            int dw = (int) (sw * sc), dh = (int) (sh * sc);
            int left = (w - dw) / 2, top = (h - dh) / 2;
            cv.drawBitmap(src, null, new Rect(left, top, left + dw, top + dh), null);
            int[] argb = new int[w * h];
            dst.getPixels(argb, 0, w, 0, 0, w, h);
            byte[] out = new byte[w * h * 3 / 2];
            for (int i = 0; i < w * h; i++) {
                int px = argb[i];
                int r = (px >> 16) & 0xff, g = (px >> 8) & 0xff, b = px & 0xff;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                out[i] = (byte) (y < 0 ? 0 : (y > 255 ? 255 : y));
            }
            for (int i = w * h; i < out.length; i++) out[i] = (byte) 128;
            try { src.recycle(); } catch (Throwable t) {}
            try { dst.recycle(); } catch (Throwable t) {}
            NV = out; NVW = w; NVH = h; NVP = path; NVT = now;
            L("nv21 " + w + "x" + h + " " + out.length);
            return out;
        } catch (Throwable t) { L("nv21 err " + t); return null; }
    }

    static Bitmap decode(String path, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / sample > max * 2 || o.outHeight / sample > max * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable t) { return null; }
    }

    static void toast(final Activity act, final String s) {
        try {
            act.runOnUiThread(new Runnable() { public void run() {
                try { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); } catch (Throwable t) {}
            }});
        } catch (Throwable t) {}
    }

    /** Keep the existing on-disk setting and return the state actually saved. */
    public static boolean setEnabled(boolean on) {
        File off = new File(OFF);
        try {
            if (on) {
                if (off.exists() && !off.delete()) L("cannot clear scan camera preference");
            } else if (!off.exists()) {
                off.getParentFile().mkdirs();
                if (!off.createNewFile()) L("cannot create scan camera preference");
            }
        } catch (Exception failure) {
            L("menu preference error " + failure.getClass().getSimpleName());
        }
        return enabled();
    }

    // ---------------- 选完图自动重开扫一扫 ----------------

    static void autoReopen(final Activity act) {
        try {
            final Intent next = new Intent(act.getIntent());
            next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            next.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            final android.content.Context app = act.getApplicationContext();
            // 不能挂在 decorView 上：微信可能在相册返回后立刻 finish，View 一 detach，postDelayed 就永远不执行
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(new Runnable() { public void run() {
                try {
                    if (!act.isFinishing() && !act.isDestroyed()) act.finish();
                    L("auto reopen: finished");
                } catch (Throwable t) { L("auto reopen finish err " + t); }
                h.postDelayed(new Runnable() { public void run() {
                    try {
                        app.startActivity(next);
                        L("auto reopen launched");
                    } catch (Throwable t) { L("auto reopen launch err " + t); }
                }}, 600);
            }}, 700);
        } catch (Throwable t) { L("auto reopen err " + t); }
    }
}
