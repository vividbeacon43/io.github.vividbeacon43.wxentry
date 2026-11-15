package com.zafiro.wxentry.kernel;

import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies WeChat's native mute setting when a fresh system message confirms that we joined a group. */
final class NewRoomMuteRuntime {
    private static final long MAX_AGE_MS = 5 * 60_000L;
    private static final int SYSMSG_TEMPLATE = 0x22000031;
    private static final int[] CONTACT_RETRY_MS = {250, 750, 2000, 5000, 10_000};
    private static final String JOIN = "(?:加入(?:了)?(?:该|該|此)?群聊|进入(?:了)?群聊|進入(?:了)?群聊)";
    private static final String CHINESE_JOIN_END = "(?:[，,；;]\\s*(?:群聊参与人还有|群聊參與人還有)[：:].{1,240})?";
    private static final String SELF_AND_OTHERS = "(?:你|您)(?:\\s*(?:和|与|與|及|、)\\s*.{1,240}?)?";
    private static final Pattern SELF_JOIN = Pattern.compile(
            "^(?:(?:你|您)(?:已|已经|已經|成功|被邀请|被邀請)?" + JOIN + CHINESE_JOIN_END
            + "|(?:你|您)(?:通过|通過|透過)(?:扫描|掃描|邀请|邀請|接受|點擊|点击|\u201c|\").{0,240}" + JOIN + CHINESE_JOIN_END
            + "|(?:你|您)(?:创建|建立|創建)了?(?:群聊|这个群聊|這個群聊)"
            + "|.{1,240}(?:邀请|邀請)\\s*" + SELF_AND_OTHERS + JOIN + CHINESE_JOIN_END
            + "|you (?:have )?(?:joined|created) (?:the|this|a) group(?: chat)?"
            + "|you joined (?:the|this) group(?: chat)? (?:via|by) .{1,240}"
            + "|.{1,240} invited you(?: and .{1,240}?)? to (?:join )?(?:the|this) group(?: chat)?)[.!。！]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INVITEE_TEMPLATE = Pattern.compile(
            "^.{1,240}(?:邀请|邀請)\\$([a-zA-Z0-9_]{1,48})\\$" + JOIN + CHINESE_JOIN_END + "[。！.!]?$"
            + "|^.{1,240} invited \\$([a-zA-Z0-9_]{1,48})\\$ to (?:join )?(?:the|this) group(?: chat)?[.!]?$",
            Pattern.CASE_INSENSITIVE);
    private static final String CONTENT_TEMPLATE = ".sysmsg.sysmsgtemplate.content_template";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> pending = new HashSet<>();
    private final Class<?> registry;
    private final Class<?> roomService;
    private final Class<?> accountServices;
    private final Class<?> messenger;
    private final Class<?> repairer;
    private final Class<?> muteConfig;
    private final Class<?> xml;

    private NewRoomMuteRuntime(ClassLoader loader) throws ClassNotFoundException {
        registry = nativeClass(loader, "fi5.n0");
        roomService = nativeClass(loader, "kg0.e");
        accountServices = nativeClass(loader, "zp0.j1");
        messenger = nativeClass(loader, "gp3.c4");
        repairer = nativeClass(loader, "tc2.e");
        muteConfig = nativeClass(loader, "com.tencent.mm.repairer.config.profile.RepairerConfigChatroomMuteRefineDefault");
        xml = nativeClass(loader, "com.tencent.mm.sdk.platformtools.ja");
    }

    static void start(ClassLoader loader) throws ReflectiveOperationException {
        NewRoomMuteRuntime runtime = new NewRoomMuteRuntime(loader);
        Class<?> message = nativeClass(loader, "com.tencent.mm.storage.f9");
        Class<?> store = nativeClass(loader, "com.tencent.mm.storage.g9");
        Method insert = store.getDeclaredMethod("a", message, boolean.class, boolean.class);
        if (insert.getReturnType() != long.class) throw new NoSuchMethodException("message storage insert");
        XposedBridge.hookMethod(insert, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !(p.getResult() instanceof Long) || (Long) p.getResult() <= 0
                        || p.args[0] == null || !enabled()) return;
                try {
                    runtime.messageInserted(p.args[0]);
                } catch (Throwable failure) {
                    Kernel.T("newroom: detection failed " + failure.getClass().getSimpleName());
                }
            }
        });
        Kernel.T("newroom: native storage hook ready");
    }

    private void messageInserted(Object message) {
        int type = ((Number) XposedHelpers.callMethod(message, "getType")).intValue();
        if (!isJoinNoticeType(type)) return;
        String room = (String) XposedHelpers.callMethod(message, "O0");
        if (room == null || !room.endsWith("@chatroom")) return;
        long created = ((Number) XposedHelpers.callMethod(message, "getCreateTime")).longValue();
        long age = System.currentTimeMillis() - created;
        if (created <= 0 || age < -60_000 || age > MAX_AGE_MS) return;
        String text = (String) XposedHelpers.callMethod(message, "j");
        if (text == null || text.length() > 32_768) return;
        String owner = Kernel.receiptAccount();
        if (owner == null || owner.isEmpty() || "null".equals(owner)) return;
        String content = text.trim();
        Map<?, ?> fields = type == SYSMSG_TEMPLATE || content.indexOf("<sysmsg") >= 0
                ? (Map<?, ?>) XposedHelpers.callStaticMethod(xml, "d", content, "sysmsg", null) : null;
        if (type == SYSMSG_TEMPLATE && !isSystemTemplate(fields)) return;
        if (!signalsSelfJoin(content, fields, owner)) return;
        Kernel.T("newroom: join confirmed");
        enqueue(room, owner);
    }

    private void enqueue(String room, String owner) {
        handler.postDelayed(() -> {
            String key = owner + '\n' + room;
            if (pending.add(key)) apply(room, owner, key, 0);
        }, CONTACT_RETRY_MS[0]);
    }

    static boolean isJoinNoticeType(int type) {
        int lowType = type & 0xffff;
        return type == SYSMSG_TEMPLATE || lowType == 10000 || lowType == 10002;
    }

    private static boolean isSystemTemplate(Map<?, ?> fields) {
        return fields != null && "sysmsgtemplate".equals(fields.get(".sysmsg.$type"));
    }

    static boolean signalsSelfJoin(String content, Map<?, ?> fields, String owner) {
        if (!isSystemTemplate(fields)) return content.indexOf("<sysmsg") < 0 && matchesSelfJoin(content);
        Object template = fields.get(CONTENT_TEMPLATE + ".template");
        if (!(template instanceof String)) return false;
        String title = normalize((String) template);
        if (matchesSelfJoin(title)) return true;
        java.util.regex.Matcher invitee = INVITEE_TEMPLATE.matcher(title);
        if (!invitee.matches()) return false;
        String placeholder = invitee.group(1) == null ? invitee.group(2) : invitee.group(1);
        String links = CONTENT_TEMPLATE + ".link_list.link";
        for (int index = 0; index < 32; index++) {
            String link = links + (index == 0 ? "" : Integer.toString(index));
            Object name = fields.get(link + ".$name");
            if (name == null) break;
            if (!placeholder.equals(name)) continue;
            String members = link + ".memberlist.member";
            for (int member = 0; member < 1000; member++) {
                Object account = fields.get(members + (member == 0 ? "" : Integer.toString(member)) + ".username");
                if (account == null) break;
                if (owner.equals(account)) return true;
            }
        }
        return false;
    }

    private static boolean matchesSelfJoin(String text) {
        return text.length() <= 512 && SELF_JOIN.matcher(normalize(text)).matches();
    }

    private static String normalize(String text) {
        return text.replaceAll("\\p{Cf}", "").replaceAll("[\\p{Z}\\p{Cc}]+", " ").trim();
    }

    private void apply(String room, String owner, String key, int attempt) {
        boolean retrying = false;
        try {
            if (!enabled() || !owner.equals(Kernel.receiptAccount())) return;
            Object service = XposedHelpers.callStaticMethod(accountServices, "v", messenger);
            Object storage = XposedHelpers.callMethod(service, "Fj");
            Object contact = XposedHelpers.callMethod(storage, "n", room, true);
            if (contact == null || !room.equals(XposedHelpers.getObjectField(contact, "field_username"))) {
                int next = attempt + 1;
                if (next < CONTACT_RETRY_MS.length) {
                    retrying = handler.postDelayed(() -> apply(room, owner, key, next), CONTACT_RETRY_MS[next]);
                } else {
                    Kernel.T("newroom: contact unavailable");
                }
                return;
            }
            if (XposedHelpers.getIntField(contact, "T") == 0) return;
            Object repair = XposedHelpers.callStaticMethod(repairer, "g");
            int mode = ((Number) XposedHelpers.callMethod(repair, "c", XposedHelpers.newInstance(muteConfig))).intValue();
            int pushFlag = mode == 1 ? 6 : mode == 2 ? 7 : 0;
            Object sdk = XposedHelpers.callStaticMethod(registry, "c", roomService);
            Object operations = XposedHelpers.callMethod(sdk, "Ej", room);
            Object task = XposedHelpers.callMethod(operations, "h", room, 0, pushFlag);
            XposedHelpers.callMethod(task, "b");
            XposedHelpers.callMethod(contact, "K2", 0);
            int updated = ((Number) XposedHelpers.callMethod(storage, "p0", room, contact)).intValue();
            Kernel.T(updated > 0 ? "newroom: muted locally; sync requested" : "newroom: local update failed");
        } catch (Throwable failure) {
            Kernel.T("newroom: apply failed " + failure.getClass().getSimpleName());
        } finally {
            if (!retrying) pending.remove(key);
        }
    }

    private static boolean enabled() {
        return !Kernel.MODULE_OFF && new File(Kernel.NEWROOM_ON_PATH).exists();
    }

    private static Class<?> nativeClass(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
}
