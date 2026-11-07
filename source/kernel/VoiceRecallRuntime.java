package com.zafiro.wxentry.kernel;

import android.content.ContentValues;
import android.database.Cursor;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/** Base anti-recall for received voice messages, shared by all four editions. */
final class VoiceRecallRuntime {
    private VoiceRecallRuntime() {}

    static void start(ClassLoader loader) throws Throwable {
        Class<?> consumer = Class.forName("w41.u", false, loader);
        Method revoke = consumer.getDeclaredMethod("c", String.class, long.class,
                Class.forName("com.tencent.mm.modelbase.p0", false, loader),
                String.class, String.class, String.class);
        if (revoke.getReturnType() != void.class) throw new NoSuchMethodException("void revoke");
        XposedBridge.hookMethod(revoke, new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                if (!enabled()) return;
                Cursor cursor = null;
                try {
                    if (p.args == null || p.args.length != 6
                            || !(p.args[0] instanceof String) || !(p.args[1] instanceof Long)) return;
                    String talker = (String) p.args[0];
                    long serverId = ((Long) p.args[1]).longValue();
                    if (talker.length() == 0 || serverId <= 0) return;
                    Object db = Kernel.db0();
                    cursor = Kernel.rawQuery(db,
                            "select msgId,type,isSend from message where talker=? and msgSvrId=? limit 1",
                            new String[]{talker, Long.toString(serverId)});
                    if (cursor == null || !cursor.moveToFirst()
                            || (cursor.getInt(1) & 0xffff) != 34 || cursor.getInt(2) != 0) return;
                    long localId = cursor.getLong(0);
                    cursor.close();
                    cursor = null;
                    if (localId <= 0 || !enabled()) return;

                    // A blocked SQL update still lets RevokeMsgEvent remove the voice metadata/file.
                    // Stop this received-voice revoke before either the row or that event is changed.
                    // Leave native voice download/playback and user-initiated deletion untouched.
                    p.setResult(null);
                    Kernel.T("ARVOICE retained mid=" + localId);
                    if (!Kernel.NO_TIP) {
                        ContentValues tip = new ContentValues();
                        tip.put("content", p.args[3] instanceof String && ((String) p.args[3]).length() > 0
                                ? (String) p.args[3] : "对方撤回了一条语音");
                        Kernel.insertTipRow(db, Long.toString(localId), tip);
                    }
                } catch (Throwable failure) {
                    // Do not log voice content, account identifiers, file paths or transport keys.
                    Kernel.T("ARVOICE revoke-error " + failure.getClass().getSimpleName());
                } finally {
                    if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
                }
            }
        });
        Kernel.T("ARVOICE ready base=true native=w41.u.c");
    }

    private static boolean enabled() {
        return !Kernel.MODULE_OFF && !Kernel.f(Kernel.AR_OFF_PATH);
    }
}
