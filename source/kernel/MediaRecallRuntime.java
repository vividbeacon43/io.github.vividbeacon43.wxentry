package com.zafiro.wxentry.kernel;
import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.*;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/** Intercept before native video/attachment deletion, and keep completed native files for 180s. */
final class MediaRecallRuntime {
    static RecallMediaCache cache;
    static final ScheduledThreadPoolExecutor io=new ScheduledThreadPoolExecutor(1);
    static final ConcurrentHashMap<String,Job> jobs=new ConcurrentHashMap<String,Job>();
    static final Handler main=new Handler(Looper.getMainLooper());
    static final java.util.concurrent.atomic.AtomicInteger queued=new java.util.concurrent.atomic.AtomicInteger();
    static final class Job {final RecallMediaCache.Entry entry;volatile long next,lastRequest;volatile int attempts;volatile boolean requesting;Job(RecallMediaCache.Entry e){entry=e;}}
    static boolean enabled(){return !Kernel.MODULE_OFF&&!Kernel.f(Kernel.AR_OFF_PATH);}
    static void error(String stage,Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();Kernel.T("ARMEDIA "+stage+" "+e.getClass().getSimpleName());}
    static void start(ClassLoader loader)throws Throwable{
        MediaNative.cl=loader;cache=new RecallMediaCache(new File(Kernel.WXDIR,"recall-media-v1"),new RecallMediaCache.Clock(){public long now(){return System.currentTimeMillis();}});
        Class<?> consumer=Class.forName("w41.u",false,loader);Method revoke=consumer.getDeclaredMethod("c",String.class,long.class,Class.forName("com.tencent.mm.modelbase.p0",false,loader),String.class,String.class,String.class);
        if(revoke.getReturnType()!=void.class)throw new NoSuchMethodException("void media revoke");
        XposedBridge.hookMethod(revoke,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            if(!enabled()||p.args==null||p.args.length!=6||!(p.args[0] instanceof String)||!(p.args[1] instanceof Long))return;
            try{
                MediaNative.Message m=MediaNative.read(null,(String)p.args[0],((Long)p.args[1]).longValue());if(m==null||!m.media()||!enabled())return;
                // Stop mutation and RevokeMsgEvent even when the local backup has not completed.
                p.setResult(null);Kernel.T("ARMEDIA retained type="+(m.type&0xffff)+" mid="+m.local);
                if(!Kernel.NO_TIP){ContentValues cv=new ContentValues();cv.put("content","对方撤回了一条媒体消息");Kernel.insertTipRow(m.db,Long.toString(m.local),cv);}
                RecallMediaCache.Entry e=cache.find(m.account,m.talker,m.local,m.server);
                if(e==null)e=cache.register(m.account,m.talker,m.local,m.server,m.type,m.received);
                if(e!=null){final RecallMediaCache.Entry retained=cache.recall(e);if(retained!=null)io.execute(new Runnable(){public void run(){restoreAndQueue(retained);}});}
            }catch(Throwable failure){error("revoke-error",failure);}
        }});
        int hooks=0;for(Class<?> db:Kernel.discoverDbImpls(loader))for(Method method:db.getDeclaredMethods()){
            Class<?>[] p=method.getParameterTypes();if(method.getReturnType()!=long.class||p.length!=3||p[0]!=String.class||p[1]!=String.class||p[2]!=ContentValues.class)continue;
            XposedBridge.hookMethod(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()&&p.getResult() instanceof Number&&((Number)p.getResult()).longValue()>=0&&"message".equals(p.args[0]))inserted(p.thisObject,(ContentValues)p.args[2]);}});hooks++;
        }
        io.setRemoveOnCancelPolicy(true);io.scheduleWithFixedDelay(new Runnable(){public void run(){try{tick();}catch(Throwable e){error("tick-error",e);}}},1,1,TimeUnit.SECONDS);
        io.execute(new Runnable(){public void run(){if(enabled())for(RecallMediaCache.Entry e:cache.entries())if(e.isRecalled()||MediaMessagePolicy.live(e.received,System.currentTimeMillis()))jobs.putIfAbsent(e.key,new Job(e));}});
        Kernel.T("ARMEDIA ready insertHooks="+hooks+" editions=all window=180s");
    }
    static void inserted(final Object db,ContentValues cv){
        if(cache==null||!enabled()||cv==null)return;Integer type=cv.getAsInteger("type"),sent=cv.getAsInteger("isSend");final String talker=cv.getAsString("talker");final Long server=cv.getAsLong("msgSvrId");
        if(type==null||!MediaMessagePolicy.media(type,6,sent==null?0:sent)||server==null||server<=0||talker==null||jobs.size()>=128)return;
        if(queued.incrementAndGet()>128){queued.decrementAndGet();return;}
        io.schedule(new Runnable(){public void run(){try{if(!enabled())return;MediaNative.Message m=MediaNative.read(db,talker,server);if(m==null||!m.media())return;RecallMediaCache.Entry e=cache.register(m.account,m.talker,m.local,m.server,m.type,m.received);if(e!=null)jobs.putIfAbsent(e.key,new Job(e));}catch(Throwable failure){error("insert-error",failure);}finally{queued.decrementAndGet();}}},100,TimeUnit.MILLISECONDS);
    }
    static void restoreAndQueue(RecallMediaCache.Entry e){try{if(!enabled())return;MediaNative.Message m=MediaNative.current(e);if(m==null)return;MediaNative.State s=MediaNative.state(m);if(s!=null&&s.full!=null)cache.restore(e,m.account,Collections.singletonList(s.full));jobs.putIfAbsent(e.key,new Job(e));}catch(Throwable failure){error("restore-error",failure);}}
    static void settingsChanged(){if(cache!=null)io.execute(new Runnable(){public void run(){try{if(!enabled()){jobs.clear();cache.discardPending();}}catch(Throwable failure){error("settings-error",failure);}}});}
    static void tick()throws Throwable{
        int expired=cache.sweep();if(expired>0)Kernel.T("ARMEDIA expired copies="+expired+" officialFilesUntouched=true");
        if(!enabled()){jobs.clear();cache.discardPending();return;}
        int requests=0;long now=System.currentTimeMillis();for(Job j:jobs.values()){
            RecallMediaCache.Entry e=j.entry;if((!e.isRecalled()&&!MediaMessagePolicy.live(e.received,now))||cache.find(e.account,e.talker,e.local,e.server)!=e){jobs.remove(e.key,j);continue;}
            if(now<j.next||j.requesting)continue;
            try{
                MediaNative.Message m=MediaNative.current(e);if(m==null){jobs.remove(e.key,j);continue;}final MediaNative.State s=MediaNative.state(m);if(s==null){jobs.remove(e.key,j);continue;}
                if(s.length>RecallMediaCache.MAX_FILE){Kernel.T("ARMEDIA skip copy limit mid="+e.local);jobs.remove(e.key,j);continue;}
                if(s.length>0&&!cache.hasCapacity(s.length)&&cache.copies(e).isEmpty()){Kernel.T("ARMEDIA skip cache quota mid="+e.local);jobs.remove(e.key,j);continue;}
                if(e.isRecalled()&&s.full!=null)cache.restore(e,m.account,Collections.singletonList(s.full));
                if(s.complete&&cache.snapshot(e,s.full,s.length,true)){jobs.remove(e.key,j);Kernel.T("ARMEDIA cached mid="+e.local+" recalled="+e.isRecalled());continue;}
                if(j.attempts>=4||now-j.lastRequest<30000){j.next=now+1000;continue;}
                if(requests>=2)continue;requests++;j.requesting=true;final Job current=j;
                main.post(new Runnable(){public void run(){try{if(enabled()&&(current.entry.isRecalled()||MediaMessagePolicy.live(current.entry.received,System.currentTimeMillis()))){
                    MediaNative.Message fresh=MediaNative.current(current.entry);MediaNative.State state=fresh==null?null:MediaNative.state(fresh);
                    if(state!=null&&!state.complete&&MediaNative.request(state)){current.attempts++;current.lastRequest=System.currentTimeMillis();}
                }}catch(Throwable failure){current.attempts++;current.lastRequest=System.currentTimeMillis();error("request-error",failure);}finally{current.requesting=false;current.next=System.currentTimeMillis()+1000;}}});
            }catch(Throwable failure){j.next=now+1000;error("prepare-error",failure);}
        }
    }
}
