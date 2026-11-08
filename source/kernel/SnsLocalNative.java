package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
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
    static void profile(Activity act,String owner,String user){
        if(act==null||act.isFinishing()||act.isDestroyed()||!Kernel.momentsPremiumEnabled()||!owner.equals(Kernel.receiptAccount())||!SnsLocalData.friend(user))return;
        try{Intent intent=new Intent();intent.putExtra("Contact_User",user);intent.putExtra("Contact_Scene",37);profile.invoke(null,act,"profile",".ui.ContactInfoUI",intent,null);Kernel.T("ARSNSLOCAL profile-opened");}
        catch(Throwable unavailable){SnsLocalSettings.toast(act,"暂时无法打开好友资料，请稍后重试");}
    }
}
