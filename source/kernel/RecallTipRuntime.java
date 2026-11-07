package com.zafiro.wxentry.kernel;

import android.content.ContentValues;
import android.database.Cursor;

/** Existing recall entry point: record the source identity; never insert another chat message. */
final class RecallTipRuntime {
    static final class DbOwner { final String account;final java.lang.ref.WeakReference<Object> db;DbOwner(String a,Object d){account=a;db=new java.lang.ref.WeakReference<Object>(d);} }
    static volatile DbOwner source=new DbOwner("",null);
    static Object sourceDb(String account) throws Throwable { DbOwner owner=source;Object db=account.equals(owner.account)?owner.db.get():null;return db!=null?db:Kernel.db0(); }
    static boolean enabled() { return !Kernel.MODULE_OFF&&!Kernel.NO_TIP&&!Kernel.f(Kernel.AR_OFF_PATH); }
    static boolean insert(Object db,String localId,Object update) {
        if(!enabled()||db==null||localId==null||!(update instanceof ContentValues))return false;
        Cursor cursor=null;
        try {
            long id=Long.parseLong(localId);if(id<=0)return false;
            String account=Kernel.receiptAccount();if(!ReceiptConfig.validId(account))return false;
            cursor=Kernel.rawQuery(db,"select talker,createTime,type,isSend,msgSvrId from message where msgId=? limit 1",new String[]{Long.toString(id)});
            if(cursor==null||!cursor.moveToFirst())return false;
            String talker=cursor.getString(0);long created=cursor.getLong(1),server=cursor.getLong(4);int type=cursor.getInt(2),sent=cursor.getInt(3);
            cursor.close();cursor=null;int low=type&0xffff;
            if(sent!=0||low==10000||low==10002||!enabled()||!account.equals(Kernel.receiptAccount()))return false;
            RecallMarkStore.Entry entry=new RecallMarkStore.Entry(account,talker,id,server,created,type);
            if(!entry.valid())return false;
            source=new DbOwner(account,db);
            if(!RecallMarkStore.mark(entry))return false;
            RecallMarkLayout.changed(entry);
            Kernel.T("ARMARK recorded id="+id);return true;
        } catch(Throwable failure) {
            Kernel.T("ARMARK record skipped "+failure.getClass().getSimpleName());return false;
        } finally { if(cursor!=null)try { cursor.close(); }catch(Throwable ignored) { } }
    }
}
