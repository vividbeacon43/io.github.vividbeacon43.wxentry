package com.zafiro.wxentry.kernel;
import android.database.Cursor;
import android.content.ContentValues;
final class Kernel {
    static boolean MODULE_OFF,NO_TIP,off,failQuery,failTip,failRead;
    static final String AR_OFF_PATH="test-antirevoke.off";
    static String talker="peer",edition="free";
    static long server=123,local=10;
    static int type=34,sent,queries,closed,tips;
    static java.util.List<String> logs=new java.util.ArrayList<String>();
    static String lastToast;
    static void toast(android.app.Activity activity,String text) { lastToast=text; }
    static boolean f(String path) { if(!AR_OFF_PATH.equals(path))throw new AssertionError();return off; }
    static Object db0() { return Kernel.class; }
    static Cursor rawQuery(Object db,String sql,String[] args) {
        queries++;
        if(failQuery)throw new IllegalStateException("private content must never appear in logs");
        if(db!=Kernel.class || !sql.contains("talker=? and msgSvrId=?") || args.length!=2)throw new AssertionError();
        final boolean matched=talker.equals(args[0]) && Long.toString(server).equals(args[1]);
        return new Cursor() {
            boolean done;
            public boolean moveToFirst() { return matched; }
            public int getInt(int c) { if(failRead)throw new IllegalStateException();return c==1?type:sent; }
            public long getLong(int c) { return local; }
            public void close() { if(done)throw new AssertionError("double close");done=true;closed++; }
        };
    }
    static boolean insertTipRow(Object db,String mid,Object cv) {
        tips++;
        if(failTip)throw new IllegalStateException("private content must never appear in logs");
        if(!"10".equals(mid)||!((ContentValues)cv).containsKey("content"))throw new AssertionError();
        return true;
    }
    static void T(String value) { logs.add(value); }
    static void reset() {
        MODULE_OFF=NO_TIP=off=failQuery=failTip=failRead=false;
        talker="peer";server=123;local=10;type=34;sent=queries=closed=tips=0;logs.clear();lastToast=null;
    }
}
