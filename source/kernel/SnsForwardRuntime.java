package com.zafiro.wxentry.kernel;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.view.*;
import de.robv.android.xposed.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Paid SNS long-press action. Finishes by opening a fresh native composer, never publishing. */
final class SnsForwardRuntime {
    static final int MENU_ID=0x57585311;
    static final Handler main=new Handler(Looper.getMainLooper());
    static final ExecutorService io=Executors.newSingleThreadExecutor();
    static final Map<MenuItem,Binding> bindings=Collections.synchronizedMap(new WeakHashMap<MenuItem,Binding>());
    static volatile Job active;static boolean ready;
    static final class Binding {
        final SnsForwardNative.Source source;final WeakReference<Activity> activity;final long at=SystemClock.elapsedRealtime();
        Binding(SnsForwardNative.Source s,Activity a){source=s;activity=new WeakReference<Activity>(a);}
    }
    static final class Job implements SnsForwardFiles.Guard {
        final Binding binding;final Activity activity;final SnsForwardDraft.Handoff handoff;
        final long until=SystemClock.elapsedRealtime()+180000L;
        volatile SnsForwardNative.Downloads downloads;volatile SnsForwardFiles files;volatile boolean stopped;
        ProgressDialog progress;
        Job(Binding b,Activity a){binding=b;activity=a;handoff=new SnsForwardDraft.Handoff(b.source.owner);}
        public boolean valid(){return !stopped&&!handoff.cancelled&&!activity.isFinishing()&&!activity.isDestroyed()&&SystemClock.elapsedRealtime()<until&&Kernel.snsForwardEnabled()&&binding.source.owner.equals(Kernel.receiptAccount());}
        void cancel(){stopped=true;handoff.cancel();SnsForwardNative.Downloads d=downloads;if(d!=null)d.detach();}
    }
    static void error(String stage,Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();String code=e.getMessage();StringBuilder frames=new StringBuilder();for(StackTraceElement f:e.getStackTrace()){if(frames.length()>420)break;frames.append(" at ").append(f.getClassName()).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());}Kernel.T("ARSNSFORWARD "+stage+" "+e.getClass().getSimpleName()+(code!=null&&code.matches("SNS_[A-Z_]{1,40}")?" "+code:"")+frames);}
    static void notice(Activity a,String text){if(a!=null&&!a.isFinishing()&&!a.isDestroyed())android.widget.Toast.makeText(a,text,android.widget.Toast.LENGTH_SHORT).show();}
    static void start(ClassLoader loader)throws Throwable{
        if(ready)return;SnsForwardNative.init(loader);
        Class<?> parent=SnsForwardNative.cls("com.tencent.mm.plugin.sns.ui.listener.h1");
        Method create=parent.getDeclaredMethod("onCreateContextMenu",ContextMenu.class,View.class,ContextMenu.ContextMenuInfo.class);
        final List<Method> selects=new ArrayList<Method>();
        for(String type:new String[]{"com.tencent.mm.plugin.sns.ui.listener.c","zk4.c","zk4.d","zk4.n0"}){
            Method method=SnsForwardNative.cls(type).getDeclaredMethod("onMMMenuItemSelected",MenuItem.class,int.class);if(method.getReturnType()!=void.class)throw new NoSuchMethodException("SNS menu result");selects.add(method);
        }
        XposedBridge.hookMethod(create,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
            if(!ready||!Kernel.snsForwardEnabled()||p.hasThrowable())return;
            try{if(!(p.args[0] instanceof Menu)||!(p.args[1] instanceof View))return;Menu menu=(Menu)p.args[0];if(menu.findItem(MENU_ID)!=null)return;
                View view=(View)p.args[1];Activity act=SnsForwardNative.activity(view.getContext());if(act==null)return;
                SnsForwardNative.Source source=SnsForwardNative.source(SnsForwardNative.info(p.thisObject,view));if(source==null)return;
                final Binding binding=new Binding(source,act);int group=menu.size()>0?menu.getItem(0).getGroupId():0;
                MenuItem item=menu.add(group,MENU_ID,0,"一键转发");bindings.put(item,binding);
                item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){Binding stored=bindings.remove(item);if(stored!=null)open(stored);return true;}});
                Kernel.T("ARSNSFORWARD menu-added modern="+p.thisObject.getClass().getName().startsWith("zk4."));
            }catch(Throwable failure){error("menu-error",failure);}
        }});
        for(Method method:selects)XposedBridge.hookMethod(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            if(!(p.args[0] instanceof MenuItem)||((MenuItem)p.args[0]).getItemId()!=MENU_ID)return;
            p.setResult(null);Binding binding=bindings.remove((MenuItem)p.args[0]);if(binding!=null)open(binding);else Kernel.T("ARSNSFORWARD stale menu binding");
        }});
        XposedBridge.hookAllMethods(Activity.class,"onPause",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){Job job=active;if(job!=null&&job.activity==p.thisObject&&!job.handoff.opened){job.cancel();finish(job,null);}}});
        final Runnable sweep=new Runnable(){public void run(){if(Kernel.APP_CTX==null){main.postDelayed(this,2000L);return;}final File cache=new File(Kernel.APP_CTX.getCacheDir(),"wxentry-sns-forward-v1");io.execute(new Runnable(){public void run(){try{SnsForwardFiles.cleanup(cache,System.currentTimeMillis());}catch(Throwable failure){error("expiry-cleanup",failure);}}});main.postDelayed(this,1800000L);}};
        MomentsSettings.forwardChanged=new Runnable(){public void run(){if(Kernel.snsForwardEnabled())return;final Job job=active;if(job!=null){job.cancel();main.post(new Runnable(){public void run(){finish(job,null);}});}}};
        sweep.run();ready=true;Kernel.T("ARSNSFORWARD ready premium=true editorOnly=true menus=classic+improved");
    }
    static void open(Binding binding){
        Activity act=binding.activity.get();if(act==null||act.isFinishing()||act.isDestroyed())return;
        if(!Kernel.snsForwardEnabled()){notice(act,Kernel.momentsPremiumEnabled()?"一键转发已关闭，请在朋友圈菜单开启":"一键转发朋友圈仅限高级版和 VIP 版");return;}
        if(!binding.source.owner.equals(Kernel.receiptAccount())||SystemClock.elapsedRealtime()-binding.at>120000L){notice(act,"菜单已失效，请重新长按目标朋友圈");return;}
        if(binding.source.kind==null){notice(act,"此类朋友圈暂不支持一键转发，请使用微信原生分享");return;}
        if(active!=null){notice(act,"正在准备另一条朋友圈，请先完成或取消");return;}
        final Job job=new Job(binding,act);active=job;
        ProgressDialog progress=new ProgressDialog(act);job.progress=progress;progress.setMessage("正在准备朋友圈内容…");progress.setCancelable(true);progress.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface ignored){job.cancel();}});progress.show();
        io.execute(new Runnable(){public void run(){prepare(job);}});
    }
    static void prepare(final Job job){
        try{
            final SnsForwardNative.Source source=job.binding.source;List<File> nativeFiles=new ArrayList<File>();boolean video=source.kind==SnsForwardDraft.Kind.VIDEO;
            if(source.kind==SnsForwardDraft.Kind.PHOTOS||video){
                job.downloads=new SnsForwardNative.Downloads(source);Set<String> requested=new HashSet<String>();
                while(true){
                    if(!job.valid())throw new IOException("SNS_CANCELLED");nativeFiles.clear();int loaded=0;
                    for(final Object media:source.media){
                        String id=SnsForwardNative.mediaId(media);File file=SnsForwardNative.ready(source,media,video);Boolean outcome=job.downloads.results.get(id);
                        if(requested.contains(id)&&Boolean.FALSE.equals(outcome))throw new IOException("SNS_DOWNLOAD_FAILED");
                        if(file!=null&&(!requested.contains(id)||Boolean.TRUE.equals(outcome)||video)){nativeFiles.add(file);loaded++;continue;}
                        nativeFiles.add(null);
                        if(!requested.contains(id)){requested.add(id);main.post(new Runnable(){public void run(){if(!job.valid())return;try{if(!SnsForwardNative.request(source,media,source.kind==SnsForwardDraft.Kind.VIDEO))job.downloads.results.put(SnsForwardNative.mediaId(media),false);}catch(Throwable failure){job.downloads.results.put(SnsForwardNative.mediaId(media),false);error("download-request-error",failure);}}});}
                    }
                    final int done=loaded;main.post(new Runnable(){public void run(){if(job.progress!=null&&job.progress.isShowing())job.progress.setMessage("正在加载朋友圈素材（"+done+"/"+source.media.size()+"）…");}});
                    if(loaded==source.media.size())break;Thread.sleep(350L);
                }
            }
            if(!job.valid())throw new IOException("SNS_CANCELLED");
            job.files=new SnsForwardFiles(new File(job.activity.getCacheDir(),"wxentry-sns-forward-v1"));
            List<String> paths=new ArrayList<String>();int index=0;for(File file:nativeFiles)paths.add(job.files.copy(file,video,index++,job).getPath());
            String thumb=null,md5=null;
            if(video){
                File cached=SnsForwardNative.thumbnail(source.media.get(0));if(cached!=null&&cached.isFile()&&SnsForwardFiles.extension(cached,false)!=null)thumb=job.files.copy(cached,false,index++,job).getPath();
                else thumb=videoThumbnail(new File(paths.get(0)),job.files.directory,job);
                md5=SnsForwardFiles.digest(new File(paths.get(0)),"MD5");
            }else if(source.kind==SnsForwardDraft.Kind.LINK&&!source.media.isEmpty()){
                File cached=SnsForwardNative.thumbnail(source.media.get(0));if(cached!=null&&cached.isFile()&&SnsForwardFiles.extension(cached,false)!=null)thumb=job.files.copy(cached,false,0,job).getPath();
            }
            if(source.kind==SnsForwardDraft.Kind.FINDER||source.kind==SnsForwardDraft.Kind.MPVIDEO||source.kind==SnsForwardDraft.Kind.LINK&&thumb==null){File cover=SnsForwardCard.preload(source.preview,job.files.directory,job);if(cover!=null)thumb=cover.getPath();}
            final SnsForwardDraft draft=new SnsForwardDraft(source.kind,source.description,source.title,source.url,source.kind==SnsForwardDraft.Kind.PHOTOS?source.media.size():video?1:0,paths,thumb,md5,source.cardXml);
            if(!draft.complete())throw new IOException("SNS_DRAFT_INCOMPLETE");
            main.post(new Runnable(){public void run(){
                try{
                    if(!job.valid()){job.cancel();finish(job,null);return;}
                    boolean opened=job.handoff.open(new SnsForwardDraft.Editor(){public boolean permitted(){return job.valid();}public String account(){return Kernel.receiptAccount();}public void open(SnsForwardDraft value)throws Exception{job.activity.startActivity(SnsForwardComposer.intent(value));}},draft);
                    if(opened){job.files.handoff();Kernel.T("ARSNSFORWARD composer-opened editorOnly=true kind="+draft.kind+" mediaCount="+draft.media.size());}
                    finish(job,opened?null:"内容准备已取消，请重试");
                }catch(Throwable failure){error("composer-error",failure);finish(job,"无法打开微信发表页面，请重试");}
            }});
        }catch(Throwable failure){error("prepare-error",failure);final String text=job.stopped?null:!job.binding.source.owner.equals(Kernel.receiptAccount())?"账号已切换，已停止准备":!Kernel.snsForwardEnabled()?"高级版授权已变化，已停止准备":SystemClock.elapsedRealtime()>=job.until?"素材加载超时，请检查网络后重试":"朋友圈素材准备失败，未打开发表页，请稍后重试";main.post(new Runnable(){public void run(){finish(job,text);}});}
        finally{SnsForwardNative.Downloads d=job.downloads;if(d!=null)d.detach();}
    }
    static String videoThumbnail(File video,File parent,SnsForwardFiles.Guard guard)throws Exception{
        if(!guard.valid())throw new IOException("SNS_CANCELLED");MediaMetadataRetriever media=new MediaMetadataRetriever();Bitmap frame=null,scaled=null;File file=new File(parent,"video-cover.jpg");
        try{media.setDataSource(video.getPath());frame=media.getFrameAtTime(0);if(frame==null)throw new IOException("SNS_VIDEO_THUMBNAIL");
            int width=frame.getWidth(),height=frame.getHeight();float ratio=Math.min(1f,640f/Math.max(width,height));scaled=Bitmap.createScaledBitmap(frame,Math.max(1,(int)(width*ratio)),Math.max(1,(int)(height*ratio)),true);
            try(FileOutputStream out=new FileOutputStream(file)){if(!scaled.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("SNS_VIDEO_THUMBNAIL");out.getFD().sync();}if(!guard.valid())throw new IOException("SNS_CANCELLED");return file.getPath();
        }finally{media.release();if(scaled!=null&&scaled!=frame)scaled.recycle();if(frame!=null)frame.recycle();}
    }
    static void finish(final Job job,String message){
        if(job.progress!=null){try{job.progress.dismiss();}catch(Throwable ignored){}job.progress=null;}
        if(active==job)active=null;if(message!=null)notice(job.activity,message);
        io.execute(new Runnable(){public void run(){try{if(job.files!=null)job.files.discard();}catch(Throwable failure){error("cleanup-error",failure);}}});
    }
}
