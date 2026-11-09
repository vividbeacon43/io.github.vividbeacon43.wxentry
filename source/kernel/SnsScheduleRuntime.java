package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.*;
import android.os.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Process-local timers plus durable recovery; no hidden catch-up or resubmission. */
final class SnsScheduleRuntime {
    static final Handler main=new Handler(Looper.getMainLooper());static final ExecutorService io=Executors.newSingleThreadExecutor();
    static boolean started,busy;static String owner="",reminder="",nativeError="定时发布尚未初始化";static long lastElapsed,lastWall;static WeakReference<Activity> foreground=new WeakReference<Activity>(null);
    static AlarmManager alarm;static AlarmManager.OnAlarmListener listener;static String alarmKey="";
    static boolean authorized(String account){return ReceiptConfig.validId(account)&&account.equals(Kernel.receiptAccount())&&"vip".equals(Kernel.EDITION)&&Kernel.momentsPremiumEnabled();}
    static void log(String step,Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();StringBuilder where=new StringBuilder();StackTraceElement[] stack=e.getStackTrace();for(int i=0;i<Math.min(6,stack.length);i++)where.append(" at ").append(stack[i].toString());Kernel.T("ARSNSSCHEDULE "+step+" "+e.getClass().getSimpleName()+where);}
    static void start(ClassLoader cl)throws Throwable{
        if(started)return;try{SnsScheduleNative.init(cl,Kernel.APP_CTX);nativeError="";}catch(Throwable e){nativeError=e instanceof IllegalStateException?e.getMessage():"当前微信版本暂未适配定时发布";log("native-unavailable",e);}
        XposedBridge.hookAllMethods(Activity.class,"onResume",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){final Activity a=(Activity)p.thisObject;main.post(new Runnable(){public void run(){foreground=new WeakReference<Activity>(a);tick(false);main.postDelayed(new Runnable(){public void run(){if(foreground.get()==a)remind(a);}},800);}});}});
        XposedBridge.hookAllMethods(Activity.class,"onPause",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(foreground.get()==p.thisObject)foreground=new WeakReference<Activity>(null);}});
        XposedBridge.hookAllMethods(Activity.class,"onActivityResult",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(p.args.length==3&&p.args[0] instanceof Integer&&((Integer)p.args[0])==SnsScheduleUi.PICK){if(SnsScheduleUi.picked((Activity)p.thisObject,((Integer)p.args[1]),(Intent)p.args[2]))p.setResult(null);}}});
        XposedBridge.hookAllMethods(Class.forName("com.tencent.mm.ui.MMActivity",false,cl),"onActivityResult",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(p.args.length==3&&p.args[0] instanceof Integer&&((Integer)p.args[0])==SnsScheduleUi.PICK&&SnsScheduleUi.picked((Activity)p.thisObject,((Integer)p.args[1]),(Intent)p.args[2]))p.setResult(null);}});
        try{SnsScheduleWake.init(Kernel.APP_CTX);}catch(Throwable e){log("prewake-init-failed",e);}
        try{alarm=(AlarmManager)Kernel.APP_CTX.getSystemService(Context.ALARM_SERVICE);}catch(Throwable ignored){}
        Activity bootActivity=Kernel.currentActivityNow();if(bootActivity!=null)foreground=new WeakReference<Activity>(bootActivity);
        listener=new AlarmManager.OnAlarmListener(){public void onAlarm(){alarmKey="";tick(true);}};started=true;
        main.postDelayed(new Runnable(){public void run(){tick(false);Activity a=foreground.get();if(a!=null)remind(a);}},1200);
        // Timer ticks are distinguished from recovery/resume ticks.
        main.postDelayed(new Runnable(){public void run(){tick(true);main.postDelayed(this,10000);}},10000);
        Kernel.T("ARSNSSCHEDULE ready vipOnly=true missed=manual native="+SnsScheduleNative.ready);
    }
    static void changed(){main.post(new Runnable(){public void run(){alarmKey="";tick(false);}});}
    static void tick(boolean timer){
        final long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();final String current=Kernel.receiptAccount();boolean continuous=SnsScheduleEngine.sameClock(current.equals(owner),lastWall,lastElapsed,now,elapsed);
        boolean newOwner=!current.equals(owner);owner=current;if(timer){lastElapsed=elapsed;lastWall=now;}if(newOwner){lastElapsed=elapsed;lastWall=now;continuous=false;reminder="";}
        if(!authorized(current)){lastElapsed=0;cancelAlarm();SnsScheduleWake.release();return;}final boolean flow=continuous,auto=timer;final Set<String> editing=SnsScheduleUi.editing();
        try{
            SnsScheduleData d=SnsScheduleStore.load(current);
            if(SnsScheduleEngine.recover(d,now,auto,flow,busy,editing))SnsScheduleStore.edit(current,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleEngine.recover(q,now,auto,flow,busy,editing);}});
            d=SnsScheduleStore.load(current);if(!busy)for(SnsScheduleData.Task t:d.tasks){if(t.state==SnsScheduleData.State.UPLOADING||t.state==SnsScheduleData.State.UNKNOWN&&t.localId>0&&!t.client.isEmpty()){poll(t);break;}if(SnsScheduleData.eligible(t,now,d.enabled,true,auto,flow)){launch(t.id,false);break;}}
            arm(d);{Activity a=foreground.get();if(a!=null)remind(a);}
            if(newOwner)io.execute(new Runnable(){public void run(){SnsScheduleMedia.sweep(current,SnsScheduleUi.editing());}});
        }catch(Throwable e){log("tick-failed",e);cancelAlarm();}
        SnsScheduleWake.releaseIfIdle();
    }
    static void cancelAlarm(){if(alarm!=null&&listener!=null)try{alarm.cancel(listener);}catch(Throwable ignored){}alarmKey="";SnsScheduleWake.cancel();}
    static void arm(SnsScheduleData d){
        long due=Long.MAX_VALUE;if(d.enabled&&!nativeError.isEmpty()){cancelAlarm();return;}if(d.enabled)for(SnsScheduleData.Task t:d.tasks)if(t.state==SnsScheduleData.State.WAITING&&t.due>System.currentTimeMillis())due=Math.min(due,t.due);
        String key=d.owner+":"+due;if(key.equals(alarmKey))return;cancelAlarm();if(due==Long.MAX_VALUE||alarm==null)return;
        try{if(!SnsScheduleWake.arm(d.owner,due))alarm.setExact(AlarmManager.RTC_WAKEUP,due,"WXEntry scheduled Moments",listener,main);alarmKey=key;}catch(Throwable e){log("alarm-unavailable",e);}
    }
    static boolean launch(final String id,boolean manual){
        if(busy||!manual&&SnsScheduleUi.editing().contains(id))return false;final String account=Kernel.receiptAccount();if(!authorized(account)||!nativeError.isEmpty())return false;final long now=System.currentTimeMillis(),startedAt=SystemClock.elapsedRealtime();
        boolean saved=SnsScheduleStore.edit(account,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData d){SnsScheduleEngine.claim(d,id,now,manual);}});
        if(!saved)return false;busy=true;SnsScheduleWake.hold(account);Kernel.T("ARSNSSCHEDULE claimed id="+id.substring(0,8)+" manual="+manual);
        io.execute(new Runnable(){public void run(){try{final SnsScheduleData.Task task=SnsScheduleStore.load(account).find(id);final Object builder=SnsScheduleNative.prepare(task,Kernel.APP_CTX);
            main.post(new Runnable(){public void run(){boolean called=false;try{
                if(!authorized(account))throw new IllegalStateException("账号或 VIP 权限已变更");
                if(!manual&&!SnsScheduleEngine.commitTimely(task.due,now,startedAt,System.currentTimeMillis(),SystemClock.elapsedRealtime())){
                    SnsScheduleStore.edit(account,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleData.Task t=q.find(id);if(t==null||t.state!=SnsScheduleData.State.CLAIMED)throw new IllegalStateException();t.state=SnsScheduleData.State.MISSED;t.message="准备时错过计划时间或系统时间变更，请确认补发";t.updated=System.currentTimeMillis();}});return;
                }

                if(!SnsScheduleStore.edit(account,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleEngine.attach(q,id,task.client,manual);}}))throw new IllegalStateException("提交记录保存失败或定时发布已关闭");
                called=true;final int local=SnsScheduleNative.submit(task,builder);if(local<=0)throw new IllegalStateException("原生提交未返回记录编号");
                boolean recorded=SnsScheduleStore.edit(account,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleEngine.queued(q,id,task.client,local,System.currentTimeMillis());}});
                if(!recorded)throw new IllegalStateException("提交结果记录失败，请核对朋友圈");SnsScheduleNative.kick();main.postDelayed(new Runnable(){public void run(){tick(true);}},1500);Kernel.T("ARSNSSCHEDULE native-queued id="+id.substring(0,8)+" local="+local+" photos="+task.photos.size()+" self="+task.selfOnly);
            }catch(Throwable e){fail(account,id,called,e);}finally{busy=false;changed();}}});
        }catch(Throwable e){main.post(new Runnable(){public void run(){fail(account,id,false,e);busy=false;changed();}});}}});return true;
    }
    static void fail(final String account,final String id,final boolean uncertain,Throwable e){log(uncertain?"submission-uncertain":"preparation-failed",e);final String message=uncertain?"提交结果不明，请核对自己的朋友圈；不会自动重发":"准备失败，请检查图片、微信版本及权限后重新创建";SnsScheduleStore.edit(account,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleData.Task t=q.find(id);if(t!=null){t.state=uncertain?SnsScheduleData.State.UNKNOWN:SnsScheduleData.State.FAILED;t.message=message;t.updated=System.currentTimeMillis();}}});if(!uncertain)SnsScheduleMedia.clean(account,id);}
    static void poll(final SnsScheduleData.Task task){busy=true;io.execute(new Runnable(){public void run(){try{final long status=SnsScheduleNative.status(task);if(status!=0){boolean saved=SnsScheduleStore.edit(task.owner,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleEngine.result(q,task.id,task.client,task.localId,status,System.currentTimeMillis());}});if(saved){SnsScheduleMedia.clean(task.owner,task.id);Kernel.T("ARSNSSCHEDULE result id="+task.id.substring(0,8)+" published="+(status!=-1)+" photos="+task.photos.size());}}else if(System.currentTimeMillis()-task.updated>10*60000){SnsScheduleStore.edit(task.owner,new SnsScheduleStore.Edit(){public void apply(SnsScheduleData q){SnsScheduleData.Task t=q.find(task.id);if(t!=null&&t.state==SnsScheduleData.State.UPLOADING)t.message="微信仍未确认发布结果，请检查网络及自己的朋友圈；不会自动重发";}});}}catch(Throwable e){log("result-unavailable",e);}finally{main.post(new Runnable(){public void run(){busy=false;SnsScheduleWake.releaseIfIdle();}});}}});}
    static void remind(final Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed()||!authorized(Kernel.receiptAccount())||!a.hasWindowFocus())return;
        try{SnsScheduleData d=SnsScheduleStore.load(Kernel.receiptAccount());int n=0;String key=d.owner;for(SnsScheduleData.Task t:d.tasks)if(t.state==SnsScheduleData.State.MISSED||t.state==SnsScheduleData.State.UNKNOWN){n++;key+=t.id+t.state;}if(n==0){reminder="";return;}if(key.equals(reminder))return;reminder=key;
            new AlertDialog.Builder(a).setTitle("定时朋友圈需要确认").setMessage("有 "+n+" 个任务已错过时间或提交结果待核对。请到任务列表检查；确认前不会补发。").setNegativeButton("稍后",null).setPositiveButton("查看任务",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){SnsScheduleUi.show(a);}}).show();
        }catch(Throwable e){log("reminder-unavailable",e);}
    }
}
