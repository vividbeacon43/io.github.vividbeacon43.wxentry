package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Kernel {
    private static volatile boolean MENU_READY;
    private static long MENU_SHOW_COUNT;
    public static boolean isMenuReady() { return MENU_READY && !MODULE_OFF; }
    public static boolean openRecoveredMenu(Activity act) {
        if (!isMenuReady() || act == null || act.isFinishing() || act.isDestroyed()) return false;
        long before = MENU_SHOW_COUNT;
        showFeatureMenu(act);
        return MENU_SHOW_COUNT > before;
    }

    static final String BUILD = "260";
    public static final String EDITION = "free";
    static final boolean RP_PREMIUM_EDITION = false;
    static final boolean TABLET_PREMIUM_EDITION = false;
    static volatile long LAST_PLUS_MS = 0;
    static volatile java.lang.ref.WeakReference<android.view.View> LAST_PLUS_VIEW = null;   // 内核内部版本号，写进 kernel.ver
    static volatile ClassLoader APP_CL = null;
    private static boolean coreHooked = false;
    private static final String SVC_LOCATOR = "zp0.j1";              // static v(Class) -> eq0.a
    private static final String SVC_IFACE   = "gp3.c4";              // interface class
    /** v114：分身（user 999）下同样是 /data/user/<id>/… */
    static String computeWxdir() {
        try {
            int uid = ((Integer) Class.forName("android.os.Process").getMethod("myUid").invoke(null)).intValue();
            int user = uid / 100000;
            if (user != 0) return "/data/user/" + user + "/com.tencent.mm/files/WechatXposed/";
        } catch (Throwable t) {}
        return "/data/data/com.tencent.mm/files/WechatXposed/";
    }
    static String computeWxData() {
        String d = computeWxdir();
        int i = d.indexOf("/files/");
        return i > 0 ? d.substring(0, i) : "/data/data/com.tencent.mm";
    }
    static final String WXDIR = computeWxdir();
    static final String ORIG_OFF_PATH = WXDIR + "origphoto.off";
    static final String AR_OFF_PATH = WXDIR + "antirevoke.off";
    static final String RP_ON_PATH = WXDIR + "redpacket.on";
    static final String RP_MODE_PATH = WXDIR + "redpacket.mode";
    static final String RP_WL_PATH = WXDIR + "redpacket.whitelist";
    static final String RP_DELAY_PATH = WXDIR + "redpacket.delay";
    static final String RP_TOAST_OFF_PATH = WXDIR + "redpacket.toast.off";
    static final String NEWROOM_ON_PATH = WXDIR + "newroom.mute.on";
    static final String NEWROOM_NOTIFY_PATH = WXDIR + "newroom.notify";
    static volatile android.content.Context APP_CTX = null;
    static volatile java.lang.ref.WeakReference<Activity> CUR_ACT = null;
    static final java.util.HashSet<String> RP_SEEN = new java.util.HashSet<String>();
    static final java.util.WeakHashMap<Object, Boolean> ORIG_USER_OFF =
            new java.util.WeakHashMap<Object, Boolean>();
    static final java.util.WeakHashMap<Object, Boolean> ORIG_TOUCH_ATTACHED =
            new java.util.WeakHashMap<Object, Boolean>();
    static final java.util.WeakHashMap<Object, long[]> ORIG_TEXT_CLICKS =
            new java.util.WeakHashMap<Object, long[]>();
    static final java.util.WeakHashMap<Object, long[]> ORIG_CLICKS =
            new java.util.WeakHashMap<Object, long[]>();
    static long lastOrigApply = 0;
    static volatile long FLAGS_AT = 0;
    static volatile boolean F_TIPTIME_OFF = false, F_TIPRENAME_OFF = false;
    static volatile String SELF_WXID = null;

    static void T(String msg) {
        try {
            java.io.File f = new java.io.File(WXDIR + "trace.log");
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) + "  " + msg + "\n");
            w.close();
        } catch (Throwable t) {}
        // v91: XposedBridge.log 在某些 LSPosed 版本/Android 17 上会抛，绝不能让它把 boot 打断
        try { XposedBridge.log("WXT: " + msg); } catch (Throwable t) {}
    }

    /** v91: 把异常原因写进 trace.log（loader 只能拿到 InvocationTargetException 外壳） */
    static void logStack(String tag, Throwable e) {
        try {
            T(tag + " " + e);
            Throwable c = e;
            int depth = 0;
            while (c != null && depth < 4) {
                java.io.StringWriter sw = new java.io.StringWriter();
                c.printStackTrace(new java.io.PrintWriter(sw));
                String[] lines = sw.toString().split("\n");
                int n = Math.min(lines.length, 8);
                for (int i = 0; i < n; i++) T("  " + (i == 0 ? "C" + depth + ": " : "    ") + lines[i].trim());
                c = c.getCause();
                depth++;
                if (c != null) T("  caused by:");
            }
        } catch (Throwable t) {}
    }


    // ===== 真名（低功耗版）=====
    static final String RN_CACHE_PATH = WXDIR + "realname.cache";
    static final String RN_OFF_PATH = WXDIR + "realname.off";
    static final String RN_BATCH_PATH = WXDIR + "realname.batch";
    static final String RN_GAP_PATH = WXDIR + "realname.gap";
    static final String RN_ALIAS_PATH = WXDIR + "realname.names";   // 群昵称->wxid 绑定（打开资料页时学到）
    static volatile boolean RN_HOOK_OK = false;       // v94：TextView 漏斗挂上没有
    // v101：钩子成功计数，用来判断「内核起来了但一个钩子都没挂上」（多半是 APK 太旧，类加载器看不到 Xposed API）
    static volatile int HOOK_NAME_OK = 0, HOOK_WRITE_N = 0, HOOK_ADDMSG_N = 0;
    static volatile boolean LOADER_BROKEN = false, BROKEN_WARNED = false;
    static final java.util.concurrent.atomic.AtomicLong RN_HITS = new java.util.concurrent.atomic.AtomicLong();   // 昵称 TextView 命中次数
    static final java.util.concurrent.atomic.AtomicLong RN_MAP_HITS = new java.util.concurrent.atomic.AtomicLong(); // 名字在映射里
    static final java.util.concurrent.atomic.AtomicLong RN_SUBS = new java.util.concurrent.atomic.AtomicLong();   // 实际补上真名的条数
    static final java.util.concurrent.atomic.AtomicLong RN_QUERIES = new java.util.concurrent.atomic.AtomicLong(); // 发出查询次数
    static volatile String RN_LAST_RESP = null;
    static final java.util.concurrent.atomic.AtomicLong TXT_HITS = new java.util.concurrent.atomic.AtomicLong();   // 4 参 setText 总次数
    static final java.util.concurrent.atomic.AtomicLong SENDDBG_N = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong TEXT_SAMPLES = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong TITLE_MISS = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong TITLE_HITS = new java.util.concurrent.atomic.AtomicLong(); // 命中标题栏次数
    static volatile String RN_SNAP = null;   // 离开群聊时留一份现场，首页菜单里也能看到
    static final String RN_DIAG_TS = WXDIR + "realname.diag.ts";   // 自动上报节流
    static final long RN_TTL = 7L * 24 * 3600 * 1000;

    static volatile boolean RN_ON = false;   // v196：默认关，首次由 rnOn() 读开关文件
    static volatile long RN_FLAG_AT = 0;
    static final java.util.HashMap<String, String> RN_MAP = new java.util.HashMap<String, String>();
    static final java.util.HashMap<String, Long> RN_TS = new java.util.HashMap<String, Long>();
    static final java.util.ArrayDeque<String> RN_QUEUE = new java.util.ArrayDeque<String>();
    static final java.util.HashSet<String> RN_SET = new java.util.HashSet<String>();
    static volatile String RN_INFLIGHT = null;
    static volatile long RN_LAST_MSG = 0;   // 最近一次【群里有新消息】的时间，用于限制「边看边查」
    static final java.util.HashMap<String, Long> RN_TRIED = new java.util.HashMap<String, Long>();
    static volatile Thread RN_WORKER = null;
    static volatile boolean RN_HOOKED = false;

    static volatile String CUR_ROOM = null;                             // 当前群；null = 不在群里
    static volatile java.util.concurrent.ConcurrentHashMap<String, String> NAME2USER = null;  // 本群 显示名 -> wxid
    static volatile java.util.HashSet<String> RN_MEMBERS = null;                              // 本群成员 wxid 集合
    // v123：取关公众号后会话别再删了 —— 删了以后微信一有新推送就会重建一条「没归类」的会话，
    // 直接落到聊天首页。改成把会话塞回「订阅号」分组，以后来的消息也继续留在里面。
    static final java.util.HashSet<String> BIZ_OFF = new java.util.HashSet<String>();
    /** v198：一键已读期间临时保留 parentRef（否则微信的 M() 会把公众号/折叠会话拉出分组） */
    static final java.util.concurrent.ConcurrentHashMap<String, String> FOLD_KEEP =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    static void bizOffLoad() {
        try {
            String t = readText(WXDIR + "biz.off");
            if (t == null) return;
            for (String l : t.split("\n")) {
                l = l.trim();
                if (l.length() > 0) BIZ_OFF.add(l);
            }
            T("biz.off loaded " + BIZ_OFF.size());
        } catch (Throwable t) { T("biz.off load err " + t); }
    }

    // v151（S08）：pending 与 confirmed 分开存。只有服务器侧确认（encryptUsername 变 fake_）
    // 才写 biz.off；没确认的留在 biz.pending，下次启动/下次取关时复查，重启不丢。
    static final java.util.HashSet<String> BIZ_PENDING = new java.util.HashSet<String>();

    static void bizPendingLoad() {
        try {
            String t = readText(WXDIR + "biz.pending");
            if (t == null) return;
            for (String l : t.split("\n")) {
                l = l.trim();
                if (l.length() > 0) BIZ_PENDING.add(l);
            }
            if (!BIZ_PENDING.isEmpty()) T("biz.pending loaded " + BIZ_PENDING.size());
        } catch (Throwable t) { T("biz.pending load err " + t); }
    }

    static void bizPendingAdd(String u) {
        try {
            if (u == null || u.length() == 0) return;
            synchronized (BIZ_PENDING) {
                if (!BIZ_PENDING.add(u)) return;
                java.io.FileWriter w = new java.io.FileWriter(new java.io.File(WXDIR + "biz.pending"), true);
                w.write(u + "\n");
                w.close();
            }
        } catch (Throwable t) { T("biz.pending add err " + t); }
    }

    static void bizPendingDel(String u) {
        try {
            synchronized (BIZ_PENDING) {
                if (!BIZ_PENDING.remove(u)) return;
                StringBuilder sb = new StringBuilder();
                for (String x : BIZ_PENDING) sb.append(x).append('\n');
                java.io.FileWriter w = new java.io.FileWriter(new java.io.File(WXDIR + "biz.pending"), false);
                w.write(sb.toString());
                w.close();
            }
        } catch (Throwable t) { T("biz.pending del err " + t); }
    }

    /** 8.0.80 实测：真取关后 rcontact.type=0（encryptUsername 变成 v3_…），
     *  不是早前记录的 fake_ 前缀；行被删掉也算已取关。 */
    static boolean bizUnfollowed(Object db, String id) {
        try {
            Cursor c = rawQuery(db, "select encryptUsername, type from rcontact where username='"
                    + id.replace("'", "''") + "'", null);
            if (c == null) return false;
            try {
                if (!c.moveToFirst()) return true;                 // 行没了
                String ev = null; int ty = -1;
                try { ev = c.getString(0); } catch (Throwable t) {}
                try { ty = c.getInt(1); } catch (Throwable t) {}
                if (ev != null && ev.startsWith("fake_")) return true;
                return ty == 0;                                    // 本地终态：不再关注
            } finally { c.close(); }
        } catch (Throwable t) { return false; }
    }

    /** 复查 pending：确认已取关（type=0 / fake_ / 行删除）才升级成 confirmed。 */
    static void bizPendingRecheck() {
        if (BIZ_PENDING.isEmpty()) return;
        Object db = null;
        try { db = db0(); } catch (Throwable t) { T("biz.pending db0 err " + t); return; }
        String[] ids = BIZ_PENDING.toArray(new String[0]);
        for (String id : ids) {
            boolean ok = bizUnfollowed(db, id);
            if (ok) { bizOffAdd(id); bizPendingDel(id); T("biz.pending confirmed " + id); }
        }
    }

    static void bizOffAdd(String u) {
        try {
            if (u == null || u.length() == 0) return;
            synchronized (BIZ_OFF) {
                if (!BIZ_OFF.add(u)) return;
                java.io.FileWriter w = new java.io.FileWriter(new java.io.File(WXDIR + "biz.off"), true);
                w.write(u + "\n");
                w.close();
            }
        } catch (Throwable t) { T("biz.off add err " + t); }
    }

    static volatile int ID_TITLE = 0;                                    // 聊天页标题 TextView id
    static volatile int ID_NICK = 0;                                     // 消息行昵称 TextView id（brc）
    static volatile String LAST_TITLE = null;
    static final java.util.HashMap<String, String> ROOM_BY_TITLE = new java.util.HashMap<String, String>();


    // ===== 版本闸门：只认微信 8.0.80（3220）+ 构建 0x28005032（5032） =====
    // 同一 versionName/versionCode 腾讯会发多个构建（渠道包/重打包），混淆类名和资源都可能变，
    // 光看版本号拦不住。这里直接读已安装 base.apk 里 AndroidManifest.xml 的 CRC32：
    // 内容差一个字节 CRC 就变，630KB 读一遍只要几毫秒，比 hash 整个 286MB 的包便宜得多。
    //   0x28005032 -> 0x903bad94  (现用构建，支持)
    //   0x28005030 -> 0xcc4b0bab  (旧构建，不再支持)
    static final int WX_OK_CODE = 3220;
    static final String WX_OK_NAME = "8.0.80";
    static final long[] WX_OK_APK_CRC = { 0x903bad94L };
    static final String[] WX_OK_APK_BUILD = { "5032" };

    /** 已安装微信 base.apk 里 AndroidManifest.xml 的 CRC32；读不到返回 -1。 */
    static long wxApkCrc(Object pi) {
        try {
            Object ai = pi.getClass().getField("applicationInfo").get(pi);
            String apk = (String) ai.getClass().getField("sourceDir").get(ai);
            java.util.zip.ZipFile zf = new java.util.zip.ZipFile(apk);
            try {
                java.util.zip.ZipEntry e = zf.getEntry("AndroidManifest.xml");
                if (e == null) { T("ver: no manifest entry in " + apk); return -1; }
                java.io.InputStream in = zf.getInputStream(e);
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) crc.update(buf, 0, n);
                in.close();
                return crc.getValue();
            } finally { try { zf.close(); } catch (Throwable t) {} }
        } catch (Throwable t) { T("ver: apk read err " + t); return -1; }
    }

    /** 返回 true 表示当前微信构建受支持；不支持的构建整个内核直接不装钩子。 */
        /** 微信运行时构建号（0x2800xxxx）。来自 WeChat 自己写的 prefs，进程内直接读文件，不用反射。 */
    static int wxBuildNo() {
        try {
            String f = computeWxData() + "/shared_prefs/com.tencent.mm_preferences.xml";
            String t = readText(f);
            if (t == null) return 0;
            int i = t.indexOf("last_reportdevice_clientversion");
            if (i < 0) return 0;
            int v = t.indexOf("value=\"", i);
            if (v < 0) return 0;
            int e = t.indexOf('"', v + 7);
            if (e < 0) return 0;
            return Integer.parseInt(t.substring(v + 7, e).trim());
        } catch (Throwable t) { return 0; }
    }

    /** 已知可用的运行时构建号：5032（原厂）与 5034（同 manifest 的代码更新）。 */
    static final int[] WX_OK_BUILDS = new int[] { 0x28005032, 0x28005034 };
    static final int   WX_MAX_BUILD = 0x28005034;

static boolean wxVersionOk(Object actObj) {
        try {
            android.content.Context c = null;
            if (actObj instanceof android.content.Context) c = (android.content.Context) actObj;
            if (c == null) c = APP_CTX;
            if (c == null) { T("ver: no context"); return false; }
            Object pi = c.getPackageManager().getPackageInfo("com.tencent.mm", 0);
            Class<?> pic = pi.getClass();
            int code = pic.getField("versionCode").getInt(pi);
            Object vn = pic.getField("versionName").get(pi);
            String name = vn == null ? "?" : String.valueOf(vn);
            long crc = wxApkCrc(pi);
            // 自测：把想模拟的 CRC 写进 ver.fakecrc（十六进制），用来验证「不支持」分支
            try {
                String fake = readText(WXDIR + "ver.fakecrc");
                if (fake != null && fake.trim().length() > 0) {
                    String s = fake.trim().replace("0x", "").replace("0X", "");
                    crc = Long.parseLong(s, 16);
                    T("ver: fakecrc " + s + " (自测)");
                }
            } catch (Throwable t) { T("ver: fakecrc bad " + t); }
            // v194：放宽闸门 —— 只认版本号（8.0.80 / 3220），不再按 APK CRC 拦人。
            // 同版本存在多种 APK 变体（crc=0x903bad94 / 0x978409f / 0xcc4b0bab …），
            // 一律放行；CRC 只写进日志用于统计，不再影响判定。
            T("wx version " + name + " (" + code + ") apk crc=0x" + Long.toHexString(crc) + " build=0x"
                    + Integer.toHexString(wxBuildNo()) + " -> gate-crc-ignored");
            // v195：versionName 也放宽（有的构建报 8.0.80_3220 这种带后缀的名字）
            boolean ok = name != null
                    && (name.equals(WX_OK_NAME) || name.startsWith(WX_OK_NAME + "_") || name.startsWith(WX_OK_NAME + "."))
                    && code == WX_OK_CODE;

            // 1.66（F-version）：manifest CRC 挡不住「同 manifest、换代码」的小版本更新
            // （8.0.80 的 5032 → 5034 就是这样，CRC 完全一样）。再按运行时构建号判一次：
            //   5032 / 5034      → 已知兼容，放行
            //   > 5034           → 未知新构建，拒绝并提示（避免类名变化后功能静默失效）
            //   0（读不到）      → 放行但记日志，免得新装用户被误伤
            int build = wxBuildNo();
            try {
                String fb = readText(WXDIR + "ver.fakebuild");
                if (fb != null && fb.trim().length() > 0) {
                    build = Integer.parseInt(fb.trim().replace("0x", "").replace("0X", ""), 16);
                    T("ver: fakebuild =0x" + Integer.toHexString(build) + " (自测)");
                }
            } catch (Throwable t) {}
            boolean buildKnown = false;
            for (int i = 0; i < WX_OK_BUILDS.length; i++) if (WX_OK_BUILDS[i] == build) buildKnown = true;
            // v195：503x 构建号不再参与判定，只写日志
            if (build == 0) {
                T("wx build = unknown (prefs 未就绪)，按未知放行");
            } else {
                T("wx build = 0x" + Integer.toHexString(build) + (buildKnown ? " ok" : " 未登记，按兼容放行"));
            }
            if (new java.io.File(WXDIR + "ver.ignore").exists()) {
                T("ver: ver.ignore present -> force enable");
                ok = true;
            }
            if (!ok) {
                try {
                    final String m = "wxentry 只支持微信 8.0.80（3220），当前是 "
                            + name + "(" + code + ")。请升级到 8.0.80 再试。";
                    Toast.makeText(c, m, Toast.LENGTH_LONG).show();
                } catch (Throwable t) {}
            }
            return ok;
        } catch (Throwable t) {
            T("ver check err " + t);
            return false;
        }
    }

    /** 由 loader 在激活校验通过后调用 */
    public static void boot(ClassLoader cl, final Object actObj) {
        try {
            boot0(cl, actObj);
        } catch (Throwable t) {
            logStack("BOOT CRASH", t);
            throw new RuntimeException(t);
        }
    }


    /** v208：把 LSPosed 版本写进 trace，诊断上报（含内核侧上报）里就能看到 */
    static String lspVer() {
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
            Class<?> bc = Class.forName("org.lsposed.lspd.BuildConfig", false, Kernel.class.getClassLoader());
            Object vn = bc.getField("VERSION_NAME").get(null);
            Object vc = bc.getField("VERSION_CODE").get(null);
            return String.valueOf(vn) + "(" + vc + ")";
        } catch (Throwable t) {}
        try {
            Class<?> xb = Class.forName("de.robv.android.xposed.XposedBridge", false, Kernel.class.getClassLoader());
            Object v = xb.getMethod("getXposedVersion").invoke(null);
            return "api" + v;
        } catch (Throwable t) {}
        return "unknown";
    }

    static void boot0(ClassLoader cl, final Object actObj) {

        APP_CL = cl;
        rememberAppContext(actObj, "boot");
        final long BOOT_T0 = System.currentTimeMillis();
        T("kernel boot v" + BUILD);
        try { T("lsp=" + lspVer()); } catch (Throwable t) {}
        // v92：把「内核自己是谁加载的、Xposed API 能不能解析」记下来，方便定位 hook 全挂的设备
        try {
            ClassLoader self = Kernel.class.getClassLoader();
            T("cl self=" + (self == null ? "boot" : self.getClass().getSimpleName()) + " parent=" + (self == null || self.getParent() == null ? "boot" : self.getParent().getClass().getSimpleName()));
            Class<?> xb = Class.forName("de.robv.android.xposed.XposedBridge", false, self);
            ClassLoader xbc = xb.getClassLoader();
            T("cl xposed=" + (xbc == null ? "boot" : xbc.getClass().getSimpleName()) + " ok");
        } catch (Throwable t) {
            T("cl probe fail " + t);
        }
        if (!wxVersionOk(actObj)) {
            T("kernel disabled: 仅支持微信 " + WX_OK_NAME + "（" + WX_OK_CODE + "）构建 5032");
            return;
        }
        // 1.48：远程停用。本地有停用标记就一个钩子都不装，只显示停用页；同时起线程继续问服务器，
        // 审核通过后下一次检查会自动清掉标记（重启微信生效）。
        try { loadFeatCache(); } catch (Throwable t) {}
        try { loadOffState(); } catch (Throwable t) {}
        if (MODULE_OFF) {
            T("module off at boot: " + OFF_REASON);
            // 1.64：boot 现在跑在后台线程，弹窗必须回 UI 线程
            try {
                final Activity oa = (Activity) actObj;
                oa.runOnUiThread(new Runnable() { public void run() {
                    try { showOffDialog(oa, OFF_REASON, true); } catch (Throwable t) {}
                } });
            } catch (Throwable t) { T("off dialog post err " + t); }
            startStatusLoop();
            return;
        }
        try { ensureDefaultCfg(); } catch (Throwable t) {}
        // 1.64：DEX 索引（首次要解压扫 18 个 dex，约 1~2.4s）提前在后台跑，
        // 和后面的钩子安装并行，别让整条启动路径卡在第一次 dexIdx() 上。
        try { startDexPrewarm(); } catch (Throwable t) {}
        try { hookBridgeProbe(cl); } catch (Throwable t) { T("hookprobe boot err " + t); }
        try { hookUnfollowProbe(cl); } catch (Throwable t) { T("unfollow probe boot err " + t); }
        try { java.io.FileOutputStream fo = new java.io.FileOutputStream(WXDIR + "kernel.ver"); fo.write(BUILD.getBytes("UTF-8")); fo.close(); } catch (Throwable t) {}


        try { rnLoad(); } catch (Throwable t) { T("rn load err " + t); }
        try { loadFolds(); } catch (Throwable t) { T("folds boot err " + t); }
        try { bizOffLoad(); bizPendingLoad(); recallPathsLoad();
        // S08：启动后台复查一次 pending —— 上次没确认的取关，网络恢复后要能自动落定
        try {
            new Thread(new Runnable() { public void run() { try { bizPendingRecheck(); } catch (Throwable t) {} } }).start();
        } catch (Throwable t) {} } catch (Throwable t) { T("biz.off boot err " + t); }
        try { hookChatNames(cl); } catch (Throwable t) { T("rn text hook err " + t); }
        try { hookProfile(cl); } catch (Throwable t) { T("profile hook err " + t); }
        try { hookAvatarTag(cl); } catch (Throwable t) { T("avatar tag hook err " + t); }

        try { RecallMarkLayout.start(cl); } catch (Throwable t) { T("ARMARK UI unavailable " + t.getClass().getSimpleName()); }
        try { UnreadReadShortcut.start(cl); } catch (Throwable t) { T("UNREAD shortcut unavailable " + t.getClass().getSimpleName()); }
        try { VoiceRecallRuntime.start(cl); } catch (Throwable t) { T("ARVOICE install-error " + t.getClass().getSimpleName()); }
        try { MediaRecallRuntime.start(cl); } catch (Throwable t) { T("ARMEDIA install-error " + t.getClass().getSimpleName()); }
        try { OriginalImageRuntime.start(cl); } catch (Throwable t) { T("ARORIGINAL install-error " + t.getClass().getSimpleName()); }
        try { VoiceForwardRuntime.start(cl); } catch (Throwable t) { T("ARVF install-error " + t.getClass().getSimpleName()); }
        try { hookMessageWrites(cl); } catch (Throwable t) { T("msg write hook err " + t); }
        try { hookAddMsg(cl); } catch (Throwable t) { T("addmsg hook err " + t); }
        try { hookSendDebug(cl); } catch (Throwable t) { T("senddbg err " + t); }
        try { hookSnsObjects(cl); } catch (Throwable t) { T("snsobj hook err " + t); }
        try { hookSnsDb(cl); } catch (Throwable t) { T("snsdb hook err " + t); }
        try { hookSnsSqlDelete(cl); } catch (Throwable t) { T("sns sqldel boot err " + t); }
        try { hookSnsSourceFlag(cl); } catch (Throwable t) { T("sns srcflag boot err " + t); }
        try { new Thread(new Runnable() { public void run() { try { Thread.sleep(15000); } catch (Throwable t) {} snsDelTestMaybe(); snsRestoreMaybe(); snsRmFlagTestMaybe(); snsMarkTestMaybe(); dbProbeMaybe(); dbgSqlMaybe(); } }).start(); } catch (Throwable t) {}
        try { hookListProbes(cl); } catch (Throwable t) { T("listprobe hook err " + t); }
        try { startSnsWatchdog(); } catch (Throwable t) {}
        try { hookUiListProbes(cl); } catch (Throwable t) { T("uilist hook err " + t); }
        try { hookFileDelete(cl); } catch (Throwable t) { T("file delete hook err " + t); }
        try { hookAutoDownload(cl); } catch (Throwable t) { T("autodl hook err " + t); }
        // Base edition: no premium image runtime is packaged.
        try { hookImgProbe(cl); } catch (Throwable t) { T("imgprobe hook err " + t); }
        try { hookD3(cl); } catch (Throwable t) { T("d3 probe hook err " + t); }
        try { hookCfgForce(cl); } catch (Throwable t) { T("cfg hook err " + t); }
        try { hookSns(cl); } catch (Throwable t) { T("sns hook err " + t); }
        try { hookRedPacket(cl); } catch (Throwable t) { T("rp hook err " + t); }

        hookOrigPicker(cl, "com.tencent.mm.plugin.picker.LocalMediaPickerUI");
        hookOrigPicker(cl, "com.tencent.mm.plugin.picker.preview.LocalMediaPreviewUI");
        hookOrigPicker(cl, "com.tencent.mm.plugin.gallery.ui.ImagePreviewUI");
        hookOrigPicker(cl, "com.tencent.mm.plugin.gallery.ui.AlbumPreviewUI");
        hookOrigPicker(cl, "com.tencent.mm.ui.MMActivity");
        hookOrigPicker(cl, "com.tencent.mm.ui.MMFragmentActivity");
        hookBar(cl, "com.tencent.mm.plugin.picker.chrome.LocalMediaPickerSendBarView");
        hookBar(cl, "com.tencent.mm.plugin.picker.chrome.LocalMediaPreviewSelectedBarView");
        hookBar(cl, "com.tencent.mm.plugin.picker.chrome.LocalMediaPreviewActionBarView");

        // v101：内核起来了但一个钩子都没挂上 —— 旧版 loaders（<=1.23）在不给 Xposed API 的
        // ClassLoader 里加载内核时就是这表现，防撤回 / 一键已读 / 真名会全废，只能换新版 APK。
        try {
            if (HOOK_NAME_OK == 0 && HOOK_WRITE_N == 0 && HOOK_ADDMSG_N == 0) {
                LOADER_BROKEN = true;
                T("loader broken: hooks=0 name=" + HOOK_NAME_OK + " write=" + HOOK_WRITE_N + " addmsg=" + HOOK_ADDMSG_N);
                diagOnce("loader-broken", "loader.broken.ts");
            }
            if (new java.io.File(WXDIR + "force.broken").exists()) LOADER_BROKEN = true;   // 自检用
        } catch (Throwable t) {}

        expireDebugFlags();   // v117：调试开关最长留 48 小时，忘关会把聊天页拖慢
        hookActivityTracker();
        try { snapWrite(tinkerSnap()); } catch (Throwable t) {}   // v116：记录本次启动的补丁指纹
        hookLivenessWatch();
        startStatusLoop();   // 1.48：每 30 分钟问一次在线状态/许愿回复

        try {
            Class<?> launcher = XposedHelpers.findClass("com.tencent.mm.ui.LauncherUI", cl);
            XposedHelpers.findAndHookMethod(launcher, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity act = (Activity) param.thisObject;
                    try { rememberAppContext(act, "launcher"); } catch (Throwable t) {}
                    try { dbgSqlMaybe(); } catch (Throwable t) { T("dbg hook err " + t); }
                    try { snsDelTestMaybe(); } catch (Throwable t) { T("deltest hook err " + t); }
                    try { attachPlusLongClick(act); } catch (Throwable t) { T("plus attach err " + t); }
                    try {
                        if (LOADER_BROKEN && !BROKEN_WARNED) {
                            BROKEN_WARNED = true;
                            act.runOnUiThread(new Runnable() { public void run() { showBrokenDialog(act); } });
                        }
                    } catch (Throwable t) {}

                    try {
                        if (new java.io.File(WXDIR + "id_probe").exists()) {
                            new java.io.File(WXDIR + "id_probe").delete();
                            Object cr = XposedHelpers.callMethod(act, "getContentResolver");
                            Object aid = XposedHelpers.callStaticMethod(
                                    Class.forName("android.provider.Settings$Secure"), "getString", cr, "android_id");
                            T("probe android_id = " + aid);
                            try {
                                java.util.UUID wv = new java.util.UUID(0xedef8ba979d64aceL, 0xa3c827dcd51d21edL);
                                Class<?> md = Class.forName("android.media.MediaDrm");
                                Object drm = md.getConstructor(java.util.UUID.class).newInstance(wv);
                                byte[] id = (byte[]) md.getMethod("getPropertyByteArray", String.class).invoke(drm, "deviceUniqueId");
                                StringBuilder sb = new StringBuilder();
                                for (int i2 = 0; i2 < id.length; i2++) sb.append(String.format("%02x", Byte.valueOf(id[i2])));
                                T("probe widevine(" + id.length + ") = " + sb);
                                try { md.getMethod("close").invoke(drm); } catch (Throwable t2) {}
                            } catch (Throwable t2) {
                                Throwable c2 = t2;
                                while (c2.getCause() != null) c2 = c2.getCause();
                                T("probe widevine err " + c2);
                            }
                            try {
                                Class<?> sp = Class.forName("android.os.SystemProperties");
                                T("probe ro.serialno = " + sp.getMethod("get", String.class).invoke(null, "ro.serialno"));
                            } catch (Throwable t2) { T("probe prop err " + t2); }
                        }
                    } catch (Throwable t2) { T("probe err " + t2); }

                    try { resolveTitleId(act); } catch (Throwable t) { T("title id err " + t); }
                }
            });
            T("launcher hook ok");
        } catch (Throwable t) { T("launcher hook fail " + t); }
    
        try {
            if (actObj instanceof Activity) {
                final Activity a = (Activity) actObj;
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        try { attachPlusLongClick(a); } catch (Throwable t) { T("boot attach err " + t); }
                        try { resolveTitleId(a); } catch (Throwable t) {}
                        try {
                            if (LOADER_BROKEN && !BROKEN_WARNED) {
                                BROKEN_WARNED = true;
                                new Thread(new Runnable() { public void run() {
                                    try { Thread.sleep(1200); } catch (Throwable t) {}
                                    // 一定要回到有 Looper 的 UI 线程再弹窗
                                    try {
                                        a.runOnUiThread(new Runnable() { public void run() {
                                            try { showBrokenDialog(a); } catch (Throwable t) { T("broken dialog2 err " + t); }
                                        } });
                                    } catch (Throwable t) { T("broken post err " + t); }
                                } }).start();
                            }
                        } catch (Throwable t) {}
                    }
                });
            }
        } catch (Throwable t) {}
        try { hookPopupContent(); } catch (Throwable t) {}
        try { hookPopupShow(); } catch (Throwable t) {}
        MENU_READY = true;
        T("boot done " + (System.currentTimeMillis() - BOOT_T0) + "ms idx=" + DEX_IDX_SRC);
        // 1.75：+ 面板是独立 PopupWindow，触摸回调会被微信覆盖 —— 改成周期扫描，出现就注入
        try {
            new Thread(new Runnable() {
                public void run() {
                    long lastScanLog = 0;
                    for (;;) {
                        try { Thread.sleep(2000); } catch (Throwable t) { return; }
                        try {
                            long nowMs = System.currentTimeMillis();
                            if (nowMs - lastScanLog > 15000) { lastScanLog = nowMs; logWindowTypes(); }
                            java.util.List<android.view.View> ws = panelWindows();
                            if (ws == null || ws.isEmpty()) continue;
                            for (int i = 0; i < ws.size(); i++) {
                                android.view.View v = ws.get(i);
                                if (v == null || hasOurBox(v,0)) continue;
                                PANEL_SEEN.put(v, "1");
                                if (DBG_PANEL) { T("panel found " + v.getClass().getName()); dumpPanelTree(v, 0); }
                                try { injectPanelEntry(v); } catch (Throwable t2) { T("panel inject err " + t2); }
                            }
                        } catch (Throwable t) {}
                    }
                }
            }).start();
        } catch (Throwable t) { T("panel watcher err " + t); }
    }


    static void markUnreadShortcut(final Activity act, final Runnable complete) { new Kernel().markAllRead(act, complete); }

    private void markAllRead(final Activity act) { markAllRead(act, null); }

    private void markAllRead(final Activity act, final Runnable complete) {
        new Thread(new Runnable() {
            public void run() {
                int n = 0;
                int ghUnread = 0;
                String err = null;
                StringBuilder sb = new StringBuilder();
                try {
                    ClassLoader cl = act.getClass().getClassLoader();
                    Class<?> locator = Class.forName(SVC_LOCATOR, true, cl);
                    Class<?> iface = Class.forName(SVC_IFACE, true, cl);
                    Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
                    if (svc == null) throw new RuntimeException("service null");
                    Object storage = XposedHelpers.callMethod(svc, "Hj");
                    if (storage == null) throw new RuntimeException("storage null");
                    Object db = XposedHelpers.getObjectField(storage, "d");
                    Object curObj = rawQuery(db,
                            "select username, parentRef from rconversation where unReadCount > 0", null);
                    Cursor c = (Cursor) curObj;
                    List<String> users = new ArrayList<String>();
                    java.util.HashMap<String, String> keep = new java.util.HashMap<String, String>();
                    while (c != null && c.moveToNext()) {
                        try {
                            String uu = c.getString(0);
                            String pr = null;
                            try { pr = c.getString(1); } catch (Throwable t1) {}
                            users.add(uu);
                            // v198：记住分组归属，微信的 M() 会把 parentRef 清空（公众号会因此跑到首页）
                            if (uu != null && pr != null && pr.trim().length() > 0 && !"officialaccounts".equals(uu))
                                keep.put(uu, pr.trim());
                        } catch (Throwable t) {}
                    }
                    if (c != null) c.close();
                    final java.util.ArrayList<String> foldSql = new java.util.ArrayList<String>();
                    if (!keep.isEmpty()) { FOLD_KEEP.putAll(keep); T("markAllRead keep fold=" + keep.size()); }
                    Method m = storage.getClass().getMethod("M", String.class);
                    m.setAccessible(true);
                    try {
                        for (String u : users) {
                            if (u == null || u.length() == 0) continue;
                            // v207：有分组归属的会话（公众号 / 折叠群）一律不走 M()——
                            // M() 会把 parentRef 清空，微信随后就按「不在分组里」通知界面，必然闪一下。
                            // 这些号只用 SQL 清未读，分组标记一个字节都不动。
                            if (keep.containsKey(u)) { foldSql.add(u); continue; }
                            try { m.invoke(storage, u); n++; }
                            catch (Throwable t) { sb.append(u).append(':').append(t).append(';'); }
                        }
                        if (!foldSql.isEmpty()) {
                            StringBuilder in = new StringBuilder();
                            for (String u : foldSql) {
                                if (in.length() > 0) in.append(",");
                                in.append("'").append(u.replace("'", "''")).append("'");
                            }
                            execSql(db, "update rconversation set unReadCount=0 where username in (" + in + ")");
                        }
                        T("markAllRead m loop n=" + n + " foldSql=" + foldSql.size());
                    } finally {
                        if (!keep.isEmpty()) {
                            try {
                                for (java.util.Map.Entry<String, String> e2 : keep.entrySet()) {
                                    execSql(db, "update rconversation set parentRef='" + e2.getValue().replace("'", "''")
                                            + "' where username='" + e2.getKey().replace("'", "''") + "'");
                                }
                                T("markAllRead restored fold=" + keep.size());
                            } catch (Throwable t) { T("markAllRead restore err " + t); }
                            FOLD_KEEP.keySet().removeAll(keep.keySet());
                        }
                    }
                    // v199：修历史遗留 —— 以前被 M() 拉出分组的公众号/服务号，按 rcontact.type 放回去
                    try {
                        execSql(db, "update rconversation set parentRef='service_officialaccounts' "
                                + "where username like 'gh_%' and (parentRef is null or parentRef='') "
                                + "and username in (select username from rcontact where type=515)");
                        execSql(db, "update rconversation set parentRef='officialaccounts' "
                                + "where username like 'gh_%' and (parentRef is null or parentRef='')");
                        T("markAllRead refold done");
                        // 触发会话列表重绘（M 会 notify），否则界面还是旧的展开态
                        try { m.invoke(storage, "officialaccounts"); } catch (Throwable t) {}
                        try { m.invoke(storage, "service_officialaccounts"); } catch (Throwable t) {}
                        // v204：事后体检 —— 还有没有 gh_ 掉在首页（正常应该是 0）
                        int leak = 0;
                        try {
                            StringBuilder lb = new StringBuilder();
                            Cursor c3 = rawQuery(db, "select username from rconversation where username like 'gh_%' "
                                    + "and (parentRef is null or parentRef='')", null);
                            try {
                                while (c3 != null && c3.moveToNext()) {
                                    leak++;
                                    if (leak <= 5) lb.append(c3.getString(0)).append(',');
                                }
                            } finally { if (c3 != null) c3.close(); }
                            T("markAllRead gh leak=" + leak + " [" + lb + "]");
                        } catch (Throwable t) { T("markAllRead leak err " + t); }
                        // v206：不重建首页。只有真的漏出分组（SQL 改写没兜住）才重建一次兜底。
                        if (leak > 0 || new java.io.File(WXDIR + "read.recreate").exists()) {
                            T("markAllRead leak=" + leak + " -> recreate");
                            try { refreshConvListUi(); } catch (Throwable t) { T("markAllRead ui err " + t); }
                        } else {
                            try { softRefreshConvList(); } catch (Throwable t) { T("markAllRead soft err " + t); }
                        }
                    } catch (Throwable t) { T("markAllRead refold err " + t); }
                    XposedBridge.log("WXEntry: markAllRead users=" + users.size() + " ok=" + n + " keep=" + keep.size()
                            + (sb.length() > 0 ? (" err=" + sb) : ""));
                } catch (Throwable t) {
                    err = t.toString();
                    // 8.0.49 没有 zp0.j1 那套服务定位 → 直接改库兜底
                    try {
                        Object db = db0();
                        execSql(db, "update rconversation set unReadCount=0 where unReadCount>0");
                        n = 1; err = null;
                        T("markAllRead sql fallback done");
                    } catch (Throwable t2) { T("markAllRead fallback err " + t2); }
                }
                final int cnt = n;
                final String e = err;
                try {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            try { Toast.makeText(act, e == null ? ("已标记已读 " + cnt + " 个会话") : ("失败: " + e),
                                    Toast.LENGTH_SHORT).show(); }
                            finally { if (complete != null) complete.run(); }
                        }
                    });
                } catch (Throwable t) { if (complete != null) complete.run(); }
            }
        }).start();
    }


    private static void toast(Activity act, String s) {
        try { Toast.makeText(act, s, Toast.LENGTH_LONG).show(); } catch (Throwable t) {}
    }

    /** 137：我们自己派发的触摸事件不算「用户手动点过」 */
    static volatile boolean SYNTH_TOUCH = false;

    static void attachUserTouch(final Activity act, Object parent) {
        try {
            if (!(parent instanceof View)) return;
            if (ORIG_TOUCH_ATTACHED.containsKey(parent)) return;
            ORIG_TOUCH_ATTACHED.put(parent, Boolean.TRUE);
            ((View) parent).setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    if (e.getAction() == 0 && !SYNTH_TOUCH
                            && !Boolean.TRUE.equals(ORIG_USER_OFF.get(act))) {
                        ORIG_USER_OFF.put(act, Boolean.TRUE);
                        T("orig: user touched -> stop forcing");
                    }
                    return false;
                }
            });
        } catch (Throwable t) {}
    }

    static void postApply(Object view) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastOrigApply < 120) return;
            lastOrigApply = now;
            Object ctx = XposedHelpers.callMethod(view, "getContext");
            if (!(ctx instanceof Activity)) return;
            final Activity act = (Activity) ctx;
            act.runOnUiThread(new Runnable() {
                public void run() { applyOriginal(act); }
            });
        } catch (Throwable t) {}
    }

    static void hookBar(ClassLoader cl, String cn) {
        try {
            Class<?> k = XposedHelpers.findClass(cn, cl);
            int n = 0;
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        if (p.thisObject != null) postApply(p.thisObject);
                    }
                });
                n++;
            }
            T("orig bar hook " + cn + " methods=" + n);
        } catch (Throwable t) { if (VB()) T("orig bar hook fail " + cn + " " + t); }
    }

    static void hookRedPacket(ClassLoader cl) {
        RedPacketRuntime.start(cl);
        TransferRuntime.start(cl);
        try {
            String[] impls = {"t93.l", "ai5.b0", "ai5.d0"};
            int n = 0;
            for (String cn : impls) {
                Class<?> k = XposedHelpers.findClass(cn, cl);
                for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 3 && ps[0] == String.class && ps[1] == String.class
                            && ps[2].getName().equals("android.content.ContentValues")) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                                try {
                                    if (p.hasThrowable() || Kernel.MODULE_OFF) return;
                                    if (p.getResult() instanceof Number && ((Number)p.getResult()).longValue()<0) return;
                                    String table = String.valueOf(p.args[0]);
                                    if (!"message".equals(table)) return;
                                    android.content.ContentValues cv =
                                            (android.content.ContentValues) p.args[2];
                                    if (cv == null) return;
                                    Integer t = cv.getAsInteger("type");

                                    // 真名：群里有人发言且没缓存 -> 只查这一个
                                    try {
                                        String room = CUR_ROOM;
                                        if (room != null && rnOn() && room.equals(cv.getAsString("talker"))) {
                                            Long isSendV = cv.getAsLong("isSend");
                                            if (isSendV == null || isSendV.longValue() != 1) RN_LAST_MSG = System.currentTimeMillis();
                                            String cc = cv.getAsString("content");
                                            if (cc != null) {
                                                int cp = cc.indexOf(":\n");
                                                if (cp <= 3) cp = cc.indexOf(": ");
                                                if (cp <= 3) cp = cc.indexOf(':');
                                                if (cp > 3 && cp <= 40) {
                                                    String u = cc.substring(0, cp).trim();
                                                    if (u.length() >= 5 && u.indexOf(' ') < 0 && u.indexOf('\n') < 0) {
                                                        if (!u.equals(selfWxid()) && rnCached(u) == null) {
                                                            T("rn: new speaker " + u);
                                                            rnEnqueue(u, room);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Throwable tt) {}

                                    // 新加群聊自动免打扰
                                    try {
                                        if (t != null && (t.intValue() & 0xFFFF) == 10000
                                                && "message".equals(table)) {
                                            String tc = cv.getAsString("talker");
                                            String cc = cv.getAsString("content");
                                            if (tc != null && tc.endsWith("@chatroom") && cc != null
                                                    && cc.contains("\u52a0\u5165\u7fa4\u804a")
                                                    && (cc.contains("\u4f60") || cc.contains("\u60a8"))) {
                                                T("newroom: joined " + tc + " :: " + brief(cc));
                                                if (new java.io.File(NEWROOM_ON_PATH).exists())
                                                    muteRoom(tc, notifyMsgValue());
                                                else T("newroom: switch off");
                                            }
                                        }
                                    } catch (Throwable tt) { T("newroom err " + tt); }
                                    // Native callbacks own success notification/replies; text is not proof of receipt.
                                    if (!RedPacketRuntime.ready() && t != null && (t.intValue() & 0xFFFF) == 10000) {
                                        String sc = cv.getAsString("content");
                                        if (sc != null && sc.contains("你领取了") && sc.contains("红包")
                                                && !new java.io.File(RP_TOAST_OFF_PATH).exists())
                                            toastMain(redPacketPremiumEnabled() ? sc.replaceAll("<[^>]+>", "") : "抢到红包！");
                                    }
                                    if (new java.io.File(WXDIR + "rp_debug").exists())
                                        T("rpdbg insert table=" + table + " type=" + t
                                          + " isSend=" + cv.getAsLong("isSend")
                                          + " msgId=" + cv.getAsLong("msgId")
                                          + " content=" + brief(cv.getAsString("content")));
                                    Long incoming = cv.getAsLong("isSend");
                                    if (t != null && (t.intValue() & 0xFFFF) == 49 && incoming != null && incoming.longValue() == 0)
                                        TransferRuntime.received(cv.getAsString("content"),cv.getAsString("talker"),cv.getAsLong("createTime"));
                                    if (t == null) return;
                                    String url = RedPacketEngine.messageUrl(t.intValue(), cv.getAsLong("isSend"), cv.getAsString("content"));
                                    if (url == null) return;
                                    Long mid = cv.getAsLong("msgId");
                                    String talker = cv.getAsString("talker");
                                    boolean isGroup = talker != null && talker.endsWith("@chatroom");
                                    T("rp: detect type=" + t + " group=" + isGroup);
                                    RedPacketRuntime.received(url, mid == null ? 0L : mid.longValue(), talker, cv.getAsLong("createTime"), cv.getAsString("content"));
                                } catch (Throwable t) { T("rp err " + t); }
                            }
                        });
                        n++;
                    }
                }
            }
            T("rp insert hooks: " + n);

            int uiHooks=0;
            for(String uiName:new String[]{"LuckyMoneyNotHookReceiveUI","LuckyMoneyNewReceiveUI"}) {
                try {
                    Class<?> ui = XposedHelpers.findClass("com.tencent.mm.plugin.luckymoney.ui."+uiName, cl);
                    XposedHelpers.findAndHookMethod(ui, "onCreate", Bundle.class, new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            RedPacketRuntime.legacyUi((Activity) param.thisObject);
                        }
                    });
                    uiHooks++;T("rp ui hook ok "+uiName);
                } catch(Throwable failure) { T("rp ui hook unavailable "+uiName+" "+failure.getClass().getSimpleName()); }
            }
            T("rp receive ui hooks: "+uiHooks);
        } catch (Throwable t) { if (VB()) T("rp hook failed " + t); }
    }

    /** 诊断：WXDIR/dbg_sql 每行一条 SQL，后台线程执行并把结果写 trace.log，跑完改名 .done */
    static void dbgSqlMaybe() { dbgSqlMaybe(null); }

    static void dbgSqlMaybe(final Object given) {
        final java.io.File f = new java.io.File(WXDIR + "dbg_sql");
        if (VB()) T("dbg probe exists=" + f.exists() + (given == null ? " (db from db0)" : " (db given)"));
        if (!f.exists()) return;
        new Thread(new Runnable() {
            public void run() {
                Object db = given;
                if (db == null) {
                    try { db = db0(); } catch (Throwable t) { T("dbg db0 err " + t); }
                }
                if (db == null) { T("dbg no db"); return; }
                T("dbg db=" + db.getClass().getName());
                java.util.List<String> lines = new java.util.ArrayList<String>();
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                    String l;
                    while ((l = r.readLine()) != null) {
                        l = l.trim();
                        if (l.length() > 0 && !l.startsWith("#")) lines.add(l);
                    }
                    r.close();
                } catch (Throwable t) { T("dbg read err " + t); }
                for (String sql : lines) {
                    // 141：自测用 —— apsel:<查询用户名> 走微信自己的删除入口（带列表刷新）
                    if (sql.startsWith("apisel:")) {
                        try {
                            Cursor c = rawQuery(db, sql.substring(6), null);
                            int n2 = 0;
                            try {
                                while (c != null && c.moveToNext()) {
                                    String u = c.getString(0);
                                    boolean ok2 = delConvViaApi(u);
                                    n2++;
                                    T("apisel " + u + " -> " + ok2);
                                }
                            } finally { if (c != null) c.close(); }
                            T("apisel done " + n2);
                        } catch (Throwable t) { T("apisel err " + t); }
                        continue;
                    }
                    if (sql.startsWith("exec:")) {
                        execSql(db, sql.substring(5));
                        T("dbg exec ok: " + sql.substring(5));
                        continue;
                    }
                    try {
                        Cursor c = rawQuery(db, sql, null);
                        if (c == null) { T("dbg " + sql + " -> null"); continue; }
                        try {
                            String[] cols = c.getColumnNames();
                            StringBuilder sb = new StringBuilder();
                            int n = 0;
                            while (c.moveToNext() && n < 20) {
                                sb.append("\n    ");
                                for (int i = 0; i < cols.length; i++) {
                                    String v;
                                    try { v = c.getString(i); } catch (Throwable t) { v = "?"; }
                                    if (v != null && v.length() > 160) v = v.substring(0, 160) + "\u2026";
                                    sb.append(cols[i]).append('=').append(v).append(" | ");
                                }
                                n++;
                            }
                            T("dbg " + sql + sb);
                        } finally { c.close(); }
                    } catch (Throwable t) { T("dbg err " + sql + " " + t); }
                }
                try { f.renameTo(new java.io.File(f.getPath() + ".done")); } catch (Throwable t) {}
            }
        }).start();
    }

    // 探针开关（保留：DB 写操作的行级日志只在 WXDIR/netprobe.on 存在时才记）
    static final java.io.File NETPROBE = new java.io.File(WXDIR + "netprobe.on");

    // v206：一键已读不再靠重建首页 —— 在 SQL 层把「parentRef 置空」改成「保持原分组」，
    // 这样微信自己的 M() 仍会发正常的已读通知（红点消失、无闪烁），但不会把公众号/折叠群踢出分组。
    static final java.io.File READ_PROBE = new java.io.File(WXDIR + "read.probe");
    static final java.util.concurrent.ConcurrentHashMap<String, String> GH_FOLD =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    static void loadFolds() {
        try {
            Object db = db0();
            if (db == null) return;
            Cursor c = (Cursor) rawQuery(db, "select username, parentRef from rconversation "
                    + "where parentRef is not null and parentRef<>'' limit 800", null);
            int n = 0;
            try {
                while (c != null && c.moveToNext()) {
                    String u = c.getString(0);
                    String p = c.getString(1);
                    if (u != null && p != null && p.length() > 0) { GH_FOLD.put(u, p); n++; }
                }
            } finally { if (c != null) c.close(); }
            T("folds loaded " + n);
        } catch (Throwable t) { T("folds load err " + t); }
    }

    /** 把 "parentRef=''" / "parentRef=null" 改成原分组；认不出来就返回 null（原样执行） */
    static String rewriteParentRef(String sql, Object[] args) {
        try {
            if (sql == null) return null;
            String low = sql.toLowerCase();
            if (low.indexOf("rconversation") < 0 || low.indexOf("parentref") < 0) return null;
            java.util.regex.Matcher mm = java.util.regex.Pattern
                    .compile("(?i)parentref\\s*=\\s*(?:''|null)").matcher(sql);
            if (!mm.find()) return null;
            String u = null;
            java.util.regex.Matcher um = java.util.regex.Pattern
                    .compile("(?i)username\\s*=\\s*'([^']*)'").matcher(sql);
            if (um.find()) u = um.group(1);
            if (u == null || u.length() == 0) {
                if (args != null) for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String && ((String) args[i]).length() > 0) { u = (String) args[i]; break; }
                }
            }
            if (u == null || u.length() == 0) return null;
            String fold = GH_FOLD.get(u);
            if (fold == null) return null;
            String fixed = mm.replaceAll("parentRef='" + fold + "'");
            return fixed;
        } catch (Throwable t) { return null; }
    }


    static volatile Object DB0 = null;

    /** 取 EnMicroMsg 的 DB 句柄：8.0.80 走 zp0.j1.v(gp3.c4)，8.0.49 走 u70.k1.u()。
     *  两种都失败时再从存储对象里翻出一个带 rawQuery(String,String[]) 的字段。 */
    static Object db0() throws Throwable {
        Object d = DB0;
        if (d != null) return d;
        try {   // 8.0.80
            Class<?> locator = Class.forName(SVC_LOCATOR, true, APP_CL);
            Class<?> iface = Class.forName(SVC_IFACE, true, APP_CL);
            Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
            Object conv = XposedHelpers.callMethod(svc, "Hj");
            d = XposedHelpers.getObjectField(conv, "d");
        } catch (Throwable t) {}
        if (d == null) {
            try {   // 8.0.49
                Class<?> k1 = Class.forName("u70.k1", true, APP_CL);
                Object core = XposedHelpers.callStaticMethod(k1, "u");
                Object st = XposedHelpers.callMethod(core, "j");
                if (st != null && findMethod(st.getClass(), Cursor.class, String.class, String[].class) != null) d = st;
                if (d == null) d = findDbIn(st);
                if (d == null) {
                    Object cfg = XposedHelpers.callMethod(core, "i");   // 配置存储，通常也挂在同一个 DB 上
                    if (cfg != null && findMethod(cfg.getClass(), Cursor.class, String.class, String[].class) != null) d = cfg;
                    if (d == null) d = findDbIn(cfg);
                }
            } catch (Throwable t) {}
        }
        if (d != null) DB0 = d;
        if (d == null) throw new RuntimeException("db0 unavailable");
        return d;
    }

    static Object findDbIn(Object st) {
        if (st == null) return null;
        Class<?> c = st.getClass();
        for (int lv = 0; c != null && lv < 6; lv++, c = c.getSuperclass()) {
            java.lang.reflect.Field[] fs = c.getDeclaredFields();
            for (java.lang.reflect.Field f : fs) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(st);
                    if (v == null) continue;
                    if (findMethod(v.getClass(), Cursor.class, String.class, String[].class) != null) return v;
                } catch (Throwable t) {}
            }
        }
        return null;
    }


    static String selfWxid() {
        if (SELF_WXID != null) return SELF_WXID;
        try {
            Object db = db0();
            Cursor c = rawQuery(db, "select username from userinfo limit 1", null);
            try {
                if (c != null && c.moveToFirst()) {
                    String u = c.getString(0);
                    if (u != null && u.trim().length() > 0) SELF_WXID = u.trim();
                }
            } finally { if (c != null) c.close(); }
        } catch (Throwable t) { T("self wxid err " + t); }
        if (SELF_WXID == null) {
            try {
                java.io.File f = new java.io.File(computeWxData() + "/shared_prefs/com.tencent.mm_preferences.xml");
                java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf("login_weixin_username");
                    if (i < 0) continue;
                    int a = line.indexOf('>', i), b = line.indexOf('<', a + 1);
                    if (a > 0 && b > a) { SELF_WXID = line.substring(a + 1, b).trim(); break; }
                }
                r.close();
                if (SELF_WXID != null) T("self wxid from prefs: " + SELF_WXID);
            } catch (Throwable t) { T("self wxid prefs err " + t); }
        }
        return SELF_WXID;
    }

    static void showBizPicker(final Activity act) {
        toast(act, "\u6b63\u5728\u8bfb\u53d6\u516c\u4f17\u53f7\u2026");
        new Thread(new Runnable() {
            public void run() {
                final java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
                final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
                try {
                    Object db = db0();
                    Cursor c = (Cursor) rawQuery(db,
                            "select username, nickname, conRemark from rcontact where username like 'gh_%' and type != 0", null);
                    try {
                        while (c != null && c.moveToNext()) {
                            String id = c.getString(0);
                            String nm = null, rm = null;
                            try { nm = c.getString(1); } catch (Throwable t) {}
                            try { rm = c.getString(2); } catch (Throwable t) {}
                            if (rm != null && rm.trim().length() > 0) nm = rm;
                            if (nm == null || nm.trim().length() == 0) nm = id;
                            ids.add(id);
                            names.add(nm.trim());
                        }
                    } finally { if (c != null) c.close(); }
                    T("biz picker: " + ids.size());
                } catch (Throwable t) { T("biz picker err " + t); }
                act.runOnUiThread(new Runnable() {
                    public void run() { buildBizDialog(act, ids, names); }
                });
            }
        }).start();
    }

    static void buildBizDialog(final Activity act,
                               final java.util.ArrayList<String> allIds,
                               final java.util.ArrayList<String> allNames) {
        try {
            final java.util.HashSet<String> sel = new java.util.HashSet<String>();
            final java.util.ArrayList<String> curIds = new java.util.ArrayList<String>(allIds);
            final java.util.ArrayList<String> curNames = new java.util.ArrayList<String>(allNames);

            android.widget.LinearLayout root = new android.widget.LinearLayout(act);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);

            android.widget.LinearLayout bar = new android.widget.LinearLayout(act);
            bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            final android.widget.TextView selAll = new android.widget.TextView(act);
            selAll.setText("\u5168\u9009  ");
            final android.widget.TextView selNone = new android.widget.TextView(act);
            selNone.setText("\u6e05\u7a7a");
            final android.widget.TextView fixBtn = new android.widget.TextView(act);
            fixBtn.setText("   修复已取关会话");
            bar.addView(selAll);
            bar.addView(selNone);
            bar.addView(fixBtn);
            root.addView(bar);
            fixBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { repairBizConvs(act); }
            });

            final android.widget.EditText search = new android.widget.EditText(act);
            search.setHint("\u641c\u7d22\u516c\u4f17\u53f7");
            root.addView(search);

            final android.widget.ListView lv = new android.widget.ListView(act);
            int layoutId = 0;
            try {
                Class<?> rl = Class.forName("android.R$layout");
                layoutId = rl.getField("simple_list_item_multiple_choice").getInt(null);
            } catch (Throwable t) { T("biz layout err " + t); }
            final int layId = layoutId;
            final android.widget.ArrayAdapter<String> ad =
                    new android.widget.ArrayAdapter<String>(act, layId, new java.util.ArrayList<String>(curNames));
            lv.setAdapter(ad);
            lv.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 900);
            root.addView(lv, lp);

            search.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(android.text.Editable s) {
                    syncBizChecked(lv, curIds, sel);
                    String q = s == null ? "" : String.valueOf(s).trim().toLowerCase();
                    curIds.clear(); curNames.clear();
                    for (int i = 0; i < allIds.size(); i++) {
                        if (q.length() == 0 || allNames.get(i).toLowerCase().contains(q)) {
                            curIds.add(allIds.get(i));
                            curNames.add(allNames.get(i));
                        }
                    }
                    android.widget.ArrayAdapter<String> na =
                            new android.widget.ArrayAdapter<String>(act, layId,
                                    new java.util.ArrayList<String>(curNames));
                    lv.setAdapter(na);
                    for (int i = 0; i < curIds.size(); i++)
                        if (sel.contains(curIds.get(i))) lv.setItemChecked(i, true);
                }
            });

            selAll.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int i = 0; i < curIds.size(); i++) { lv.setItemChecked(i, true); sel.add(curIds.get(i)); }
                    toast(act, "\u5df2\u9009 " + sel.size() + " \u4e2a");
                }
            });
            selNone.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    sel.clear();
                    for (int i = 0; i < curIds.size(); i++) lv.setItemChecked(i, false);
                    toast(act, "\u5df2\u6e05\u7a7a");
                }
            });

            new android.app.AlertDialog.Builder(act)
                .setTitle("\u53d6\u6d88\u5173\u6ce8\u516c\u4f17\u53f7\uff08\u52fe\u9009\u540e\u5220\u9664\uff09")
                .setView(root)
                .setPositiveButton("\u5220\u9664\u9009\u4e2d", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        syncBizChecked(lv, curIds, sel);
                        if (sel.isEmpty()) { toast(act, "\u6ca1\u6709\u9009\u4e2d"); return; }
                        final java.util.ArrayList<String> list = new java.util.ArrayList<String>(sel);
                        new android.app.AlertDialog.Builder(act)
                            .setTitle("\u8981\u53d6\u6d88\u5173\u6ce8\u8fd9 " + list.size() + " \u4e2a\u516c\u4f17\u53f7\u5417\uff1f")
                            .setPositiveButton("\u786e\u5b9a", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface dd, int ww) {
                                    deleteBizAccounts(list);
                                }
                            })
                            .setNegativeButton("\u53d6\u6d88", null)
                            .show();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
            T("biz dialog shown, total=" + allIds.size());
        } catch (Throwable t) { T("biz dialog err " + t); }
    }

    static void syncBizChecked(Object lv, java.util.ArrayList<String> curIds, java.util.HashSet<String> sel) {
        try {
            android.util.SparseBooleanArray arr = (android.util.SparseBooleanArray)
                    XposedHelpers.callMethod(lv, "getCheckedItemPositions");
            if (arr == null) return;
            for (int i = 0; i < curIds.size(); i++) {
                if (arr.get(i)) sel.add(curIds.get(i));
                else sel.remove(curIds.get(i));
            }
        } catch (Throwable t) { T("syncBizChecked err " + t); }
    }

    static void execSql(Object db, String sql) {
        Cursor c = null;
        try {
            c = (Cursor) rawQuery(db, sql, null);
            if (c != null) c.moveToFirst();
        } catch (Throwable t) {
            T("execSql err " + t + " sql=" + sql);
        } finally {
            if (c != null) try { c.close(); } catch (Throwable t) {}
        }
    }

    /** 131：真正的 execSQL —— 以前用 rawQuery 跑 DELETE，驱动会先 getCount() 把整个删除跑完，
     *  几十万行的 message 删除会把库锁住几分钟，主线程更新直接 ANR（上报里抓到过）。 */
    static void execSqlDirect(Object db, String sql) {
        try {
            java.lang.reflect.Method m = null;
            for (Class<?> k = db.getClass(); k != null && m == null; k = k.getSuperclass()) {
                try { m = k.getDeclaredMethod("execSQL", String.class); } catch (Throwable t) {}
            }
            if (m != null) {
                try { m.setAccessible(true); } catch (Throwable t) {}
                m.invoke(db, sql);
                return;
            }
        } catch (Throwable t) { T("execSqlDirect err " + t); }
        execSql(db, sql);
    }

    static int countOf(Object db, String sql) {
        Cursor c = null;
        try {
            c = (Cursor) rawQuery(db, sql, null);
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } catch (Throwable t) {}
        finally { if (c != null) try { c.close(); } catch (Throwable t) {} }
        return 0;
    }



    /** v201：微信的会话对象缓存不会因为 SQL 删除而更新 —— 手动把被删的会话从缓存 Map 里摘掉 */
    static int purgeConvCache(Object conv, java.util.List<String> users) {
        int removed = 0;
        try {
            Class<?> c = conv.getClass();
            int depth = 0;
            while (c != null && c != Object.class && depth++ < 6) {
                java.lang.reflect.Field[] fs = c.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    try {
                        if (!java.util.Map.class.isAssignableFrom(fs[i].getType())) continue;
                        fs[i].setAccessible(true);
                        Object o = fs[i].get(conv);
                        if (!(o instanceof java.util.Map)) continue;
                        java.util.Map m = (java.util.Map) o;
                        synchronized (m) {
                            for (int k = 0; k < users.size(); k++) {
                                try { if (m.remove(users.get(k)) != null) removed++; } catch (Throwable t2) {}
                            }
                        }
                    } catch (Throwable t3) {}
                }
                c = c.getSuperclass();
            }
            try { if (!new java.io.File(WXDIR + "clear.debug").exists()) throw new RuntimeException("skip");
                Class<?> c2 = conv.getClass(); int dep2 = 0;
                while (c2 != null && c2 != Object.class && dep2++ < 6) {
                    for (java.lang.reflect.Field fx : c2.getDeclaredFields()) {
                        try {
                            fx.setAccessible(true);
                            Object ov = fx.get(conv);
                            String extra = "";
                            if (ov instanceof java.util.Map) extra = " size=" + ((java.util.Map) ov).size();
                            else if (ov instanceof java.util.Collection) extra = " n=" + ((java.util.Collection) ov).size();
                            T("clear field " + c2.getSimpleName() + "." + fx.getName() + " " + fx.getType().getSimpleName() + extra);
                        } catch (Throwable t9) {}
                    }
                    c2 = c2.getSuperclass();
                }
            } catch (Throwable t8) {}
        } catch (Throwable t) { T("clear purge err " + t); }
        return removed;
    }


    /** v202：CUR_ACT 可能为空，直接从 ActivityThread 里捞当前 Activity */
    static Activity currentActivityNow() {
        try {
            java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
            Activity a0 = ref == null ? null : ref.get();
            if (a0 != null && !a0.isFinishing() && !a0.isDestroyed()) return a0;
        } catch (Throwable t) {}
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = XposedHelpers.callStaticMethod(at, "currentActivityThread");
            Object acts = XposedHelpers.getObjectField(app, "mActivities");
            if (acts instanceof java.util.Map) {
                for (Object v : ((java.util.Map) acts).values()) {
                    try {
                        Object a = XposedHelpers.getObjectField(v, "activity");
                        if (a instanceof Activity) {
                            Activity act = (Activity) a;
                            if (!act.isFinishing() && !act.isDestroyed()) return act;
                        }
                    } catch (Throwable t) {}
                }
            }
        } catch (Throwable t) { T("clear cur act err " + t); }
        return null;
    }

    /** v203：清会话后强制刷新首页会话列表。
     *  首页是 ListView（ConversationListView）+ 适配器自带缓存，光发 notify 未必能剔除已删项，
     *  所以 notify 之后再重建一次首页（WXDIR/clear.norecreate 可关掉重建做对比）。 */
    static void refreshConvListUi() {
        try {
            final Activity a = currentActivityNow();
            if (a == null) { T("clear ui: no act"); return; }
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        java.util.ArrayList<Object> vs = new java.util.ArrayList<Object>();
                        collectLists(a.getWindow().getDecorView(), vs, 0);
                        int abs = 0, rv = 0;
                        for (int i = 0; i < vs.size(); i++) {
                            try {
                                Object ad = XposedHelpers.callMethod(vs.get(i), "getAdapter");
                                if (ad == null) continue;
                                XposedHelpers.callMethod(ad, "notifyDataSetChanged");
                                if (vs.get(i) instanceof android.widget.AbsListView) abs++; else rv++;
                            } catch (Throwable t) {}
                        }
                        T("clear ui: lists=" + vs.size() + " abs=" + abs + " rv=" + rv);
                    } catch (Throwable t) { T("clear ui err " + t); }
                }
            });
            if (new java.io.File(WXDIR + "clear.norecreate").exists()) { T("clear recreate off"); return; }
            new Thread(new Runnable() {
                public void run() {
                    try { Thread.sleep(600); } catch (Throwable t) {}
                    try {
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    if (a.isFinishing() || a.isDestroyed()) { T("clear recreate skipped"); return; }
                                    T("clear recreate " + a.getClass().getName());
                                    a.recreate();
                                } catch (Throwable t) { T("clear recreate err " + t); }
                            }
                        });
                    } catch (Throwable t) { T("clear recreate outer " + t); }
                }
            }).start();
        } catch (Throwable t) { T("clear ui outer " + t); }
    }

    /** v206：不重建 Activity 的软刷新 —— 通知适配器 + invalidateViews，红点会按新数据重画 */
    static void softRefreshConvList() {
        try {
            final Activity a = currentActivityNow();
            if (a == null) { T("read ui soft: no act"); return; }
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        java.util.ArrayList<Object> vs = new java.util.ArrayList<Object>();
                        collectLists(a.getWindow().getDecorView(), vs, 0);
                        int n = 0;
                        for (int i = 0; i < vs.size(); i++) {
                            Object v = vs.get(i);
                            try {
                                Object ad = XposedHelpers.callMethod(v, "getAdapter");
                                if (ad != null) { XposedHelpers.callMethod(ad, "notifyDataSetChanged"); n++; }
                            } catch (Throwable t) {}
                            try {
                                if (v instanceof android.widget.AbsListView)
                                    XposedHelpers.callMethod(v, "invalidateViews");
                            } catch (Throwable t) {}
                        }
                        T("read ui soft lists=" + vs.size() + " notify=" + n);
                    } catch (Throwable t) { T("read ui soft err " + t); }
                }
            });
        } catch (Throwable t) { T("read ui soft outer " + t); }
    }

    static void collectLists(Object v, java.util.ArrayList<Object> out, int d) {
        if (v == null || d > 25) return;
        try {
            if (v instanceof android.widget.AbsListView
                    || v.getClass().getName().contains("RecyclerView")) out.add(v);
        } catch (Throwable t) {}
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectLists(g.getChildAt(i), out, d + 1);
        }
    }

    /** 131：分批删某个会话的消息（每批 400 行，批间让出 DB），避免长时间独占写锁 */
    static int clearMessagesOf(Object db, String talker) {
        int total = 0;
        String safe = talker.replace("'", "''");
        for (int i = 0; i < 2000; i++) {
            int n = countOf(db, "select count(*) from message where talker='" + safe + "'");
            if (n <= 0) break;
            execSqlDirect(db, "delete from message where rowid in (select rowid from message where talker='"
                    + safe + "' limit 400)");
            total += Math.min(n, 400);
            try { Thread.sleep(25); } catch (Throwable t) {}
        }
        return total;
    }

    /** 131：分批删会话（每批 200 行） */
    static int clearConvsOf(Object db, String inList) {
        int total = 0;
        for (int i = 0; i < 200; i++) {
            int n = countOf(db, "select count(*) from rconversation where username in (" + inList + ")");
            if (n <= 0) break;
            execSqlDirect(db, "delete from rconversation where rowid in "
                    + "(select rowid from rconversation where username in (" + inList + ") limit 200)");
            total += Math.min(n, 200);
            try { Thread.sleep(20); } catch (Throwable t) {}
        }
        return total;
    }

    static String argsStr(Object[] as) {
        if (as == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < as.length; i++) {
            if (i > 0) sb.append(", ");
            Object a = as[i];
            sb.append(a == null ? "null" : (a.getClass().getSimpleName() + "(" + shorten(String.valueOf(a)) + ")"));
        }
        return sb.append(']').toString();
    }

    /** 143：取关公众号的网络场景探针 —— 抓 y61.e 的构造参数与调用栈 */
    static void hookUnfollowProbe(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("y61.e", cl);
            for (final java.lang.reflect.Constructor<?> c : k.getDeclaredConstructors()) {
                try { c.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(c, new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (!NETPROBE.exists()) return;
                            try {
                                StringBuilder sb = new StringBuilder();
                                for (Object a : p.args) sb.append(shorten(String.valueOf(a))).append(" | ");
                                StackTraceElement[] st = new Throwable().getStackTrace();
                                StringBuilder stb = new StringBuilder();
                                for (int i = 0; i < st.length && i < 10; i++) {
                                    stb.append('\n').append("    ").append(st[i].getClassName())
                                            .append('.').append(st[i].getMethodName())
                                            .append(":").append(st[i].getLineNumber());
                                }
                                T("UNFOLLOW scene args=[" + sb + "]" + stb);
                            } catch (Throwable t) {}
                        }
                    });
                } catch (Throwable t) {}
            }
            T("unfollow probe ok " + k.getName());
            // 144：把取关管理器 aw1.n 的方法签名打出来，找可以直接调用的入口
            try {
                Class<?> mgr = XposedHelpers.findClass("aw1.n", cl);
                for (java.lang.reflect.Method mm : mgr.getDeclaredMethods()) {
                    StringBuilder pb = new StringBuilder();
                    for (Class<?> pc : mm.getParameterTypes()) {
                        if (pb.length() > 0) pb.append(',');
                        pb.append(pc.getName());
                    }
                    T("aw1.n " + mm.getName() + "(" + pb + ") -> " + mm.getReturnType().getName());
                }
            } catch (Throwable t) { T("aw1.n dump err " + t); }
            // 145：抓 aw1.n.a 的实参，确认第二个参数哪个值代表「取关」
            try {
                Class<?> mgr2 = XposedHelpers.findClass("aw1.n", cl);
                for (final java.lang.reflect.Method mm2 : mgr2.getDeclaredMethods()) {
                    if (!"a".equals(mm2.getName())) continue;
                    try { mm2.setAccessible(true); } catch (Throwable t) {}
                    XposedBridge.hookMethod(mm2, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (!NETPROBE.exists()) return;
                            try { T("UNFOLLOW call aw1.n.a " + argsStr(p.args)); } catch (Throwable t) {}
                        }
                    });
                }
            } catch (Throwable t) { T("aw1.n hook err " + t); }
        } catch (Throwable t) { T("unfollow probe err " + t); }
    }

    /** 140：走微信自己的「删除该聊天」入口（com.tencent.mm.storage.m4.k(String)），
     *  它会删会话 + 删记录 + 通知列表刷新，不用重启微信。 */
    static boolean delConvViaApi(String username) {
        try {
            Class<?> locator = Class.forName(SVC_LOCATOR, true, APP_CL);
            Class<?> iface = Class.forName(SVC_IFACE, true, APP_CL);
            Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
            Object conv = XposedHelpers.callMethod(svc, "Hj");
            if (conv == null) return false;
            XposedHelpers.callMethod(conv, "k", username);
            return true;
        } catch (Throwable t) {
            T("delConvViaApi err " + username + " " + t);
            return false;
        }
    }

    /** 138：删完会话后让微信自己刷新一次列表（不然内存里的旧列表还会显示被删的会话，要重启才消失） */
    static void refreshConvList(String anyUsername) {
        try {
            Class<?> locator = Class.forName(SVC_LOCATOR, true, APP_CL);
            Class<?> iface = Class.forName(SVC_IFACE, true, APP_CL);
            Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
            Object conv = XposedHelpers.callMethod(svc, "Hj");
            if (conv != null && anyUsername != null) {
                XposedHelpers.callMethod(conv, "M", anyUsername);
                T("refresh conv list via M(" + anyUsername + ")");
            }
        } catch (Throwable t) { T("refresh conv list err " + t); }
    }

    /** 146：真正走微信的取关请求 —— aw1.n.a(username, true)，和手动点「不再关注」同一条路 */
    static boolean unfollowViaApi(String username) {
        try {
            Class<?> mgr = XposedHelpers.findClass("aw1.n", APP_CL);
            try {
                XposedHelpers.callStaticMethod(mgr, "a", username, Boolean.TRUE);
                return true;
            } catch (Throwable ignore) {
                Object inst = mgr.getDeclaredConstructor().newInstance();
                XposedHelpers.callMethod(inst, "a", username, Boolean.TRUE);
                return true;
            }
        } catch (Throwable t) {
            T("unfollowViaApi err " + username + " " + t);
            return false;
        }
    }

    static void deleteBizAccounts(final java.util.ArrayList<String> ids) {
        new Thread(new Runnable() {
            public void run() {
                int sent = 0, confirmed = 0, failed = 0;
                try { bizPendingRecheck(); } catch (Throwable t) { T("biz.pending recheck err " + t); }
                Object db = null;
                try { db = db0(); } catch (Throwable t) { T("biz db err " + t); }
                for (String id : ids) {
                    try {
                        // 1.63（F06）：不再「网络取关失败 → 直接改多表 → 报成功」。
                        // 真实取关只走微信自己的入口，结果分三档：已发起 / 已确认 / 失败。
                        if (!unfollowViaApi(id)) {
                            failed++;
                            T("biz unfollow not sent " + id);
                            Thread.sleep(200);
                            continue;
                        }
                        sent++;
                        Thread.sleep(800);          // 给服务器/微信落库留时间，但这不是成功依据
                        boolean okNow = false;
                        try {
                            Object dbx = db;
                            if (dbx == null) dbx = db0();
                            okNow = bizUnfollowed(dbx, id);
                        } catch (Throwable t2) { T("biz confirm err " + t2); }
                        if (okNow) {
                            confirmed++;
                            bizOffAdd(id);                  // S08：只有确认后才进已取关名单
                            bizPendingDel(id);
                        } else {
                            bizPendingAdd(id);              // 未确认：留 pending，重启后复查
                            T("biz unfollow pending " + id);
                        }
                    } catch (Throwable t) { T("biz del err " + id + " " + t); }
                }
                T("biz unfollow sent=" + sent + " confirmed=" + confirmed + " failed=" + failed + " /" + ids.size());
                toastMain("\u5df2\u53d1\u8d77\u53d6\u6d88\u5173\u6ce8 " + sent + " \u4e2a\uff08\u786e\u8ba4 " + confirmed
                        + "\uff09\uff0c\u5931\u8d25 " + failed + " \u4e2a");
            }
        }).start();
    }

    /** v123：把「已取关但会话还在首页」的公众号一次性收回订阅号分组 */
    static void repairBizConvs(final Activity act) {
        new Thread(new Runnable() {
            public void run() {
                int n = 0;
                try {
                    Object db = db0();
                    execSql(db, "update rconversation set parentRef='officialaccounts', unReadCount=0 "
                            + "where username like 'gh_%' and username not in "
                            + "(select username from rcontact where type != 0)");
                    Cursor c = (Cursor) rawQuery(db, "select changes()", null);
                    if (c != null) { try { if (c.moveToFirst()) n = c.getInt(0); } finally { c.close(); } }
                    // 同步进 biz.off，后面新推送的会话也会被自动塞回订阅号
                    Cursor q = (Cursor) rawQuery(db, "select username from rcontact where username like 'gh_%' and type=0", null);
                    try {
                        while (q != null && q.moveToNext()) bizOffAdd(q.getString(0));
                    } finally { if (q != null) q.close(); }
                    T("biz repair done rows=" + n + " off=" + BIZ_OFF.size());
                } catch (Throwable t) { T("biz repair err " + t); }
                toastMain("已收回 " + n + " 个公众号会话");
            }
        }).start();
    }

    static int notifyMsgValue() {
        try {
            String s = readText(NEWROOM_NOTIFY_PATH);
            if (s != null && s.trim().length() > 0) return Integer.parseInt(s.trim());
        } catch (Throwable t) {}
        return 0;   // 0 = 免打扰（微信内部 notifyMsg = isMute^1，免打扰时发 0）
    }

    static void muteRoom(final String roomId, final int notifyMsg) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ClassLoader cl = APP_CL;
                    Object svc = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("fi5.n0", cl), "c",
                            XposedHelpers.findClass("kg0.e", cl));
                    Object ops = XposedHelpers.callMethod(svc, "Ej", roomId);
                    Object task = XposedHelpers.callMethod(ops, "h", roomId,
                            Integer.valueOf(notifyMsg), Integer.valueOf(0));
                    XposedHelpers.callMethod(task, "b");
                    T("newroom: MUTED " + roomId + " notify=" + notifyMsg);
                } catch (Throwable t) { T("newroom mute err " + t); }
            }
        }).start();
    }

    static String groupNameFromDb(Object db, String id) {
        String[] sqls = {
                "select displayName from chatroom where chatroomname='" + id + "'",
                "select displayName from rconversation where username='" + id + "'"
        };
        for (String sql : sqls) {
            try {
                Cursor c = (Cursor) rawQuery(db, sql, null);
                try {
                    if (c != null && c.moveToFirst()) {
                        String v = c.getString(0);
                        if (v != null && v.trim().length() > 0) {
                            T("picker name fallback: " + id + " -> " + v.trim());
                            return v.trim();
                        }
                    }
                } finally {
                    if (c != null) c.close();
                }
            } catch (Throwable t) { T("picker name fallback miss [" + sql + "] " + t); }
        }
        return null;
    }

    static void showGroupPicker(final Activity act) {
        toast(act, "\u6b63\u5728\u8bfb\u53d6\u7fa4\u5217\u8868\u2026");
        new Thread(new Runnable() {
            public void run() {
                final java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
                final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
                try {
                    ClassLoader cl = act.getClass().getClassLoader();
                    Class<?> locator = Class.forName(SVC_LOCATOR, true, cl);
                    Class<?> iface = Class.forName(SVC_IFACE, true, cl);
                    Object svc = XposedHelpers.callStaticMethod(locator, "v", iface);
                    Object conv = XposedHelpers.callMethod(svc, "Hj");
                    Object db = XposedHelpers.getObjectField(conv, "d");
                    Cursor c;
                    boolean hasName = true;
                    try {
                        c = (Cursor) rawQuery(db,
                                "select username, conRemark, nickname from rcontact where username like '%@chatroom'",
                                null);
                    } catch (Throwable t) {
                        hasName = false;
                        c = (Cursor) rawQuery(db,
                                "select username from rcontact where username like '%@chatroom'", null);
                    }
                    while (c != null && c.moveToNext()) {
                        String id = c.getString(0);
                        String nm = "";
                        if (hasName) {
                            try { nm = c.getString(1); } catch (Throwable t) {}
                            if (nm == null || nm.trim().length() == 0) {
                                try { nm = c.getString(2); } catch (Throwable t) {}
                            }
                        }
                        if (nm == null || nm.trim().length() == 0) {
                            String fb = groupNameFromDb(db, id);
                            if (fb != null) nm = fb;
                        }
                        if (nm == null || nm.trim().length() == 0) nm = id;
                        ids.add(id);
                        names.add(nm.trim());
                    }
                    if (c != null) c.close();
                    T("group picker: loaded " + ids.size() + " groups");
                } catch (Throwable t) { T("group picker load err " + t); }
                act.runOnUiThread(new Runnable() {
                    public void run() { buildGroupDialog(act, ids, names); }
                });
            }
        }).start();
    }

    static void buildGroupDialog(final Activity act,
                                 final java.util.ArrayList<String> allIds,
                                 final java.util.ArrayList<String> allNames) {
        try {
            final java.util.HashSet<String> sel = loadWhitelist();
            final java.util.ArrayList<String> curIds = new java.util.ArrayList<String>(allIds);
            final java.util.ArrayList<String> curNames = new java.util.ArrayList<String>(allNames);

            final android.widget.LinearLayout root = new android.widget.LinearLayout(act);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);

            final android.widget.EditText search = new android.widget.EditText(act);
            search.setHint("\u641c\u7d22\u7fa4\u540d");
            root.addView(search);

            final android.widget.ListView lv = new android.widget.ListView(act);
            int layoutId = 0;
            try {
                Class<?> rl = Class.forName("android.R$layout");
                layoutId = rl.getField("simple_list_item_multiple_choice").getInt(null);
            } catch (Throwable t) { T("layout id err " + t); }
            final int layId = layoutId;
            android.widget.ArrayAdapter<String> ad =
                    new android.widget.ArrayAdapter<String>(act, layId,
                            new java.util.ArrayList<String>(curNames));
            lv.setAdapter(ad);
            lv.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            for (int i = 0; i < curIds.size(); i++)
                if (sel.contains(curIds.get(i))) lv.setItemChecked(i, true);
            root.addView(lv);

            search.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(android.text.Editable s) {
                    syncChecked(lv, curIds, sel);
                    String q = s == null ? "" : String.valueOf(s).trim().toLowerCase();
                    curIds.clear();
                    curNames.clear();
                    for (int i = 0; i < allIds.size(); i++) {
                        if (q.length() == 0 || allNames.get(i).toLowerCase().contains(q)) {
                            curIds.add(allIds.get(i));
                            curNames.add(allNames.get(i));
                        }
                    }
                    android.widget.ArrayAdapter<String> na =
                            new android.widget.ArrayAdapter<String>(act, layId,
                                    new java.util.ArrayList<String>(curNames));
                    lv.setAdapter(na);
                    for (int i = 0; i < curIds.size(); i++)
                        if (sel.contains(curIds.get(i))) lv.setItemChecked(i, true);
                }
            });

            new android.app.AlertDialog.Builder(act)
                .setTitle("\u53ea\u62a2\u8fd9\u4e9b\u7fa4\u7684\u7ea2\u5305\uff08\u4e0d\u52fe=\u4e0d\u9650\uff09")
                .setView(root)
                .setPositiveButton("\u4fdd\u5b58", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        syncChecked(lv, curIds, sel);
                        saveWhitelist(sel);
                        toast(act, "\u5df2\u4fdd\u5b58 " + sel.size() + " \u4e2a\u7fa4");
                        T("group picker saved: " + sel.size() + " -> "
                          + (sel.isEmpty() ? "@none" : sel.toString()));
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
            T("group dialog shown, groups=" + allIds.size());
        } catch (Throwable t) { T("group dialog err " + t); }
    }

    static void syncChecked(Object lv, java.util.ArrayList<String> curIds, java.util.HashSet<String> sel) {
        try {
            android.util.SparseBooleanArray arr = (android.util.SparseBooleanArray)
                    XposedHelpers.callMethod(lv, "getCheckedItemPositions");
            if (arr == null) return;
            for (int i = 0; i < curIds.size(); i++) {
                if (arr.get(i)) sel.add(curIds.get(i));
                else sel.remove(curIds.get(i));
            }
        } catch (Throwable t) { T("syncChecked err " + t); }
    }

    static java.util.HashSet<String> loadWhitelist() {
        java.util.HashSet<String> set = new java.util.HashSet<String>();
        try {
            String s = readText(RP_WL_PATH);
            if (s != null) {
                String[] lines = s.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.length() > 0) set.add(line);
                }
            }
        } catch (Throwable t) {}
        return set;
    }

    static void saveWhitelist(java.util.HashSet<String> set) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(RP_WL_PATH, false);
            if (set.isEmpty()) fw.write("@none\n");
            else for (String id : set) fw.write(id + "\n");
            fw.close();
        } catch (Throwable t) { T("save whitelist err " + t); }
    }

    static String readText(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return null;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString();
        } catch (Throwable t) { return null; }
    }

    static boolean inWhitelist(String talker) {
        try {
            String wl = readText(RP_WL_PATH);
            if (wl == null || wl.trim().length() == 0) return true;
            for (String line : wl.split("\n")) {
                String s = line.trim();
                if (s.length() == 0 || s.startsWith("#")) continue;
                if (s.equals(talker)) return true;
            }
            return false;
        } catch (Throwable t) { return true; }
    }

    static long rpDelayRandom() {
        long lo = 400, hi = 900;
        try {
            String s = readText(RP_DELAY_PATH);
            if (s != null) {
                s = s.trim();
                int dash = s.indexOf('-');
                if (dash > 0) {
                    lo = Long.parseLong(s.substring(0, dash).trim());
                    hi = Long.parseLong(s.substring(dash + 1).trim());
                } else if (s.length() > 0) {
                    lo = hi = Long.parseLong(s);
                }
            }
        } catch (Throwable t) {}
        if (hi < lo) { long x = lo; lo = hi; hi = x; }
        if (hi == lo) return lo;
        return lo + (long) (Math.random() * (hi - lo));
    }

    static void toastMain(final String msg) {
        try {
            if (APP_CTX == null) return;
            Class<?> looperCls = Class.forName("android.os.Looper");
            Object main = XposedHelpers.callStaticMethod(looperCls, "getMainLooper");
            Class<?> handlerCls = Class.forName("android.os.Handler");
            java.lang.reflect.Constructor<?> ctor = handlerCls.getDeclaredConstructor(looperCls);
            ctor.setAccessible(true);
            Object h = ctor.newInstance(main);
            Runnable r = new Runnable() {
                public void run() {
                    try { Toast.makeText(APP_CTX, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable t) {}
                }
            };
            XposedHelpers.callMethod(h, "post", r);
        } catch (Throwable t) { T("toast err " + t); }
    }



    static String brief(String s) {
        if (s == null) return "null";
        s = s.replace("\n", " ");
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    static String nativeUrl(String content) {
        if (content == null) return null;
        try {
            int i = content.indexOf("<nativeurl>");
            int j = content.indexOf("</nativeurl>");
            if (i < 0 || j <= i) return null;
            String s = content.substring(i + 11, j).trim();
            // 微信的 content 里 nativeurl 常被 CDATA 包着，剥掉再给微信自己用
            if (s.startsWith("<![CDATA[")) s = s.substring(9);
            if (s.endsWith("]]>")) s = s.substring(0, s.length() - 3);
            return s.trim().replace("&amp;", "&");
        } catch (Throwable t) { return null; }
    }

    static android.content.Context rememberAppContext(Object seed, String source) {
        boolean missing = APP_CTX == null;
        android.content.Context app = RedPacketUi.application(APP_CTX, seed);
        if (app != null) APP_CTX = app;
        if (missing && app != null) T("rp ui context ready source=" + source);
        return app;
    }

    static void openReceiveUI(final String url, final long msgId, final String talker, final String content) {
        try {
            if (MODULE_OFF || RedPacketRuntime.silentSelected()
                    || !ReceiptStore.permitsMessage(ReceiptConfig.Feature.RED_PACKET, receiptAccount(), talker, content)) return;
            final Activity current = currentActivityNow();
            final android.content.Context app = rememberAppContext(current, "receive");
            synchronized (RP_SEEN) {
                if (RP_SEEN.contains(url)) { T("rp: dup url"); return; }
                RP_SEEN.add(url);
                boolean requested = false;
                try {
                    String source = RedPacketUi.open(app, current, RedPacketUiNative.prepare(APP_CL, url, msgId, talker, content));
                    requested = !"no-context".equals(source);
                    T("rp: receive ui launch requested=" + requested + " source=" + source
                            + " nativeEntry=true scope=" + ReceiptConfig.Scope.of(talker));
                } finally {
                    if (!requested) RP_SEEN.remove(url);
                }
            }
        } catch (Throwable failure) { T("rp: receive ui launch failed " + failure.getClass().getSimpleName()); }
    }

    static String redPacketUiState(Activity act) {
        try {
            Object target = XposedHelpers.getObjectField(act, "o");
            if (!(target instanceof android.view.View)) return "button-missing";
            android.view.View button = (android.view.View) target;
            return "shown=" + button.isShown() + ",enabled=" + button.isEnabled()
                    + ",focus=" + act.hasWindowFocus() + ",closing=" + act.isFinishing();
        } catch (Throwable failure) { return "unavailable:" + failure.getClass().getSimpleName(); }
    }

    static void clickKai(final Activity act) { tryClickKai(act); }
    static boolean tryClickKai(final Activity act) {
        try {
            Object root = act.getWindow().getDecorView();
            Object btn = findButtonByText(root, "\u5f00");
            if (btn == null) btn = findButtonByText(root, "\u958b");
            if (btn == null) {
                try { btn = XposedHelpers.getObjectField(act, "o"); } catch (Throwable t) {}
            }
            if (!(btn instanceof android.view.View)) return false;
            android.view.View button=(android.view.View)btn;
            if (!button.isShown() || !button.isEnabled() || !act.hasWindowFocus()) return false;
            Object ok = XposedHelpers.callMethod(btn, "performClick");
            T("rp: clicked 开 -> " + ok);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) { T("rp click err " + t); return false; }
    }

    static Object findButtonByText(Object v, String text) {
        try {
            String cn = v.getClass().getName();
            if (cn.contains("Button")) {
                try {
                    Object t = XposedHelpers.callMethod(v, "getText");
                    if (t != null && text.equals(String.valueOf(t).trim())) return v;
                } catch (Throwable t) {}
            }
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                Object r = findButtonByText(c, text);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    /** v117：调试开关过期清理。
     *  img.probe / sendmsg.debug / img.prefetch.on 这些是排查问题用的，忘了删会一直拖慢聊天页
     *  （实测 img.prefetch.on 强制假装 Wi-Fi，会让微信在开聊天时疯狂预下载图片，首屏明显卡）。
     *  超过 48 小时自动删掉，正在调试的（刚建的）不受影响。 */
    static void expireDebugFlags() {
        String[] names = {"img.probe", "img.prefetch.on", "dl.probe", "sendmsg.debug",
                "title.sample", "listprobe", "dbg_sql", "force.broken", "kernel.silent.test", "revoke.test",
                "status.fast", "status.offline"};
        long now = System.currentTimeMillis();
        for (String n : names) {
            try {
                java.io.File f = new java.io.File(WXDIR + n);
                if (f.exists() && now - f.lastModified() > 48L * 3600 * 1000 && f.delete())
                    T("debug flag expired: " + n);
            } catch (Throwable t) {}
        }
    }

    // ===== 1.48：远程停用 + 许愿回复 =====
    static final String OFF_FILE = WXDIR + "module.off";
    static volatile boolean MODULE_OFF = false;
    static volatile String OFF_REASON = null;
    static volatile String SERVER_REPLY = null;
    static volatile long LAST_STATUS_AT = 0;
    static final String FEAT_CACHE = WXDIR + "feat.cache";
    static volatile String SERVER_TIER = "免费版";                                      // 当前等级名；默认免费版
    static final java.util.HashSet<String> FEATURES = new java.util.HashSet<String>();   // 服务器下发的已授权功能
    static final java.util.HashSet<String> GATED = new java.util.HashSet<String>();      // 需要单独授权的功能 key（实现功能时登记）

    /** 以后按功能发授权时用：GATED 里的功能必须出现在 FEATURES 里才放行，其余功能一律放行。
     *  例：登记 "realname.pro" 后，只有服务器给这个 wxid 配了 realname.pro 才可用。 */
    static boolean hasFeat(String name) {
        return !GATED.contains(name) || FEATURES.contains(name);
    }

    /** 把服务器给的功能列表写进内存并落盘（离线也保留） */
    static void applyFeat(String csv) {
        FEATURES.clear();
        if (csv != null)
            for (String f : csv.split(",")) if (f.trim().length() > 0) FEATURES.add(f.trim());
        try {
            java.io.FileWriter w = new java.io.FileWriter(FEAT_CACHE, false);
            w.write(csv == null ? "" : csv);
            w.close();
        } catch (Throwable t) {}
    }

    static void loadFeatCache() {
        try {
            String s = readText(FEAT_CACHE);
            if (s == null) return;
            FEATURES.clear();
            for (String f : s.trim().split(",")) if (f.trim().length() > 0) FEATURES.add(f.trim());
            T("feat cache: " + FEATURES.size());
        } catch (Throwable t) {}
    }

    static void loadOffState() {
        try {
            java.io.File f = new java.io.File(OFF_FILE);
            if (!f.exists()) return;
            String t = readText(OFF_FILE);
            // 1.49：旧版"离线停用"留下的标记自动清掉（需求变更：离线不再停用）
            if (t != null && t.trim().startsWith("无法连接服务器")) {
                try { f.delete(); } catch (Throwable x) {}
                T("stale offline-off flag cleared");
                return;
            }
            MODULE_OFF = true;
            OFF_REASON = (t == null || t.trim().length() == 0) ? "模块已被停用" : t.trim();
        } catch (Throwable t) {}
    }

    static void setOff(String reason) {
        boolean was = MODULE_OFF;
        MODULE_OFF = true;
        RecallMarkLayout.refresh();
        OFF_REASON = (reason == null || reason.length() == 0) ? "模块已被停用" : reason;
        try {
            java.io.FileWriter w = new java.io.FileWriter(OFF_FILE, false);
            w.write(OFF_REASON);
            w.close();
        } catch (Throwable t) {}
        if (!was) {
            T("module off: " + OFF_REASON);
            showOffDialogCurrent();
        }
    }

    static void clearOff() {
        if (MODULE_OFF) T("module re-enabled (restart to take effect)");
        MODULE_OFF = false;
        RecallMarkLayout.refresh();
        OFF_REASON = null;
        try { new java.io.File(OFF_FILE).delete(); } catch (Throwable t) {}
    }

    static void startStatusLoop() {
        try {
            Thread th = new Thread(new Runnable() {
                public void run() {
                    boolean first = true;
                    while (true) {
                        long gap = 30L * 60 * 1000;   // 需求：30 分钟一次
                        try { if (new java.io.File(WXDIR + "status.fast").exists()) gap = 20000; } catch (Throwable t) {}
                        // 刚启动时网络/域名可能还没就绪：先重试 3 次（间隔 2 秒）再判离线
                        int tries = first ? 3 : 1;
                        int st = -1;
                        for (int i = 0; i < tries && st < 0; i++) {
                            try { st = fetchStatus(); } catch (Throwable t) { T("status err " + t); }
                            if (st < 0 && i + 1 < tries) { try { Thread.sleep(2000); } catch (Throwable t) {} }
                        }
                        if (st < 0) T("status unreachable, keep current state");   // 需求变更：离线不停用
                        first = false;
                        try { Thread.sleep(gap); } catch (Throwable t) { return; }
                    }
                }
            });
            th.setDaemon(true);
            th.start();
            T("status loop started");
        } catch (Throwable t) { T("status loop err " + t); }
    }

    /** 返回 1=正常，2=被停用，-1=离线/出错（此时不动状态，由调用方决定） */
    static int fetchStatus() {
        try { if (new java.io.File(WXDIR + "status.offline").exists()) { T("status offline (test)"); return -1; } } catch (Throwable t) {}
        String r = callMainStr("statusLine");
        LAST_STATUS_AT = System.currentTimeMillis();
        T("status raw: " + (r == null ? "null" : (r.length() > 100 ? r.substring(0, 100) : r)));
        if (r == null || !r.startsWith("OK|")) return -1;
        String[] p = r.split("\\|", 6);
        String dis = p.length > 1 ? p[1] : "0";
        String reason = p.length > 2 ? p[2] : "";
        String reply = p.length > 3 ? p[3] : "";
        String feat = p.length > 4 ? p[4] : "";
        String tierName = p.length > 5 ? p[5] : "";
        if (reply != null && reply.length() > 0) SERVER_REPLY = reply;
        SERVER_TIER = (tierName == null || tierName.length() == 0) ? "免费版" : tierName;
        applyFeat(feat);
        if ("1".equals(dis)) { setOff(reason); return 2; }
        clearOff();
        return 1;
    }

    static void showOffDialogCurrent() {
        try {
            java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
            final Activity a = ref == null ? null : ref.get();
            if (a == null) return;
            a.runOnUiThread(new Runnable() {
                public void run() { try { showOffDialog(a, OFF_REASON, true); } catch (Throwable t) {} }
            });
        } catch (Throwable t) {}
    }

    /** 停用页：原因 + 申诉 + 重试 */
    static void showOffDialog(final Activity act, String reason, final boolean retry) {
        try {
            final String msg = (reason == null ? "模块已被停用" : reason)
                    + "\n\n如果认为有误，可以提交申诉；审核通过后会自动恢复（重启微信生效）。";
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(act);
            b.setTitle("模块已停用");
            b.setMessage(msg);
            b.setPositiveButton("申诉", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { showAppealDialog(act); }
            });
            if (retry) {
                b.setNeutralButton("重试", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        toast(act, "正在检查…");
                        new Thread(new Runnable() {
                            public void run() {
                                int st = fetchStatus();
                                if (st < 0) { toastMain("连不上服务器，稍后再试"); return; }
                                boolean ok = !MODULE_OFF;
                                toastMain(ok ? "已恢复，重启微信后生效" : "仍未恢复：" + (OFF_REASON == null ? "" : OFF_REASON));
                            }
                        }).start();
                    }
                });
            }
            b.setNegativeButton("关闭", null);
            b.show();
        } catch (Throwable t) { T("off dialog err " + t); }
    }

    /** 1.50：输入功能码，解锁后立即生效（不用重启） */
    

    static void showAppealDialog(final Activity act) {
        try {
            final android.widget.EditText et = new android.widget.EditText(act);
            et.setHint("说明一下情况，例如：激活码是自用的，没有共享");
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(act);
            b.setTitle("申诉");
            b.setView(et);
            b.setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String txt = String.valueOf(et.getText());
                    final String text = txt.trim();
                    if (text.length() < 2) { toast(act, "写点内容吧"); return; }
                    toast(act, "正在提交…");
                    new Thread(new Runnable() {
                        public void run() {
                            String r = callMainStr2("sendAppeal", text);
                            toastMain("OK".equals(r) ? "申诉已提交，审核后自动恢复" : ("提交失败：" + r));
                        }
                    }).start();
                }
            });
            b.setNegativeButton("取消", null);
            b.show();
        } catch (Throwable t) { T("appeal dialog err " + t); }
    }

    static String callMainStr2(String method, String arg) {
        try {
            Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
            java.lang.reflect.Method m = c.getDeclaredMethod(method, String.class);
            m.setAccessible(true);
            Object r = m.invoke(null, arg);
            return r == null ? "" : r.toString();
        } catch (Throwable t) { return "ERR"; }
    }

    // ===== v116：微信 Tinker 热补丁指纹 =====
    // 补丁是"下载 → 下次启动生效"。正常重启时我们会全量重扫、重装钩子，不受影响；
    // 这里只处理"补丁在本进程运行期间被换掉"的情况：提示重启，并上报一次。
    static volatile long LAST_PATCH_CHECK = 0;

    /** 当前补丁目录的指纹：patch-xxx/目录时间 + dex 下每个补丁包的名字/大小/时间 */
    static String tinkerSnap() {
        StringBuilder sb = new StringBuilder();
        try {
            java.io.File t = new java.io.File(computeWxData() + "/tinker");
            java.io.File[] ps = t.listFiles();
            if (ps == null) return "";
            java.util.Arrays.sort(ps);
            for (java.io.File p : ps) {
                if (!p.isDirectory() || !p.getName().startsWith("patch-")) continue;
                sb.append(p.getName()).append('/').append(p.length()).append('/').append(p.lastModified());
                java.io.File[] fs = new java.io.File(p, "dex").listFiles();
                if (fs != null) {
                    java.util.Arrays.sort(fs);
                    for (java.io.File f : fs) {
                        if (!f.isFile()) continue;
                        sb.append(',').append(f.getName()).append('/').append(f.length()).append('/').append(f.lastModified());
                    }
                }
                sb.append(';');
            }
        } catch (Throwable t) {}
        return sb.toString();
    }

    static void snapWrite(String s) {
        try {
            java.io.FileWriter w = new java.io.FileWriter(WXDIR + "tinker.snap", false);
            w.write(s);
            w.close();
        } catch (Throwable t) {}
    }

    static void tinkerSeenChanged(final Activity act) {
        long now = System.currentTimeMillis();
        if (now - LAST_PATCH_CHECK < 30000) return;      // 30 秒最多查一次
        LAST_PATCH_CHECK = now;
        try {
            String cur = tinkerSnap();
            String base = readText(WXDIR + "tinker.snap");
            if (base == null) { snapWrite(cur); return; }
            if (base.trim().equals(cur)) return;
            snapWrite(cur);
            T("tinker changed -> restart needed");
            toast(act, "微信热补丁已更新，重启微信后模块才会生效");
            diagOnce("kernel-fail:tinker-changed", "kernel.stale.ts");
        } catch (Throwable t) {}
    }

    /** v116：钩子全静默（10 分钟一个都没命中）时上报一次，用于发现"补丁换了类"这类静默失效 */
    static void hookLivenessWatch() {
        new Thread(new Runnable() {
            public void run() {
                long wait = 10L * 60 * 1000;
                boolean test = false;
                try {
                    test = new java.io.File(WXDIR + "kernel.silent.test").exists();
                    if (test) wait = 20000;
                } catch (Throwable t) {}
                try { Thread.sleep(wait); } catch (Throwable t) { return; }
                try {
                    if (test || (HOOK_NAME_OK == 0 && HOOK_WRITE_N == 0 && HOOK_ADDMSG_N == 0
                            && TXT_HITS.get() == 0 && TITLE_HITS.get() == 0)) {
                        T("hooks silent" + (test ? " (test)" : "") + ": name=" + HOOK_NAME_OK + " write=" + HOOK_WRITE_N
                                + " addmsg=" + HOOK_ADDMSG_N + " txt=" + TXT_HITS.get() + " ttl=" + TITLE_HITS.get());
                        diagOnce("kernel-fail:silent-hooks", "kernel.silent.ts");
                    }
                } catch (Throwable t) {}
            }
        }).start();
    }

    static void hookActivityTracker() {
        try {
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    final Activity act = (Activity) p.thisObject;
                    rememberAppContext(act, "resume");
                    CUR_ACT = new java.lang.ref.WeakReference<Activity>(act);
                    try { tinkerSeenChanged(act); } catch (Throwable t) {}
                    // v93：微信在后台待久了回到前台，首页那颗「+」的 View 会被重建，
                    // 之前挂在旧 View 上的长按监听就没了 → 表现为「点 + 没入口，必须强停才好」。
                    // 回到前台时补挂一次（1 秒节流）。
                    try {
                        String cn = act.getClass().getName();
                        if (cn.endsWith("LauncherUI")) {
                            long now = System.currentTimeMillis();
                            if (now - LAST_PLUS_MS > 1000) {
                                LAST_PLUS_MS = now;
                                attachPlusLongClick(act);
                            }
                        }
                    } catch (Throwable t) {}
                }
            });
            XposedHelpers.findAndHookMethod(android.app.Activity.class, "onPause", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
                    if (ref != null && ref.get() == p.thisObject) CUR_ACT = null;
                }
            });
            T("activity tracker ok");
            // v93：兜底巡检——万一 onResume 没触发（或 View 被原地替换），也能把入口补回去
            try {
                Thread th = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            try { Thread.sleep(25000); } catch (Throwable t) { return; }
                            try {
                                java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
                                final Activity a = ref == null ? null : ref.get();
                                if (a == null) continue;
                                if (!a.getClass().getName().endsWith("LauncherUI")) continue;
                                a.runOnUiThread(new Runnable() {
                                    public void run() {
                                        try {
                                            long now = System.currentTimeMillis();
                                            if (now - LAST_PLUS_MS < 15000) return;
                                            LAST_PLUS_MS = now;
                                            attachPlusLongClick(a);
                                        } catch (Throwable t) {}
                                    }
                                });
                            } catch (Throwable t) {}
                        }
                    }
                });
                th.setDaemon(true);
                th.start();
            } catch (Throwable t) { T("plus watchdog err " + t); }
        } catch (Throwable t) { T("activity tracker failed " + t); }
        startOrigPoller();
    }

    static void startOrigPoller() {
        try {
            Class<?> looperCls = Class.forName("android.os.Looper");
            Object main = XposedHelpers.callStaticMethod(looperCls, "getMainLooper");
            Class<?> handlerCls = Class.forName("android.os.Handler");
            java.lang.reflect.Constructor<?> ctor = handlerCls.getDeclaredConstructor(looperCls);
            ctor.setAccessible(true);
            final Object handler = ctor.newInstance(main);
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
                        Activity a = ref == null ? null : ref.get();
                        long delay = 5000;   // 空闲时 5s 一次，几乎不耗电
                        if (a != null) {
                            String cn = a.getClass().getName();
                            boolean picker = cn.endsWith("LocalMediaPickerUI")
                                    || cn.endsWith("LocalMediaPreviewUI")
                                    || cn.endsWith("ImagePreviewUI")
                                    || cn.endsWith("AlbumPreviewUI")
                                    || cn.contains("picker") || cn.contains("Picker")
                                    || cn.contains("gallery") || cn.contains("Gallery");
                            if (picker) {
                                delay = 700;   // 选图页才快速轮询
                                if (!new java.io.File(ORIG_OFF_PATH).exists()) applyOriginal(a);
                            }
                        }
                        XposedHelpers.callMethod(handler, "postDelayed", this, Long.valueOf(delay));
                    } catch (Throwable t) {
                        try { XposedHelpers.callMethod(handler, "postDelayed", this, Long.valueOf(5000)); } catch (Throwable t2) {}
                    }
                }
            };
            XposedHelpers.callMethod(handler, "post", r);
            T("orig poller started");
        } catch (Throwable t) { T("orig poller failed " + t); }
    }

    static void hookOrigPicker(ClassLoader cl, String cn) {
        try {
            Class<?> k = XposedHelpers.findClass(cn, cl);
            XposedHelpers.findAndHookMethod(k, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (MODULE_OFF) return;
                    final Activity act = (Activity) param.thisObject;
                    if (!isPickerLike(act.getClass().getName())) return;   // 普通页面不处理，省掉三次遍历
                    ORIG_CLICKS.remove(act);
                    ORIG_TEXT_CLICKS.remove(act);
                    new Thread(new Runnable() {
                        public void run() {
                            for (int i = 0; i < 3; i++) {
                                try { Thread.sleep(i == 0 ? 120 : (i == 1 ? 200 : 400)); } catch (Throwable t) {}
                                try {
                                    act.runOnUiThread(new Runnable() {
                                        public void run() { applyOriginal(act); }
                                    });
                                } catch (Throwable t) {}
                            }
                        }
                    }).start();
                }
            });
            T("orig hook ok " + cn);
        } catch (Throwable t) { if (VB()) T("orig hook fail " + cn + " " + t); }
    }

    /** 只有这些页面才需要「原图」处理；别的页面直接跳过，避免 resume 时遍历整棵视图树 */
    static boolean isPickerLike(String cn) {
        if (cn == null) return false;
        return cn.endsWith("LocalMediaPickerUI") || cn.endsWith("LocalMediaPreviewUI")
                || cn.endsWith("ImagePreviewUI") || cn.endsWith("AlbumPreviewUI")
                || cn.endsWith("GalleryEntryUI")
                || cn.contains("picker") || cn.contains("Picker")
                || cn.contains("gallery") || cn.contains("Gallery");
    }

    /** 133：在屏幕上找一个「原图」文字控件（必须真的可见、坐标在屏幕内） */
    static Object[] findVisibleOrigText(Object v, int depth) {
        if (v == null || depth > 40) return null;
        try {
            Object t = XposedHelpers.callMethod(v, "getText");
            if (t != null && "\u539f\u56fe".equals(String.valueOf(t))) {
                Object shown = XposedHelpers.callMethod(v, "isShown");
                if (Boolean.TRUE.equals(shown)) {
                    int[] loc = new int[2];
                    XposedHelpers.callMethod(v, "getLocationOnScreen", loc);
                    int w = ((Integer) XposedHelpers.callMethod(v, "getWidth")).intValue();
                    int h = ((Integer) XposedHelpers.callMethod(v, "getHeight")).intValue();
                    Object root = XposedHelpers.callMethod(v, "getRootView");
                    int sh = ((Integer) XposedHelpers.callMethod(root, "getHeight")).intValue();
                    int sw = ((Integer) XposedHelpers.callMethod(root, "getWidth")).intValue();
                    if (w > 0 && h > 0 && loc[1] >= 0 && loc[1] < sh && loc[0] >= 0 && loc[0] < sw) {
                        return new Object[] { v, Integer.valueOf(loc[0]), Integer.valueOf(loc[1]),
                                Integer.valueOf(w), Integer.valueOf(h) };
                    }
                }
            }
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                Object[] r = findVisibleOrigText(c, depth + 1);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    /** 133：给某个 View 的中心补一次真实触摸（performClick 在 5032 上改不了勾选状态） */
    static boolean touchView(final Activity act, Object v) {
        try {
            int[] loc = new int[2];
            XposedHelpers.callMethod(v, "getLocationOnScreen", loc);
            int w = ((Integer) XposedHelpers.callMethod(v, "getWidth")).intValue();
            int h = ((Integer) XposedHelpers.callMethod(v, "getHeight")).intValue();
            if (w <= 0 || h <= 0) return false;
            float x = loc[0] + w / 2f;
            float y = loc[1] + h / 2f;
            long tnow = System.currentTimeMillis();
            try {
                Object up = XposedHelpers.callStaticMethod(Class.forName("android.os.SystemClock"), "uptimeMillis");
                if (up instanceof Long) tnow = ((Long) up).longValue();
            } catch (Throwable t) {}
            Class<?> me = Class.forName("android.view.MotionEvent");
            Object d = XposedHelpers.callStaticMethod(me, "obtain",
                    Long.valueOf(tnow), Long.valueOf(tnow), Integer.valueOf(0),
                    Float.valueOf(x), Float.valueOf(y), Integer.valueOf(0));
            Object u = XposedHelpers.callStaticMethod(me, "obtain",
                    Long.valueOf(tnow), Long.valueOf(tnow + 60), Integer.valueOf(1),
                    Float.valueOf(x), Float.valueOf(y), Integer.valueOf(0));
            Object decor = act.getWindow().getDecorView();
            XposedHelpers.callMethod(decor, "dispatchTouchEvent", d);
            XposedHelpers.callMethod(decor, "dispatchTouchEvent", u);
            T("orig touch " + (int) x + "," + (int) y + " on " + v.getClass().getSimpleName());
            return true;
        } catch (Throwable t) { T("orig touch err " + t); return false; }
    }

    // ===== 135：原图勾选排查用（origdump.on 才输出，正常用户零开销）=====
    static final java.io.File ORIGDUMP = new java.io.File(WXDIR + "origdump.on");
    static volatile long ORIGDUMP_LAST = 0;

    static String descView(Object v) {
        try {
            int[] loc = new int[2];
            XposedHelpers.callMethod(v, "getLocationOnScreen", loc);
            int w = ((Integer) XposedHelpers.callMethod(v, "getWidth")).intValue();
            int h = ((Integer) XposedHelpers.callMethod(v, "getHeight")).intValue();
            boolean clickable = Boolean.TRUE.equals(XposedHelpers.callMethod(v, "isClickable"));
            boolean checked = false;
            try { checked = Boolean.TRUE.equals(XposedHelpers.callMethod(v, "isChecked")); } catch (Throwable t) {}
            int id = 0;
            try { id = ((Integer) XposedHelpers.callMethod(v, "getId")).intValue(); } catch (Throwable t) {}
            String txt = "";
            try {
                Object t = XposedHelpers.callMethod(v, "getText");
                if (t != null) txt = String.valueOf(t);
                if (txt.length() > 24) txt = txt.substring(0, 24) + "…";
            } catch (Throwable t) {}
            return v.getClass().getName() + " id=0x" + Integer.toHexString(id)
                    + " click=" + clickable + " checked=" + checked
                    + " at=" + loc[0] + "," + loc[1] + " " + w + "x" + h
                    + (txt.length() > 0 ? " text=[" + txt + "]" : "");
        } catch (Throwable t) { return "desc err " + t; }
    }

    static boolean textHasOrig(Object v) {
        try {
            Object t = XposedHelpers.callMethod(v, "getText");
            if (t != null && String.valueOf(t).contains("\u539f\u56fe")) return true;
        } catch (Throwable t) {}
        return false;
    }

    static void dumpOrigNode(Object v, int lv) {
        T("origdump   " + (lv == 0 ? "" : "  ") + "[" + lv + "] " + descView(v));
        try {
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n && i < 10; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                T("origdump      ch[" + i + "] " + descView(c));
            }
        } catch (Throwable t) {}
    }

    static int dumpOrigWalk(Object v, int depth, int found) {
        if (v == null || depth > 45 || found >= 4) return found;
        try {
            if (textHasOrig(v)) {
                T("origdump hit[" + found + "] depth=" + depth + " " + descView(v));
                Object p = XposedHelpers.callMethod(v, "getParent");
                if (p != null) {
                    dumpOrigNode(p, 0);
                    Object pp = XposedHelpers.callMethod(p, "getParent");
                    if (pp != null) dumpOrigNode(pp, 1);
                    Object ppp = pp == null ? null : XposedHelpers.callMethod(pp, "getParent");
                    if (ppp != null) dumpOrigNode(ppp, 2);
                }
                found++;
            }
            int n = 0;
            try { n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue(); } catch (Throwable t) {}
            for (int i = 0; i < n && found < 4; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                found = dumpOrigWalk(c, depth + 1, found);
            }
        } catch (Throwable t) {}
        return found;
    }

    static void dumpOrigTree(Activity act) {
        try {
            long now = System.currentTimeMillis();
            if (now - ORIGDUMP_LAST < 5000) return;
            ORIGDUMP_LAST = now;
            Object decor = act.getWindow().getDecorView();
            T("origdump === " + act.getClass().getSimpleName() + " ===");
            int n = dumpOrigWalk(decor, 0, 0);
            T("origdump === end hits=" + n + " ===");
        } catch (Throwable t) { T("origdump err " + t); }
    }

    static void applyOriginal(final Activity act) {
        try {
            try { if (ORIGDUMP.exists()) dumpOrigTree(act); } catch (Throwable t) {}
            if (new java.io.File(ORIG_OFF_PATH).exists()) return;
            if (Boolean.TRUE.equals(ORIG_USER_OFF.get(act))) return;
            String full = act.getClass().getName();
            if (!isPickerLike(full)) return;
            long t0 = System.currentTimeMillis();
            String where = act.getClass().getSimpleName();
            Object decorV = act.getWindow().getDecorView();
            Object cb = findOriginalCheckBox(decorV);
            // 134：找到的 CheckBox 可能在屏幕外（隐藏面板里），这种点了也没用，直接作废
            if (cb != null) {
                try {
                    int[] cl = new int[2];
                    XposedHelpers.callMethod(cb, "getLocationOnScreen", cl);
                    Object rootV = XposedHelpers.callMethod(cb, "getRootView");
                    int sh0 = ((Integer) XposedHelpers.callMethod(rootV, "getHeight")).intValue();
                    int cw = ((Integer) XposedHelpers.callMethod(cb, "getWidth")).intValue();
                    int chh = ((Integer) XposedHelpers.callMethod(cb, "getHeight")).intValue();
                    if (chh <= 0 || cw <= 0 || cl[1] < 0 || cl[1] >= sh0) {
                        T("orig[" + where + "]: checkbox off-screen at " + cl[0] + "," + cl[1] + " -> ignore");
                        cb = null;
                    }
                } catch (Throwable t) { cb = null; }
            }
            if (cb == null && hasText(decorV, "\u539f\u56fe")) {
                // 兜底：Activity 里有「可见」的 CheckBox 字段
                Object f = findCheckBoxField(act);
                if (f != null) {
                    cb = f;
                    T("orig[" + where + "]: use CheckBox field " + f.getClass().getName());
                }
            }
            long dt = System.currentTimeMillis() - t0;
            if (dt > 150) T("SLOW orig[" + where + "] " + dt + "ms");
            long[] st2 = ORIG_TEXT_CLICKS.get(act);
            if (st2 == null) { st2 = new long[]{0L, 0L}; ORIG_TEXT_CLICKS.put(act, st2); }
            if (cb == null) {
                return;
            }
            boolean checked = false;
            try { checked = Boolean.TRUE.equals(XposedHelpers.callMethod(cb, "isChecked")); }
            catch (Throwable t) { checked = false; }   // 图标/文字控件读不到状态，按未勾处理
            long now = System.currentTimeMillis();
            long[] st = ORIG_CLICKS.get(act);
            if (st == null) { st = new long[]{0L, 0L, 0L}; ORIG_CLICKS.put(act, st); }
            if (checked) { st[0] = 0; st[2] = 0; return; }
            if (st[2] == 1) return;                        // 这一轮已经点过了
            if (st[0] == 0) {                              // 第一次看到未勾：等下一次确认
                st[0] = now;
                T("orig[" + where + "]: unchecked, waiting confirm");
                return;
            }
            if (now - st[0] < 150) return;                 // 还没稳定
            Object parent = XposedHelpers.callMethod(cb, "getParent");
            attachUserTouch(act, parent);
            // 137：5032 上 performClick 只消费事件不改状态；直接给「可点祖先」派发一次真实触摸。
            // 先往上找 clickable 的容器（实测是 RelativeLayout id=0x7f0a4f24，包着 WeCheckBox + 原图文字）。
            Object target = cb;
            Object tp = cb;
            for (int lv = 0; lv < 4 && tp != null; lv++) {
                try {
                    if (Boolean.TRUE.equals(XposedHelpers.callMethod(tp, "isClickable"))) { target = tp; break; }
                } catch (Throwable t) {}
                tp = XposedHelpers.callMethod(tp, "getParent");
            }
            boolean ok = false;
            try {
                SYNTH_TOUCH = true;
                ok = touchView(act, target);
            } catch (Throwable t) { T("orig touch err " + t); }
            finally { SYNTH_TOUCH = false; }
            // 132：performClick 返回 true 只是"消费了事件"，勾选状态可能没变（5032 构建上就是这样）。
            // 所以点完再读一次状态，没变就补一次真正的触摸事件。
            try { Thread.sleep(280); } catch (Throwable t) {}
            boolean nowChecked = false;
            try { nowChecked = Boolean.TRUE.equals(XposedHelpers.callMethod(cb, "isChecked")); } catch (Throwable t) {}
            st[2] = 1;
            T("orig[" + where + "]: clicked after confirm ok=" + ok + " checked=" + nowChecked);
        } catch (Throwable t) { T("orig err " + t); }
    }

    static boolean hasText(Object v, String text) {
        try {
            Object t = XposedHelpers.callMethod(v, "getText");
            if (t != null && String.valueOf(t).contains(text)) return true;
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                if (hasText(c, text)) return true;
            }
        } catch (Throwable t) {}
        return false;
    }

    static Object findCheckBoxField(Object act) {
        try {
            Class<?> k = act.getClass();
            while (k != null && k != Object.class) {
                java.lang.reflect.Field[] fs = k.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    String tn = fs[i].getType().getName();
                    if (!tn.contains("CheckBox")) continue;
                    try {
                        fs[i].setAccessible(true);
                        Object v = fs[i].get(act);
                        if (v == null) continue;
                        try {
                            Object vis = XposedHelpers.callMethod(v, "getVisibility");
                            if (vis != null && ((Integer) vis).intValue() != 0) continue;  // 不可见就不用
                        } catch (Throwable t) {}
                        return v;
                    } catch (Throwable t) {}
                }
                k = k.getSuperclass();
            }
        } catch (Throwable t) {}
        return null;
    }

    static Object findOriginalCheckBox(Object v) {
        try {
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                String txt = null;
                try {
                    Object t = XposedHelpers.callMethod(c, "getText");
                    if (t != null) txt = String.valueOf(t);
                } catch (Throwable t) {}
                if ("\u539f\u56fe".equals(txt)) {
                    Object p = XposedHelpers.callMethod(c, "getParent");
                    Object p2 = p == null ? null : XposedHelpers.callMethod(p, "getParent");
                    Object p3 = p2 == null ? null : XposedHelpers.callMethod(p2, "getParent");
                    Object cb = findCheckBoxIn(p, 0);
                    if (cb == null) cb = findCheckBoxIn(p2, 0);
                    if (cb == null) cb = findCheckBoxIn(p3, 0);
                    if (cb != null) return cb;
                    // 没有 CheckBox：这个页面的勾选是「图标 + 文字」，找可点的图标
                    Object icon = findClickableImageIn(p, 0);
                    if (icon == null) icon = findClickableImageIn(p2, 0);
                    if (icon != null) return icon;
                    // 再退一步：文字本身可点就点文字
                    try {
                        if (Boolean.TRUE.equals(XposedHelpers.callMethod(c, "isClickable"))) return c;
                    } catch (Throwable t) {}
                }
                Object r = findOriginalCheckBox(c);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    static Object findClickableImageIn(Object v, int depth) {
        if (v == null || depth > 4) return null;
        try {
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                String cn = c.getClass().getName();
                if (cn.contains("ImageView")) {
                    try {
                        if (Boolean.TRUE.equals(XposedHelpers.callMethod(c, "isClickable"))) return c;
                    } catch (Throwable t) {}
                }
                Object r = findClickableImageIn(c, depth + 1);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    static Object findCheckBoxIn(Object v, int depth) {
        if (v == null || depth > 4) return null;
        try {
            String cn = v.getClass().getName();
            if (cn.contains("CheckBox")) return v;
            int n = ((Integer) XposedHelpers.callMethod(v, "getChildCount")).intValue();
            for (int i = 0; i < n; i++) {
                Object c = XposedHelpers.callMethod(v, "getChildAt", Integer.valueOf(i));
                Object r = findCheckBoxIn(c, depth + 1);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    static boolean f(String path) { try { return new java.io.File(path).exists(); } catch (Throwable t) { return false; } }

    static String onOff(String path) { return f(path) ? "\uff1a\u5f00" : "\uff1a\u5173"; }


    // ================= 真名 =================

    /** v197：所有 setResult 前先核对返回类型，微信热更新改了签名也不会把微信搞崩 */
    static boolean retIs(XC_MethodHook.MethodHookParam p, Class<?> t) {
        try {
            if (p == null || p.method == null) return false;
            java.lang.reflect.Member m0 = p.method;
            if (!(m0 instanceof java.lang.reflect.Method)) return false;
            return ((java.lang.reflect.Method) m0).getReturnType() == t;
        } catch (Throwable x) { return false; }
    }

    static boolean rnOn() {
        long now = System.currentTimeMillis();
        if (now - RN_FLAG_AT > 5000) {
            RN_FLAG_AT = now;
            boolean on = true;
            try { on = !new java.io.File(RN_OFF_PATH).exists(); } catch (Throwable t) {}
            RN_ON = on;
        }
        return RN_ON;
    }

    static void rnLoad() {
        try {
            String all = readText(RN_CACHE_PATH);
            if (all == null) return;
            int n = 0;
            for (String line : all.split("\n")) {
                String[] p = line.split("\t");
                if (p.length < 2 || p[0].trim().length() == 0) continue;
                String u = p[0].trim();
                RN_MAP.put(u, p[1].trim());
                long ts = 0;
                try { if (p.length > 2) ts = Long.parseLong(p[2].trim()); } catch (Throwable t) {}
                RN_TS.put(u, Long.valueOf(ts));
                n++;
            }
            T("rn: cache " + n);
        } catch (Throwable t) { T("rn load err " + t); }
    }

    static String rnCached(String user) {
        if (user == null) return null;
        String v = RN_MAP.get(user);
        if (v == null) return null;
        Long ts = RN_TS.get(user);
        if (ts != null && ts.longValue() > 0 && System.currentTimeMillis() - ts.longValue() > RN_TTL) return null;
        return v;
    }

    static void rnSave(String user, String mask) {
        try {
            long now = System.currentTimeMillis();
            RN_MAP.put(user, mask);
            RN_TS.put(user, Long.valueOf(now));
            java.io.File f = new java.io.File(RN_CACHE_PATH);
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(user + "\t" + mask + "\t" + now + "\n");
            w.close();
        } catch (Throwable t) { T("rn save err " + t); }
    }

    /** 每次进群取最近发言的多少人去采集（默认 20，0=只显示缓存不联网） */
    static int rnBatch() {
        try {
            String s = readText(RN_BATCH_PATH);
            if (s != null && s.trim().length() > 0) {
                int v = Integer.parseInt(s.trim());
                return v < 0 ? 0 : (v > 30 ? 30 : v);
            }
        } catch (Throwable t) {}
        return 20;
    }

    /** 两次查询的间隔毫秒（默认 2000，下限 800） */
    static long rnGap() {
        try {
            String s = readText(RN_GAP_PATH);
            if (s != null && s.trim().length() > 0) {
                long v = Long.parseLong(s.trim());
                return v < 800 ? 800 : (v > 60000 ? 60000 : v);
            }
        } catch (Throwable t) {}
        return 2000;
    }

    static void rnHook(final ClassLoader cl) {
        if (RN_HOOKED) return;
        RN_HOOKED = true;
        try {
            Class<?> sceneCls = XposedHelpers.findClass(
                    "com.tencent.mm.plugin.remittance.model.i", cl);
            int n = 0;
            for (java.lang.reflect.Method m : sceneCls.getDeclaredMethods()) {
                if (!"H".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        try { rnOnResp(p.thisObject); } catch (Throwable t) { T("rn resp err " + t); }
                    }
                });
                n++;
            }
            T("rn: resp hook " + n);
        } catch (Throwable t) { T("rn hook err " + t); }
    }

    static String rnReqUser(Object scene) {
        try {
            Object o = XposedHelpers.getObjectField(scene, "n");
            Object m = XposedHelpers.getObjectField(o, "a");
            Object req = XposedHelpers.getObjectField(m, "a");
            Object d = XposedHelpers.getObjectField(req, "d");
            return d instanceof String ? (String) d : null;
        } catch (Throwable t) { return null; }
    }

    static int rnInt(Object o, String name) {
        try {
            Object v = XposedHelpers.getObjectField(o, name);
            return v instanceof Integer ? ((Integer) v).intValue() : -1;
        } catch (Throwable t) { return -1; }
    }

    static void rnOnResp(Object scene) {
        String user = rnReqUser(scene);
        Object resp = null;
        try { resp = XposedHelpers.getObjectField(scene, "r"); } catch (Throwable t) {}
        if (user != null && user.equals(RN_INFLIGHT)) RN_INFLIGHT = null;
        if (resp == null) { T("rn: resp null " + user); return; }
        String mask = null;
        try { mask = (String) XposedHelpers.getObjectField(resp, "f"); } catch (Throwable t) {}
        int ret = rnInt(resp, "d");
        if (user != null && mask != null && mask.trim().length() > 0) rnSave(user, mask.trim());
        RN_LAST_RESP = user + " ret=" + ret + " mask=" + mask;
        T("rn: resp " + user + " ret=" + ret + " mask=" + mask);
    }

    static void rnQuery(final String user, final String sceneArg) {
        // 1.63：在发起线程里就把「在途」标上。以前写在子线程里，rnLoop 的等待循环
        // 可能一次都不等就继续下一条，导致并发查询、响应串号。
        RN_INFLIGHT = user;
        RN_QUERIES.incrementAndGet();
        new Thread(new Runnable() {
            public void run() {
                try {
                    ClassLoader cl = APP_CL;
                    rnHook(cl);
                    Class<?> sceneCls = XposedHelpers.findClass(
                            "com.tencent.mm.plugin.remittance.model.i", cl);
                    java.lang.reflect.Constructor<?> ctor =
                            sceneCls.getDeclaredConstructor(String.class, String.class);
                    ctor.setAccessible(true);
                    Object scene = ctor.newInstance(user, sceneArg == null ? "" : sceneArg);
                    Object j1 = XposedHelpers.callStaticMethod(XposedHelpers.findClass("zp0.j1", cl), "j");
                    Object y = XposedHelpers.callMethod(j1, "q");
                    Object s1 = XposedHelpers.getObjectField(y, "b");
                    XposedHelpers.callMethod(s1, "g", scene);
                } catch (Throwable t) { RN_INFLIGHT = null; T("rn query err " + t); }
            }
        }).start();
    }

    static boolean rnQueryable(String user) {
        if (user == null) return false;
        String u = user.trim();
        if (u.length() == 0) return false;
        if (u.endsWith("@chatroom") || u.endsWith("@app")) return false;
        if (u.startsWith("gh_") || u.equals("filehelper") || u.equals("weixin")) return false;
        return true;
    }

    static void rnEnqueue(String user, String group) {
        try {
            if (!rnOn()) return;
            if (!rnQueryable(user)) return;
            final String u = user.trim();
            if (rnCached(u) != null) return;
            Long tried = RN_TRIED.get(u);
            if (tried != null && System.currentTimeMillis() - tried.longValue() < 12L * 3600 * 1000) return;
            final String item = u + "|" + (group == null ? "" : group.trim());
            synchronized (RN_QUEUE) {
                if (!RN_SET.add(item)) return;
                if (RN_SET.size() > 200) { RN_SET.remove(item); return; }
                RN_QUEUE.add(item);
            }
            synchronized (RN_LOCK) { rnStartWorkerLocked(); }
        } catch (Throwable t) { T("rn enqueue err " + t); }
    }

    static final Object RN_LOCK = new Object();     // 1.63：worker 的启动/退出/入队统一用这把锁

    static void rnStartWorker() {
        synchronized (RN_LOCK) { rnStartWorkerLocked(); }
    }

    static void rnStartWorkerLocked() {
        Thread t = RN_WORKER;
        if (t != null && t.isAlive()) return;       // 1.63：检查与创建在同一把锁里，不会再起第二个 worker
        RN_WORKER = new Thread(new Runnable() {
            public void run() { rnLoop(); }
        });
        RN_WORKER.setPriority(Thread.MIN_PRIORITY);
        RN_WORKER.start();
    }

    static void rnLoop() {
        int done = 0;
        while (true) {
            String u;
            synchronized (RN_QUEUE) {
                u = RN_QUEUE.poll();
                if (u != null) RN_SET.remove(u);
            }
            if (u == null) {
                // 1.63：退出前在锁里再看一眼队列，并在这里清掉 RN_WORKER，
                // 避免「刚 poll 到空 → 别处入队 → 以为还有 worker → 队列没人处理」。
                synchronized (RN_LOCK) {
                    synchronized (RN_QUEUE) { if (!RN_QUEUE.isEmpty()) continue; }
                    RN_WORKER = null;
                    break;
                }
            }
            String uu = u, gg = "";
            int bar = u.indexOf('|');
            if (bar >= 0) { uu = u.substring(0, bar); gg = u.substring(bar + 1); }
            if (rnCached(uu) != null) continue;
            try {
                RN_INFLIGHT = null;
                rnQuery(uu, gg);
                for (int i = 0; i < 30 && RN_INFLIGHT != null; i++) Thread.sleep(200);
                if (RN_INFLIGHT != null) { T("rn: timeout " + uu); RN_INFLIGHT = null; }
            } catch (Throwable t) { T("rn loop err " + t); }
            RN_TRIED.put(uu, Long.valueOf(System.currentTimeMillis()));
            done++;
            if (!RN_QUEUE.isEmpty()) {
                try { Thread.sleep(rnGap() + (long) (Math.random() * 500)); } catch (Throwable t) {}
            }
        }
        T("rn: worker end, queried=" + done);
        if (done > 0) {
            String room = CUR_ROOM;
            if (room != null) { rnBuildNameMap(room); refreshChatNamesOnce(); }
        }
    }

    /** 采集到新名字后，刷新一次聊天列表，让后缀立刻出现（每次进群最多一次） */
    static void refreshChatNamesOnce() {
        try {
            java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
            final Activity a = ref == null ? null : ref.get();
            if (a == null || CUR_ROOM == null) return;
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Object list = findByPrefix(a.getWindow().getDecorView(), "MMChattingListView", 0);
                        if (list == null) return;
                        Object rv = findByPrefix(list, "RecyclerView", 0);
                        if (rv == null) return;
                        Object ad = XposedHelpers.callMethod(rv, "getAdapter");
                        if (ad == null) return;
                        XposedHelpers.callMethod(ad, "notifyDataSetChanged");
                        T("rn: list refreshed");
                    } catch (Throwable t) { T("rn refresh err " + t); }
                }
            });
        } catch (Throwable t) { T("rn refresh err " + t); }
    }

    static Object findByPrefix(Object v, String prefix, int depth) {
        if (v == null || depth > 30) return null;
        if (v.getClass().getName().contains(prefix)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Object r = findByPrefix(g.getChildAt(i), prefix, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    static void putName(java.util.Map<String, String> m, String name, String uid) {
        String old = m.get(name);
        if (old != null && !old.equals(uid)) m.put(name, "\u0000");   // 重名：宁缺毋滥
        else m.put(name, uid);
    }

    /** 给全群成员建 显示名->wxid 映射（成本 1~3 条 SQL，不联网） */
    static void rnBuildNameMap(String room) {
        try {
            Object db = db0();
            String list = null;
            Cursor c = (Cursor) rawQuery(db,
                    "select memberlist from chatroom where chatroomname='" + room + "'", null);
            try { if (c != null && c.moveToFirst()) list = c.getString(0); }
            finally { if (c != null) c.close(); }
            if (list == null) return;
            String self = selfWxid();
            java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
            for (String u : list.split(";")) {
                String uu = u == null ? "" : u.trim();
                if (uu.length() == 0 || uu.equals(self)) continue;
                ids.add(uu);
            }
            java.util.concurrent.ConcurrentHashMap<String, String> m = new java.util.concurrent.ConcurrentHashMap<String, String>();
            for (int off = 0; off < ids.size(); off += 300) {
                StringBuilder in = new StringBuilder();
                int end = Math.min(ids.size(), off + 300);
                for (int i = off; i < end; i++) {
                    if (in.length() > 0) in.append(",");
                    in.append("'").append(ids.get(i)).append("'");
                }
                Cursor c2 = null;
                try {
                    c2 = (Cursor) rawQuery(db,
                            "select username, conRemark, nickname from rcontact where username in (" + in + ")", null);
                    while (c2 != null && c2.moveToNext()) {
                        String u = c2.getString(0);
                        String rm = null, nk = null;
                        try { rm = c2.getString(1); } catch (Throwable t) {}
                        try { nk = c2.getString(2); } catch (Throwable t) {}
                        if (rm != null && rm.trim().length() > 0) putName(m, rm.trim(), u);
                        if (nk != null && nk.trim().length() > 0) putName(m, nk.trim(), u);
                    }
                } catch (Throwable t) {} finally { if (c2 != null) c2.close(); }
            }
            rnLoadAlias(room, m);
            if (room.equals(CUR_ROOM)) {
                NAME2USER = m;
                RN_MEMBERS = new java.util.HashSet<String>(ids);
            }
            T("rn: names " + room + " members=" + ids.size() + " names=" + m.size());
            if (rnOn() && m.isEmpty()) rnDiagOnce("realname-nomap");
        } catch (Throwable t) { T("rn names err " + t); }
    }

    static void rnOnGroupOpen(final String room) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    rnBuildNameMap(room);
                    T("rn state " + rnState());

                    int batch = rnBatch();
                    if (batch <= 0 || !rnOn()) { T("rn: collect off"); return; }
                    Object db = db0();
                    String list = null;
                    Cursor c = (Cursor) rawQuery(db,
                            "select memberlist from chatroom where chatroomname='" + room + "'", null);
                    try { if (c != null && c.moveToFirst()) list = c.getString(0); }
                    finally { if (c != null) c.close(); }
                    if (list == null) return;
                    String self = selfWxid();
                    java.util.HashSet<String> members = new java.util.HashSet<String>();
                    for (String u : list.split(";")) {
                        String uu = u == null ? "" : u.trim();
                        if (uu.length() == 0 || uu.equals(self)) continue;
                        members.add(uu);
                    }
                    java.util.LinkedHashSet<String> active = new java.util.LinkedHashSet<String>();
                    Cursor c3 = null;
                    try {
                        c3 = (Cursor) rawQuery(db,
                                "select content from message where talker='" + room
                                + "' and isSend=0 and type=1 order by createTime desc limit 300", null);
                        while (c3 != null && c3.moveToNext() && active.size() < batch) {
                            String cc = c3.getString(0);
                            if (cc == null) continue;
                            int p = cc.indexOf(":\n");
                            if (p <= 3) p = cc.indexOf(": ");
                            if (p <= 3) p = cc.indexOf(':');
                            if (p <= 3 || p > 40) continue;
                            String u = cc.substring(0, p).trim();
                            if (u.length() < 5 || u.indexOf(' ') >= 0 || u.indexOf('\n') >= 0) continue;
                            if (members.contains(u)) active.add(u);
                        }
                    } catch (Throwable t) {} finally { if (c3 != null) c3.close(); }

                    int q = 0;
                    for (String u : active) {
                        if (q >= batch) break;
                        if (rnCached(u) != null) continue;
                        rnEnqueue(u, room);
                        q++;
                    }
                    T("rn: group " + room + " members=" + members.size()
                      + " active=" + active.size() + " queued=" + q + " need=" + batch);

                    // 再补一次「屏幕上正在显示的发言人」——覆盖只发图片/视频、不进文本统计的人
                    try { Thread.sleep(1500); } catch (Throwable t) {}
                    if (!room.equals(CUR_ROOM)) return;
                    java.lang.ref.WeakReference<Activity> ref = CUR_ACT;
                    final Activity a = ref == null ? null : ref.get();
                    if (a == null) return;
                    a.runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                Object list = findByPrefix(a.getWindow().getDecorView(), "MMChattingListView", 0);
                                if (list == null) return;
                                java.util.LinkedHashSet<String> vis = new java.util.LinkedHashSet<String>();
                                rnCollectVisible(list, vis, 0);
                                int q2 = 0;
                                for (String u : vis) {
                                    if (q2 >= 20) break;
                                    rnEnqueue(u, room);
                                    q2++;
                                }
                                if (q2 > 0 || vis.size() > 0) T("rn: visible queued " + q2 + "/" + vis.size());
                            } catch (Throwable t) { T("rn visible err " + t); }
                        }
                    });
                } catch (Throwable t) { T("rn group err " + t); }
            }
        }).start();
    }

    /** 扫当前屏幕上的名字，命中本群成员且没缓存就收进 out（最多 20 个） */
    static void rnCollectVisible(Object v, java.util.Set<String> out, int depth) {
        if (v == null || depth > 40 || out.size() >= 20) return;
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs == null) return;
            String s = cs.toString().trim();
            int L = s.length();
            if (L < 1 || L > 40) return;
            java.util.Map<String, String> m = NAME2USER;
            if (m == null) return;
            String u = m.get(s);
            if (u == null || u.length() == 0 || u.charAt(0) == '\u0000') return;
            if (rnCached(u) == null) out.add(u);
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                rnCollectVisible(g.getChildAt(i), out, depth + 1);
                if (out.size() >= 20) return;
            }
        }
    }


    // ===== 从「个人资料页」学 群昵称 -> wxid（这类名字不在 rcontact 里，只能这样补） =====
    static void hookProfile(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("com.tencent.mm.plugin.profile.ui.ContactInfoUI", cl);
            XposedHelpers.findAndHookMethod(k, "onResume", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    final Activity act = (Activity) p.thisObject;
                    new Thread(new Runnable() {
                        public void run() {
                            try { Thread.sleep(900); } catch (Throwable t) {}
                            try { captureProfile(act); } catch (Throwable t) { T("rn profile err " + t); }
                        }
                    }).start();
                }
            });
            T("profile hook ok");
        } catch (Throwable t) { T("profile hook fail " + t); }
    }

    static boolean rnIsUser(String u) {
        if (u == null) return false;
        if (u.endsWith("@chatroom") || u.endsWith("@app")) return false;   // v97：群/公众号不是人
        try {
            Object db = db0();
            Cursor c = (Cursor) rawQuery(db,
                    "select 1 from rcontact where username='" + u.replace("'", "''") + "' limit 1", null);
            try { return c != null && c.moveToFirst(); } finally { if (c != null) c.close(); }
        } catch (Throwable t) { return false; }
    }

    static void rnCollectTexts(Object v, java.util.List<String> out, int depth) {
        if (v == null || depth > 40 || out.size() > 200) return;
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs != null) out.add(cs.toString());
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) rnCollectTexts(g.getChildAt(i), out, depth + 1);
        }
    }

    static void captureProfile(Activity act) {
        try {
            // 1) 找 wxid：先看启动 Intent 的 extras，再看 Activity 自己的（非静态）String 字段
            String uid = null;
            java.util.ArrayList<String> cand = new java.util.ArrayList<String>();
            try {
                Object it = act.getIntent();
                Object ex = it == null ? null : XposedHelpers.callMethod(it, "getExtras");
                if (ex != null) {
                    java.util.Set<String> ks = (java.util.Set<String>) XposedHelpers.callMethod(ex, "keySet");
                    for (String k : ks) {
                        Object v = XposedHelpers.callMethod(ex, "get", k);
                        if (!(v instanceof String)) continue;
                        String sv = ((String) v).trim();
                        if (sv.length() < 4 || sv.length() > 64) continue;
                        if (sv.indexOf(' ') >= 0 || sv.indexOf('/') >= 0) continue;
                        cand.add(sv);
                    }
                }
            } catch (Throwable t) { T("rn profile intent err " + t); }
            try {
                Class<?> c = act.getClass();
                while (c != null && c.getName().startsWith("com.tencent.mm")
                        && !c.getName().equals("com.tencent.mm.ui.MMActivity")
                        && !c.getName().equals("com.tencent.mm.ui.MMFragmentActivity")) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (f.getType() != String.class) continue;
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(act);
                            if (!(v instanceof String)) continue;
                            String vv = ((String) v).trim();
                            if (vv.length() < 4 || vv.length() > 64) continue;
                            if (vv.indexOf(' ') >= 0 || vv.indexOf('/') >= 0) continue;
                            cand.add(vv);
                        } catch (Throwable t) {}
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable t) { T("rn profile field err " + t); }
            java.util.Map<String, String> m = NAME2USER;
            if (m != null) {
                for (String vv : cand) {
                    if (m.containsValue(vv)) { uid = vv; break; }
                }
            }
            if (uid == null) {
                for (String vv : cand) {
                    if (rnIsUser(vv)) { uid = vv; break; }
                }
            }
            if (uid == null) { T("rn profile: no uid, cand=" + cand); return; }

            // 2) 读页面上的「群昵称」和标题
            java.util.List<String> texts = new java.util.ArrayList<String>();
            Object decor = act.getWindow() == null ? null : act.getWindow().getDecorView();
            rnCollectTexts(decor, texts, 0);

            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            String title = null;
            for (int i = 0; i < texts.size(); i++) {
                String t = texts.get(i);
                if (t == null) continue;
                String tt = t.trim();
                if (tt.length() == 0 || tt.length() > 60) continue;
                if (tt.startsWith("\u7fa4\u6635\u79f0")) {          // 群昵称：xxx / 群昵称: xxx
                    int p = tt.indexOf('\uff1a');
                    if (p < 0) p = tt.indexOf(':');
                    if (p > 0) {
                        String v2 = tt.substring(p + 1).trim();
                        if (v2.length() > 0) names.add(v2);
                    } else if (tt.equals("\u7fa4\u6635\u79f0") && i + 1 < texts.size()) {
                        String v2 = texts.get(i + 1).trim();
                        if (v2.length() > 0) names.add(v2);
                    }
                }
                if (tt.equals("\u6635\u79f0") && i + 1 < texts.size()) {
                    String v2 = texts.get(i + 1).trim();
                    if (v2.length() > 0) names.add(v2);
                }
            }
            // 标题栏的名字（普通昵称）
            if (decor != null) title = rnTitleText(decor);
            if (title != null && title.trim().length() > 0) names.add(title.trim());

            if (names.isEmpty()) { T("rn profile: uid=" + uid + " no name"); return; }
            java.util.Set<String> mem = RN_MEMBERS;
            boolean inRoom = mem != null && mem.contains(uid);
            for (String nm : names) {
                String n2 = nm.trim();
                if (n2.length() < 1 || n2.length() > 40) continue;
                if (inRoom) {
                    java.util.Map<String, String> mm = NAME2USER;
                    if (mm != null) mm.put(n2, uid);
                    rnSaveAlias(CUR_ROOM, n2, uid);
                } else {
                    rnSaveAlias(null, n2, uid);
                }
            }
            T("rn profile: uid=" + uid + " names=" + names + " cached=" + rnCached(uid));
            if (rnCached(uid) == null && rnOn()) rnEnqueue(uid, CUR_ROOM);
            else refreshChatNamesOnce();
        } catch (Throwable t) { T("rn profile err " + t); }
    }

    static String rnTitleText(Object v) {
        try {
            if (v == null || ID_TITLE == 0) return null;
            if (v instanceof android.widget.TextView) {
                if (((android.view.View) v).getId() == ID_TITLE) {
                    CharSequence cs = ((android.widget.TextView) v).getText();
                    return cs == null ? null : cs.toString();
                }
                return null;
            }
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    String r = rnTitleText(g.getChildAt(i));
                    if (r != null) return r;
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    static void rnSaveAlias(String room, String name, String uid) {
        try {
            String r = (room == null || room.length() == 0) ? "-" : room;
            java.io.File f = new java.io.File(RN_ALIAS_PATH);
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(r + "\t" + name.replace('\t', ' ').replace('\n', ' ') + "\t" + uid + "\n");
            w.close();
        } catch (Throwable t) { T("rn alias save err " + t); }
    }

    static void rnLoadAlias(String room, java.util.Map<String, String> m) {
        try {
            String all = readText(RN_ALIAS_PATH);
            if (all == null) return;
            int n = 0;
            for (String line : all.split("\n")) {
                String[] p = line.split("\t");
                if (p.length < 3) continue;
                if (!"-".equals(p[0].trim()) && !p[0].trim().equals(room)) continue;
                m.put(p[1].trim(), p[2].trim());
                n++;
            }
            if (n > 0) T("rn alias: loaded " + n + " for " + room);
        } catch (Throwable t) { T("rn alias load err " + t); }
    }

    static void resolveTitleId(Activity act) {
        if (ID_TITLE != 0) return;
        try {
            Object res = XposedHelpers.callMethod(act, "getResources");
            Object v = XposedHelpers.callMethod(res, "getIdentifier", "obn", "id", "com.tencent.mm");
            int id = v instanceof Integer ? ((Integer) v).intValue() : 0;
            if (id == 0) id = 0x7f0a69cb;
            ID_TITLE = id;
            T("title id = 0x" + Integer.toHexString(id));
        } catch (Throwable t) { T("title id err " + t); }
    }

    static String stripCount(String t) {
        if (t == null) return null;
        String s0 = t.trim();
        int p1 = s0.lastIndexOf('('), p2 = s0.lastIndexOf('\uff08');
        int p = p1 > p2 ? p1 : p2;
        if (p > 0 && (s0.endsWith(")") || s0.endsWith("\uff09"))) {
            String inner = s0.substring(p + 1, s0.length() - 1).trim();
            boolean digits = inner.length() > 0;
            for (int i = 0; i < inner.length(); i++) {
                if (!Character.isDigit(inner.charAt(i))) { digits = false; break; }
            }
            if (digits) return s0.substring(0, p).trim();
        }
        return s0;
    }

    static String roomByTitleDb(String name) {
        try {
            Object db = db0();
            if (db == null) { T("rn title db null: " + name); return null; }
            String safe = name.replace("'", "''");
            Cursor c = (Cursor) rawQuery(db,
                    "select username from rcontact where (nickname='" + safe
                    + "' or conRemark='" + safe + "') and username like '%@chatroom' limit 1", null);
            try { if (c != null && c.moveToFirst()) return c.getString(0); }
            finally { if (c != null) c.close(); }
            if (TITLE_MISS.getAndIncrement() < 30) T("rn title miss: [" + name + "]");
        } catch (Throwable t) { T("rn title db err " + t); }
        return null;
    }

    static void rnSnap() {
        try {
            RN_SNAP = "room=" + CUR_ROOM + " map=" + (NAME2USER == null ? 0 : NAME2USER.size())
                    + " txt=" + TXT_HITS.get() + " ttl=" + TITLE_HITS.get()
                + " hits=" + RN_HITS.get() + " mhit=" + RN_MAP_HITS.get()
                    + " subs=" + RN_SUBS.get() + " q=" + RN_QUERIES.get() + " last=" + RN_LAST_RESP;
        } catch (Throwable t) {}
    }

    /** v94：真名自检 —— 把当前状态写进 trace，并让引导器上传一份诊断 */
    static String rnState() {
        int cache = 0;
        try {
            String all = readText(RN_CACHE_PATH);
            if (all != null) for (int i = 0; i < all.length(); i++) if (all.charAt(i) == '\n') cache++;
        } catch (Throwable t) {}
        return "hook=" + (RN_HOOK_OK ? 1 : 0)
                + " nick=0x" + Integer.toHexString(ID_NICK)
                + " title=0x" + Integer.toHexString(ID_TITLE)
                + " on=" + (rnOn() ? 1 : 0)
                + " room=" + CUR_ROOM
                + " map=" + (NAME2USER == null ? 0 : NAME2USER.size())
                + " cache=" + cache
                + " txt=" + TXT_HITS.get() + " ttl=" + TITLE_HITS.get()
                + " hits=" + RN_HITS.get()
                + " mhit=" + RN_MAP_HITS.get()
                + " subs=" + RN_SUBS.get()
                + " q=" + RN_QUERIES.get()
                + " last=" + RN_LAST_RESP
                + " prev={" + RN_SNAP + "}"
                + " act=" + (CUR_ACT != null && CUR_ACT.get() != null ? 1 : 0);
    }

    static void rnUploadDiag(final String reason) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    java.lang.reflect.Method m = c.getDeclaredMethod("uploadDiag", String.class);
                    m.setAccessible(true);
                    m.invoke(null, reason);
                    T("rn diag upload -> " + reason);
                } catch (Throwable t) {
                    Throwable cc = t;
                    while (cc.getCause() != null) cc = cc.getCause();
                    T("rn diag upload fail " + cc);
                }
            }
        }).start();
    }

    /** 只在必要时自动传一次（默认 6 小时一次），免得刷屏 */
    static void rnDiagOnce(String reason) {
        try {
            java.io.File ts = new java.io.File(RN_DIAG_TS);
            long now = System.currentTimeMillis();
            if (ts.exists() && now - ts.lastModified() < 6 * 3600 * 1000L) return;
            ts.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(ts, false);
            w.write(String.valueOf(now));
            w.close();
            T("rn diag auto " + reason);
            rnUploadDiag(reason);
        } catch (Throwable t) {}
    }

    static void rnSelfCheck(final Activity act) {
        T("rn selfcheck " + rnState());
        T("feature diag running="+BUILD+" edition="+EDITION+" cached="+readText(WXDIR+"kernel.ver"));
        T("ARMARK state "+RecallMarkLayout.state());
        toast(act, "\u6b63\u5728\u4e0a\u62a5\u2026");
        rnUploadDiag("realname-debug");
    }

    static void enterRoom(String room) {
        if (room == null || !room.endsWith("@chatroom")) return;
        if (room.equals(CUR_ROOM)) return;
        if (CUR_ROOM != null) rnSnap();
        CUR_ROOM = room;
        NAME2USER = null;
        T("rn: enter " + room);
        rnOnGroupOpen(room);
    }

    /** 标题变化：判断是哪个群；不是群就清空上下文 */
    static void onTitleText(final String txt) {
        try {
            if (txt.equals(LAST_TITLE)) return;
            LAST_TITLE = txt;
            final String t = stripCount(txt);
            if (t.length() == 0 || t.equals("\u5fae\u4fe1") || t.equals("\u901a\u8baf\u5f55")
                    || t.equals("\u53d1\u73b0") || t.equals("\u6211")) {
                if (CUR_ROOM != null) { rnSnap(); CUR_ROOM = null; NAME2USER = null; T("rn: leave room"); }
                return;
            }
            String hit = ROOM_BY_TITLE.get(t);
            if (hit != null) { enterRoom(hit); return; }
            new Thread(new Runnable() {
                public void run() {
                    String room = roomByTitleDb(t);
                    if (room == null) {
                        if (t.equals(LAST_TITLE) && CUR_ROOM != null) {
                            rnSnap();
                            CUR_ROOM = null; NAME2USER = null;
                            T("rn: leave room (not a group)");
                        }
                        return;
                    }
                    ROOM_BY_TITLE.put(t, room);
                    enterRoom(room);
                }
            }).start();
        } catch (Throwable t) {}
    }


    // ===== 自动学「显示名 -> wxid」：消息头像的 tag 里就带着发送者 wxid =====
    static void resolveNickId(Object ctx) {
        if (ID_NICK != 0) return;
        try {
            Object res = XposedHelpers.callMethod(ctx, "getResources");
            Object v = XposedHelpers.callMethod(res, "getIdentifier", "brc", "id", "com.tencent.mm");
            int id = v instanceof Integer ? ((Integer) v).intValue() : 0;
            if (id == 0) id = 0x7f0a10bf;
            ID_NICK = id;
            T("nick id = 0x" + Integer.toHexString(id));
        } catch (Throwable t) { T("nick id err " + t); }
    }

    static Object findNickView(Object v, int depth) {
        if (v == null || depth > 14) return null;
        try {
            if (v instanceof android.widget.TextView) {
                int id = ((android.view.View) v).getId();
                if (ID_NICK != 0 && id == ID_NICK) return v;
            }
            if (!(v instanceof android.view.ViewGroup)) return null;
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Object r = findNickView(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        } catch (Throwable t) {}
        return null;
    }

    static void hookAvatarTag(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(android.view.View.class, "setTag", Object.class, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        Object tag = p.args[0];
                        if (tag == null) return;
                        if (!tag.getClass().getName().endsWith(".ws")) return;
                        String user = null, rm = null;
                        try { Object v = XposedHelpers.getObjectField(tag, "b"); if (v instanceof String) user = (String) v; } catch (Throwable t) {}
                        try { Object v = XposedHelpers.getObjectField(tag, "e"); if (v instanceof String) rm = (String) v; } catch (Throwable t) {}
                        if (rm == null || !rm.endsWith("@chatroom")) return;      // 只认群消息行
                        Object av = p.thisObject;
                        // 先确认这是「聊天页里的消息行」（列表页的行也有 tag，不能拿来当群上下文）
                        Object item = null, cur = av;
                        for (int up = 0; up < 10 && cur != null; up++) {
                            Object par = XposedHelpers.callMethod(cur, "getParent");
                            if (!(par instanceof android.view.View)) break;
                            cur = par;
                            Object tg = XposedHelpers.callMethod(cur, "getTag");
                            if (tg != null && tg.getClass().getName().startsWith("com.tencent.mm.ui.chatting.viewitems.")) { item = cur; break; }
                        }
                        if (item == null) return;
                        if (ID_NICK == 0) {
                            try { resolveNickId(XposedHelpers.callMethod(av, "getContext")); } catch (Throwable t) {}
                        }
                        String room = CUR_ROOM;
                        if (room == null) {
                            // v99：标题解析不出群（那台 Android 14 就是这样，ttl=21 但 room 一直是 null）时，
                            // 用消息行自带的群 id 兜底建上下文 —— 微信自己把答案塞在 tag 里了。
                            T("rn room from tag " + rm);
                            enterRoom(rm);
                            return;
                        }
                        if (user == null || !rm.equals(room)) return;
                        Object nameView = findNickView(item, 0);
                        if (nameView == null) return;
                        Object ncs = XposedHelpers.callMethod(nameView, "getText");
                        if (!(ncs instanceof CharSequence)) return;
                        String name = ncs.toString().trim();
                        int L = name.length();
                        if (L < 1 || L > 40) return;
                        if (name.indexOf("\uff08") >= 0) return;         // 已经拼过后缀的别再学

                        java.util.Map<String, String> m = NAME2USER;
                        String old = (m == null) ? null : m.get(name);
                        if (m == null || !user.equals(old)) {
                            if (m != null) m.put(name, user);
                            rnSaveAlias(room, name, user);
                            T("rn learn: [" + name + "] -> " + user);
                        }
                        String mask = rnCached(user);
                        boolean rnOnNow = rnOn();
                        if (rnOnNow && mask != null && mask.length() > 0) {
                            // 有缓存：直接把这一行的名字补上后缀
                            XposedHelpers.callMethod(nameView, "setText", name + "\uff08" + mask + "\uff09");
                        } else if (rnOnNow) {
                            rnEnqueue(user, room);
                        }
                    } catch (Throwable t) {}
                }
            });
            T("avatar tag hook ok");
        } catch (Throwable t) { T("avatar tag hook fail " + t); }
    }

    /** 唯一入口：TextView.setText 的真实漏斗 */
    static void hookChatNames(ClassLoader cl) {
        try {
            Class<?> bt = Class.forName("android.widget.TextView$BufferType");
            XposedHelpers.findAndHookMethod(android.widget.TextView.class, "setText",
                    CharSequence.class, bt, boolean.class, int.class, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (MODULE_OFF) return;
                            try {
                                TXT_HITS.incrementAndGet();
                                int id = ((android.view.View) p.thisObject).getId();
                                // v102：标题栏 id 在某些设备/版本上不是 0x7f0a69cb（ttl 一直是 0）。
                                // 这种情况下采样几条 setText，把「谁在设文字」记下来，好定位真正的标题控件。
                                try {
                                    if (id != ID_TITLE
                                            && (TITLE_HITS.get() == 0 || new java.io.File(WXDIR + "title.sample").exists())
                                            && TEXT_SAMPLES.get() < 40) {
                                        Object o0 = p.args[0];
                                        if (o0 instanceof CharSequence) {
                                            String s0 = o0.toString().replace('\n', ' ').trim();
                                            if (s0.length() >= 2 && s0.length() <= 40) {
                                                TEXT_SAMPLES.incrementAndGet();
                                                T("setText id=0x" + Integer.toHexString(id) + " [" + s0 + "]");
                                            }
                                        }
                                    }
                                } catch (Throwable t2) {}
                                if (ID_TITLE != 0 && id == ID_TITLE) {
                                    TITLE_HITS.incrementAndGet();
                                    Object o0 = p.args[0];
                                    if (o0 instanceof CharSequence) onTitleText(o0.toString());
                                    return;
                                }
                                if (CUR_ROOM == null || !rnOn()) return;
                                // 只认「发送者昵称」那个 TextView；消息正文/按钮/时间一律不碰
                                int vid = ((android.view.View) p.thisObject).getId();
                                if (vid != (ID_NICK != 0 ? ID_NICK : 0x7f0a10bf)) return;
                                java.util.Map<String, String> m = NAME2USER;
                                if (m == null) return;
                                Object o = p.args[0];
                                if (!(o instanceof CharSequence)) return;
                                String txt = o.toString();
                                int L = txt.length();
                                if (L < 1 || L > 40) return;
                                RN_HITS.incrementAndGet();
                                String u = m.get(txt);
                                if (u == null || u.length() == 0 || u.charAt(0) == '\u0000') return;
                                RN_MAP_HITS.incrementAndGet();

                                String mask = rnCached(u);
                                if (mask == null || mask.length() == 0) {
                                    // 没缓存：只有「群里刚有新消息」时才去查，翻历史不触发
                                    long at = RN_LAST_MSG;
                                    if (rnOn() && at > 0 && System.currentTimeMillis() - at < 4000) {
                                        RN_LAST_MSG = 0;
                                        rnEnqueue(u, CUR_ROOM);
                                    }
                                    return;
                                }
                                if (txt.indexOf(mask) >= 0) return;
                                p.args[0] = txt + "\uff08" + mask + "\uff09";
                                RN_SUBS.incrementAndGet();
                            } catch (Throwable t) {}
                        }
                    });
            RN_HOOK_OK = true;
            HOOK_NAME_OK = 1;
            T("chat name hook ok");
        } catch (Throwable t) { RN_HOOK_OK = false; T("chat name hook fail " + t); }
    }

    // ================= 防撤回（跨版本自动定位） =================
    // 老做法是把混淆类名写死（8.0.80: t93.l / ai5.b0 / ai5.d0），微信换版本就全挂。
    // 现在启动时扫微信自己的 base.apk，把所有
    //     update(String table, ContentValues cv, String where, String[] args) -> int
    // 的实现类找出来全部挂上：8.0.49 -> o94.y / wc2.j，8.0.80 -> ai5.b0 / ai5.d0 / t93.l。
    // 撤回的本质是原地 UPDATE message 行（type 低 16 位 = 10002），拦掉就是防撤回。

    static final java.io.File PROBE = new java.io.File(WXDIR + "sqlprobe.on");
    static final java.io.File REVOKE_TEST = new java.io.File(WXDIR + "revoke.test");
    static final java.io.File REVOKE_SELF_TEST = new java.io.File(WXDIR + "revoke.self.test");   // 自检用：挑一条自己发的消息
    static volatile boolean TEST_RUNNING = false;
    static volatile boolean NO_TIP = false;
    static volatile Object DB_REF = null;
    static final java.util.ArrayList<java.lang.reflect.Method> WRITE_METHODS =
            new java.util.ArrayList<java.lang.reflect.Method>();
    static final java.util.ArrayList<Object> DB_LIST = new java.util.ArrayList<Object>();
    static final java.util.HashSet<String> DB_OPENED = new java.util.HashSet<String>();

    static String shorten(String s) {
        if (s == null) return "null";
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }

    static String appApk(Object ctx) {
        if (ctx == null) return null;
        try {
            Object ai = XposedHelpers.callMethod(ctx, "getApplicationInfo");
            Object sd = XposedHelpers.getObjectField(ai, "sourceDir");
            if (sd != null) return String.valueOf(sd);
        } catch (Throwable t) {}
        try {
            Object pc = XposedHelpers.callMethod(ctx, "getPackageCodePath");
            if (pc != null) return String.valueOf(pc);
        } catch (Throwable t) {}
        return null;
    }

    static String apkPath() {
        try {
            Object app = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ActivityThread"), "currentApplication");
            String s = appApk(app);
            if (s != null) return s;
        } catch (Throwable t) {}
        try {
            String s = appApk(APP_CTX);
            if (s != null) return s;
        } catch (Throwable t) {}
        try {
            java.io.File[] a = new java.io.File("/data/app").listFiles();
            if (a != null) for (java.io.File d1 : a) {
                java.io.File[] b = d1.listFiles();
                if (b == null) continue;
                for (java.io.File d2 : b) {
                    if (d2.getName().indexOf("com.tencent.mm-") < 0) continue;
                    java.io.File apk = new java.io.File(d2, "base.apk");
                    if (apk.exists()) return apk.getAbsolutePath();
                }
            }
        } catch (Throwable t) {}
        return null;
    }

    static int rd4(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static int rd2(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    static String dexStr(byte[] b, int off) {
        int q = off;
        while ((b[q] & 0x80) != 0) q++;     // 跳过 uleb128 的 utf16 长度
        q++;
        int e = q;
        while (b[e] != 0) e++;
        return new String(b, q, e - q, java.nio.charset.Charset.forName("UTF-8"));
    }

    static String dexType(byte[] b, int idx, int typeOff, int strOff, java.util.HashMap<Integer, String> cache) {
        String s = cache.get(Integer.valueOf(idx));
        if (s != null) return s;
        int t = rd4(b, typeOff + 4 * idx);
        s = dexStr(b, rd4(b, strOff + 4 * t));
        cache.put(Integer.valueOf(idx), s);
        return s;
    }

    static void dexScan(byte[] b, java.util.Set<String> out) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            if (!"I".equals(dexType(b, rd4(b, po + 4), typeOff, strOff, tc))) continue;
            int pl = rd4(b, po + 8);
            if (pl == 0 || rd4(b, pl) != 4) continue;
            if (!"Ljava/lang/String;".equals(dexType(b, rd2(b, pl + 4), typeOff, strOff, tc))) continue;
            if (!"Landroid/content/ContentValues;".equals(dexType(b, rd2(b, pl + 6), typeOff, strOff, tc))) continue;
            if (!"Ljava/lang/String;".equals(dexType(b, rd2(b, pl + 8), typeOff, strOff, tc))) continue;
            if (!"[Ljava/lang/String;".equals(dexType(b, rd2(b, pl + 10), typeOff, strOff, tc))) continue;
            out.add(dexType(b, cls, typeOff, strOff, tc));
        }
    }

    static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        in.close();
        return bo.toByteArray();
    }

    // ================= 一次性 dex 索引（冷启动提速） =================
    // 旧实现：真名/防撤/朋友圈/红包 5 个功能各自把 18 个 classes*.dex（共约 180MB）扫一遍，
    // 而且全跑在微信主线程上，合计 ~4.7s —— 这就是「微信重新打开要卡 5 秒」的根因。
    // 现在：一遍扫完全部特征，结果落盘缓存（dex.idx）；微信升级（apk 路径/大小/时间变）自动重扫。
    static final class DexIdx {
        String key = "";
        final java.util.LinkedHashSet<String> dbImpls = new java.util.LinkedHashSet<String>();
        final java.util.LinkedHashSet<String> am3 = new java.util.LinkedHashSet<String>();
        final java.util.LinkedHashSet<String> am6 = new java.util.LinkedHashSet<String>();
        final java.util.LinkedHashSet<String> snsObj = new java.util.LinkedHashSet<String>();
        final java.util.LinkedHashSet<String> snsStore = new java.util.LinkedHashSet<String>();
    }

    static final java.io.File DEX_IDX_FILE = new java.io.File(WXDIR + "dex.idx");
    static volatile DexIdx DEX_IDX = null;
    static volatile String DEX_IDX_SRC = "-";

    /** 微信 Tinker 热补丁目录下的 dex 包（补丁里的类不在 base.apk 里） */
    static java.util.List<java.io.File> tinkerDexZips() {
        java.util.ArrayList<java.io.File> out = new java.util.ArrayList<java.io.File>();
        try {
            java.io.File t = new java.io.File(computeWxData() + "/tinker");   // 1.63：分身/多用户也走当前用户目录
            java.io.File[] ps = t.listFiles();
            if (ps == null) return out;
            java.util.Arrays.sort(ps);
            for (java.io.File p : ps) {
                if (!p.isDirectory() || !p.getName().startsWith("patch-")) continue;
                java.io.File d = new java.io.File(p, "dex");
                java.io.File[] fs = d.listFiles();
                if (fs == null) continue;
                java.util.Arrays.sort(fs);
                for (java.io.File f : fs) {
                    if (!f.isFile()) continue;
                    if (!f.getName().endsWith(".apk") && !f.getName().endsWith(".dex") && !f.getName().endsWith(".jar")) continue;
                    if (f.length() > 400L * 1024 * 1024) continue;
                    out.add(f);
                }
            }
        } catch (Throwable t) {}
        return out;
    }

    static String dexIdxKey(String apk) {
        StringBuilder sb = new StringBuilder(BUILD).append('|').append(apk);
        try {
            java.io.File f = new java.io.File(apk);
            sb.append('|').append(f.length()).append('|').append(f.lastModified());
        } catch (Throwable t) {}
        try {
            for (java.io.File f : tinkerDexZips())
                sb.append('|').append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified());
        } catch (Throwable t) {}
        return sb.toString();
    }

    static void dexIdxAdd(java.util.Set<String> set, String v) {
        if (v == null) return;
        String[] a = v.trim().split(" ");
        for (int i = 0; i < a.length; i++) if (a[i].length() > 0) set.add(a[i]);
    }

    static String dexIdxJoin(java.util.Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String x : set) sb.append(x).append(' ');
        return sb.toString().trim();
    }

    static DexIdx dexIdxLoad(String key) {
        try {
            if (!DEX_IDX_FILE.exists()) return null;
            DexIdx idx = new DexIdx();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(DEX_IDX_FILE), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.length() < 3) continue;
                    if (line.startsWith("K ")) idx.key = line.substring(2);
                    else if (line.startsWith("DB ")) dexIdxAdd(idx.dbImpls, line.substring(3));
                    else if (line.startsWith("A3 ")) dexIdxAdd(idx.am3, line.substring(3));
                    else if (line.startsWith("A6 ")) dexIdxAdd(idx.am6, line.substring(3));
                    else if (line.startsWith("SO ")) dexIdxAdd(idx.snsObj, line.substring(3));
                    else if (line.startsWith("SS ")) dexIdxAdd(idx.snsStore, line.substring(3));
                }
            } finally { try { r.close(); } catch (Throwable t) {} }
            if (!key.equals(idx.key)) return null;
            if (idx.dbImpls.isEmpty() && idx.am3.isEmpty() && idx.snsStore.isEmpty()) return null;
            return idx;
        } catch (Throwable t) { return null; }
    }

    static void dexIdxSave(DexIdx idx) {
        java.io.File tmp = new java.io.File(WXDIR + "dex.idx.tmp");
        try {
            java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
            java.io.Writer w = new java.io.OutputStreamWriter(fo, "UTF-8");
            try {
                w.write("K " + idx.key + "\n");
                w.write("DB " + dexIdxJoin(idx.dbImpls) + "\n");
                w.write("A3 " + dexIdxJoin(idx.am3) + "\n");
                w.write("A6 " + dexIdxJoin(idx.am6) + "\n");
                w.write("SO " + dexIdxJoin(idx.snsObj) + "\n");
                w.write("SS " + dexIdxJoin(idx.snsStore) + "\n");
            } finally { try { w.close(); } catch (Throwable t) {} }
            tmp.renameTo(DEX_IDX_FILE);
        } catch (Throwable t) { T("dex idx save err " + t); }
    }

    /** 一遍扫完 18 个 dex，把所有功能的特征类一次性捞出来（原来要扫 5 遍）。 */
    static void dexScanAll(byte[] b, DexIdx idx) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            String ret = dexType(b, rd4(b, po + 4), typeOff, strOff, tc);
            if (!"V".equals(ret) && !"I".equals(ret) && !"Landroid/database/Cursor;".equals(ret)
                    && !ret.startsWith("Lcom/tencent/mm/modelbase/")) continue;
            int pl = rd4(b, po + 8);
            if (pl == 0) continue;
            int n = rd4(b, pl);
            if (n < 1) continue;
            String p0 = dexType(b, rd2(b, pl + 4), typeOff, strOff, tc);
            // ① 消息库 update 实现类：int (String, ContentValues, String, String[])
            if (n == 4 && "I".equals(ret) && "Ljava/lang/String;".equals(p0)) {
                String p1 = dexType(b, rd2(b, pl + 6), typeOff, strOff, tc);
                String p2 = dexType(b, rd2(b, pl + 8), typeOff, strOff, tc);
                String p3 = dexType(b, rd2(b, pl + 10), typeOff, strOff, tc);
                if ("Landroid/content/ContentValues;".equals(p1) && "Ljava/lang/String;".equals(p2)
                        && "[Ljava/lang/String;".equals(p3)) {
                    idx.dbImpls.add(dexType(b, cls, typeOff, strOff, tc));
                }
            }
            // ② 消息加入管线（AddMsg）三参：(String, Map, modelbase.*) -> modelbase.*
            if (n == 3 && ret.startsWith("Lcom/tencent/mm/modelbase/") && "Ljava/lang/String;".equals(p0)) {
                String p1 = dexType(b, rd2(b, pl + 6), typeOff, strOff, tc);
                String p2 = dexType(b, rd2(b, pl + 8), typeOff, strOff, tc);
                if ("Ljava/util/Map;".equals(p1) && p2.startsWith("Lcom/tencent/mm/modelbase/")) {
                    idx.am3.add(dexType(b, cls, typeOff, strOff, tc));
                }
            }
            // ③ 消息加入管线（AddMsg）六参：(String, long, modelbase.*, String, String, String) -> void
            if (n == 6 && "V".equals(ret) && "Ljava/lang/String;".equals(p0)) {
                String p1 = dexType(b, rd2(b, pl + 6), typeOff, strOff, tc);
                String p2 = dexType(b, rd2(b, pl + 8), typeOff, strOff, tc);
                if ("J".equals(p1) && p2.startsWith("Lcom/tencent/mm/modelbase/")) {
                    idx.am6.add(dexType(b, cls, typeOff, strOff, tc));
                }
            }
            if (n == 1) {
                // ④ 朋友圈对象钩子：void x(protobuf.SnsObject|TimeLineObject)，类在 plugin/sns 下
                if ("V".equals(ret) && (p0.endsWith("protobuf/SnsObject;") || p0.endsWith("protobuf/TimeLineObject;"))) {
                    String cn = dexType(b, cls, typeOff, strOff, tc);
                    if (cn.startsWith("Lcom/tencent/mm/plugin/sns/")) idx.snsObj.add(cn);
                }
            }
            // ⑤ 朋友圈存储类：Cursor x(>=6 参)，类在 plugin/sns/storage 下
            if (n >= 6 && "Landroid/database/Cursor;".equals(ret)) {
                String cn = dexType(b, cls, typeOff, strOff, tc);
                if (cn.startsWith("Lcom/tencent/mm/plugin/sns/storage/")) idx.snsStore.add(cn);
            }
        }
    }

    static volatile Thread DEX_PREWARM = null;

    /** 后台预热 DEX 索引；dexIdx() 本身是同步的，谁先到谁算，另一个等锁就行。 */
    static void startDexPrewarm() {
        Thread t = DEX_PREWARM;
        if (DEX_IDX != null || (t != null && t.isAlive())) return;
        Thread n = new Thread(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    DexIdx x = dexIdx();
                    T("dex prewarm done " + (System.currentTimeMillis() - t0) + "ms n=" + (x == null ? -1 : x.dbImpls.size()));
                } catch (Throwable t) { T("dex prewarm err " + t); }
            }
        });
        n.setName("wxentry-dex");
        n.setPriority(Thread.MIN_PRIORITY);
        DEX_PREWARM = n;
        n.start();
    }

    static DexIdx dexIdx() {
        DexIdx c = DEX_IDX;
        if (c != null) return c;
        synchronized (Kernel.class) {
            if (DEX_IDX != null) return DEX_IDX;
            long t0 = System.currentTimeMillis();
            String apk = apkPath();
            String key = dexIdxKey(apk);
            DexIdx idx = dexIdxLoad(key);
            if (idx != null) {
                DEX_IDX_SRC = "cache";
            } else {
                idx = new DexIdx();
                idx.key = key;
                if (apk != null) {
                    java.util.zip.ZipFile zf = null;
                    try {
                        zf = new java.util.zip.ZipFile(apk);
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) {
                            java.util.zip.ZipEntry e = en.nextElement();
                            String nm = e.getName();
                            if (!nm.startsWith("classes") || !nm.endsWith(".dex")) continue;
                            try { dexScanAll(readAll(zf.getInputStream(e)), idx); }
                            catch (Throwable t) { T("idx dex err " + nm + " " + t); }
                        }
                    } catch (Throwable t) { T("idx zip err " + t); }
                    finally { if (zf != null) { try { zf.close(); } catch (Throwable t) {} } }
                }
                // 再扫一遍微信的 Tinker 补丁（热补丁里的类不在 base.apk 中）
                try {
                    java.util.List<java.io.File> zips = tinkerDexZips();
                    for (java.io.File tf : zips) {
                        java.util.zip.ZipFile zf2 = null;
                        try {
                            zf2 = new java.util.zip.ZipFile(tf);
                            java.util.Enumeration<? extends java.util.zip.ZipEntry> en2 = zf2.entries();
                            while (en2.hasMoreElements()) {
                                java.util.zip.ZipEntry e2 = en2.nextElement();
                                String nm2 = e2.getName();
                                if (!nm2.startsWith("classes") || !nm2.endsWith(".dex")) continue;
                                try { dexScanAll(readAll(zf2.getInputStream(e2)), idx); }
                                catch (Throwable t) { T("idx tinker dex err " + nm2 + " " + t); }
                            }
                        } catch (Throwable t) { T("idx tinker err " + tf.getName() + " " + t); }
                        finally { if (zf2 != null) { try { zf2.close(); } catch (Throwable t) {} } }
                    }
                    if (!zips.isEmpty()) T("idx tinker zips " + zips.size());
                } catch (Throwable t) { T("idx tinker outer " + t); }
                dexIdxSave(idx);
                DEX_IDX_SRC = "scan";
            }
            DEX_IDX = idx;
            T("dex idx " + DEX_IDX_SRC + " " + (System.currentTimeMillis() - t0) + "ms"
                    + " db=" + idx.dbImpls.size() + " am3=" + idx.am3.size() + " am6=" + idx.am6.size()
                    + " so=" + idx.snsObj.size() + " ss=" + idx.snsStore.size());
            return idx;
        }
    }

    static java.util.List<Class<?>> discoverDbImpls(ClassLoader cl) {
        DexIdx idx = dexIdx();
        java.util.ArrayList<Class<?>> out = new java.util.ArrayList<Class<?>>();
        StringBuilder sb = new StringBuilder();
        for (String d : idx.dbImpls) {
            if (d.length() < 3) continue;
            String cn = d.substring(1, d.length() - 1).replace('/', '.');
            if (cn.startsWith("android.") || cn.startsWith("com.tencent.wcdb.")) continue;
            try {
                Class<?> k = Class.forName(cn, false, cl);
                if (k.isInterface() || java.lang.reflect.Modifier.isAbstract(k.getModifiers())) continue;
                out.add(k);
                sb.append(cn).append(' ');
            } catch (Throwable t) {}
        }
        T("dw impls[" + out.size() + "] " + sb);
        return out;
    }

    // ===== AddMsg（消息加入管线）探针 =====
    // 微X 在 8.0.49 上钩的是 tj0.s.c4/h；8.0.80 全包只有一个类带同样签名：w41.u.j7/c。
    // 这里先只观察：撤回消息进管线时把字段打出来，确认后再接「取消处理」。
    static final java.io.File ADDPROBE = new java.io.File(WXDIR + "addmsg.probe");
    static final java.io.File REVOKE_ADDMSG_BLOCK = new java.io.File(WXDIR + "addmsg.block.on");
    static final java.io.File SELF_RECALL_BLOCK = new java.io.File(WXDIR + "selfrecall.block.on");
    static final java.io.File SELF_RECALL_OFF = new java.io.File(WXDIR + "selfrecall.off");
    static final java.io.File SELF_RECALL_KEEP = new java.io.File(WXDIR + "selfrecall.keep.on");   // 内联保留副本（有崩溃风险）   // 存在=自己的撤回也不留副本

    static String amType(Object addMsg) {
        try { Object v = XposedHelpers.callMethod(addMsg, "getType"); if (v != null) return String.valueOf(v); } catch (Throwable t) {}
        try { Object v = XposedHelpers.getObjectField(addMsg, "type"); return "field:" + v; } catch (Throwable t) {}
        return "?";
    }

    static void dexScanAddMsg(byte[] b, java.util.Set<String> three, java.util.Set<String> six) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            String ret = dexType(b, rd4(b, po + 4), typeOff, strOff, tc);
            int pl = rd4(b, po + 8);
            if (pl == 0) continue;
            int n = rd4(b, pl);
            if (n == 3) {
                if (!"Ljava/lang/String;".equals(dexType(b, rd2(b, pl + 4), typeOff, strOff, tc))) continue;
                if (!"Ljava/util/Map;".equals(dexType(b, rd2(b, pl + 6), typeOff, strOff, tc))) continue;
                if (!dexType(b, rd2(b, pl + 8), typeOff, strOff, tc).startsWith("Lcom/tencent/mm/modelbase/")) continue;
                if (!ret.startsWith("Lcom/tencent/mm/modelbase/")) continue;
                three.add(dexType(b, cls, typeOff, strOff, tc));
            } else if (n == 6) {
                if (!"Ljava/lang/String;".equals(dexType(b, rd2(b, pl + 4), typeOff, strOff, tc))) continue;
                if (!"J".equals(dexType(b, rd2(b, pl + 6), typeOff, strOff, tc))) continue;
                if (!dexType(b, rd2(b, pl + 8), typeOff, strOff, tc).startsWith("Lcom/tencent/mm/modelbase/")) continue;
                if (!"V".equals(ret)) continue;
                six.add(dexType(b, cls, typeOff, strOff, tc));
            }
        }
    }

    static java.util.List<Class<?>> discoverAddMsg(ClassLoader cl) {
        DexIdx idx = dexIdx();
        java.util.ArrayList<Class<?>> out = new java.util.ArrayList<Class<?>>();
        StringBuilder sb = new StringBuilder();
        for (String d : idx.am3) {
            if (!idx.am6.contains(d)) continue;
            String cn = d.substring(1, d.length() - 1).replace('/', '.');
            try {
                out.add(Class.forName(cn, false, cl));
                sb.append(cn).append(' ');
            } catch (Throwable t) {}
        }
        T("addmsg cands[" + out.size() + "] " + sb);
        return out;
    }

    // ===== 侦察：记录所有模块（含微X）注册的 Xposed 钩子 =====
    static final java.io.File HOOKPROBE = new java.io.File(WXDIR + "hookprobe.on");

    static void hookBridgeProbe(ClassLoader cl) {
        if (!HOOKPROBE.exists()) return;
        try {
            Class<?> xb = XposedBridge.class;
            int n = 0;
            for (final java.lang.reflect.Method m : xb.getDeclaredMethods()) {
                if (!m.getName().equals("hookMethod")) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length < 2 || !java.lang.reflect.Member.class.isAssignableFrom(ps[0])) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                Object mem = p.args[0];
                                if (!(mem instanceof java.lang.reflect.Member)) return;
                                java.lang.reflect.Member mm = (java.lang.reflect.Member) mem;
                                String where = "?";
                                StackTraceElement[] st = new Throwable().getStackTrace();
                                for (StackTraceElement e : st) {
                                    String cn = e.getClassName();
                                    if (cn.startsWith("de.robv") || cn.startsWith("com.zafiro") || cn.startsWith("java.")
                                            || cn.startsWith("android.") || cn.startsWith("com.android")) continue;
                                    where = cn + "." + e.getMethodName() + ":" + e.getLineNumber();
                                    break;
                                }
                                T("HOOKPROBE " + mm.getDeclaringClass().getName() + "." + mm.getName() + "  <- " + where);
                            } catch (Throwable t) {}
                        }
                    });
                    n++;
                } catch (Throwable t) { T("hookprobe attach fail " + t); }
            }
            T("hookprobe armed x" + n);
        } catch (Throwable t) { T("hookprobe err " + t); }
    }

    // ================= 朋友圈「删除对象」层探针/拦截 =================
    // 微X 的 snsDelClass：8.0.49 = com.tencent.mm.plugin.sns.model.m2（L/M(SnsObject)）
    //                    8.0.80 = com.tencent.mm.plugin.sns.model.o2 / r2（H/I(SnsObject)）
    // 这里按签名自动定位：plugin/sns 包下、参数是 protobuf.SnsObject、返回 void 的方法。
    static final java.io.File SNS_BLOCK = new java.io.File(WXDIR + "sns.block.on");

    static void dexScanSnsObj(byte[] b, java.util.Set<String> out) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            if (!"V".equals(dexType(b, rd4(b, po + 4), typeOff, strOff, tc))) continue;
            int pl = rd4(b, po + 8);
            if (pl == 0 || rd4(b, pl) != 1) continue;
            String p0 = dexType(b, rd2(b, pl + 4), typeOff, strOff, tc);
            if (!p0.endsWith("protobuf/SnsObject;") && !p0.endsWith("protobuf/TimeLineObject;")) continue;
            String cn = dexType(b, cls, typeOff, strOff, tc);
            if (!cn.startsWith("Lcom/tencent/mm/plugin/sns/")) continue;
            out.add(cn);
        }
    }

    // ===== 时间线列表探针：找出「拿着整条时间线列表」的方法 =====
    static final java.io.File LISTPROBE = new java.io.File(WXDIR + "listprobe.on");
    static final java.io.File SNS_KEEP_INJECT = new java.io.File(WXDIR + "sns.keep.inject.on");
    static final java.io.File SNS_KEEP_OFF = new java.io.File(WXDIR + "sns.keep.off");
    static final java.io.File SNS_ARCHIVE = new java.io.File(WXDIR + "sns_archive");
    static final java.util.HashSet<String> SNS_SEEN = new java.util.HashSet<String>();
    static final java.util.HashSet<String> SNS_CUR = new java.util.HashSet<String>();
    static final java.util.HashSet<String> SNS_PREV = new java.util.HashSet<String>();
    static final java.util.HashSet<String> SNS_DELETED = new java.util.HashSet<String>();
    static volatile long SNS_LAST_TL = 0;
    static void hookUiListProbes(ClassLoader cl) {
        if (!LISTPROBE.exists()) return;
        java.util.LinkedHashSet<String> descs = new java.util.LinkedHashSet<String>();
        String apk = apkPath();
        if (apk != null) {
            java.util.zip.ZipFile zf = null;
            try {
                zf = new java.util.zip.ZipFile(apk);
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    String nm = e.getName();
                    if (!nm.startsWith("classes") || !nm.endsWith(".dex")) continue;
                    try { dexScanUiList(readAll(zf.getInputStream(e)), descs); } catch (Throwable t) {}
                }
            } catch (Throwable t) {}
            finally { if (zf != null) { try { zf.close(); } catch (Throwable t) {} } }
        }
        int n = 0;
        for (String d : descs) {
            String cn = d.substring(1, d.length() - 1).replace('/', '.');
            Class<?> k;
            try { k = Class.forName(cn, false, cl); } catch (Throwable t) { continue; }
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1 || m.getReturnType() != void.class) continue;
                if (!java.util.Collection.class.isAssignableFrom(ps[0])) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                Object a = p.args[0];
                                int sz = a instanceof java.util.Collection ? ((java.util.Collection<?>) a).size() : -1;
                                if (sz < 0) return;
                                T("UILIST " + p.method.getDeclaringClass().getSimpleName() + "." + p.method.getName() + " n=" + sz);
                            } catch (Throwable t) {}
                        }
                    });
                    n++;
                } catch (Throwable t) {}
            }
        }
        T("uilist hooks: " + n);
    }

    static void dexScanUiList(byte[] b, java.util.Set<String> out) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            if (!"V".equals(dexType(b, rd4(b, po + 4), typeOff, strOff, tc))) continue;
            int pl = rd4(b, po + 8);
            if (pl == 0 || rd4(b, pl) != 1) continue;
            String p0 = dexType(b, rd2(b, pl + 4), typeOff, strOff, tc);
            if (!p0.equals("Ljava/util/List;") && !p0.equals("Ljava/util/LinkedList;")
                    && !p0.equals("Ljava/util/ArrayList;") && !p0.equals("Ljava/util/Collection;")) continue;
            String cn = dexType(b, cls, typeOff, strOff, tc);
            if (!cn.startsWith("Lcom/tencent/mm/plugin/sns/ui/")) continue;
            out.add(cn);
        }
    }

    static final String[] LIST_CLASSES = {
            "com.tencent.mm.plugin.sns.model.x",
            "com.tencent.mm.plugin.sns.model.l5",
            "com.tencent.mm.plugin.sns.model.c4",
            "com.tencent.mm.plugin.sns.model.a8",
            "com.tencent.mm.plugin.sns.model.u",
            "com.tencent.mm.plugin.sns.model.t6",
            "com.tencent.mm.plugin.sns.model.u6",
            "com.tencent.mm.plugin.sns.storage.d2",
            "com.tencent.mm.plugin.sns.storage.i2",
            "com.tencent.mm.plugin.sns.storage.a2"
    };

    static String snsId(Object o) {
        if (o == null) return null;
        try { Object v = XposedHelpers.getObjectField(o, "Id"); if (v != null) return String.valueOf(v); } catch (Throwable t) {}
        try { Object v = XposedHelpers.callMethod(o, "getId"); if (v != null) return String.valueOf(v); } catch (Throwable t) {}
        return null;
    }

    static void archiveSns(Object o, String id) {
        java.io.File f = new java.io.File(SNS_ARCHIVE, id + ".bin");
        if (f.exists() && f.length() > 0) return;
        try {
            byte[] b = (byte[]) XposedHelpers.callMethod(o, "toByteArray");
            if (b == null || b.length == 0 || b.length > 2 * 1024 * 1024) return;
            if (!SNS_ARCHIVE.exists()) SNS_ARCHIVE.mkdirs();
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(b);
            fo.close();
            if (SNS_SEEN.add(id) && SNS_SEEN.size() <= 30) T("SNS-ARCH " + id + " " + b.length + "B");
        } catch (Throwable t) { T("SNS-ARCH err " + t); }
    }

    static int pInt(Object o, String name) {
        try { return o.getClass().getField(name).getInt(o); } catch (Throwable t) { return 0; }
    }

    static void pSetInt(Object o, String name, int v) {
        try { o.getClass().getField(name).setInt(o, v); } catch (Throwable t) {}
    }

    static void killDeleteFlag(Object o, String id) {
        try {
            int df = pInt(o, "DeleteFlag");
            if (df != 0) {
                pSetInt(o, "DeleteFlag", 0);
                T("SNS-KEEP id=" + id + " DeleteFlag " + df + " -> 0");
            }
        } catch (Throwable t) {}
        // 内层 TimeLineObject（如果存在同名标记）
        try {
            Object tl = XposedHelpers.getObjectField(o, "ObjectDesc");
            if (tl != null) {
                int df2 = pInt(tl, "DeleteFlag");
                if (df2 != 0) {
                    pSetInt(tl, "DeleteFlag", 0);
                    T("SNS-KEEP id=" + id + " ObjectDesc.DeleteFlag " + df2 + " -> 0");
                }
            }
        } catch (Throwable t) {}
    }

    static void archiveSnsFromTimeLine(Object timeLine, String id) {
        java.io.File f = new java.io.File(SNS_ARCHIVE, id + ".tl.bin");
        if (f.exists() && f.length() > 0) return;
        try {
            byte[] b = (byte[]) XposedHelpers.callMethod(timeLine, "toByteArray");
            if (b == null || b.length == 0 || b.length > 2 * 1024 * 1024) return;
            if (!SNS_ARCHIVE.exists()) SNS_ARCHIVE.mkdirs();
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(b);
            fo.close();
            if (SNS_SEEN.add(id) && SNS_SEEN.size() <= 40) T("SNS-ARCH-TL " + id + " " + b.length + "B");
        } catch (Throwable t) { T("SNS-ARCH-TL err " + t); }
    }

    static Object loadSns(java.io.File f) {
        try {
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
            in.close();
            Class<?> k = Class.forName("com.tencent.mm.protocal.protobuf.SnsObject", false, APP_CL);
            return XposedHelpers.callStaticMethod(k, "parseFrom", b);
        } catch (Throwable t) { return null; }
    }

    static void startSnsWatchdog() {
        if (SNS_KEEP_OFF.exists()) return;   // 保活/注入整套已关，别再打 SNS-GONE 日志
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2000);
                        if (SNS_LAST_TL == 0) continue;
                        if (System.currentTimeMillis() - SNS_LAST_TL < 4000) continue;
                        java.util.HashSet<String> cur;
                        synchronized (SNS_CUR) {
                            if (SNS_CUR.isEmpty()) continue;
                            cur = new java.util.HashSet<String>(SNS_CUR);
                            SNS_CUR.clear();
                        }
                        synchronized (SNS_PREV) {
                            if (!SNS_PREV.isEmpty()) {
                                java.util.HashSet<String> gone = new java.util.HashSet<String>(SNS_PREV);
                                gone.removeAll(cur);
                                if (!gone.isEmpty()) {
                                    synchronized (SNS_DELETED) { SNS_DELETED.addAll(gone); }
                                    T("SNS-GONE " + gone.size() + " 条（已被删/不再下发）");
                                }
                            }
                            SNS_PREV.clear();
                            SNS_PREV.addAll(cur);
                        }
                    } catch (Throwable t) {}
                }
            }
        }).start();
    }

    static void hookListProbes(ClassLoader cl) {
        if (SNS_KEEP_OFF.exists() && !LISTPROBE.exists() && !SNS_KEEP_INJECT.exists()) return;
        int n = 0;
        for (String cn : LIST_CLASSES) {
            Class<?> k;
            try { k = XposedHelpers.findClass(cn, cl); } catch (Throwable t) { continue; }
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                boolean has = false;
                for (Class<?> c : ps) if (java.util.List.class.isAssignableFrom(c) || java.util.Collection.class.isAssignableFrom(c)) has = true;
                if (!has) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                StringBuilder sb = new StringBuilder();
                                String first = "";
                                for (Object a : p.args) {
                                    if (a instanceof java.util.Collection) {
                                        java.util.Collection<?> cc = (java.util.Collection<?>) a;
                                        sb.append(" n=").append(cc.size());
                                        if (first.length() == 0 && !cc.isEmpty()) {
                                            Object e0 = cc.iterator().next();
                                            first = " first=" + (e0 == null ? "null" : e0.getClass().getName());
                                        }
                                    }
                                }
                                if (LISTPROBE.exists()) T("LISTPROBE " + p.method.getDeclaringClass().getSimpleName() + "." + p.method.getName() + sb + first);
                                // ---- 朋友圈归档 + 缺失补回 ----
                                try {
                                    Object lst = null;
                                    for (Object a : p.args) if (a instanceof java.util.LinkedList) { lst = a; break; }
                                    if (lst == null) return;
                                    java.util.LinkedList ll = (java.util.LinkedList) lst;
                                    java.util.HashSet<String> batchIds = new java.util.HashSet<String>();
                                    for (Object o : ll) {
                                        String id = snsId(o);
                                        if (id == null) continue;
                                        batchIds.add(id);
                                        archiveSns(o, id);
                                        if (!SNS_KEEP_OFF.exists()) killDeleteFlag(o, id);
                                    }
                                    if (!SNS_KEEP_INJECT.exists()) return;
                                    int added = 0;
                                    java.util.ArrayList<String> todo;
                                    synchronized (SNS_DELETED) {
                                        todo = new java.util.ArrayList<String>(SNS_DELETED);
                                        SNS_DELETED.clear();
                                    }
                                    for (String id : todo) {
                                        if (batchIds.contains(id)) continue;
                                        Object obj = loadSns(new java.io.File(SNS_ARCHIVE, id + ".bin"));
                                        if (obj == null) { T("SNS-INJECT skip(no SnsObject archive) " + id); continue; }
                                        if (obj == null) continue;
                                        ll.add(obj);
                                        added++;
                                    }
                                    if (added > 0) T("SNS-INJECT added=" + added + " pending=" + todo.size());
                                } catch (Throwable t) { T("SNS-KEEP err " + t); }
                            } catch (Throwable t) {}
                        }
                    });
                    n++;
                } catch (Throwable t) {}
            }
        }
        T("listprobe hooks: " + n);
    }


    // ============ 朋友圈 DB 层（SnsMicroMsg.db / SnsInfoStorage）============
    // 8.0.49 的 SnsInfoStorage = com.tencent.mm.plugin.sns.storage.d2，父类 z74.l0/z74.q0，
    // 不是 o94.z —— 所以旧的 DELPROBE（钩 o94.z 家族）**永远看不到朋友圈的删除/更新**。
    static final java.io.File SNS_DBPROBE = new java.io.File(WXDIR + "sns.dbprobe.on");
    static final java.io.File SNS_KEEP_DEL = new java.io.File(WXDIR + "sns.keep.del.on");
    static final java.io.File SNS_DB_OFF  = new java.io.File(WXDIR + "sns.db.off");
    static volatile boolean F_SNSDB = false;
    static volatile long F_SNSDB_AT = 0;
    static volatile boolean F_KDEL = false;
    static volatile long F_KDEL_AT = 0;
    static volatile int SNSDB_LOG = 0;
    static boolean VB() { return new java.io.File(WXDIR + "verbose.on").exists(); }
    static final java.util.HashSet<Long> MY_SNS = new java.util.HashSet<Long>();
    static final String SNS_DEL_MARK = "\uff08\u8fd9\u6761\u670b\u53cb\u5708\u88ab\u5220\u9664\u5566\uff09";  // （这条朋友圈被删除啦）
    static final String SNS_DEL_MARK_OLD = "\u3010\u539f\u52a8\u6001\u5df2\u88ab\u4f5c\u8005\u5220\u9664\u3011";  // 旧文案，遇到就替换掉  // 自己发的 snsId（删自己帖子要放行）

    static boolean snsDbProbeOn() {
        long now = System.currentTimeMillis();
        if (now - F_SNSDB_AT > 2000) { F_SNSDB_AT = now; F_SNSDB = SNS_DBPROBE.exists(); }
        return F_SNSDB;
    }

    static boolean snsKeepDelOn() {
        long now = System.currentTimeMillis();
        if (now - F_KDEL_AT > 2000) { F_KDEL_AT = now; F_KDEL = SNS_KEEP_DEL.exists(); }
        return F_KDEL;
    }

    static String stCaller(int from, int n) {
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < st.length && i < from + n; i++)
                sb.append(st[i].getClassName()).append('.').append(st[i].getMethodName()).append('<');
            return sb.toString();
        } catch (Throwable t) { return "?"; }
    }

    static boolean stHas(String key) {
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 0; i < st.length; i++) {
                String s = st[i].getClassName();
                if (s != null && s.indexOf(key) >= 0) return true;
            }
        } catch (Throwable t) {}
        return false;
    }

    static String snsArg(Object a) {
        try {
            if (a == null) return "null";
            if (a instanceof String) { String s = (String) a; return s.length() > 70 ? s.substring(0, 70) + ".." : s; }
            if (a instanceof byte[]) return "byte[" + ((byte[]) a).length + "]";
            if (a instanceof android.database.Cursor) return "Cursor";
            if (a instanceof java.util.Collection) return a.getClass().getSimpleName() + "(" + ((java.util.Collection<?>) a).size() + ")";
            if (a instanceof Number || a instanceof Boolean) return String.valueOf(a);
            return a.getClass().getSimpleName();
        } catch (Throwable t) { return "?"; }
    }

    static void dexScanSnsStorage(byte[] b, java.util.Set<String> out) {
        int strOff = rd4(b, 0x3C), typeOff = rd4(b, 0x44), protoOff = rd4(b, 0x4C),
                methN = rd4(b, 0x58), methOff = rd4(b, 0x5C);
        java.util.HashMap<Integer, String> tc = new java.util.HashMap<Integer, String>();
        for (int i = 0; i < methN; i++) {
            int cls = rd2(b, methOff + 8 * i);
            int proto = rd2(b, methOff + 8 * i + 2);
            int po = protoOff + 12 * proto;
            if (!"Landroid/database/Cursor;".equals(dexType(b, rd4(b, po + 4), typeOff, strOff, tc))) continue;
            int pl = rd4(b, po + 8);
            if (pl == 0 || rd4(b, pl) < 6) continue;
            String cn = dexType(b, cls, typeOff, strOff, tc);
            if (!cn.startsWith("Lcom/tencent/mm/plugin/sns/storage/")) continue;
            out.add(cn);
        }
    }

    static Class<?> findSnsStorage(ClassLoader cl) {
        for (String cn : new String[]{"com.tencent.mm.plugin.sns.storage.d2", "com.tencent.mm.plugin.sns.storage.k2"}) {
            try {
                Class<?> k = XposedHelpers.findClass(cn, cl);
                for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0] == long.class && m.getReturnType() == boolean.class) return k;
                }
            } catch (Throwable t) {}
        }
        java.util.LinkedHashSet<String> descs = dexIdx().snsStore;
        for (String d : descs) {
            String cn = d.substring(1, d.length() - 1).replace('/', '.');
            try {
                Class<?> k = Class.forName(cn, false, cl);
                for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0] == long.class && m.getReturnType() == boolean.class) {
                        if (VB()) T("snsdb found " + cn);
                        return k;
                    }
                }
            } catch (Throwable t) {}
        }
        return null;
    }


    /** 朋友圈删除自检：文件 sns.deltest 内容 = snsId（十进制，可带负号）。
     *  走微信自己的 SnsInfoStorage.delete(snsId)，验证「拦截」是否真的生效，并回读行是否还在。 */
    static void snsDelTestMaybe() {
        try {
            final java.io.File f = new java.io.File(WXDIR + "sns.deltest");
            if (!f.exists()) return;
            final String txt = readText(f.getAbsolutePath());
            f.renameTo(new java.io.File(WXDIR + "sns.deltest.done"));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(2500);
                        Object st = snsStorage();
                        if (st == null) { T("SNS-DELTEST no storage obj"); return; }
                        String s = txt == null ? "" : txt.trim();
                        long id = Long.parseLong(s);
                        Object info0 = snsInfoOf(st, id);
                        Object r;
                        try {
                            java.lang.reflect.Method m = st.getClass().getDeclaredMethod("delete", long.class);
                            m.setAccessible(true);
                            r = m.invoke(st, Long.valueOf(id));
                        } catch (Throwable t) { T("SNS-DELTEST invoke err " + t); return; }
                        Thread.sleep(600);
                        Object info1 = snsInfoOf(st, id);
                        T("SNS-DELTEST id=" + id + " before=" + (info0 != null) + " delete=" + r + " after=" + (info1 != null)
                                + " keepDel=" + SNS_KEEP_DEL.exists());
                    } catch (Throwable t) { T("SNS-DELTEST err " + t); }
                }
            }).start();
        } catch (Throwable t) { T("SNS-DELTEST outer " + t); }
    }


    /** 兜底：钩存储基类的 delete(table,where,args)，凡是删 SnsInfo 行的都拦。
     *  8.0.49 基类 = z74.l0；8.0.80 基类变了，所以按「存储类 + 它的父类链」自动找。 */
    static void hookSnsSqlDelete(ClassLoader cl) {
        java.util.LinkedHashSet<Class<?>> cands = new java.util.LinkedHashSet<Class<?>>();
        try { cands.add(XposedHelpers.findClass("z74.l0", cl)); } catch (Throwable t) {}
        try {
            Class<?> c = findSnsStorage(cl);
            for (int i = 0; c != null && i < 6; i++) { cands.add(c); c = c.getSuperclass(); }
        } catch (Throwable t) {}
        int n = 0;
        for (Class<?> base : cands) {
            try {
                for (java.lang.reflect.Method m : base.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (!m.getName().equals("delete")) continue;
                    if (!(ps.length == 3 && ps[0] == String.class && ps[1] == String.class && ps[2] == String[].class)) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                String table = (String) p.args[0];
                                String where = String.valueOf(p.args[1]);
                                if (table == null || table.indexOf("SnsInfo") < 0) return;
                                if (!snsKeepDelOn() || SNS_DB_OFF.exists()) return;
                                boolean ui = stHas("SnsCommentDetailUI") || stHas("SnsUploadUI") || stHas("SnsGalleryUI") || stHas("TimelineClickListener");
                                T("SNS-SQLDEL " + table + " where=" + where + (ui ? " allow(UI)" : " BLOCK"));
                                if (!ui && retIs(p, int.class)) p.setResult(Integer.valueOf(0));
                            } catch (Throwable t) {}
                        }
                    });
                    n++;
                }
            } catch (Throwable t) { T("sns sqldel hook fail " + base.getName() + " " + t); }
        }
        T("sns sqldel hooks " + n);
    }


    // ===== 版本无关的 SnsInfoStorage 访问（8.0.49 = d2 / 8.0.80 = f2）=====
    static volatile Object SNS_STORE = null;

    static Object snsStorage() {
        if (SNS_STORE != null) return SNS_STORE;
        try {
            Object o = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.tencent.mm.plugin.sns.model.f4", APP_CL), "z8");
            if (o != null) { SNS_STORE = o; return o; }
        } catch (Throwable t) {}
        return null;
    }

    static Object snsInfoOf(Object st, long id) {
        try {
            for (java.lang.reflect.Method m : st.getClass().getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 1 && ps[0] == long.class
                        && m.getReturnType().getName().endsWith("SnsInfo")) {
                    m.setAccessible(true);
                    return m.invoke(st, Long.valueOf(id));
                }
            }
        } catch (Throwable t) { T("snstr getInfo err " + t); }
        return null;
    }

    static Object snsSave(Object st, long id, Object info) {
        try {
            for (java.lang.reflect.Method m : st.getClass().getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 2 && ps[0] == long.class && ps[1].isInstance(info)
                        && m.getReturnType() == boolean.class) {
                    m.setAccessible(true);
                    return m.invoke(st, Long.valueOf(id), info);
                }
            }
        } catch (Throwable t) { T("snstr save err " + t); }
        return "no-save";
    }

    /** 在帖子正文前插【（这条朋友圈被删除啦）】标记（已插过就不重复），返回 是否新插/已有/失败。 */
    static String snsMarkContent(Object info) {
        try {
            Object tl = XposedHelpers.callMethod(info, "getTimeLine");
            if (tl == null) return "no-timeline";
            String cd = null;
            try {
                Object o = XposedHelpers.getObjectField(tl, "ContentDesc");
                cd = (o == null ? null : String.valueOf(o));
            } catch (Throwable t) {
                T("SNS-MARK getField ContentDesc fail " + t);
                return "no-field";
            }
            if (cd != null && cd.indexOf(SNS_DEL_MARK_OLD) >= 0) cd = cd.replace(SNS_DEL_MARK_OLD, "");
            if (cd != null && cd.indexOf(SNS_DEL_MARK) >= 0) return "already";
            String nv = SNS_DEL_MARK + (cd == null ? "" : cd);
            java.lang.reflect.Field fd = tl.getClass().getField("ContentDesc");
            fd.set(tl, nv);
            try { XposedHelpers.callMethod(info, "setTimeLine", tl); } catch (Throwable t) {}
            return "ok";
        } catch (Throwable t) { return "err:" + t; }
    }

    /** 自检：sns.restore 内容 = snsId，把 sourceType 的 bit2 加回去并存库（验证「复活」）。 */
    static void snsRestoreMaybe() {
        try {
            final java.io.File f = new java.io.File(WXDIR + "sns.restore");
            if (!f.exists()) return;
            final String t = readText(f.getAbsolutePath());
            f.renameTo(new java.io.File(WXDIR + "sns.restore.done"));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(2500);
                        Object st = snsStorage();
                        if (st == null) { T("SNS-RESTORE no storage"); return; }
                        long id = Long.parseLong(t.trim());
                        Object info = snsInfoOf(st, id);
                        if (info == null) { T("SNS-RESTORE no row " + id); return; }
                        int before = ((Integer) XposedHelpers.callMethod(info, "getSourceType")).intValue();
                        XposedHelpers.callMethod(info, "addSourceFlag", Integer.valueOf(2));
                        int after = ((Integer) XposedHelpers.callMethod(info, "getSourceType")).intValue();
                        Object r = snsSave(st, id, info);
                        T("SNS-RESTORE id=" + id + " src " + before + " -> " + after + " save=" + r);
                    } catch (Throwable tt) { T("SNS-RESTORE err " + tt); }
                }
            }).start();
        } catch (Throwable t) { T("SNS-RESTORE outer " + t); }
    }

    /** 自检：sns.rmflag 内容 = snsId，直接对该行调 removeSourceFlag(2)，看钩子有没有拦住。 */
    static void snsRmFlagTestMaybe() {
        try {
            final java.io.File f = new java.io.File(WXDIR + "sns.rmflag");
            if (!f.exists()) return;
            final String t = readText(f.getAbsolutePath());
            f.renameTo(new java.io.File(WXDIR + "sns.rmflag.done"));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(2500);
                        Object st = snsStorage();
                        if (st == null) { T("SNS-RMFLAG no storage"); return; }
                        long id = Long.parseLong(t.trim());
                        Object info = snsInfoOf(st, id);
                        if (info == null) { T("SNS-RMFLAG no row " + id); return; }
                        int before = ((Integer) XposedHelpers.callMethod(info, "getSourceType")).intValue();
                        XposedHelpers.callMethod(info, "removeSourceFlag", Integer.valueOf(2));
                        int after = ((Integer) XposedHelpers.callMethod(info, "getSourceType")).intValue();
                        T("SNS-RMFLAG id=" + id + " src " + before + " -> " + after + (after == before ? " BLOCKED" : " REMOVED"));
                    } catch (Throwable tt) { T("SNS-RMFLAG err " + tt); }
                }
            }).start();
        } catch (Throwable t) { T("SNS-RMFLAG outer " + t); }
    }

    /** 自检：sns.mark 内容 = snsId，给该行正文插标记并存库。 */
    static void snsMarkTestMaybe() {
        try {
            final java.io.File f = new java.io.File(WXDIR + "sns.mark");
            if (!f.exists()) return;
            final String t = readText(f.getAbsolutePath());
            f.renameTo(new java.io.File(WXDIR + "sns.mark.done"));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(2500);
                        Object st = snsStorage();
                        if (st == null) { T("SNS-MARK no storage"); return; }
                        long id = Long.parseLong(t.trim());
                        Object info = snsInfoOf(st, id);
                        if (info == null) { T("SNS-MARK no row " + id); return; }
                        String r = snsMarkContent(info);
                        Object saved = snsSave(st, id, info);
                        T("SNS-MARK id=" + id + " mark=" + r + " save=" + saved);
                    } catch (Throwable tt) { T("SNS-MARK err " + tt); }
                }
            }).start();
        } catch (Throwable t) { T("SNS-MARK outer " + t); }
    }


    // ============ 朋友圈「消失」的真因：sourceType 的 bit2（时间线来源位）被清 ============
    // 朋友删帖 → 行不删，只把 SnsInfo.sourceType 的 bit1(值 2) 清掉 → 时间线查询
    //   select *,rowid from SnsInfo where (sourceType & 2 != 0)
    // 查不到了。钩 SnsInfo.removeSourceFlag 拦掉清 bit2，并在正文前加「（这条朋友圈被删除啦）」。
    static void hookSnsSourceFlag(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("com.tencent.mm.plugin.sns.storage.SnsInfo", cl);
            java.lang.reflect.Method m = k.getDeclaredMethod("removeSourceFlag", int.class);
            m.setAccessible(true);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (!snsKeepDelOn() || SNS_DB_OFF.exists()) return;
                        Object self = p.thisObject;
                        int flag = ((Integer) p.args[0]).intValue();
                        if ((flag & 2) == 0) return;
                        int cur = 0;
                        try { cur = ((Integer) XposedHelpers.callMethod(self, "getSourceType")).intValue(); } catch (Throwable t) {}
                        if ((cur & 128) != 0) return;   // 广告行不动
                        Object sid = null;
                        try { sid = XposedHelpers.callMethod(self, "getSnsId"); } catch (Throwable t) {}
                        int cur2 = cur;
                        if (retIs(p, void.class)) p.setResult(null);
                        String mark = snsMarkContent(self);
                        T("SNS-KEEPSRC removeSourceFlag(" + flag + ") BLOCKED id=" + sid + " src=" + cur2 + " mark=" + mark + " <- " + stCaller(3, 4));
                    } catch (Throwable t) {}
                }
            });
            T("sns srcflag hook ok");
        } catch (Throwable t) { T("sns srcflag hook fail " + t); }
    }



    /** 自检：dbg_db 文件存在时，用 db0() 查一条会话数，验证 DB 句柄在本版本可用。 */
    static void dbProbeMaybe() {
        try {
            final java.io.File f = new java.io.File(WXDIR + "dbg_db");
            if (!f.exists()) return;
            f.renameTo(new java.io.File(WXDIR + "dbg_db.done"));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Object db = db0();
                        Cursor c = rawQuery(db, "select count(*) from rconversation", null);
                        int n = -1;
                        if (c != null) { if (c.moveToFirst()) n = c.getInt(0); c.close(); }
                        T("DBPROBE db=" + db.getClass().getName() + " conversations=" + n);
                    } catch (Throwable t) { T("DBPROBE err " + t); }
                }
            }).start();
        } catch (Throwable t) { T("DBPROBE outer " + t); }
    }


    /** 新用户首次运行：所有功能默认关闭（只建 .off，不建 .on）。
     *  老用户（目录里已有任何开关文件）不动，只补标记。 */
    static void ensureDefaultCfg() {
        try {
            java.io.File dir = new java.io.File(WXDIR);
            java.io.File mark = new java.io.File(WXDIR + ".cfg_init");
            if (mark.exists()) return;
            dir.mkdirs();
            String[] known = {"addmsg.block.on", "selfrecall.block.on", "sns.keep.del.on", "sns.keep.off",
                    "sns.db.off", "newroom.mute.on", "realname.off", "origphoto.off", "antirevoke.off",
                    "redpacket.on", "verbose.on", "sns.keep.inject.on", "sns.keep.del.on"};
            boolean old = false;
            for (String k : known) {
                if (new java.io.File(WXDIR + k).exists()) { old = true; break; }
            }
            if (!old) {
                String[] off = {"realname.off", "origphoto.off", "antirevoke.off"};
                for (String k : off) {
                    try { new java.io.File(WXDIR + k).createNewFile(); } catch (Throwable t) {}
                }
                String[] on = {"addmsg.block.on", "sns.keep.del.on", "newroom.mute.on", "redpacket.on"};
                for (String k : on) {
                    try { new java.io.File(WXDIR + k).delete(); } catch (Throwable t) {}
                }
            }
            try { mark.createNewFile(); } catch (Throwable t) {}
            T("cfg init old=" + old);
        } catch (Throwable t) { T("cfg init err " + t); }
    }

    static void hookSnsDb(ClassLoader cl) {
        Class<?> k = findSnsStorage(cl);
        if (k == null) { T("snsdb: SnsInfoStorage not found"); return; }
        final String shortName = k.getSimpleName();
        int n = 0, del = 0;
        for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
            final Class<?>[] ps = m.getParameterTypes();
            final String mn = m.getName();
            final boolean isDelSig = ps.length == 1 && ps[0] == long.class && m.getReturnType() == boolean.class;
            final boolean isDel = isDelSig && mn.toLowerCase().indexOf("delete") >= 0;
            try { m.setAccessible(true); } catch (Throwable t) {}
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        try {
                            if (SNS_STORE == null && p.thisObject != null) SNS_STORE = p.thisObject;
                            boolean probe = snsDbProbeOn();
                            boolean keep = isDel && snsKeepDelOn() && !SNS_DB_OFF.exists();
                            if (!probe && !keep) return;
                            StringBuilder sb = new StringBuilder(shortName).append('.').append(mn).append('(');
                            for (int i = 0; i < p.args.length; i++) {
                                if (i > 0) sb.append(',');
                                sb.append(snsArg(p.args[i]));
                            }
                            sb.append(')');
                            if (probe && SNSDB_LOG < 300) { SNSDB_LOG++; T("SNSDB " + sb); }
                            if (isDel) {
                                boolean ui = stHas("SnsCommentDetailUI") || stHas("SnsUploadUI") || stHas("SnsGalleryUI") || stHas("TimelineClickListener");
                                if (!ui) {
                                    try {
                                        long lid = ((Long) p.args[0]).longValue();
                                        synchronized (MY_SNS) { if (MY_SNS.contains(Long.valueOf(lid))) ui = true; }
                                    } catch (Throwable t) {}
                                }
                                T("SNS-DEL " + sb + (keep && !ui ? " BLOCK" : " allow") + " <- " + stCaller(3, 5));
                                if (keep && !ui && retIs(p, boolean.class)) p.setResult(Boolean.FALSE);
                            }
                        } catch (Throwable t) {}
                    }
                });
                n++;
                if (isDel) del++;
            } catch (Throwable t) {}
        }
        T("snsdb hooks " + n + " del=" + del + " @ " + k.getName());
    }

    static void hookSnsObjects(ClassLoader cl) {
        java.util.LinkedHashSet<String> descs = dexIdx().snsObj;
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (String d : descs) {
            String cn = d.substring(1, d.length() - 1).replace('/', '.');
            try {
                Class<?> k = Class.forName(cn, false, cl);
                for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length != 1 || m.getReturnType() != void.class) continue;
                    String pn = ps[0].getName();
                    if (!pn.endsWith("SnsObject") && !pn.endsWith("TimeLineObject")) continue;
                    try { m.setAccessible(true); } catch (Throwable t) {}
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                if (SNS_KEEP_OFF.exists() && !PROBE.exists() && !SNS_BLOCK.exists()
                                        && !SNS_KEEP_INJECT.exists() && !LISTPROBE.exists()) return;
                                Object o = p.args[0];
                                String id = "?";
                                try { Object v = XposedHelpers.callMethod(o, "getId"); id = String.valueOf(v); } catch (Throwable t) {}
                                if ("?".equals(id)) { try { id = String.valueOf(XposedHelpers.getObjectField(o, "Id")); } catch (Throwable t) {} }
                                if (p.method.getName().equals("setTimeLine")) {
                                    synchronized (SNS_CUR) { SNS_CUR.add(id); }
                                    try {
                                        Object self = p.thisObject;
                                        Object un = XposedHelpers.callMethod(self, "getUserName");
                                        if (un != null && SELF_WXID != null && SELF_WXID.equals(String.valueOf(un))) {
                                            Object sid = XposedHelpers.callMethod(self, "getSnsId");
                                            if (sid != null) {
                                                long lv = new java.math.BigInteger(String.valueOf(sid)).longValue();
                                                synchronized (MY_SNS) { MY_SNS.add(Long.valueOf(lv)); }
                                            }
                                        }
                                    } catch (Throwable t) {}
                                    SNS_LAST_TL = System.currentTimeMillis();
                                    if (SNS_KEEP_INJECT.exists() || LISTPROBE.exists()) archiveSnsFromTimeLine(o, id);
                                    if (!SNS_KEEP_OFF.exists()) {
                                        try {
                                            int df3 = pInt(o, "DeleteFlag");
                                            if (df3 != 0) { pSetInt(o, "DeleteFlag", 0); T("SNS-KEEP-TL id=" + id + " DeleteFlag " + df3 + " -> 0"); }
                                        } catch (Throwable t) {}
                                    }
                                }
                                String tag = "SNSOBJ " + p.method.getDeclaringClass().getSimpleName() + "." + p.method.getName() + " id=" + id;
                                T(tag);
                                if (SNS_BLOCK.exists() && retIs(p, void.class)) {
                                    p.setResult(null);
                                    T(tag + " BLOCKED");
                                }
                            } catch (Throwable t) { T("SNSOBJ err " + t); }
                        }
                    });
                    n++;
                    sb.append(cn).append('.').append(m.getName()).append(' ');
                }
            } catch (Throwable t) { T("snsobj load err " + cn + " " + t); }
        }
        T("snsobj hooks: " + n + " " + sb);
    }

    /** v107：只有放了 sendmsg.debug 文件才装 —— 打出发送管线里 oa6.a.f/g 的参数，定位 oa6.b 断言 */
    static void hookSendDebug(ClassLoader cl) {
        if (!new java.io.File(WXDIR + "sendmsg.debug").exists()) return;
        try {
            Class<?> c = XposedHelpers.findClass("oa6.a", cl);
            for (final String mn : new String[]{"f", "g"}) {
                try {
                    XposedBridge.hookAllMethods(c, mn, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            try {
                                if (SENDDBG_N.get() > 500) return;
                                StackTraceElement[] st = new Throwable().getStackTrace();
                                StringBuilder fr = new StringBuilder();
                                boolean sendPipe = false;
                                for (int i = 1; i < Math.min(st.length, 8); i++) {
                                    fr.append(st[i].getClassName()).append('.').append(st[i].getMethodName()).append('<');
                                    if (st[i].getClassName() != null && st[i].getClassName().startsWith("s61.")) sendPipe = true;
                                }
                                boolean failed = false;
                                if (p.args != null && p.args.length > 1 && (p.args[1] instanceof Boolean))
                                    failed = !((Boolean) p.args[1]).booleanValue();
                                // v110：只记「断言失败」或「发送管线里」的调用，避免启动期刷屏把关键行冲掉
                                if (!failed && !sendPipe) return;
                                StringBuilder sb = new StringBuilder();
                                if (p.args != null) for (Object a : p.args) sb.append('[').append(shorten(String.valueOf(a))).append(']');
                                SENDDBG_N.incrementAndGet();
                                T("SENDDBG oa6.a." + mn + " fail=" + failed + " send=" + sendPipe + " args=" + sb + " from=" + fr);
                            } catch (Throwable t) {}
                        }
                    });
                } catch (Throwable t) { T("SENDDBG hook " + mn + " fail " + t); }
            }
            T("SENDDBG hooks ok");
        } catch (Throwable t) { T("SENDDBG find fail " + t); }
    }

    static void hookAddMsg(ClassLoader cl) {
        java.util.List<Class<?>> cands = discoverAddMsg(cl);
        if (cands.isEmpty()) {
            for (String cn : new String[]{"w41.u", "tj0.s"}) {
                try { cands.add(XposedHelpers.findClass(cn, cl)); } catch (Throwable t) {}
            }
            T("addmsg fallback " + cands.size());
        }
        int n = 0;
        for (Class<?> k : cands) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                boolean j7 = ps.length == 3 && ps[0] == String.class && java.util.Map.class.isAssignableFrom(ps[1]);
                boolean c = ps.length == 6 && ps[0] == String.class && ps[1] == long.class;
                if (!j7 && !c) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (MODULE_OFF) return;
                            try { dbgSqlMaybe(); } catch (Throwable t) {}
                            try {
                                if (!ADDPROBE.exists()) return;
                                Object addMsg = null;
                                StringBuilder map = new StringBuilder();
                                for (Object a : p.args) {
                                    if (a != null && a.getClass().getName().startsWith("com.tencent.mm.modelbase.") && addMsg == null) addMsg = a;
                                    if (a instanceof java.util.Map) {
                                        java.util.Map<?, ?> mm = (java.util.Map<?, ?>) a;
                                        int i = 0;
                                        for (java.util.Map.Entry<?, ?> e : mm.entrySet()) {
                                            if (i++ >= 12) break;
                                            map.append(' ').append(e.getKey()).append('=').append(shorten(String.valueOf(e.getValue())));
                                        }
                                    }
                                }
                                if (addMsg == null) return;
                                T("ADDPROBE " + p.method.getName() + " type=" + amType(addMsg) + " map:" + map);
                            } catch (Throwable t) { T("addmsg probe err " + t); }
                            // ---- 撤回 sysmsg：识别 + 中和 ----
                            if (p.args[1] instanceof java.util.Map) {
                                java.util.Map mm = (java.util.Map) p.args[1];
                                Object st = mm.get(".sysmsg.$type");
                                String sts = st == null ? "" : String.valueOf(st);
                                if (sts.toLowerCase().contains("revoke")) {
                                    String session = String.valueOf(mm.get(".sysmsg.revokemsg.session"));
                                    String svrid = String.valueOf(mm.get(".sysmsg.revokemsg.newmsgid"));
                                    String repl = String.valueOf(mm.get(".sysmsg.revokemsg.replacemsg"));
                                    T("REVOKE-ADDMSG " + p.method.getName() + " session=" + session + " newmsgid=" + svrid
                                            + " replace=" + shorten(repl));
                                    if (REVOKE_ADDMSG_BLOCK.exists()) {
                                        try {
                                            long mid = -1;
                                            Object db = DB_REF;
                                            if (db != null && svrid != null && svrid.length() > 0 && !"null".equals(svrid)) {
                                                Cursor c = rawQuery(db, "select msgId from message where msgSvrId=?", new String[]{svrid});
                                                try { if (c != null && c.moveToFirst()) mid = c.getLong(0); } finally { if (c != null) c.close(); }
                                            }
                                            T("REVOKE-ADDMSG local msgId=" + mid);
                                            if (mid > 0 && db != null) { try { recallRememberMsg(db, mid); } catch (Throwable t2) {} }
                                            // 自己撤回自己的消息：默认放行（否则消息留在本地、对方却已经收到撤回）
                                            if (mid > 0 && db != null && !SELF_RECALL_BLOCK.exists()) {
                                                Cursor sc = rawQuery(db, "select isSend from message where msgId=?", new String[]{String.valueOf(mid)});
                                                int self = -1;
                                                try { if (sc != null && sc.moveToFirst()) self = sc.getInt(0); } finally { if (sc != null) sc.close(); }
                                                if (self == 1) { T("REVOKE-ADDMSG self recall, let through"); return; }
                                            }
                                            int removed = 0;
                                            try {
                                                java.util.Iterator it = mm.keySet().iterator();
                                                while (it.hasNext()) {
                                                    String k = String.valueOf(it.next());
                                                    if (k != null && k.startsWith(".sysmsg")) { it.remove(); removed++; }
                                                }
                                            } catch (Throwable t2) { T("REVOKE-ADDMSG map not mutable " + t2); }
                                            T("REVOKE-ADDMSG neutralized keys=" + removed + " left=" + mm.size());
                                            if (mid > 0 && db != null) {
                                                android.content.ContentValues cv = new android.content.ContentValues();
                                                cv.put("type", Integer.valueOf(10000));
                                                cv.put("content", repl == null || repl.length() == 0 ? "\"x\" \u64a4\u56de\u4e86\u4e00\u6761\u6d88\u606f" : repl);
                                                boolean ok = insertTipRow(db, String.valueOf(mid), cv);
                                                T("REVOKE-ADDMSG tip inserted=" + ok);
                                            }
                                        } catch (Throwable t2) { T("REVOKE-ADDMSG err " + t2); }
                                    } else {
                                        T("REVOKE-ADDMSG block off (file addmsg.block.on)");
                                    }
                                }
                            }
                        }
                    });
                    n++;
                } catch (Throwable t) { T("addmsg hook fail " + m.getName() + " " + t); }
            }
        }
        HOOK_ADDMSG_N = n;
        T("addmsg hooks: " + n);
    }

    static void hookMessageWrites(ClassLoader cl) {
        final java.io.File AR_OFF = new java.io.File(AR_OFF_PATH);
        java.util.List<Class<?>> impls = discoverDbImpls(cl);
        if (impls.isEmpty()) {
            for (String cn : new String[]{"ai5.b0", "ai5.d0", "t93.l", "o94.y", "wc2.j"}) {
                try { impls.add(XposedHelpers.findClass(cn, cl)); } catch (Throwable t) {}
            }
            T("dw fallback impls " + impls.size());
        }
        int n = 0;
        for (final Class<?> k : impls) {
            try {
                for (java.lang.reflect.Constructor<?> ctor : k.getDeclaredConstructors()) {
                    try { ctor.setAccessible(true); } catch (Throwable t) {}
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam p) {
                            final Object db = p.thisObject;
                            if (db != null) {
                                synchronized (DB_LIST) { if (!DB_LIST.contains(db) && DB_LIST.size() < 32) DB_LIST.add(db); }
                                if (DB_REF == null) DB_REF = db;
                                if (PROBE.exists()) {
                                    final String cn = k.getName();
                                    new Thread(new Runnable() {
                                        public void run() {
                                            try {
                                                Thread.sleep(600);
                                                String path = null;
                                                Cursor c = rawQuery(db, "pragma database_list", null);
                                                if (c != null) {
                                                    try { while (c.moveToNext()) if (c.getString(2) != null) path = c.getString(2); } finally { c.close(); }
                                                }
                                                String key = cn + "|" + path;
                                                synchronized (DB_OPENED) {
                                                    if (!DB_OPENED.contains(key)) { DB_OPENED.add(key); T("DBOPEN " + key); }
                                                }
                                            } catch (Throwable t) {}
                                        }
                                    }).start();
                                }
                            }
                        }
                    });
                }
            } catch (Throwable t) { T("ctor hook fail " + k.getName() + " " + t); }
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getReturnType() != long.class) continue;
                Class<?>[] ips = m.getParameterTypes();
                if (ips.length != 3 || ips[0] != String.class || ips[1] != String.class
                        || !ips[2].getName().equals("android.content.ContentValues")) continue;
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (MODULE_OFF) return;
                            try {
                                if (!"rconversation".equals(String.valueOf(p.args[0]))) return;
                                if (!(p.args[2] instanceof android.content.ContentValues)) return;
                                android.content.ContentValues cv3 = (android.content.ContentValues) p.args[2];
                                if (NETPROBE.exists())
                                    T("DBINS " + String.valueOf(p.args[0]) + " " + shorten(String.valueOf(cv3)));
                                String u3 = cv3.getAsString("username");
                                if (u3 != null && BIZ_OFF.contains(u3)) {
                                    cv3.put("parentRef", "officialaccounts");
                                    cv3.put("unReadCount", 0);
                                    T("biz fold insert " + u3);
                                }
                                if (u3 != null) {
                                    String kp = FOLD_KEEP.get(u3);
                                    if (kp != null) { cv3.put("parentRef", kp); T("ma keep fold insert " + u3 + " -> " + kp); }
                                }
                            } catch (Throwable t) {}
                        }
                    });
                } catch (Throwable t) { T("biz insert hook fail " + m.getName() + " " + t); }
            }
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getReturnType() != int.class) continue;
                Class<?>[] dps = m.getParameterTypes();
                if (dps.length != 3 || dps[0] != String.class || dps[1] != String.class || dps[2] != String[].class) continue;
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (!NETPROBE.exists()) return;
                            try {
                                String tb = String.valueOf(p.args[0]);
                                T("DBDEL " + tb + " where=" + shorten(String.valueOf(p.args[1]))
                                        + " args=" + java.util.Arrays.toString((Object[]) p.args[2]));
                                if (NETPROBE.exists() && (tb.startsWith("Biz") || "bizinfo".equals(tb))) {
                                    StackTraceElement[] st3 = new Throwable().getStackTrace();
                                    StringBuilder sb3 = new StringBuilder();
                                    for (int si = 0; si < st3.length && si < 10; si++) {
                                        sb3.append('\n').append("    ").append(st3[si].getClassName())
                                                .append('.').append(st3[si].getMethodName())
                                                .append(":").append(st3[si].getLineNumber());
                                    }
                                    T("BIZSTACK " + tb + sb3);
                                }
                                // 139：手动删聊天时把调用栈打出来，找微信自己的「删会话」入口
                                if ("rconversation".equals(tb)) {
                                    StackTraceElement[] st = new Throwable().getStackTrace();
                                    StringBuilder sbk = new StringBuilder();
                                    for (int si = 0; si < st.length && si < 12; si++) {
                                        sbk.append('\n').append("    ").append(st[si].getClassName())
                                                .append('.').append(st[si].getMethodName())
                                                .append(":").append(st[si].getLineNumber());
                                    }
                                    T("DBDEL-STACK" + sbk);
                                }
                            } catch (Throwable t) {}
                        }
                    });
                } catch (Throwable t) { T("dbdel hook fail " + m.getName() + " " + t); }
            }
            // v206：M() 清 parentRef 走的是 execSQL，直接在 SQL 层改写（红点照常刷新，分组不动）
            for (final java.lang.reflect.Method me : k.getDeclaredMethods()) {
                if (!"execSQL".equals(me.getName())) continue;
                Class<?>[] eps = me.getParameterTypes();
                if (eps.length < 1 || eps.length > 2 || eps[0] != String.class) continue;
                if (java.lang.reflect.Modifier.isAbstract(me.getModifiers())) continue;
                try { me.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(me, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (MODULE_OFF) return;
                            try {
                                String sql = String.valueOf(p.args[0]);
                                if (sql == null) return;
                                String low = sql.toLowerCase();
                                if (low.indexOf("rconversation") < 0 || low.indexOf("parentref") < 0) return;
                                Object[] arr = (p.args.length > 1 && p.args[1] instanceof Object[])
                                        ? (Object[]) p.args[1] : null;
                                if (READ_PROBE.exists()) T("READSQL " + shorten(sql));
                                String fixed = rewriteParentRef(sql, arr);
                                if (fixed != null && !fixed.equals(sql)) {
                                    p.args[0] = fixed;
                                    T("READSQL keep fold " + shorten(fixed));
                                }
                            } catch (Throwable t) {}
                        }
                    });
                } catch (Throwable t) { T("execsql hook fail " + me.getName() + " " + t); }
            }
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getReturnType() != int.class) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 4) continue;
                if (ps[0] != String.class || ps[2] != String.class || ps[3] != String[].class) continue;
                if (!ps[1].getName().equals("android.content.ContentValues")) continue;
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                try { m.setAccessible(true); } catch (Throwable t) {}
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                            if (MODULE_OFF) return;
                            try {
                                DB_REF = p.thisObject;
                                maybeRunRevokeTest();
                                String table = String.valueOf(p.args[0]);
                                if (PROBE.exists() && table != null && table.toLowerCase().contains("sns")) {
                                    String w = p.args.length > 2 ? String.valueOf(p.args[2]) : "";
                                    T("UPD-SNS db=" + p.thisObject.getClass().getName() + " table=" + table
                                            + " where=" + shorten(w) + " cv=" + shorten(String.valueOf(p.args[1])));
                                }
                                if (NETPROBE.exists() && !"message".equals(table)) {
                                    String wh = p.args.length > 2 ? String.valueOf(p.args[2]) : "";
                                    String wa2 = "";
                                    try { if (p.args.length > 3 && p.args[3] != null) wa2 = java.util.Arrays.toString((Object[]) p.args[3]); } catch (Throwable t) {}
                                    T("DBUPD " + table + " where=" + shorten(wh) + " args=" + wa2
                                            + " cv=" + shorten(String.valueOf(p.args[1])));
                                }
                                if ("rcontact".equals(table) && p.args[1] instanceof android.content.ContentValues) {
                                    try {
                                        Object eu = ((android.content.ContentValues) p.args[1]).get("encryptUsername");
                                        if (eu instanceof String && ((String) eu).startsWith("fake_") && NETPROBE.exists()) {
                                            StackTraceElement[] st2 = new Throwable().getStackTrace();
                                            StringBuilder sb2 = new StringBuilder();
                                            for (int si = 0; si < st2.length && si < 12; si++) {
                                                sb2.append('\n').append("    ").append(st2[si].getClassName())
                                                        .append('.').append(st2[si].getMethodName())
                                                        .append(":").append(st2[si].getLineNumber());
                                            }
                                            T("FAKESTACK" + sb2);
                                        }
                                    } catch (Throwable t) {}
                                }
                                if ("rconversation".equals(table)) {   // v123：取关的公众号会话强制留在订阅号分组
                                    try {
                                        if (p.args[1] instanceof android.content.ContentValues) {
                                            android.content.ContentValues cv2 = (android.content.ContentValues) p.args[1];
                                            String u = cv2.getAsString("username");
                                            if (u == null && p.args.length > 3 && p.args[3] instanceof Object[]) {
                                                Object[] wa2 = (Object[]) p.args[3];
                                                if (wa2.length > 0 && wa2[0] != null) u = String.valueOf(wa2[0]);
                                            }
                                            if (u != null && BIZ_OFF.contains(u)) {
                                                cv2.put("parentRef", "officialaccounts");
                                                cv2.put("unReadCount", 0);
                                            }
                                            if (u != null) {
                                                String kp = FOLD_KEEP.get(u);
                                                if (kp != null) { cv2.put("parentRef", kp); T("ma keep fold " + u + " -> " + kp); }
                                            }
                                        }
                                    } catch (Throwable t) {}
                                    return;
                                }
                                if (table == null || !"message".equals(table)) return;   // 只认消息表，别误伤 SNS/其它表
                                if (!(p.args[1] instanceof android.content.ContentValues)) return;
                                android.content.ContentValues cv = (android.content.ContentValues) p.args[1];
                                Object tv = cv.get("type");
                                if (PROBE.exists()) {
                                    String where = p.args.length > 2 ? String.valueOf(p.args[2]) : "";
                                    String wa = "";
                                    try {
                                        if (p.args.length > 3 && p.args[3] != null)
                                            wa = java.util.Arrays.toString((Object[]) p.args[3]);
                                    } catch (Throwable t) {}
                                    T("SQL[upd] " + table + " type=" + tv + " where=" + where
                                            + " args=" + wa + " content=" + shorten(cv.getAsString("content")));
                                }
                                if (!(tv instanceof Integer)) return;
                                int type = ((Integer) tv).intValue();
                                int low = type & 0xFFFF;

                                if (low == 10000) {
                                    String cc = cv.getAsString("content");
                                    String neu = tweakTip(cc, cv.getAsLong("createTime"));
                                    if (neu != null) cv.put("content", neu);
                                }

                                String mid = null;
                                if (p.args.length > 3 && p.args[3] instanceof Object[]) {
                                    Object[] wa = (Object[]) p.args[3];
                                    if (wa.length > 0 && wa[0] != null) mid = String.valueOf(wa[0]);
                                }

                                String why = null;
                                boolean selfRow = false;
                                if (low == 10002) {
                                    // v103：这里以前一律拦截，导致「自己撤回自己的消息」被拦在 DB 层，
                                    // 而微信客户端那边已经按撤回处理了 —— 之后点「重新编辑」再发送，
                                    // 发送管线（Pipeline_SendMsgMgr_Processor）拿到一条状态矛盾的行，直接崩（oa6.b）。
                                    try {
                                        if (mid != null && p.thisObject != null) {
                                            Cursor sc = rawQuery(p.thisObject,
                                                    "select isSend from message where msgId=?", new String[]{mid});
                                            try { if (sc != null && sc.moveToFirst()) selfRow = sc.getInt(0) == 1; }
                                            finally { if (sc != null) sc.close(); }
                                        }
                                    } catch (Throwable t2) {}
                                    if (selfRow) {
                                        // v113：自己的撤回就当普通撤回处理，不碰 message 表（一写就崩）。
                                        // 只有显式开了 selfrecall.keep.on 才把原文记到 recall.txt 备查。
                                        if (SELF_RECALL_KEEP.exists() && mid != null && p.thisObject != null) {
                                            logSelfRecallText(p.thisObject, mid);
                                        }
                                        return;
                                    }
                                    why = selfRow ? "self-block" : "peer";
                                } else if (low == 10000) {
                                    Object cvo = cv.get("content");
                                    if (cvo instanceof String) {
                                        String c = (String) cvo;
                                        if (c.contains("\u64a4\u56de") || c.toLowerCase().contains("recall"))
                                            why = "peer";
                                    }
                                }
                                if (why == null) return;
                                if (AR_OFF.exists()) return;
                                // v151（S10）：DB 层也要在改行之前把 imgPath 记下来
                                if (mid != null && p.thisObject != null) {
                                    try { recallRememberMsg(p.thisObject, Long.parseLong(mid)); } catch (Throwable t2) {}
                                }
                                // 自己撤回且显式要求拦截：消息留在原地就好，不插提示行
                                // （插一条 isSend=0 的撤回行会让会话状态自相矛盾）
                                if ("self-block".equals(why)) {
                                    if (retIs(p, int.class)) p.setResult(Integer.valueOf(0));
                                    T("revoke self blocked (no tip) msgId=" + mid);
                                    return;
                                }
                                boolean tipped = !NO_TIP && mid != null && insertTipRow(p.thisObject, mid, cv);
                                if (retIs(p, int.class)) p.setResult(Integer.valueOf(0));
                                if (tipped) {
                                    LAST_REVOKE_AT = System.currentTimeMillis();
                                    T("revoke blocked msgId=" + mid);
                                } else {
                                    T("revoke blocked (no tip) msgId=" + mid);
                                }
                            } catch (Throwable t) { T("msg write err " + t); }
                        }
                    });
                    WRITE_METHODS.add(m);
                    n++;
                } catch (Throwable t) { T("hook msg write fail " + k.getName() + "." + m.getName() + " " + t); }
            }
        }
        HOOK_WRITE_N = n;
        T("msg write hooks: " + n + " ctor db=" + (DB_REF == null ? "none" : DB_REF.getClass().getName()));
    }

    static void maybeRunRevokeTest() {
        if (TEST_RUNNING || DB_REF == null || WRITE_METHODS.isEmpty()) return;
        if (!REVOKE_TEST.exists()) return;
        runRevokeTest(DB_REF);
    }

    static void startRevokeTestWatcher() {
        if (!REVOKE_TEST.exists()) return;
        T("TEST watcher started");
        new Thread(new Runnable() {
            public void run() {
                Object best = null;
                long bestCount = 0;
                for (int round = 0; round < 60; round++) {
                    java.util.ArrayList<Object> snap;
                    synchronized (DB_LIST) { snap = new java.util.ArrayList<Object>(DB_LIST); }
                    for (Object o : snap) {
                        try {
                            String path = null;
                            Cursor pc = rawQuery(o, "pragma database_list", null);
                            if (pc != null) {
                                try {
                                    while (pc.moveToNext()) if (pc.getString(2) != null) path = pc.getString(2);
                                } finally { pc.close(); }
                            }
                            Cursor c = rawQuery(o, "select count(*) from message", null);
                            long cnt = -1;
                            if (c != null) { try { if (c.moveToFirst()) cnt = c.getLong(0); } finally { c.close(); } }
                            T("TEST db " + o.getClass().getName() + " file=" + path + " msg=" + cnt);
                            if (cnt > bestCount) { bestCount = cnt; best = o; }
                        } catch (Throwable t) { T("TEST probe err " + o.getClass().getName() + " " + t); }
                    }
                    if (best != null) break;
                    try { Thread.sleep(500); } catch (Throwable t) {}
                }
                if (best == null) { T("TEST no message db found"); return; }
                T("TEST picked " + best.getClass().getName() + " rows=" + bestCount);
                runRevokeTest(best);
            }
        }).start();
    }

    static void runRevokeTest(final Object db) {
        if (TEST_RUNNING) return;
        TEST_RUNNING = true;
        new Thread(new Runnable() {
            public void run() {
                long maxId = 0;
                try {
                    Thread.sleep(300);
                    Cursor c0 = rawQuery(db, "select max(msgId) from message", null);
                    try { if (c0 != null && c0.moveToFirst()) maxId = c0.getLong(0); } finally { if (c0 != null) c0.close(); }
                    String mid = null;
                    int oldType = 0;
                    String sel = REVOKE_SELF_TEST.exists()
                            ? "select msgId, type from message where isSend=1 and (type & 65535) = 1 order by msgId desc limit 1"
                            : "select msgId, type from message where (type & 65535) = 1 order by msgId desc limit 1";
                    Cursor c = rawQuery(db, sel, null);
                    try {
                        if (c != null && c.moveToFirst()) { mid = String.valueOf(c.getLong(0)); oldType = c.getInt(1); }
                    } finally { if (c != null) c.close(); }
                    T("TEST target msgId=" + mid + " type=" + oldType + " maxId=" + maxId);
                    if (mid == null) { T("TEST no text row"); return; }
                    NO_TIP = true;
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put("type", Integer.valueOf(0x11000000 | 10002));
                    cv.put("content", "1");
                    java.lang.reflect.Method wm = null;
                    for (java.lang.reflect.Method x : WRITE_METHODS) {
                        if (x.getDeclaringClass().isAssignableFrom(db.getClass())) { wm = x; break; }
                    }
                    if (wm == null) { T("TEST no matching write method for " + db.getClass().getName()); return; }
                    T("TEST using " + wm.getDeclaringClass().getName() + "." + wm.getName());
                    Object r = wm.invoke(db, "message", cv, "msgId=?", new String[]{mid});
                    int now = -1;
                    String nowContent = "?";
                    Cursor c2 = rawQuery(db, "select type, content from message where msgId=?", new String[]{mid});
                    try {
                        if (c2 != null && c2.moveToFirst()) { now = c2.getInt(0); nowContent = c2.getString(1); }
                    } finally { if (c2 != null) c2.close(); }
                    boolean pass = (now == oldType);
                    T("TEST updateReturn=" + r + " typeNow=" + now + " content=" + shorten(nowContent)
                            + (pass ? " PASS(拦截成功)" : " FAIL(没拦住)"));
                } catch (Throwable t) {
                    T("TEST err " + t);
                } finally {
                    NO_TIP = false;
                    TEST_RUNNING = false;
                    try { REVOKE_TEST.renameTo(new java.io.File(REVOKE_TEST.getPath() + ".done")); } catch (Throwable t) {}
                }
            }
        }).start();
    }

    // ================= 朋友圈防删除（保留别人删掉的朋友圈） =================
    static final java.io.File SNS_OFF = new java.io.File(WXDIR + "sns.keep.off");

    static void hookSnsGuards(final Class<?> k) {
        for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
            Class<?>[] ps = m.getParameterTypes();
            boolean del = m.getReturnType() == int.class && ps.length == 3
                    && ps[0] == String.class && ps[1] == String.class && ps[2] == String[].class;
            boolean exec2 = m.getReturnType() == boolean.class && ps.length == 2
                    && ps[0] == String.class && ps[1] == String.class;
            boolean exec3 = m.getReturnType() == boolean.class && ps.length == 3
                    && ps[0] == String.class && ps[1] == String.class && ps[2] == Object[].class;
            if (!del && !exec2 && !exec3) continue;
            try { m.setAccessible(true); } catch (Throwable t) {}
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        try {
                            if (SNS_OFF.exists()) return;
                            if (p.args.length < 1 || !(p.args[0] instanceof String)) return;
                            String table = null, where = null, sql = null;
                            Object[] wa = null;
                            if (m.getReturnType() == int.class) {
                                table = (String) p.args[0];
                                where = p.args[1] instanceof String ? (String) p.args[1] : null;
                                if (p.args[2] instanceof Object[]) wa = (Object[]) p.args[2];
                            } else {
                                sql = (String) p.args[0];
                                if (p.args.length > 2 && p.args[2] instanceof Object[]) wa = (Object[]) p.args[2];
                            }
                            if (PROBE.exists()) {
                                T("DELPROBE db=" + p.thisObject.getClass().getName()
                                        + " table=" + table + " where=" + shorten(where) + " sql=" + shorten(sql));
                            }
                            boolean isSns = (table != null && (table.equalsIgnoreCase("SnsInfo")
                                        || table.equalsIgnoreCase("snsExtInfo3") || table.equalsIgnoreCase("SnsInfo2")))
                                    || (sql != null && (sql.toLowerCase().contains("snsinfo")
                                        || sql.toLowerCase().contains("snsinfo")));
                            if (!isSns) return;
                            String low = sql == null ? "" : sql.trim().toLowerCase();
                            boolean isDel = (table != null) || low.startsWith("delete");
                            if (!isDel) return;
                            String self = selfWxid();
                            String q = sql != null ? sql
                                    : ("select userName from " + table + (where != null && where.length() > 0 ? " where " + where : ""));
                            int others = 0, rows = 0;
                            Cursor c = rawQuery(p.thisObject, q, (String[]) (wa instanceof String[] ? wa : null));
                            try {
                                int idx = -1;
                                if (c != null) {
                                    String[] cols = c.getColumnNames();
                                    for (int i = 0; i < cols.length; i++) if ("userName".equalsIgnoreCase(cols[i])) idx = i;
                                    while (c.moveToNext()) {
                                        rows++;
                                        if (idx >= 0) {
                                            String u = c.getString(idx);
                                            if (self == null || u == null || !self.equals(u)) others++;
                                        }
                                    }
                                }
                            } finally { if (c != null) c.close(); }
                            String what = sql != null ? shorten(sql) : (table + " | " + where + " | " + java.util.Arrays.toString(wa));
                            if (rows == 0) { T("SNS-KEEP nothing to delete: " + what); return; }
                            if (others == 0 && self != null) { T("SNS-KEEP own post, allow: " + what); return; }
                            Class<?> rt = m.getReturnType();
                            if (rt == int.class) p.setResult(Integer.valueOf(0));
                            else if (rt == boolean.class) p.setResult(Boolean.FALSE);
                            else T("SNS-KEEP skip result type " + rt);
                            T("SNS-KEEP blocked rows=" + rows + " others=" + others + " " + what);
                        } catch (Throwable t) { T("SNS-KEEP err " + t); }
                    }
                });
            } catch (Throwable t) { T("sns guard hook fail " + m.getName() + " " + t); }
        }
    }

    static java.lang.reflect.Method findMethod(Class<?> c, Class<?> ret, Class<?>... ps) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (ret == long.class ? m.getReturnType() != long.class : !ret.isAssignableFrom(m.getReturnType())) continue;
                Class<?>[] mp = m.getParameterTypes();
                if (mp.length != ps.length) continue;
                boolean ok = true;
                for (int i = 0; i < ps.length; i++) if (ps[i] != null && mp[i] != ps[i]) { ok = false; break; }
                if (ok) { try { m.setAccessible(true); } catch (Throwable t) {} return m; }
            }
        }
        return null;
    }

    static Cursor rawQuery(Object db, String sql, String[] args) throws Throwable {
        java.lang.reflect.Method m = findMethod(db.getClass(), Cursor.class, String.class, String[].class);
        if (m == null) { T("rawQuery not found on " + db.getClass().getName()); return null; }
        return (Cursor) m.invoke(db, sql, args);
    }

    static long insertRow(Object db, String table, String nullCol, android.content.ContentValues cv) throws Throwable {
        java.lang.reflect.Method m = findMethod(db.getClass(), long.class, String.class, String.class,
                android.content.ContentValues.class);
        if (m == null) { T("insert not found on " + db.getClass().getName()); return -1; }
        Object r = m.invoke(db, table, nullCol, cv);
        return r instanceof Long ? ((Long) r).longValue() : -1;
    }

    /** 读一整行成 ContentValues（撤回前先存一份，等会儿原行就被改成提示了） */
    static android.content.ContentValues readRowCv(Object db, String mid) {
        Cursor c = null;
        try {
            c = rawQuery(db, "select * from message where msgId=?", new String[]{mid});
            if (c == null || !c.moveToFirst()) return null;
            String[] cols = c.getColumnNames();
            android.content.ContentValues base = new android.content.ContentValues();
            for (int i = 0; i < cols.length; i++) {
                int t = c.getType(i);
                if (t == 0) continue;
                if (t == 4) { base.put(cols[i], c.getBlob(i)); continue; }
                if (t == 1) { base.put(cols[i], Long.valueOf(c.getLong(i))); continue; }
                if (t == 2) { base.put(cols[i], Double.valueOf(c.getDouble(i))); continue; }
                base.put(cols[i], c.getString(i));
            }
            return base;
        } catch (Throwable t) { return null; }
        finally { if (c != null) { try { c.close(); } catch (Throwable t) {} } }
    }

    /** v111：自己撤回的文字记到 side 文件（不碰 message 表，避免发送管线断言崩溃） */
    static final String RECALL_TXT_PATH = WXDIR + "recall.txt";

    static void logSelfRecallText(Object db, String mid) {
        try {
            android.content.ContentValues cv = readRowCv(db, mid);
            if (cv == null) { T("rtext: row not found " + mid); return; }
            Integer ty = cv.getAsInteger("type");
            long low = ty == null ? 0 : (ty.intValue() & 0xFFFF);
            if (low != 1) { T("rtext: not text " + mid + " type=" + ty); return; }
            String content = cv.getAsString("content");
            if (content == null || content.trim().length() == 0) return;
            String talker = cv.getAsString("talker");
            Long ct = cv.getAsLong("createTime");
            String line = (ct == null ? System.currentTimeMillis() : ct.longValue()) + "|" + talker + "|"
                    + content.replace('\n', ' ').replace('|', '/');
            java.io.File f = new java.io.File(RECALL_TXT_PATH);
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(line + "\n");
            w.close();
            // 只留最近 500 条
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
                java.util.ArrayList<String> all = new java.util.ArrayList<String>();
                String ln;
                while ((ln = r.readLine()) != null) all.add(ln);
                r.close();
                if (all.size() > 500) {
                    java.io.FileWriter w2 = new java.io.FileWriter(f, false);
                    for (int i = all.size() - 500; i < all.size(); i++) w2.write(all.get(i) + "\n");
                    w2.close();
                }
            } catch (Throwable t2) {}
            T("rtext ok mid=" + mid + " talker=" + talker + " len=" + content.length());
        } catch (Throwable t) { T("rtext err " + t); }
    }

    static java.util.ArrayList<String> readRecallTexts() {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        try {
            java.io.File f = new java.io.File(RECALL_TXT_PATH);
            if (!f.exists()) return out;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String ln;
            while ((ln = r.readLine()) != null) if (ln.trim().length() > 0) out.add(0, ln);   // 新的在前
            r.close();
        } catch (Throwable t) {}
        return out;
    }

    static void showRecallTexts(final Activity act) {
        final java.util.ArrayList<String> list = readRecallTexts();
        if (list.isEmpty()) { toast(act, "\u8fd8\u6ca1\u6709\u8bb0\u5f55"); return; }
        final String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String[] p = list.get(i).split("\\|", 3);
            String tm = "";
            try {
                tm = new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(Long.parseLong(p[0])));
            } catch (Throwable t) {}
            String who = p.length > 1 ? p[1] : "";
            String txt = p.length > 2 ? p[2] : "";
            if (txt.length() > 40) txt = txt.substring(0, 40) + "\u2026";
            names[i] = tm + "  " + txt;
        }
        new android.app.AlertDialog.Builder(act)
            .setTitle("\u64a4\u56de\u7684\u6587\u5b57\uff08" + list.size() + "\uff09")
            .setItems(names, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String[] p = list.get(w).split("\\|", 3);
                    String txt = p.length > 2 ? p[2] : "";
                    try {
                        Object cm = XposedHelpers.callMethod(act, "getSystemService", "clipboard");
                        XposedHelpers.callMethod(cm, "setText", txt);
                        toast(act, "\u5df2\u590d\u5236");
                    } catch (Throwable t) { toast(act, "\u590d\u5236\u5931\u8d25"); }
                }
            })
            .setNegativeButton("\u5173\u95ed", null)
            .show();
    }

    static boolean insertTipRow(Object db, String mid, Object srcCv) {
        return RecallTipRuntime.insert(db, mid, srcCv);
    }

    static String tweakTip(String c, Long ct) {
        try {
            if (c == null) return null;
            long ts = ct == null ? System.currentTimeMillis() : ct.longValue();
            String hm = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(ts));
            if (c.contains("\u9000\u51fa\u4e86\u6b64\u7fa4") || c.contains("\u9000\u51fa\u4e86\u7fa4\u804a")) {
                refreshFlags();
                if (F_TIPTIME_OFF) return null;
                if (c.endsWith("\uff09") && c.lastIndexOf("\uff08") > c.length() - 8) return null;
                return c + "\uff08" + hm + "\uff09";
            }
            refreshFlags();
            if (F_TIPRENAME_OFF) return null;
            int ai = c.indexOf(" \u6539\u540d\u4e3a ");
            if (ai > 0) {
                String oldN = c.substring(0, ai).trim();
                String rest = c.substring(ai + 5).trim();
                String uid = null;
                String newN = rest;
                int lp = rest.lastIndexOf('('), rp = rest.lastIndexOf(')');
                if (lp > 0 && rp > lp) {
                    uid = rest.substring(lp + 1, rp).trim();
                    newN = rest.substring(0, lp).trim();
                }
                String who = null;
                String self = selfWxid();
                if (uid != null && self != null && uid.equals(self)) who = "\u4f60";
                else if (uid != null) who = displayNameOf(uid);
                if (who == null || who.length() == 0) who = oldN.length() > 0 ? oldN : (uid != null ? uid : "\u6709\u4eba");
                if ("\u6211\u5728\u672c\u7fa4\u7684\u6635\u79f0".equals(who)) who = "\u4f60";
                return who + " \u628a\u7fa4\u6635\u79f0\u6539\u6210\u4e86\u300c" + newN + "\u300d";
            }
        } catch (Throwable t) { T("tweakTip err " + t); }
        return null;
    }

    static String displayNameOf(String uid) {
        try {
            if (uid == null || uid.length() == 0) return null;
            Object db = db0();
            Cursor c = (Cursor) rawQuery(db,
                    "select conRemark, nickname from rcontact where username='" + uid + "'", null);
            try {
                if (c != null && c.moveToFirst()) {
                    String rm = null, nk = null;
                    try { rm = c.getString(0); } catch (Throwable t) {}
                    try { nk = c.getString(1); } catch (Throwable t) {}
                    if (rm != null && rm.trim().length() > 0) return rm.trim();
                    if (nk != null && nk.trim().length() > 0) return nk.trim();
                }
            } finally { if (c != null) c.close(); }
        } catch (Throwable t) {}
        return null;
    }

    static void refreshFlags() {
        long now = System.currentTimeMillis();
        if (now - FLAGS_AT < 5000) return;
        FLAGS_AT = now;
        try { F_TIPTIME_OFF = new java.io.File(WXDIR + "tip.time.off").exists(); } catch (Throwable t) {}
        try { F_TIPRENAME_OFF = new java.io.File(WXDIR + "tip.rename.off").exists(); } catch (Throwable t) {}
    }


    static String localKernelVer() { return BUILD; }

    static int iv(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return 0; }
    }

    /** 刷新菜单顶部那行小字：内核 vX · 服务器 vY（有新版本时提示可点） */
    /** 1.43：顶部小字前缀 —— 模块版本 + 内核版本 */
    static String verPrefix() {
        String mv = callMainStr("myVersionName");
        return "模块 " + (mv == null ? "?" : mv) + " · 内核 v";
    }

    static void refreshVerLine(final Activity act, final android.widget.TextView tv) {
        new Thread(new Runnable() {
            public void run() {
                final String sv = callMainStr("serverVersion");
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        String lv = localKernelVer();
                        if (sv == null) { tv.setText(verPrefix() + lv + " · 服务器 查询失败，点此重试"); return; }
                        if (iv(sv) > iv(lv)) tv.setText(verPrefix() + lv + " → 服务器 v" + sv + "（点此在线升级）");
                        else tv.setText(verPrefix() + lv + " · 服务器 v" + sv + "（已是最新）");
                    }
                });
            }
        }).start();
    }

    /** 点顶部小字：有新版就升级，否则重新查询 */
    static void onVerLineClick(final Activity act, final android.widget.TextView tv) {
        String t = String.valueOf(tv.getText());
        if (t.indexOf("在线升级") >= 0) {
            new android.app.AlertDialog.Builder(act)
                    .setTitle("在线升级内核")
                    .setMessage("将从服务器下载新内核，下载完成后需要重启微信生效。")
                    .setPositiveButton("升级", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) { doOnlineUpgrade(act, tv); }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        tv.setText(verPrefix() + localKernelVer() + " · 服务器 查询中…");
        refreshVerLine(act, tv);
    }

    static void doOnlineUpgrade(final Activity act, final android.widget.TextView tv) {
        toast(act, "正在下载新内核…");
        new Thread(new Runnable() {
            public void run() {
                final String msg = callMainStr("checkUpdateNow");
                T("online upgrade -> " + msg);
                act.runOnUiThread(new Runnable() {
                    public void run() {
                        refreshVerLine(act, tv);
                        new android.app.AlertDialog.Builder(act)
                                .setTitle("在线升级内核")
                                .setMessage(msg == null ? "升级失败" : msg)
                                .setPositiveButton("立即重启", new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(android.content.DialogInterface d, int w) {
                                        toast(act, "正在重启微信…");
                                        new Thread(new Runnable() { public void run() {
                                            try { Thread.sleep(500); } catch (Throwable t) {}
                                            System.exit(0);
                                        } }).start();
                                    }
                                })
                                .setNegativeButton("稍后", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    /** v101：钩子全挂时告诉用户「装新版 APK」，别让人一脸茫然 */
    static void showBrokenDialog(final Activity act) {
        try {
            new android.app.AlertDialog.Builder(act)
                .setTitle("\u6a21\u5757\u6ca1\u80fd\u542f\u52a8")
                .setMessage("\u94a9\u5b50\u4e00\u4e2a\u90fd\u6ca1\u6302\u4e0a\uff08\u591a\u534a\u662f\u5b89\u88c5\u7684\u6a21\u5757 APK \u592a\u65e7\uff09\u3002\n\n"
                        + "\u9632\u64a4\u56de\u3001\u4e00\u952e\u5df2\u8bfb\u3001\u771f\u540d\u3001\u539f\u56fe\u90fd\u9760\u8fd9\u4e9b\u94a9\u5b50\uff0c"
                        + "\u88c5\u4e00\u4e0b\u6700\u65b0\u7248 APK \u5c31\u80fd\u6062\u590d\uff08\u8986\u76d6\u5b89\u88c5\uff0c\u4e0d\u7528\u5148\u5378\u8f7d\uff09\u3002")
                .setPositiveButton("\u53bb\u6253\u5f00\u53d1\u5e03\u9875", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            // 用反射拼 Intent：内核是拿精简 stub 编译的，没有 Uri / ACTION_VIEW 常量
                            Class<?> uriCls = Class.forName("android.net.Uri");
                            Object uri = uriCls.getMethod("parse", String.class).invoke(null,
                                    "https://github.com/vividbeacon43/io.github.vividbeacon43.wxentry/releases/latest");
                            Class<?> intentCls = Class.forName("android.content.Intent");
                            Object it = intentCls.getConstructor(String.class, uriCls)
                                    .newInstance("android.intent.action.VIEW", uri);
                            intentCls.getMethod("addFlags", int.class).invoke(it, Integer.valueOf(0x10000000));
                            act.startActivity((android.content.Intent) it);
                        } catch (Throwable t) { T("open release err " + t); }
                    }
                })
                .setNegativeButton("\u77e5\u9053\u4e86", null)
                .show();
        } catch (Throwable t) { T("broken dialog err " + t); }
    }

    /** 通用的一次性上报（默认 24h 一次） */
    static void diagOnce(String reason, String tsName) {
        try {
            java.io.File ts = new java.io.File(WXDIR + tsName);
            long now = System.currentTimeMillis();
            if (ts.exists() && now - ts.lastModified() < 24L * 3600 * 1000) return;
            ts.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(ts, false);
            w.write(String.valueOf(now));
            w.close();
            T("diag auto " + reason);
            rnUploadDiag(reason);
        } catch (Throwable t) {}
    }

    static final String DONATION_URL = "https://www.zidianidc.com/shop/Z2EWXNI6";

    static void openDonation(final Activity act) {
        if (act == null || act.isFinishing() || act.isDestroyed()) return;
        try {
            android.content.Intent link = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(DONATION_URL));
            link.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
            // Resolve only browser apps while delivering the original VIEW intent and URL.
            android.content.Intent browser = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            browser.addCategory(android.content.Intent.CATEGORY_APP_BROWSER);
            link.setSelector(browser);
            act.startActivity(link);
        } catch (android.content.ActivityNotFoundException missing) {
            copyDonationLink(act, "未找到可用浏览器");
        } catch (Throwable failure) {
            T("donation browser open failed " + failure.getClass().getSimpleName());
            copyDonationLink(act, "浏览器暂不可用");
        }
    }

    static void copyDonationLink(Activity act, String reason) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard unavailable");
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("打赏链接", DONATION_URL));
            toast(act, reason + "，打赏链接已复制");
        } catch (Throwable unavailable) {
            T("donation copy failed " + unavailable.getClass().getSimpleName());
            android.widget.TextView text = new android.widget.TextView(act);
            text.setText(DONATION_URL);text.setTextIsSelectable(true);text.setTextSize(16);
            int padding = (int)(16 * act.getResources().getDisplayMetrics().density);
            text.setPadding(padding,padding,padding,padding);
            new android.app.AlertDialog.Builder(act).setTitle("请长按复制打赏链接").setView(text)
                    .setPositiveButton("关闭", null).show();
        }
    }

    // Each visible row owns its action; positions never determine behavior.
    enum MenuAction {
        MARK_READ, CLEAR_CHATS, UNFOLLOW_BIZ, RP_GROUPS, RP_TOGGLE, RP_RECEIVE_MODE, RP_SETTINGS, RP_TEMPLATES, RP_HISTORY, RP_DELAY, RP_KEYWORDS, TRANSFER_KEYWORDS, TRANSFER_TOGGLE, TRANSFER_LISTS, TRANSFER_DELAY, ANTI_TOGGLE,
        REALNAME_TOGGLE, ORIGINAL_TOGGLE, NEWROOM_TOGGLE, CHECK_UPDATE, WISH,
        SNS_TOGGLE, RESTART, DIAGNOSTICS, APK_UPDATE, ACTIVATION, DONATE, TABLET_MODE, TABLET_QR, ORIGINAL_DOWNLOAD, ORIGINAL_LISTS, SNS_FORWARD, SNS_AD_BLOCK, SNS_LOCAL_LIKES, SNS_LOCAL_COMMENTS, SNS_LOCAL_PRESETS, SNS_LOCAL_CLEAR
    }
    enum MenuCategory {
        CHAT("聊天"), RED_PACKET("红包"), MOMENTS("朋友圈"), ANTI_RECALL("防撤回"), SUPPORT("设置与支持"), EXPERIMENTAL("实验功能");
        final String label;
        MenuCategory(String value) { label = value; }
    }
    static final class MenuEntry {
        final MenuAction action;
        final MenuCategory category;
        String label;
        MenuEntry(MenuAction a, String text) { this(a, null, text); }
        MenuEntry(MenuAction a, MenuCategory c, String text) { action = a; category = c; label = text; }
        public String toString() { return label; }
    }
    static String menuCategoryTitle(MenuCategory category) {
        if (category == null) return "模块设置";
        return category == MenuCategory.ANTI_RECALL && imageRecallEnabled()
                ? "防撤回（高级版）" : category.label;
    }
    static void addMenuAction(java.util.ArrayList<MenuEntry> rows, MenuAction action, String label) {
        MenuEntry row = new MenuEntry(action, label);
        refreshMenuLabel(row);
        rows.add(row);
    }
    static java.util.ArrayList<MenuEntry> menuPageEntries(MenuCategory category) {
        java.util.ArrayList<MenuEntry> rows = new java.util.ArrayList<MenuEntry>();
        if (category == null) {
            for (MenuCategory page : MenuCategory.values()) {
                if(page==MenuCategory.EXPERIMENTAL&&!tabletFeatureAvailable())continue;
                if(page==MenuCategory.MOMENTS&&!momentsPremiumEnabled())continue;
                rows.add(new MenuEntry(null, page, menuCategoryTitle(page) + "  ›"));
            }
            addMenuAction(rows, MenuAction.DONATE, "打赏");
            return rows;
        }
        switch (category) {
            case CHAT:
                addMenuAction(rows, MenuAction.MARK_READ, "一键已读");
                addMenuAction(rows, MenuAction.CLEAR_CHATS, "清空聊天列表");
                addMenuAction(rows, MenuAction.UNFOLLOW_BIZ, "公众号取关");
                addMenuAction(rows, MenuAction.REALNAME_TOGGLE, "");
                addMenuAction(rows, MenuAction.ORIGINAL_TOGGLE, "");
                addMenuAction(rows, MenuAction.ORIGINAL_DOWNLOAD, "");
                addMenuAction(rows, MenuAction.ORIGINAL_LISTS, "原图下载黑白名单（个人 / 群聊）");
                addMenuAction(rows, MenuAction.NEWROOM_TOGGLE, "");
                break;
            case RED_PACKET:
                addMenuAction(rows, MenuAction.RP_TOGGLE, "");
                addMenuAction(rows, MenuAction.RP_RECEIVE_MODE, "");
                addMenuAction(rows, MenuAction.RP_GROUPS, "红包黑白名单（个人 / 群聊）");
                addMenuAction(rows, MenuAction.RP_DELAY, "");
                if(receiptKeywordsEnabled())addMenuAction(rows,MenuAction.RP_KEYWORDS,"");
                if (redPacketPremiumEnabled()) {
                    addMenuAction(rows, MenuAction.RP_SETTINGS, "领取提示与自动回复（高级版）");
                    addMenuAction(rows, MenuAction.RP_TEMPLATES, "编辑自动回复感言");
                    addMenuAction(rows, MenuAction.RP_HISTORY, "查看领取与收款记录");
                    addMenuAction(rows, MenuAction.TRANSFER_TOGGLE, "");
                    addMenuAction(rows, MenuAction.TRANSFER_LISTS, "转账黑白名单（个人 / 群聊）");
                    addMenuAction(rows, MenuAction.TRANSFER_DELAY, "");
                    if(receiptKeywordsEnabled())addMenuAction(rows,MenuAction.TRANSFER_KEYWORDS,"");
                }
                break;
            case MOMENTS:
                if(momentsPremiumEnabled()){addMenuAction(rows,MenuAction.SNS_FORWARD,"");addMenuAction(rows,MenuAction.SNS_AD_BLOCK,"");addMenuAction(rows,MenuAction.SNS_LOCAL_LIKES,"");addMenuAction(rows,MenuAction.SNS_LOCAL_COMMENTS,"");addMenuAction(rows,MenuAction.SNS_LOCAL_PRESETS,"伪评论内容预设");addMenuAction(rows,MenuAction.SNS_LOCAL_CLEAR,"清空本地互动记录");}
                break;
            case ANTI_RECALL:
                addMenuAction(rows, MenuAction.ANTI_TOGGLE, "");
                addMenuAction(rows, MenuAction.SNS_TOGGLE, "");
                break;
            case EXPERIMENTAL:
                if(!tabletFeatureAvailable())break;
                addMenuAction(rows,MenuAction.TABLET_MODE,"平板模式");
                addMenuAction(rows,MenuAction.TABLET_QR,"平板扫码登录");
                break;
            case SUPPORT:
                addMenuAction(rows, MenuAction.ACTIVATION, "升级/更换激活码");
                String pending = callMainStr("pendingVersion");
                addMenuAction(rows, MenuAction.CHECK_UPDATE, pending != null && pending.length() > 0
                        ? "新版本 v" + pending + "，点此更新" : "检查内核更新");
                String apk = callMainStr("apkPendingName");
                if (apk != null && apk.length() > 0)
                    addMenuAction(rows, MenuAction.APK_UPDATE, "模块 " + apk + " 可更新，点此下载");
                addMenuAction(rows, MenuAction.WISH, "功能建议");
                addMenuAction(rows, MenuAction.DIAGNOSTICS, "真名自检（上报日志）");
                addMenuAction(rows, MenuAction.RESTART, "重启微信");
                break;
        }
        return rows;
    }
    static int menuPageHeight(Activity act, int rows, MenuCategory category) {
        android.util.DisplayMetrics metrics = act.getResources().getDisplayMetrics();
        return Math.min((int)(rows * (category==MenuCategory.MOMENTS?72:56) * metrics.density), (int)(metrics.heightPixels * 0.55f));
    }
    static void refreshMenuLabel(MenuEntry item) {
        switch (item.action) {
            case RP_TOGGLE: item.label = (freeRedPacketForegroundOnly()?"前台自动拉起并拆包":"自动领取红包"); break;
            case RP_RECEIVE_MODE: item.label = "领取方式：" + (freeRedPacketForegroundOnly()?"前台自动拉起并拆包":RedPacketRuntime.silentSelected() ? "后台静默领取" : "前台自动点领取"); break;
            case RP_DELAY: item.label="红包随机延时"; break;
            case RP_KEYWORDS:item.label=ReceiptSettings.keywordLabel(ReceiptConfig.Feature.RED_PACKET);break;
            case TRANSFER_KEYWORDS:item.label=ReceiptSettings.keywordLabel(ReceiptConfig.Feature.TRANSFER);break;
            case TRANSFER_TOGGLE: item.label="自动接收转账（高级版）"; break;
            case TRANSFER_DELAY: item.label="转账随机延时"; break;
            case ANTI_TOGGLE: item.label = "防撤回" + (imageRecallEnabled() ? "（高级版）" : "")
                    ; break;
            case REALNAME_TOGGLE: item.label = "真名"; break;
            case ORIGINAL_TOGGLE: item.label = "发送原图"; break;
            case ORIGINAL_DOWNLOAD: item.label = "自动下载原图"; break;
            case NEWROOM_TOGGLE: item.label = "新群免打扰"; break;
            case SNS_TOGGLE: item.label = "朋友圈防撤回"; break;
            case SNS_FORWARD: item.label="朋友圈一键转发";break;
            case SNS_AD_BLOCK: item.label="朋友圈去广告";break;
            case SNS_LOCAL_LIKES:item.label="朋友圈伪集赞";break;
            case SNS_LOCAL_COMMENTS:item.label="朋友圈伪评论";break;
            default: break;
        }
    }
    static void performMenuAction(final Activity act, MenuEntry item, final android.widget.TextView tVer, final Runnable refresh) {
        switch (item.action) {
            case MARK_READ: new Kernel().markAllRead(act); break;
            case CLEAR_CHATS: showChatCleaner(act); break;
            case UNFOLLOW_BIZ: showBizPicker(act); break;
            case RP_GROUPS: ReceiptSettings.lists(act,ReceiptConfig.Feature.RED_PACKET); break;
            case RP_KEYWORDS:ReceiptSettings.keywords(act,ReceiptConfig.Feature.RED_PACKET,refresh);break;
            case TRANSFER_KEYWORDS:ReceiptSettings.keywords(act,ReceiptConfig.Feature.TRANSFER,refresh);break;
            case RP_TOGGLE: ReceiptSettings.toggle(act,ReceiptConfig.Feature.RED_PACKET,refresh); break;
            case RP_DELAY: ReceiptSettings.delay(act,ReceiptConfig.Feature.RED_PACKET,refresh); break;
            case TRANSFER_TOGGLE: ReceiptSettings.toggle(act,ReceiptConfig.Feature.TRANSFER,refresh); break;
            case TRANSFER_LISTS: ReceiptSettings.lists(act,ReceiptConfig.Feature.TRANSFER); break;
            case TRANSFER_DELAY: ReceiptSettings.delay(act,ReceiptConfig.Feature.TRANSFER,refresh); break;
            case RP_RECEIVE_MODE: RedPacketSettings.mode(act, refresh); break;
            case RP_SETTINGS: RedPacketSettings.show(act); break;
            case RP_TEMPLATES: RedPacketSettings.templates(act); break;
            case RP_HISTORY: RedPacketSettings.history(act); break;
            case ANTI_TOGGLE: toggleFlag(AR_OFF_PATH, "防撤回", true); RecallMarkLayout.refresh(); MediaRecallRuntime.settingsChanged(); break;
            case REALNAME_TOGGLE:
                toggleFlag(RN_OFF_PATH, "群成员真名", true);
                // v196：立刻生效，不等下一次轮询
                RN_FLAG_AT = 0;
                RN_ON = !f(RN_OFF_PATH);
                if (!RN_ON) {
                    T("rn: off by menu -> refresh");
                    try { RN_QUEUE.clear(); RN_SET.clear(); } catch (Throwable t) {}
                    refreshChatNamesOnce();
                } else if (CUR_ROOM != null) {
                    rnOnGroupOpen(CUR_ROOM);
                }
                break;
            case ORIGINAL_TOGGLE: toggleFlag(ORIG_OFF_PATH, "原图发送", true); break;
            case ORIGINAL_DOWNLOAD: ReceiptSettings.toggle(act,ReceiptConfig.Feature.ORIGINAL_IMAGE,refresh); OriginalImageRuntime.settingsChanged(); break;
            case ORIGINAL_LISTS: ReceiptSettings.lists(act,ReceiptConfig.Feature.ORIGINAL_IMAGE); break;
            case NEWROOM_TOGGLE: toggleFlag(NEWROOM_ON_PATH, "新群免打扰"); break;
            case SNS_TOGGLE: toggleFlag(WXDIR + "sns.keep.del.on", "朋友圈防撤回"); break;
            case SNS_FORWARD:setMomentsToggle(act,MomentsConfig.Feature.FORWARD,!MomentsSettings.configured(MomentsConfig.Feature.FORWARD));break;
            case SNS_AD_BLOCK:setMomentsToggle(act,MomentsConfig.Feature.AD_BLOCK,!MomentsSettings.configured(MomentsConfig.Feature.AD_BLOCK));break;
            case SNS_LOCAL_LIKES:SnsLocalSettings.toggle(act,SnsLocalData.Feature.LIKES,!SnsLocalStore.enabled(SnsLocalData.Feature.LIKES));break;
            case SNS_LOCAL_COMMENTS:SnsLocalSettings.toggle(act,SnsLocalData.Feature.COMMENTS,!SnsLocalStore.enabled(SnsLocalData.Feature.COMMENTS));break;
            case SNS_LOCAL_PRESETS:SnsLocalSettings.presets(act);break;
            case SNS_LOCAL_CLEAR:SnsLocalSettings.clear(act);break;
            case CHECK_UPDATE:
                checkKernelUpdate(act);
                refreshVerLine(act, tVer);
                break;
            case DONATE: openDonation(act); break;
            case TABLET_MODE: break;
            case TABLET_QR: callTabletUi(act,"openTabletQr");break;
            case WISH: showWishDialog(act); break;
            case DIAGNOSTICS: rnSelfCheck(act); break;
            case RESTART: restartWeChat(act); break;
            case APK_UPDATE: doApkInstall(act); break;
            case ACTIVATION:
                try {
                    Class<?> loader = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    loader.getMethod("changeActivation", Activity.class).invoke(null, act);
                } catch (Throwable failure) { toast(act, "请先更新模块加载器"); }
                break;
        }
        refreshMenuLabel(item);
    }

    static boolean tabletFeatureAvailable() {return TABLET_PREMIUM_EDITION&&"1".equals(callMainStr("tabletFeatureEnabled"));}
    static void callTabletUi(Activity act,String method) {
        if(!tabletFeatureAvailable()){toast(act,"平板模式仅限高级版和 VIP 版，请升级内核并安装模块 APK 1.97");return;}
        try {Class.forName("com.zafiro.wxentry.Main",true,Kernel.class.getClassLoader()).getMethod(method,android.app.Activity.class).invoke(null,act);}
        catch(Throwable missing){toast(act,"请先更新模块 APK 至 1.97，再使用平板扫码登录");}
    }
    static void refreshMenuLabelSafe(MenuEntry row) { if(row.action!=null)refreshMenuLabel(row); }
    static Boolean menuToggleValue(MenuEntry row) {
        if(row.action==null)return null;
        switch(row.action) {
            case RP_TOGGLE:return ReceiptSettings.enabled(ReceiptConfig.Feature.RED_PACKET);
            case TRANSFER_TOGGLE:return ReceiptSettings.enabled(ReceiptConfig.Feature.TRANSFER);
            case RP_DELAY:return ReceiptSettings.delayEnabled(ReceiptConfig.Feature.RED_PACKET);
            case TRANSFER_DELAY:return ReceiptSettings.delayEnabled(ReceiptConfig.Feature.TRANSFER);
            case ANTI_TOGGLE:return !f(AR_OFF_PATH);
            case REALNAME_TOGGLE:return !f(RN_OFF_PATH);
            case ORIGINAL_TOGGLE:return !f(ORIG_OFF_PATH);
            case ORIGINAL_DOWNLOAD:return ReceiptSettings.enabled(ReceiptConfig.Feature.ORIGINAL_IMAGE);
            case NEWROOM_TOGGLE:return f(NEWROOM_ON_PATH);
            case SNS_TOGGLE:return f(WXDIR+"sns.keep.del.on");
            case SNS_FORWARD:return snsForwardEnabled();
            case SNS_AD_BLOCK:return snsAdBlockEnabled();
            case SNS_LOCAL_LIKES:return SnsLocalStore.enabled(SnsLocalData.Feature.LIKES);
            case SNS_LOCAL_COMMENTS:return SnsLocalStore.enabled(SnsLocalData.Feature.COMMENTS);
            case TABLET_MODE:return "1".equals(callMainStr("tabletModeConfigured"));
            default:return null;
        }
    }
    static boolean setMenuToggle(Activity act,MenuEntry row,boolean on,android.widget.TextView version,Runnable refresh) {
        if(row.action==MenuAction.TABLET_MODE) {
            if(!tabletFeatureAvailable()){toast(act,"平板模式仅限高级版和 VIP 版，请升级内核并安装模块 APK 1.97");return false;}
            try {
                Class<?> main=Class.forName("com.zafiro.wxentry.Main",true,Kernel.class.getClassLoader());
                return Boolean.TRUE.equals(main.getMethod("setTabletMode",android.app.Activity.class,boolean.class).invoke(null,act,on));
            }catch(Throwable missing){toast(act,"请先更新模块 APK 至 1.97，再使用平板模式");return false;}
        }
        if(row.action==MenuAction.SNS_FORWARD)return setMomentsToggle(act,MomentsConfig.Feature.FORWARD,on);
        if(row.action==MenuAction.SNS_AD_BLOCK)return setMomentsToggle(act,MomentsConfig.Feature.AD_BLOCK,on);
        if(row.action==MenuAction.SNS_LOCAL_LIKES)return SnsLocalSettings.toggle(act,SnsLocalData.Feature.LIKES,on);
        if(row.action==MenuAction.SNS_LOCAL_COMMENTS)return SnsLocalSettings.toggle(act,SnsLocalData.Feature.COMMENTS,on);
        if(row.action==MenuAction.RP_DELAY)return ReceiptSettings.setDelayEnabled(act,ReceiptConfig.Feature.RED_PACKET,on,null);
        if(row.action==MenuAction.TRANSFER_DELAY)return ReceiptSettings.setDelayEnabled(act,ReceiptConfig.Feature.TRANSFER,on,null);
        if(menuToggleValue(row)!=null && menuToggleValue(row).booleanValue()!=on)performMenuAction(act,row,version,null);
        return Boolean.TRUE.equals(menuToggleValue(row));
    }

    static void showFeatureMenu(final Activity act) {
        try {
            if (MODULE_OFF) { showOffDialog(act, OFF_REASON, true); return; }
            if (act == null || act.isFinishing() || act.isDestroyed()) return;
            final MenuCategory[] page = new MenuCategory[] { null };
            final java.util.ArrayList<MenuEntry> items = menuPageEntries(null);

            final android.widget.LinearLayout head = new android.widget.LinearLayout(act);
            head.setOrientation(android.widget.LinearLayout.VERTICAL);
            int dpad = (int) (16 * act.getResources().getDisplayMetrics().density);
            head.setPadding(dpad, dpad, dpad, 0);
            final android.widget.TextView tTitle = new android.widget.TextView(act);
            tTitle.setText("模块设置");
            tTitle.setTextSize(18);
            head.addView(tTitle);
            final android.widget.TextView tVer = new android.widget.TextView(act);
            tVer.setTextSize(12);
            tVer.setTextColor(0xFF888888);
            tVer.setPadding(0, (int) (6 * act.getResources().getDisplayMetrics().density), 0, 0);
            tVer.setText(verPrefix() + localKernelVer() + " · 服务器 查询中…");
            tVer.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) { onVerLineClick(act, tVer); }
            });
            head.addView(tVer);
            refreshVerLine(act, tVer);
            try {
                if (SERVER_TIER == null || SERVER_TIER.length() == 0) SERVER_TIER = "免费版";
                android.widget.TextView tT = new android.widget.TextView(act);
                tT.setTextSize(12);
                tT.setTextColor(0xFF1565C0);
                tT.setPadding(0, (int) (6 * act.getResources().getDisplayMetrics().density), 0, 0);
                tT.setText("当前版本：" + editionLabel());
                head.addView(tT);
            } catch (Throwable t) {}
            try {
                if (SERVER_REPLY != null && SERVER_REPLY.length() > 0) {
                    android.widget.TextView tR = new android.widget.TextView(act);
                    tR.setTextSize(12);
                    tR.setTextColor(0xFF2E7D32);
                    tR.setPadding(0, (int) (8 * act.getResources().getDisplayMetrics().density), 0, 0);
                    tR.setText("官方回复：" + SERVER_REPLY);
                    head.addView(tR);
                }
            } catch (Throwable t) {}

            int simpleItem = 0;
            try {
                simpleItem = ((Integer) XposedHelpers.callMethod(act.getResources(), "getIdentifier",
                        "simple_list_item_1", "layout", "android")).intValue();
            } catch (Throwable t) {}
            if (simpleItem == 0) simpleItem = 0x01090043;   // android.R.layout.simple_list_item_1
            final Runnable[] renderMenu=new Runnable[1];
            final android.widget.ArrayAdapter<MenuEntry> listAd =
                    new android.widget.ArrayAdapter<MenuEntry>(act, simpleItem, items) {
                        public boolean areAllItemsEnabled() { return true; }
                        public boolean isEnabled(int position) { return true; }
                        public android.view.View getView(int position, android.view.View reusable, android.view.ViewGroup parent) {
                            final MenuEntry entry=getItem(position);refreshMenuLabelSafe(entry);
                            Boolean checked=menuToggleValue(entry);
                            if(checked!=null) {
                                String detail=entry.action==MenuAction.RP_DELAY?ReceiptSettings.delayRangeLabel(ReceiptConfig.Feature.RED_PACKET):entry.action==MenuAction.TRANSFER_DELAY?ReceiptSettings.delayRangeLabel(ReceiptConfig.Feature.TRANSFER):entry.action==MenuAction.TABLET_MODE?"开启或关闭后需重启微信，登录页生效":entry.action==MenuAction.SNS_FORWARD?"长按目标进入发表页，需手动发表":entry.action==MenuAction.SNS_AD_BLOCK?"过滤微信广告，刷新朋友圈生效":entry.action==MenuAction.SNS_LOCAL_LIKES?"长按文案选择好友，仅本机展示":entry.action==MenuAction.SNS_LOCAL_COMMENTS?"长按文案添加评论，仅本机展示":entry.action==MenuAction.ORIGINAL_DOWNLOAD?"收到或打开图片时按名单下载可用原图，独立于防撤回":"";
                                SettingsUi.ToggleRow row=SettingsUi.toggle(act,entry.label,detail,checked.booleanValue(),new SettingsUi.Change() {
                                    public boolean set(boolean on) { boolean saved=setMenuToggle(act,entry,on,tVer,renderMenu[0]);notifyDataSetChanged();return saved; }
                                });
                                int pad=SettingsUi.dp(act,16);row.setPadding(pad,SettingsUi.dp(act,8),pad,SettingsUi.dp(act,8));
                                if(entry.action==MenuAction.RP_DELAY||entry.action==MenuAction.TRANSFER_DELAY)row.setOnClickListener(new android.view.View.OnClickListener() {
                                    public void onClick(android.view.View v) { performMenuAction(act,entry,tVer,renderMenu[0]); }
                                });
                                return row;
                            }
                            android.widget.TextView text=SettingsUi.text(act,entry.label,entry.category!=null?17:16);
                            text.setTypeface(entry.category!=null?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);
                            text.setMinimumHeight(SettingsUi.dp(act,56));text.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            int pad=SettingsUi.dp(act,16);text.setPadding(pad,SettingsUi.dp(act,12),pad,SettingsUi.dp(act,12));
                            android.view.View view=text;
                            return view;
                        }
                    };
            final android.widget.ListView lv = new android.widget.ListView(act);
            lv.setAdapter(listAd);

            final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(act);
            boolean custom = false;
            try {
                java.lang.reflect.Method sm = android.app.AlertDialog.Builder.class
                        .getMethod("setCustomTitle", android.view.View.class);
                sm.invoke(builder, head);
                custom = true;
            } catch (Throwable t) { T("custom title fail " + t); }
            if (!custom) builder.setTitle("模块设置");
            final boolean customTitleUsed = custom;

            android.widget.LinearLayout box = new android.widget.LinearLayout(act);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.addView(lv, new android.view.ViewGroup.LayoutParams(
                    -1, menuPageHeight(act, items.size(), page[0])));

            final android.app.AlertDialog dlg = builder.setView(box)
                    .setNegativeButton("\u5173\u95ed", null).create();
            final Runnable renderPage = new Runnable() {
                public void run() {
                    items.clear();
                    items.addAll(menuPageEntries(page[0]));
                    tTitle.setText(page[0] == null ? "模块设置" : menuCategoryTitle(page[0]));
                    if (!customTitleUsed) dlg.setTitle(page[0] == null ? "模块设置" : menuCategoryTitle(page[0]));
                    listAd.notifyDataSetChanged();
                    android.view.ViewGroup.LayoutParams params = lv.getLayoutParams();
                    params.height = menuPageHeight(act, items.size(), page[0]);
                    lv.setLayoutParams(params);
                    lv.setSelection(0);
                    android.widget.Button back = dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
                    if (back != null) back.setText(page[0] == null ? "关闭" : "返回");
                    T("feature menu page=" + (page[0] == null ? "root" : page[0].name()));
                }
            };
            renderMenu[0]=renderPage;
            lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                public void onItemClick(android.widget.AdapterView<?> parent, android.view.View v, int w, long id) {
                    try {
                        if (w < 0 || w >= items.size()) return;
                        MenuEntry row = items.get(w);
                        if(menuToggleValue(row)!=null)return; // Switch rows own their click; never toggle twice through ListView.
                        if (row.category != null) {
                            page[0] = row.category;
                            renderPage.run();
                        } else if (row.action != null) {
                            performMenuAction(act, row, tVer, renderPage);
                            listAd.notifyDataSetChanged();
                            tTitle.setText(menuCategoryTitle(page[0]));
                            if (!customTitleUsed) dlg.setTitle(menuCategoryTitle(page[0]));
                        }
                    } catch (Throwable t) { T("menu item err " + t); }
                }
            });
            dlg.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
                public boolean onKey(android.content.DialogInterface d, int key, android.view.KeyEvent event) {
                    if (key != android.view.KeyEvent.KEYCODE_BACK || page[0] == null) return false;
                    if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) {
                        page[0] = null;
                        renderPage.run();
                    }
                    return true;
                }
            });
            // Gesture back can cancel the dialog without delivering a key event.
            dlg.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                public void onCancel(android.content.DialogInterface d) {
                    if (page[0] != null && !act.isFinishing() && !act.isDestroyed()) showFeatureMenu(act);
                }
            });
            dlg.show();
            dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View view) {
                    if (page[0] == null) dlg.dismiss();
                    else { page[0] = null; renderPage.run(); }
                }
            });
            MENU_SHOW_COUNT++;
            T("feature menu shown");
        } catch (Throwable t) { T("feature menu err " + t); }
    }

    /** \u64a4\u56de\u56fe\u7247\u5907\u4efd\uff1a\u5217\u8868 + \u76f4\u63a5\u9884\u89c8\uff08\u4e0d\u9700\u8981\u5b58\u50a8\u6743\u9650\uff09 */
    static void showRecallShots(final Activity act) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.File dir = new java.io.File(WXDIR + "recall");
                    java.io.File[] fs = dir.listFiles();
                    final java.util.ArrayList<java.io.File> list = new java.util.ArrayList<java.io.File>();
                    if (fs != null) {
                        java.util.Arrays.sort(fs, new java.util.Comparator<java.io.File>() {
                            public int compare(java.io.File a, java.io.File b) {
                                return Long.compare(b.lastModified(), a.lastModified());
                            }
                        });
                        for (java.io.File f : fs) {
                            if (!f.isFile()) continue;
                            if (f.length() < 1024) continue;
                            if (f.getName().indexOf("writable") >= 0) continue;
                            list.add(f);
                        }
                    }
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            if (list.isEmpty()) { toast(act, "\u8fd8\u6ca1\u6709\u4fdd\u5b58\u7684\u56fe\u7247"); return; }
                            String[] names = new String[list.size()];
                            for (int i = 0; i < list.size(); i++) {
                                java.io.File f = list.get(i);
                                names[i] = f.getName() + "   " + (f.length() / 1024) + " KB";
                            }
                            new android.app.AlertDialog.Builder(act)
                                .setTitle("\u64a4\u56de\u56fe\u7247\u5907\u4efd\uff08" + list.size() + "\uff09")
                                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(android.content.DialogInterface d, int which) {
                                        preview(act, list.get(which));
                                    }
                                })
                                .setNeutralButton("\u6e05\u7a7a", new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(android.content.DialogInterface d, int w) {
                                        int n = 0;
                                        for (java.io.File f : list) if (f.delete()) n++;
                                        toast(act, "\u5df2\u5220\u9664 " + n + " \u4e2a");
                                    }
                                })
                                .setNegativeButton("\u5173\u95ed", null)
                                .show();
                        }
                    });
                } catch (Throwable t) { T("recall list err " + t); }
            }
        }).start();
    }

    static void preview(final Activity act, final java.io.File f) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int max = Math.max(o.outWidth, o.outHeight);
            while (max / sample > 1600) sample *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = sample;
            final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (bm == null) { toast(act, "\u65e0\u6cd5\u663e\u793a\uff08\u4e0d\u662f\u56fe\u7247\uff1f\uff09"); return; }
            android.widget.ImageView iv = new android.widget.ImageView(act);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            new android.app.AlertDialog.Builder(act)
                .setTitle(f.getName() + "   " + (f.length() / 1024) + " KB")
                .setView(iv)
                .setPositiveButton("\u5173\u95ed", null)
                .show();
        } catch (Throwable t) { T("preview err " + t); toast(act, "\u9884\u89c8\u5931\u8d25"); }
    }

    /** \u529f\u80fd\u8bb8\u613f\uff1a\u586b\u4e00\u6bb5\u6587\u5b57\u63d0\u4ea4\u5230\u670d\u52a1\u5668\uff08\u5e26\u6388\u6743\u51ed\u8bc1\uff09 */
    static void showWishDialog(final Activity act) {
        try {
            final android.widget.EditText et = new android.widget.EditText(act);
            et.setHint("\u60f3\u8981\u4ec0\u4e48\u529f\u80fd\uff1f\u6bcf\u5929\u53ea\u80fd\u63d0\u4e00\u6b21\uff0c\u5199\u6e05\u695a\u4f7f\u7528\u573a\u666f\u66f4\u5bb9\u6613\u88ab\u91c7\u7528");
            et.setMinLines(4);
            et.setGravity(48);
            et.setTextSize(15);
            new android.app.AlertDialog.Builder(act)
                .setTitle("\u529f\u80fd\u8bb8\u613f\uff08\u6bcf\u5929 1 \u6b21\uff09")
                .setView(et)
                .setPositiveButton("\u63d0\u4ea4", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        doWish(act, et.getText().toString());
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
        } catch (Throwable t) { T("wish dialog err " + t); }
    }

    static void doWish(final Activity act, String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.length() < 2) { toast(act, "\u5185\u5bb9\u592a\u77ed\u4e86"); return; }
        toast(act, "\u6b63\u5728\u63d0\u4ea4\u2026");
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try {
                    Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    java.lang.reflect.Method m = c.getDeclaredMethod("postWish", String.class);
                    m.setAccessible(true);
                    Object r = m.invoke(null, text);
                    msg = r == null ? "\u63d0\u4ea4\u5931\u8d25" : r.toString();
                } catch (Throwable t) {
                    Throwable cc = t;
                    while (cc.getCause() != null) cc = cc.getCause();
                    msg = "\u63d0\u4ea4\u5931\u8d25\uff1a" + cc;
                }
                T("wish -> " + msg);
                toastMain(msg);
            }
        }).start();
    }

    /** \u624b\u52a8\u68c0\u67e5\u5185\u6838\u66f4\u65b0\uff08\u6bcf\u5929\u4e00\u6b21\uff0c\u670d\u52a1\u5668\u6709\u65b0\u7248\u672c\u65f6\u4e0d\u9650\uff09 */
    static void checkKernelUpdate(final Activity act) {
        toast(act, "\u6b63\u5728\u5411\u670d\u52a1\u5668\u67e5\u8be2\u2026");
        T("manual check start");
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try {
                    Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    java.lang.reflect.Method m = c.getDeclaredMethod("checkUpdateNow");
                    m.setAccessible(true);
                    Object r = m.invoke(null);
                    msg = r == null ? "\u68c0\u6d4b\u5931\u8d25" : r.toString();
                } catch (Throwable t) {
                    Throwable c = t;
                    while (c.getCause() != null) c = c.getCause();
                    msg = "\u68c0\u6d4b\u5931\u8d25\uff1a" + c;
                }
                T("manual update -> " + msg);
                final String m2 = msg;
                try {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                new android.app.AlertDialog.Builder(act)
                                        .setTitle("\u68c0\u67e5\u5185\u6838\u66f4\u65b0")
                                        .setMessage(m2)
                                        .setPositiveButton("\u77e5\u9053\u4e86", null)
                                        .show();
                            } catch (Throwable t) { toast(act, m2); }
                        }
                    });
                } catch (Throwable t) { toastMain(m2); }
            }
        }).start();
    }

    static void toggleFlag(String path, String name) { toggleFlag(path, name, false); }

    static void toggleFlag(String path, String name, boolean inverse) {
        try {
            java.io.File f = new java.io.File(path);
            if (f.exists()) f.delete(); else f.createNewFile();
            boolean on = f.exists();
            if (inverse) on = !on;
            T("toggle " + name + " -> " + (on ? "ON" : "OFF") + " (" + path + ")");
            toastMain(name + (on ? "\uff1a\u5f00" : "\uff1a\u5173"));
        } catch (Throwable t) { T("toggle err " + t); }
    }


    static void showChatCleaner(final Activity act) {
        toast(act, "\u6b63\u5728\u8bfb\u53d6\u4f1a\u8bdd\u2026");
        new Thread(new Runnable() {
            public void run() {
                final java.util.ArrayList<String> ids = new java.util.ArrayList<String>();
                final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
                final java.util.ArrayList<Integer> types = new java.util.ArrayList<Integer>();
                try {
                    Object db = db0();
                    Cursor c = null;
                    try {
                        c = (Cursor) rawQuery(db, "select username from rconversation", null);
                        while (c != null && c.moveToNext()) {
                            String u = c.getString(0);
                            if (u == null || u.length() == 0) continue;
                            ids.add(u);
                            types.add(Integer.valueOf(u.startsWith("gh_") ? 0
                                    : (u.endsWith("@chatroom") ? 1 : 2)));
                            names.add(null);
                        }
                    } finally { if (c != null) c.close(); }

                    // 名字：rcontact 里查（备注优先，其次昵称）
                    java.util.HashMap<String, String> nm = new java.util.HashMap<String, String>();
                    for (int off = 0; off < ids.size(); off += 300) {
                        StringBuilder in = new StringBuilder();
                        int end = Math.min(ids.size(), off + 300);
                        for (int k = off; k < end; k++) {
                            if (in.length() > 0) in.append(",");
                            in.append("'").append(ids.get(k).replace("'", "''")).append("'");
                        }
                        Cursor c2 = null;
                        try {
                            c2 = (Cursor) rawQuery(db,
                                    "select username, conRemark, nickname from rcontact where username in (" + in + ")", null);
                            while (c2 != null && c2.moveToNext()) {
                                String u = c2.getString(0);
                                String rm = null, nk = null;
                                try { rm = c2.getString(1); } catch (Throwable t) {}
                                try { nk = c2.getString(2); } catch (Throwable t) {}
                                if (rm != null && rm.trim().length() > 0) nm.put(u, rm.trim());
                                else if (nk != null && nk.trim().length() > 0) nm.put(u, nk.trim());
                            }
                        } catch (Throwable t) {} finally { if (c2 != null) c2.close(); }
                    }
                    for (int k = 0; k < ids.size(); k++) {
                        String v = nm.get(ids.get(k));
                        names.set(k, (v == null || v.trim().length() == 0) ? ids.get(k) : v.trim());
                    }
                    T("cleaner: loaded " + ids.size());
                } catch (Throwable t) { T("cleaner load err " + t); }
                act.runOnUiThread(new Runnable() {
                    public void run() { buildChatCleaner(act, ids, names, types); }
                });
            }
        }).start();
    }

    static void clearSyncSel(android.widget.ListView lv, java.util.ArrayList<String> curIds,
                             java.util.HashSet<String> sel) {
        try {
            android.util.SparseBooleanArray cip = lv.getCheckedItemPositions();
            for (int i = 0; i < curIds.size(); i++) {
                String u = curIds.get(i);
                boolean checked = cip != null && cip.get(i);
                if (checked) sel.add(u); else sel.remove(u);
            }
        } catch (Throwable t) {}
    }

    static void clearRebuild(final android.widget.ListView lv, final android.widget.ArrayAdapter<String> ad,
                             final java.util.ArrayList<String> curIds, final java.util.ArrayList<String> curNames,
                             final java.util.ArrayList<String> ids, final java.util.ArrayList<String> names,
                             final java.util.ArrayList<Integer> types, int filter, String q,
                             final java.util.HashSet<String> sel, final android.widget.TextView cnt) {
        try {
            curIds.clear();
            curNames.clear();
            String qq = q == null ? "" : q.trim().toLowerCase();
            for (int i = 0; i < ids.size(); i++) {
                int tp = types.get(i).intValue();
                if (filter >= 0 && tp != filter) continue;
                String nm = names.get(i);
                if (qq.length() > 0 && (nm == null || nm.toLowerCase().indexOf(qq) < 0)) continue;
                curIds.add(ids.get(i));
                curNames.add((tp == 0 ? "[\u516c\u4f17\u53f7] " : tp == 1 ? "[\u7fa4\u804a] " : "[\u4e2a\u4eba] ") + nm);
            }
            ad.notifyDataSetChanged();
            for (int i = 0; i < curIds.size(); i++) {
                try { lv.setItemChecked(i, sel.contains(curIds.get(i))); } catch (Throwable t) {}
            }
            cnt.setText("\u5df2\u9009 " + sel.size() + " / \u672c\u9875 " + curIds.size());
        } catch (Throwable t) { T("cleaner rebuild err " + t); }
    }

    static void buildChatCleaner(final Activity act,
                                 final java.util.ArrayList<String> ids,
                                 final java.util.ArrayList<String> names,
                                 final java.util.ArrayList<Integer> types) {
        try {
            final int pad = 28;
            final java.util.HashSet<String> sel = new java.util.HashSet<String>();
            final int[] filter = new int[]{-1};
            final boolean[] alsoMsg = new boolean[]{false};

            android.widget.LinearLayout root = new android.widget.LinearLayout(act);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);

            // 顶部：已选计数 + 全选 / 清空
            android.widget.LinearLayout head = new android.widget.LinearLayout(act);
            head.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            final android.widget.TextView cnt = new android.widget.TextView(act);
            cnt.setText("\u5df2\u9009 0");
            cnt.setPadding(pad, pad / 2, pad, pad / 2);
            cnt.setTextSize(14);
            final android.widget.TextView all = new android.widget.TextView(act);
            all.setText("\u5168\u9009\u672c\u7c7b");
            all.setPadding(pad, pad / 2, pad, pad / 2);
            all.setTextSize(14);
            final android.widget.TextView none = new android.widget.TextView(act);
            none.setText("\u6e05\u7a7a\u52fe\u9009");
            none.setPadding(pad, pad / 2, pad, pad / 2);
            none.setTextSize(14);
            head.addView(cnt);
            root.addView(head);

            // 全选 / 清空（放在列表上方，避免被列表挡住）
            android.widget.LinearLayout ops = new android.widget.LinearLayout(act);
            ops.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            ops.addView(all);
            ops.addView(none);
            root.addView(ops);

            // 类别
            final android.widget.LinearLayout tabs = new android.widget.LinearLayout(act);
            tabs.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            final String[] tabNames = new String[]{"\u5168\u90e8", "\u516c\u4f17\u53f7", "\u7fa4\u804a", "\u4e2a\u4eba"};
            root.addView(tabs);

            // 搜索
            final android.widget.EditText search = new android.widget.EditText(act);
            search.setHint("\u641c\u7d22\u4f1a\u8bdd");
            root.addView(search);

            // 列表
            final android.widget.ListView lv = new android.widget.ListView(act);
            int layoutId = 0;
            try {
                layoutId = Class.forName("android.R$layout").getField("simple_list_item_multiple_choice").getInt(null);
            } catch (Throwable t) { T("cleaner layout err " + t); }
            final int layId = layoutId;
            lv.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            final java.util.ArrayList<String> curIds = new java.util.ArrayList<String>();
            final java.util.ArrayList<String> curNames = new java.util.ArrayList<String>();
            final android.widget.ArrayAdapter<String> ad =
                    new android.widget.ArrayAdapter<String>(act, layId, curNames);
            lv.setAdapter(ad);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 660);
            root.addView(lv, lp);

            // 同时清空聊天记录
            final android.widget.TextView also = new android.widget.TextView(act);
            also.setText("\u540c\u65f6\u6e05\u7a7a\u804a\u5929\u8bb0\u5f55\uff1a\u5426");
            also.setPadding(pad, pad, pad, pad);
            also.setTextSize(15);
            root.addView(also);

            lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    try {
                        String u = curIds.get(pos);
                        if (lv.isItemChecked(pos)) sel.add(u); else sel.remove(u);
                        cnt.setText("已选 " + sel.size() + " / 本页 " + curIds.size());
                    } catch (Throwable t) {}
                }
            });

            clearRebuild(lv, ad, curIds, curNames, ids, names, types, -1, "", sel, cnt);

            final android.widget.TextView[] tabViews = new android.widget.TextView[4];
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                final android.widget.TextView tv = new android.widget.TextView(act);
                tv.setText(tabNames[i]);
                tv.setPadding(pad, pad / 2, pad, pad / 2);
                tv.setTextSize(16);
                tv.setTextColor(0xff333333);
                tv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        clearSyncSel(lv, curIds, sel);
                        filter[0] = idx - 1;
                        for (int k = 0; k < 4; k++) tabViews[k].setTextColor(k == idx ? 0xff07c160 : 0xff333333);
                        clearRebuild(lv, ad, curIds, curNames, ids, names, types, filter[0],
                                String.valueOf(search.getText()), sel, cnt);
                    }
                });
                tabViews[i] = tv;
                tabs.addView(tv);
            }
            tabViews[0].setTextColor(0xff07c160);

            search.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(android.text.Editable s) {
                    clearSyncSel(lv, curIds, sel);
                    clearRebuild(lv, ad, curIds, curNames, ids, names, types, filter[0],
                            s == null ? "" : s.toString(), sel, cnt);
                }
            });

            all.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (String u : curIds) sel.add(u);
                    for (int i = 0; i < curIds.size(); i++) {
                        try { lv.setItemChecked(i, true); } catch (Throwable t) {}
                    }
                    cnt.setText("\u5df2\u9009 " + sel.size() + " / \u672c\u9875 " + curIds.size());
                }
            });
            none.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    sel.clear();
                    for (int i = 0; i < curIds.size(); i++) {
                        try { lv.setItemChecked(i, false); } catch (Throwable t) {}
                    }
                    cnt.setText("\u5df2\u9009 0 / \u672c\u9875 " + curIds.size());
                }
            });
            also.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    alsoMsg[0] = !alsoMsg[0];
                    also.setText("\u540c\u65f6\u6e05\u7a7a\u804a\u5929\u8bb0\u5f55\uff1a" + (alsoMsg[0] ? "\u662f" : "\u5426"));
                }
            });

            new android.app.AlertDialog.Builder(act)
                .setTitle("\u6e05\u7a7a\u804a\u5929\u5217\u8868")
                .setView(root)
                .setPositiveButton("\u5220\u9664\u9009\u4e2d", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        clearSyncSel(lv, curIds, sel);
                        if (sel.isEmpty()) { toast(act, "\u6ca1\u6709\u9009\u4e2d\u4f1a\u8bdd"); return; }
                        final java.util.ArrayList<String> chosen = new java.util.ArrayList<String>(sel);
                        new android.app.AlertDialog.Builder(act)
                            .setMessage("\u5220\u9664 " + chosen.size() + " \u4e2a\u4f1a\u8bdd"
                                    + (alsoMsg[0] ? "\uff08\u542b\u804a\u5929\u8bb0\u5f55\uff09" : "") + "\uff1f")
                            .setPositiveButton("\u786e\u5b9a", new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d2, int w2) {
                                    clearSelected(chosen, alsoMsg[0]);
                                }
                            })
                            .setNegativeButton("\u53d6\u6d88", null)
                            .show();
                    }
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
        } catch (Throwable t) { T("cleaner dialog err " + t); }
    }

    static void clearSelected(final java.util.ArrayList<String> ids, final boolean alsoMsg) {
        new Thread(new Runnable() {
            public void run() {
                int conv = 0, msg = 0;
                try {
                    Object db = db0();
                    for (int off = 0; off < ids.size(); off += 100) {
                        int end = Math.min(ids.size(), off + 100);
                        StringBuilder in = new StringBuilder();
                        for (int i = off; i < end; i++) {
                            if (in.length() > 0) in.append(",");
                            in.append("'").append(ids.get(i).replace("'", "''")).append("'");
                        }
                        String list = in.toString();
                        if (alsoMsg) {
                            // 140：优先用微信自己的删除入口（会顺带刷新列表）
                            for (int i = off; i < end; i++) {
                                String u = ids.get(i);
                                if (delConvViaApi(u)) { conv++; }
                                else {
                                    msg += clearMessagesOf(db, u);
                                    String one = "'" + u.replace("'", "''") + "'";
                                    conv += clearConvsOf(db, one);
                                }
                            }
                        } else {
                            conv += clearConvsOf(db, list);
                        }
                        try { Thread.sleep(30); } catch (Throwable t) {}
                        T("clear progress conv=" + conv + " msg=" + msg + " (" + end + "/" + ids.size() + ")");
                    }
                } catch (Throwable t) { T("clear selected err " + t); }
                T("clear selected conv=" + conv + " msg=" + msg + " alsoMsg=" + alsoMsg);
                if (conv > 0) {
                    // v190：被删的会话已经不在表里，拿它去 M() 不会触发任何通知；
                    // 换一个还存在的会话当锚点，再补一次 DB 级 updateNotify，列表立刻刷新
                    String anchor = "filehelper";
                    try {
                        Object db2 = db0();
                        Cursor c2 = null;
                        try {
                            c2 = (Cursor) rawQuery(db2, "select username from rconversation where username not like 'gh_%' limit 1", null);
                            if (c2 != null && c2.moveToFirst()) {
                                String uu = c2.getString(0);
                                if (uu != null && uu.length() > 0) anchor = uu;
                            }
                        } finally { if (c2 != null) c2.close(); }
                    } catch (Throwable t) { T("clear anchor err " + t); }
                    try {
                        Class<?> lc = Class.forName(SVC_LOCATOR, true, APP_CL);
                        Class<?> ic = Class.forName(SVC_IFACE, true, APP_CL);
                        Object sv = XposedHelpers.callStaticMethod(lc, "v", ic);
                        Object cvx = XposedHelpers.callMethod(sv, "Hj");
                        if (cvx != null) {
                            java.util.ArrayList<String> gone = new java.util.ArrayList<String>();
                            Object ddb = db0();
                            for (int gi = 0; gi < ids.size(); gi++) {
                                String gu = ids.get(gi);
                                if (gu == null) continue;
                                try {
                                    if (countOf(ddb, "select count(*) from rconversation where username='"
                                            + gu.replace("'", "''") + "'") == 0) gone.add(gu);
                                } catch (Throwable t) {}
                            }
                            int pg = purgeConvCache(cvx, gone);
                            int nt = 0;
                            for (int ni = 0; ni < gone.size(); ni++) {
                                try { XposedHelpers.callMethod(cvx, "M", gone.get(ni)); nt++; } catch (Throwable t) {}
                            }
                            T("clear: cache purge removed=" + pg + " gone=" + gone.size() + " of " + ids.size()
                                    + " notify=" + nt);
                        }
                    } catch (Throwable t) { T("clear purge outer " + t); }
                    try { refreshConvList(anchor); } catch (Throwable t) {}
                    try { refreshConvListUi(); } catch (Throwable t) {}
                    try {
                        XposedHelpers.callMethod(db0(), "updateNotify");
                        T("clear: updateNotify ok anchor=" + anchor);
                    } catch (Throwable t) { T("clear: updateNotify err " + t); }
                }
                toastMain("\u5df2\u5220\u9664 " + conv + " \u4e2a\u4f1a\u8bdd" + (alsoMsg ? "\uff08\u542b\u804a\u5929\u8bb0\u5f55\uff09" : ""));
            }
        }).start();
    }

    // ===== 主页「发现新版本」横幅 + 手动更新 =====

    static String callMainStr(String method) {
        try {
            Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
            java.lang.reflect.Method m = c.getDeclaredMethod(method);
            m.setAccessible(true);
            Object r = m.invoke(null);
            return r == null ? null : r.toString();
        } catch (Throwable t) { return null; }
    }



    /** 1.47：真正能重启的「重启微信」。
     *  以前只 System.exit(0)，进程被杀但没人再拉起来，表现就是"只关闭不重启"。
     *  现在先用 AlarmManager 排一个 1 秒后拉起 LauncherUI 的闹钟（闹钟由系统持有，进程死了照样触发），再退出。 */
    static void restartWeChat(final Activity act) {
        toast(act, "正在重启微信…");
        try {
            Object i = Class.forName("android.content.Intent").getConstructor().newInstance();
            java.lang.reflect.Method setCn = i.getClass().getMethod("setClassName", String.class, String.class);
            setCn.invoke(i, "com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
            i.getClass().getMethod("addFlags", int.class).invoke(i, Integer.valueOf(0x10000000));   // NEW_TASK
            Class<?> piC = Class.forName("android.app.PendingIntent");
            int flags = 0x08000000 | 0x04000000;   // FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE
            Object pi = piC.getMethod("getActivity", android.content.Context.class, int.class,
                    Class.forName("android.content.Intent"), int.class)
                    .invoke(null, act, Integer.valueOf(0x5745), i, Integer.valueOf(flags));
            Object svc = act.getClass().getMethod("getSystemService", String.class).invoke(act, "alarm");
            Class<?> amC = Class.forName("android.app.AlarmManager");
            long at = System.currentTimeMillis() + 1000;
            try {
                amC.getMethod("setExact", int.class, long.class, piC).invoke(svc, Integer.valueOf(1), Long.valueOf(at), pi);
            } catch (Throwable t) {
                amC.getMethod("set", int.class, long.class, piC).invoke(svc, Integer.valueOf(1), Long.valueOf(at), pi);
            }
            T("restart: alarm set");
        } catch (Throwable t) { T("restart alarm err " + t); }
        new Thread(new Runnable() { public void run() {
            try { Thread.sleep(300); } catch (Throwable t) {}
            try {
                Class<?> p = Class.forName("android.os.Process");
                int pid = ((Integer) p.getMethod("myPid").invoke(null)).intValue();
                p.getMethod("killProcess", int.class).invoke(null, Integer.valueOf(pid));
            } catch (Throwable t) {}
            System.exit(0);
        } }).start();
    }

    static void doApkInstall(final Activity act) {
        toast(act, "正在准备下载…");
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try {
                    Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    java.lang.reflect.Method m = c.getDeclaredMethod("installApkUpdate", android.content.Context.class);
                    m.setAccessible(true);
                    Object r = m.invoke(null, act);
                    msg = r == null ? "下载失败" : r.toString();
                } catch (Throwable t) {
                    Throwable cc = t;
                    while (cc.getCause() != null) cc = cc.getCause();
                    msg = "下载失败：" + cc;
                }
                T("apk download -> " + msg);
                toastMain(msg);
            }
        }).start();
    }

    static void doInstall(final Activity act) {
        toast(act, "正在下载更新…");
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try {
                    Class<?> c = Class.forName("com.zafiro.wxentry.Main", true, Kernel.class.getClassLoader());
                    java.lang.reflect.Method m = c.getDeclaredMethod("installUpdate");
                    m.setAccessible(true);
                    Object r = m.invoke(null);
                    msg = r == null ? "更新失败" : r.toString();
                } catch (Throwable t) {
                    Throwable cc = t;
                    while (cc.getCause() != null) cc = cc.getCause();
                    msg = "更新失败：" + cc;
                }
                T("banner install -> " + msg);
                toastMain(msg);
            }
        }).start();
    }

    // ===== 防撤回：别让微信把刚收到不久的图片/视频删掉 =====
    static boolean freeRedPacketForegroundOnly() { return "free".equals(EDITION); }
    static String receiptAccount() {
        try { return String.valueOf(XposedHelpers.callStaticMethod(XposedHelpers.findClass("w41.z1",APP_CL),"u")); }
        catch(Throwable unavailable) { return ""; }
    }
    static boolean receiptKeywordsEnabled() { return "vip".equals(EDITION) && redPacketPremiumEnabled(); }
    static boolean redPacketPremiumEnabled() {
        return RP_PREMIUM_EDITION && RedPacketPolicy.premium(EDITION, callMainStr("currentKernelEdition"), MODULE_OFF);
    }
    static boolean momentsPremiumEnabled() {return !MODULE_OFF && ("plus".equals(EDITION)||"vip".equals(EDITION)) && "1".equals(callMainStr2("imageFeatureEnabledFor", EDITION));}
    static boolean snsForwardEnabled() {return momentsPremiumEnabled()&&MomentsSettings.configured(MomentsConfig.Feature.FORWARD);}
    static boolean snsAdBlockEnabled() {return momentsPremiumEnabled()&&MomentsSettings.configured(MomentsConfig.Feature.AD_BLOCK);}
    static boolean setMomentsToggle(Activity act,MomentsConfig.Feature feature,boolean on){
        if(!momentsPremiumEnabled()){toast(act,"朋友圈增强功能仅限高级版和 VIP 版");return false;}
        if(!MomentsSettings.set(feature,on)){toast(act,"朋友圈设置保存失败，请稍后重试");return momentsPremiumEnabled()&&MomentsSettings.configured(feature);}
        if(feature==MomentsConfig.Feature.AD_BLOCK)toast(act,(on?"去广告已开启":"去广告已关闭")+"，刷新或重新进入朋友圈生效");
        return on;
    }
    static boolean imageRecallEnabled() {
        return !MODULE_OFF && !f(AR_OFF_PATH) && "1".equals(callMainStr2("imageFeatureEnabledFor", EDITION));
    }
    static String editionLabel() {
        if ("plus".equals(EDITION)) return "高级版";
        if ("vip".equals(EDITION)) return "VIP 版";
        return "免费版";
    }
    static volatile boolean AR_ON = true;
    static long AR_AT = 0;
    static int DEL_KEEP = 0;
    static volatile long LAST_REVOKE_AT = 0;

    static boolean arOn() {
        long now = System.currentTimeMillis();
        if (now - AR_AT > 5000) {
            AR_AT = now;
            boolean on = true;
            try { on = !new java.io.File(AR_OFF_PATH).exists(); } catch (Throwable t) {}
            AR_ON = on;
        }
        return AR_ON;
    }

    // v151（S10）：撤回文件按「这条消息自己的文件路径」保护，不再对整个时间窗内的媒体一刀切。
    static final java.util.concurrent.ConcurrentHashMap<String, Long> RECALL_PATHS =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> RECALL_NAMES =
            new java.util.concurrent.ConcurrentHashMap<String, Long>();   // 只存无法还原目录的 basename
    static final Object RECALL_LOCK = new Object();

    static void recallPathsLoad() {
        try {
            String t = readText(WXDIR + "recall.paths");
            if (t == null) return;
            long now = System.currentTimeMillis();
            for (String l : t.split("\n")) {
                int tab = l.indexOf('\t');
                if (tab <= 0) continue;
                long ts = 0;
                try { ts = Long.parseLong(l.substring(0, tab).trim()); } catch (Throwable x) { continue; }
                if (now - ts > 7L * 24 * 3600 * 1000) continue;
                String v = l.substring(tab + 1).trim();
                if (v.length() == 0) continue;
                if (v.startsWith("/")) RECALL_PATHS.put(v, Long.valueOf(ts));
                else RECALL_NAMES.put(v, Long.valueOf(ts));
            }
            T("recall.paths loaded " + RECALL_PATHS.size() + "+" + RECALL_NAMES.size());
        } catch (Throwable t) { T("recall.paths load err " + t); }
    }

    static void recallRemember(String raw) {
        try {
            if (raw == null) return;
            String v = raw.trim();
            if (v.length() < 6) return;
            String key = v;
            if (!v.startsWith("/")) {
                int sl = v.lastIndexOf('/');
                key = sl >= 0 ? v.substring(sl + 1) : v;      // THUMBNAIL_DIRPATH://th_xxx 之类只留 basename
                if (key.length() < 6) return;
            }
            long now = System.currentTimeMillis();
            synchronized (RECALL_LOCK) {
                if (key.startsWith("/")) RECALL_PATHS.put(key, Long.valueOf(now));
                else RECALL_NAMES.put(key, Long.valueOf(now));
                StringBuilder sb = new StringBuilder();
                for (java.util.Map.Entry<String, Long> e : RECALL_PATHS.entrySet())
                    sb.append(e.getValue()).append('\t').append(e.getKey()).append('\n');
                for (java.util.Map.Entry<String, Long> e : RECALL_NAMES.entrySet())
                    sb.append(e.getValue()).append('\t').append(e.getKey()).append('\n');
                try {
                    java.io.FileWriter w = new java.io.FileWriter(new java.io.File(WXDIR + "recall.paths"), false);
                    w.write(sb.toString()); w.close();
                } catch (Throwable t) { T("recall.paths write err " + t); }
            }
            T("recall.keep path " + (key.startsWith("/") ? key : "name:" + key));
        } catch (Throwable t) { T("recallRemember err " + t); }
    }

    /** 撤回发生时把这条消息自己的文件路径登记下来（图片/视频/缩略图）。 */
    static void recallRememberMsg(Object db, long mid) {
        try {
            Cursor c = rawQuery(db, "select imgPath from message where msgId=?", new String[]{String.valueOf(mid)});
            String ip = null;
            try { if (c != null && c.moveToFirst()) ip = c.getString(0); } finally { if (c != null) c.close(); }
            if (ip == null || ip.length() < 6) { T("recall.paths none msgId=" + mid); return; }
            recallRemember(ip);
        } catch (Throwable t) { T("recallRememberMsg err " + t); }
    }

    /** path 是否属于某条被撤回消息的文件（含 th_ 缩略图与对应大图）。 */
    static boolean recallMatches(String path) {
        if (path == null) return false;
        long now = System.currentTimeMillis();
        long ttl = 7L * 24 * 3600 * 1000;
        Long ts = RECALL_PATHS.get(path);
        if (ts != null && now - ts.longValue() <= ttl) return true;
        int i = path.lastIndexOf('/');
        String dir = i > 0 ? path.substring(0, i + 1) : "";
        String nm = i > 0 ? path.substring(i + 1) : path;
        boolean thumb = nm.startsWith("th_");
        String peer = thumb ? dir + nm.substring(3) : dir + "th_" + nm;
        ts = RECALL_PATHS.get(peer);
        if (ts != null && now - ts.longValue() <= ttl) return true;
        // 只有确实拿不到目录的（微信给的是 THUMBNAIL_DIRPATH 形式）才退化成按名字匹配
        ts = RECALL_NAMES.get(nm);
        if (ts != null && now - ts.longValue() <= ttl) return true;
        if (thumb) {
            String big = nm.substring(3);
            ts = RECALL_NAMES.get(big);
            if (ts != null && now - ts.longValue() <= ttl) return true;
        }
        return false;
    }

    static boolean inRecallWindow() {
        long dt = System.currentTimeMillis() - LAST_REVOKE_AT;
        return LAST_REVOKE_AT != 0 && dt >= 0 && dt <= 20000;
    }

    /** 撤回瞬间把还能拿到的文件复制到模块私有目录（不进相册） */
    static void stash(java.io.File f) {
        if (!inRecallWindow()) return;
        if (!f.exists() || !f.isFile()) return;
        long len = f.length();
        if (len < 1024 || len > 50L * 1024 * 1024) return;
        String nm = f.getName();
        if (nm.indexOf("writable") >= 0) return;
        if (nm.endsWith("_tmp.jpg")) return;
        java.io.File dir = new java.io.File(WXDIR + "recall");
        if (!dir.exists()) dir.mkdirs();
        java.io.File dst = new java.io.File(dir, f.getName());
        if (dst.exists() && dst.length() == len) return;
        java.io.FileInputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = new java.io.FileInputStream(f);
            out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            T("stash " + dst.getName() + " (" + len + ")");
        } catch (Throwable t) {
            T("stash fail " + t);
        } finally {
            try { if (in != null) in.close(); } catch (Throwable t) {}
            try { if (out != null) out.close(); } catch (Throwable t) {}
        }
    }

    /** 撤回时微信只删缩略图；若大图本地没有，就用缩略图补一份，避免点开提示「图片已过期」 */
    /** 旧行为：立刻用缩略图冒充大图（现在的默认不用，文件 recall.blur.on 才走这条） */
    static void synthBigImage(java.io.File thumb) {
        long dt = System.currentTimeMillis() - LAST_REVOKE_AT;
        if (LAST_REVOKE_AT == 0 || dt < 0 || dt > 20000) return;
        copyThumb(thumb);
    }

    /** 撤回窗口内被删的是 th_ 缩略图：先把高清的机会留给微信自己下载，90 秒还没有才补位 */
    static void scheduleSynth(final java.io.File thumb) {
        long dt = System.currentTimeMillis() - LAST_REVOKE_AT;
        if (LAST_REVOKE_AT == 0 || dt < 0 || dt > 20000) return;
        final String n = thumb.getName();
        if (!n.startsWith("th_")) return;
        if (new java.io.File(WXDIR + "recall.blur.on").exists()) { synthBigImage(thumb); return; }
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(90000);
                    String hash = n.substring(3);
                    if (hash.endsWith("_tmp.jpg")) hash = hash.substring(0, hash.length() - 8);
                    if (hash.length() < 16) return;
                    java.io.File dir = thumb.getParentFile();
                    if (dir == null) return;
                    java.io.File big = new java.io.File(dir, hash + ".jpg");
                    if (big.exists() && big.length() > 0) { T("synth skip(hd arrived) " + big.getName()); return; }
                    copyThumb(thumb);
                } catch (Throwable t) { T("scheduleSynth err " + t); }
            }
        }).start();
    }

    static void copyThumb(java.io.File thumb) {
        String n = thumb.getName();
        if (!n.startsWith("th_")) return;
        String hash = n.substring(3);
        if (hash.endsWith("_tmp.jpg")) hash = hash.substring(0, hash.length() - 8);
        if (hash.length() < 16) return;
        if (!thumb.exists() || thumb.length() <= 0) return;
        java.io.File dir = thumb.getParentFile();
        if (dir == null) return;
        java.io.File big = new java.io.File(dir, hash + ".jpg");
        if (big.exists() && big.length() > 0) return;
        java.io.FileInputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = new java.io.FileInputStream(thumb);
            out = new java.io.FileOutputStream(big);
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            T("synth big " + big.getName() + " from thumb " + thumb.length());
        } catch (Throwable t) {
            T("synth fail " + t);
        } finally {
            try { if (in != null) in.close(); } catch (Throwable t) {}
            try { if (out != null) out.close(); } catch (Throwable t) {}
        }
    }

    // ===== 强制开启「聊天图片自动下载」（微信 8.0.80 已无 UI 开关，由 AB 配置控制）=====
    static int ADB = 0;
    static int ADB2 = 0;
    // ===== 实验：强制打开「聊天图片自动下载」配置 =====
    static int CFG_LOG = 0;

    // ===== 朋友圈抓取（探针/备份）=====
    static int SNS_N = 0;

    static void hookSns(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("com.tencent.mm.plugin.sns.storage.SnsInfo", cl);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (SNS_N > 400) return;
                        if (p.args == null || p.args.length < 1 || !(p.args[0] instanceof String)) return;
                        String raw = (String) p.args[0];
                        if (raw == null || raw.length() < 20) return;
                        SNS_N++;
                        java.io.File dir = new java.io.File(WXDIR + "sns");
                        if (!dir.exists()) dir.mkdirs();
                        String name = System.currentTimeMillis() + "_" + SNS_N + ".txt";
                        java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(dir, name));
                        fo.write(raw.getBytes("UTF-8"));
                        fo.close();
                        if (SNS_N <= 5) T("sns captured #" + SNS_N + " len=" + raw.length() + " " + name);
                    } catch (Throwable t) {}
                }
            };
            int n = 0;
            StringBuilder sb = new StringBuilder("SnsInfo decl:");
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                sb.append(' ').append(m.getName());
                if (m.getName().equals("fromJson") || m.getName().toLowerCase().contains("json")
                        || m.getName().equals("parse") || m.getName().equals("parseXml")) {
                    try { XposedBridge.hookMethod(m, h); n++; } catch (Throwable t2) {}
                }
            }
            T("sns hook ok x" + n);
        } catch (Throwable t) { T("sns hook fail " + t); }
    }

    static void hookCfgForce(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("rp.j", cl);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (p.args.length < 1 || !(p.args[0] instanceof String)) return;
                        String key = (String) p.args[0];
                        if (key.indexOf("ImgAutoDownload") < 0 && key.indexOf("ChatImgAutoDownload") < 0) return;
                        if (!retIs(p, boolean.class)) { if (CFG_LOG < 8) { CFG_LOG++; T("cfg skip non-boolean " + key); } return; }
                        if (CFG_LOG < 8) { CFG_LOG++; T("cfg " + key + " old=" + p.getResult()); }
                        p.setResult(Boolean.TRUE);
                    } catch (Throwable t) {}
                }
            };
            XposedBridge.hookAllMethods(k, "e", h);
            T("cfg force hook ok");
        } catch (Throwable t) { T("cfg hook fail " + t); }
    }

    static void hookAutoDownload(ClassLoader cl) {
        try {
            Class<?> k = XposedHelpers.findClass("sy3.a", cl);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (new java.io.File(WXDIR + "autodl.off").exists()) return;
                        if (!retIs(p, boolean.class)) return;
                        Object r = p.getResult();
                        if (r instanceof Boolean && !((Boolean) r).booleanValue()) p.setResult(Boolean.TRUE);
                    } catch (Throwable t) {}
                }
            };
            XposedBridge.hookAllMethods(k, "a", h);
            XposedBridge.hookAllMethods(k, "b", h);
            T("autodl force hook ok");

        } catch (Throwable t) { T("autodl hook fail " + t); }
    }

    // v186: silent image downloads use ImageRecallRuntime and the native queue.

    // ===== 图片下载探针：抓「谁在写 image2/*.jpg」的调用栈 =====
    // 开着 img.probe 文件时才记录，用来定位微信的图片下载入口。
    static final java.io.File IMG_PROBE = new java.io.File(WXDIR + "img.probe");
    static final java.util.HashSet<String> IMG_PROBE_SEEN = new java.util.HashSet<String>();

    // ===== 看图下载入口探针：com.tencent.mm.ui.chatting.gallery.d3 =====
    static final java.io.File D3_PROBE = new java.io.File(WXDIR + "dl.probe");

    static String argStr(Object a) {
        try {
            if (a == null) return "null";
            if (a instanceof String) { String v = (String) a; return v.length() > 60 ? v.substring(0, 60) + ".." : v; }
            if (a instanceof Number || a instanceof Boolean) return String.valueOf(a);
            return a.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(a));
        } catch (Throwable t) { return "?"; }
    }

    static void hookD3(ClassLoader cl) {
        try {
            final Class<?> k = XposedHelpers.findClass("com.tencent.mm.ui.chatting.gallery.d3", cl);
            int n = 0;
            for (final java.lang.reflect.Method m : k.getDeclaredMethods()) {
                String mn = m.getName();
                if (mn.startsWith("on")) continue;   // 跳过回调，减少噪音
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            try {
                                if (!D3_PROBE.exists()) return;
                                StringBuilder sb = new StringBuilder();
                                for (Object a : p.args) sb.append(argStr(a)).append(" | ");
                                T("D3 " + p.method.getName() + " this=" + Integer.toHexString(System.identityHashCode(p.thisObject)) + " args=" + sb);
                            } catch (Throwable t) {}
                        }
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                if (!D3_PROBE.exists()) return;
                                if (p.getResult() != null && !(p.getResult() instanceof Boolean))
                                    T("D3 " + p.method.getName() + " ret=" + argStr(p.getResult()));
                            } catch (Throwable t) {}
                        }
                    });
                    n++;
                } catch (Throwable t) {}
            }
            T("d3 probe hooks " + n);
            // 下载任务：u3.a()/b()、s3.run() 里才是真正的下载，抓深栈
            try {
                Class<?> u3 = XposedHelpers.findClass("com.tencent.mm.ui.chatting.gallery.u3", cl);
                XC_MethodHook th = new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (!D3_PROBE.exists()) return;
                            StringBuilder sb = new StringBuilder();
                            for (Object a : p.args) sb.append(argStr(a)).append(" | ");
                            T("U3 " + p.method.getName() + " this=" + Integer.toHexString(System.identityHashCode(p.thisObject)) + " args=" + sb);
                            T("U3STACK " + deepCaller(2, 14));
                        } catch (Throwable t) {}
                    }
                };
                int un = 0;
                for (java.lang.reflect.Method m : u3.getDeclaredMethods()) {
                    String mn = m.getName();
                    if (!(mn.equals("a") || mn.equals("b") || mn.equals("run"))) continue;
                    try { XposedBridge.hookMethod(m, th); un++; } catch (Throwable t) {}
                }
                T("u3 hooks " + un);
            } catch (Throwable t) { T("u3 hook fail " + t); }
        } catch (Throwable t) { T("d3 probe fail " + t); }
    }

    static String deepCaller(int from, int n) {
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < st.length && i < from + n; i++) {
                sb.append(st[i].getClassName()).append('.').append(st[i].getMethodName()).append('<');
            }
            return sb.toString();
        } catch (Throwable t) { return "?"; }
    }

    static void hookImgProbe(ClassLoader cl) {
        try {
            XC_MethodHook fh = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (!IMG_PROBE.exists()) return;
                        Object a0 = p.args.length > 0 ? p.args[0] : null;
                        String path = null;
                        if (a0 instanceof String) path = (String) a0;
                        else if (a0 instanceof java.io.File) path = ((java.io.File) a0).getAbsolutePath();
                        if (path == null) return;
                        if (path.indexOf("/image2/") < 0 && path.indexOf("/image/") < 0) return;
                        if (!path.endsWith(".jpg") && !path.endsWith(".png")) return;
                        synchronized (IMG_PROBE_SEEN) {
                            if (IMG_PROBE_SEEN.size() > 200) IMG_PROBE_SEEN.clear();
                            if (!IMG_PROBE_SEEN.add(path)) return;
                        }
                        T("IMGWRITE " + path + " <- " + deepCaller(4, 22));
                    } catch (Throwable t) {}
                }
            };
            int n = 0;
            for (java.lang.reflect.Constructor<?> c : java.io.FileOutputStream.class.getDeclaredConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 0) continue;
                if (ps[0] != String.class && ps[0] != java.io.File.class) continue;
                try { XposedBridge.hookMethod(c, fh); n++; } catch (Throwable t) {}
            }
            T("imgprobe hook ok x" + n);
        } catch (Throwable t) { T("imgprobe hook fail " + t); }
    }

    static void hookFileDelete(ClassLoader cl) {
        try {
            XposedBridge.hookAllMethods(java.io.File.class, "delete", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (MODULE_OFF) return;              // 1.63：被停用后不再改任何删除行为
                        if (!arOn()) return;
                        java.io.File f = (java.io.File) p.thisObject;
                        if (f.isDirectory()) return;         // 1.63：目录不拦
                        String path = f.getAbsolutePath();
                        if (path == null || path.indexOf("MicroMsg/") < 0) return;
                        // 1.63（F07）：只在「刚发生撤回」的窗口里保护文件。
                        // 以前对最近 7 天的所有媒体文件无条件 setResult(true)，
                        // 用户自己清理缓存会提示成功、文件却还在，空间也不释放。
                        // v151（S10）：用户自己清理时留一条明确 bypass
                        if (new java.io.File(WXDIR + "del.bypass").exists()) return;
                        boolean matched = recallMatches(path);
                        boolean kept = new java.io.File(WXDIR + "recall/" + f.getName()).exists();
                        // 只保护「确实属于某条撤回消息的文件」；已存副本仅在撤回窗口内兜底，
                        // 不再对窗口内所有 image/video 一律放行删除失败。
                        if (!matched && !(kept && inRecallWindow())) return;
                        long lm = f.lastModified();
                        if (lm <= 0) return;
                        if (System.currentTimeMillis() - lm > 7L * 24 * 3600 * 1000) return;
                        if (!retIs(p, boolean.class)) return;
                        p.setResult(Boolean.TRUE);
                        if (DEL_KEEP < 8) { DEL_KEEP++; if (VB()) T("keep file " + path); }
                        try { scheduleSynth(f); } catch (Throwable t2) {}
                        try { stash(f); } catch (Throwable t2) {}
                    } catch (Throwable t) {}
                }
            });
            T("file delete hook ok");
        } catch (Throwable t) { T("file delete hook fail " + t); }
    }


    static void hookPopupContent() {
        try {
            XposedBridge.hookAllMethods(android.widget.PopupWindow.class, "setContentView", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        if (p.args == null || p.args.length == 0) return;
                        Object o = p.args[0];
                        if (!(o instanceof android.view.ViewGroup)) return;
                        final android.view.ViewGroup v = (android.view.ViewGroup) o;
                        retryPanelEntry(v,0);
                    } catch (Throwable t) { T("popup hook err " + t); }
                }
            });
            T("popup content hook ok");
        } catch (Throwable t) { T("popup content hook fail " + t); }
    }

    static boolean ownsPlusListener(android.view.View view) {
        try {
            Object info = XposedHelpers.getObjectField(view, "mListenerInfo");
            if (info == null) return false;
            Object listener = XposedHelpers.getObjectField(info, "mOnLongClickListener");
            return listener != null && listener.getClass().getClassLoader() == Kernel.class.getClassLoader()
                && listener.getClass().getName().startsWith(Kernel.class.getName() + "$");
        } catch (Throwable unavailable) { return false; }
    }

    static void attachPlusLongClick(final android.app.Activity act) {
        try {
            final android.view.ViewGroup root = (android.view.ViewGroup) act.getWindow().getDecorView();
            root.post(new Runnable() {
                public void run() {
                    try {
                        android.view.View v = findPlus(root);
                        if (v == null) { T("plus button not found"); return; }
                        // 已经挂在这个 View 上就静默跳过，避免巡检刷屏
                        java.lang.ref.WeakReference<android.view.View> ref = LAST_PLUS_VIEW;
                        boolean sameView = ref != null && ref.get() == v;
                        if (sameView && ownsPlusListener(v)) return;
                        LAST_PLUS_VIEW = new java.lang.ref.WeakReference<android.view.View>(v);
                        if (!sameView) try {
                            v.setOnTouchListener(new android.view.View.OnTouchListener() {
                                public boolean onTouch(android.view.View view, android.view.MotionEvent e) {
                                    try {
                                        if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                                            new Thread(new Runnable() {
                                                public void run() {
                                                    // 面板弹出的头 1.5 秒里密集扫描，避免用户看到「先四个、后补一行」
                                                    for (int k = 0; k < 12; k++) {
                                                        try { Thread.sleep(120); } catch (Throwable t) { return; }
                                                        try { act.runOnUiThread(new Runnable() { public void run() { try { probePlusPanel(act); } catch (Throwable t) {} } }); } catch (Throwable t) {}
                                                    }
                                                }
                                            }).start();
                                        }
                                    } catch (Throwable t) {}
                                    return false;   // 不拦截，微信自己的面板照常弹
                                }
                            });
                        } catch (Throwable t) { T("plus touch err " + t); }
                        v.setLongClickable(true);
                        v.setOnLongClickListener(new android.view.View.OnLongClickListener() {
                            public boolean onLongClick(android.view.View view) {
                                T("plus long-click");
                                showFeatureMenu(act);
                                return true;
                            }
                        });
                        T("plus long-click attached: " + v.getClass().getName());
                    } catch (Throwable t) { T("plus attach err " + t); }
                }
            });
        } catch (Throwable t) { T("plus hook err " + t); }
    }

    static final java.util.WeakHashMap<android.view.View, String> PANEL_SEEN =
            new java.util.WeakHashMap<android.view.View, String>();

    static void logWindowTypes() {
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object inst = wmg.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field f = wmg.getDeclaredField("mViews");
            f.setAccessible(true);
            java.util.List all = (java.util.List) f.get(inst);
            StringBuilder sb = new StringBuilder("win scan n=");
            sb.append(all == null ? -1 : all.size());
            sb.append(" act=").append(CUR_ACT != null && CUR_ACT.get() != null ? 1 : 0).append(" t=");
            if (all != null) {
                Object[] arr = all.toArray();
                for (int i = 0; i < arr.length && i < 12; i++) {
                    android.view.View v = (android.view.View) arr[i];
                    if (v == null) { sb.append("null,"); continue; }
                    try {
                        Object lp = XposedHelpers.callMethod(v, "getLayoutParams");
                        if (lp == null) { sb.append("nolp,"); continue; }
                        sb.append(XposedHelpers.getObjectField(lp, "type")).append(',');
                    } catch (Throwable t) { sb.append("err,"); }
                }
            }
            T(sb.toString());
        } catch (Throwable t) { T("win scan err " + t); }
    }

    /** 取当前应用里 type=TYPE_APPLICATION_PANEL 的窗口根 View。 */
    static java.util.List<android.view.View> panelWindows() {
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object inst = wmg.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field f = wmg.getDeclaredField("mViews");
            f.setAccessible(true);
            java.util.List all = (java.util.List) f.get(inst);
            if (all == null) return null;
            Object[] arr = all.toArray();
            java.util.ArrayList<android.view.View> out = new java.util.ArrayList<android.view.View>();
            for (int i = 0; i < arr.length; i++) {
                android.view.View v = (android.view.View) arr[i];
                if (v == null) continue;
                Object lp = XposedHelpers.callMethod(v, "getLayoutParams");
                if (lp == null || !lp.getClass().getName().equals("android.view.WindowManager$LayoutParams")) continue;
                int wtype = ((Integer) XposedHelpers.getObjectField(lp, "type")).intValue();
                if (wtype == 1000 || wtype == 1001) out.add(v);   // TYPE_APPLICATION_PANEL / ATTACHED_DIALOG
            }
            return out;
        } catch (Throwable t) { return null; }
    }

    static final boolean DBG_PANEL = new java.io.File(computeWxdir() + "panel.debug").exists();

    /** 1.73：+ 面板（PopupWindow）出现时，把模块入口插进去。 */
    static void probePlusPanel(final android.app.Activity act) {
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object inst = wmg.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field f = wmg.getDeclaredField("mViews");
            f.setAccessible(true);
            java.util.List views = (java.util.List) f.get(inst);
            if (views == null) return;
            Object[] arr = views.toArray();
            for (int i = 0; i < arr.length; i++) {
                android.view.View v = (android.view.View) arr[i];
                if (v == null) continue;
                Object lp = XposedHelpers.callMethod(v, "getLayoutParams");
                if (lp == null || !lp.getClass().getName().equals("android.view.WindowManager$LayoutParams")) continue;
                int wtype = ((Integer) XposedHelpers.getObjectField(lp, "type")).intValue();
                if (wtype != 1000 && wtype != 1001) continue;      // TYPE_APPLICATION_PANEL
                int ww = ((Integer) XposedHelpers.getObjectField(lp, "width")).intValue();
                int wh = ((Integer) XposedHelpers.getObjectField(lp, "height")).intValue();
                if (hasOurBox(v,0)) continue;
                PANEL_SEEN.put(v, "1");
                if (DBG_PANEL) { T("panel root " + v.getClass().getName() + " " + ww + "x" + wh); dumpPanelTree(v, 0); }
                try { injectPanelEntry(v); } catch (Throwable t) { T("panel inject err " + t); }
            }
        } catch (Throwable t) { T("panel probe err " + t); }
    }

    static void dumpPanelTree(android.view.View v, int depth) {
        try {
            if (depth > 6) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(v.getClass().getSimpleName()).append(' ').append(v.getWidth()).append('x').append(v.getHeight());
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            sb.append(" @").append(loc[1]);
            CharSequence d = v.getContentDescription();
            if (d != null) sb.append(" d=").append(d);
            T("panel| " + sb);
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount() && i < 12; i++) dumpPanelTree(g.getChildAt(i), depth + 1);
            }
        } catch (Throwable t) {}
    }

    static android.app.Activity activityOf(android.content.Context c) {
        try {
            int guard = 0;
            while (c != null && guard++ < 10) {
                if (c instanceof android.app.Activity) return (android.app.Activity) c;
                Object base = null;
                try { base = XposedHelpers.callMethod(c, "getBaseContext"); } catch (Throwable t) { return null; }
                if (!(base instanceof android.content.Context)) return null;
                c = (android.content.Context) base;
            }
        } catch (Throwable t) {}
        return null;
    }

    static boolean isAdapterView(android.view.View v) {
        try {
            Class<?> c = v.getClass();
            while (c != null) {
                if ("android.widget.AdapterView".equals(c.getName())) return true;
                c = c.getSuperclass();
            }
        } catch (Throwable t) {}
        return false;
    }

    static android.view.ViewGroup pickPanelContainer(android.view.ViewGroup g, int depth) {
        try {
            if (depth > 4) return null;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (isAdapterView(g.getChildAt(i))) return g;      // 网格的父容器
            }
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View c = g.getChildAt(i);
                if (c instanceof android.view.ViewGroup) {
                    android.view.ViewGroup r = pickPanelContainer((android.view.ViewGroup) c, depth + 1);
                    if (r != null) return r;
                }
            }
        } catch (Throwable t) {}
        return null;
    }



    static final java.util.WeakHashMap<android.view.View, String> PANEL_INJECTED =
            new java.util.WeakHashMap<android.view.View, String>();

    /** 白色线稿「小波」：歪一点的天线小环 + 圆脸 + 大眼睛 + 实心大笑脸。 */
    static android.graphics.Bitmap makeWxIcon(int sz) {
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
            float k = sz / 26f * 0.80f;          // 字形留白跟原生图标一致
            float o = (sz - 26f * k) / 2f;
            android.graphics.Paint st = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            st.setColor(0xFFFFFFFF);
            st.setAntiAlias(true);
            st.setStyle(android.graphics.Paint.Style.STROKE);
            st.setStrokeWidth(1.7f * k);
            android.graphics.Paint fl = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            fl.setColor(0xFFFFFFFF);
            fl.setAntiAlias(true);
            fl.setStyle(android.graphics.Paint.Style.FILL);

            // 天线：小环 + 略向右倾的短杆
            cv.drawCircle(13.2f * k + o, 4.0f * k + o, 1.7f * k, st);
            cv.drawLine(13.0f * k + o, 7.75f * k + o, 13.15f * k + o, 5.7f * k + o, st);

            // 圆脸（略扁一点，和原生图标同一量级）
            android.graphics.RectF head = new android.graphics.RectF(5.0f * k + o, 7.6f * k + o,
                    21.0f * k + o, 21.6f * k + o);
            cv.drawOval(head, st);

            // 大眼睛
            cv.drawCircle(10.2f * k + o, 12.7f * k + o, 1.15f * k, fl);
            cv.drawCircle(15.8f * k + o, 12.7f * k + o, 1.15f * k, fl);

            // 实心大笑脸
            android.graphics.RectF mouth = new android.graphics.RectF(9.7f * k + o, 15.3f * k + o,
                    16.3f * k + o, 19.4f * k + o);
            cv.drawArc(mouth, 8f, 164f, true, fl);
            return bmp;
        } catch (Throwable t) { T("icon draw err " + t); return null; }
    }

    static void setSize(android.view.View v, int w, int h) {
        try {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp == null) return;
            lp.width = w;
            lp.height = h;
            v.setLayoutParams(lp);
        } catch (Throwable t) { T("setSize err " + t); }
    }

    /** 改左边距（间隔线用），LayoutParams 不是 Margin 类型就忽略。 */
    static void setMarginLeft(android.view.View v, int ml) {
        try {
            Object lp = v.getLayoutParams();
            if (lp == null) return;
            XposedHelpers.callMethod(lp, "setMargins", Integer.valueOf(ml), Integer.valueOf(0),
                    Integer.valueOf(0), Integer.valueOf(0));
            v.setLayoutParams((android.view.ViewGroup.LayoutParams) lp);
        } catch (Throwable t) { T("setMargin err " + t); }
    }

    static android.view.View findByType(android.view.View v, int type) {
        if (type == 1 && v instanceof android.widget.TextView) return v;
        if (type == 2 && v instanceof android.widget.ImageView) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View r = findByType(g.getChildAt(i), type);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** 量一个原生面板项：{行高, 图标左, 图标宽, 图标高, 文字左, 文字 px, 文字色}，未布局好返回 null。 */
    static int[] panelMetrics(android.view.View grid) {
        try {
            if (!(grid instanceof android.view.ViewGroup)) return null;
            android.view.ViewGroup g = (android.view.ViewGroup) grid;
            if (g.getChildCount() == 0) return null;
            android.view.View item = g.getChildAt(0);
            int ih = item.getHeight();
            if (ih <= 0) return null;
            android.view.View icon = findByType(item, 2);
            android.view.View text = findByType(item, 1);
            if (icon == null || text == null) return null;
            if (icon.getWidth() <= 0 || icon.getHeight() <= 0 || text.getWidth() <= 0) return null;
            int[] a = new int[2];
            int[] b = new int[2];
            item.getLocationInWindow(a);
            icon.getLocationInWindow(b);
            int iconLeft = b[0] - a[0];
            text.getLocationInWindow(b);
            int textLeft = b[0] - a[0];
            if (iconLeft <= 0 || textLeft <= iconLeft) return null;
            android.widget.TextView t = (android.widget.TextView) text;
            return new int[]{ ih, iconLeft, icon.getWidth(), icon.getHeight(), textLeft,
                    Math.round(t.getTextSize()), t.getCurrentTextColor() };
        } catch (Throwable t) { return null; }
    }

    /** 原生项布局好之后，把我们的行/图标/文字/间隔线对齐到它的真实尺寸。 */
    static final String PANEL_TAG = "wxentry.panel";

    /** 我们的行是不是已经套在这个网格外面了（看网格父容器的 tag，不受容器实例变化影响）。 */
    static boolean panelBoxAlive(android.view.View grid) {
        Object parent=grid.getParent();return parent instanceof android.view.View&&PANEL_TAG.equals(((android.view.View)parent).getTag())&&PlusPanelLayout.present((android.view.View)parent);
    }

    /** 递归找带我们 tag 的视图（窗口内容可能被重建，不能靠实例比较）。 */
    static boolean hasOurBox(android.view.View view,int depth) { return PlusPanelLayout.present(view); }

    static void retryPanelEntry(final android.view.View root,final int attempt) {
        if(root==null||attempt>12)return;
        root.post(new Runnable(){public void run(){
            if(MODULE_OFF)return;
            try { injectPanelEntry(root);if(hasOurBox(root,0)){PlusPanelLayout.reveal(root);expandPanelWindow(root);return;} }
            catch(Throwable failure){T("panel retry "+failure.getClass().getSimpleName());}
            if(attempt<12)root.postDelayed(new Runnable(){public void run(){retryPanelEntry(root,attempt+1);}},75);
        }});
    }

    static final java.util.WeakHashMap<android.view.View, String> PANEL_HOSTS =
            new java.util.WeakHashMap<android.view.View, String>();

    /** 面板 Show 之前就把窗口高度改成 WRAP_CONTENT，第一帧就是 5 项全展开，不会先 4 项再跳。 */
    static void hookPopupShow() {
        try {
            XC_MethodHook cb = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    try {
                        Object pw = p.thisObject;
                        Object cv = XposedHelpers.callMethod(pw, "getContentView");
                        if (!(cv instanceof android.view.View)) return;
                        android.view.View cvv = (android.view.View) cv;
                        // v209：面板有时是「先 setContentView、后填数据」，显示前再补一次注入
                        try {
                            retryPanelEntry(cvv,0);
                        } catch (Throwable t) {}
                        if (!hasOurBox(cvv, 0)) return;
                        PlusPanelLayout.reveal(cvv);expandPanelWindow(cvv);
                    } catch (Throwable t) { T("panel show err " + t); }
                }
            };
            XposedBridge.hookAllMethods(android.widget.PopupWindow.class, "showAtLocation", cb);
            XposedBridge.hookAllMethods(android.widget.PopupWindow.class, "showAsDropDown", cb);
            T("popup show hook ok");
        } catch (Throwable t) { T("popup show hook err " + t); }
    }

    static final java.util.WeakHashMap<android.view.View, String> PANEL_EXPANDED =
            new java.util.WeakHashMap<android.view.View, String>();

    /** 递归找面板里的网格（AdapterView）。 */
    static android.view.View findGrid(android.view.View v, int depth) {
        if (v == null || depth > 8) return null;
        if (isAdapterView(v)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View r = findGrid(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    static int adapterCount(android.view.View grid) {
        try {
            Object ad = XposedHelpers.callMethod(grid, "getAdapter");
            Object c = XposedHelpers.callMethod(ad, "getCount");
            return ((Integer) c).intValue();
        } catch (Throwable t) {
            return grid instanceof android.view.ViewGroup ? ((android.view.ViewGroup) grid).getChildCount() : 0;
        }
    }

    /** 我们插了一行之后窗口该有多高：行 + 间隔线 + 全部原生项 + 上下内边距。 */
    static int calcPanelHeight(android.view.View content, android.view.View grid) {
        int n = adapterCount(grid);
        if (n <= 0) n = 4;
        int pad = 18;
        try {
            int pt = ((Integer) XposedHelpers.callMethod(content, "getPaddingTop")).intValue();
            int pb = ((Integer) XposedHelpers.callMethod(content, "getPaddingBottom")).intValue();
            if (pt + pb > 0) pad = pt + pb;
        } catch (Throwable t) {}
        return LAST_ROW_H + 1 + n * LAST_ITEM_H + pad;
    }

    /** 兜底：窗口已经显示出来了但高度还被微信按 4 项卡着，就按内容直接改窗口高度。 */
    static void expandPanelWindow(android.view.View any) {
        try {
            android.view.View root = any;
            for (int i = 0; i < 12; i++) {
                Object p = root.getParent();
                if (!(p instanceof android.view.View)) break;
                root = (android.view.View) p;
            }
            Object lp = XposedHelpers.callMethod(root, "getLayoutParams");
            if (lp == null) return;
            android.view.View grid = findGrid(root, 0);
            if (grid == null) return;
            int need = calcPanelHeight(root, grid);
            int h = XposedHelpers.getIntField(lp, "height");
            if (h == need) return;
            XposedHelpers.setIntField(lp, "height", need);
            Object ctx = XposedHelpers.callMethod(root, "getContext");
            Object wm = XposedHelpers.callMethod(ctx, "getSystemService", "window");
            XposedHelpers.callMethod(wm, "updateViewLayout", root, lp);
            if (!PANEL_FIT_LOGGED) { PANEL_FIT_LOGGED = true; T("panel window fit " + h + " -> " + need); }
        } catch (Throwable t) { T("panel window err " + t); }
    }

    static boolean PANEL_LOGGED = false;
    static volatile int LAST_ROW_H = 152;
    static volatile int LAST_ITEM_H = 152;
    static boolean PANEL_FIT_LOGGED = false;

    /** 把原生项的涟漪背景复制一份给我们的行，点下去有同样的按压反馈。 */
    static void applyNativeBg(android.view.View grid, android.view.View row) {
        try {
            if (!(grid instanceof android.view.ViewGroup)) return;
            android.view.ViewGroup g = (android.view.ViewGroup) grid;
            if (g.getChildCount() == 0) return;
            android.view.View proto = g.getChildAt(0);
            Object bg = XposedHelpers.callMethod(proto, "getBackground");
            if (bg == null) return;
            Object cs = XposedHelpers.callMethod(bg, "getConstantState");
            if (cs == null) return;
            Object d = XposedHelpers.callMethod(cs, "newDrawable");
            if (d == null) return;
            try { XposedHelpers.callMethod(d, "mutate"); } catch (Throwable t) {}
            XposedHelpers.callMethod(row, "setBackground", d);
        } catch (Throwable t) { T("bg clone err " + t); }
    }

    static void refinePanel(final android.view.View grid, final android.widget.LinearLayout row,
                            final android.widget.ImageView iv, final android.widget.TextView tv,
                            final android.view.View divider, final int tries) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    int[] m = panelMetrics(grid);
                    if (m == null) {
                        if (tries < 30) row.postDelayed(this, 50);
                        return;
                    }
                    int rowH = m[0], iconLf = m[1], iconPx = m[2], textLf = m[4];
                    LAST_ITEM_H = rowH;
                    int gap = Math.max(0, textLf - iconLf - iconPx);
                    row.setPadding(iconLf, 0, row.getPaddingRight(), 0);
                    setSize(row, -1, rowH);
                    setSize(iv, iconPx, iconPx);
                    android.graphics.Bitmap bm = makeWxIcon(iconPx);
                    if (bm != null) iv.setImageBitmap(bm);
                    tv.setPadding(gap, 0, 0, 0);
                    try { tv.setTextSize(0, (float) m[5]); } catch (Throwable t) {}
                    setMarginLeft(divider, textLf);
                    applyNativeBg(grid, row);
                    expandPanelWindow(row);
                    if (!PANEL_LOGGED) { PANEL_LOGGED = true;
                        T("panel refine rowH=" + rowH + " icon=" + iconPx + " at=" + iconLf + " txt=" + textLf + " sz=" + m[5]); }
                } catch (Throwable t) { T("panel refine err " + t); }
            }
        };
        row.postDelayed(r, 40);
    }

    /** v209：只认「＋」面板 —— 聊天列表长按菜单也是 PopupWindow + 列表，不能往里塞入口。
     *  hits[0]=命中「＋」面板特征项数，hits[1]<0 表示命中聊天菜单特征（明确排除）。 */
    static final boolean[] PANEL_SKIP_LOGGED = new boolean[]{false};

    static boolean looksLikePlusPanel(android.view.View v, int depth, int[] hits) {
        if (v == null || depth > 8) return hits[0] > 0;
        try {
            if (v instanceof android.widget.TextView) {
                CharSequence cs = ((android.widget.TextView) v).getText();
                String s = cs == null ? "" : cs.toString().trim();
                if (s.length() > 0) {
                    if (s.equals("\u626b\u4e00\u626b") || s.equals("\u6536\u4ed8\u6b3e")
                            || s.equals("\u53d1\u8d77\u7fa4\u804a") || s.equals("\u6dfb\u52a0\u670b\u53cb")
                            || s.equals("\u6dfb\u52a0\u597d\u53cb")) { hits[0]++; return true; }
                    if (s.equals("\u6807\u4e3a\u5df2\u8bfb") || s.equals("\u7f6e\u9876\u8be5\u804a\u5929")
                            || s.equals("\u4e0d\u663e\u793a\u8be5\u804a\u5929") || s.equals("\u5220\u9664\u8be5\u804a\u5929")
                            || s.equals("\u6807\u8bb0\u672a\u8bfb") || s.equals("\u5173\u95ed\u8be5\u804a\u5929")) {
                        hits[1] = -100;
                    }
                }
            }
        } catch (Throwable t) {}
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) looksLikePlusPanel(g.getChildAt(i), depth + 1, hits);
        }
        return hits[0] > 0;
    }

    static void injectPanelEntry(final android.view.View root) {
        if(root==null||MODULE_OFF)return;
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()){root.post(new Runnable(){public void run(){injectPanelEntry(root);}});return;}
        int[] gate = new int[]{0, 0};
        try { looksLikePlusPanel(root, 0, gate); } catch (Throwable t) {}
        if (gate[0] <= 0 || gate[1] < 0) {
            if (gate[1] < 0 && !PANEL_SKIP_LOGGED[0]) { PANEL_SKIP_LOGGED[0] = true; T("panel skip: 聊天长按菜单"); }
            return;
        }
        if (!(root instanceof android.view.ViewGroup)) return;
        if (hasOurBox(root,0)) { PlusPanelLayout.reveal(root);return; }
        android.view.ViewGroup container = (android.view.ViewGroup) root;
        android.view.View grid = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            if (isAdapterView(container.getChildAt(i))) { grid = container.getChildAt(i); break; }
        }
        if (grid == null) {
            android.view.ViewGroup pc = pickPanelContainer(container, 0);
            if (pc == null) return;
            container = pc;
            for (int i = 0; i < container.getChildCount(); i++) {
                if (isAdapterView(container.getChildAt(i))) { grid = container.getChildAt(i); break; }
            }
        }
        if (grid == null) return;
        if (panelBoxAlive(grid)) return;                         // 网格外已经套着我们的行

        android.content.Context c0 = null;
        try { c0 = (android.content.Context) XposedHelpers.callMethod(container, "getContext"); } catch (Throwable t) {}
        final android.app.Activity act = activityOf(c0);
        if (act == null) { T("panel inject deferred: no activity");return; }
        final android.view.ViewGroup fc = container;
        final android.view.View fg = grid;
        act.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if(hasOurBox(fc,0)||panelBoxAlive(fg)||fg.getParent()!=fc)return;
                    float d = act.getResources().getDisplayMetrics().density;
                    int[] nm = panelMetrics(fg);
                    int rowPx   = nm != null ? nm[0] : Math.round(56.4f * d);
                    int iconLf  = nm != null ? nm[1] : Math.round(21.5f * d);
                    int iconPx  = nm != null ? nm[2] : Math.round(20.8f * d);
                    int textLf  = nm != null ? nm[4] : Math.round(56.4f * d);
                    float txtPx = nm != null ? (float) nm[5] : (16f * d);
                    int txtCol  = nm != null ? nm[6] : 0xFFFFFFFF;
                    int gapPx   = Math.max(0, textLf - iconLf - iconPx);

                    android.widget.LinearLayout row = new android.widget.LinearLayout(act);
                    XposedHelpers.callMethod(row, "setOrientation", Integer.valueOf(0));
                    try { XposedHelpers.callMethod(row, "setGravity", Integer.valueOf(0x10)); } catch (Throwable t) {}
                    row.setPadding(iconLf, 0, Math.max(0, Math.round(10 * d)), 0);

                    android.widget.ImageView iv = new android.widget.ImageView(act);
                    android.graphics.Bitmap bmp = makeWxIcon(iconPx);
                    if (bmp != null) XposedHelpers.callMethod(iv, "setImageBitmap", bmp);

                    android.widget.TextView tv = new android.widget.TextView(act);
                    tv.setText("\u5fae\u4fe1\u589e\u5f3a");
                    try { tv.setTextSize(0, txtPx); } catch (Throwable t) { tv.setTextSize(16); }
                    try { tv.setTextColor(txtCol); } catch (Throwable t) {}
                    tv.setPadding(gapPx, 0, 0, 0);
                    try { XposedHelpers.callMethod(tv, "setSingleLine", Boolean.TRUE); } catch (Throwable t) {}

                    Object lpIcon = XposedHelpers.newInstance(android.view.ViewGroup.LayoutParams.class,
                            Integer.valueOf(iconPx), Integer.valueOf(iconPx));
                    Object lpText = XposedHelpers.newInstance(android.view.ViewGroup.LayoutParams.class,
                            Integer.valueOf(-2), Integer.valueOf(-2));
                    Object lpRow = XposedHelpers.newInstance(android.view.ViewGroup.LayoutParams.class,
                            Integer.valueOf(-1), Integer.valueOf(rowPx));
                    Object lpWrap = XposedHelpers.newInstance(android.view.ViewGroup.LayoutParams.class,
                            Integer.valueOf(-1), Integer.valueOf(-2));

                    XposedHelpers.callMethod(row, "addView", iv, lpIcon);
                    XposedHelpers.callMethod(row, "addView", tv, lpText);
                    row.setOnClickListener(new android.view.View.OnClickListener() {
                        public void onClick(android.view.View v) {
                            try { showFeatureMenu(act); } catch (Throwable t) { T("panel click err " + t); }
                        }
                    });

                    android.view.View divider = new android.view.View(act);
                    try { XposedHelpers.callMethod(divider, "setBackgroundColor", Integer.valueOf(0x1AFFFFFF)); } catch (Throwable t) {}
                    Object lpLine = XposedHelpers.newInstance(android.view.ViewGroup.LayoutParams.class,
                            Integer.valueOf(-1), Integer.valueOf(1));

                    if(!PlusPanelLayout.mount(fc,fg,row,divider,(android.view.ViewGroup.LayoutParams)lpRow,(android.view.ViewGroup.LayoutParams)lpLine,(android.view.ViewGroup.LayoutParams)lpWrap))return;
                    PANEL_INJECTED.put(fc,"1");PANEL_HOSTS.put(fc,"1");
                    T("panel entry attached nativeItems="+adapterCount(fg));
                    refinePanel(fg, row, iv, tv, divider, 0);
                    if (!PANEL_LOGGED) T("panel inject rowH=" + rowPx + " icon=" + iconPx + " txtL=" + textLf + " src=" + (nm == null ? "dp" : "measure"));
                } catch (Throwable t) { T("panel inject err " + t); }
            }
        });
    }

    static android.view.View findPlus(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View c = g.getChildAt(i);
            CharSequence d = c.getContentDescription();
            if (d != null) {
                String t = d.toString();
                if (t.contains("\u66f4\u591a") || t.contains("More") || t.contains("more")) return c;
            }
            if (c instanceof android.view.ViewGroup) {
                android.view.View r = findPlus((android.view.ViewGroup) c);
                if (r != null) return r;
            }
        }
        return null;
    }
}
