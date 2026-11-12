package com.zafiro.wxentry.kernel;

import android.content.Context;
import android.content.res.Resources;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Reads the effective WeChat setting without writing its preferences. */
final class WechatAppearance {
    private static volatile Resolver resolver;

    static boolean isDark(Context host) {
        ClassLoader loader = host.getClassLoader();
        Resolver current = resolver;
        if (current == null || current.loader != loader) {
            current = new Resolver(loader);
            resolver = current;
        }
        if (current.readTheme != null) {
            try {
                return Boolean.TRUE.equals(current.readTheme.invoke(null, host.getResources()));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // A host patch may replace or disable this helper.
            }
        }
        return SettingsUi.dark(host);
    }

    private static final class Resolver {
        final ClassLoader loader;
        final Method readTheme;

        Resolver(ClassLoader loader) {
            this.loader = loader;
            Method method = null;
            try {
                // WeChat 8.0.80's own display settings use this Resources-aware reader.
                Method candidate = Class.forName("com.tencent.mm.ui.ok", false, loader)
                        .getMethod("F", Resources.class);
                if (Modifier.isStatic(candidate.getModifiers()) && candidate.getReturnType() == boolean.class)
                    method = candidate;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // Use the Activity's Android configuration on other host builds.
            }
            readTheme = method;
        }
    }
}
