package com.zafiro.wxentry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * 引导器：设备指纹 + 激活（服务器验证）+ 内核下发/校验/加载
 * 真正的功能代码在服务器下发的 kernel.dex 里（com.zafiro.wxentry.kernel.Kernel）
 */
public class Main implements IXposedHookLoadPackage {

    /** v1.42：分身（MultiApp / user 999）下数据目录不是 /data/data，要按进程 uid 推 */
    static String computeDir() {
        try {
            int uid = ((Integer) Class.forName("android.os.Process").getMethod("myUid").invoke(null)).intValue();
            int user = uid / 100000;
            if (user != 0) return "/data/user/" + user + "/com.tencent.mm/files/WechatXposed/";
        } catch (Throwable t) {}
        return "/data/data/com.tencent.mm/files/WechatXposed/";
    }
    static String computeDataDir() {
        String d = computeDir();                      // …/com.tencent.mm/files/WechatXposed/
        int i = d.indexOf("/files/");
        return i > 0 ? d.substring(0, i) : "/data/data/com.tencent.mm";
    }
    static final String DIR = computeDir();
    static final String LIC = DIR + "license.dat";
    static final String KERNEL_ENC = DIR + "kernel.enc";
    static final String KERNEL = DIR + "kernel.dex";   // 仅旧版兼容
    static final String KERNEL_SHA = DIR + "kernel.sha";
    static final String LAST_CHECK = DIR + "manual_check";
    static final String PENDING = DIR + "update.pending";
    static final long DAY_MS = 86400000L;
    static final String KERNEL_OPT = computeDataDir() + "/code_cache/wxentry_opt";

    // === 服务器 ===
    static final String BASE = "https://walideng.fun/wx/";
    static final String URL_ACTIVATE = BASE + "api.php?a=activate";
    static final String URL_WISH = BASE + "wish";
    static final String URL_MANIFEST = BASE + "kernel/manifest";
    static final String URL_KERNEL   = BASE + "kernel/download";
    // === 1.43：模块 APK 自身更新 + 内核自动更新/回滚 ===
    static final int MY_VC = 91;                       // 由 release.py 打包时改写
    static final String MY_VN = "1.91";
    static final String URL_APK_MANIFEST = BASE + "apk.json";
    static final String URL_APK = BASE + "wxentry.apk";
    static final String APK_PENDING = DIR + "apk.pending";
    static final String APK_CHECKED = DIR + "apk.checked";
    static final String KERNEL_BAD = DIR + "kernel.bad";


    static volatile String RUNNING_EDITION = "free";
    static volatile Class<?> RUNNING_KERNEL_CLASS;
    static final KernelDelivery DELIVERY = new KernelDelivery(new File(DIR), new KernelDelivery.Transport() {
        public KernelDelivery.Manifest manifest(String token) throws Exception {
            String response = new String(channelPost(URL_MANIFEST, "{\"token\":\"" + jesc(token) + "\"}"), "UTF-8");
            if (!"true".equals(jsonStr(response, "ok"))) throw new Exception("Invalid channel response");
            return new KernelDelivery.Manifest(jsonStr(response, "edition"), jsonStr(response, "v"),
                    jsonStr(response, "sha256"), jsonStr(response, "sig"), jsonStr(response, "grant_sig"));
        }
        public byte[] download(String token, KernelDelivery.Manifest m) throws Exception {
            return channelPost(URL_KERNEL, "{\"token\":\"" + jesc(token) + "\",\"v\":\""
                    + m.version + "\",\"sha256\":\"" + m.sha + "\"}");
        }
        public byte[] decrypt(byte[] encrypted) throws Exception { return Main.decrypt(encrypted); }
        public boolean verify(String data, String signature) { return verifySig(data, signature); }
        public boolean isCurrent(String token) { return token != null && token.equals(licField("token")); }
        public boolean blocked(String sha) {
            String bad = readText(KERNEL_BAD);
            if (bad == null) return false;
            String[] p = bad.trim().split("\\|");
            try { return p.length == 2 && sha.equals(p[0]) && System.currentTimeMillis() - Long.parseLong(p[1]) < 7 * DAY_MS; }
            catch (Throwable invalid) { return false; }
        }
    });
    static byte[] channelPost(String url, String body) throws Exception {
        try { return httpPost(url, body); }
        catch (Exception failure) {
            String message = failure.getMessage();
            if (message != null && (message.startsWith("HTTP 401") || message.startsWith("HTTP 403")))
                throw new KernelDelivery.AuthorizationDenied();
            throw failure;
        }
    }
    public static String currentKernelEdition() { return DELIVERY.edition(licField("token")); }
    public static String editionName(String edition) { return EditionPolicy.name(edition); }
    public static String imageFeatureEnabledFor(String runningEdition) {
        String authorized = currentKernelEdition();
        return EditionPolicy.includesImages(runningEdition) && EditionPolicy.rank(authorized) >= EditionPolicy.rank(runningEdition) ? "1" : "0";
    }
    public static void changeActivation(Activity act) { showActivate(act, readWxid(act)); }
    static KernelDelivery.Result refreshKernel() throws Exception { return DELIVERY.refresh(licField("token")); }
    static void refreshEditionLater() {
        new Thread(new Runnable() { public void run() {
            try { refreshKernel(); } catch (Throwable t) { log("edition refresh " + t.getClass().getSimpleName()); }
        }}, "wxentry-edition-update").start();
    }
    public static String runningKernelVersion() {
        try { java.lang.reflect.Field field=RUNNING_KERNEL_CLASS.getDeclaredField("BUILD");field.setAccessible(true);return String.valueOf(field.get(null)); }
        catch(Throwable missing) { return "?"; }
    }
    static String kernelUpdateMessage(KernelDelivery.Result result) {
        String name = EditionPolicy.name(result.manifest.edition);
        if (!result.manifest.edition.equals(RUNNING_EDITION)) return "已自动匹配" + name + "，重启微信后生效";
        if(result.changed||!result.manifest.version.equals(runningKernelVersion()))return "已下载"+name+" v"+result.manifest.version+"，重启微信后生效";
        return "当前已是"+name+"最新版本";
    }
    static void rememberRunningKernel(Class<?> kernel) {
        RUNNING_KERNEL_CLASS = kernel;
        try { RUNNING_EDITION = String.valueOf(kernel.getField("EDITION").get(null)); }
        catch (Throwable missing) { RUNNING_EDITION = "free"; }
    }
    static void showEditionRestart(final Activity act, String message) {
        new AlertDialog.Builder(act).setTitle("版本已匹配").setMessage(message)
                .setPositiveButton("重启微信", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        try {
                            java.lang.reflect.Method method = RUNNING_KERNEL_CLASS.getDeclaredMethod("restartWeChat", Activity.class);
                            method.setAccessible(true); method.invoke(null, act);
                        } catch (Throwable t) { Toast.makeText(act, "请重新打开微信使新版本生效", Toast.LENGTH_LONG).show(); }
                    }
                }).setNegativeButton("稍后", null).show();
    }

    // === 公钥（RSA-2048, X.509/SPKI, base64）===
    // 内核 AES-256-CBC 密钥（与构建工具 EncKernel 一致）
    static final byte[] K_KEY = {77, 21, -25, 10, -93, -49, 88, -32, 48, 104, 3, 91, 53, 31, 70, 89, -13, 123, -128, -63, -99, -111, -22, 100, -104, 80, 34, 113, -128, -34, -100, -95};
    static final byte[] K_IV = {32, -43, -36, -85, -34, 86, -110, 84, 95, 22, -65, -67, 30, -33, 66, -116};
    static final String PUB_B64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuhwOuMMBhWEsQ2TeN7C35k8xzPajYhZlYi1q+8E106/SXnsV++4ALWZPUTA9rWuG2Bsl6vm68/clAeXYM91+kwYhUssdO6WA/t/fXYUXnFno7/GR84+jXCc8Clqppf8ubJOyjbAjyt/CsD39rxwI0u+dcz+g9/Y5T/f22eq2EQWpVWBi8WpH2H3IkdM8bsGsh2u67jZcpqppF9HuiQAqhtWiJ6zm4ritUJ7DX+C9Su5YV5+cZnAsRXkeknRrf0Vv4lUJPEQLq2GV04ENyxTK3xl9Rbj40uuFfcc3x22xwpbl9l82tgk5puT5I9P86r2HixLgmge6WxEqXrf72kebAQIDAQAB";

    static volatile ClassLoader WX_CL = null;
    static volatile boolean DEX_PATCHED = false;
    /** 1.44：这次启动真正跑的是不是服务器下发的内核（false 表示退回了内嵌版） */
    static volatile boolean KERNEL_DYNAMIC_OK = false;
    static volatile String PROC = "";
    // A startup attempt is not a successful kernel/menu handoff.
    static boolean booted = false;
    static volatile String KERNEL_BOOT_STATE = "not-started";
    static volatile java.lang.ref.WeakReference<Activity> RECOVERY_ACTIVITY;
    static int RECOVERY_GENERATION;

    static String xposedBridgeName() { return XposedBridge.class.getName(); }
    static byte[] runtimeDex(byte[] verified) {
        if (verified == null) return null;
        try {
            byte[] adapted = FrameworkDexCompat.adapt(verified, xposedBridgeName());
            if (adapted != verified) log("runtime dex adapted to active framework API");
            return adapted;
        } catch (Exception incompatible) {
            log("framework dex compatibility unavailable: " + incompatible);
            return verified;
        }
    }
    static boolean kernelMenuReady() {
        return "ready".equals(MenuEntryDispatch.readiness(RUNNING_KERNEL_CLASS));
    }
    static void openRecoveryEntry(final Activity act) {
        log("entry clicked state=" + KERNEL_BOOT_STATE);
        String wxid = WX_ID_LAST;
        Class<?> authorized = wxid != null && licenseOk(act, wxid) ? RUNNING_KERNEL_CLASS : null;
        MenuEntryDispatch.open(authorized, Activity.class, act, new Runnable() {
            public void run() { showRecoveryStatus(act); }
        });
    }
    static void showRecoveryStatus(final Activity act) {
        if (act.isFinishing() || act.isDestroyed()) return;
        // Display local status immediately; a network request must not hide the entry.
        String state = "ready".equals(KERNEL_BOOT_STATE) ? "菜单暂时无法打开" : "内核尚未就绪";
        new AlertDialog.Builder(act).setTitle("WXEntry 状态")
            .setMessage(state + "\n可检查更新，或重新打开微信。")
            .setPositiveButton("关闭", null)
            .setNegativeButton("激活/更换激活码", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { changeActivation(act); }
            })
            .setNeutralButton("检查内核更新", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    new Thread(new Runnable() { public void run() {
                        final String result = checkUpdateNow();
                        act.runOnUiThread(new Runnable() { public void run() {
                            if (!act.isFinishing() && !act.isDestroyed()) Toast.makeText(act, result, Toast.LENGTH_LONG).show();
                        }});
                    }}, "wxentry-entry-update").start();
                }
            }).show();
        log("entry status shown");
    }
    static void installRecoveryLifecycle() {
        try {
            XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    Activity act = (Activity)p.thisObject;
                    if (act.getClass().getName().endsWith(".LauncherUI")) onWeChatReady(act);
                }
            });
            log("entry recovery lifecycle installed");
        } catch (Throwable t) { log("entry recovery lifecycle failed: " + t); }
    }
    static void ensureRecoveryEntry(final Activity act) {
        if (!act.getClass().getName().endsWith(".LauncherUI")) return;
        act.runOnUiThread(new Runnable() { public void run() {
            RECOVERY_ACTIVITY = new java.lang.ref.WeakReference<Activity>(act);
            final int generation = ++RECOVERY_GENERATION;
            final View decor = act.getWindow().getDecorView();
            decor.post(new Runnable() {
                int attempts;
                public void run() {
                    if (generation != RECOVERY_GENERATION || act.isFinishing() || act.isDestroyed()) return;
                    if (attachOnce(act)) return;
                    if (++attempts < 15) decor.postDelayed(this, 400);
                    else log("entry recovery button not found after retries");
                }
            });
        }});
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!"com.tencent.mm".equals(lp.packageName)) return;
        WX_CL = lp.classLoader;
        PROC = "";
        try { PROC = String.valueOf(XposedHelpers.getObjectField(lp, "processName")); } catch (Throwable t) {}
        if (PROC.length() > 0 && !"com.tencent.mm".equals(PROC)) return;
        installRecoveryLifecycle();
        log("handleLoadPackage pkg=" + lp.packageName + " proc=" + PROC);
        try { cleanApkPending(); } catch (Throwable t) {}
        try {
            if (BOOTLOG.exists() && BOOTLOG.length() > 0) {
                if (BOOTPREV.exists()) BOOTPREV.delete();
                BOOTLOG.renameTo(BOOTPREV);
            }
        } catch (Throwable t) {}
        PREV_FAIL = prevBootFail();
        bootMark("lpp" + (PREV_FAIL == null ? "" : " (prevFail=" + PREV_FAIL + ")"));

        try {
            Class<?> launcher = XposedHelpers.findClass("com.tencent.mm.ui.LauncherUI", WX_CL);
            XposedHelpers.findAndHookMethod(launcher, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    final Activity act = (Activity) p.thisObject;
                    log("LauncherUI.onCreate fired");
                    if (new File(DIR + "watchdog.test").exists()) { log("onCreate skipped (watchdog.test)"); return; }
                    try { onWeChatReady(act); } catch (Throwable t) { log("ready err " + t); }
                }
            });
            log("launcher hook installed");
            // Tinker/热补丁下 LauncherUI 可能来自另一个 ClassLoader，直接找类会漏；这里挂系统 Activity 兜底
            try {
                XposedBridge.hookAllMethods(Activity.class, "onCreate", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        try {
                            Object o = p.thisObject;
                            if (o == null) return;
                            String cn = o.getClass().getName();
                            // 1.32：不再只认 LauncherUI —— 微信基本是单 Activity，
                            // 注入晚于 LauncherUI.onCreate 时，等下一个 Activity 出现是等不到的
                            if (cn == null || !cn.startsWith("com.tencent.mm.")) return;
                            log("Activity.onCreate fallback hit " + cn + " cl=" + o.getClass().getClassLoader());
                            if (new File(DIR + "watchdog.test").exists()) { log("fallback skipped (watchdog.test)"); return; }
                            onWeChatReady((Activity) o);
                        } catch (Throwable t) { log("fallback err " + t); }
                    }
                });
                log("activity fallback installed");
                startReadyWatchdog();
            } catch (Throwable t) { log("activity fallback fail " + t); }
        } catch (Throwable t) { log("launcher hook fail " + t); }
    }


    /** 1.32：从 ActivityThread 的 mActivities 里捞当前活着的 Activity。
     *  用于「注入时机晚于 LauncherUI.onCreate」的情况——此时不会再有任何 onCreate 回调，
     *  模块会一直不启动，表现为防撤回/一键已读全都没反应，boot.log 里只有 lpp 没有 ready。 */
    static Activity currentActivity() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object th = at.getMethod("currentActivityThread").invoke(null);
            if (th == null) return null;
            java.lang.reflect.Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object map = f.get(th);
            if (!(map instanceof java.util.Map)) return null;
            for (Object rec : ((java.util.Map<?, ?>) map).values()) {
                if (rec == null) continue;
                try {
                    java.lang.reflect.Field af = rec.getClass().getDeclaredField("activity");
                    af.setAccessible(true);
                    Object a = af.get(rec);
                    if (a instanceof Activity) return (Activity) a;
                } catch (Throwable t) {}
            }
        } catch (Throwable t) { log("currentActivity err " + t); }
        return null;
    }

    /** 注入后最多等 30 秒；期间没有 Activity 回调就自己捞一个把内核拉起来 */
    static void startReadyWatchdog() {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 15; i++) {
                    try { Thread.sleep(2000); } catch (Throwable t) {}
                    if (READY_STARTED) return;
                    Activity a = currentActivity();
                    if (a == null) continue;
                    log("watchdog boot from " + a.getClass().getName());
                    try { onWeChatReady(a); } catch (Throwable t) { log("watchdog ready err " + t); }
                    return;
                }
                if (!READY_STARTED) log("watchdog gave up: no activity after 30s");
            }
        }).start();
    }

    static synchronized void onWeChatReady(final Activity act) {
        if (act == null) return;
        ensureRecoveryEntry(act);
        if (READY_STARTED) return;
        READY_STARTED = true;
        // wxid 要等微信把数据库准备好，后台轮询几次
        new Thread(new Runnable() {
            public void run() {
                String w = null;
                for (int i = 0; i < 60 && w == null; i++) {
                    try { w = readWxid(act); } catch (Throwable t) { log("readWxid err " + t); break; }
                    if (w == null) {
                        try { Thread.sleep(500); } catch (Throwable t) {}
                    }
                }
                final String wxid = w;
                WX_ID_LAST = wxid;
                if (wxid == null) log("wxid still null, will retry on plus click");
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        boolean lic = false;
                        if (wxid != null) {
                            try { lic = licenseOk(act, wxid); } catch (Throwable t) {}
                        }
                        if (!lic) attachPlusEntry(act);   // 已激活交给内核，不再遍历视图树
                        if (wxid == null) {
                            Toast.makeText(act, "未激活：长按右上角 + 输入激活码", Toast.LENGTH_LONG).show();
                        } else {
                            proceed(act, wxid);
                        }
                    }
                });
            }
        }).start();
    }


    /** 长按右上角 + ：未激活 → 弹激活框；已激活 → 交给内核的功能菜单 */
    static void attachPlusEntry(final Activity act) { ensureRecoveryEntry(act); }

    static boolean hasKernelEntryListener(View view) {
        Class<?> kernel = RUNNING_KERNEL_CLASS;
        if (kernel == null) return false;
        try {
            Object info = XposedHelpers.getObjectField(view, "mListenerInfo");
            if (info == null) return false;
            Object listener = XposedHelpers.getObjectField(info, "mOnLongClickListener");
            return listener != null && listener.getClass().getClassLoader() == kernel.getClassLoader()
                && listener.getClass().getName().startsWith(kernel.getName() + "$");
        } catch (Throwable unavailable) { return false; }
    }

    static boolean attachOnce(final Activity act) {
        try {
            android.view.ViewGroup root = (android.view.ViewGroup) act.getWindow().getDecorView();
            android.view.View v = findPlusForStatus2(root);
            if (v == null) return false;
            // Keep an existing kernel-owned entry, including v191's legacy entry.
            // A fallback must not replace a working menu with an older-protocol status page.
            if (hasKernelEntryListener(v)) return true;
            v.setLongClickable(true);
            v.setOnLongClickListener(new android.view.View.OnLongClickListener() {
                public boolean onLongClick(android.view.View view) {
                    try {
                        // 1.71：长按 + 打开状态页（未激活/被停用也能看到原因），内核起来后由内核接管成功能菜单
                        openRecoveryEntry(act);
                    } catch (Throwable t) { log("plus click err " + t); }
                    return true;
                }
            });
            log("activation entry attached: " + v.getClass().getName());
            return true;
        } catch (Throwable t) { return false; }
    }

    static android.view.View findPlusForStatus(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            CharSequence d = c.getContentDescription();
            if (d != null) {
                String s = d.toString();
                if (s.contains("更多") || s.contains("More") || s.contains("more")) return c;
            }
            if (c instanceof android.view.ViewGroup) {
                android.view.View r = findPlusForStatus((android.view.ViewGroup) c);
                if (r != null) return r;
            }
        }
        return null;
    }

    static void proceed(final Activity act, final String wxid) {
        log("wxid=" + wxid);
        if (wxid == null || wxid.length() < 3) {
            // 1.73：还没登录/刚切换账号时读不到 wxid —— 提示登录并轮询，读到就继续
            log("wxid not ready, wait for login");
            try {
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(act, "请先登录微信，登录后会自动继续", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Throwable t) {}
            new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < 20; i++) {
                        try { Thread.sleep(1500); } catch (Throwable t) {}
                        String w = readWxid(act);
                        if (w != null && w.length() >= 3) {
                            log("wxid ready: " + w);
                            final String fw = w;
                            act.runOnUiThread(new Runnable() {
                                public void run() { proceed(act, fw); }
                            });
                            return;
                        }
                    }
                    log("wxid still missing after wait");
                }
            }).start();
            return;
        }
        if (!signedByMe(act)) {
            log("apk signature mismatch -> abort");
            act.runOnUiThread(new Runnable() {
                public void run() { Toast.makeText(act, "模块签名校验失败，请安装官方版本", Toast.LENGTH_LONG).show(); }
            });
            return;
        }
        if (licenseOk(act, wxid)) {
            if (booted) return;
            booted = true;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        final byte[] dex = loadKernelBytes();
                        if (dex != null && dex.length > 1000) {
                            // 1.64：boot 不再回 UI 线程跑 —— 首次 DEX 索引要 1~2.4 秒，
                            // 放主线程会卡住首帧/输入。内核内部需要的 UI 操作自己 runOnUiThread。
                            bootKernel(act, dex);
                            checkUpdateLater();
                            checkApkUpdateLater();
                        } else {
                            // 1.45：拿不到服务器内核（离线首启/下载失败/坏内核被拉黑）→ 直接用 APK 内嵌内核，
                            // 模块至少能用，不再弹「内核不可用」。
                            log("no server kernel -> embedded");
                            bootKernel(act, null);
                        }
                    } catch (Throwable t) { log("kernel err " + t); }
                }
            }).start();
            return;
        }
        // 1.68：本地没有凭证时先问服务端 —— 已激活过的 wxid 直接通行，被拉黑的直接拒
        if (wxid == null || !wxid.equals(whoamiFor)) {
            whoamiFor = wxid;
            final Activity a0 = act;
            final String w0 = wxid;
            new Thread(new Runnable() {
                public void run() {
                    String rr = null;
                    for (int i = 0; i < 4; i++) {                 // 网络抖动/限流时自动重试，别直接弹激活框
                        rr = whoami(w0);
                        if (rr != null) break;                    // OK / DISABLED / NONE 都算有结论
                        try { Thread.sleep(1200L * (i + 1)); } catch (Throwable t) {}
                    }
                    final String r = rr;
                    a0.runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                if (r != null && r.startsWith("OK|")) {
                                    String[] pp = r.split("\\|", -1);
                                    if (pp.length >= 3) {
                                        writeText(LIC, "wxid=" + w0 + "\ncode=" + pp[1] + "\ntoken=" + pp[2] + "\n");
                                        log("whoami: server has this wxid, license restored");
                                        proceed(a0, w0);
                                        return;
                                    }
                                } else if (r != null && r.startsWith("DISABLED|")) {
                                    String reason = r.length() > 9 ? r.substring(9) : "";
                                    log("whoami: disabled " + reason);
                                    Toast.makeText(a0, "该微信号已被停用" + (reason.length() > 0 ? "：" + reason : ""), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                showActivate(a0, w0);
                            } catch (Throwable t) { log("whoami ui err " + t); }
                        }
                    });
                }
            }).start();
            return;
        }
        final String auto = readText(DIR + "auto_activate");
        if (auto != null && auto.trim().length() > 0) {
            log("auto activate with " + auto.trim());
            doActivate(act, wxid, auto.trim(), null);
            return;
        }
        act.runOnUiThread(new Runnable() {
            public void run() { try { showActivate(act, wxid); } catch (Throwable t) { log("ui err " + t); } }
        });
    }


    static int dbg = 0;

    /** 读当前登录微信的 wxid：SharedPreferences → 文件 → 授权文件 → userinfo 表 */
    static String wxidFromXml(String path) {
        try {
            String xml = readText(path);
            if (xml == null) return null;
            int k = xml.indexOf("login_weixin_username");
            if (k > 0) {
                int gt = xml.indexOf('>', k);
                int lt = gt > 0 ? xml.indexOf('<', gt) : -1;
                if (gt > 0 && lt > gt) {
                    String v = xml.substring(gt + 1, lt).trim();
                    if (v.length() > 3 && v.indexOf(' ') < 0) return v;
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    /** 1.86：微信可能把登录信息放到别的 prefs 文件里，整目录扫一遍 */
    static String wxidFromPrefsDir(String dirPath) {
        try {
            java.io.File d = new java.io.File(dirPath);
            String[] names = d.list();
            if (names == null) return null;
            int n = 0;
            for (String nm : names) {
                if (nm == null || !nm.endsWith(".xml")) continue;
                if (++n > 40) break;
                String v = wxidFromXml(dirPath + "/" + nm);
                if (v != null) { log("wxid prefs hit " + nm); return v; }
            }
        } catch (Throwable t) {}
        return null;
    }

    static String wxidFromLicense() {
        try {
            String t = readText(LIC);
            if (t != null) {
                for (String line : t.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("wxid=")) {
                        String v = line.substring(5).trim();
                        if (v.length() > 3) return v;
                    }
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    static volatile long WXID_FAIL_AT = 0;
    static String readWxid(android.content.Context ctx) {
        StringBuilder why = new StringBuilder();
        // 1) 直接问 SharedPreferences —— 和微信自己读的是同一份，不受路径/SELinux 影响
        try {
            if (ctx != null) {
                android.content.SharedPreferences sp = ctx.getSharedPreferences("com.tencent.mm_preferences", 0);
                if (sp != null) {
                    String v = sp.getString("login_weixin_username", null);
                    if (v != null && v.trim().length() > 3) { log("wxid src=sp"); return v.trim(); }
                }
            } else why.append("ctx-null;");
        } catch (Throwable t) { why.append("sp:").append(t.getClass().getSimpleName()).append(";"); }

        // 2) 文件：常用路径 → 整目录扫 → device-protected（Android 15+ 部分机型）
        String base = computeDataDir();
        String v = wxidFromXml(base + "/shared_prefs/com.tencent.mm_preferences.xml");
        if (v != null) { log("wxid src=prefs"); return v; }
        v = wxidFromPrefsDir(base + "/shared_prefs");
        if (v != null) { log("wxid src=prefs-scan"); return v; }
        try {
            int user = android.os.Process.myUid() / 100000;
            String de = "/data/user_de/" + user + "/com.tencent.mm/shared_prefs";
            v = wxidFromXml(de + "/com.tencent.mm_preferences.xml");
            if (v == null) v = wxidFromPrefsDir(de);
            if (v != null) { log("wxid src=prefs-de"); return v; }
        } catch (Throwable t) {}

        // 3) 已激活的设备：授权文件里就写着 wxid
        v = wxidFromLicense();
        if (v != null) { log("wxid src=license"); return v; }

        // 4) 最后再试数据库（微信内核没起来时这条路会 NPE，要判空）
        Cursor c = null;
        try {
            ClassLoader cl = WX_CL;
            Class<?> locator = Class.forName("zp0.j1", true, cl);
            Class<?> iface = Class.forName("gp3.c4", true, cl);
            Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
            Object conv = XposedHelpers.callMethod(svc, "Hj");
            Object db = XposedHelpers.getObjectField(conv, "d");
            if (db != null) {
                c = (Cursor) XposedHelpers.callMethod(db, "B", "select username from userinfo limit 1", null);
                if (c != null && c.moveToFirst()) {
                    String u = c.getString(0);
                    if (u != null && u.trim().length() > 0) { log("wxid src=db"); return u.trim(); }
                }
            } else why.append("db-null;");
        } catch (Throwable t) {
            if (dbg++ < 3) log("readWxid db err " + t);
            why.append("db:").append(t.getClass().getSimpleName()).append(";");
        } finally { if (c != null) try { c.close(); } catch (Throwable t) {} }

        long now = System.currentTimeMillis();
        if (now - WXID_FAIL_AT > 5000) {
            WXID_FAIL_AT = now;
            log("wxid fail " + why + " base=" + base);
        }
        return null;
    }

    static String readWxid() { return readWxid(APP_CTX_REF); }

    // ---------------- 设备指纹（备用）----------------
    static String deviceCode(Context c) {
        String raw = rawId(c);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(raw.getBytes("UTF-8"));
            String b32 = b32(h, 12);          // 12 个字符
            return b32.substring(0, 4) + "-" + b32.substring(4, 8) + "-" + b32.substring(8, 12);
        } catch (Throwable t) { return "ERR"; }
    }

    static String rawId(Context c) {
        // 1) Widevine DRM ID：TEE 里，刷机/重装一般不变（普通应用读不到主板序列号）
        try {
            java.util.UUID wv = new java.util.UUID(0xedef8ba979d64aceL, 0xa3c827dcd51d21edL);
            Class<?> md = Class.forName("android.media.MediaDrm");
            Object drm = md.getConstructor(java.util.UUID.class).newInstance(wv);
            byte[] id = (byte[]) md.getMethod("getPropertyByteArray", String.class).invoke(drm, "deviceUniqueId");
            try { md.getMethod("close").invoke(drm); } catch (Throwable t) {}
            StringBuilder sb = new StringBuilder();
            for (byte b : id) sb.append(String.format("%02x", b));
            if (sb.length() >= 32) return "WV:" + sb;
        } catch (Throwable t) { log("wv err " + t); }
        // 2) Android ID
        try {
            Object v = XposedHelpers.callStaticMethod(
                    Class.forName("android.provider.Settings$Secure"), "getString",
                    XposedHelpers.callMethod(c, "getContentResolver"), "android_id");
            if (v instanceof String && ((String) v).length() > 0) return "AID:" + v;
        } catch (Throwable t) {}
        // 3) 兜底：机型指纹
        try {
            return "FP:" + XposedHelpers.getStaticObjectField(android.os.Build.class, "FINGERPRINT");
        } catch (Throwable t) { return "UNKNOWN"; }
    }


    static final String EXPECTED_SIG = "87b7f91d54f8d67a367630cacfc9476b6e4d83bea7ced24cd2f7bd884b3f9d11";

    /** 校验模块自身的签名：被重打包（签名不同）就拒绝加载内核。
     *  查不到包（系统可见性限制）时放行，避免误伤。 */
    /** 找模块自己的 APK 路径。
     *  包名改成 io.github.vividbeacon43.wxentry 之后，微信进程受包可见性限制查不到模块
     *  （getPackageInfo → NameNotFound），而 LSPosed 的模块类是从内存 dex 加载的，
     *  getProtectionDomain() 直接是 null。所以直接跟着 ClassLoader 的 DexPathList 摸到 APK 路径。 */
    static String moduleApk() {
        try {
            java.net.URL u = Main.class.getResource("/AndroidManifest.xml");
            if (u != null) {
                String s = u.toString();
                int i = s.indexOf("!/");
                if (s.startsWith("jar:file:") && i > 9) return s.substring(9, i);
            }
        } catch (Throwable t) {}
        try {
            ClassLoader cl = Main.class.getClassLoader();
            Object pl = XposedHelpers.getObjectField(cl, "pathList");
            Object[] els = (Object[]) XposedHelpers.getObjectField(pl, "dexElements");
            for (int i = 0; els != null && i < els.length; i++) {
                try {
                    Object df = XposedHelpers.getObjectField(els[i], "dexFile");
                    if (df != null) {
                        String n = (String) XposedHelpers.callMethod(df, "getName");
                        if (n != null && n.endsWith(".apk")) return n;
                    }
                } catch (Throwable t) {}
                try {
                    String p = (String) XposedHelpers.getObjectField(els[i], "path");
                    if (p != null && p.endsWith(".apk")) return p;
                } catch (Throwable t) {}
            }
        } catch (Throwable t) {}
        try {
            String s = String.valueOf(Main.class.getClassLoader());
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(/data/app/[^,\\]\\s]+\\.apk)").matcher(s);
            if (m.find()) return m.group(1);
        } catch (Throwable t) {}
        return null;
    }

    /** 从模块 APK 的 v1 签名块里取证书算 sha256 —— 不经过 PackageManager，不受包可见性影响。 */
    static String apkCertSha(String apk) {
        java.util.zip.ZipFile zf = null;
        try {
            zf = new java.util.zip.ZipFile(apk);
            String hit = null;
            for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                String up = n.toUpperCase();
                if (up.startsWith("META-INF/") && (up.endsWith(".RSA") || up.endsWith(".DSA") || up.endsWith(".EC"))) { hit = n; break; }
            }
            if (hit == null) return null;
            java.io.InputStream in = zf.getInputStream(zf.getEntry(hit));
            java.security.cert.Certificate cert = java.security.cert.CertificateFactory
                    .getInstance("X.509").generateCertificate(in);
            byte[] d = cert.getEncoded();
            in.close();
            return sha256Hex(d);
        } catch (Throwable t) {
            log("sig: apk read err " + t);
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Throwable t) {}
        }
    }

    /** 严格模式：WechatXposed/sig.strict 存在时，「查不到签名」按不通过处理。 */
    static boolean sigStrict() {
        try { return new File(DIR + "sig.strict").exists(); } catch (Throwable t) { return false; }
    }

    static boolean signedByMe(Context c) {
        // 1) 首选：直接读模块 APK 里的证书（包可见性、内存 dex 都不影响）
        try {
            String apk = moduleApk();
            if (apk != null) {
                String hex = apkCertSha(apk);
                if (hex != null) {
                    boolean ok = EXPECTED_SIG.equalsIgnoreCase(hex);
                    log("sig=" + hex + (ok ? " (ok)" : " (MISMATCH)"));
                    return ok;   // APK 里读到了就是权威结果，不用再问 PackageManager
                } else {
                    log("sig: apk has no v1 block, fallback");
                }
            } else {
                log("sig: module apk path unknown, fallback");
            }
        } catch (Throwable t) { log("sig: apk path err " + t); }
        // 2) 兜底：PackageManager（能用就用，查不到就放行）
        try {
            Object pm = XposedHelpers.callMethod(c, "getPackageManager");
            Object pi = null;
            String[] names = new String[]{"io.github.vividbeacon43.wxentry", "com.zafiro.wxentry"};
            for (int i = 0; i < names.length && pi == null; i++) {
                try { pi = XposedHelpers.callMethod(pm, "getPackageInfo", names[i], Integer.valueOf(64)); }
                catch (Throwable t) {}
            }
            if (pi == null) {
                log("sig: package not visible, UNVERIFIED" + (sigStrict() ? " (strict: blocked)" : " (fail-open)"));
                return !sigStrict();
            }
            Object[] sigs = (Object[]) XposedHelpers.getObjectField(pi, "signatures");
            if (sigs == null || sigs.length == 0) {
                log("sig: none, UNVERIFIED" + (sigStrict() ? " (strict: blocked)" : " (fail-open)"));
                return !sigStrict();          // S07：无法验证 → 由严格模式决定，不再无条件放行
            }
            String hex = sha256Hex((byte[]) XposedHelpers.callMethod(sigs[0], "toByteArray"));
            log("sig(pm)=" + hex);
            return EXPECTED_SIG.equalsIgnoreCase(hex);
        } catch (Throwable t) {
            log("sig check unavailable: " + t + (sigStrict() ? " (strict: blocked)" : " (fail-open)"));
            return !sigStrict();   // 默认放行，但绝不谎称「已验证」；sig.strict 打开才拦
        }
    }


    // ---------------- 授权 ----------------



    static java.lang.ref.WeakReference<android.view.View> LAST_STATUS_VIEW = null;

    static android.view.View findPlusForStatus2(android.view.ViewGroup g) {
        try {
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View c = g.getChildAt(i);
                CharSequence d = c.getContentDescription();
                if (d != null) {
                    String t = d.toString();
                    if (t.contains("\u66f4\u591a") || t.contains("More") || t.contains("more")) return c;
                }
                if (c instanceof android.view.ViewGroup) {
                    android.view.View r = findPlusForStatus2((android.view.ViewGroup) c);
                    if (r != null) return r;
                }
            }
        } catch (Throwable t) {}
        return null;
    }


    static void showStatusDialog(final Activity act) {
        final StringBuilder sb = new StringBuilder();
        sb.append("\u6a21\u5757\uff1a").append(APP_VER).append("\n");
        String kv = null;
        try { kv = readText(DIR + "kernel.ver"); } catch (Throwable t) {}
        sb.append("\u5185\u6838\uff1a").append(kv == null || kv.trim().length() == 0 ? "\u672a\u52a0\u8f7d" : ("v" + kv.trim())).append("\n");
        String lic = readText(LIC);
        String wxid = lic == null ? null : line(lic, "wxid=");
        String token = lic == null ? null : line(lic, "token=");
        boolean ok = false;
        try { ok = wxid != null && token != null && verifyToken(token, wxid); } catch (Throwable t) {}
        sb.append("\u6388\u6743\uff1a").append(ok ? "\u5df2\u6fc0\u6d3b" : "\u672a\u6fc0\u6d3b").append("\n");
        if (!ok) sb.append("\n\u672a\u6fc0\u6d3b\u6216\u88ab\u505c\u7528\u65f6\uff0c\u529f\u80fd\u4e0d\u4f1a\u751f\u6548\u3002\u70b9\u4e0b\u9762\u7684\u300c\u6fc0\u6d3b\u300d\u8f93\u5165\u6fc0\u6d3b\u7801\u3002");
        final String w0 = wxid != null ? wxid : readWxid(act);
        new Thread(new Runnable() {
            public void run() {
                final String r = whoami(w0 == null ? "" : w0);
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String msg = sb.toString();
                            if (r != null && r.startsWith("DISABLED|")) {
                                String why = r.length() > 9 ? r.substring(9) : "";
                                msg = "\u6a21\u5757\uff1a" + APP_VER + "\n\u5185\u6838\uff1a\u672a\u52a0\u8f7d\n\u6388\u6743\uff1a\u5df2\u88ab\u505c\u7528"
                                        + (why.length() > 0 ? "\n\u539f\u56e0\uff1a" + why : "");
                            }
                            AlertDialog.Builder b = new AlertDialog.Builder(act);
                            b.setTitle("WXEntry \u72b6\u6001");
                            b.setMessage(msg);
                            b.setPositiveButton("\u5173\u95ed", null);
                            if (r == null || !r.startsWith("DISABLED|")) {
                                b.setNegativeButton("\u6fc0\u6d3b", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int i) {
                                        try { showActivate(act, w0 == null ? "" : w0); } catch (Throwable t) {}
                                    }
                                });
                            }
                            b.show();
                        } catch (Throwable t) { log("status dlg ui err " + t); }
                    }
                });
            }
        }).start();
    }

    static volatile String whoamiFor = null;   // 已经问过 whoami 的 wxid

    /** 1.68：问服务端「这个 wxid 是不是已经激活过」。是就直接拿回凭证，不再弹激活框。 */
    static String whoami(String wxid) {
        try {
            String body = "{\"wxid\":\"" + jesc(wxid) + "\"}";
            String resp = http(BASE + "whoami", body);
            if (resp == null) return null;
            String f = resp.replace(" ", "").replace("\n", "");
            if (f.contains("\"disabled\":true")) return "DISABLED|" + flat(jsonStr(resp, "reason"));
            if (!f.contains("\"ok\":true")) {
                if (f.contains("\"none\":true")) return "NONE";   // 服务端确认没绑过码 → 才弹激活框
                return null;                                        // 网络/服务异常 → 重试
            }
            String tk = jsonStr(resp, "token");
            String cd = jsonStr(resp, "code");
            if (tk == null || cd == null || tk.length() < 20) return null;
            return "OK|" + cd + "|" + tk;
        } catch (Throwable t) { log("whoami err " + t); return null; }
    }

    static boolean licenseOk(Context c, String wxid) {
        try {
            String s = readText(LIC);
            if (s == null) return false;
            String token = line(s, "token=");
            String saved = line(s, "wxid=");
            if (token == null || saved == null) return false;
            if (!saved.equals(wxid)) { log("license wxid mismatch"); return false; }
            return verifyToken(token, wxid);
        } catch (Throwable t) { return false; }
    }

    static boolean verifyToken(String token, String wxid) {
        try {
            int dot = token.indexOf('.');
            if (dot <= 0) return false;
            byte[] payload = b64d(token.substring(0, dot));
            byte[] sig = b64d(token.substring(dot + 1));
            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(publicKey());
            v.update(payload);
            if (!v.verify(sig)) { log("bad signature"); return false; }
            String p = new String(payload, "UTF-8");
            return p.indexOf("\"wxid\":\"" + wxid + "\"") >= 0;
        } catch (Throwable t) { log("verify err " + t); return false; }
    }

    static PublicKey publicKey() throws Exception {
        byte[] der = b64d(PUB_B64);
        return java.security.KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    // ---------------- 激活界面 ----------------
    static void doActivate(final Activity act, final String wxid, final String code, final TextView status) {
        if (status != null) status.setText("正在验证激活码并匹配版本…");
        new Thread(new Runnable() { public void run() {
            try {
                String response = http(URL_ACTIVATE, "{\"code\":\"" + jesc(code) + "\",\"wxid\":\"" + jesc(wxid) + "\"}");
                String token = jsonStr(response, "token");
                if (token == null || !verifyToken(token, wxid)) {
                    final String message = jsonStr(response, "msg");
                    act.runOnUiThread(new Runnable() { public void run() {
                        if (status != null) status.setText(message == null ? "激活失败" : message);
                    }}); return;
                }
                writeFileAtomic(new File(LIC), ("wxid=" + wxid + "\ncode=" + code + "\ntoken=" + token + "\n").getBytes("UTF-8"));
                if (!ensureKernel()) {
                    act.runOnUiThread(new Runnable() { public void run() {
                        if (status != null) status.setText("激活成功，版本下载尚未完成，请检查网络后重试");
                    }}); return;
                }
                final String label = EditionPolicy.name(currentKernelEdition());
                if (!booted) {
                    booted = true; bootKernel(act, readVerifiedCache()); checkUpdateLater(); checkApkUpdateLater();
                    act.runOnUiThread(new Runnable() { public void run() {
                        if (status != null) status.setText("激活成功，已自动启用" + label);
                        afterActivated(act);
                    }});
                } else {
                    act.runOnUiThread(new Runnable() { public void run() {
                        String message = "激活成功，已匹配" + label + "，重启微信后生效";
                        if (status != null) status.setText(message);
                        showEditionRestart(act, message);
                    }});
                }
            } catch (Throwable t) {
                act.runOnUiThread(new Runnable() { public void run() {
                    if (status != null) status.setText("激活或版本下载失败，请检查网络后重试");
                }});
                log("activation channel " + t.getClass().getSimpleName());
            }
        }}, "wxentry-activate-edition").start();
    }

    static void showActivate(final Activity act, final String wxid) {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 40;
        final String[] wxRef = new String[]{wxid};
        TextView t1 = new TextView(act);
        t1.setText("当前微信：" + (wxid == null ? "读取中…" : wxid) + "\n（激活码自动绑定此微信号，并匹配对应版本）");
        t1.setPadding(pad, pad, pad, pad);
        t1.setTextSize(15);
        t1.setTextIsSelectable(true);
        root.addView(t1);
        final EditText input = new EditText(act);
        input.setHint("粘贴激活码");
        root.addView(input);
        final TextView status = new TextView(act);
        status.setPadding(pad, pad, pad, pad);
        status.setTextSize(13);
        root.addView(status);

        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setTitle(booted ? "升级/更换激活码" : "微信模块激活")
                .setView(root)
                .setPositiveButton("激活", null)
                .setNegativeButton("关闭", null);
        final AlertDialog d = b.create();
        d.show();
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String code = input.getText().toString().trim();
                if (code.length() < 8) { status.setText("激活码格式不对"); return; }
                String w = wxRef[0];
                if (w == null) w = readWxid(act);
                if (w == null) {
                    // 1.86：登录信息可能刚写入，重试几次再报错
                    status.setText("正在读取微信号…");
                    for (int i = 0; i < 3 && w == null; i++) {
                        try { Thread.sleep(400); } catch (Throwable t) {}
                        w = readWxid(act);
                    }
                }
                if (w == null) { status.setText("读取微信号失败，请重启微信后再试；仍失败请截图发到频道"); return; }
                doActivate(act, w, code, status);
            }
        });
    }

    // ---------------- 内核 ----------------

    static byte[] decrypt(byte[] enc) throws Exception {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(javax.crypto.Cipher.DECRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(K_KEY, "AES"),
                new javax.crypto.spec.IvParameterSpec(K_IV));
        return c.doFinal(enc);
    }

    /** 取内核明文：优先本地缓存（解密+校验），否则联网拉取 */
    // ---------------- 自检 / 诊断 ----------------
    static final String APP_VER = "1.91";
    static volatile boolean READY_STARTED = false;   // onWeChatReady 只跑一次
    static final File BOOTLOG = new File(DIR + "boot.log");
    static final File BOOTPREV = new File(DIR + "boot.prev");
    static final File DIAG_SENT = new File(DIR + "diag.sent");
    static final String URL_DIAG = BASE + "diag";   // 走 wx-api.py 的 /wx/diag（1.15 起；旧 diag.php 已废弃）
    static volatile String PREV_FAIL = null;
    static volatile android.content.Context APP_CTX_REF = null;
    static volatile String WX_ID_LAST = null;

    static void bootMark(String stage) {
        try {
            FileOutputStream o = new FileOutputStream(BOOTLOG, true);
            o.write((new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date())
                    + " " + stage + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) {}
    }

    /** 上次启动有没有走到 ready；没走到就把最后一步返回出来 */
    static String prevBootFail() {
        try {
            if (!BOOTPREV.exists()) return null;
            String txt = readText(BOOTPREV.getAbsolutePath());
            if (txt == null) return null;
            String[] ls = txt.trim().split("\n");
            if (ls.length == 0) return null;
            String last = ls[ls.length - 1].trim();
            if (last.endsWith(" ready") || last.contains(" ready")) return null;
            return last;
        } catch (Throwable t) { return null; }
    }

    /** 从指定偏移读文件（1.21 用来只看本次启动新写的日志） */
    static String readFrom(String path, long off) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            if (off < 0 || off > f.length()) off = 0;
            java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r");
            try {
                r.seek(off);
                byte[] b = new byte[(int) Math.min(f.length() - off, 20000)];
                r.readFully(b);
                return new String(b, "UTF-8");
            } finally { r.close(); }
        } catch (Throwable t) { return null; }
    }

    static String tailOf(String path, int maxLines) {
        try {
            File f = new File(path);
            if (!f.exists()) return "(no " + f.getName() + ")";
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<String>();
            String line;
            while ((line = r.readLine()) != null) {
                if (q.size() >= maxLines) q.removeFirst();
                q.addLast(line);
            }
            r.close();
            StringBuilder sb = new StringBuilder();
            for (String x : q) sb.append(x).append('\n');
            return sb.toString();
        } catch (Throwable t) { return "(tail err " + t + ")"; }
    }

    /** 1.54：截断单节，保证后面的小节标题不会被总长度上限切掉 */
    /** 按 UTF-8 字节数截断，且不切在多字节字符中间。客户端以前按 char 限长，
     *  中文/emoji 一多，解压后字节数就超过服务端 256KB 上限，最该看的那份反被丢掉。 */
    static String capBytes(String s, int maxBytes) {
        if (s == null) return "";
        try {
            byte[] b = s.getBytes("UTF-8");
            if (b.length <= maxBytes) return s;
            int end = maxBytes;
            while (end > 0 && (b[end] & 0xC0) == 0x80) end--;
            return new String(b, 0, end, "UTF-8") + "\n… (truncated " + (b.length - end) + " bytes)\n";
        } catch (Throwable t) {
            return s.length() > maxBytes ? s.substring(0, maxBytes) : s;
        }
    }

    static String cap(String s, int n) {
        if (s == null) return "";
        if (s.length() <= n) return s;
        return s.substring(0, n) + "\n… (truncated " + (s.length() - n) + " chars)\n";
    }

    static final String DIAG_KEY = "wxentry-diag-v2-bddaea09a90e";

    static String licField(String key) {
        try {
            String lic = readText(LIC);
            if (lic == null) return null;
            String[] ls = lic.split("\n");
            for (String ln : ls) if (ln.startsWith(key + "=")) return ln.substring(key.length() + 1).trim();
        } catch (Throwable t) {}
        return null;
    }

    static String sha256Str(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", Byte.valueOf(b)));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    static String hmacHex(String key, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
            byte[] d = mac.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", Byte.valueOf(b)));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }


    /** 1.88：采集 LSPosed 版本 —— 诊断上报里要能看出对方用的是哪一版框架 */
    static String lspVersion() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app != null) {
                Object pm = app.getClass().getMethod("getPackageManager").invoke(app);
                Object pi = pm.getClass().getMethod("getPackageInfo", String.class, int.class)
                        .invoke(pm, "org.lsposed.manager", Integer.valueOf(0));
                Class<?> pc = pi.getClass();
                Object vn = pc.getField("versionName").get(pi);
                Object vc = pc.getField("versionCode").get(pi);
                return String.valueOf(vn) + "(" + vc + ")";
            }
        } catch (Throwable t) {}
        try {
            Class<?> bc = Class.forName("org.lsposed.lspd.BuildConfig", false, Main.class.getClassLoader());
            Object vn = bc.getField("VERSION_NAME").get(null);
            Object vc = bc.getField("VERSION_CODE").get(null);
            return String.valueOf(vn) + "(" + vc + ")";
        } catch (Throwable t) {}
        try {
            Class<?> xb = Class.forName("de.robv.android.xposed.XposedBridge", false, Main.class.getClassLoader());
            Object v = xb.getMethod("getXposedVersion").invoke(null);
            return "api" + v;
        } catch (Throwable t) {}
        return "unknown";
    }

    static String diagText(String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== wxentry diag ===\n");
        sb.append("time=").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())).append('\n');
        sb.append("reason=").append(reason).append('\n');
        sb.append("loader=").append(APP_VER).append('\n');
        try { sb.append("kernel=").append(readText(DIR + "kernel.ver")).append('\n'); } catch (Throwable t) {}
        try { sb.append("lsp=").append(lspVersion()).append('\n'); } catch (Throwable t) {}
        try {
            android.content.Context c = APP_CTX_REF;
            Object pm = c.getPackageManager();
            Object pi = pm.getClass().getMethod("getPackageInfo", String.class, int.class)
                    .invoke(pm, "com.tencent.mm", Integer.valueOf(0));
            Class<?> pc = pi.getClass();
            Object vn = pc.getField("versionName").get(pi);
            Object vc = pc.getField("versionCode").get(pi);
            sb.append("wechat=").append(vn).append('/').append(vc).append('\n');
        } catch (Throwable t) { sb.append("wechat=?\n"); }
        try {
            String model = String.valueOf(android.os.Build.class.getField("MODEL").get(null));
            Class<?> bv = Class.forName("android.os.Build$VERSION");
            String rel = String.valueOf(bv.getField("RELEASE").get(null));
            sb.append("device=").append(model).append(" / android ").append(rel).append('\n');
        } catch (Throwable t) {}
        try {
            String w = licField("wxid");
            String c = licField("code");
            if (w == null && c == null) sb.append("license=none\n");
            else {
                String wh = w == null ? "-" : sha256Str(w).substring(0, 12);
                String cm = c == null ? "-" : (c.length() <= 4 ? "****" : c.substring(0, 4) + "****");
                sb.append("license=wxidhash:").append(wh).append(" code:").append(cm).append('\n');
            }
        } catch (Throwable t) {}
        try {
            File d = new File(DIR);
            File[] fs = d.listFiles();
            if (fs != null) {
                sb.append("files=");
                for (File f : fs) sb.append(f.getName()).append('(').append(f.length()).append(") ");
                sb.append('\n');
            }
        } catch (Throwable t) {}
        sb.append("prevFail=").append(PREV_FAIL).append('\n');
        // 1.54：每节限长。以前 trace.log 的行很长时会把后面几节的标题挤出 200KB 上限，
        // 服务端 diag_sane 找不到 "--- trace.log ---" 就整份丢（上报里的 400 bad payload）。
        // 每节按字节预算：8K/8K/48K/120K，合计 < 190KB，稳定小于服务端 256KB 解压上限
        sb.append("--- boot.prev ---\n").append(capBytes(tailOf(BOOTPREV.getAbsolutePath(), 30), 8000));
        sb.append("--- boot.log ---\n").append(capBytes(tailOf(BOOTLOG.getAbsolutePath(), 30), 8000));
        sb.append("--- loader.log ---\n").append(capBytes(tailOf(DIR + "loader.log", 120), 48000));
        sb.append("--- trace.log ---\n").append(capBytes(tailOf(DIR + "trace.log", 200), 120000));
        return sb.toString();
    }

    static String gzip64(String s) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bo);
        gz.write(s.getBytes("UTF-8"));
        gz.close();
        return android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
    }

    // ---------------- 1.48：远程停用 / 申诉 ----------------

    /** 问一次服务器：这个微信号有没有被停用、有没有许愿回复。
     *  返回 OK|0/1|原因|回复 或 ERR（离线/出错）。 */
    public static String statusLine() {
        try {
            String lic = readText(LIC);
            String token = lic == null ? null : line(lic, "token=");
            if (token == null || token.length() < 20) return "ERR";
            String body = "{\"token\":\"" + jesc(token) + "\"}";
            String resp = http(BASE + "status", body);
            if (resp == null) return "ERR";
            String f = resp.replace(" ", "").replace("\n", "");
            if (!f.contains("\"ok\":true")) return "ERR";
            boolean dis = f.contains("\"disabled\":true");
            String reason = jsonStr(resp, "reason");
            String reply = jsonStr(resp, "reply");
            String feat = jsonStr(resp, "feat");
            String tierName = jsonStr(resp, "tier_name");
            String edition = jsonStr(resp, "kernel_edition");
            if (EditionPolicy.valid(edition) && !edition.equals(currentKernelEdition())) refreshEditionLater();
            return "OK|" + (dis ? "1" : "0") + "|" + flat(reason) + "|" + flat(reply) + "|" + flat(feat)
                    + "|" + flat(tierName);
        } catch (Throwable t) { log("status err " + t); return "ERR"; }
    }

    static String flat(String s) {
        if (s == null) return "";
        return s.replace("|", "/").replace("\n", " ").trim();
    }

    /** 提交申诉（带授权凭证）。返回 OK 或错误文案。 */
    public static String sendAppeal(String text) {
        try {
            String lic = readText(LIC);
            String token = lic == null ? null : line(lic, "token=");
            if (token == null || token.length() < 20) return "请先激活模块";
            String body = "{\"token\":\"" + jesc(token) + "\",\"text\":\"" + jesc(text) + "\"}";
            String resp = http(BASE + "appeal", body);
            if (resp == null) return "网络错误";
            if ("true".equals(jsonStr(resp, "ok"))) return "OK";
            String m = jsonStr(resp, "msg");
            return m == null ? "提交失败" : m;
        } catch (Throwable t) { return "提交失败：" + t; }
    }

    /** 输入功能码解锁：成功返回 OK|等级名|功能列表，失败返回 ERR|原因 */
    public static String unlockCode(String code) { return "ERR|请在升级/更换激活码中输入对应版本激活码"; }

    /** 历史日志里可能还留着 1.62 及以前写进去的完整 token。上传前就地抹掉（保留其余内容）。 */
    static void scrubLogs() {
        String[] files = new String[] { DIR + "loader.log", DIR + "trace.log",
                BOOTLOG.getAbsolutePath(), BOOTPREV.getAbsolutePath() };
        for (int i = 0; i < files.length; i++) {
            try {
                File f = new File(files[i]);
                if (!f.exists()) continue;
                String t = readText(files[i]);
                if (t == null || t.length() == 0) continue;
                // S01：大文件不再跳过（跳过等于把 token 原样传出去），只处理尾部 2MB
                if (t.length() > 2 * 1024 * 1024) t = t.substring(t.length() - 2 * 1024 * 1024);
                String c = scrubTokens(t);
                if (!c.equals(t)) writeText(files[i], c);
            } catch (Throwable t) {}
        }
    }

    /** S01：任何形态的 token 都抹掉——紧凑/带空格的 JSON、token= 赋值、双重转义都覆盖。
     *  发送前对最终字符串再调一次，历史文件清不掉也不影响脱敏结果。 */
    static String scrubTokens(String s) {
        if (s == null) return "";
        String out = s;
        // {"token":"..."} / { "token" : "..." } / {\"token\":\"...\"}
        out = out.replaceAll("(?i)(\\\\?\"token\\\\?\"\\s*:\\s*\\\\?\")[^\"\\\\]{8,}(\\\\?\")", "$1***$2");
        // token=xxx / token: xxx
        out = out.replaceAll("(?i)(\\btoken\\s*[=:]\\s*)[A-Za-z0-9+/=_.:\\-]{16,}", "$1***");
        // Authorization: Bearer xxx
        out = out.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._\\-+/=]{16,}", "$1***");
        return out;
    }

    static void uploadDiag(final String reason) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    long now = System.currentTimeMillis();
                    // 1.28：手动「真名自检」属于用户主动上报，不受 30 分钟节流限制
                    boolean critical = reason.startsWith("activate") || reason.startsWith("boot-fail")
                            || reason.startsWith("prev-boot") || reason.startsWith("realname-debug")
                            || reason.startsWith("kernel-fail");   // 1.46：内核回退/钩子静默/补丁变更不能被 30 分钟节流吃掉
                    if (!critical && DIAG_SENT.exists() && now - DIAG_SENT.lastModified() < 30L * 60 * 1000) return;
                    String w = licField("wxid");
                    String c = licField("code");
                    String wh = w == null ? "" : sha256Str(w).substring(0, 12);
                    // 只带码的末 4 位（前 4 位对所有码都是 "WX-M"，没有信息量）
                    String cp = c == null || c.length() < 4 ? "" : c.substring(c.length() - 4);
                    scrubLogs();                       // 上传前先把历史日志里的 token 抹掉
                    // S01：最终待发送串强制脱敏，不依赖历史文件是否清理成功
                    String data = capBytes(scrubTokens(diagText(reason)), 200000);
                    String gz = gzip64(data);
                    String ts = String.valueOf(now / 1000L);
                    // 签名覆盖 原因|时间戳|正文：改了正文/换了原因/重放旧包都会对不上
                    String mac = hmacHex(DIAG_KEY, reason + "|" + ts + "|" + data);
                    // 带上授权凭证：服务端验签通过才算「可信」，没有就只能进隔离目录
                    String lic = licField("token");
                    String licPart = (lic == null || lic.length() < 20) ? "" : ",\"lic\":\"" + jesc(lic) + "\"";
                    String body = "{\"reason\":\"" + jesc(reason) + "\",\"wh\":\"" + wh + "\",\"cp\":\"" + cp
                            + "\",\"loader\":\"" + APP_VER + "\",\"ts\":" + ts
                            + ",\"hmac\":\"" + mac + "\"" + licPart + ",\"gz\":\"" + gz + "\"}";
                    String r = httpPostHdr(URL_DIAG, body, "X-Wx-Diag", "1");
                    log("diag sent(" + reason + ") -> " + (r == null ? "null" : (r.length() > 120 ? r.substring(0, 120) : r)));
                    if (DIAG_SENT.exists()) DIAG_SENT.setLastModified(now); else DIAG_SENT.createNewFile();
                } catch (Throwable t) { log("diag send err " + t); }
            }
        }).start();
    }

    /** 读缓存并**当场验证**（解密 + sha 必须等于 kernel.sha）。验不过就作废这一代缓存并返回 null。 */
    static byte[] readVerifiedCache() {
        KernelDelivery.Bundle bundle = DELIVERY.cached(licField("token"));
        return bundle == null ? null : bundle.dex;
    }

    static void dropKernelCache() { DELIVERY.clear(); }

    /** S05：每次写用独立临时文件；失败只清自己那份，绝不删除已发布的目标。 */
    static void writeFileAtomic(File f, byte[] data) throws Exception {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dir, f.getName() + "." + Long.toHexString(System.nanoTime())
                + "-" + Integer.toHexString(Thread.currentThread().hashCode()) + ".tmp");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
        try {
            fo.write(data);
            fo.flush();
            try { fo.getFD().sync(); } catch (Throwable t) {}
        } finally { try { fo.close(); } catch (Throwable t) {} }
        if (!tmp.renameTo(f)) {
            try { tmp.delete(); } catch (Throwable t) {}
            throw new Exception("rename fail: " + f);
        }
    }

    /** S05：内核缓存唯一安装入口——密文与 sha 成对提交，手动/自动共用，外部加锁。 */
    static final Object CACHE_LOCK = new Object();

    

    static byte[] loadKernelBytes() {
        byte[] cached = readVerifiedCache();
        if (cached != null) { log("kernel from cache"); return cached; }
        if (!ensureKernel()) return null;
        // ensureKernel 成功时已经验过哈希，这里再读一次，保证返回的就是那份验过的字节
        return readVerifiedCache();
    }

    /** 启动后后台比对一次清单；有新版就悄悄替换（下次启动生效） */
    static void checkUpdateLater() {
        new Thread(new Runnable() { public void run() {
            try { Thread.sleep(8000); refreshKernel(); }
            catch (Throwable t) { log("channel update " + t.getClass().getSimpleName()); }
        }}, "wxentry-channel-update").start();
    }

    /** 许愿：携带本机授权凭证，服务端验签后才收，wxid 由服务端从凭证里取。 */
    public static String postWish(String text) {
        try {
            String lic = readText(LIC);
            String token = lic == null ? null : line(lic, "token=");
            if (token == null || token.length() < 20) return "请先激活模块再许愿";
            String body = "{\"token\":\"" + token + "\",\"text\":\"" + jesc(text) + "\"}";
            String resp = http(URL_WISH, body);
            log("wish resp ok=" + jsonStr(resp, "ok"));
            String msg = jsonStr(resp, "msg");
            String ok = jsonStr(resp, "ok");
            if (msg == null) return "提交失败，稍后再试";
            return msg;
        } catch (Throwable t) {
            log("wish err " + t);
            return "提交失败：" + t;
        }
    }

    static String jesc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') continue;
            else if (c < 0x20) continue;
            else sb.append(c);
        }
        return sb.toString();
    }

    // ---------------- 1.43：模块 APK 自更新 ----------------

    /** 后台比对 apk.json；每天最多查一次。有新版就写 apk.pending（v|name|sha|url） */
    static void checkApkUpdateLater() {
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(12000); } catch (Throwable t) {}
                try {
                    File ck = new File(APK_CHECKED);
                    long now = System.currentTimeMillis();
                    try { cleanApkPending(); } catch (Throwable t) {}
                    if (ck.exists() && now - ck.lastModified() < DAY_MS) return;
                    String man = http(URL_APK_MANIFEST, null);
                    String v = jsonStr(man, "v"), name = jsonStr(man, "name");
                    String sha = jsonStr(man, "sha256"), sig = jsonStr(man, "sig");
                    if (v == null || name == null || sha == null || sig == null) { log("apk manifest empty"); return; }
                    if (!verifySig(v + "|" + name + "|" + sha, sig)) { log("apk manifest bad sig"); return; }
                    int sv = 0;
                    try { sv = Integer.parseInt(v.trim()); } catch (Throwable t) {}
                    if (sv <= MY_VC) {
                        try { new File(APK_PENDING).delete(); } catch (Throwable t) {}
                    } else {
                        writeText(APK_PENDING, sv + "|" + name + "|" + sha + "|" + URL_APK);
                        log("apk update available v" + sv + " (" + name + ")");
                    }
                    try {
                        if (!ck.exists()) ck.createNewFile();
                        ck.setLastModified(now);
                    } catch (Throwable t) {}
                } catch (Throwable t) { log("apk update check skip: " + t); }
            }
        }).start();
    }

    /** 1.57：apk.pending 里记的版本 <= 当前已装版本时直接删掉。
     *  以前只在「今天还没查过」时才会重算，装完新版后 pending 会一直挂着，菜单一直显示旧版本可更新。 */
    static void cleanApkPending() {
        try {
            String t = readText(APK_PENDING);
            if (t == null || t.trim().length() == 0) return;
            String v = t.trim().split("\\|")[0].trim();
            int sv = Integer.parseInt(v);
            if (sv <= MY_VC) {
                new File(APK_PENDING).delete();
                log("apk.pending stale v" + sv + " cleared (installed " + MY_VC + ")");
            }
        } catch (Throwable t) {}
    }

    /** 菜单用：有新模块版本时返回版本名（如 1.44），否则 null */
    public static String apkPendingName() {
        try {
            String t = readText(APK_PENDING);
            if (t == null || t.trim().length() == 0) return null;
            String[] p = t.trim().split("\\|");
            try { if (Integer.parseInt(p[0].trim()) <= MY_VC) return null; } catch (Throwable t2) {}
            String name = p.length > 1 ? p[1] : p[0];
            return name == null || name.length() == 0 ? null : name;
        } catch (Throwable t) { return null; }
    }

    /** 菜单顶部显示用 */
    public static String myVersionName() { return MY_VN; }

    /** 用户点「模块可更新」后调用：系统 DownloadManager 下载，失败退回浏览器 */
    public static String installApkUpdate(android.content.Context c) {
        String t = readText(APK_PENDING);
        if (t == null) return "没有待更新的模块版本";
        String[] p = t.trim().split("\\|");
        String name = p.length > 1 ? p[1] : (p.length > 0 ? p[0] : "");
        String url = p.length > 3 ? p[3] : URL_APK;
        try {
            Class<?> uriC = Class.forName("android.net.Uri");
            Object uri = uriC.getMethod("parse", String.class).invoke(null, url);
            Object dm = c.getClass().getMethod("getSystemService", String.class).invoke(c, "download");
            Class<?> reqC = Class.forName("android.app.DownloadManager$Request");
            Object req = reqC.getConstructor(uriC).newInstance(uri);
            reqC.getMethod("setTitle", CharSequence.class).invoke(req, "wxentry " + name);
            reqC.getMethod("setDescription", CharSequence.class).invoke(req, "下载完成后点通知安装");
            reqC.getMethod("setMimeType", String.class).invoke(req, "application/vnd.android.package-archive");
            reqC.getMethod("setNotificationVisibility", int.class).invoke(req, Integer.valueOf(1));
            Class<?> dmC = Class.forName("android.app.DownloadManager");
            dmC.getMethod("enqueue", reqC).invoke(dm, req);
            log("apk download enqueued " + name);
            return "已开始下载模块 " + name + "，完成后点通知安装";
        } catch (Throwable e) {
            log("dm err " + e);
            try {
                Class<?> uriC = Class.forName("android.net.Uri");
                Object uri = uriC.getMethod("parse", String.class).invoke(null, url);
                Class<?> intentC = Class.forName("android.content.Intent");
                Object i = intentC.getConstructor().newInstance();
                intentC.getMethod("setAction", String.class).invoke(i, "android.intent.action.VIEW");
                intentC.getMethod("setData", uriC).invoke(i, uri);
                intentC.getMethod("addFlags", int.class).invoke(i, Integer.valueOf(0x10000000));
                c.getClass().getMethod("startActivity", intentC).invoke(c, i);
                log("apk download via browser " + name);
                return "已打开浏览器下载模块 " + name;
            } catch (Throwable e2) {
                log("browser err " + e2);
                return "下载失败，请到 github.com/vividbeacon43 更新";
            }
        }
    }

    /** 主页横幅用：有新版本时返回版本号，否则 null */
    public static String pendingVersion() {
        String target = currentKernelEdition();
        return target.equals(RUNNING_EDITION) ? null : localVersion() + " · " + EditionPolicy.name(target);
    }

    /** 用户点横幅后执行下载，返回给他看的文案 */
    public static String installUpdate() { return checkUpdateNow(); }


    /** 只查服务器版本号（不下载），失败返回 null */
    public static String serverVersion() {
        try { return DELIVERY.probe(licField("token")).version; }
        catch (Throwable t) { return null; }
    }

    /** 当前本地内核版本 */
    public static String localVersion() {
        try { String s = readText(DIR + "kernel.ver"); return s == null ? "?" : s.trim(); } catch (Throwable t) { return "?"; }
    }

    /** 手动检查更新：每天最多一次；发现服务器有新版本时不占当天的次数。返回给用户看的文案。 */
    public static String checkUpdateNow() {
        try {
            KernelDelivery.Result result=refreshKernel();
            try {
                if(!kernelMenuReady()&&APP_CTX_REF instanceof android.content.Context) {
                    String wx=((android.content.Context)APP_CTX_REF).getPackageManager().getPackageInfo("com.tencent.mm",0).versionName;
                    if(!"8.0.80".equals(wx))return "内核已下载，当前微信 "+wx+"；本内核适配微信 8.0.80，请先更新微信";
                }
            }catch(Throwable unavailable) { }
            return kernelUpdateMessage(result);
        }
        catch (Throwable t) {
            String detail=t.getClass().getSimpleName();Throwable cause=t.getCause();if(cause!=null)detail+="/"+cause.getClass().getSimpleName();
            String message=t.getMessage();if(message!=null)detail+=" "+message.replace('\n',' ').replace('\r',' ');
            log("manual kernel update failed "+detail.substring(0,Math.min(detail.length(),400)));
            if(t instanceof KernelDelivery.AuthorizationDenied)return "授权状态校验失败，请重新激活或上报日志";
            return "版本更新失败，已记录具体原因，请上报日志";
        }
    }

    static String humanLeft(long ms) {
        long m = ms / 60000L;
        if (m >= 60) return (m / 60) + " 小时";
        return (m < 1 ? 1 : m) + " 分钟";
    }

    static boolean ensureKernel() {
        try { refreshKernel(); return readVerifiedCache() != null; }
        catch (Throwable t) { log("channel fetch " + t.getClass().getSimpleName()); return readVerifiedCache() != null; }
    }


    static boolean verifySig(String data, String sigB64) {
        try {
            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(publicKey());
            v.update(data.getBytes("UTF-8"));
            return v.verify(b64d(sigB64));
        } catch (Throwable t) { log("verifySig err " + t); return false; }
    }


    /** 激活完成后的收尾：把当前界面重建一次，让"+"菜单和各钩子立刻生效，不用用户手动重启。 */
    static void afterActivated(final Activity act) {
        try {
            Toast.makeText(act, "激活成功，功能已开启\n长按首页右上角 + 打开功能菜单", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {}
        new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(1200); } catch (Throwable t) {}
                try {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                java.lang.reflect.Method m = Activity.class.getMethod("recreate");
                                m.invoke(act);
                            } catch (Throwable t) { log("recreate err " + t); }                        }
                    });
                } catch (Throwable t) {}
            }
        }).start();
    }

    /** 1.54：内嵌内核优先用「带 Xposed 父级」的 loader，实在不行再退回 mainCl */
    static Class<?> loadEmbeddedKernel() {
        try {
            ClassLoader mainCl = Main.class.getClassLoader();
            ClassLoader host = mainCl;
            ClassLoader[] cands = new ClassLoader[] { mainCl,
                    Thread.currentThread().getContextClassLoader() };
            for (ClassLoader c : cands) {
                if (c == null) continue;
                try { Class.forName(xposedBridgeName(), false, c); host = c; break; } catch (Throwable t) {}
            }
            final ClassLoader h = host;
            ClassLoader f = new ClassLoader(h) {
                protected Class<?> loadClass(String n, boolean r) throws ClassNotFoundException {
                    if (n != null && n.startsWith("com.zafiro.wxentry.kernel.")) throw new ClassNotFoundException(n);
                    if (n != null && n.startsWith("com.zafiro.wxentry."))
                        return Main.class.getClassLoader().loadClass(n);
                    return super.loadClass(n, r);
                }
            };
            byte[] emb = runtimeDex(readApkEntry("classes2.dex"));
            if (emb != null && emb.length > 1000) {
                // 1.63：这里以前用的是 h，过滤器 f 造了却没用 —— 父 loader 先把 Kernel 类交出去的话，
                // 拿到的可能不是这份内嵌 dex 里的类。统一用 f（只挡 com.zafiro.wxentry.kernel.*）
                ClassLoader d2 = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(emb), f);
                Class<?> k = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, d2);
                try {
                    Class.forName(xposedBridgeName(), false, k.getClassLoader());
                    log("embedded kernel ok (xposed visible)");
                } catch (Throwable x) { log("embedded kernel loaded but xposed NOT visible: " + x); }
                return k;
            }
        } catch (Throwable t) { log("loadEmbeddedKernel err " + t); }
        return null;
    }

    /** 1.54：从模块 APK 里读一个 dex 条目（内嵌内核 classes2.dex） */
    static byte[] readApkEntry(String name) {
        try {
            // 1.63：只认模块自己的 APK。以前用 APP_CTX_REF（微信 Activity）的 getPackageCodePath，
            // 拿到的是**微信**的 base.apk，读出来的 classes2.dex 根本不是我们的内核。
            String apk = moduleApk();
            if (apk == null) { log("readApkEntry: module apk unknown"); return null; }
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile(apk);
            try {
                java.util.zip.ZipEntry e = zf.getEntry(name);
                if (e == null) return null;
                java.io.InputStream in = zf.getInputStream(e);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                return bo.toByteArray();
            } finally { try { zf.close(); } catch (Throwable t) {} }
        } catch (Throwable t) { log("readApkEntry err " + t); return null; }
    }

    /** S09：宿主构建白名单放到 Loader 层——动态内核、内嵌内核、加载失败回退都先过这一关。 */
    static int wxBuildNo(Context c) {
        try {
            Object ai = XposedHelpers.callMethod(c.getPackageManager(), "getApplicationInfo",
                    "com.tencent.mm", Integer.valueOf(0));
            String dataDir = (String) XposedHelpers.getObjectField(ai, "dataDir");
            String x = readText(dataDir + "/shared_prefs/com.tencent.mm_preferences.xml");
            if (x == null) return 0;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("last_reportdevice_clientversion\"[^>]*?value=\"([^\"]+)\"").matcher(x);
            if (!m.find()) return 0;
            String v = m.group(1).trim();
            if (v.startsWith("0x") || v.startsWith("0X")) return (int) Long.parseLong(v.substring(2), 16);
            return (int) Long.parseLong(v);
        } catch (Throwable t) { return 0; }
    }

    static boolean wxBuildOk(Context c) {
        // 1.85：取消 503x 构建号闸门。新构建（5035+）不再被 loader 拦下，
        // 版本兼容交给内核按 versionName / versionCode 判断，出问题靠 hook-zero 上报告警。
        return true;
    }

    static void bootKernel(Activity fallback, byte[] dexBytes) {
        Activity latest = RECOVERY_ACTIVITY == null ? null : RECOVERY_ACTIVITY.get();
        final Activity act = latest != null && !latest.isFinishing() && !latest.isDestroyed() ? latest : fallback;
        KERNEL_BOOT_STATE = "loading";
        try {
            // S09：不论动态/内嵌/回退，先过宿主构建闸门，避免旧内嵌内核绕过内核侧检查
            if (act != null && !wxBuildOk(act)) {
                KERNEL_BOOT_STATE = "unsupported-build";
                log("loader: wx build unsupported (0x" + Integer.toHexString(wxBuildNo(act)) + ") -> no hooks");
                act.runOnUiThread(new Runnable() {
                    public void run() { Toast.makeText(act, "暂不支持当前微信构建，请等待模块适配", Toast.LENGTH_LONG).show(); }
                });
                return;
            }
            // 1.55：readApkEntry 需要 Context 读模块 APK，这里先把 Context 记上（以前是在内核加载之后才赋值）
            try { if (APP_CTX_REF == null && act != null) APP_CTX_REF = act; } catch (Throwable t) {}
            // 1.54：先挑一个「看得见 de.robv.android.xposed.*」的父 ClassLoader。
            // 上报里大量 hook-zero 就是内嵌内核挂在模块自己的 PathClassLoader 上，
            // 那个 loader 在 Android 16/17 + 新 LSPosed 上解析不到 XposedBridge，内核能加载但钩子全抛 NoClassDefFoundError。
            ClassLoader mainCl = Main.class.getClassLoader();
            ClassLoader xposedHost = null;
            String hostName = "main";
            ClassLoader[] hostCands = new ClassLoader[] { mainCl, XposedBridge.class.getClassLoader(),
                    (act == null ? null : act.getClass().getClassLoader()),
                    Thread.currentThread().getContextClassLoader(),
                    ClassLoader.getSystemClassLoader(),
                    (mainCl == null ? null : mainCl.getParent()),
                    (act == null || act.getClass().getClassLoader() == null ? null : act.getClass().getClassLoader().getParent()) };
            String[] hostNames = new String[] { "main", "api", "app", "thread", "system", "main.parent", "app.parent" };
            // 1.58：逐个记下失败原因 —— 上报里 25 台设备四个候选全失败（xposed host=main(noxp)），
            // 不打原因就没法知道到底是「类不在」还是「loader 挡了」
            for (int i = 0; i < hostCands.length; i++) {
                ClassLoader c = hostCands[i];
                if (c == null) { log("xhost " + hostNames[i] + " = null"); continue; }
                try {
                    Class<?> xb2 = Class.forName(xposedBridgeName(), false, c);
                    log("xhost " + hostNames[i] + " (" + c.getClass().getSimpleName() + ") ok -> "
                            + (xb2.getClassLoader() == null ? "boot" : xb2.getClassLoader().getClass().getSimpleName()));
                    if (xposedHost == null) { xposedHost = c; hostName = hostNames[i]; }
                } catch (Throwable t) {
                    log("xhost " + hostNames[i] + " (" + c.getClass().getSimpleName() + ") fail: " + t);
                }
            }
            if (xposedHost == null) { xposedHost = mainCl; hostName = "main(noxp)"; }
            log("xposed host=" + hostName + " (" + (xposedHost == null ? "null" : xposedHost.getClass().getSimpleName()) + ")");
            final ClassLoader host = xposedHost;
            Class<?> kernelClass = null;
            ClassLoader dcl = null;
            String picked = "none";
            // 父级挡住 com.zafiro.wxentry.kernel.*，否则「父优先」会命中 APK 内嵌的那份，热更新永远用不上
            ClassLoader filter = new ClassLoader(host) {
                protected Class<?> loadClass(String n, boolean r) throws ClassNotFoundException {
                    if (n != null && n.startsWith("com.zafiro.wxentry.kernel.")) throw new ClassNotFoundException(n);
                    if (n != null && n.startsWith("com.zafiro.wxentry."))
                        return Main.class.getClassLoader().loadClass(n);
                    return super.loadClass(n, r);
                }
            };
            dexBytes = runtimeDex(dexBytes);
            // 1.25：先试「动态内核」（保留热更新能力）；能解析 Xposed API 才用它
            try {
                if (dexBytes == null) throw new ClassNotFoundException("no server kernel");
                ClassLoader dyn = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(dexBytes), filter);
                Class<?> kd = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, dyn);
                Class.forName(xposedBridgeName(), false, dyn);   // 动态内核必须能看见 Xposed
                kernelClass = kd;
                picked = "dynamic(server)";
            } catch (Throwable t) {
                log("dynamic(server) fail: " + t);
                Throwable cause=t.getCause();if(cause!=null)log("dynamic cause: "+cause);
                for(Throwable suppressed:t.getSuppressed())log("dynamic detail: "+suppressed);
            }
            if (kernelClass == null) {
                // 1.54：内嵌内核也走同一条路——从 APK 里读 classes2.dex 的字节，用带 Xposed 父级的
                // InMemoryDexClassLoader 加载，而不是直接 Class.forName(..., mainCl)。
                try {
                    byte[] emb = runtimeDex(readApkEntry("classes2.dex"));
                    if (emb != null && emb.length > 1000) {
                        // 注意：这里不能用 filter —— 它会拦 com.zafiro.wxentry.kernel.*，
                        // 而这个 loader 只有我们这一份 dex，拦了就没地方找（1.55 就栽在这）
                        ClassLoader d2 = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(emb), host);
                        Class<?> kd2 = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, d2);
                        Class.forName(xposedBridgeName(), false, d2);
                        kernelClass = kd2;
                        picked = "embedded(classes2.dex,host)";
                    } else {
                        log("embedded bytes missing");
                    }
                } catch (Throwable t) {
                    log("embedded(host) fail: " + t);
                }
            }
            if (kernelClass == null) {
                try {
                    kernelClass = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, mainCl);
                    picked = "embedded(classes2.dex)";
                } catch (Throwable t) {
                    log("no embedded kernel: " + t);
                }
            }
            if (kernelClass == null) {
                // 老路径：动态加载（1.23 的 addDexPath，其次 1.20 的多候选）
                if (!DEX_PATCHED && dexBytes != null) {
                    try {
                        File kf = new File(DIR + "kernel.rt.dex");
                        writeAll(kf, dexBytes);
                        try { kf.setReadOnly(); } catch (Throwable t) {}
                        Object pathList = null;
                        for (Class<?> c = mainCl.getClass(); c != null && pathList == null; c = c.getSuperclass()) {
                            try {
                                java.lang.reflect.Field f = c.getDeclaredField("pathList");
                                f.setAccessible(true);
                                pathList = f.get(mainCl);
                            } catch (Throwable t) {}
                        }
                        if (pathList != null) {
                            java.lang.reflect.Method add = null;
                            for (java.lang.reflect.Method m : pathList.getClass().getDeclaredMethods()) {
                                if (!"addDexPath".equals(m.getName())) continue;
                                try { m.setAccessible(true); } catch (Throwable t) {}
                                if (m.getParameterTypes().length == 1) { add = m; break; }
                                if (add == null) add = m;
                            }
                            if (add != null) {
                                Object[] args = new Object[add.getParameterTypes().length];
                                args[0] = kf.getAbsolutePath();
                                add.invoke(pathList, args);
                                DEX_PATCHED = true;
                                kernelClass = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, mainCl);
                                picked = "module(addDexPath)";
                                log("dex appended into module loader");
                            }
                        }
                    } catch (Throwable t) { log("module-loader route fail: " + t); }
                }
                if (kernelClass == null && dexBytes != null) {
                    ClassLoader xbCl = null;
                    try { xbCl = de.robv.android.xposed.XposedBridge.class.getClassLoader(); } catch (Throwable t) {}
                    ClassLoader appCl = null;
                    try { appCl = act.getClass().getClassLoader(); } catch (Throwable t) {}
                    ClassLoader xbPar = null;
                    try { xbPar = xbCl == null ? null : xbCl.getParent(); } catch (Throwable t) {}
                    ClassLoader ctxCl = null;
                    try { ctxCl = Thread.currentThread().getContextClassLoader(); } catch (Throwable t) {}
                    ClassLoader[] cands = new ClassLoader[] { mainCl, xbCl, xbPar, appCl, ctxCl, ClassLoader.getSystemClassLoader() };
                    String[] names = new String[] { "main", "xposed", "xposed.parent", "app", "thread", "system" };
                    for (int i = 0; i < cands.length && dcl == null; i++) {
                        ClassLoader c = cands[i];
                        if (c == null) continue;
                        try {
                            ClassLoader test = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(dexBytes), c);
                            Class.forName(xposedBridgeName(), false, test);
                            dcl = test; picked = names[i];
                        } catch (Throwable t) { log("cand " + names[i] + " fail: " + t); }
                    }
                    if (dcl == null) {
                        dcl = new dalvik.system.InMemoryDexClassLoader(java.nio.ByteBuffer.wrap(dexBytes), mainCl);
                        picked = "main(fallback2)";
                    }
                    kernelClass = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, dcl);
                }
            }
            KERNEL_DYNAMIC_OK = (kernelClass != null && picked != null && picked.startsWith("dynamic"));
            log("dex parent=" + picked);
            Class<?> k = kernelClass;
            rememberRunningKernel(k);
            ClassLoader live = WX_CL;
            try {
                ClassLoader l = act.getClass().getClassLoader();
                if (l != null) live = l;
            } catch (Throwable t) {}
            try { log("kernel cl=" + (k == null || k.getClassLoader() == null ? "boot" : k.getClassLoader().getClass().getName())
                    + " | app cl=" + (live == null ? "null" : live.getClass().getSimpleName())); } catch (Throwable t) {}
            try { APP_CTX_REF = act; } catch (Throwable t) {}
            long tlen = 0;
            try { tlen = new File(DIR + "trace.log").length(); } catch (Throwable t) {}
            try {
                k.getMethod("boot", ClassLoader.class, Object.class).invoke(null, live, act);
            } catch (Throwable bt) {
                if (!picked.startsWith("dynamic")) throw bt;      // 内嵌都起不来 → 交给外层 boot fail
                // 1.45：动态内核"能加载但 boot 抛异常"（微信换类、内核自身 bug）→ 当场换成 APK 内嵌内核，
                // 不再让整个会话没模块，也不再依赖"下次启动回滚"。
                KERNEL_DYNAMIC_OK = false;
                Throwable cause = bt;
                while (cause.getCause() != null) cause = cause.getCause();
                log("dynamic boot fail -> embedded: " + cause);
                try {
                    String badSha = readText(KERNEL_SHA);
                    if (badSha != null && badSha.trim().length() > 10)
                        writeText(KERNEL_BAD, badSha.trim() + "|" + System.currentTimeMillis());
                    new File(KERNEL_ENC).delete();
                    new File(KERNEL_SHA).delete();
                } catch (Throwable x) { log("bad mark err " + x); }
                Class<?> emb = loadEmbeddedKernel();
                if (emb == null) emb = Class.forName("com.zafiro.wxentry.kernel.Kernel", true, mainCl);
                rememberRunningKernel(emb);
                emb.getMethod("boot", ClassLoader.class, Object.class).invoke(null, live, act);
                log("kernel fallback -> embedded");
                try {
                    File ts = new File(DIR + "kernel.fallback.ts");
                    long nowMs = System.currentTimeMillis();
                    if (!ts.exists() || nowMs - ts.lastModified() > 24L * 3600 * 1000) {
                        if (ts.exists()) ts.setLastModified(nowMs); else ts.createNewFile();
                        uploadDiag("kernel-fail:fallback-embedded");
                    }
                } catch (Throwable x) {}
            }
            KERNEL_BOOT_STATE = MenuEntryDispatch.readiness(RUNNING_KERNEL_CLASS);
            log("kernel state=" + KERNEL_BOOT_STATE + " edition=" + RUNNING_EDITION);
            // 1.45：只有动态内核自己跑起来才算"这个 sha 没问题"，退回内嵌时不能清坏标记
            try { if (KERNEL_DYNAMIC_OK && kernelMenuReady()) { new File(KERNEL_BAD).delete(); new File(DIR + "heal.ver").delete(); } } catch (Throwable t) {}
            // 1.22：每天一次「开机心跳」，让后台能看到每台设备的真实状态（走 selftest 通道，不占可信额度）
            try {
                File hb = new File(DIR + "heartbeat");
                long nowMs = System.currentTimeMillis();
                if (!hb.exists() || nowMs - hb.lastModified() > 24L * 3600 * 1000) {
                    if (hb.exists()) hb.setLastModified(nowMs); else hb.createNewFile();
                    log("heartbeat upload");
                    uploadDiag(kernelMenuReady() ? "selftest:boot-ok" :
                        "legacy-unconfirmed".equals(KERNEL_BOOT_STATE) ? "selftest:legacy-kernel" : "selftest:kernel-not-ready");
                }
            } catch (Throwable t) { log("heartbeat err " + t); }
            // 1.21：内核“启动成功”但 hook 全挂的情况以前不会上报（boot 没抛异常）。
            // 这里看一眼这次启动新写的 trace，如果里面有 NoClassDefFound / hook fail，就自动传一份。
            try {
                String fresh = readFrom(DIR + "trace.log", tlen);
                // 1.28：去掉 "cl probe fail" —— 隔离设备上动态 dex 探不到 XposedBridge 是正常现象，
                // 内嵌内核照常工作，以前每启动一次就误报一次 hook-zero。
                if (fresh != null && (fresh.contains("NoClassDefFound") || fresh.contains("hook fail")
                        || fresh.contains("BOOT CRASH"))) {
                    log("hook-fail detected, uploading diag");
                    uploadDiag("kernel-fail:hook-zero");
                }
            } catch (Throwable t) { log("hookcheck err " + t); }
            bootMark(kernelMenuReady() ? "ready" : KERNEL_BOOT_STATE);
            // 联调钩子：WechatXposed/diag.test 里写一个 reason，下次启动按它上报一次（读完即删）
            try {
                String tst = readText(DIR + "diag.test");
                if (tst != null && tst.trim().length() > 3) {
                    final String tr = tst.trim();
                    new File(DIR + "diag.test").delete();
                    log("diag test trigger: " + tr);
                    uploadDiag(tr);
                }
            } catch (Throwable t) {}
            if (PREV_FAIL != null) uploadDiag("prev-boot:" + PREV_FAIL);
        } catch (Throwable t) {
            KERNEL_BOOT_STATE = "failed";
            log("boot fail " + t);
            // 1.18：把 InvocationTargetException 里面的真正原因也记下来
            Throwable c = t.getCause(); int ci = 0;
            while (c != null && ci < 3) { log("  cause" + ci + ": " + c); c = c.getCause(); ci++; }
            bootMark("bootfail " + t);
            // 1.18：自愈——内核启动失败就丢掉本地缓存，下次启动重新拉服务器上的最新内核（同一内核版本只试一次）
            try {
                String kv = readText(DIR + "kernel.ver");
                String curSha = readText(KERNEL_SHA);
                // 1.45：记成坏内核（7 天内不再自动拉），并删掉缓存，避免同一份坏内核反复启动失败
                if (curSha != null && curSha.trim().length() > 10)
                    writeText(KERNEL_BAD, curSha.trim() + "|" + System.currentTimeMillis());
                new File(KERNEL_ENC).delete();
                new File(KERNEL_SHA).delete();
                try { new File(DIR + "kernel.enc.bak").delete(); new File(DIR + "kernel.sha.bak").delete(); } catch (Throwable x) {}
                log("self-heal: bad kernel v" + kv + " dropped");
            } catch (Throwable x) { log("heal err " + x); }
            uploadDiag("boot-fail:" + t);
        }
    }

    // ---------------- HTTP ----------------
    static String http(String url, String body) throws Exception {
        byte[] r = body == null ? httpBytes(url) : httpPost(url, body);
        return r == null ? null : new String(r, "UTF-8");
    }

    static byte[] httpBytes(String url) throws Exception {
        if (url.startsWith("http://")) return rawHttp(url, null);
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "wxentry");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] data = readAll(in);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + new String(data, "UTF-8"));
        return data;
    }

    static String httpPostHdr(String url, String body, String hk, String hv) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("User-Agent", "wxentry/" + APP_VER);
        try { c.setRequestProperty(hk, hv); } catch (Throwable t) {}
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] data = readAll(in);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + new String(data, "UTF-8"));
        return new String(data, "UTF-8");
    }

    static byte[] httpPost(String url, String body) throws Exception {
        if (url.startsWith("http://")) return rawHttp(url, body);
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("User-Agent", "wxentry");
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] data = readAll(in);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + new String(data, "UTF-8"));
        return data;
    }

    /** 仅用于本地调试（http://）——绕过 App 的明文流量限制 */
    static byte[] rawHttp(String url, String body) throws Exception {
        java.net.URL u = new java.net.URL(url);
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 80;
        String path = u.getFile();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 8000);
        s.setSoTimeout(20000);
        OutputStream os = s.getOutputStream();
        StringBuilder h = new StringBuilder();
        h.append(body == null ? "GET " : "POST ").append(path).append(" HTTP/1.1\r\n");
        h.append("Host: ").append(host).append("\r\n");
        h.append("Connection: close\r\n");
        h.append("User-Agent: wxentry\r\n");
        if (body != null) {
            h.append("Content-Type: application/json; charset=utf-8\r\n");
            h.append("Content-Length: ").append(body.getBytes("UTF-8").length).append("\r\n");
        }
        h.append("\r\n");
        os.write(h.toString().getBytes("UTF-8"));
        if (body != null) os.write(body.getBytes("UTF-8"));
        os.flush();
        byte[] all = readAll(s.getInputStream());
        s.close();
        // 找 \r\n\r\n
        int sep = -1;
        for (int i = 0; i + 3 < all.length; i++) {
            if (all[i] == 13 && all[i + 1] == 10 && all[i + 2] == 13 && all[i + 3] == 10) { sep = i; break; }
        }
        if (sep < 0) throw new Exception("bad http response");
        String head = new String(all, 0, sep, "UTF-8");
        if (head.indexOf(" 200 ") < 0) throw new Exception("HTTP " + head.split("\r\n")[0]);
        int bs = sep + 4;
        byte[] payload = new byte[all.length - bs];
        System.arraycopy(all, bs, payload, 0, payload.length);
        return payload;
    }

    // ---------------- 工具 ----------------
    /** 1.63：替换掉原来的 indexOf/substring 取字段。原实现遇到 "请点\"重试\"按钮"
     *  这类合法转义会截断，也可能把后面的字段名当成值。
     *  这里按 JSON 语法走一遍：字符串解析转义，嵌套对象/数组整段跳过，只取顶层字段。 */
    static String jsonStr(String json, String key) {
        if (json == null) return null;
        try {
            int i = 0, n = json.length();
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n || json.charAt(i) != '{') return jsonStrLegacy(json, key);
            i++;
            while (i < n) {
                while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
                if (i >= n || json.charAt(i) == '}') break;
                if (json.charAt(i) != '"') return jsonStrLegacy(json, key);
                StringBuilder kb = new StringBuilder();
                i = jsonReadString(json, i, kb);
                while (i < n && Character.isWhitespace(json.charAt(i))) i++;
                if (i >= n || json.charAt(i) != ':') return jsonStrLegacy(json, key);
                i++;
                while (i < n && Character.isWhitespace(json.charAt(i))) i++;
                if (i >= n) break;
                char c = json.charAt(i);
                if (c == '"') {
                    StringBuilder vb = new StringBuilder();
                    i = jsonReadString(json, i, vb);
                    if (kb.toString().equals(key)) return vb.toString();
                } else if (c == '{' || c == '[') {
                    i = jsonSkipValue(json, i);
                    if (kb.toString().equals(key)) return null;
                } else {
                    int st = i;
                    while (i < n && ",}] \t\r\n".indexOf(json.charAt(i)) < 0) i++;
                    if (kb.toString().equals(key)) {
                        String raw = json.substring(st, i);
                        return "null".equals(raw) ? null : raw;
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return jsonStrLegacy(json, key);
        }
    }

    /** 从 i（指向开头的引号）读一个 JSON 字符串，返回结束后一位。 */
    static int jsonReadString(String s, int i, StringBuilder out) throws Exception {
        int n = s.length();
        i++;                                     // 跳过开头引号
        while (i < n) {
            char c = s.charAt(i++);
            if (c == '"') return i;
            if (c != '\\') { out.append(c); continue; }
            if (i >= n) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    if (i + 4 <= n) {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    break;
                default: out.append(e);          // \" \\ \/ 以及未知转义
            }
        }
        return i;
    }

    /** 跳过一个嵌套对象/数组（i 指向 { 或 [），返回结束后一位。 */
    static int jsonSkipValue(String s, int i) throws Exception {
        int depth = 0, n = s.length();
        boolean inStr = false;
        while (i < n) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') { i += 2; continue; }
                if (c == '"') inStr = false;
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) return i + 1;
            }
            i++;
        }
        return i;
    }

    /** 老的非严格解析，只作为非对象输入时的兜底。 */
    static String jsonStrLegacy(String json, String key) {
        if (json == null) return null;
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return null;
        int q = json.indexOf('"', c + 1);
        if (q < 0) return null;
        int e = json.indexOf('"', q + 1);
        if (e < 0) return null;
        return json.substring(q + 1, e).replace("\\/", "/");
    }

    static String line(String text, String prefix) {
        for (String l : text.split("\n")) if (l.startsWith(prefix)) return l.substring(prefix.length()).trim();
        return null;
    }

    static String sha256Hex(byte[] d) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(d)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String b32(byte[] data, int chars) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte b : data) {
            buf = (buf << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5 && sb.length() < chars) {
                sb.append(B32.charAt((buf >> (bits - 5)) & 31));
                bits -= 5;
            }
            if (sb.length() >= chars) break;
        }
        while (sb.length() < chars) sb.append('A');
        return sb.toString();
    }

    static String b64e(byte[] d) { return android.util.Base64.encodeToString(d, android.util.Base64.NO_WRAP); }
    static byte[] b64d(String s) { return android.util.Base64.decode(s, android.util.Base64.DEFAULT); }

    static byte[] readAll(File f) throws Exception { return readAll(new FileInputStream(f)); }

    static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    static String readText(String path) {
        try { return new String(readAll(new File(path)), "UTF-8"); } catch (Throwable t) { return null; }
    }

    static void writeText(String path, String s) {
        try {
            File f = new File(path);
            f.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(s.getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) { log("write err " + t); }
    }

    static void writeAll(File f, byte[] d) throws Exception {
        f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(d);
        o.close();
    }

    static void log(String msg) {
        try { XposedBridge.log("WXL: " + msg); } catch (Throwable t) {}
        try {
            File f = new File(DIR + "loader.log");
            f.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(f, true);
            o.write((new java.text.SimpleDateFormat("MM-dd HH:mm:ss").format(new java.util.Date())
                    + "  " + msg + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable t) {}
    }
}
