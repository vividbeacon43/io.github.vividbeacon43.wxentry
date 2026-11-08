package android.app;
public class ActivityThread {
    public static Application current;
    public static boolean fail;
    public static Application currentApplication() {
        if (fail) throw new IllegalStateException("application unavailable");
        return current;
    }
}
