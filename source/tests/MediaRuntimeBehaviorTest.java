package com.zafiro.wxentry.kernel;
import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import de.robv.android.xposed.*;
public final class MediaRuntimeBehaviorTest {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static XC_MethodHook hook;
 static boolean revoke()throws Throwable{XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{"peer",123L,new com.tencent.mm.modelbase.p0(),"private revoke body",null,null};hook.invoke(p);return p.stopped;}
 static void reset(){Kernel.reset();MediaNative.msg=new MediaNative.Message();ReceiptStore.on=ReceiptStore.allow=true;MediaNative.manager=new MediaNative.Manager();MediaNative.available=true;MediaNative.originalDone=MediaNative.compressedDone=false;OriginalImageRuntime.jobs.clear();}
 static OriginalImageRuntime.Job job(){MediaNative.msg.type=3;OriginalImageRuntime.Job j=new OriginalImageRuntime.Job(MediaNative.msg);OriginalImageRuntime.jobs.put(j.key,j);return j;}
 public static void main(String[] args)throws Throwable{
  Path root=Files.createTempDirectory(Paths.get(args[0]),"media-runtime-");Kernel.WXDIR=root.toString()+File.separator;
  try{
   RecallMediaCache prior=new RecallMediaCache(new File(Kernel.WXDIR,"recall-media-v1"),new RecallMediaCache.Clock(){public long now(){return System.currentTimeMillis();}});RecallMediaCache.Entry waiting=prior.register("0123456789abcdef0123456789abcdef","restart-peer",81,181,43,System.currentTimeMillis());
   MediaRecallRuntime.start(MediaRuntimeBehaviorTest.class.getClassLoader());MediaRecallRuntime.io.submit(new Runnable(){public void run(){}}).get();MediaRecallRuntime.io.shutdownNow();
   ok(MediaRecallRuntime.jobs.containsKey(waiting.key),"restart resumes a pending message within its original three-minute deadline");
   hook=XposedBridge.hooks.get(Class.forName("w41.u").getDeclaredMethod("c",String.class,long.class,com.tencent.mm.modelbase.p0.class,String.class,String.class,String.class));
   ok(hook!=null,"native revoke hook installed before row mutation/cleanup event");
   for(int type:new int[]{43,62,49}){reset();MediaNative.msg.type=type;MediaNative.msg.appType=6;ok(revoke()&&Kernel.tips==1,"video/short-video/attachment retained and annotated "+type);}
   reset();Kernel.off=true;ok(!revoke(),"anti-recall off leaves native revoke intact");
   reset();Kernel.MODULE_OFF=true;ok(!revoke(),"module off leaves native revoke intact");
   reset();Kernel.NO_TIP=true;ok(revoke()&&Kernel.tips==0,"no-tip still retains video");
   reset();Kernel.failTip=true;ok(revoke(),"failed annotation cannot delete retained video");ok(!Kernel.logs.toString().contains("private"),"private message/filename is absent from diagnostics");
   reset();RecallMediaCache cache=MediaRecallRuntime.cache;MediaRecallRuntime.cache=null;ok(revoke()&&Kernel.tips==1,"cache failure cannot suppress interception or annotation");MediaRecallRuntime.cache=cache;
   for(int type:new int[]{1,3,34,10000}){reset();MediaNative.msg.type=type;ok(!revoke(),"unrelated type stays in its native/other adapter "+type);}
   reset();MediaNative.msg.type=49;MediaNative.msg.appType=2001;ok(!revoke(),"red packet not treated as file");
   reset();MediaNative.msg=null;ok(!revoke(),"self/missing identity unresolved by native reader stays native");
   reset();Kernel.off=true;ok(OriginalImageRuntime.enabled(),"original downloader independent of anti-recall toggle");OriginalImageRuntime.Job j=job();OriginalImageRuntime.request(j);
   ok(MediaNative.manager.requests==1&&MediaNative.manager.type==1&&MediaNative.manager.id==81,"native original record and type 1 selected");
   ok(j.running,"native download tracked until a genuine callback");OriginalImageRuntime.detach(j);ok(MediaNative.manager.cancels==1&&MediaNative.manager.cancelType==1,"detach only our original-mode listener");
   reset();ReceiptStore.allow=false;j=job();OriginalImageRuntime.request(j);ok(MediaNative.manager.requests==0,"blacklist or unmatched whitelist blocks submission");
   reset();j=job();Kernel.owner="other-account";OriginalImageRuntime.request(j);ok(MediaNative.manager.requests==0,"account switch after queueing blocks submission");
   reset();j=job();ReceiptStore.on=false;OriginalImageRuntime.request(j);ok(MediaNative.manager.requests==0,"switch off after queueing blocks submission");
   reset();j=job();MediaNative.originalDone=true;OriginalImageRuntime.request(j);ok(j.done&&MediaNative.manager.requests==0,"existing completed original does not download twice");
   reset();j=job();MediaNative.compressedDone=true;OriginalImageRuntime.request(j);ok(MediaNative.manager.requests==1&&!j.done,"compressed image completion is not mistaken for original completion");OriginalImageRuntime.detach(j);
   reset();j=job();Kernel.MODULE_OFF=true;OriginalImageRuntime.request(j);ok(MediaNative.manager.requests==0,"module disable blocks independent original mode");
   System.out.println("PASS "+checks+" native media interception, original mode, queued list/owner and failure behavior checks");
  }finally{MediaRecallRuntime.io.shutdownNow();OriginalImageRuntime.io.shutdownNow();}
 }
}
