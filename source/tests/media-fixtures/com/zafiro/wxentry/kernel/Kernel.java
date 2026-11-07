package com.zafiro.wxentry.kernel;
public class Kernel {
 static boolean MODULE_OFF,NO_TIP,off,failTip;static int tips;static String WXDIR,AR_OFF_PATH="antirevoke.off",owner="account";
 static java.util.List<String> logs=new java.util.ArrayList<>();
 static boolean f(String path){return off;}static String receiptAccount(){return owner;}
 static void T(String s){logs.add(s);}static java.util.List<Class<?>> discoverDbImpls(ClassLoader l){return java.util.Collections.emptyList();}
 static void insertTipRow(Object db,String mid,Object cv){tips++;if(failTip)throw new IllegalStateException("private file name");}
 static void reset(){MODULE_OFF=NO_TIP=off=failTip=false;tips=0;owner="account";logs.clear();}
}
