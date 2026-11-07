package com.zafiro.wxentry.kernel;

import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import java.lang.reflect.Method;
import java.util.*;

/** Keep WeChat's native forwarding marker on its own line below outgoing voice. */
final class VoiceForwardLayout {
    private static final Map<View,Saved> changed=new WeakHashMap<View,Saved>();
    private static final class Saved {
        final int below,bottom,top,anchor,appliedTop;
        Saved(RelativeLayout.LayoutParams p,int anchor,int top) {
            below=p.getRules()[RelativeLayout.BELOW];bottom=p.getRules()[RelativeLayout.ALIGN_BOTTOM];
            this.top=p.topMargin;this.anchor=anchor;appliedTop=top;
        }
    }
    static void start(ClassLoader loader)throws Throwable {
        Class<?> holder=Class.forName("com.tencent.mm.ui.chatting.viewitems.h0",false,loader);
        Class<?> chat=Class.forName("wk5.d",false,loader),model=Class.forName("qm5.d",false,loader);
        Class<?> item=Class.forName("com.tencent.mm.ui.chatting.viewitems.nq",false,loader);
        Method bind=item.getDeclaredMethod("n",holder,chat,model,String.class);
        XposedBridge.hookMethod(bind,new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p) {
                try { restore((View)XposedHelpers.getObjectField(p.args[0],"c")); }
                catch(Throwable ignored) {}
            }
            protected void afterHookedMethod(MethodHookParam p) {
                if(Kernel.MODULE_OFF || p.hasThrowable())return;
                try {
                    TextView marker=(TextView)XposedHelpers.getObjectField(p.args[0],"c");
                    View bubble=(View)XposedHelpers.getObjectField(p.args[0],"p");
                    apply(marker,bubble);
                }catch(Throwable failure) { Kernel.T("ARVF layout-error "+failure.getClass().getSimpleName()); }
            }
        });
    }
    static boolean apply(TextView marker,View bubble) {
        if(marker==null || bubble==null || marker.getVisibility()!=View.VISIBLE
                || bubble.getVisibility()!=View.VISIBLE || bubble.getId()==View.NO_ID
                || marker.getParent()!=bubble.getParent() || !(marker.getParent() instanceof RelativeLayout)
                || marker.getText()==null || marker.getText().length()==0
                || !(marker.getLayoutParams() instanceof RelativeLayout.LayoutParams))return false;
        if(changed.containsKey(marker))return true;
        RelativeLayout.LayoutParams original=(RelativeLayout.LayoutParams)marker.getLayoutParams();
        int gap=Math.max(1,Math.round(4*marker.getResources().getDisplayMetrics().density));
        Saved saved=new Saved(original,bubble.getId(),gap);
        RelativeLayout.LayoutParams next=new RelativeLayout.LayoutParams(original);
        next.removeRule(RelativeLayout.ALIGN_BOTTOM);
        next.addRule(RelativeLayout.BELOW,bubble.getId());next.topMargin=gap;
        changed.put(marker,saved);marker.setLayoutParams(next);
        Kernel.T("ARVF forward-marker-below-bubble");return true;
    }
    static void restore(View marker) {
        Saved saved=changed.remove(marker);
        if(saved==null || !(marker.getLayoutParams() instanceof RelativeLayout.LayoutParams))return;
        RelativeLayout.LayoutParams current=(RelativeLayout.LayoutParams)marker.getLayoutParams();
        // Restore only the properties still owned by this patch, preserving later native changes.
        RelativeLayout.LayoutParams next=new RelativeLayout.LayoutParams(current);boolean dirty=false;
        if(current.getRules()[RelativeLayout.BELOW]==saved.anchor) { rule(next,RelativeLayout.BELOW,saved.below);dirty=true; }
        if(current.getRules()[RelativeLayout.ALIGN_BOTTOM]==0) { rule(next,RelativeLayout.ALIGN_BOTTOM,saved.bottom);dirty=true; }
        if(current.topMargin==saved.appliedTop) { next.topMargin=saved.top;dirty=true; }
        if(dirty)marker.setLayoutParams(next);
    }
    private static void rule(RelativeLayout.LayoutParams p,int verb,int value) {
        if(value==0)p.removeRule(verb);else p.addRule(verb,value);
    }
}
