package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Serializes native transcription requests for received voice messages in the foreground chat. */
final class VoiceTextRuntime {
    private static final long RETRY_MS = 700L;
    private static final long DOWNLOAD_RETRY_MS = 3000L;
    private static final int CHECKS_PER_TICK = 12;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Session current;
    private static volatile boolean ready;
    private static Class<?> componentInterface, componentType, adapterInterface, adapterType, messageType, voiceKeyInterface;
    private static Method nativeStart, nativeDownloaded, fileBytes, findService, getNetwork, findMessage;

    private VoiceTextRuntime() {}

    static boolean available() {
        return ready;
    }

    static boolean enabled() {
        return configuredSince(owner()) > 0;
    }

    static boolean setEnabled(Activity activity, boolean on) {
        String owner = owner();
        SharedPreferences preferences = settings();
        boolean previous = configuredSince(owner) > 0;
        if (previous == on) return previous;
        if (owner == null || preferences == null) {
            Toast.makeText(activity, "请登录微信后再设置语音自动转文字", Toast.LENGTH_SHORT).show();
            return previous;
        }
        SharedPreferences.Editor change = preferences.edit();
        if (on) change.putLong(owner, System.currentTimeMillis());
        else change.remove(owner);
        if (!change.commit()) {
            Toast.makeText(activity, "语音自动转文字设置未保存", Toast.LENGTH_SHORT).show();
            return previous;
        }
        if (!on) closeSession();
        return on;
    }

    private static String owner() {
        if (!ready || Kernel.MODULE_OFF) return null;
        String account = Kernel.receiptAccount();
        return ReceiptConfig.validId(account) && !"null".equals(account) ? RedPacketRuntime.hash(account) : null;
    }

    private static SharedPreferences settings() {
        Context app = Kernel.APP_CTX;
        return app == null ? null : app.getSharedPreferences("wxentry.voice_text", Context.MODE_PRIVATE);
    }

    private static long configuredSince(String owner) {
        SharedPreferences preferences = settings();
        return owner == null || preferences == null ? 0 : preferences.getLong(owner, 0);
    }

    static void start(ClassLoader loader) throws Throwable {
        Class<?> chat = Class.forName("wk5.d", false, loader);
        Class<?> fragment = Class.forName("com.tencent.mm.ui.chatting.ChattingUIFragment", false, loader);
        Class<?> baseFragment = Class.forName("com.tencent.mm.ui.chatting.BaseChattingUIFragment", false, loader);
        Class<?> dataChange = Class.forName("yt3.r1", false, loader);
        Class<?> actions = Class.forName("com.tencent.mm.ui.chatting.viewitems.sq", false, loader);
        Class<?> files = Class.forName("q71.c1", false, loader);
        Class<?> serviceLocator = Class.forName("fi5.n0", false, loader);
        Class<?> accountServices = Class.forName("w41.i9", false, loader);
        Class<?> storage = Class.forName("com.tencent.mm.storage.g9", false, loader);
        Class<?> messageLookup = Class.forName("yx0.k0", false, loader);
        componentInterface = Class.forName("qk5.n2", false, loader);
        componentType = Class.forName("com.tencent.mm.ui.chatting.component.gp", false, loader);
        adapterInterface = Class.forName("qk5.a0", false, loader);
        adapterType = Class.forName("com.tencent.mm.ui.chatting.adapter.k", false, loader);
        messageType = Class.forName("com.tencent.mm.storage.f9", false, loader);
        voiceKeyInterface = Class.forName("vv.h1", false, loader);

        Method resume = fragment.getDeclaredMethod("K0");
        Method pause = fragment.getDeclaredMethod("J0");
        Method exit = fragment.getDeclaredMethod("b1");
        Method dataReady = adapterType.getDeclaredMethod("n", dataChange);
        Method inserted = storage.getDeclaredMethod("a", messageType, boolean.class, boolean.class);
        nativeStart = actions.getDeclaredMethod("c", int.class, chat, messageType, componentInterface, boolean.class, int.class);
        nativeDownloaded = actions.getDeclaredMethod("p", messageType);
        fileBytes = files.getDeclaredMethod("g", messageType, String.class);
        findService = serviceLocator.getDeclaredMethod("c", Class.class);
        getNetwork = accountServices.getDeclaredMethod("e");
        findMessage = messageLookup.getDeclaredMethod("k2", String.class, long.class);
        findMessage.setAccessible(true);
        baseFragment.getDeclaredField("f");
        chat.getDeclaredField("i");
        adapterType.getDeclaredField("K");
        adapterType.getDeclaredMethod("W0", int.class);
        adapterType.getDeclaredMethod("getCount");
        componentType.getDeclaredMethod("C0", long.class, String.class);
        componentType.getDeclaredMethod("y0", long.class);
        componentType.getDeclaredField("m");
        componentType.getDeclaredField("t");
        if (inserted.getReturnType() != long.class || findMessage.getReturnType() != messageType
                || nativeStart.getReturnType() != boolean.class || nativeDownloaded.getReturnType() != boolean.class
                || fileBytes.getReturnType() != int.class) {
            throw new NoSuchMethodException("Voice transcript contract changed");
        }

        XposedBridge.hookMethod(resume, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!ready || p.hasThrowable()) return;
                try {
                    resumeChat(XposedHelpers.getObjectField(p.thisObject, "f"));
                } catch (Throwable failure) {
                    logError("resume", failure);
                }
            }
        });
        XposedBridge.hookMethod(pause, endHook(false));
        XposedBridge.hookMethod(exit, endHook(true));
        XposedBridge.hookMethod(dataReady, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Session session = current;
                if (session == null || p.hasThrowable()) return;
                try {
                    if (session.chat.get() == XposedHelpers.getObjectField(p.thisObject, "K")) {
                        MAIN.removeCallbacks(session.scan);
                        MAIN.post(session.scan);
                    }
                } catch (Throwable failure) {
                    logError("adapter", failure);
                }
            }
        });
        XposedBridge.hookMethod(inserted, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Session session = current;
                if (session == null || Kernel.MODULE_OFF || p.hasThrowable()
                        || !(p.getResult() instanceof Number) || ((Number) p.getResult()).longValue() <= 0) return;
                try {
                    Voice voice = readVoice(p.args[0], session.talker, true);
                    if (voice == null) return;
                    MAIN.post(() -> {
                        if (current == session) session.enqueue(voice);
                    });
                } catch (Throwable failure) {
                    logError("insert", failure);
                }
            }
        });
        ready = true;
        Kernel.T("ARVOICETEXT ready native=voice-transform scope=loaded-and-new");
    }

    private static XC_MethodHook endHook(boolean leaving) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Session session = current;
                if (session == null) return;
                try {
                    if (session.chat.get() != XposedHelpers.getObjectField(p.thisObject, "f")) return;
                    if (leaving) closeSession();
                    else session.pause();
                } catch (Throwable failure) {
                    logError("pause", failure);
                }
            }
        };
    }

    private static void resumeChat(Object chat) {
        if (!XposedHelpers.getBooleanField(chat, "i")) return;
        String owner = owner();
        long since = configuredSince(owner);
        String talker = (String) XposedHelpers.callMethod(chat, "x");
        if (since <= 0 || talker == null || talker.isEmpty()) {
            closeSession();
            return;
        }
        Session session = current;
        if (session == null || !session.matches(chat, owner, talker, since)) {
            closeSession();
            session = new Session(chat, owner, talker, since);
            current = session;
        }
        session.resumed = true;
        MAIN.removeCallbacks(session.scan);
        MAIN.post(session.scan);
        session.schedule(260L);
    }

    private static void closeSession() {
        Session session = current;
        current = null;
        if (session != null) session.pause();
    }

    private static Voice readVoice(Object message, String talker, boolean incoming) {
        if (!messageType.isInstance(message) || number(message, "getType") != 34
                || number(message, "A0") != 0 || !talker.equals(XposedHelpers.callMethod(message, "O0"))) return null;
        long id = number(message, "getMsgId");
        long server = number(message, "G0");
        return id > 0 && server != 0 ? new Voice(id, server, incoming) : null;
    }

    private static long number(Object object, String method) {
        return ((Number) XposedHelpers.callMethod(object, method)).longValue();
    }

    private static void logError(String stage, Throwable failure) {
        while (failure.getCause() != null && failure.getCause() != failure) failure = failure.getCause();
        Kernel.T("ARVOICETEXT " + stage + "-error " + failure.getClass().getSimpleName());
    }

    private static final class Voice {
        final long id, server;
        final boolean incoming;
        long checkAfter;
        int lookupRetries;

        Voice(long id, long server, boolean incoming) {
            this.id = id;
            this.server = server;
            this.incoming = incoming;
        }
    }

    private static final class Session implements Runnable {
        final WeakReference<Object> chat;
        final String owner, talker;
        final long since;
        final Map<Long, Long> seen = new HashMap<>();
        final LinkedHashMap<Long, Voice> pending = new LinkedHashMap<>();
        final Runnable scan = this::scanLoaded;
        boolean resumed;
        long scheduledAt = Long.MAX_VALUE;

        Session(Object chat, String owner, String talker, long since) {
            this.chat = new WeakReference<>(chat);
            this.owner = owner;
            this.talker = talker;
            this.since = since;
        }

        boolean matches(Object context, String owner, String talker, long since) {
            return chat.get() == context && this.owner.equals(owner) && this.talker.equals(talker) && this.since == since;
        }

        boolean owns(Object context) {
            return current == this && context != null && matches(context, VoiceTextRuntime.owner(),
                    (String) XposedHelpers.callMethod(context, "x"), configuredSince(owner));
        }

        void pause() {
            resumed = false;
            MAIN.removeCallbacks(scan);
            MAIN.removeCallbacks(this);
            scheduledAt = Long.MAX_VALUE;
        }

        void enqueue(Voice voice) {
            Long prior = seen.put(voice.id, voice.server);
            if (prior != null && prior.longValue() == voice.server) return;
            pending.put(voice.id, voice);
            schedule(260L);
        }

        void scanLoaded() {
            if (!resumed) return;
            try {
                Object context = chat.get();
                if (!owns(context)) return;
                Object adapter = XposedHelpers.callMethod(context, "a", adapterInterface);
                if (!adapterType.isInstance(adapter)) return;
                int count = (int) number(adapter, "getCount");
                for (int position = 0; position < count; position++) {
                    Voice voice = readVoice(XposedHelpers.callMethod(adapter, "W0", position), talker, false);
                    if (voice != null) enqueue(voice);
                }
            } catch (Throwable failure) {
                logError("scan", failure);
            }
        }

        void schedule(long delay) {
            if (!resumed || pending.isEmpty()) return;
            long when = SystemClock.uptimeMillis() + delay;
            if (when >= scheduledAt) return;
            MAIN.removeCallbacks(this);
            scheduledAt = when;
            MAIN.postAtTime(this, when);
        }

        @Override public void run() {
            scheduledAt = Long.MAX_VALUE;
            if (!resumed || pending.isEmpty()) return;
            try {
                Object context = chat.get();
                if (!owns(context)) {
                    if (current == this) closeSession();
                    return;
                }
                Activity activity = (Activity) XposedHelpers.callMethod(context, "g");
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                if (!activity.hasWindowFocus()) {
                    schedule(RETRY_MS);
                    return;
                }
                Object component = XposedHelpers.callMethod(context, "a", componentInterface);
                if (!componentType.isInstance(component)) {
                    schedule(RETRY_MS);
                    return;
                }
                Map<?, ?> nativeQueue = (Map<?, ?>) XposedHelpers.getStaticObjectField(componentType, "t");
                long network = number(getNetwork.invoke(null), "n");
                if (XposedHelpers.getBooleanField(component, "m") || !nativeQueue.isEmpty()
                        || (network != 4 && network != 6)) {
                    schedule(RETRY_MS);
                    return;
                }
                drain(context, component);
            } catch (Throwable failure) {
                logError("queue", failure);
                schedule(DOWNLOAD_RETRY_MS);
            }
        }

        private void drain(Object context, Object component) {
            long now = SystemClock.uptimeMillis();
            long next = Long.MAX_VALUE;
            int checks = 0;
            Iterator<Voice> rows = pending.values().iterator();
            while (rows.hasNext()) {
                Voice voice = rows.next();
                if (voice.checkAfter > now) {
                    next = Math.min(next, voice.checkAfter);
                    continue;
                }
                if (checks++ >= CHECKS_PER_TICK) {
                    next = Math.min(next, now + 100L);
                    break;
                }
                try {
                    Object message = findMessage.invoke(null, talker, voice.id);
                    Voice latest = readVoice(message, talker, voice.incoming);
                    if (latest == null && voice.incoming && voice.lookupRetries++ < 3
                            && (!messageType.isInstance(message) || number(message, "getMsgId") <= 0)) {
                        voice.checkAfter = now + RETRY_MS;
                        next = Math.min(next, voice.checkAfter);
                        continue;
                    }
                    if (latest == null || latest.server != voice.server || latest.id != voice.id
                            || Boolean.TRUE.equals(XposedHelpers.callMethod(message, "U2"))
                            || Boolean.TRUE.equals(XposedHelpers.callMethod(message, "a2"))) {
                        rows.remove();
                        continue;
                    }
                    String file = (String) XposedHelpers.callMethod(message, "z0");
                    if (number(message, "N0") == 5 || file == null || file.isEmpty()
                            || !Boolean.TRUE.equals(nativeDownloaded.invoke(null, message))
                            || ((Number) fileBytes.invoke(null, message, file)).intValue() <= 0) {
                        voice.checkAfter = now + DOWNLOAD_RETRY_MS;
                        next = Math.min(next, voice.checkAfter);
                        continue;
                    }
                    rows.remove();
                    if (Boolean.TRUE.equals(XposedHelpers.callMethod(component, "y0", voice.id))) continue;
                    Object keyService = findService.invoke(null, voiceKeyInterface);
                    String clientKey = (String) XposedHelpers.callMethod(keyService, "Fj", message);
                    if (clientKey == null || clientKey.isEmpty()) continue;
                    String transcript = (String) XposedHelpers.callMethod(component, "C0", voice.id, clientKey);
                    if (transcript != null && !transcript.trim().isEmpty()) continue;
                    // Negative position prevents a scroll. The fourth native option preserves the unread audio marker.
                    nativeStart.invoke(null, -1, context, message, component, false, 3);
                    Kernel.T("ARVOICETEXT native-request source=" + (voice.incoming ? "arrival" : "loaded"));
                    next = Math.min(next, now + RETRY_MS);
                    break;
                } catch (Throwable failure) {
                    logError("request", failure);
                    if (pending.containsKey(voice.id)) rows.remove();
                }
            }
            if (next != Long.MAX_VALUE) schedule(Math.max(100L, next - SystemClock.uptimeMillis()));
        }
    }
}
