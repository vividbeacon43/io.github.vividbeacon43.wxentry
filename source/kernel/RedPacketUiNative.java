package com.zafiro.wxentry.kernel;

import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;

/** Uses the host's complete visible receive entry, including its native page selector and envelope data. */
final class RedPacketUiNative {
    static RedPacketUi.Launch prepare(ClassLoader cl, String url, long msgId, String talker, String content) throws Throwable {
        if (cl == null || content == null || content.length() > 262144 || !ReceiptConfig.validId(talker))
            throw new IllegalArgumentException("Native red packet source unavailable");
        Class<?> messageClass = Class.forName("xx0.r", false, cl);
        Method parse = messageClass.getDeclaredMethod("v", String.class);
        if (!Modifier.isStatic(parse.getModifiers()) || parse.getReturnType() != messageClass)
            throw new IllegalStateException("Native red packet parser changed");
        parse.setAccessible(true);
        final Object message = parse.invoke(null, content);
        if (message == null || !url.equals(field(messageClass, "s1").get(message))
                || field(messageClass, "i").getInt(message) != 2001)
            throw new IllegalArgumentException("Native red packet identity mismatch");
        // De parses this native scene field before routing. Validate without inventing a scene.
        Object scene = field(messageClass, "r1").get(message);
        if (!(scene instanceof String) || !((String) scene).matches("[0-9]{1,9}"))
            throw new IllegalArgumentException("Native red packet scene unavailable");
        Class<?> pluginClass = Class.forName("com.tencent.mm.plugin.wxpay.api.IPluginWxPay", false, cl);
        Method locate = Class.forName("fi5.n0", false, cl).getDeclaredMethod("c", Class.class);
        if (!Modifier.isStatic(locate.getModifiers())) throw new IllegalStateException("Native plugin registry changed");
        locate.setAccessible(true);
        final Object plugin = locate.invoke(null, pluginClass);
        if (!pluginClass.isInstance(plugin)) throw new IllegalStateException("Native wallet plugin unavailable");
        final Method receive = pluginClass.getMethod("De", Context.class, Bundle.class, messageClass);
        if (receive.getReturnType() != void.class || Modifier.isStatic(receive.getModifiers()))
            throw new IllegalStateException("Native visible receive entry changed");
        final Bundle extras = new Bundle();
        extras.putString("key_username", talker);
        extras.putLong("key_msgid", msgId);
        return new RedPacketUi.Launch() {
            public void start(Context target) throws Throwable {
                // The host retains all skin/animation fields and selects NewReceiveUI or NotHookReceiveUI itself.
                try { receive.invoke(plugin, target, extras, message); }
                catch (InvocationTargetException failure) { throw failure.getTargetException(); }
            }
        };
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
