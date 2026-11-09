package com.zafiro.wxentry.kernel;

import android.content.Context;
import de.robv.android.xposed.XposedHelpers;
import java.util.*;

/** Native 8.0.80 upload path, exclusively for the separately authorized scheduler. */
final class SnsScheduleNative {
    static ClassLoader cl;static boolean ready;
    static Class<?> cls(String name)throws ClassNotFoundException{return Class.forName(name,false,cl);}
    static Object call(Object target,String method,Object...args){return XposedHelpers.callMethod(target,method,args);}
    static Object field(Object target,String name){return XposedHelpers.getObjectField(target,name);}
    static Object model(String method){return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.tencent.mm.plugin.sns.model.p4",cl),method);}
    static String text(Object target,String name){Object v=field(target,name);return v instanceof String?(String)v:"";}
    static void init(ClassLoader loader,Context c)throws Throwable{
        cl=loader;if(c.getPackageManager().getPackageInfo("com.tencent.mm",0).versionCode!=3220)throw new IllegalStateException("当前微信版本暂未适配定时发布");
        Class<?> b=cls("com.tencent.mm.plugin.sns.model.i8"),i=cls("com.tencent.mm.plugin.sns.storage.SnsInfo");
        b.getDeclaredConstructor(int.class,Context.class);b.getDeclaredMethod("Y",List.class);b.getDeclaredMethod("v",String.class);b.getDeclaredMethod("i");b.getDeclaredMethod("m");
        cls("com.tencent.mm.plugin.sns.data.w0").getDeclaredConstructor(String.class,int.class);cls("com.tencent.mm.plugin.sns.storage.f2").getDeclaredMethod("D2",int.class);cls("com.tencent.mm.plugin.sns.model.y7").getDeclaredMethod("d");
        i.getDeclaredMethod("getPostInfo");i.getDeclaredMethod("getTimeLine");i.getDeclaredMethod("getUserName");i.getDeclaredMethod("isDieItem");
        cls("t56.sj0").getDeclaredMethod("f",cls("t56.vj0"));cls("t56.vj0").getDeclaredMethod("a",int.class);cls("t56.vj0").getDeclaredMethod("getNumber");ready=true;
    }
    static Object prepare(SnsScheduleData.Task t,Context c)throws Throwable{
        if(!ready||!SnsScheduleRuntime.authorized(t.owner))throw new IllegalStateException("账号或 VIP 权限未就绪");SnsScheduleMedia.verify(t);
        Object b=cls("com.tencent.mm.plugin.sns.model.i8").getDeclaredConstructor(int.class,Context.class).newInstance(t.photos.isEmpty()?2:1,c);
        if(Boolean.TRUE.equals(field(b,"p")))throw new IllegalStateException("原生上传环境不可用");
        Object timeline=field(b,"f"),post=field(b,"g"),info=field(b,"h"),sns=field(b,"i");if(!t.owner.equals(text(timeline,"UserName")))throw new IllegalStateException("原生账号不匹配");
        call(b,"v",t.text);Object visibility=cls("t56.sj0").getDeclaredConstructor().newInstance();Object v=XposedHelpers.callStaticMethod(cls("t56.vj0"),"a",t.selfOnly?2:1);if(((Number)call(v,"getNumber")).intValue()!=(t.selfOnly?2:1))throw new IllegalStateException("可见范围适配无效");call(visibility,"f",v);XposedHelpers.setObjectField(post,"T",visibility);XposedHelpers.setIntField(sns,"VisibilityType",t.selfOnly?2:1);
        XposedHelpers.setIntField(b,"c",t.selfOnly?1:0);XposedHelpers.setIntField(post,"f",t.selfOnly?1:0);XposedHelpers.setIntField(timeline,"Privated",t.selfOnly?1:0);
        if(t.selfOnly){call(info,"setPravited",1);call(info,"setLocalPrivate");call(info,"removeSourceFlag",2);call(info,"unSetExtFlag");}
        if(!t.photos.isEmpty()){List<Object> images=new ArrayList<Object>();for(SnsScheduleData.Photo p:t.photos)images.add(cls("com.tencent.mm.plugin.sns.data.w0").getDeclaredConstructor(String.class,int.class).newInstance(SnsScheduleMedia.path(t,p).getPath(),2));call(b,"Y",images);}
        verifyDraft(t,timeline,post);t.client=text(post,"g");if(t.client.isEmpty())throw new IllegalStateException("原生任务标识为空");return b;
    }
    static void verifyDraft(SnsScheduleData.Task t,Object timeline,Object post){
        Object content=field(timeline,"ContentObj");Object list=field(content,"h");if(!t.owner.equals(text(timeline,"UserName"))||!t.text.equals(text(timeline,"ContentDesc"))||!(list instanceof List)||((List<?>)list).size()!=t.photos.size()||XposedHelpers.getIntField(timeline,"Privated")!=(t.selfOnly?1:0)||XposedHelpers.getIntField(post,"f")!=(t.selfOnly?1:0))throw new IllegalStateException("原生内容或图片准备不完整");
        for(Object image:(List<?>)list)if(XposedHelpers.getIntField(image,"e")!=2)throw new IllegalStateException("图片类型不匹配");
    }
    static int submit(SnsScheduleData.Task t,Object builder)throws Throwable{
        if(!SnsScheduleRuntime.authorized(t.owner))throw new IllegalStateException("提交前权限已变更");SnsScheduleMedia.verify(t);verifyDraft(t,field(builder,"f"),field(builder,"g"));if(!t.client.equals(text(field(builder,"g"),"g")))throw new IllegalStateException("原生任务不匹配");return ((Number)call(builder,"i")).intValue();
    }
    static void kick(){call(model("uk"),"d");}
    static long status(SnsScheduleData.Task t)throws Throwable{
        if(!SnsScheduleRuntime.authorized(t.owner)||t.localId<1||t.client.isEmpty())throw new IllegalStateException("无法核对当前账号的发布结果");Object info=call(model("mk"),"D2",t.localId);if(info==null)throw new IllegalStateException("原生发布记录不存在");
        Object post=call(info,"getPostInfo"),timeline=call(info,"getTimeLine");if(!t.owner.equals(call(info,"getUserName"))||!t.client.equals(text(post,"g")))throw new IllegalStateException("原生记录归属不匹配");verifyDraft(t,timeline,post);
        if(Boolean.TRUE.equals(call(info,"isDieItem")))return -1;long id=XposedHelpers.getLongField(info,"field_snsId");return id!=0&&(XposedHelpers.getIntField(info,"field_localFlag")&16)==0?id:0;
    }
}
