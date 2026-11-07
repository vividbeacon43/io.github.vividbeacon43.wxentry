package com.zafiro.wxentry.kernel;

import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Message-owned visual annotations. Original bodies and the message table stay untouched. */
final class RecallMarkLayout {
    static final String SUFFIX="（已撤回）";
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static final Map<View,Binding> BOUND=new WeakHashMap<View,Binding>();
    static final Map<View,Attachment> APPLIED=new WeakHashMap<View,Attachment>();
    static Adapter api;static boolean ready,reported;
    static final Map<View,WeakReference<View>> OWNERS=new WeakHashMap<View,WeakReference<View>>();
    static final Map<View,Binding> PENDING=new WeakHashMap<View,Binding>();
    static final ThreadLocal<Boolean> WRITING=new ThreadLocal<Boolean>();
    static final class MarkerSpan { }
    static final class Binding {
        final RecallMarkStore.Entry identity;final String body;final WeakReference<Object> holder;
        Binding(RecallMarkStore.Entry e,String text,Object h) { identity=e;body=text==null?"":text;holder=new WeakReference<Object>(h); }
    }
    static final class Attachment {
        final WeakReference<View> inline,badge;final String written;
        Attachment(View text,View label,String value) { inline=new WeakReference<View>(text);badge=new WeakReference<View>(label);written=value; }
    }
    static long bindings,renders,matched,shown,sourceChecks,sourceRejected,missingBubble,missingText,nativeRefreshes;
    static String lastError="none",lastResult="none";
    static void start(ClassLoader loader) throws Throwable {
        if(ready)return;final Adapter resolved=new Adapter(loader);api=resolved;
        if(resolved.bind!=null)XposedBridge.hookMethod(resolved.bind,new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                try { unbind(resolved.row(p.args[0])); }catch(Throwable failure) { error(failure); }
            }
            protected void afterHookedMethod(MethodHookParam p) {
                if(p.hasThrowable())return;
                try { View row=resolved.row(p.args[0]);bound(row,row.getTag(),resolved.message(p.args[1]),resolved); }
                catch(Throwable failure) { error(failure); }
            }
        });
        XposedBridge.hookMethod(resolved.common,new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                try { unbind((View)resolved.convertView.get(p.args[0])); }catch(Throwable failure) { error(failure); }
            }
            protected void afterHookedMethod(MethodHookParam p) {
                if(p.hasThrowable())return;
                try { bound((View)resolved.convertView.get(p.args[0]),p.args[0],p.args[3],resolved); }
                catch(Throwable failure) { error(failure); }
            }
        });
        XC_MethodHook textChanged=new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam p) {
                if(!p.hasThrowable()&&!Boolean.TRUE.equals(WRITING.get()))nativeChanged((View)p.thisObject);
            }
        };
        XposedBridge.hookMethod(resolved.neatSet,textChanged);
        XposedBridge.hookMethod(TextView.class.getDeclaredMethod("setText",CharSequence.class,TextView.BufferType.class),textChanged);
        ready=true;Kernel.T("ARMARK row hooks ready recycler="+(resolved.bind!=null)+" common=true");
    }
    static void unbind(View row) {
        if(row==null)return;synchronized(BOUND) { BOUND.remove(row); }synchronized(PENDING){PENDING.remove(row);}restore(row);
    }
    static void bound(final View row,Object holder,Object msg,Adapter resolved) throws Throwable {
        if(row==null||!resolved.holder.isInstance(holder)||msg==null)return;
        String account=Kernel.receiptAccount();int sent=((Number)resolved.sent.invoke(msg)).intValue();
        if(sent!=0||!ReceiptConfig.validId(account))return;
        RecallMarkStore.Entry identity=new RecallMarkStore.Entry(account,(String)resolved.talker.invoke(msg),((Number)resolved.id.invoke(msg)).longValue(),((Number)resolved.server.invoke(msg)).longValue(),resolved.rawCreated!=null?((Number)resolved.rawCreated.get(msg)).longValue():((Number)resolved.created.invoke(msg)).longValue(),((Number)resolved.type.invoke(msg)).intValue());
        if(!identity.rowValid())return;
        final Binding binding=new Binding(identity,(String)resolved.content.invoke(msg),holder);
        synchronized(BOUND) { BOUND.put(row,binding);bindings++; }
        if(Looper.myLooper()==Looper.getMainLooper())render(row,binding);
        // Also refresh after the native row binder has finished subsequent formatting.
        schedule(row,binding);
    }
    static void schedule(final View row,final Binding binding) {
        synchronized(PENDING){if(PENDING.get(row)==binding)return;PENDING.put(row,binding);}
        MAIN.post(new Runnable(){public void run(){synchronized(PENDING){if(PENDING.get(row)!=binding)return;PENDING.remove(row);}render(row,binding);}});
    }
    static void nativeChanged(View text) {
        WeakReference<View> owner;synchronized(OWNERS){owner=OWNERS.get(text);}View row=owner==null?null:owner.get();if(row==null)return;
        Binding binding;synchronized(BOUND){binding=BOUND.get(row);}Attachment attachment;synchronized(APPLIED){attachment=APPLIED.get(row);}
        if(binding!=null&&attachment!=null&&attachment.inline.get()==text){nativeRefreshes++;schedule(row,binding);}
    }
    static String state() {
        int tracked,applied;synchronized(BOUND){tracked=BOUND.size();}synchronized(APPLIED){applied=APPLIED.size();}
        return "ready="+ready+" bound="+bindings+" tracked="+tracked+" applied="+applied+" renders="+renders+" matched="+matched+" shown="+shown+" dbChecks="+sourceChecks+" dbRejected="+sourceRejected+" noBubble="+missingBubble+" noText="+missingText+" formatRefresh="+nativeRefreshes+" last="+lastResult+" ledgerError="+RecallMarkStore.readError+" error="+lastError;
    }
    static void changed(final RecallMarkStore.Entry entry) {
        MAIN.post(new Runnable() { public void run() {
            int visible=0,exact=0;
            for(Map.Entry<View,Binding> item:snapshot())if(item.getValue().identity.sameRow(entry)) {
                visible++;if(item.getValue().identity.matches(entry))exact++;render(item.getKey(),item.getValue());
            }
            Kernel.T("ARMARK event visible="+visible+" exact="+exact+" "+state());
        } });
    }
    static void refresh() {
        MAIN.post(new Runnable() { public void run() { for(Map.Entry<View,Binding> item:snapshot())render(item.getKey(),item.getValue()); } });
    }
    static List<Map.Entry<View,Binding>> snapshot() {
        synchronized(BOUND) {
            List<Map.Entry<View,Binding>> copy=new ArrayList<Map.Entry<View,Binding>>();
            for(Map.Entry<View,Binding> item:BOUND.entrySet())copy.add(new AbstractMap.SimpleImmutableEntry<View,Binding>(item.getKey(),item.getValue()));
            return copy;
        }
    }
    static RecallMarkStore.Entry recalled(Binding binding) {
        RecallMarkStore.Entry saved=RecallMarkStore.candidate(binding.identity);if(saved==null)return null;
        if(binding.identity.server>0&&saved.server>0&&binding.identity.server!=saved.server){sourceRejected++;lastResult="view-server-conflict";return null;}
        // Presentation timestamps/server metadata may differ. Verify the original stored row instead.
        Cursor cursor=null;sourceChecks++;
        try {
            if(!saved.account.equals(Kernel.receiptAccount()))return null;
            Object db=RecallTipRuntime.sourceDb(saved.account);if(db==null){lastResult="source-db-unavailable";return null;}
            cursor=Kernel.rawQuery(db,"select talker,createTime,type,isSend,msgSvrId from message where msgId=? limit 1",new String[]{Long.toString(saved.local)});
            if(cursor!=null&&cursor.moveToFirst()&&cursor.getInt(3)==0) {
                RecallMarkStore.Entry original=new RecallMarkStore.Entry(saved.account,cursor.getString(0),saved.local,cursor.getLong(4),cursor.getLong(1),cursor.getInt(2));
                int low=original.type&0xffff;
                if(low!=10000&&low!=10002&&saved.matches(original)&&saved.account.equals(Kernel.receiptAccount()))return saved;
            }
            sourceRejected++;lastResult="source-identity-mismatch";return null;
        }catch(Throwable unavailable){sourceRejected++;lastResult="source-read-"+unavailable.getClass().getSimpleName();return null;}
        finally{if(cursor!=null)try{cursor.close();}catch(Throwable ignored){}}
    }
    static void render(View row,Binding binding) {
        if(row==null)return;
        synchronized(BOUND) { if(BOUND.get(row)!=binding)return; }
        restore(row);renders++;
        if(!RecallTipRuntime.enabled()||!binding.identity.account.equals(Kernel.receiptAccount())){lastResult="disabled-or-account";return;}
        RecallMarkStore.Entry saved=recalled(binding);Object holder=binding.holder.get();
        if(saved==null)return;if(holder==null||api==null){lastResult="holder-unavailable";return;}matched++;
        try {
            View bubble=(View)api.bubble.get(holder);if(bubble==null)missingBubble++;
            View target=null;
            if((saved.type&0xffff)==1) {
                if(bubble!=null)target=findText(bubble,body(binding.body,binding.identity.talker));
                if(target==null)target=findText(row,body(binding.body,binding.identity.talker));
                if(target!=null) {
                    CharSequence original=strip(read(target));CharSequence marked=decorate(original,dark(target));write(target,marked);
                    if(owned(read(target))) {
                        synchronized(APPLIED){APPLIED.put(row,new Attachment(target,null,marked.toString()));}
                        synchronized(OWNERS){OWNERS.put(target,new WeakReference<View>(row));}
                        shown++;lastResult="inline";return;
                    }
                    write(target,original);
                }else missingText++;
            }
            View anchor=bubble!=null?bubble:target;
            if(anchor==null)anchor=contentAnchor(holder,row);
            View label=badgeInRow(anchor,row);
            if(label!=null){synchronized(APPLIED){APPLIED.put(row,new Attachment(null,label,null));}shown++;lastResult="badge";}
            else lastResult="no-safe-anchor";
        }catch(Throwable failure){error(failure);}
    }
    static View badgeInRow(View anchor,View row) {
        if(!inside(anchor,row))return null;
        for(View current=anchor;current!=null&&current!=row;) {
            View label=badge(current);if(label!=null)return label;
            current=current.getParent() instanceof View?(View)current.getParent():null;
        }
        return null;
    }
    static boolean inside(View child,View row) {
        for(View current=child;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(current==row)return true;
        return false;
    }
    static View contentAnchor(Object holder,View row) {
        View best=null;int score=0,visited=0;
        for(Class<?> type=holder.getClass();type!=null&&type!=api.holder;type=type.getSuperclass())for(Field field:type.getDeclaredFields()) {
            if(++visited>48)return best;if(!View.class.isAssignableFrom(field.getType()))continue;
            try {
                field.setAccessible(true);View candidate=(View)field.get(holder);
                if(candidate==null||candidate==row||candidate.getVisibility()!=View.VISIBLE||!inside(candidate,row))continue;
                int rank=candidate instanceof ViewGroup?3:candidate instanceof TextView?1:2;
                if(rank>score){best=candidate;score=rank;}
            }catch(Throwable ignored){}
        }
        return best;
    }
    static boolean dark(View view) { return (view.getResources().getConfiguration().uiMode&0x30)==0x20; }
    static int color(boolean night) { return night?0xFFB7C2D2:0xFF7A8493; }
    static CharSequence decorate(CharSequence original,boolean night) {
        SpannableStringBuilder text=new SpannableStringBuilder(strip(original));int start=text.length();text.append(SUFFIX);
        text.setSpan(new MarkerSpan(),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(color(night)),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.82f),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return text;
    }
    static boolean owned(CharSequence text) { return text instanceof Spanned&&((Spanned)text).getSpans(0,text.length(),MarkerSpan.class).length>0; }
    static CharSequence strip(CharSequence text) {
        if(text==null)return "";if(!(text instanceof Spanned))return text;
        Spanned spans=(Spanned)text;MarkerSpan[] own=spans.getSpans(0,text.length(),MarkerSpan.class);if(own.length==0)return text;
        List<int[]> ranges=new ArrayList<int[]>();
        for(MarkerSpan marker:own) { int a=spans.getSpanStart(marker),b=spans.getSpanEnd(marker);if(a>=0&&b<=text.length()&&a<b&&text.subSequence(a,b).toString().equals(SUFFIX))ranges.add(new int[]{a,b}); }
        Collections.sort(ranges,new Comparator<int[]>() { public int compare(int[] a,int[] b) { return b[0]-a[0]; } });
        SpannableStringBuilder clean=new SpannableStringBuilder(text);for(int[] range:ranges)clean.delete(range[0],range[1]);return clean;
    }
    static void restore(View row) {
        Attachment previous;synchronized(APPLIED) { previous=APPLIED.remove(row); }if(previous==null)return;
        try { View text=previous.inline.get();if(text!=null) {
            synchronized(OWNERS){WeakReference<View> owner=OWNERS.get(text);if(owner!=null&&owner.get()==row)OWNERS.remove(text);}
            CharSequence current=read(text);
            if(owned(current))write(text,strip(current));
            else if(previous.written!=null&&current!=null&&previous.written.equals(current.toString())&&current.toString().endsWith(SUFFIX)) {
                SpannableStringBuilder clean=new SpannableStringBuilder(current);clean.delete(clean.length()-SUFFIX.length(),clean.length());write(text,clean);
            }
        } }
        catch(Throwable failure) { error(failure); }
        View label=previous.badge.get();if(label!=null&&label.getParent() instanceof ViewGroup)((ViewGroup)label.getParent()).removeView(label);
    }
    static String body(String text,String talker) {
        if(text==null)return "";
        if(talker!=null&&talker.endsWith("@chatroom")) { int colon=text.indexOf(":\n");if(colon>0&&colon<256&&ReceiptConfig.validId(text.substring(0,colon)))return text.substring(colon+2); }
        return text;
    }
    static View findText(View bubble,String expected) {
        List<View> queue=new ArrayList<View>();queue.add(bubble);View best=null;int score=0,length=-1;
        for(int i=0;i<queue.size()&&i<160;i++) {
            View view=queue.get(i);if(view.getVisibility()!=View.VISIBLE)continue;
            if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view;for(int c=0;c<group.getChildCount()&&queue.size()<160;c++)queue.add(group.getChildAt(c)); }
            if(!(view instanceof TextView)&&!api.neat.isInstance(view))continue;
            try {
                CharSequence value=strip(read(view));if(value==null||value.length()==0)continue;
                int rank=api.neat.isInstance(view)?(value.toString().equals(expected)?200:150):value.toString().equals(expected)?100:view==bubble?40:0;
                if(rank>score||rank==score&&rank>0&&value.length()>length) { best=view;score=rank;length=value.length(); }
            }catch(Throwable ignored) { }
        }
        return best;
    }
    static CharSequence read(View text) throws Throwable { return text instanceof TextView?((TextView)text).getText():(CharSequence)api.neatGet.invoke(text); }
    static void write(View text,CharSequence value) throws Throwable {
        Boolean previous=WRITING.get();WRITING.set(Boolean.TRUE);
        try { if(text instanceof TextView)((TextView)text).setText(value,TextView.BufferType.SPANNABLE);else api.neatSet.invoke(text,value,TextView.BufferType.SPANNABLE,null); }
        finally { if(previous==null)WRITING.remove();else WRITING.set(previous); }
    }
    static View badge(View bubble) {
        if(!(bubble.getParent() instanceof ViewGroup)||bubble.getVisibility()!=View.VISIBLE)return null;
        ViewGroup parent=(ViewGroup)bubble.getParent();TextView label=new TextView(bubble.getContext());label.setText(SUFFIX);label.setTextSize(12);
        label.setTextColor(color(dark(bubble)));label.setClickable(false);label.setLongClickable(false);label.setFocusable(false);
        int gap=Math.max(1,Math.round(2*bubble.getResources().getDisplayMetrics().density));
        if(parent instanceof RelativeLayout&&bubble.getId()!=View.NO_ID) {
            RelativeLayout.LayoutParams params=new RelativeLayout.LayoutParams(-2,-2);params.addRule(RelativeLayout.BELOW,bubble.getId());params.addRule(RelativeLayout.ALIGN_START,bubble.getId());params.topMargin=gap;parent.addView(label,params);
        } else if(parent instanceof LinearLayout&&((LinearLayout)parent).getOrientation()==LinearLayout.VERTICAL) {
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.topMargin=gap;parent.addView(label,parent.indexOfChild(bubble)+1,params);
        } else return null;
        return label;
    }
    static void error(Throwable failure) { lastError=failure.getClass().getSimpleName();if(!reported) { reported=true;Kernel.T("ARMARK UI skipped "+failure.getClass().getSimpleName()); } }
    static Field field(Class<?> cls,String name) throws Exception {
        for(Class<?> c=cls;c!=null;c=c.getSuperclass())try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f; }catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    static final class Adapter {
        final Class<?> holder,neat;final Method bind,common,id,server,created,type,talker,sent,content,neatGet,neatSet;
        final Field itemView,modelMessage,message,bubble,convertView,rawCreated;
        Adapter(ClassLoader loader) throws Throwable {
            Class<?> msg=Class.forName("com.tencent.mm.storage.f9",false,loader);
            holder=Class.forName("com.tencent.mm.ui.chatting.viewitems.h0",false,loader);neat=Class.forName("com.tencent.mm.ui.widget.MMNeat7extView",false,loader);
            common=Class.forName("com.tencent.mm.ui.chatting.viewitems.b0",false,loader).getDeclaredMethod("m",holder,int.class,Class.forName("wk5.d",false,loader),msg);
            bubble=field(holder,"clickArea");convertView=field(holder,"convertView");
            Field storedTime=null;try{storedTime=field(msg,"field_createTime");}catch(NoSuchFieldException absent){}rawCreated=storedTime;
            Method adapter=null;Field rowField=null,wrapperField=null,messageField=null;
            try {
                Class<?> row=Class.forName("qw5.s0",false,loader),model=Class.forName("qm5.d",false,loader);
                adapter=Class.forName("nm5.g",false,loader).getDeclaredMethod("h",row,Class.forName("qw5.c",false,loader),int.class,int.class,boolean.class,List.class);
                rowField=field(row,"itemView");wrapperField=field(model,"d");messageField=field(wrapperField.getType(),"b");
            }catch(ReflectiveOperationException unavailable){adapter=null;}
            bind=adapter;itemView=rowField;modelMessage=wrapperField;message=messageField;
            id=msg.getMethod("getMsgId");server=msg.getMethod("G0");created=msg.getMethod("getCreateTime");type=msg.getMethod("getType");talker=msg.getMethod("O0");sent=msg.getMethod("A0");content=msg.getMethod("j");
            neatGet=neat.getMethod("a");neatSet=neat.getMethod("c",CharSequence.class,TextView.BufferType.class,Boolean.class);
        }
        View row(Object holder) throws Throwable { return (View)itemView.get(holder); }
        Object message(Object model) throws Throwable { Object wrapper=modelMessage.get(model);return wrapper==null?null:message.get(wrapper); }
    }
}
