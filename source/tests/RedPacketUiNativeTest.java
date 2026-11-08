package com.zafiro.wxentry.kernel;
import android.content.Context;
import android.os.Bundle;
import com.tencent.mm.plugin.wxpay.api.IPluginWxPay;

public final class RedPacketUiNativeTest {
    static int checks;
    static final String URL="wxpay://c2cbizmessagehandler/hongbao/receivehongbao?sendid=synthetic&channelid=1";
    static final String XML="<msg><appmsg><type>2001</type><wcpayinfo><nativeurl><![CDATA["+URL+"]]></nativeurl></wcpayinfo></appmsg></msg>";
    static final ClassLoader CL=RedPacketUiNativeTest.class.getClassLoader();
    static final class Wallet implements IPluginWxPay {
        int calls; boolean fail; Context context; Bundle extras; xx0.r message;
        public void De(Context context,Bundle extras,xx0.r message) {
            calls++; if(fail)throw new IllegalStateException("native page error");
            this.context=context;this.extras=extras;this.message=message;
        }
    }
    interface Action {void run() throws Throwable;}
    static void check(boolean ok,String reason) {checks++;if(!ok)throw new AssertionError(reason);}
    static void rejects(Action action,String reason) throws Throwable {
        boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}check(rejected,reason);
    }
    static xx0.r valid() {
        xx0.r p=new xx0.r();p.s1=URL;p.r1="1002";p.i=2001;p.opaqueEnvelope=new Object();xx0.r.next=p;return p;
    }
    public static void main(String[] args) throws Throwable {
        Wallet wallet=new Wallet();fi5.n0.plugin=wallet;xx0.r parsed=valid();Context context=new Context();
        RedPacketUi.Launch launch=RedPacketUiNative.prepare(CL,URL,73,"peer",XML);
        check(wallet.calls==0,"preparation cannot submit a receipt or open a page");
        launch.start(context);
        check(wallet.calls==1 && wallet.context==context,"actual registered native wallet receives chosen context");
        check(fi5.n0.requested==IPluginWxPay.class,"uses existing native wallet, never constructs its own plugin");
        check(XML.equals(xx0.r.lastInput),"native parser receives complete original card XML");
        check(wallet.message==parsed && wallet.message.opaqueEnvelope==parsed.opaqueEnvelope,"entire native envelope model is preserved without rebuilding skin fields");
        check("peer".equals(wallet.extras.values.get("key_username")),"personal session reaches native page entry");
        check(Long.valueOf(73).equals(wallet.extras.values.get("key_msgid")),"message identity reaches native page entry");
        check(wallet.extras.values.size()==2,"module leaves scene, envelope metadata and page selection to native helper");
        String group="sender:\n"+XML;valid();RedPacketUiNative.prepare(CL,URL,74,"room@chatroom",group).start(context);
        check(group.equals(xx0.r.lastInput) && "room@chatroom".equals(wallet.extras.values.get("key_username")),"group prefix and session are preserved for native parser");
        final int before=wallet.calls;
        rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",null),"missing original card cannot hard-code an old page");
        rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",new String(new char[262145])),"oversized source is rejected");
        rejects(()->RedPacketUiNative.prepare(CL,URL,1,"bad session",XML),"invalid session cannot route");
        rejects(()->RedPacketUiNative.prepare(null,URL,1,"peer",XML),"missing native classloader cannot route");
        xx0.r.next=null;rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"native parse failure cannot route");
        valid().s1="other";rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"card URL mismatch cannot route a different packet");
        valid().i=2000;rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"other app-message types are rejected");
        valid().r1=null;rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"missing native scene is not invented");
        valid().r1="not-an-int";rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"invalid native scene is rejected before invocation");
        valid();fi5.n0.plugin=new Object();rejects(()->RedPacketUiNative.prepare(CL,URL,1,"peer",XML),"unavailable native wallet cannot route");
        check(wallet.calls==before,"invalid preparations leave native operations untouched");
        fi5.n0.plugin=wallet;valid();wallet.fail=true;
        rejects(()->RedPacketUiNative.prepare(CL,URL,75,"peer",XML).start(context),"native route failure propagates without a hard-coded fallback");
        check(wallet.calls==before+1,"failed native route is invoked only once");
        System.out.println("PASS "+checks+" native red packet source, envelope and entry delegation checks");
    }
}
