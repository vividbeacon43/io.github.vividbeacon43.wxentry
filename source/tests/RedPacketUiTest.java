package com.zafiro.wxentry.kernel;
import android.app.*;
import android.content.*;

public final class RedPacketUiTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++; if (!value) throw new AssertionError(reason);
    }
    private static final class Launch implements RedPacketUi.Launch {
        Context target; int calls; boolean fail;
        public void start(Context context) {
            calls++;
            if (fail) throw new IllegalStateException("native route failed");
            target=context;
        }
    }
    public static void main(String[] args) throws Throwable {
        Application app = new Application();
        Activity alreadyCreated = new Activity(); alreadyCreated.app=app;
        // Report regression: LauncherUI.onCreate finished before the kernel installed that hook.
        Context recovered = RedPacketUi.application(null, alreadyCreated);
        check(recovered==app, "late kernel boot obtains the existing Activity's application");
        check(recovered!=alreadyCreated, "application cache never retains the Activity");
        check(RedPacketUi.application(null,app)==app, "Application seed is supported");
        Application other = new Application(); ActivityThread.current=other;
        check(RedPacketUi.application(app,alreadyCreated)==app, "valid application cache is retained");
        check(RedPacketUi.application(null,null)==other, "current application recovers a null boot seed");
        alreadyCreated.failContext=true;
        check(RedPacketUi.application(null,alreadyCreated)==other, "failed seed context falls back to current application");
        alreadyCreated.failContext=false; alreadyCreated.app=null;
        check(RedPacketUi.application(null,alreadyCreated)==other, "missing seed application does not cache an Activity");
        app.app=null;
        check(RedPacketUi.application(null,app)==app, "Application remains valid if its context getter returns null");
        ActivityThread.fail=true;
        check(RedPacketUi.application(null,null)==null, "unavailable application fails visibly without crashing");
        check(RedPacketUi.application(app,null)==app, "valid cache works even when ActivityThread is unavailable");
        Activity live = new Activity(); Launch route=new Launch();
        check("activity".equals(RedPacketUi.open(null,live,route)), "visible Activity works despite missing APP_CTX");
        check(route.target==live && route.calls==1, "native entry receives actual foreground Activity once");
        check(live.launches==0, "module does not directly start a hard-coded receive page");
        check("activity".equals(RedPacketUi.open(app,live,route)) && route.target==live, "foreground Activity takes precedence");
        live.finishing=true;
        check("application".equals(RedPacketUi.open(app,live,route)) && route.target==app, "finishing Activity is skipped");
        live.finishing=false; live.destroyed=true;
        check("application".equals(RedPacketUi.open(app,live,route)) && route.target==app, "destroyed Activity is skipped");
        live.destroyed=false; live.focused=false;
        check("application".equals(RedPacketUi.open(app,live,route)) && route.target==app, "unfocused Activity uses native application route");
        check("application".equals(RedPacketUi.open(app,null,route)), "missing Activity uses native application route");
        int count=route.calls;
        check("no-context".equals(RedPacketUi.open(null,null,route)) && route.calls==count, "no context cannot submit native route");
        live.focused=true; route.fail=true; boolean failed=false;
        try { RedPacketUi.open(app,live,route); } catch (IllegalStateException expected) { failed=true; }
        check(failed && route.calls==count+1, "native route failure propagates without a duplicate application fallback");
        System.out.println("PASS "+checks+" red packet late-boot context and native launch delegation checks");
    }
}
