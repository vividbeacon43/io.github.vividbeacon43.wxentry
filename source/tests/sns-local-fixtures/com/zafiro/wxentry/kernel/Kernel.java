package com.zafiro.wxentry.kernel;
final class Kernel {
 static String WXDIR,account="wxid_owner_a",edition="plus";
 static boolean verified=true,enabled=true;
 static String receiptAccount(){return account;}
 static boolean momentsPremiumEnabled(){return verified&&enabled&&(edition.equals("plus")||edition.equals("vip"));}
 static void T(String message){}
}
