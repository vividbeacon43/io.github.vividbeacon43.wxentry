package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

/** Native visible reception only. Does not submit receipt requests or retain an Activity. */
final class RedPacketUi {
    interface Launch { void start(Context target) throws Throwable; }
    static Context application(Context cached, Object seed) {
        if (cached != null) return cached;
        Context found = from(seed);
        if (found != null) return found;
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return from(app);
        } catch (Throwable unavailable) { return null; }
    }

    private static Context from(Object seed) {
        if (!(seed instanceof Context)) return null;
        Context context = (Context) seed;
        try {
            Context app = context.getApplicationContext();
            if (app != null) return app;
        } catch (Throwable unavailable) { }
        return context instanceof Application ? context : null;
    }

    static String open(Context application, Activity current, Launch launch) throws Throwable {
        boolean foreground = false;
        try {
            foreground = current != null && !current.isFinishing()
                    && !current.isDestroyed() && current.hasWindowFocus();
        } catch (Throwable unavailable) { }
        if (!foreground && application == null) return "no-context";
        if (foreground) {
            launch.start(current);
            return "activity";
        }
        launch.start(application);
        return "application";
    }
}
