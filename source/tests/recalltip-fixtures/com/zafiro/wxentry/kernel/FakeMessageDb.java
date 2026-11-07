package com.zafiro.wxentry.kernel;
public class FakeMessageDb {
 public final java.util.Map<Long,android.content.ContentValues> rows=new java.util.LinkedHashMap<>();public int queries,closed;public Runnable onQuery;
 public synchronized void insert(android.content.ContentValues row){Long id=row.getAsLong("msgId");if(rows.containsKey(id))throw new IllegalStateException("duplicate primary key msgId");rows.put(id,new android.content.ContentValues(row));}
 public synchronized android.database.Cursor query(String sql,String[] args){queries++;if(!sql.equals("select talker,createTime,type,isSend,msgSvrId from message where msgId=? limit 1"))throw new AssertionError("Only bounded source lookup allowed");final android.content.ContentValues row=rows.get(Long.parseLong(args[0]));if(onQuery!=null)onQuery.run();return new android.database.Cursor(){String[] columns={"talker","createTime","type","isSend","msgSvrId"};boolean done;public boolean moveToFirst(){return row!=null;}public String getString(int i){return row.getAsString(columns[i]);}public long getLong(int i){Long v=row.getAsLong(columns[i]);return v==null?0:v;}public int getInt(int i){return (int)getLong(i);}public void close(){if(!done){closed++;done=true;}}};}
}

