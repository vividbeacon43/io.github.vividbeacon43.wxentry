package com.zafiro.wxentry.kernel;
import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Independent all-edition original-photo switch; uses native compressType=1, never thumbnail mode. */
final class OriginalImageRuntime {
    static final Handler main=new Handler(Looper.getMainLooper());
    static final ScheduledThreadPoolExecutor io=new ScheduledThreadPoolExecutor(1);
    static final ConcurrentHashMap<String,Job> jobs=new ConcurrentHashMap<String,Job>();
    static final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
    static final class Job {
        final String key,account,owner,talker;final long local,server,received;
        volatile boolean running,queued,done;volatile Object manager,listener,idTalker;volatile long imageId,next;volatile int generation,attempts;
        Job(MediaNative.Message m){account=m.account;owner=ReceiptStore.account();talker=m.talker;local=m.local;server=m.server;received=m.received;key=account+"|"+local+"|"+server;}
    }
    static boolean enabled(){return !Kernel.MODULE_OFF&&ReceiptStore.enabled(ReceiptConfig.Feature.ORIGINAL_IMAGE);}
    static boolean allowed(String owner,String talker){return !Kernel.MODULE_OFF&&owner.equals(Kernel.receiptAccount())&&ReceiptStore.permits(ReceiptConfig.Feature.ORIGINAL_IMAGE,owner,talker);}
    static boolean alive(Job j){return allowed(j.owner,j.talker)&&MediaMessagePolicy.live(j.received,System.currentTimeMillis())&&jobs.get(j.key)==j;}
    static void error(String stage,Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();Kernel.T("ARORIGINAL "+stage+" "+e.getClass().getSimpleName());}
    static void start(ClassLoader loader)throws Throwable{
        MediaNative.cl=loader;
        Class<?> nativeImage=Class.forName("g61.j",false,loader),talker=Class.forName("com.tencent.mm.plugin.msg.MsgIdTalker",false,loader),listener=Class.forName("ma0.w",false,loader);
        nativeImage.getDeclaredMethod("b",long.class,talker,int.class,Object.class,int.class,listener,int.class,boolean.class);
        nativeImage.getDeclaredMethod("g",long.class,talker,int.class,listener);
        int hooks=0;for(Class<?> db:Kernel.discoverDbImpls(loader))for(Method m:db.getDeclaredMethods()){
            Class<?>[] p=m.getParameterTypes();if(m.getReturnType()!=long.class||p.length!=3||p[0]!=String.class||p[1]!=String.class||p[2]!=ContentValues.class)continue;
            XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()&&p.getResult() instanceof Number&&((Number)p.getResult()).longValue()>=0&&"message".equals(p.args[0]))inserted(p.thisObject,(ContentValues)p.args[2]);}});hooks++;
        }
        io.setRemoveOnCancelPolicy(true);io.scheduleWithFixedDelay(new Runnable(){public void run(){try{tick();}catch(Throwable e){error("tick-error",e);}}},1,1,TimeUnit.SECONDS);
        Kernel.T("ARORIGINAL ready insertHooks="+hooks+" editions=all independent=true nativeType=1");
    }
    static void inserted(final Object db,ContentValues cv){
        if(!enabled()||cv==null)return;Integer type=cv.getAsInteger("type"),sent=cv.getAsInteger("isSend");final Long sid=cv.getAsLong("msgSvrId");final String talker=cv.getAsString("talker");
        if(type==null||(type&0xffff)!=3||(sent!=null&&sent!=0)||sid==null||sid<=0||talker==null||jobs.size()>=128||!allowed(Kernel.receiptAccount(),talker))return;
        if(pending.incrementAndGet()>128){pending.decrementAndGet();return;}
        io.schedule(new Runnable(){public void run(){try{if(!enabled())return;MediaNative.Message m=MediaNative.read(db,talker,sid);if(m!=null&&(m.type&0xffff)==3&&MediaMessagePolicy.live(m.received,System.currentTimeMillis())){Job j=new Job(m);jobs.putIfAbsent(j.key,j);}}catch(Throwable e){error("insert-error",e);}finally{pending.decrementAndGet();}}},150,TimeUnit.MILLISECONDS);
    }
    static MediaNative.Message current(Job j)throws Throwable{
        MediaNative.Message m=MediaNative.read(null,j.talker,j.server);return m!=null&&m.local==j.local&&m.account.equals(j.account)&&(m.type&0xffff)==3?m:null;
    }
    static void tick()throws Throwable{
        int scheduled=0;for(final Job j:jobs.values()){
            if(!alive(j)||j.done){jobs.remove(j.key,j);main.post(new Runnable(){public void run(){detach(j);}});continue;}
            if(j.running||j.queued||System.currentTimeMillis()<j.next)continue;
            if(j.attempts>=4){jobs.remove(j.key,j);continue;}
            try{MediaNative.Message m=current(j);if(m==null){jobs.remove(j.key,j);continue;}Object base=MediaNative.imageInfo(m);if(base==null){j.next=System.currentTimeMillis()+500;continue;}
                if(!MediaMessagePolicy.original(Kernel.MODULE_OFF,enabled(),m.type,0,MediaNative.originalAvailable(base))){j.next=System.currentTimeMillis()+1000;if(System.currentTimeMillis()-j.received>10000)jobs.remove(j.key,j);continue;}
                if(MediaNative.originalReady(m,base)){j.done=true;continue;}
                if(scheduled>=4)continue;scheduled++;j.queued=true;main.post(new Runnable(){public void run(){request(j);}});
            }catch(Throwable e){j.next=System.currentTimeMillis()+1500;error("prepare-error",e);}
        }
    }
    static void request(final Job j){
        j.queued=false;if(!alive(j)||j.running)return;int running=0;for(Job other:jobs.values())if(other.running)running++;if(running>=4)return;
        try{
            MediaNative.Message m=current(j);if(m==null)return;Object base=MediaNative.imageInfo(m);if(!MediaNative.originalAvailable(base))return;
            if(MediaNative.originalReady(m,base)){j.done=true;return;}Object original=MediaNative.originalInfo(base);if(original==null)return;
            j.imageId=XposedHelpers.getLongField(original,"a");if(j.imageId<=0)return;
            j.idTalker=XposedHelpers.newInstance(MediaNative.cls("com.tencent.mm.plugin.msg.MsgIdTalker"),j.local,j.talker);
            j.manager=XposedHelpers.callMethod(MediaNative.service("ma0.y"),"Hj");final int generation=++j.generation;
            j.listener=Proxy.newProxyInstance(MediaNative.cl,new Class<?>[]{MediaNative.cls("ma0.w")},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                String n=method.getName();if(n.equals("hashCode"))return System.identityHashCode(proxy);if(n.equals("equals"))return args!=null&&args[0]==proxy;if(n.equals("toString"))return "WXEntryOriginalListener";
                if(n.equals("d")&&args!=null&&args.length==10){final boolean success=((Number)args[6]).intValue()==0&&((Number)args[7]).intValue()==0;main.post(new Runnable(){public void run(){if(j.generation==generation)completed(j,success);}});}return null;
            }});
            j.running=true;j.attempts++;int result=((Number)XposedHelpers.callMethod(j.manager,"b",j.imageId,j.idTalker,1,j.key,0,j.listener,-1,false)).intValue();
            Kernel.T("ARORIGINAL request mid="+j.local+" nativeType=1 result="+result);
            if(result<0)completed(j,false);else main.postDelayed(new Runnable(){public void run(){if(j.running&&j.generation==generation){detach(j);j.next=System.currentTimeMillis()+5000;}}},45000);
        }catch(Throwable e){detach(j);j.next=System.currentTimeMillis()+5000;error("request-error",e);}
    }
    static void completed(final Job j,final boolean success){
        if(!j.running)return;j.running=false;j.manager=null;j.listener=null;j.idTalker=null;j.next=System.currentTimeMillis()+5000;
        io.execute(new Runnable(){public void run(){try{if(!alive(j))return;MediaNative.Message m=current(j);if(success&&m!=null&&MediaNative.originalReady(m,MediaNative.imageInfo(m))){j.done=true;Kernel.T("ARORIGINAL complete mid="+j.local);}}catch(Throwable e){error("completion-error",e);}}});
    }
    static void detach(Job j){
        j.generation++;j.queued=false;if(j.running){j.running=false;try{if(j.manager!=null&&j.listener!=null)XposedHelpers.callMethod(j.manager,"g",j.imageId,j.idTalker,1,j.listener);}catch(Throwable e){error("detach-error",e);}}
        j.manager=null;j.listener=null;j.idTalker=null;
    }
    static void settingsChanged(){if(!enabled())for(final Job j:jobs.values()){jobs.remove(j.key,j);main.post(new Runnable(){public void run(){detach(j);}});}}
}
