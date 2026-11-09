package com.zafiro.wxentry.kernel;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.UUID;

/** One private idle-capable alarm per deadline; a bounded CPU lock never lights the display. */
final class SnsScheduleWake {
    static final long EARLY_MS=2000,LOCK_MS=90000;
    static Context app;static AlarmManager alarms;static PowerManager power;static PowerManager.WakeLock lock;
    static String action;static Ticket ticket;static boolean initialized;
    static final class Ticket {
        final String owner,uri;final long due;final PendingIntent operation;
        Ticket(String owner,long due,String uri,PendingIntent op){this.owner=owner;this.due=due;this.uri=uri;operation=op;}
    }
    static void init(Context context)throws Throwable{
        if(initialized)return;app=context.getApplicationContext();alarms=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
        action="com.tencent.mm.wxentry.SCHEDULE_WAKE."+UUID.randomUUID().toString().replace("-","");
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
            final Ticket t=ticket;if(t==null||intent==null||!action.equals(intent.getAction())||!t.uri.equals(intent.getDataString()))return;
            long now=System.currentTimeMillis();if(!permitted(t.owner,t.due,now)){cancel();releaseIfIdle();return;}
            hold(t.owner);Kernel.T("ARSNSSCHEDULE prewake screenOn="+power.isInteractive()+" earlyMs="+(t.due-now));
            SnsScheduleRuntime.main.postDelayed(new Runnable(){public void run(){if(ticket==t&&permitted(t.owner,t.due,System.currentTimeMillis()))SnsScheduleRuntime.tick(true);else releaseIfIdle();}},Math.max(0,t.due-now));
        }};
        IntentFilter filter=new IntentFilter(action);if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,filter,4);else app.registerReceiver(receiver,filter);
        initialized=true;
    }
    static boolean exactAllowed(){try{return initialized&&alarms!=null&&(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms());}catch(Throwable e){return false;}}
    static boolean permitted(String owner,long due,long now){
        if(!SnsScheduleRuntime.authorized(owner)||now<due-EARLY_MS-2000||now-due>45000)return false;
        try{SnsScheduleData data=SnsScheduleStore.load(owner);if(!data.enabled)return false;for(SnsScheduleData.Task task:data.tasks)if(task.state==SnsScheduleData.State.WAITING&&task.due==due&&!SnsScheduleUi.editing().contains(task.id))return true;}catch(Throwable e){SnsScheduleRuntime.log("prewake-check-failed",e);}return false;
    }
    static boolean arm(String owner,long due){
        cancel();if(!exactAllowed()||!SnsScheduleRuntime.authorized(owner))return false;
        try{String uri="wxentry-schedule://wake/"+UUID.randomUUID().toString();Intent intent=new Intent(action).setPackage(app.getPackageName()).setData(Uri.parse(uri));PendingIntent pi=PendingIntent.getBroadcast(app,0,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_CANCEL_CURRENT);ticket=new Ticket(owner,due,uri,pi);
            // Only one allow-while-idle alarm: a second one seconds later can be throttled by Doze.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,SnsScheduleEngine.wakeAt(due,System.currentTimeMillis()),pi);return true;
        }catch(Throwable e){cancel();SnsScheduleRuntime.log("prewake-arm-failed",e);return false;}
    }
    static void cancel(){Ticket t=ticket;ticket=null;if(t!=null){try{alarms.cancel(t.operation);}catch(Throwable ignored){}try{t.operation.cancel();}catch(Throwable ignored){}}}
    static void hold(String owner){
        if(!SnsScheduleRuntime.authorized(owner)||power==null)return;try{if(lock==null){lock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WXEntry:scheduled-moments");lock.setReferenceCounted(false);}lock.acquire(LOCK_MS);}catch(Throwable e){SnsScheduleRuntime.log("cpu-wake-unavailable",e);}
    }
    static void release(){try{if(lock!=null&&lock.isHeld())lock.release();}catch(Throwable ignored){}}
    static void releaseIfIdle(){
        if(SnsScheduleRuntime.busy)return;try{long now=System.currentTimeMillis();Ticket t=ticket;if(t!=null&&t.due-now<=EARLY_MS&&now-t.due<=45000)return;
            String owner=Kernel.receiptAccount();if(SnsScheduleRuntime.authorized(owner))for(SnsScheduleData.Task task:SnsScheduleStore.load(owner).tasks)if((task.state==SnsScheduleData.State.CLAIMED||task.state==SnsScheduleData.State.UPLOADING)&&now-task.updated<LOCK_MS)return;
        }catch(Throwable ignored){}release();
    }
    static void permission(Activity a){try{a.startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",Uri.parse("package:com.tencent.mm")));}catch(Throwable e){SnsScheduleUi.toast(a,"请到系统设置 → 微信 → 闹钟和提醒，允许准时执行");}}
}
