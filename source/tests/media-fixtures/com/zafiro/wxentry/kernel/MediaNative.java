package com.zafiro.wxentry.kernel;
import java.io.File;
import com.tencent.mm.plugin.msg.MsgIdTalker;
final class MediaNative {
 static ClassLoader cl;static Message msg;static boolean available=true,originalDone,compressedDone;
 static final class Message {Object db,nativeMessage;String account="0123456789abcdef0123456789abcdef",talker="peer";long local=10,server=123,received=System.currentTimeMillis();int type=43,appType;boolean media(){return MediaMessagePolicy.media(type,appType,0);}}
 static class State {File full;Message message;long length;boolean complete;}
 public static class Info {public long a=81;}
 public static class Provider {public Manager Hj(){return manager;}}
 public static class Manager {int requests,cancels,type,cancelType;long id;Object listener;
  public int b(long image,MsgIdTalker t,int compress,Object key,int priority,ma0.w callback,int scene,boolean b){requests++;type=compress;id=image;listener=callback;return 0;}
  public boolean g(long image,MsgIdTalker t,int compress,ma0.w callback){cancels++;cancelType=compress;return true;}
 }
 static Manager manager=new Manager();
 static Message read(Object db,String talker,long sid){return msg!=null&&talker.equals(msg.talker)&&sid==msg.server?msg:null;}
 static Message current(RecallMediaCache.Entry e){return null;}static State state(Message m){return null;}static boolean request(State s){return false;}
 static Class<?> cls(String name)throws ClassNotFoundException{return Class.forName(name);}
 static Object imageInfo(Message m){return new Info();}static Object originalInfo(Object b){return new Info();}
 static boolean originalAvailable(Object b){return available;}static boolean originalReady(Message m,Object b){return originalDone;}
 static Object service(String name){return new Provider();}
}
