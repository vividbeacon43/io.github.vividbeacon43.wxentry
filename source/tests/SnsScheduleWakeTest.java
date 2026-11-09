package com.zafiro.wxentry.kernel;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;

public final class SnsScheduleWakeTest {
 static int checks;static String owner="wxid_wake_owner";static Context context=new Context();
 static void ok(boolean v){checks++;if(!v)throw new AssertionError("wake check "+checks);}
 static long queue(){long due=System.currentTimeMillis()+1500;SnsScheduleData q=new SnsScheduleData(owner);q.enabled=true;q.tasks.add(new SnsScheduleData.Task("1234567890abcdef1234567890abcdef",owner,"private wake test",due,true,System.currentTimeMillis()));SnsScheduleStore.data=q;return due;}
 static void dispatch(){context.receiver.onReceive(context,context.alarm.operation.intent);}
 public static void main(String[] args)throws Throwable{
  SnsScheduleWake.init(context);ok(context.receiverFlags==4);ok(SnsScheduleWake.exactAllowed());long due=queue();ok(SnsScheduleWake.arm(owner,due));ok(context.alarm.scheduled==1&&context.alarm.type==AlarmManager.RTC_WAKEUP);ok(context.alarm.operation.flags==(PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_CANCEL_CURRENT));ok("com.tencent.mm".equals(context.alarm.operation.intent.getPackage()));ok(SnsScheduleWake.lock==null);
  context.receiver.onReceive(context,new Intent("unrelated").setData(Uri.parse(SnsScheduleWake.ticket.uri)));ok(SnsScheduleWake.lock==null);context.receiver.onReceive(context,new Intent(SnsScheduleWake.action).setData(Uri.parse("wxentry-schedule://wrong")));ok(SnsScheduleWake.lock==null);
  SnsScheduleStore.data.enabled=false;dispatch();ok(SnsScheduleWake.ticket==null&&SnsScheduleWake.lock==null);SnsScheduleStore.data.enabled=true;
  for(String edition:new String[]{"free","plus"}){SnsScheduleRuntime.edition=edition;int before=context.alarm.scheduled;ok(!SnsScheduleWake.arm(owner,queue()));ok(context.alarm.scheduled==before);}SnsScheduleRuntime.edition="vip";
  SnsScheduleRuntime.verified=false;ok(!SnsScheduleWake.arm(owner,queue()));SnsScheduleRuntime.verified=true;ok(!SnsScheduleWake.arm("different_owner",queue()));
  context.alarm.allowed=false;ok(!SnsScheduleWake.exactAllowed());ok(!SnsScheduleWake.arm(owner,queue()));context.alarm.allowed=true;context.alarm.fail=true;ok(!SnsScheduleWake.arm(owner,queue()));ok(SnsScheduleWake.ticket==null);context.alarm.fail=false;
  due=queue();ok(SnsScheduleWake.arm(owner,due));dispatch();PowerManager.WakeLock lock=context.power.latest;ok(lock!=null&&lock.level==PowerManager.PARTIAL_WAKE_LOCK&&!lock.referenceCounted&&lock.isHeld());ok(lock.timeout==90000&&!context.power.interactive);ok(SnsScheduleRuntime.main.lastDelay>0&&SnsScheduleRuntime.main.lastDelay<=2000);ok(SnsScheduleRuntime.ticks==0);
  SnsScheduleWake.cancel();SnsScheduleRuntime.main.drain();ok(SnsScheduleRuntime.ticks==0&&!lock.isHeld());
  due=queue();ok(SnsScheduleWake.arm(owner,due));dispatch();SnsScheduleRuntime.main.drain();ok(SnsScheduleRuntime.ticks==1);SnsScheduleWake.cancel();SnsScheduleStore.data.tasks.get(0).state=SnsScheduleData.State.UPLOADING;SnsScheduleWake.releaseIfIdle();ok(lock.isHeld());SnsScheduleStore.data.tasks.get(0).state=SnsScheduleData.State.PUBLISHED;SnsScheduleWake.releaseIfIdle();ok(!lock.isHeld());
  due=queue();ok(SnsScheduleWake.arm(owner,due));SnsScheduleStore.fail=true;dispatch();ok(SnsScheduleWake.ticket==null&&!lock.isHeld());SnsScheduleStore.fail=false;
  due=queue();ok(SnsScheduleWake.arm(owner,due));SnsScheduleStore.data.tasks.get(0).state=SnsScheduleData.State.MISSED;dispatch();ok(SnsScheduleWake.ticket==null);
  due=queue();ok(SnsScheduleWake.arm(owner,due));SnsScheduleRuntime.account="another_account";dispatch();ok(SnsScheduleWake.ticket==null&&!lock.isHeld());SnsScheduleRuntime.account=owner;
  due=queue();ok(SnsScheduleWake.arm(owner,due));SnsScheduleWake.hold(owner);SnsScheduleRuntime.busy=true;SnsScheduleStore.data.tasks.get(0).state=SnsScheduleData.State.CLAIMED;dispatch();ok(SnsScheduleWake.ticket==null&&lock.isHeld());SnsScheduleRuntime.busy=false;SnsScheduleStore.data.tasks.get(0).state=SnsScheduleData.State.PUBLISHED;SnsScheduleWake.releaseIfIdle();ok(!lock.isHeld());
  Activity a=new Activity();SnsScheduleWake.permission(a);ok("android.settings.REQUEST_SCHEDULE_EXACT_ALARM".equals(a.started.getAction())&&"package:com.tencent.mm".equals(a.started.getDataString()));
  Build.VERSION.SDK_INT=29;SnsScheduleWake.initialized=false;context=new Context();SnsScheduleWake.init(context);context.alarm.allowed=false;ok(context.receiverFlags==0&&SnsScheduleWake.exactAllowed());
  System.out.println("PASS "+checks+" private idle alarm, VIP/account/queue gates, bounded screen-off CPU lock, stale cancellation and release checks");
 }
}
