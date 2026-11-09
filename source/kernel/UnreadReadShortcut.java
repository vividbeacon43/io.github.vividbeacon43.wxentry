package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** The native unread sheet owns this view and its lifetime. No window polling or replacement. */
final class UnreadReadShortcut {
    static final String ACTIVITY="com.tencent.mm.ui.conversation.unreadmode.UnreadConversationActivity";
    static final String FRAGMENT=ACTIVITY+"$UnreadConversationFmUI";
    static final String TAG="wxentry.unread.mark-all";
    static final AtomicBoolean BUSY=new AtomicBoolean();
    static boolean ready;
    static int id(Resources resources,String name,String kind) { return resources.getIdentifier(name,kind,"com.tencent.mm"); }
    static void start(ClassLoader loader)throws Throwable {
        if(ready)return;
        Method init=Class.forName(FRAGMENT,false,loader).getDeclaredMethod("initView");
        XposedBridge.hookMethod(init,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
            if(p.hasThrowable()||Kernel.MODULE_OFF)return;
            try { attach((Activity)XposedHelpers.callMethod(p.thisObject,"getActivity"),(View)XposedHelpers.callMethod(p.thisObject,"getView")); }
            catch(Throwable failure){error(failure);}
        }});
        Method resume=Class.forName(ACTIVITY,false,loader).getDeclaredMethod("onResume");
        XposedBridge.hookMethod(resume,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
            if(p.hasThrowable()||Kernel.MODULE_OFF)return;
            Activity activity=(Activity)p.thisObject;attach(activity,activity.getWindow().getDecorView());
        }});
        ready=true;Kernel.T("UNREAD shortcut ready editions=all native=unreadmode");
        java.lang.ref.WeakReference<Activity> current=Kernel.CUR_ACT;
        final Activity activity=current==null?null:current.get();
        if(activity!=null&&ACTIVITY.equals(activity.getClass().getName()))activity.runOnUiThread(new Runnable(){public void run(){attach(activity,activity.getWindow().getDecorView());}});
    }
    static void attach(final Activity activity,View root) {
        if(activity==null||root==null||activity.isFinishing()||activity.isDestroyed()||Kernel.MODULE_OFF||!ACTIVITY.equals(activity.getClass().getName()))return;
        try {
            Resources resources=activity.getResources();int hostId=id(resources,"atg","id"),backId=id(resources,"wrn","id"),boxId=id(resources,"wro","id"),layout=id(resources,"ezn","layout"),done=id(resources,"icons_outlined_done","raw");
            if(hostId==0||backId==0||boxId==0||layout==0||done==0)return;
            View found=root.findViewById(hostId),source=root.findViewById(backId),sourceBox=root.findViewById(boxId);
            if(!(found instanceof FrameLayout)||!(source instanceof RelativeLayout)||source.getParent()!=found||!(sourceBox instanceof ViewGroup)||root.findViewWithTag(TAG)!=null)return;
            final FrameLayout host=(FrameLayout)found;final ViewGroup sourceButton=(ViewGroup)source;
            View copiedPage=LayoutInflater.from(activity).inflate(layout,null,false);
            View copy=copiedPage.findViewById(backId);View copiedBox=copiedPage.findViewById(boxId);
            if(!(copy instanceof RelativeLayout)||!(copiedBox instanceof ViewGroup)||!(copy.getParent() instanceof ViewGroup))return;
            final RelativeLayout button=(RelativeLayout)copy;final View box=copiedBox;
            final TextView sourceLabel=label(sourceButton),text=label(button);ImageView image=image((ViewGroup)box),nativeImage=image((ViewGroup)sourceBox);
            if(sourceLabel==null||text==null||image==null||nativeImage==null)return;
            ((ViewGroup)copy.getParent()).removeView(copy);
            remapIds(button,new HashMap<Integer,Integer>());
            text.setText("一键已读");text.setSingleLine(true);image.setRotation(0);image.setImageResource(done);
            // The native arrow's rotated 8x16 box is 16px wide in dp terms. Keep that same footprint.
            ViewGroup.LayoutParams iconParams=image.getLayoutParams();ViewGroup.LayoutParams nativeParams=nativeImage.getLayoutParams();
            int side=Math.max(nativeParams.width,nativeParams.height);if(side>0){iconParams.width=side;iconParams.height=side;image.setLayoutParams(iconParams);}
            button.setId(View.generateViewId());button.setTag(TAG);button.setContentDescription("一键已读");button.setClickable(true);button.setFocusable(true);
            disableChildActions(button);button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(source.getLayoutParams());params.gravity=Gravity.RIGHT|Gravity.TOP;
            if(source.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)source.getLayoutParams();params.topMargin=margins.topMargin;params.bottomMargin=margins.bottomMargin;params.rightMargin=margins.leftMargin;}
            final View originalBox=sourceBox;
            View.OnLayoutChangeListener mirror=new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){mirror(originalBox,box,sourceButton,button);mirror(sourceLabel,text,sourceButton,button);}};
            button.addOnLayoutChangeListener(mirror);sourceButton.addOnLayoutChangeListener(mirror);
            button.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
                if(Kernel.MODULE_OFF||activity.isFinishing()||activity.isDestroyed()||!ReceiptConfig.validId(Kernel.receiptAccount())||!BUSY.compareAndSet(false,true))return;
                button.setEnabled(false);button.setAlpha(.45f);Kernel.T("UNREAD shortcut clicked");
                try { Kernel.markUnreadShortcut(activity,new Runnable(){public void run(){BUSY.set(false);button.post(new Runnable(){public void run(){button.setEnabled(true);button.setAlpha(1f);}});}}); }
                catch(Throwable failure){BUSY.set(false);button.setEnabled(true);button.setAlpha(1f);error(failure);}
            }});
            host.addView(button,params);button.post(new Runnable(){public void run(){mirror(originalBox,box,sourceButton,button);mirror(sourceLabel,text,sourceButton,button);}});
            Kernel.T("UNREAD shortcut attached nativeStyle=true");
        }catch(Throwable failure){error(failure);}
    }
    static void mirror(View original,View target,ViewGroup source,ViewGroup copy) {
        if(source.getWidth()==0||copy.getWidth()==0)return;
        float center=copy.getWidth()-(original.getLeft()+original.getWidth()/2f);
        target.setTranslationX(center-(target.getLeft()+target.getWidth()/2f));
    }
    static TextView label(ViewGroup view){for(int i=0;i<view.getChildCount();i++)if(view.getChildAt(i) instanceof TextView)return (TextView)view.getChildAt(i);return null;}
    static ImageView image(ViewGroup view){for(int i=0;i<view.getChildCount();i++)if(view.getChildAt(i) instanceof ImageView)return (ImageView)view.getChildAt(i);return null;}
    static void disableChildActions(ViewGroup view){for(int i=0;i<view.getChildCount();i++){View child=view.getChildAt(i);child.setClickable(false);child.setLongClickable(false);child.setFocusable(false);child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);if(child instanceof ViewGroup)disableChildActions((ViewGroup)child);}}
    static void remapIds(View root,Map<Integer,Integer> remap){collect(root,remap);apply(root,remap);}
    static void collect(View view,Map<Integer,Integer> remap){if(view.getId()!=View.NO_ID&&!remap.containsKey(view.getId()))remap.put(view.getId(),View.generateViewId());if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),remap);}}
    static void apply(View view,Map<Integer,Integer> remap){Integer mapped=remap.get(view.getId());if(mapped!=null)view.setId(mapped);if(view.getLayoutParams() instanceof RelativeLayout.LayoutParams){RelativeLayout.LayoutParams params=(RelativeLayout.LayoutParams)view.getLayoutParams();int[] rules=params.getRules().clone();for(int i=0;i<rules.length;i++){Integer replacement=remap.get(rules[i]);if(replacement!=null)params.addRule(i,replacement);}}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),remap);}}
    static void error(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();Kernel.T("UNREAD shortcut error "+failure.getClass().getSimpleName());}
}
