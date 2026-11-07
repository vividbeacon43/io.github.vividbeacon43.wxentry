package com.zafiro.wxentry.kernel;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** No Android dependencies: request ownership and reply decisions can be tested on the JVM. */
final class RedPacketEngine {
    static final long TIMEOUT_MS = 45000;
    static final int MAX_PENDING = 32;
    enum Stage { WAITING, QUERY, OPEN, DONE }
    static final class Packet {
        final String account, talker, url, sendId;
        final int channel, msgType;
        final long msgId;
        Packet(String account, String talker, String url, String sendId, int channel, int msgType, long msgId) {
            this.account=account; this.talker=talker; this.url=url; this.sendId=sendId;
            this.channel=channel; this.msgType=msgType; this.msgId=msgId;
        }
        String key() { return account + "|" + sendId; }
    }
    static final class Attempt {
        final Packet packet;
        final boolean silent;
        final long deadline;
        Stage stage=Stage.WAITING;
        String sender="", senderId="";
        Attempt(Packet p, boolean s, long end) { packet=p; silent=s; deadline=end; }
    }
    static final class Query {
        int error, hbStatus=-1, receiveStatus=-1;
        String timing="", sender="", senderId="";
        boolean needsAction;
    }
    static final class Result {
        int error, receiveStatus=-1;
        long cents;
        String sendId="", sender="", senderId="";
    }
    interface Host {
        long now();
        boolean allowed(Packet p);
        boolean reserve(String key); // Persist before submitting: unknown results must never be retried.
        long delay();
        void later(long delay, Runnable action);
        void query(Attempt a) throws Throwable;
        void open(Attempt a, String timing) throws Throwable;
        void legacy(Attempt a) throws Throwable;
        void finished(Attempt a, String status, Result success);
    }
    final Host host;
    final Map<String,Attempt> pending=new LinkedHashMap<String,Attempt>();
    RedPacketEngine(Host h) { host=h; }

    synchronized Attempt accept(final Packet p, boolean silent) {
        if (p==null || !host.allowed(p) || pending.containsKey(p.key()) || pending.size()>=MAX_PENDING) return null;
        if (!host.reserve(p.key())) return null;
        final long delay=Math.max(0,Math.min(ReceiptConfig.MAX_DELAY_MS,host.delay()));
        final Attempt a=new Attempt(p,silent,host.now()+delay+TIMEOUT_MS);
        pending.put(p.key(),a);
        host.later(delay+TIMEOUT_MS,new Runnable() { public void run() { expire(a); } });
        host.later(delay,new Runnable() {
            public void run() { begin(a); }
        });
        return a;
    }
    synchronized void begin(Attempt a) {
        if (!live(a)) return;
        a.stage=Stage.QUERY;
        try { if (a.silent) host.query(a); else host.legacy(a); }
        catch (Throwable failure) { finish(a,"提交领取请求失败",null); }
    }
    synchronized boolean live(Attempt a) {
        if (a==null || a.stage==Stage.DONE || pending.get(a.packet.key())!=a) return false;
        if (host.now()>=a.deadline) { finish(a,"领取结果超时，未重试",null); return false; }
        if (!host.allowed(a.packet)) { finish(a,"设置或账号已变更，停止后续处理",null); return false; }
        return true;
    }
    synchronized void queried(Attempt a, Query q) {
        if (!live(a) || a.stage!=Stage.QUERY) return;
        if (q==null || q.error!=0) { finish(a,"红包查询失败",null); return; }
        a.sender=q.sender; a.senderId=q.senderId;
        if (q.receiveStatus!=0 || q.hbStatus!=2) { finish(a,"已领取、已领完或已过期，未自动回复",null); return; }
        if (q.needsAction || empty(q.timing)) { finish(a,"需要在微信中手动处理",null); return; }
        a.stage=Stage.OPEN;
        if (a.silent) try { host.open(a,q.timing); }
        catch (Throwable failure) { finish(a,"提交拆包请求失败",null); }
    }
    synchronized void opened(Attempt a, Result r) {
        if (!live(a) || a.stage!=Stage.OPEN) return;
        if (r==null || r.error!=0 || r.receiveStatus!=2 || r.cents<=0 || !a.packet.sendId.equals(r.sendId)) {
            finish(a,"未确认领取成功，未自动回复",null); return;
        }
        if (empty(r.sender)) r.sender=a.sender;
        if (empty(r.senderId)) r.senderId=a.senderId;
        finish(a,"领取成功",r);
    }
    synchronized Attempt owned(String url, String sendId) {
        for (Attempt a : new ArrayList<Attempt>(pending.values()))
            if (!a.silent && a.packet.url.equals(url) && a.packet.sendId.equals(sendId) && live(a)) return a;
        return null;
    }
    synchronized void expire(Attempt a) {
        if (a.stage!=Stage.DONE && host.now()>=a.deadline) finish(a,"领取结果超时，未重试",null);
    }
    synchronized void finish(Attempt a,String status,Result result) {
        if (a.stage==Stage.DONE) return;
        a.stage=Stage.DONE; pending.remove(a.packet.key());
        try { host.finished(a,status,result); } catch (Throwable ignored) { }
    }
    static boolean empty(String s) { return s==null || s.trim().length()==0; }

    static String messageUrl(int type,Long sent,String content) {
        if((type&0xffff)!=49||sent==null||sent.longValue()!=0||content==null)return null;
        try {
            int start=content.indexOf("<nativeurl>"),end=content.indexOf("</nativeurl>");
            if(start<0||end<=start)return null;
            String url=content.substring(start+11,end).trim();
            if(url.startsWith("<![CDATA["))url=url.substring(9);
            if(url.endsWith("]]>"))url=url.substring(0,url.length()-3);
            url=url.trim().replace("&amp;","&");java.net.URI uri=new java.net.URI(url);
            return "wxpay".equals(uri.getScheme())&&"c2cbizmessagehandler".equals(uri.getHost())&&"/hongbao/receivehongbao".equals(uri.getPath())?url:null;
        }catch(Exception invalid){return null;}
    }

    static Packet parse(String account,String talker,String url,long msgId) {
        if (empty(account) || empty(talker) || empty(url) || talker.indexOf('|')>=0 || account.indexOf('|')>=0) return null;
        try {
            URI uri=new URI(url);
            if (!"wxpay".equals(uri.getScheme()) || !"c2cbizmessagehandler".equals(uri.getHost())
                    || !"/hongbao/receivehongbao".equals(uri.getPath()) || uri.getRawQuery()==null) return null;
            Map<String,String> values=new LinkedHashMap<String,String>();
            for (String part : uri.getRawQuery().split("&")) {
                String[] pair=part.split("=",2);
                String key=URLDecoder.decode(pair[0],"UTF-8");
                if (values.containsKey(key)) return null;
                values.put(key,pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"");
            }
            String id=values.get("sendid");
            int channel=Integer.parseInt(values.get("channelid"));
            int type=values.containsKey("msgtype")?Integer.parseInt(values.get("msgtype")):1;
            if (id==null || !id.matches("[A-Za-z0-9_-]{1,256}") || channel<0 || type!=1) return null;
            return new Packet(account,talker,url,id,channel,type,msgId);
        } catch (Throwable malformed) { return null; }
    }
    static List<String> templates(String text) {
        List<String> out=new ArrayList<String>();
        if (text!=null) for (String line : text.split("\\r?\\n")) {
            line=line.trim();
            if (line.length()>0 && line.length()<=300 && out.size()<50 && !out.contains(line)) out.add(line);
        }
        return out;
    }
    static String reply(String text,boolean random,Random rng) {
        List<String> lines=templates(text);
        return lines.isEmpty()?null:lines.get(random?rng.nextInt(lines.size()):0);
    }
    static String amount(long cents) { return (cents/100)+"."+((cents%100)<10?"0":"")+(cents%100); }
}
