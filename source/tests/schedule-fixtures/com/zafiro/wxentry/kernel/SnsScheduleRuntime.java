package com.zafiro.wxentry.kernel;
final class SnsScheduleRuntime {
 static String account="wxid_schedule_owner",edition="vip";static boolean verified=true,moduleOn=true;
 static boolean authorized(String a){return a.equals(account)&&edition.equals("vip")&&verified&&moduleOn;}
 static void log(String s,Throwable t){}
}
