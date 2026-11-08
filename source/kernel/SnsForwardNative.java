package com.zafiro.wxentry.kernel;
import android.content.Context;
import android.content.ContextWrapper;
import android.app.Activity;
import android.view.View;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only SNS source lookup and the native download manager. No post/upload API. */
final class SnsForwardNative {
    static ClassLoader cl;static Class<?> infoType,modernType;
    static final class Source {
        final String owner,id,description,title,url,cardXml,preview,localId;final int createTime;final SnsForwardDraft.Kind kind;final List<Object> media;
        Source(String a,String i,String d,String t,String u,SnsForwardDraft.Kind k,List<Object> list){this(a,i,d,t,u,k,list,null,null);}
        Source(String a,String i,String d,String t,String u,SnsForwardDraft.Kind k,List<Object> list,String xml,String cover){this(a,i,d,t,u,k,list,xml,cover,"",0);}
        Source(String a,String i,String d,String t,String u,SnsForwardDraft.Kind k,List<Object> list,String xml,String cover,String local,int created){createTime=created;localId=local;cardXml=xml;preview=cover;owner=a;id=i;description=d;title=t;url=u;kind=k;media=Collections.unmodifiableList(list);}
    }
    static Class<?> cls(String name)throws ClassNotFoundException{return Class.forName(name,false,cl);}
    static Object call(String type,String method,Object...args)throws Throwable{return XposedHelpers.callStaticMethod(cls(type),method,args);}
    static String text(Object value,String name){Object v=XposedHelpers.getObjectField(value,name);return v instanceof String?(String)v:"";}
    static void init(ClassLoader loader)throws Throwable{
        cl=loader;infoType=cls("com.tencent.mm.plugin.sns.storage.SnsInfo");modernType=cls("zk4.g");
        infoType.getDeclaredMethod("getTimeLine");infoType.getDeclaredMethod("isNoCopy");infoType.getDeclaredMethod("isAd");
        Class<?> media=cls("gd5.bs4"),scene=cls("com.tencent.mm.storage.s7"),manager=cls("com.tencent.mm.plugin.sns.model.n0"),listener=cls("com.tencent.mm.plugin.sns.model.i0");
        manager.getDeclaredMethod("d",media,int.class,cls("com.tencent.mm.plugin.sns.data.s0"),scene);
        manager.getDeclaredMethod("c",listener);manager.getDeclaredMethod("v",listener);manager.getDeclaredMethod("l",String.class);
        cls("com.tencent.mm.plugin.sns.model.j1").getDeclaredMethod("l",media);cls("com.tencent.mm.plugin.sns.model.j1").getDeclaredMethod("D",media);
    }
    static Activity activity(Context context){for(int i=0;i<8&&context!=null;i++){if(context instanceof Activity)return (Activity)context;if(!(context instanceof ContextWrapper))return null;Context next=((ContextWrapper)context).getBaseContext();if(next==context)return null;context=next;}return null;}
    static Object info(Object listener,View view)throws Throwable{
        // Improved timeline handlers own the exact bound model, including views without a plain tag.
        for(Class<?> c=listener.getClass();c!=null&&!c.getName().equals("com.tencent.mm.plugin.sns.ui.listener.h1");c=c.getSuperclass())for(Field field:c.getDeclaredFields()){
            if(!modernType.isAssignableFrom(field.getType()))continue;field.setAccessible(true);Object handler=field.get(listener);
            if(handler!=null){Object model=XposedHelpers.callMethod(handler,"c");if(model!=null){Object info=XposedHelpers.callMethod(model,"Y0");if(infoType.isInstance(info))return info;}}
        }
        Object tag=view.getTag();Object found=tag(tag);if(found!=null)return found;
        // Native description selection stores its exact tag under this resource ID in 8.0.80.
        return tag(view.getTag(2131386703));
    }
    static Object tag(Object tag)throws Throwable{
        if(tag==null)return null;if(infoType.isInstance(tag))return tag;String type=tag.getClass().getName();
        Object store=call("com.tencent.mm.plugin.sns.model.p4","mk");
        if(type.equals("com.tencent.mm.plugin.sns.ui.dq")){
            Object info=XposedHelpers.callMethod(store,"I2",text(tag,"b"));return info!=null?info:XposedHelpers.callMethod(store,"X1",text(tag,"a"));
        }
        if(type.equals("com.tencent.mm.plugin.sns.ui.cl"))return XposedHelpers.callMethod(store,"I2",text(tag,"a"));
        if(type.equals("com.tencent.mm.plugin.sns.ui.j2"))return XposedHelpers.callMethod(store,"I2",text(tag,"b"));
        if(type.equals("com.tencent.mm.plugin.sns.ui.k9"))return tag(XposedHelpers.getObjectField(tag,"a"));
        if(type.equals("com.tencent.mm.protocal.protobuf.TimeLineObject")){Long id=SnsForwardDraft.serverId(text(tag,"Id"));return id==null?null:XposedHelpers.callMethod(store,"V1",id.longValue());}
        return null; // A comment/avatar or unrelated long press is never guessed as a target post.
    }
    static Source source(Object info)throws Throwable{
        if(info==null||!infoType.isInstance(info)||Boolean.TRUE.equals(XposedHelpers.callMethod(info,"isAd"))||Boolean.TRUE.equals(XposedHelpers.callMethod(info,"isNoCopy")))return null;
        String owner=Kernel.receiptAccount();if(!ReceiptConfig.validId(owner))return null;
        Object current=XposedHelpers.callMethod(info,"getTimeLine");if(current==null)return null;
        byte[] bytes=(byte[])XposedHelpers.callMethod(current,"toByteArray");if(bytes.length>2*1024*1024)return null;
        Object timeline=cls("com.tencent.mm.protocal.protobuf.TimeLineObject").getDeclaredConstructor().newInstance();XposedHelpers.callMethod(timeline,"parseFrom",bytes);
        Object content=XposedHelpers.getObjectField(timeline,"ContentObj");if(content==null)return null;
        Object values=XposedHelpers.getObjectField(content,"h");List<Object> media=new ArrayList<Object>();if(values instanceof List)media.addAll((List<?>)values);
        int style=XposedHelpers.getIntField(content,"e");String desc=text(timeline,"ContentDesc"),url=text(content,"g");
        SnsForwardDraft.Kind kind=SnsForwardDraft.kind(style,media.size(),desc,url);
        if(style==54&&media.size()==1&&XposedHelpers.getIntField(media.get(0),"e")==6)kind=SnsForwardDraft.Kind.VIDEO;
        if(kind==SnsForwardDraft.Kind.PHOTOS)for(Object item:media)if(XposedHelpers.getIntField(item,"e")!=2)kind=null;
        String xml=null,cover=null;
        if(kind==SnsForwardDraft.Kind.FINDER||kind==SnsForwardDraft.Kind.MPVIDEO){
            Object data=XposedHelpers.getObjectField(content,kind==SnsForwardDraft.Kind.FINDER?"o":"q");
            if(data==null)kind=SnsForwardDraft.http(url)?SnsForwardDraft.Kind.LINK:null;
            else{Object serializer=cls(kind==SnsForwardDraft.Kind.FINDER?"g71.k":"g71.q").getDeclaredConstructor().newInstance();XposedHelpers.setObjectField(serializer,"a",data);xml=(String)XposedHelpers.callMethod(serializer,"a");cover=SnsForwardCard.preview(xml,kind==SnsForwardDraft.Kind.FINDER?"finderFeed":"mmbrandmpvideo");}
        }
        if((cover==null||cover.isEmpty())&&!media.isEmpty()) {String address=text(media.get(0),"i");if(SnsForwardDraft.http(address))cover=address;}
        Kernel.T("ARSNSFORWARD source style="+style+" mediaCount="+media.size()+" kind="+kind);
        return new Source(owner,text(timeline,"Id"),desc,text(content,"f"),url,kind,media,xml,cover,(String)XposedHelpers.callMethod(info,"getLocalid"),XposedHelpers.getIntField(timeline,"CreateTime"));
    }
    static Object manager()throws Throwable{return call("com.tencent.mm.plugin.sns.model.p4","jk");}
    static File physical(String path)throws Throwable{
        if(path==null||path.isEmpty())return null;Object p=call("com.tencent.mm.vfs.z6","i",path,false);
        return p instanceof String&&((String)p).startsWith("/")?new File((String)p).getCanonicalFile():null;
    }
    static String mediaId(Object media){return text(media,"d");}
    static String videoPath(Source source,Object media)throws Throwable{
        // The native resolver checks VideoInfo completion and selects its playable variant.
        // A bare sight_* file can be encrypted even when present and fully downloaded.
        return (String)call("com.tencent.mm.plugin.sns.model.j7","i",source.localId,media);
    }
    static File ready(Source source,Object media,boolean video)throws Throwable{
        String id=mediaId(media);if(id.isEmpty())return null;Object manager=manager();
        if(!video&&Boolean.TRUE.equals(XposedHelpers.callMethod(manager,"l",id)))return null;
        File path=physical(video?videoPath(source,media):(String)call("com.tencent.mm.plugin.sns.model.j1","l",media));
        return path!=null&&allowedPath(path)&&path.isFile()&&path.length()>24&&SnsForwardFiles.extension(path,video)!=null?path:null;
    }
    static boolean allowedPath(File file)throws Throwable{
        String path=file.getCanonicalPath();for(String method:new String[]{"Hj","Ij"}){File root=physical((String)call("com.tencent.mm.plugin.sns.model.p4",method));if(root!=null&&path.startsWith(root.getCanonicalPath()+File.separator))return true;}return false;
    }
    static File thumbnail(Object media)throws Throwable{File f=physical((String)call("com.tencent.mm.plugin.sns.model.j1","t",media));return f!=null&&allowedPath(f)?f:null;}
    static boolean request(Source source,Object media,boolean video)throws Throwable{
        if(video){
            // Same full download task used by the native video editor: the video service
            // selects/decrypts its variant and updates VideoInfo before j7.i returns it.
            Object service=call("com.tencent.mm.plugin.sns.model.p4","wk");
            return Boolean.TRUE.equals(XposedHelpers.callMethod(service,"m",media,source.createTime,source.localId,false,true,40,(String)call("com.tencent.mm.plugin.sns.model.j7","c",source.createTime,media)));
        }
        Object scene=XposedHelpers.getStaticObjectField(cls("com.tencent.mm.storage.s7"),"c");
        // 2 is a full photo. Thumbnail modes are never accepted as full media.
        return Boolean.TRUE.equals(XposedHelpers.callMethod(manager(),"d",media,2,null,scene));
    }
    static final class Downloads {
        final Map<String,Boolean> results=new ConcurrentHashMap<String,Boolean>();final Set<String> expected=new HashSet<String>();
        final Object manager,listener;boolean detached;
        Downloads(final Source source)throws Throwable{
            for(Object media:source.media)expected.add(mediaId(media));manager=manager();
            listener=Proxy.newProxyInstance(cl,new Class<?>[]{cls("com.tencent.mm.plugin.sns.model.i0")},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
                String name=method.getName();if(name.equals("hashCode"))return System.identityHashCode(proxy);if(name.equals("equals"))return args!=null&&args[0]==proxy;if(name.equals("toString"))return "WXEntrySnsDraftDownloadListener";
                if(args==null||args.length<1||!(args[0] instanceof String)||!expected.contains(args[0]))return null;
                if(name.equals("onImageFinish")&&source.kind==SnsForwardDraft.Kind.PHOTOS&&args.length==3){int type=((Number)args[1]).intValue();if(type==2||type==8||type==10||type==11)results.put((String)args[0],Boolean.TRUE.equals(args[2]));}
                if(name.equals("onSightFinish")&&source.kind==SnsForwardDraft.Kind.VIDEO&&args.length==2)results.put((String)args[0],Boolean.TRUE.equals(args[1]));
                return null;
            }});
            XposedHelpers.callMethod(manager,"c",listener);
            // Native registration is posted to its own handler. Wait for the following task before requests.
            final java.util.concurrent.CountDownLatch registered=new java.util.concurrent.CountDownLatch(1);
            Object handler=XposedHelpers.getObjectField(manager,"a");
            XposedHelpers.callMethod(handler,"post",new Runnable(){public void run(){registered.countDown();}});
            if(!registered.await(3,java.util.concurrent.TimeUnit.SECONDS)){detach();throw new java.io.IOException("SNS_LISTENER_TIMEOUT");}
        }
        synchronized void detach(){if(detached)return;detached=true;try{XposedHelpers.callMethod(manager,"v",listener);}catch(Throwable failure){Kernel.T("ARSNSFORWARD detach-error "+failure.getClass().getSimpleName());}}
    }
}
