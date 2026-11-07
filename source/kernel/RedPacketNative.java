package com.zafiro.wxentry.kernel;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Adapter for the 8.0.80 DEX contracts checked by NativeRedPacketContractCheck. */
final class RedPacketNative {
    interface Listener {
        RedPacketEngine.Attempt legacy(String url,String sendId);
        void query(RedPacketEngine.Attempt a,RedPacketEngine.Query q);
        void opened(RedPacketEngine.Attempt a,RedPacketEngine.Result r);
        void replied(RedPacketEngine.Attempt a,boolean success);
        void error(Throwable failure);
    }
    static final class Tracked {
        final RedPacketEngine.Attempt attempt;
        final int kind;
        Tracked(RedPacketEngine.Attempt a,int k) { attempt=a;kind=k; }
    }
    final Listener listener;
    final Method account,nickname,head,network,submit;
    final Field queueField;
    final Constructor<?> queryCtor,openCtor,textCtor;
    final Map<Object,Tracked> requests=new IdentityHashMap<Object,Tracked>();

    RedPacketNative(ClassLoader cl,Listener callback) throws Throwable {
        listener=callback;
        Class<?> query=Class.forName("com.tencent.mm.plugin.luckymoney.model.n6",false,cl);
        Class<?> open=Class.forName("com.tencent.mm.plugin.luckymoney.model.h6",false,cl);
        Class<?> result=Class.forName("com.tencent.mm.plugin.luckymoney.model.e1",false,cl);
        Class<?> base=Class.forName("com.tencent.mm.modelbase.n1",false,cl);
        Class<?> queue=Class.forName("com.tencent.mm.modelbase.s1",false,cl);
        Class<?> locator=Class.forName("zp0.j1",false,cl);
        queryCtor=query.getDeclaredConstructor(int.class,int.class,String.class,String.class,int.class,String.class,String.class);
        openCtor=open.getDeclaredConstructor(int.class,int.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class);
        final Constructor<?> shortOpenCtor=open.getDeclaredConstructor(int.class,int.class,String.class,String.class,String.class,String.class,String.class,String.class,String.class);
        textCtor=Class.forName("q61.s0",false,cl).getDeclaredConstructor(String.class,String.class,int.class,int.class,long.class,String.class);
        account=Class.forName("w41.z1",false,cl).getDeclaredMethod("u");
        nickname=Class.forName("w41.z1",false,cl).getDeclaredMethod("m");
        head=Class.forName("com.tencent.mm.plugin.luckymoney.model.o5",false,cl).getDeclaredMethod("l");
        network=locator.getDeclaredMethod("q");
        queueField=Class.forName("zp0.y",false,cl).getDeclaredField("b");
        submit=queue.getDeclaredMethod("g",base);
        // Fail before installing any hooks if a required field has moved.
        for(String f:new String[]{"s","t","P","o","z","C"})query.getDeclaredField(f);
        open.getDeclaredField("h");open.getDeclaredField("i");open.getDeclaredField("m");
        for(String f:new String[]{"A","q","n","i","Q"})result.getDeclaredField(f);
        Class.forName("q61.s0",false,cl).getDeclaredField("f");
        Class.forName("com.tencent.mm.plugin.luckymoney.model.q5",false,cl).getDeclaredMethod("isJumpRemind");
        NativeSceneBus.register(cl,new NativeSceneBus.Listener() {
            public void ended(Object scene,int type,int code) {
                try { RedPacketNative.this.ended(scene,type,code); }
                catch(Throwable failure) { listener.error(failure); }
            }
        });
        XC_MethodHook created=new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if(p.hasThrowable() || p.args.length<4) return;
                    RedPacketEngine.Attempt a=listener.legacy((String)p.args[3],(String)p.args[2]);
                    if(a!=null)track(p.thisObject,a,p.thisObject.getClass()==queryCtor.getDeclaringClass()?0:1);
                } catch(Throwable failure) { listener.error(failure); }
            }
        };
        XposedBridge.hookMethod(queryCtor,created);
        XposedBridge.hookMethod(openCtor,created);
        // Some UI callers use the nine-argument delegating overload.
        XposedBridge.hookMethod(shortOpenCtor,created);
    }
    String account() throws Throwable { return string(account.invoke(null)); }
    static String string(Object value) { return value==null?"":String.valueOf(value); }
    static Object field(Object object,String name) throws Throwable {
        for(Class<?> k=object.getClass();k!=null;k=k.getSuperclass())try {
            Field f=k.getDeclaredField(name);f.setAccessible(true);return f.get(object);
        } catch(NoSuchFieldException absent) { }
        throw new NoSuchFieldException(name);
    }
    void track(Object scene,RedPacketEngine.Attempt a,int kind) {
        synchronized(requests) { requests.put(scene,new Tracked(a,kind)); }
    }
    void forget(RedPacketEngine.Attempt a) {
        synchronized(requests) {
            Iterator<Tracked> it=requests.values().iterator();
            while(it.hasNext())if(it.next().attempt==a)it.remove();
        }
    }
    void submit(Object scene) throws Throwable {
        Object holder=network.invoke(null);
        Object queue=queueField.get(holder);
        if(!Boolean.TRUE.equals(submit.invoke(queue,scene)))throw new IllegalStateException("Native queue rejected request");
    }
    void query(RedPacketEngine.Attempt a) throws Throwable {
        RedPacketEngine.Packet p=a.packet;
        Object scene=queryCtor.newInstance(p.msgType,p.channel,p.sendId,p.url,0,"v1.0",p.talker);
        track(scene,a,0);submit(scene);
    }
    void open(RedPacketEngine.Attempt a,String timing) throws Throwable {
        RedPacketEngine.Packet p=a.packet;
        Object scene=openCtor.newInstance(p.msgType,p.channel,p.sendId,p.url,string(head.invoke(null)),
                string(nickname.invoke(null)),p.talker,"v1.0",timing,"");
        track(scene,a,1);submit(scene);
    }
    void reply(RedPacketEngine.Attempt a,String text) throws Throwable {
        Object scene=textCtor.newInstance(a.packet.talker,text,1,0,0L,"");
        if(((Number)field(scene,"f")).longValue()<=0)throw new IllegalStateException("No native outgoing message");
        track(scene,a,2);submit(scene);
    }
    void ended(Object scene,int errorType,int errorCode) throws Throwable {
        Tracked own;
        synchronized(requests) { own=requests.remove(scene); }
        if(own==null)return; // Never infer success from unrelated/manual requests.
        int error=(errorType!=0 || errorCode!=0)?1:0;
        if(own.kind==2) { listener.replied(own.attempt,error==0);return; }
        if(own.kind==0) {
            RedPacketEngine.Query q=new RedPacketEngine.Query();q.error=error;
            if(error==0) {
                q.hbStatus=((Number)field(scene,"s")).intValue();
                q.receiveStatus=((Number)field(scene,"t")).intValue();
                q.timing=string(field(scene,"P"));q.sender=string(field(scene,"o"));q.senderId=string(field(scene,"z"));
                String agreed=string(field(scene,"C"));
                Method jump=scene.getClass().getMethod("isJumpRemind");
                q.needsAction=Boolean.TRUE.equals(jump.invoke(scene)) || (!RedPacketEngine.empty(agreed) && !"1".equals(agreed));
            }
            listener.query(own.attempt,q);
        } else {
            RedPacketEngine.Result r=new RedPacketEngine.Result();r.error=error;r.networkType=errorType;r.networkCode=errorCode;
            r.requestVerified=own.attempt.packet.sendId.equals(string(field(scene,"i")))&&own.attempt.packet.url.equals(string(field(scene,"m")));
            Object data=error==0?field(scene,"h"):null;r.detailPresent=data!=null;
            if(data!=null) {
                r.receiveStatus=((Number)field(data,"A")).intValue();r.cents=((Number)field(data,"q")).longValue();
                r.sendId=string(field(data,"n"));r.sender=string(field(data,"i"));r.senderId=string(field(data,"Q"));
                r.responseIdState=RedPacketEngine.empty(r.sendId)?"missing":own.attempt.packet.sendId.equals(r.sendId)?"match":"mismatch";
                // Native open UI uses the request's h6.i / h6.m, not e1.n, for identity.
                // Only a tracked personal request with matching immutable input values may fill an omitted echo.
                if(!own.attempt.packet.talker.endsWith("@chatroom")&&RedPacketEngine.empty(r.sendId)&&r.requestVerified) {
                    r.sendId=own.attempt.packet.sendId;r.idFromRequest=true;
                }
            }
            listener.opened(own.attempt,r);
        }
    }
}
