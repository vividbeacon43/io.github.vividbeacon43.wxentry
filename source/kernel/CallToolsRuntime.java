package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Allows the stock chat media and scanner entry points during a one-to-one WeChat call. */
final class CallToolsRuntime {
    private static final String PREFS = "wxentry.call_tools";
    private static final String SCAN_ENTRY = "CLICK_ENTER_SCAN";
    private static final String PRIOR_SCOPE = "wxentry.call_tools.prior_scope";
    private static final String PRIOR_VOICE_CHECK = "wxentry.call_tools.prior_voice_check";
    private static final String DISPATCH_VOICE_CHECK = "wxentry.call_tools.dispatch_voice_check";
    private enum Entry { VIDEO, SCANNER }
    private static final ThreadLocal<Entry> entry = new ThreadLocal<>();
    private static final ThreadLocal<VoiceCheck> voiceCheck = new ThreadLocal<>();
    private static volatile boolean ready;

    private static final class VoiceCheck {
        boolean queried;
        Object result;
        String callType;
    }

    private CallToolsRuntime() {}

    static boolean available() {
        return ready && !Kernel.MODULE_OFF && paidGrant();
    }

    static boolean enabled() {
        String owner = owner();
        return owner != null && settings().getBoolean(owner, false);
    }

    static boolean setEnabled(Activity activity, boolean enabled) {
        if (!available()) {
            Toast.makeText(activity, "此功能仅限已授权的高级版和 VIP 版", Toast.LENGTH_SHORT).show();
            return false;
        }
        String owner = owner();
        if (owner == null) {
            Toast.makeText(activity, "请登录微信后再设置", Toast.LENGTH_SHORT).show();
            return false;
        }
        SharedPreferences settings = settings();
        if (!settings.edit().putBoolean(owner, enabled).commit()) {
            Toast.makeText(activity, "通话工具设置未保存", Toast.LENGTH_SHORT).show();
        }
        return settings.getBoolean(owner, false);
    }

    private static String owner() {
        if (Kernel.APP_CTX == null || !available()) return null;
        String account = Kernel.receiptAccount();
        return ReceiptConfig.validId(account) && !"null".equals(account) ? RedPacketRuntime.hash(account) : null;
    }

    private static SharedPreferences settings() {
        return Kernel.APP_CTX.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean paidGrant() {
        // The existing loader bridge checks the signed edition for the current activation token.
        return "1".equals(Kernel.callMainStr2("imageFeatureEnabledFor", Kernel.EDITION));
    }

    static void start(ClassLoader loader) {
        try {
            Context app = Kernel.APP_CTX;
            if (app == null || app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionCode != 3220) return;
            Class<?> guard = type(loader, "sq.b");
            Class<?> event = type(loader, "com.tencent.mm.autogen.events.VoipCheckIsDeviceUsingEvent");
            Constructor<?> newEvent = event.getDeclaredConstructor();
            Field deviceState = event.getDeclaredField("g");
            Class<?> state = deviceState.getType();
            Field videoCall = state.getDeclaredField("a");
            Field cameraInUse = state.getDeclaredField("b");
            Field voiceInUse = state.getDeclaredField("c");

            Method scanner = guard.getDeclaredMethod("i", Context.class, boolean.class, Bundle.class);
            Method video = guard.getDeclaredMethod("C", Context.class);
            Method otherCamera = guard.getDeclaredMethod("m", Context.class, boolean.class, Bundle.class);
            Method finderAudio = guard.getDeclaredMethod("q", Context.class, boolean.class);
            Method tingAudio = guard.getDeclaredMethod("B", Context.class, boolean.class);
            Method liveAudio = guard.getDeclaredMethod("s", Context.class);
            Method chatVideo = type(loader, "ao5.sb").getDeclaredMethod("b",
                    type(loader, "com.tencent.mm.storage.f9"), String.class, String.class, int.class, View.class);
            Method plusScan = type(loader, "com.tencent.mm.ui.zg").getDeclaredMethod("onItemClick",
                    AdapterView.class, View.class, int.class, long.class);
            Method discoverScan = type(loader, "com.tencent.mm.ui.FindMoreFriendsUI").getDeclaredMethod("w0",
                    type(loader, "com.tencent.mm.ui.base.preference.q"),
                    type(loader, "com.tencent.mm.ui.base.preference.Preference"),
                    type(loader, "com.tencent.mm.ui.base.preference.m0"));
            Method voiceGate = type(loader, "com.tencent.mm.ui.chatting.viewitems.sq").getDeclaredMethod("g",
                    type(loader, "wk5.d"), type(loader, "com.tencent.mm.storage.f9"),
                    type(loader, "com.tencent.mm.plugin.msgquote.model.MsgQuoteItem"));
            Method dispatch = event.getSuperclass().getDeclaredMethod("e");
            if (scanner.getReturnType() != boolean.class || video.getReturnType() != boolean.class
                    || voiceGate.getReturnType() != boolean.class || dispatch.getReturnType() != boolean.class) return;

            XposedBridge.hookMethod(chatVideo, scope(Entry.VIDEO));
            XposedBridge.hookMethod(plusScan, scope(Entry.SCANNER));
            XposedBridge.hookMethod(discoverScan, scope(Entry.SCANNER));
            hookVoicePlayback(voiceGate, dispatch, event, deviceState, videoCall, voiceInUse);
            XposedBridge.hookMethod(scanner, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!ready || Kernel.MODULE_OFF || entry.get() != Entry.SCANNER) return;
                    Bundle args = (Bundle) p.args[2];
                    if (args == null || !args.getBoolean(SCAN_ENTRY)) return;
                    try {
                        if (!enabled()) return;
                        String callType = callUsing(newEvent, deviceState, videoCall, cameraInUse);
                        if (callType == null) return;
                        // Keep the native check for other camera owners. Android still opens the real camera.
                        boolean blocked = (Boolean) otherCamera.invoke(null, p.args);
                        p.setResult(blocked);
                        if (!blocked) Kernel.T("CALLTOOLS scanner entry call=" + callType);
                    } catch (Throwable failure) {
                        Kernel.T("CALLTOOLS scanner error " + failure.getClass().getSimpleName());
                    }
                }
            });
            XposedBridge.hookMethod(video, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!ready || Kernel.MODULE_OFF || entry.get() != Entry.VIDEO) return;
                    try {
                        if (!enabled()) return;
                        String callType = callUsing(newEvent, deviceState, videoCall, voiceInUse);
                        if (callType == null) return;
                        // Run the checks WeChat would reach if its one-to-one call did not block playback.
                        Context context = (Context) p.args[0];
                        boolean blocked = (Boolean) otherCamera.invoke(null, context, true, null)
                                || (Boolean) finderAudio.invoke(null, context, true)
                                || (Boolean) tingAudio.invoke(null, context, true);
                        if (!blocked) liveAudio.invoke(null, context);
                        p.setResult(blocked);
                        if (!blocked) Kernel.T("CALLTOOLS video entry call=" + callType);
                    } catch (Throwable failure) {
                        Kernel.T("CALLTOOLS video error " + failure.getClass().getSimpleName());
                    }
                }
            });
            ready = true;
            Kernel.T("CALLTOOLS ready video=chat scanner=home,discover voice=chat");
        } catch (Throwable failure) {
            Kernel.T("CALLTOOLS unavailable " + failure.getClass().getSimpleName());
        }
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static String callUsing(Constructor<?> newEvent, Field state, Field video, Field resource) throws Exception {
        Object check = newEvent.newInstance();
        XposedHelpers.callMethod(check, "e");
        Object result = state.get(check);
        if (!resource.getBoolean(result)) return null;
        return video.getBoolean(result) ? "video" : "voice";
    }

    private static void hookVoicePlayback(Method gate, Method dispatch, Class<?> event, Field state,
                                          Field video, Field occupied) {
        XposedBridge.hookMethod(gate, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                p.setObjectExtra(PRIOR_VOICE_CHECK, voiceCheck.get());
                voiceCheck.remove();
                try {
                    if (enabled()) voiceCheck.set(new VoiceCheck());
                } catch (Throwable failure) {
                    Kernel.T("CALLTOOLS voice gate error " + failure.getClass().getSimpleName());
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                VoiceCheck check = voiceCheck.get();
                VoiceCheck previous = (VoiceCheck) p.getObjectExtra(PRIOR_VOICE_CHECK);
                if (previous == null) voiceCheck.remove();
                else voiceCheck.set(previous);
                if (check == null || check.result == null) return;
                try {
                    occupied.setBoolean(check.result, true);
                    if (!p.hasThrowable() && Boolean.TRUE.equals(p.getResult())) {
                        Kernel.T("CALLTOOLS voice entry call=" + check.callType);
                    }
                } catch (Throwable failure) {
                    Kernel.T("CALLTOOLS voice restore error " + failure.getClass().getSimpleName());
                }
            }
        });
        XposedBridge.hookMethod(dispatch, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                VoiceCheck check = voiceCheck.get();
                if (check == null || check.queried || p.thisObject.getClass() != event) return;
                check.queried = true;
                p.setObjectExtra(DISPATCH_VOICE_CHECK, check);
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                VoiceCheck check = (VoiceCheck) p.getObjectExtra(DISPATCH_VOICE_CHECK);
                if (check == null || p.hasThrowable()) return;
                try {
                    Object result = state.get(p.thisObject);
                    if (!occupied.getBoolean(result)) return;
                    check.callType = video.getBoolean(result) ? "video" : "voice";
                    // This query belongs to the chat voice player. Keep the original gate and its other checks.
                    occupied.setBoolean(result, false);
                    check.result = result;
                } catch (Throwable failure) {
                    Kernel.T("CALLTOOLS voice query error " + failure.getClass().getSimpleName());
                }
            }
        });
    }

    private static XC_MethodHook scope(Entry current) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                p.setObjectExtra(PRIOR_SCOPE, entry.get());
                entry.set(current);
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                Entry previous = (Entry) p.getObjectExtra(PRIOR_SCOPE);
                if (previous == null) entry.remove();
                else entry.set(previous);
            }
        };
    }
}
