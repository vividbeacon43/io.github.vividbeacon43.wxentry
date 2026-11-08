package com.zafiro.wxentry.kernel;
import android.database.Cursor;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;

/** WeChat 8.0.80 native storage/download adapter. Never constructs transport credentials. */
final class MediaNative {
    static ClassLoader cl;
    static final class Message {
        Object db,nativeMessage;String account,talker,reference,content;long local,server,received;int type,appType;
        boolean media(){return MediaMessagePolicy.media(type,appType,0);}
    }
    static final class State {Message message;Object info;File full;long length;boolean complete,online;}
    static Class<?> cls(String name)throws ClassNotFoundException{return Class.forName(name,false,cl);}
    static Object call(String type,String name,Object...args)throws Throwable{return XposedHelpers.callStaticMethod(cls(type),name,args);}
    static Object service(String name)throws Throwable{return call("fi5.n0","c",cls(name));}
    static File physical(String path)throws Throwable{
        if(path==null||path.length()==0)return null;Object p=call("com.tencent.mm.vfs.z6","i",path,false);
        if(!(p instanceof String)||!((String)p).startsWith("/"))return null;return new File((String)p).getCanonicalFile();
    }
    static Message read(Object given,String talker,long sid)throws Throwable{
        if(talker==null||talker.length()==0||sid<=0)return null;Object db=given==null?Kernel.db0():given;
        Cursor c=Kernel.rawQuery(db,"select msgId,type,isSend,imgPath,createTime,content from message where talker=? and msgSvrId=? limit 1",new String[]{talker,Long.toString(sid)});
        Message m=new Message();
        try{if(c==null||!c.moveToFirst()||c.getInt(2)!=0)return null;m.local=c.getLong(0);m.type=c.getInt(1);m.reference=c.getString(3);m.received=c.getLong(4);m.content=c.getString(5);}
        finally{if(c!=null)c.close();}
        if(m.local<=0)return null;int low=m.type&0xffff;if(low!=3&&low!=43&&low!=62&&low!=49)return null;
        c=Kernel.rawQuery(db,"pragma database_list",null);
        try{while(c!=null&&c.moveToNext()){String path=c.getString(2);if(path==null)continue;java.util.regex.Matcher a=java.util.regex.Pattern.compile("/MicroMsg/([a-fA-F0-9]{32})/").matcher(path);if(a.find()){m.account=a.group(1);break;}}}finally{if(c!=null)c.close();}
        if(m.account==null)return null;
        if(given!=null){
            Cursor active=Kernel.rawQuery(Kernel.db0(),"pragma database_list",null);String currentAccount=null;
            try{while(active!=null&&active.moveToNext()){String path=active.getString(2);if(path==null)continue;java.util.regex.Matcher a=java.util.regex.Pattern.compile("/MicroMsg/([a-fA-F0-9]{32})/").matcher(path);if(a.find()){currentAccount=a.group(1);break;}}}finally{if(active!=null)active.close();}
            if(!m.account.equals(currentAccount))return null;
        }
        m.db=db;m.talker=talker;m.server=sid;
        Object nativeMessage=call("yx0.k0","cj",talker,m.local);
        if(nativeMessage==null||((Number)XposedHelpers.callMethod(nativeMessage,"G0")).longValue()!=sid||((Number)XposedHelpers.callMethod(nativeMessage,"A0")).intValue()!=0||!talker.equals(XposedHelpers.callMethod(nativeMessage,"O0")))return null;
        m.nativeMessage=nativeMessage;
        if(low==49){if(m.content==null||m.content.length()>1024*1024)return null;Object app=call("xx0.r","v",m.content);if(app==null)return null;m.appType=XposedHelpers.getIntField(app,"i");if(m.appType!=6)return null;}
        long now=System.currentTimeMillis();if(m.received>0&&m.received<100000000000L)m.received*=1000L;if(m.received<=0||m.received>now)m.received=now;
        return m;
    }
    static Message current(RecallMediaCache.Entry entry)throws Throwable{
        Message m=read(null,entry.talker,entry.server);return m!=null&&m.local==entry.local&&m.account.equals(entry.account)&&(m.type&0xffff)==(entry.type&0xffff)&&m.media()?m:null;
    }
    static State state(Message m)throws Throwable{
        State s=new State();s.message=m;
        if((m.type&0xffff)==49){
            Object storage=call("com.tencent.mm.pluginsdk.model.app.v5","Ej");s.info=XposedHelpers.callMethod(storage,"M1",m.local,m.talker);
            if(s.info!=null){
                if(XposedHelpers.getLongField(s.info,"field_msgInfoId")!=m.local||!m.talker.equals(XposedHelpers.getObjectField(s.info,"field_msgInfoTalker"))||XposedHelpers.getBooleanField(s.info,"field_isUpload"))return null;
                s.length=XposedHelpers.getLongField(s.info,"field_totalLen");s.complete=Boolean.TRUE.equals(XposedHelpers.callMethod(s.info,"m0"));s.full=physical((String)XposedHelpers.getObjectField(s.info,"field_fileFullPath"));
            }
        }else{
            if(m.reference==null||m.reference.length()==0)return s;s.info=call("n71.b2","h",m.reference);
            if(s.info!=null){
                long local=XposedHelpers.getLongField(s.info,"n"),server=XposedHelpers.getLongField(s.info,"c");
                if((local>0&&local!=m.local)||(server>0&&server!=m.server)||!m.talker.equals(XposedHelpers.callMethod(s.info,"i")))return null;
                s.length=XposedHelpers.getIntField(s.info,"f");s.complete=Boolean.TRUE.equals(XposedHelpers.callMethod(s.info,"k"));s.online=Boolean.TRUE.equals(XposedHelpers.callMethod(s.info,"l"));
                s.full=physical((String)XposedHelpers.callMethod(service("rb5.q"),"Fj",m.nativeMessage,true));
            }
        }
        s.complete=s.complete&&s.length>0&&s.full!=null&&s.full.isFile()&&s.full.length()==s.length;return s;
    }
    static boolean request(State state)throws Throwable{
        Message m=state.message;
        if((m.type&0xffff)==49){Object helper=XposedHelpers.getStaticObjectField(cls("ql5.g"),"a");XposedHelpers.callMethod(helper,"j",m.nativeMessage,null);return true;}
        if(state.info==null)return false;int status=XposedHelpers.getIntField(state.info,"i");if(status==112)return true;
        return ((Number)call("n71.b2",state.online?"E":"F",m.reference)).intValue()>=0;
    }
    static Object imageInfo(Message m)throws Throwable{return XposedHelpers.callMethod(call("g61.a1","Hj"),"K3",m.talker,m.server);}
    static Object originalInfo(Object base)throws Throwable{return call("g61.b0","d",base);}
    static void refreshOriginal(Object holder,Message m)throws Throwable{
        if(holder==null||m==null)return;Object adapter=XposedHelpers.getObjectField(holder,"d");if(adapter==null)return;Object ui=XposedHelpers.getObjectField(adapter,"g");
        if(ui==null||Boolean.TRUE.equals(XposedHelpers.callMethod(ui,"isFinishing"))||Boolean.TRUE.equals(XposedHelpers.callMethod(ui,"isDestroyed"))||XposedHelpers.getLongField(ui,"D")!=m.local||!m.talker.equals(XposedHelpers.getObjectField(ui,"E")))return;
        Object selected=XposedHelpers.callMethod(adapter,"v");if(selected==null||((Number)XposedHelpers.callMethod(selected,"getMsgId")).longValue()!=m.local||((Number)XposedHelpers.callMethod(selected,"G0")).longValue()!=m.server)return;
        XposedHelpers.callMethod(holder,"x",selected,true);
        XposedHelpers.callMethod(ui,"Ba",XposedHelpers.callMethod(ui,"A9"));
        Kernel.T("ARORIGINAL viewer-refreshed");
    }
    static boolean originalAvailable(Object base)throws Throwable{return base!=null&&Boolean.TRUE.equals(XposedHelpers.callMethod(base,"k"));}
    static boolean originalReady(Message m,Object base)throws Throwable{
        if(!originalAvailable(base))return false;Object original=originalInfo(base);if(original==null||!Boolean.TRUE.equals(XposedHelpers.callMethod(original,"l")))return false;
        String reference=(String)XposedHelpers.callMethod(call("g61.a1","Hj"),"s4",m.talker,base);
        Object kind=XposedHelpers.getStaticObjectField(cls("jv5.e0"),"g");String path=(String)XposedHelpers.callMethod(service("ep3.u0"),"Mj",m.nativeMessage,kind,reference,"","");
        File file=physical(path);return file!=null&&file.isFile()&&file.length()>=24;
    }
}
