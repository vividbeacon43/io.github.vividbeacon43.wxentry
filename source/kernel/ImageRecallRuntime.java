package com.zafiro.wxentry.kernel;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Adapter for WeChat 8.0.80 / 5032. No activity, fake Wi-Fi or fabricated callback. */
final class ImageRecallRuntime {
    private static ClassLoader cl;
    private static RecallImageCache cache;
    private static Handler main;
    private static final ScheduledThreadPoolExecutor io=new ScheduledThreadPoolExecutor(1);
    private static final ConcurrentHashMap<String,Job> jobs=new ConcurrentHashMap<String,Job>();
    private static final ArrayDeque<Job> ready=new ArrayDeque<Job>(); // main looper only
    private static int active; // main looper only
    private static final java.util.concurrent.atomic.AtomicInteger inserts=new java.util.concurrent.atomic.AtomicInteger();
    private static final class Job {
        final RecallImageCache.Entry entry;
        volatile boolean done, queued, running;
        int attempts;
        long next;
        Object manager, idTalker, listener;
        long imageId;
        int generation;
        Job(RecallImageCache.Entry e) { entry=e; }
    }
    private static final class Message {
        Object db;
        String account,talker,path;
        long local,server,arrival;
    }
    private static void log(String stage,long mid,String detail) {
        Kernel.T("ARIMG "+stage+" mid="+mid+(detail.length()==0?"":" "+detail));
    }
    private static void error(String stage,long mid,Throwable failure) {
        while(failure.getCause()!=null && failure.getCause()!=failure) failure=failure.getCause();
        // Do not log message XML, CDN credentials or exception messages containing paths/URLs.
        log(stage,mid,failure.getClass().getSimpleName());
    }
    private static boolean enabled() {
        return Kernel.imageRecallEnabled();
    }
    static void authorizationChanged() {
        if(cache==null || main==null) return;
        io.execute(new Runnable() { public void run() {
            try {
                if(enabled()) {
                    for(RecallImageCache.Entry e:cache.entries())
                        if(e.isRecalled() && enabled()) cache.restore(e);
                } else {
                    for(final Job j:jobs.values()) {
                        jobs.remove(j.entry.key,j);
                        main.post(new Runnable() { public void run() { detach(j); pump(); }});
                    }
                    cache.discardPending();
                }
                Kernel.T("ARIMG authorization mode="+(Kernel.imageRecallEnabled()?"pro":"basic"));
            } catch(Throwable t) { error("authorization-error",0,t); }
        }});
    }
    static void start(ClassLoader loader) throws Throwable {
        cl=loader;
        main=new Handler(Looper.getMainLooper());
        cache=new RecallImageCache(new File(Kernel.WXDIR,"recall-images-v2"),
                new RecallImageCache.Clock() { public long now() { return System.currentTimeMillis(); }});
        // Resolve all required entry points before installing any image-specific hooks.
        Class.forName("g61.a1",false,cl); Class.forName("ma0.w",false,cl);
        Class<?> consumer=Class.forName("w41.u",false,cl);
        Method revoke=consumer.getDeclaredMethod("c",String.class,long.class,
                Class.forName("com.tencent.mm.modelbase.p0",false,cl),String.class,String.class,String.class);
        if(revoke.getReturnType()!=void.class) throw new NoSuchMethodException("void revoke");
        XposedBridge.hookMethod(revoke,new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                if(!enabled()) return;
                try {
                    Message m=message(null,(String)p.args[0],((Number)p.args[1]).longValue());
                    if(m==null) return; // Incoming image only; text and self-recall retain existing behavior.
                    RecallImageCache.Entry e=cache.recall(m.account,m.talker,m.local,m.server);
                    if(e==null) {
                        e=register(m,m.arrival);
                        if(e!=null) e=cache.recall(m.account,m.talker,m.local,m.server);
                    }
                    if(e==null) return;
                    if(!enabled()) return;
                    // Stop the native revoke event before it deletes ImgInfo and the original paths.
                    p.setResult(null);
                    final RecallImageCache.Entry retained=e;
                    log("retained",m.local,"");
                    if(!Kernel.NO_TIP) {
                        ContentValues tip=new ContentValues();
                        tip.put("content",p.args[3] instanceof String?(String)p.args[3]:"对方撤回了一张图片");
                        Kernel.insertTipRow(m.db,Long.toString(m.local),tip);
                    }
                    io.execute(new Runnable() { public void run() {
                        try { if(enabled()) { cache.restore(retained); ensureJob(retained); } }
                        catch(Throwable t) { error("restore-error",retained.localId,t); }
                    }});
                } catch(Throwable t) { error("revoke-error",0,t); }
            }
        });
        int hooks=0;
        for(Class<?> db:Kernel.discoverDbImpls(cl)) for(Method method:db.getDeclaredMethods()) {
            Class<?>[] ps=method.getParameterTypes();
            if(method.getReturnType()!=long.class || ps.length!=3 || ps[0]!=String.class
                    || ps[1]!=String.class || ps[2]!=ContentValues.class) continue;
            XposedBridge.hookMethod(method,new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    if(p.hasThrowable() || !(p.getResult() instanceof Number)
                            || ((Number)p.getResult()).longValue()<0 || !"message".equals(p.args[0])) return;
                    inserted(p.thisObject,(ContentValues)p.args[2]);
                }
            });
            hooks++;
        }
        io.setRemoveOnCancelPolicy(true);
        io.execute(new Runnable() { public void run() {
            for(RecallImageCache.Entry e:cache.entries()) {
                try { if(e.isRecalled() && enabled()) cache.restore(e); }
                catch(Throwable t) { error("startup-restore-error",e.localId,t); }
            }
        }});
        io.scheduleWithFixedDelay(new Runnable() { public void run() {
            try { tick(); } catch(Throwable t) { error("sweep-error",0,t); }
        }},1,1,TimeUnit.SECONDS);
        Kernel.T("ARIMG ready insertHooks="+hooks+" window=180s native=g61.j mode="
                +(Kernel.imageRecallEnabled()?"pro":"basic"));
    }
    static void inserted(final Object db,ContentValues cv) {
        if(cache==null || cv==null || !enabled()) return;
        Integer type=cv.getAsInteger("type"), sent=cv.getAsInteger("isSend");
        final Long sid=cv.getAsLong("msgSvrId");
        final String talker=cv.getAsString("talker");
        if(type==null || (type.intValue()&0xffff)!=3 || (sent!=null && sent.intValue()!=0)
                || sid==null || sid.longValue()<=0 || talker==null || jobs.size()>=128) return;
        final long arrival=System.currentTimeMillis();
        if(inserts.incrementAndGet()>128) { inserts.decrementAndGet(); return; }
        io.schedule(new Runnable() { public void run() {
            try {
                if(!enabled()) return;
                Message m=message(db,talker,sid.longValue());
                if(m!=null) { RecallImageCache.Entry e=register(m,arrival); if(e!=null) ensureJob(e); }
            } catch(Throwable t) { error("insert-error",0,t); }
            finally { inserts.decrementAndGet(); }
        }},100,TimeUnit.MILLISECONDS);
    }
    private static Message message(Object given,String talker,long sid) throws Throwable {
        Object db=given==null?Kernel.db0():given;
        Cursor c=Kernel.rawQuery(db,"select msgId,type,isSend,imgPath,createTime from message where talker=? and msgSvrId=? limit 1",
                new String[]{talker,Long.toString(sid)});
        Message m=new Message();
        try {
            if(c==null || !c.moveToFirst() || (c.getInt(1)&0xffff)!=3 || c.getInt(2)!=0) return null;
            m.local=c.getLong(0); m.path=c.getString(3); m.arrival=c.getLong(4);
        } finally { if(c!=null) c.close(); }
        c=Kernel.rawQuery(db,"pragma database_list",null);
        try {
            while(c!=null && c.moveToNext()) {
                String path=c.getString(2);
                if(path==null) continue;
                int pos=path.indexOf("/MicroMsg/");
                if(pos<0 || path.length()<pos+43) continue;
                String a=path.substring(pos+10,pos+42);
                if(a.matches("[a-fA-F0-9]{32}") && path.charAt(pos+42)=='/') { m.account=a; break; }
            }
        } finally { if(c!=null) c.close(); }
        if(m.local<=0 || m.account==null) return null;
        m.db=db; m.talker=talker; m.server=sid; return m;
    }
    private static RecallImageCache.Entry register(Message m,long at) throws Exception {
        return cache.registerAt(m.account,m.talker,m.local,m.server,m.path,at);
    }
    private static void ensureJob(RecallImageCache.Entry e) {
        if(jobs.size()>=128) return;
        jobs.putIfAbsent(e.key,new Job(e));
    }
    private static boolean alive(Job j) {
        return j.entry.isRecalled() || System.currentTimeMillis()<j.entry.expiresAt;
    }
    private static void tick() throws Throwable {
        int removed=cache.sweep();
        if(!enabled()) removed+=cache.discardPending();
        if(removed>0) Kernel.T("ARIMG expired entries="+removed+" officialFilesUntouched=true");
        for(final Job j:jobs.values()) {
            if(!enabled() || !alive(j)) {
                jobs.remove(j.entry.key,j);
                main.post(new Runnable() { public void run() { detach(j); pump(); }});
                continue;
            }
            if(j.done) { jobs.remove(j.entry.key,j); continue; }
            if(j.queued || j.running || System.currentTimeMillis()<j.next) continue;
            if(j.attempts>=6) { jobs.remove(j.entry.key,j); continue; }
            try {
                Object info=imageInfo(j.entry);
                if(info==null || XposedHelpers.getLongField(info,"a")<=0) { j.next=System.currentTimeMillis()+500; continue; }
                if(Boolean.TRUE.equals(XposedHelpers.callMethod(info,"l")) && snapshot(j,info)) {
                    j.done=true; continue;
                }
                j.imageId=XposedHelpers.getLongField(info,"a");
                j.queued=true;
                main.post(new Runnable() { public void run() { ready.add(j); pump(); }});
            } catch(Throwable t) { j.next=System.currentTimeMillis()+1000; error("prepare-error",j.entry.localId,t); }
        }
    }
    private static Object imageInfo(RecallImageCache.Entry e) throws Throwable {
        // A switched account must never resolve or restore another account's message.
        Message current=message(null,e.talker,e.serverId);
        if(current==null || !current.account.equals(e.account) || current.local!=e.localId) return null;
        Object storage=XposedHelpers.callStaticMethod(Class.forName("g61.a1",true,cl),"Hj");
        return XposedHelpers.callMethod(storage,"K3",e.talker,e.serverId);
    }
    private static void pump() {
        while(active<4 && !ready.isEmpty()) {
            final Job j=ready.remove(); j.queued=false;
            if(j.done || !enabled() || !alive(j) || jobs.get(j.entry.key)!=j) continue;
            final int generation=++j.generation;
            j.attempts++;
            j.next=System.currentTimeMillis()+3000;
            try {
                Object provider=XposedHelpers.callStaticMethod(Class.forName("fi5.n0",true,cl),"c",Class.forName("ma0.y",false,cl));
                j.manager=XposedHelpers.callMethod(provider,"Hj");
                j.idTalker=XposedHelpers.newInstance(Class.forName("com.tencent.mm.plugin.msg.MsgIdTalker",false,cl),j.entry.localId,j.entry.talker);
                j.listener=Proxy.newProxyInstance(cl,new Class<?>[]{Class.forName("ma0.w",false,cl)},new InvocationHandler() {
                    public Object invoke(Object proxy,Method method,Object[] args) {
                        String name=method.getName();
                        if(name.equals("hashCode")) return System.identityHashCode(proxy);
                        if(name.equals("equals")) return args!=null && args[0]==proxy;
                        if(name.equals("toString")) return "WXEntryImageListener";
                        if(name.equals("d") && args!=null && args.length==10) {
                            final boolean success=((Number)args[6]).intValue()==0 && ((Number)args[7]).intValue()==0;
                            main.post(new Runnable() { public void run() { if(j.generation==generation) completed(j,success); }});
                        }
                        return null;
                    }
                });
                j.running=true; active++;
                int result=((Number)XposedHelpers.callMethod(j.manager,"b",j.imageId,j.idTalker,0,
                        j.entry.key,0,j.listener,-1,false)).intValue();
                log("request",j.entry.localId,"result="+result+" attempt="+j.attempts);
                if(result<0) completed(j,false);
                else main.postDelayed(new Runnable() { public void run() {
                    if(j.running && j.generation==generation) { detach(j); j.next=System.currentTimeMillis()+3000; pump(); }
                }},45000);
            } catch(Throwable t) { error("request-error",j.entry.localId,t); completed(j,false); }
        }
    }
    private static void completed(final Job j,final boolean success) {
        if(!j.running) return;
        j.running=false; active=Math.max(0,active-1);
        j.next=System.currentTimeMillis()+3000;
        // DownloadImgService removes its listener after this real completion callback.
        j.listener=null; j.manager=null; j.idTalker=null;
        io.execute(new Runnable() { public void run() {
            try {
                if(!enabled() || !alive(j)) return;
                Object info=imageInfo(j.entry);
                if(success && info!=null && Boolean.TRUE.equals(XposedHelpers.callMethod(info,"l")))
                    j.done=snapshot(j,info);
                else log("download-failed",j.entry.localId,"");
            } catch(Throwable t) { error("snapshot-error",j.entry.localId,t); }
        }});
        pump();
    }
    private static void detach(Job j) {
        ready.remove(j); j.queued=false;
        if(!j.running) return;
        j.running=false; active=Math.max(0,active-1);
        try {
            // Removes only our listener. The native service retains official viewer listeners.
            if(j.manager!=null && j.listener!=null)
                XposedHelpers.callMethod(j.manager,"g",j.imageId,j.idTalker,0,j.listener);
        } catch(Throwable t) { error("detach-error",j.entry.localId,t); }
        j.listener=null; j.manager=null; j.idTalker=null;
    }
    private static File physical(String path) throws Throwable {
        if(path==null || path.length()==0) return null;
        Object resolved=XposedHelpers.callStaticMethod(Class.forName("com.tencent.mm.vfs.z6",false,cl),"i",path,false);
        if(!(resolved instanceof String) || !((String)resolved).startsWith("/")) return null;
        return new File((String)resolved).getCanonicalFile();
    }
    private static boolean snapshot(Job j,Object info) throws Throwable {
        if(!enabled()) return false;
        Object message=XposedHelpers.callStaticMethod(Class.forName("yx0.k0",false,cl),"cj",j.entry.talker,j.entry.localId);
        if(message==null || ((Number)XposedHelpers.callMethod(message,"G0")).longValue()!=j.entry.serverId) return false;
        String path=(String)XposedHelpers.callStaticMethod(Class.forName("com.tencent.mm.ui.chatting.gallery.d3",false,cl),"y",message,info,false);
        File full=physical(path);
        if(full==null || !full.isFile() || !j.entry.account.equals(RecallImageCache.accountOf(full.getPath()))) return false;
        if(!enabled()) return false;
        cache.observeCompleted(j.entry,full,false);
        // Preserve the genuine native thumbnail too, never use it as a substitute for the full file.
        try {
            Object plugin=XposedHelpers.callStaticMethod(Class.forName("fi5.n0",false,cl),"c",Class.forName("ep3.u0",false,cl));
            String thumbPath=(String)XposedHelpers.callMethod(plugin,"Vj",message,
                    (String)XposedHelpers.callMethod(message,"z0"),false,false);
            File thumb=physical(thumbPath);
            if(thumb!=null && thumb.isFile()) cache.observeCompleted(j.entry,thumb,true);
        } catch(Throwable t) { error("thumbnail-error",j.entry.localId,t); }
        boolean stored=false;
        for(RecallImageCache.Image image:cache.images(j.entry)) if(!image.thumbnail) { stored=true; break; }
        if(stored) {
            if(j.entry.isRecalled() && enabled()) cache.restore(j.entry);
            log("cached",j.entry.localId,"fullBytes="+full.length()+" retained="+j.entry.isRecalled());
        }
        return stored;
    }
}
