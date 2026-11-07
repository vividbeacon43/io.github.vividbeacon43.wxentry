package com.zafiro.wxentry.kernel;
final class ReceiptStore {
 static boolean on=true,allow=true;static String account(){return Kernel.owner;}
 static boolean enabled(ReceiptConfig.Feature f){return on;}
 static boolean permits(ReceiptConfig.Feature f,String account,String talker){return on&&allow&&account.equals(Kernel.owner);}
}
