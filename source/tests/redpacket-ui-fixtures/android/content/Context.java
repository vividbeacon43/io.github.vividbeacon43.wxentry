package android.content;
public class Context {
    public Context app;
    public int launches;
    public Intent last;
    public boolean failContext, failLaunch;
    public Context getApplicationContext() {
        if (failContext) throw new IllegalStateException("context unavailable");
        return app;
    }
    public void startActivity(Intent intent) {
        if (failLaunch) throw new IllegalStateException("start denied");
        last = intent; launches++;
    }
}
