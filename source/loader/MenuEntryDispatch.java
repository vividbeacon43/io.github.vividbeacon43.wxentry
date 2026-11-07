package com.zafiro.wxentry;

/** One dispatch rule for the loader's fallback entry. No boot-attempt flag is used. */
public final class MenuEntryDispatch {
    private MenuEntryDispatch() {}
    public static String readiness(Class<?> kernel) {
        if (kernel == null) return "not-loaded";
        try {
            java.lang.reflect.Method ready = kernel.getMethod("isMenuReady");
            if (!java.lang.reflect.Modifier.isStatic(ready.getModifiers())) return "failed";
            return Boolean.TRUE.equals(ready.invoke(null)) ? "ready" : "not-ready";
        } catch (NoSuchMethodException oldKernel) {
            return "legacy-unconfirmed";
        } catch (ReflectiveOperationException | LinkageError | SecurityException failedKernel) {
            return "failed";
        }
    }
    public static boolean open(Class<?> authorizedKernel, Class<?> activityType, Object activity,
                               Runnable status) {
        if (authorizedKernel != null) {
            try {
                java.lang.reflect.Method entry = authorizedKernel.getMethod("openRecoveredMenu", activityType);
                if (!java.lang.reflect.Modifier.isStatic(entry.getModifiers())) throw new NoSuchMethodException("Static entry required");
                Object opened = entry.invoke(null, activity);
                if (Boolean.TRUE.equals(opened)) return true;
            } catch (ReflectiveOperationException | LinkageError | SecurityException failedKernel) {
                // The status entry must remain usable with missing/failed/older kernels.
            }
        }
        status.run();
        return true;
    }
}
