package com.zafiro.wxentry.kernel;

import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Gallery footer presentation only. Native listeners, models and storage are never changed. */
final class SnsLocalGallery {
    static final Map<View,State> states=new WeakHashMap<View,State>();
    static boolean ticking;static String policy="";
    static final class State {
        final WeakReference<TextView> likes,comments;final CharSequence likeText,commentText;
        final int likeVisible,commentVisible;String signature="";
        State(TextView likes,TextView comments){this.likes=new WeakReference<TextView>(likes);this.comments=new WeakReference<TextView>(comments);likeText=likes.getText();commentText=comments.getText();likeVisible=likes.getVisibility();commentVisible=comments.getVisibility();}
        void restore(){TextView l=likes.get(),c=comments.get();if(l!=null){l.setText(likeText);l.setVisibility(likeVisible);}if(c!=null){c.setText(commentText);c.setVisibility(commentVisible);}}
    }
    static void start()throws Throwable{
        for(String[] binding:new String[][]{{"r3","q3","i","j"},{"s3","e4","k","l"}}){
        Class<?> footer=SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui."+binding[0]),holder=SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui."+binding[1]);
        footer.getDeclaredField("f");footer.getDeclaredField("d");holder.getDeclaredField(binding[2]);holder.getDeclaredField(binding[3]);
        XposedBridge.hookMethod(footer.getDeclaredMethod("f"),new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){if(Looper.myLooper()==Looper.getMainLooper()){State old=states.remove(p.thisObject);if(old!=null)old.restore();}}
            protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()&&Looper.myLooper()==Looper.getMainLooper()&&p.thisObject instanceof View)render((View)p.thisObject);}
        });}
        Kernel.T("ARSNSLOCAL gallery-ready localOnly=true");
    }
    static void render(View footer){
        try{
            State old=states.get(footer);if(old!=null)old.restore();
            Object info=XposedHelpers.getObjectField(footer,"f");SnsLocalNative.Target target=SnsLocalNative.target(info);
            if(target==null||!SnsLocalSurfaces.access(target.owner)){states.remove(footer);return;}
            SnsLocalData data=SnsLocalStore.load(target.owner);SnsLocalData.Post post=data.posts.get(target.key);
            if(post==null||(!data.likesOn&&!data.commentsOn)){states.remove(footer);return;}
            Object social=XposedHelpers.callStaticMethod(SnsLocalNative.cls("com.tencent.mm.plugin.sns.model.w5"),"e",info);
            if(social==null){states.remove(footer);return;}
            Set<String> genuine=new HashSet<String>();Object list=XposedHelpers.getObjectField(social,"LikeUserList");
            if(list instanceof List)for(Object item:(List<?>)list){Object id=XposedHelpers.getObjectField(item,"d");if(id instanceof String)genuine.add((String)id);}
            int nativeLikes=Math.max(genuine.size(),((Number)XposedHelpers.getObjectField(social,"LikeCount")).intValue());
            int nativeComments=Math.max(0,((Number)XposedHelpers.getObjectField(social,"CommentCount")).intValue());
            int extra=SnsLocalProjection.extraLikes(data,post,genuine),extraComments=data.commentsOn?post.comments.size():0;
            if(extra==0&&extraComments==0){states.remove(footer);return;}
            boolean modern=footer.getClass().getName().equals("com.tencent.mm.plugin.sns.ui.s3");
            Object holder=XposedHelpers.getObjectField(footer,"d");TextView likes=(TextView)XposedHelpers.getObjectField(holder,modern?"k":"i"),comments=(TextView)XposedHelpers.getObjectField(holder,modern?"l":"j");
            if(old==null||old.likes.get()!=likes||old.comments.get()!=comments)old=new State(likes,comments);
            states.put(footer,old);if(extra>0){likes.setText(String.valueOf(nativeLikes+extra));likes.setVisibility(View.VISIBLE);}if(extraComments>0){comments.setText(String.valueOf(nativeComments+extraComments));comments.setVisibility(View.VISIBLE);}
            String signature=target.key+"/"+nativeLikes+"/"+extra+"/"+nativeComments+"/"+extraComments;
            if(!signature.equals(old.signature)){old.signature=signature;Kernel.T("ARSNSLOCAL gallery-rendered genuine="+nativeLikes+" local="+extra+" total="+(nativeLikes+extra));}watch();
        }catch(Throwable failure){State old=states.remove(footer);if(old!=null)old.restore();SnsLocalRuntime.log("gallery-error",failure);}
    }
    static void refresh(){for(View footer:new ArrayList<View>(states.keySet()))if(footer!=null)try{XposedHelpers.callMethod(footer,"f");}catch(Throwable failure){State old=states.remove(footer);if(old!=null)old.restore();}watch();}
    static void watch(){if(ticking||states.isEmpty())return;ticking=true;SnsLocalRuntime.main.postDelayed(()->{ticking=false;if(states.isEmpty())return;String now=Kernel.receiptAccount()+"/"+Kernel.momentsPremiumEnabled()+"/"+SnsLocalStore.revision();if(!now.equals(policy)){policy=now;refresh();}watch();},2000L);}
}
