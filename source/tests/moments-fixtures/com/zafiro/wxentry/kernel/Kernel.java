package com.zafiro.wxentry.kernel;
final class Kernel {
 static String WXDIR,owner="wxid_moments_a",edition="plus";static boolean verified=true,MODULE_OFF;
 static String receiptAccount(){return owner;}
 static boolean momentsPremiumEnabled(){return !MODULE_OFF&&verified&&(edition.equals("plus")||edition.equals("vip"));}
 static boolean snsAdBlockEnabled(){return momentsPremiumEnabled()&&MomentsSettings.configured(MomentsConfig.Feature.AD_BLOCK);}
 static void T(String text){}
}
