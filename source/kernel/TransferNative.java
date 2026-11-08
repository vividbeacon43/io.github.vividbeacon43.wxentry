package com.zafiro.wxentry.kernel;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

final class TransferNative {
    interface Listener {
        void queried(TransferEngine.Attempt a,TransferEngine.Query q);
        void confirmed(TransferEngine.Attempt a,int error,boolean needsAction);
        void error(Throwable error);
    }
    static final class Owned {
        final TransferEngine.Attempt attempt;final boolean confirm;
        TransferEngine.Query parsed;boolean parsedConfirm,needsAction;
        Owned(TransferEngine.Attempt a,boolean c) { attempt=a;confirm=c; }
    }
    final Listener listener;final Constructor<?> queryCtor,confirmCtor;
    final Method account,network,submit;final Field queue;
    final Map<Object,Owned> requests=new IdentityHashMap<Object,Owned>();
    TransferNative(ClassLoader cl,Listener l) throws Throwable {
        listener=l;
        Class<?> query=Class.forName("com.tencent.mm.plugin.remittance.model.h1",false,cl);
        Class<?> confirm=Class.forName("com.tencent.mm.plugin.remittance.model.n0",false,cl);
        queryCtor=query.getDeclaredConstructor(int.class,String.class,String.class,int.class,String.class,String.class);
        confirmCtor=confirm.getDeclaredConstructor(String.class,String.class,int.class,String.class,String.class,int.class,
            String.class,String.class,int.class,String.class,Map.class,long.class,String.class,String.class);
        Method queryParsed=query.getDeclaredMethod("onGYNetEnd",int.class,String.class,JSONObject.class);
        Method confirmParsed=confirm.getDeclaredMethod("onGYNetEnd",int.class,String.class,JSONObject.class);
        query.getDeclaredField("d");confirm.getDeclaredField("d");
        account=Class.forName("w41.z1",false,cl).getDeclaredMethod("u");
        network=Class.forName("zp0.j1",false,cl).getDeclaredMethod("q");
        queue=Class.forName("zp0.y",false,cl).getDeclaredField("b");
        submit=Class.forName("com.tencent.mm.modelbase.s1",false,cl).getDeclaredMethod("g",Class.forName("com.tencent.mm.modelbase.n1",false,cl));
        XC_MethodHook parsed=new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam p) {
                Owned own; synchronized(requests) { own=requests.get(p.thisObject); }
                if(own==null || p.hasThrowable() || ((Number)p.args[0]).intValue()!=0 || !(p.args[2] instanceof JSONObject))return;
                try {
                    JSONObject json=(JSONObject)p.args[2];
                    if(own.confirm) { own.parsedConfirm=true;own.needsAction=requiresAction(json); }
                    else own.parsed=parseQuery(json);
                } catch(Throwable failure) { listener.error(failure); }
            }
        };
        XposedBridge.hookMethod(queryParsed,parsed);XposedBridge.hookMethod(confirmParsed,parsed);
        NativeSceneBus.register(cl,new NativeSceneBus.Listener() {
            public void ended(Object scene,int type,int code) { try { end(scene,type,code); }catch(Throwable failure) { listener.error(failure); } }
        });
    }
    String account() throws Throwable { Object value=account.invoke(null);return value==null?"":String.valueOf(value); }
    static boolean nonempty(JSONObject json,String key) {
        Object value=json.opt(key);if(value==null || value==JSONObject.NULL)return false;
        String text=String.valueOf(value).trim();return !text.isEmpty() && !text.equals("{}") && !text.equals("[]") && !text.equals("0");
    }
    static boolean requiresAction(JSONObject json) {
        for(String key:new String[]{"intercept_win","intercept_win_after","half_page_info","jump_remind_info","showmess"})if(nonempty(json,key))return true;
        JSONObject real=json.optJSONObject("real_name_info");
        return real!=null && !"0".equals(real.optString("guide_flag","0"));
    }
    static TransferEngine.Query parseQuery(JSONObject json) {
        TransferEngine.Query q=new TransferEngine.Query();
        q.status=json.optInt("trans_status",-1);q.cents=json.optLong("fee",-1);q.currency=json.optString("fee_type","");
        q.sender=json.optString("payer_name","");q.receiver=json.optString("receiver_name","");q.isPayer=json.optBoolean("is_payer",true);
        q.needsAction=requiresAction(json) || json.optInt("delay_confirm_flag",0)!=0 || json.optBoolean("is_quota_exceeded",false);
        JSONObject info=json.optJSONObject("recv_account_info");
        if(info==null)q.balanceAvailable=true; // Native UI's ordinary legacy default is channel 1, no serial.
        else {
            JSONArray channels=info.optJSONArray("recv_channel");
            if(channels!=null)for(int i=0;i<channels.length();i++) {
                JSONObject channel=channels.optJSONObject(i);if(channel==null)continue;
                if(channel.optInt("recv_channel_type",-1)==1 && channel.optInt("recv_channel_avail_state",-1)==1
                        && !nonempty(channel,"jump_remind_info") && !nonempty(channel,"sub_recv_channel_info")) {
                    q.balanceAvailable=true;q.bindSerial=channel.optString("bind_serial","");break;
                }
            }
        }
        return q;
    }
    void submit(Object scene,Owned owned) throws Throwable {
        synchronized(requests) { requests.put(scene,owned); }
        if(!Boolean.TRUE.equals(submit.invoke(queue.get(network.invoke(null)),scene)))throw new IllegalStateException("Transfer queue rejected request");
    }
    void query(TransferEngine.Attempt a,boolean verify) throws Throwable {
        TransferEngine.Packet p=a.packet;
        submit(queryCtor.newInstance(verify?1:0,p.transaction,p.transfer,p.invalid,p.group(),p.attach),new Owned(a,false));
    }
    void confirm(TransferEngine.Attempt a,TransferEngine.Query q) throws Throwable {
        TransferEngine.Packet p=a.packet;
        submit(confirmCtor.newInstance(p.transaction,p.transfer,Math.toIntExact(q.cents),"confirm",p.talker,p.invalid,"",p.group(),1,q.bindSerial,null,0L,q.sender,p.attach),new Owned(a,true));
    }
    void forget(TransferEngine.Attempt a) {
        synchronized(requests) { Iterator<Owned> it=requests.values().iterator();while(it.hasNext())if(it.next().attempt==a)it.remove(); }
    }
    void end(Object scene,int type,int code) {
        Owned own;synchronized(requests) { own=requests.remove(scene); }if(own==null)return;
        int error=type!=0 || code!=0?1:0;
        if(own.confirm)listener.confirmed(own.attempt,error!=0 || !own.parsedConfirm?1:0,own.needsAction);
        else {
            TransferEngine.Query q=own.parsed==null?new TransferEngine.Query():own.parsed;
            q.error=error!=0 || own.parsed==null?1:0;listener.queried(own.attempt,q);
        }
    }
}
