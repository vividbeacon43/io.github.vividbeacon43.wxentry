package com.zafiro.wxentry.kernel;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import com.tencent.mm.plugin.luckymoney.model.*;
public final class RedPacketNativeTest {
    static int checks;
    static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    static class Sink implements RedPacketNative.Listener {
        RedPacketEngine.Attempt owned;RedPacketEngine.Query q;RedPacketEngine.Result r;
        int queries,opens,replies,errors;boolean sent;
        public RedPacketEngine.Attempt legacy(String url,String id) { return owned!=null && owned.packet.url.equals(url) && owned.packet.sendId.equals(id)?owned:null; }
        public void query(RedPacketEngine.Attempt a,RedPacketEngine.Query q) { this.q=q;queries++; }
        public void opened(RedPacketEngine.Attempt a,RedPacketEngine.Result r) { this.r=r;opens++; }
        public void replied(RedPacketEngine.Attempt a,boolean good) { replies++;sent=good; }
        public void error(Throwable failure) { errors++; }
    }
    static void callback(Object scene,int type,int code) throws Throwable {
        Method end=com.tencent.mm.modelbase.s1.class.getDeclaredMethod("onSceneEnd",int.class,int.class,String.class,com.tencent.mm.modelbase.n1.class);
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.args=new Object[]{type,code,"",scene};
        XposedBridge.hooks.get(end).invoke(p);
        check(!p.stopped,"native callback not suppressed");
    }
    static void constructed(Constructor<?> ctor,Object object,Object[] args) throws Throwable {
        XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=object;p.args=args;
        XposedBridge.hooks.get(ctor).after(p);
    }
    public static void main(String[] args) throws Throwable {
        Sink sink=new Sink();RedPacketNative api=new RedPacketNative(RedPacketNativeTest.class.getClassLoader(),sink);
        check("self".equals(api.account()),"current account read from native API");
        w41.z1.current="other";check("other".equals(api.account()),"account never cached");w41.z1.current="self";
        RedPacketEngine.Packet p=RedPacketEngineTest.packet("id");RedPacketEngine.Attempt a=new RedPacketEngine.Attempt(p,true,10000);
        api.query(a);n6 query=(n6)zp0.j1.holder.b.last;
        check(Arrays.equals(query.arguments,new Object[]{1,1,"id",p.url,0,"v1.0",p.talker}),"query native parameters");
        callback(query,0,0);check(sink.queries==1 && sink.q.hbStatus==2 && sink.q.receiveStatus==0,"native query status parsed");
        check("timing".equals(sink.q.timing) && "发送者".equals(sink.q.sender) && "sender".equals(sink.q.senderId),"timing and actual sender fields");
        callback(query,0,0);check(sink.queries==1,"native duplicate callback ignored");
        api.query(a);query=(n6)zp0.j1.holder.b.last;query.C="0";callback(query,0,0);check(sink.q.needsAction,"agreement required is not bypassed");
        api.query(a);query=(n6)zp0.j1.holder.b.last;query.C="1";callback(query,0,0);check(!sink.q.needsAction,"already agreed handled");
        api.query(a);query=(n6)zp0.j1.holder.b.last;query.jump=true;callback(query,0,0);check(sink.q.needsAction,"jump reminder blocks automatic opening");
        api.query(a);callback(zp0.j1.holder.b.last,4,-1);check(sink.q.error!=0,"transport error propagated");
        api.open(a,"server-token");h6 open=(h6)zp0.j1.holder.b.last;
        check(Arrays.equals(open.arguments,new Object[]{1,1,"id",p.url,"own-head","我的昵称",p.talker,"v1.0","server-token",""}),"open uses own profile, original conversation and server token");
        callback(open,0,0);check(sink.opens==1 && sink.r.cents==123 && sink.r.receiveStatus==2 && sink.r.sendId.equals("id"),"owned receipt status/id/amount parsed");
        api.open(a,"token");open=(h6)zp0.j1.holder.b.last;open.h=null;callback(open,0,0);check(sink.r.cents==0 && sink.r.receiveStatus==-1,"missing response never a success");
        api.reply(a,"谢谢！");q61.s0 text=(q61.s0)zp0.j1.holder.b.last;
        check(Arrays.equals(text.arguments,new Object[]{p.talker,"谢谢！",1,0,0L,""}),"reply goes to original group, not sender private chat");
        callback(text,0,0);check(sink.replies==1 && sink.sent,"reply success from native callback");
        api.reply(a,"谢谢！");callback(zp0.j1.holder.b.last,0,1);check(!sink.sent && sink.replies==2,"reply failure distinguished");
        int count=zp0.j1.holder.b.submitted;q61.s0.next=-1;
        try { api.reply(a,"x");throw new AssertionError("expected local creation failure"); }catch(IllegalStateException expected) {}
        check(zp0.j1.holder.b.submitted==count,"failed local text creation not submitted");q61.s0.next=42;
        sink.owned=new RedPacketEngine.Attempt(p,false,10000);
        open=new h6(1,1,"id",p.url,"","",p.talker,"v1.0","t","");
        constructed(api.openCtor,open,new Object[]{1,1,"id",p.url,"","",p.talker,"v1.0","t",""});
        callback(open,0,0);check(sink.opens==3,"legacy owned constructor mapped");
        int before=sink.opens;
        open=new h6(1,1,"different",p.url,"","",p.talker,"v1.0","t","");
        constructed(api.openCtor,open,new Object[]{1,1,"different",p.url,"","",p.talker,"v1.0","t",""});
        callback(open,0,0);check(sink.opens==before,"manual/unrelated packets ignored");
        api.query(a);Object forgotten=zp0.j1.holder.b.last;api.forget(a);before=sink.queries;callback(forgotten,0,0);
        check(sink.queries==before && api.requests.isEmpty(),"timeout cleanup removes scene ownership");
        zp0.j1.holder.b.accepted=false;
        try { api.query(a);throw new AssertionError("expected queue rejection"); }catch(IllegalStateException expected) {}
        check(sink.errors==0,"no swallowed adapter errors in valid fixtures");
        int hooks=XposedBridge.hooks.size();
        try {
            new RedPacketNative(new ClassLoader(RedPacketNativeTest.class.getClassLoader()) {
                protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                    if(name.equals("q61.s0"))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);
                }
            },sink);throw new AssertionError("unsupported adapter must fail");
        }catch(ClassNotFoundException expected) {}
        check(XposedBridge.hooks.size()==hooks,"incompatible profile fails before installing hooks");
        System.out.println("PASS "+checks+" native red packet callback/submission checks");
    }
}
