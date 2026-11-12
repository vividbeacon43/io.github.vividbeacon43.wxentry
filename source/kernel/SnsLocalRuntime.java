package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/** Paid, local-only post menus and view binding. Never changes native SNS records. */
final class SnsLocalRuntime {
    static final int LIKES_ID=0x57585321,COMMENTS_ID=0x57585322,CLEAR_ID=0x57585323;
    static final String OWN_TAG="wxentry.sns.local.v1";
    static final Handler main=new Handler(Looper.getMainLooper());
    static final ExecutorService io=Executors.newSingleThreadExecutor();
    static final Map<View,Bound> states=new WeakHashMap<View,Bound>();
    static final Map<MenuItem,MenuBinding> menus=Collections.synchronizedMap(new WeakHashMap<MenuItem,MenuBinding>());
    static boolean ready,ticking;static String policy="";
    static final class Bound {
        final WeakReference<View> item;final String owner,key;
        final List<WeakReference<View>> added=new ArrayList<WeakReference<View>>();
        WeakReference<View> nativeRoot=new WeakReference<View>(null);
        String signature="";int likesVisible=20;boolean commentsExpanded;
        WeakReference<View> replacedLike=new WeakReference<View>(null);int replacedVisibility;
        Bound(View item,SnsLocalNative.Target target){this.item=new WeakReference<View>(item);owner=target.owner;key=target.key;}
        void restoreLike(){View view=replacedLike.get();if(view!=null)view.setVisibility(replacedVisibility);replacedLike=new WeakReference<View>(null);}
        void hideLike(View view){replacedLike=new WeakReference<View>(view);replacedVisibility=view.getVisibility();view.setVisibility(View.GONE);}
        void rehideLike(){View view=replacedLike.get();if(view!=null)view.setVisibility(View.GONE);}
        void clear(){restoreLike();for(WeakReference<View> ref:added){View view=ref.get();if(view!=null&&OWN_TAG.equals(view.getTag())&&view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);}added.clear();signature="";}
        boolean attached(){if(added.isEmpty())return false;for(WeakReference<View> r:added){View v=r.get();if(v==null||v.getParent()==null)return false;}return true;}
        void added(View view){view.setTag(OWN_TAG);added.add(new WeakReference<View>(view));}
    }
    static final class MenuBinding {
        final SnsLocalNative.Target target;final WeakReference<Activity> act;final int action;final long at=SystemClock.elapsedRealtime();
        MenuBinding(SnsLocalNative.Target t,Activity a,int action){target=t;act=new WeakReference<Activity>(a);this.action=action;}
    }
    static void log(String step,Throwable error){while(error.getCause()!=null&&error.getCause()!=error)error=error.getCause();Kernel.T("ARSNSLOCAL "+step+" "+error.getClass().getSimpleName());}
    static boolean valid(Bound bound,SnsLocalData.Feature feature){
        if(!SnsLocalStore.enabled(feature)||!bound.owner.equals(Kernel.receiptAccount()))return false;
        try{View item=bound.item.get();SnsLocalNative.Target now=item==null?null:SnsLocalNative.itemTarget(item);return now!=null&&now.owner.equals(bound.owner)&&now.key.equals(bound.key);}catch(Throwable unavailable){return false;}
    }
    static void start(ClassLoader loader)throws Throwable{
        if(ready)return;SnsLocalNative.init(loader);
        Method measure=SnsLocalNative.itemType.getDeclaredMethod("q");
        Method bind=SnsLocalNative.itemType.getDeclaredMethod("h",SnsLocalNative.cls("qw5.s0"),SnsLocalNative.cls("el4.b"),int.class);
        XposedBridge.hookMethod(bind,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            if(!(p.thisObject instanceof View))return;final View item=(View)p.thisObject;
            if(Looper.myLooper()!=Looper.getMainLooper())return;
            Bound old=states.get(item);if(old==null)return;
            try{Object model=p.args[1]==null?null:XposedHelpers.callMethod(p.args[1],"n");SnsLocalNative.Target target=model==null?null:SnsLocalNative.target(XposedHelpers.callMethod(model,"Y0"));if(target==null||!old.owner.equals(target.owner)||!old.key.equals(target.key)){old.clear();states.remove(item);}}catch(Throwable failure){old.clear();states.remove(item);}
        }});
        XposedBridge.hookMethod(measure,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(Looper.myLooper()==Looper.getMainLooper()&&p.thisObject instanceof View){Bound old=states.get((View)p.thisObject);if(old!=null)old.restoreLike();}}protected void afterHookedMethod(MethodHookParam p){
            if(!(p.thisObject instanceof View))return;final View item=(View)p.thisObject;
            if(p.hasThrowable()){main.post(new Runnable(){public void run(){Bound old=states.get(item);if(old!=null)old.clear();}});return;}
            if(Looper.myLooper()==Looper.getMainLooper())render(item,false);else main.post(new Runnable(){public void run(){if(render(item,false))item.requestLayout();}});
        }});
        Method reset=SnsLocalNative.interactionType.getDeclaredMethod("reset");
        XposedBridge.hookMethod(reset,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){final Object nativeView=p.thisObject;Runnable clear=new Runnable(){public void run(){for(Bound bound:new ArrayList<Bound>(states.values()))if(bound.nativeRoot.get()==nativeView)bound.clear();}};if(Looper.myLooper()==Looper.getMainLooper())clear.run();else main.post(clear);}});
        Class<?> listener=SnsLocalNative.cls("com.tencent.mm.plugin.sns.ui.listener.h1");
        Method create=listener.getDeclaredMethod("onCreateContextMenu",ContextMenu.class,View.class,ContextMenu.ContextMenuInfo.class);
        XposedBridge.hookMethod(create,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
            if(!ready||!Kernel.momentsPremiumEnabled()||p.hasThrowable())return;
            try{if(!(p.args[0] instanceof Menu)||!(p.args[1] instanceof View))return;Menu menu=(Menu)p.args[0];View view=(View)p.args[1];Activity act=SnsLocalNative.activity(view.getContext());if(act==null)return;
                SnsLocalNative.Target target=SnsLocalNative.target(SnsLocalNative.info(p.thisObject,view));if(target==null)return;
                SnsLocalData data=SnsLocalStore.load(target.owner);
                if(data.likesOn)add(menu,act,target,LIKES_ID,"伪集赞");if(data.commentsOn)add(menu,act,target,COMMENTS_ID,"伪评论");if(data.posts.containsKey(target.key))add(menu,act,target,CLEAR_ID,"清除本条本地互动");
            }catch(Throwable failure){log("menu-error",failure);}
        }});
        for(String name:new String[]{"com.tencent.mm.plugin.sns.ui.listener.c","zk4.c","zk4.d","zk4.n0"}){
            Method select=SnsLocalNative.cls(name).getDeclaredMethod("onMMMenuItemSelected",MenuItem.class,int.class);
            XposedBridge.hookMethod(select,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(!(p.args[0] instanceof MenuItem))return;MenuItem item=(MenuItem)p.args[0];if(item.getItemId()!=LIKES_ID&&item.getItemId()!=COMMENTS_ID&&item.getItemId()!=CLEAR_ID)return;p.setResult(null);select(menus.remove(item));}});
        }
        XposedBridge.hookAllMethods(Activity.class,"onResume",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof Activity)SnsLocalSurfaces.resumed((Activity)p.thisObject);main.post(new Runnable(){public void run(){refresh();}});}});
        SnsLocalStore.changed=new Runnable(){public void run(){main.post(new Runnable(){public void run(){refresh();}});}};
        ready=true;Kernel.T("ARSNSLOCAL ready localOnly=true nativeRecordsUnchanged=true");
        try{SnsLocalSurfaces.start();}catch(Throwable failure){log("surfaces-unavailable",failure);}
        try{SnsLocalGallery.start();}catch(Throwable failure){log("gallery-unavailable",failure);}
    }
    static void add(Menu menu,Activity act,SnsLocalNative.Target target,int id,String title){
        if(menu.findItem(id)!=null)return;int group=menu.size()>0?menu.getItem(0).getGroupId():0;MenuItem item=menu.add(group,id,0,title);menus.put(item,new MenuBinding(target,act,id));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){public boolean onMenuItemClick(MenuItem item){select(menus.remove(item));return true;}});
    }
    static void select(MenuBinding binding){
        if(binding==null)return;Activity act=binding.act.get();if(act==null||act.isFinishing()||act.isDestroyed())return;
        if(!Kernel.momentsPremiumEnabled()||!binding.target.owner.equals(Kernel.receiptAccount())||SystemClock.elapsedRealtime()-binding.at>120000){SnsLocalSettings.toast(act,"菜单已失效，请重新长按目标朋友圈");return;}
        if(binding.action==CLEAR_ID){SnsLocalPicker.clearPost(act,binding.target);return;}
        SnsLocalData.Feature feature=binding.action==LIKES_ID?SnsLocalData.Feature.LIKES:SnsLocalData.Feature.COMMENTS;
        if(!SnsLocalStore.enabled(feature)){SnsLocalSettings.toast(act,"请先在朋友圈菜单打开对应开关");return;}SnsLocalPicker.choose(act,binding.target,feature);
    }
    static boolean render(View item,boolean force){
        Bound bound=states.get(item);
        try{
            SnsLocalNative.Target target=SnsLocalNative.itemTarget(item);
            if(target==null){if(bound!=null){bound.clear();states.remove(item);}return false;}
            if(bound==null||!bound.owner.equals(target.owner)||!bound.key.equals(target.key)){if(bound!=null)bound.clear();bound=new Bound(item,target);states.put(item,bound);}
            SnsLocalData data=SnsLocalStore.load(target.owner);SnsLocalData.Post post=data.posts.get(target.key);
            boolean allowed=Kernel.momentsPremiumEnabled()&&target.owner.equals(Kernel.receiptAccount());
            if(!allowed||post==null||(!data.likesOn||post.likes.isEmpty())&&(!data.commentsOn||post.comments.isEmpty())){boolean had=!bound.added.isEmpty();bound.clear();watch();return had;}
            ViewGroup host=(ViewGroup)XposedHelpers.callMethod(item,"getCommentContainer");View nativeRoot=(View)XposedHelpers.callMethod(item,"getCommentView");
            View nativeLike=null,nativeComments=null;
            if(nativeRoot!=null){nativeLike=(View)XposedHelpers.callMethod(nativeRoot,"getLikeLayout");nativeComments=(View)XposedHelpers.callMethod(nativeRoot,"getCommentLayout");}
            boolean realLikes=nativeLike!=null&&nativeLike.getVisibility()==View.VISIBLE,realComments=nativeComments instanceof ViewGroup&&((ViewGroup)nativeComments).getChildCount()>0;
            Set<String> genuine=realLikes?SnsLocalNative.realLikes(SnsLocalNative.model(item)):Collections.<String>emptySet();
            List<SnsLocalData.Person> likes=new ArrayList<SnsLocalData.Person>();if(data.likesOn)for(SnsLocalData.Person person:post.likes)if(SnsLocalSelection.selectable(person.id)&&!genuine.contains(person.id))likes.add(person);
            List<SnsLocalData.Comment> comments=data.commentsOn?post.comments:Collections.<SnsLocalData.Comment>emptyList();
            if(likes.isEmpty()&&comments.isEmpty()){boolean had=!bound.added.isEmpty();bound.clear();return had;}
            CharSequence genuineText=realLikes?SnsLocalNative.likesText(nativeLike):null;
            String signature=SnsLocalStore.revision()+"/"+System.identityHashCode(nativeRoot)+"/"+realLikes+"/"+realComments+"/"+genuine.hashCode()+"/"+(genuineText==null?0:genuineText.toString().hashCode())+"/"+bound.likesVisible+"/"+bound.commentsExpanded;
            if(!force&&signature.equals(bound.signature)&&bound.attached()){if(realLikes&&!likes.isEmpty())bound.hideLike(nativeLike);return false;}
            bound.clear();bound.nativeRoot=new WeakReference<View>(nativeRoot);
            if(!likes.isEmpty()||!comments.isEmpty())SnsLocalPanel.mount(host,nativeRoot,nativeLike,nativeComments,realLikes,realComments,bound,likes,comments);
            bound.signature=signature;Kernel.T("ARSNSLOCAL rendered likes="+likes.size()+" comments="+comments.size()+" native="+(nativeRoot!=null));watch();return true;
        }catch(Throwable failure){if(bound!=null)bound.clear();log("bind-error",failure);return false;}
    }
    static void expand(final Bound bound,boolean likes){if(likes)bound.likesVisible+=100;else bound.commentsExpanded=true;View item=bound.item.get();if(item!=null){bound.restoreLike();if(render(item,true))item.requestLayout();}}
    static void refresh(){for(View item:new ArrayList<View>(states.keySet()))if(item!=null)try{XposedHelpers.callMethod(item,"q");item.requestLayout();}catch(Throwable failure){Bound old=states.get(item);if(old!=null)old.clear();}SnsLocalSurfaces.refresh();SnsLocalGallery.refresh();watch();}
    static void watch(){if(ticking||states.isEmpty())return;ticking=true;main.postDelayed(new Runnable(){public void run(){ticking=false;if(states.isEmpty())return;String now=Kernel.receiptAccount()+"/"+Kernel.momentsPremiumEnabled()+"/"+SnsLocalStore.revision();if(!now.equals(policy)){policy=now;refresh();}watch();}},2000L);}
}
