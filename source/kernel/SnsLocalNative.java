package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

/** Read-only post identity, genuine-like lookup, native avatar loading and native contact navigation. */
final class SnsLocalNative {
    static ClassLoader cl;static Class<?> infoType,modelType,itemType,interactionType,handlerType;
    static Method avatar,profile;
    static final class Target {final String owner,key;Target(String owner,String key){this.owner=owner;this.key=key;}}
    static Class<?> cls(String name)throws Exception{return Class.forName(name,false,cl);}
    static void init(ClassLoader loader)throws Exception{
        cl=loader;infoType=cls("com.tencent.mm.plugin.sns.storage.SnsInfo");modelType=cls("cl4.p");itemType=cls("yk4.o2");interactionType=cls("com.tencent.mm.plugin.sns.ui.improve.view.ImproveInteractionLayout");handlerType=cls("zk4.g");
        itemType.getDeclaredMethod("q");itemType.getDeclaredMethod("getCommentView");cls("yk4.d2").getDeclaredMethod("getCommentContainer");
        modelType.getDeclaredMethod("Y0");modelType.getDeclaredMethod("a1");interactionType.getDeclaredMethod("getLikeLayout");interactionType.getDeclaredMethod("getCommentLayout");
        interactionType.getConstructor(Context.class);interactionType.getDeclaredMethod("getDivider");
        Class<?> likes=cls("com.tencent.mm.plugin.sns.ui.SnsLikesTextView");likes.getConstructor(Context.class,android.util.AttributeSet.class);likes.getDeclaredField("i");likes.getDeclaredField("m");likes.getDeclaredField("h");
        cls("com.tencent.mm.plugin.sns.data.g1").getDeclaredMethod("g",Context.class,int.class,int.class,int.class,boolean.class);cls("iu5.w").getConstructor(Drawable.class,int.class);
        avatar=cls("com.tencent.mm.pluginsdk.ui.u").getDeclaredMethod("a",ImageView.class,String.class);
        if(!Modifier.isStatic(avatar.getModifiers())||avatar.getReturnType()!=void.class)throw new NoSuchMethodException("Native avatar loader changed");
        profile=cls("yc5.l").getDeclaredMethod("j",Context.class,String.class,String.class,Intent.class,Bundle.class);
        if(!Modifier.isStatic(profile.getModifiers()))throw new NoSuchMethodException("Native profile route changed");
        cls("com.tencent.mm.plugin.profile.ui.ContactInfoUI");
    }
    static Activity activity(Context c){for(int i=0;c!=null&&i<8;i++){if(c instanceof Activity)return (Activity)c;if(!(c instanceof ContextWrapper))return null;Context next=((ContextWrapper)c).getBaseContext();if(next==c)return null;c=next;}return null;}
    static String key(Object info)throws Throwable{
        if(info==null||!infoType.isInstance(info)||Boolean.TRUE.equals(XposedHelpers.callMethod(info,"isAd")))return null;
        String author=(String)XposedHelpers.callMethod(info,"getUserName");if(!ReceiptConfig.validId(author))return null;
        Object timeline=XposedHelpers.callMethod(info,"getTimeLine");if(timeline==null)return null;Object value=XposedHelpers.getObjectField(timeline,"Id");if(!(value instanceof String))return null;
        String id=((String)value).trim();if(!id.matches("-?[0-9]{1,20}"))return null;BigInteger number=new BigInteger(id);if(number.signum()==0)return null;
        String hex;if(number.signum()<0)hex=Long.toHexString(Long.parseLong(id));else{if(number.bitLength()>64)return null;hex=number.toString(16);}
        String key=author+"~"+hex;return SnsLocalData.postKey(key)?key:null;
    }
    static Target target(Object info)throws Throwable{String owner=Kernel.receiptAccount(),key=key(info);return ReceiptConfig.validId(owner)&&key!=null?new Target(owner,key):null;}
    static Object model(Object item)throws Throwable{Object data=XposedHelpers.callMethod(item,"getData");return data==null?null:XposedHelpers.callMethod(data,"n");}
    static Target itemTarget(Object item)throws Throwable{Object model=model(item);return model==null?null:target(XposedHelpers.callMethod(model,"Y0"));}
    static Object info(Object listener,View view)throws Throwable{
        for(Class<?> type=listener.getClass();type!=null&&!type.getName().equals("com.tencent.mm.plugin.sns.ui.listener.h1");type=type.getSuperclass())for(Field f:type.getDeclaredFields())if(handlerType.isAssignableFrom(f.getType())){
            f.setAccessible(true);Object handler=f.get(listener);if(handler!=null){Object model=XposedHelpers.callMethod(handler,"c");if(model!=null){Object info=XposedHelpers.callMethod(model,"Y0");if(infoType.isInstance(info))return info;}}
        }
        Object found=tag(view.getTag());return found!=null?found:tag(view.getTag(2131386703));
    }
    static Object tag(Object value)throws Throwable{
        if(value==null)return null;if(infoType.isInstance(value))return value;
        Object store=XposedHelpers.callStaticMethod(cls("com.tencent.mm.plugin.sns.model.p4"),"mk");String name=value.getClass().getName();
        if(name.equals("com.tencent.mm.plugin.sns.ui.dq")){Object info=XposedHelpers.callMethod(store,"I2",XposedHelpers.getObjectField(value,"b"));return info!=null?info:XposedHelpers.callMethod(store,"X1",XposedHelpers.getObjectField(value,"a"));}
        if(name.equals("com.tencent.mm.plugin.sns.ui.cl"))return XposedHelpers.callMethod(store,"I2",XposedHelpers.getObjectField(value,"a"));
        if(name.equals("com.tencent.mm.plugin.sns.ui.j2"))return XposedHelpers.callMethod(store,"I2",XposedHelpers.getObjectField(value,"b"));
        if(name.equals("com.tencent.mm.plugin.sns.ui.k9"))return tag(XposedHelpers.getObjectField(value,"a"));
        return null;
    }
    static Set<String> realLikes(Object model){Set<String> result=new HashSet<String>();try{Object sns=XposedHelpers.callMethod(model,"a1");Object list=XposedHelpers.getObjectField(sns,"LikeUserList");if(list instanceof List)for(Object p:(List<?>)list){Object id=XposedHelpers.getObjectField(p,"d");if(id instanceof String)result.add((String)id);}}catch(Throwable unavailable){}return result;}
    static void avatar(ImageView image,String user)throws Throwable{if(!SnsLocalData.friend(user))throw new IllegalArgumentException("Invalid local profile");avatar.invoke(null,image,user);}
    static View interaction(Context context)throws Throwable{return (View)interactionType.getConstructor(Context.class).newInstance(context);}
    static View likesView(Context context)throws Throwable{return (View)cls("com.tencent.mm.plugin.sns.ui.SnsLikesTextView").getConstructor(Context.class,android.util.AttributeSet.class).newInstance(context,null);}
    static TextView likesLabel(Object view){return (TextView)XposedHelpers.getObjectField(view,"i");}
    static CharSequence likesText(Object view){Object value=XposedHelpers.getObjectField(view,"m");return value instanceof CharSequence?(CharSequence)value:likesLabel(view).getText();}
    static void likeStyle(View local,View source){
        local.setPadding(source.getPaddingLeft(),source.getPaddingTop(),source.getPaddingRight(),source.getPaddingBottom());
        TextView to=likesLabel(local),from=likesLabel(source);to.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,from.getTextSize());to.setTypeface(from.getTypeface());to.setIncludeFontPadding(from.getIncludeFontPadding());to.setLineSpacing(from.getLineSpacingExtra(),from.getLineSpacingMultiplier());
        try{Object config=XposedHelpers.getStaticObjectField(cls("dk4.a"),"a");if(Boolean.TRUE.equals(XposedHelpers.callMethod(config,"a"))){int padding=((Number)XposedHelpers.callStaticMethod(cls("af5.a"),"b",local.getContext(),6)).intValue();local.setPadding(local.getPaddingLeft(),padding,local.getPaddingRight(),padding);}}catch(Throwable ignored){}
    }
    static int linkColor(Context context){return context.getResources().getColor(2131099991);}
    static Object heart(Context context)throws Throwable{
        int size=((Number)XposedHelpers.callStaticMethod(cls("af5.a"),"h",context,2131165473)).intValue(),space=((Number)XposedHelpers.callStaticMethod(cls("af5.a"),"h",context,2131165396)).intValue();
        Drawable drawable=(Drawable)XposedHelpers.callStaticMethod(cls("com.tencent.mm.plugin.sns.data.g1"),"g",context,size,size,space,false);
        return cls("iu5.w").getConstructor(Drawable.class,int.class).newInstance(drawable,1);
    }
    static LinearLayout.LayoutParams interactionParams()throws Throwable{
        Object config=XposedHelpers.getStaticObjectField(cls("yk4.d2"),"G");LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=((Number)XposedHelpers.callMethod(config,"g")).intValue();params.bottomMargin=((Number)XposedHelpers.callMethod(config,"b")).intValue();return params;
    }
    static void profile(Activity act,String owner,String user){
        if(act==null||act.isFinishing()||act.isDestroyed()||!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount())||!SnsLocalData.friend(user))return;
        try{Intent intent=new Intent();intent.putExtra("Contact_User",user);intent.putExtra("Contact_Scene",37);profile.invoke(null,act,"profile",".ui.ContactInfoUI",intent,null);Kernel.T("ARSNSLOCAL profile-opened");}
        catch(Throwable unavailable){SnsLocalSettings.toast(act,"暂时无法打开好友资料，请稍后重试");}
    }
}
