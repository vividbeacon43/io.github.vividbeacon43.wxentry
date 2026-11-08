package com.zafiro.wxentry.kernel;

import java.io.StringReader;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Incoming ordinary CNY transfers only. Confirmation is followed by a fresh status query. */
final class TransferEngine {
    static final long TIMEOUT_MS=45000;
    enum Stage { WAITING, QUERY, CONFIRM, VERIFY, DONE }
    static final class Packet {
        final String account,talker,sender,receiver,transaction,transfer,attach,memo;
        final int cents,invalid;
        Packet(String a,String t,String s,String r,String tx,String tr,int amount,int expiry,String extra) {
            this(a,t,s,r,tx,tr,amount,expiry,extra,null);
        }
        Packet(String a,String t,String s,String r,String tx,String tr,int amount,int expiry,String extra,String note) {
            account=a;talker=t;sender=s;receiver=r;transaction=tx;transfer=tr;cents=amount;invalid=expiry;attach=extra;
            memo=note;
        }
        String key() { return account+"|transfer|"+transfer; }
        String group() { return talker.endsWith("@chatroom")?talker:""; }
    }
    static final class Attempt {
        final Packet packet;final long deadline;Stage stage=Stage.WAITING;
        Query acceptedQuery;
        Attempt(Packet p,long end) { packet=p;deadline=end; }
    }
    static final class Query {
        int error,status=-1;long cents=-1;
        String sender="",receiver="",currency="",bindSerial="";
        boolean isPayer=true,needsAction,balanceAvailable;
    }
    interface Host {
        long now();boolean allowed(Packet p);boolean reserve(String key);long delay(Packet p);
        void later(long ms,Runnable action);
        void query(Attempt a,boolean verify) throws Throwable;
        void confirm(Attempt a,Query q) throws Throwable;
        void finished(Attempt a,String status,boolean success);
    }
    final Host host;final Map<String,Attempt> pending=new LinkedHashMap<String,Attempt>();
    TransferEngine(Host h) { host=h; }
    synchronized Attempt accept(final Packet p) {
        if(p==null || !host.allowed(p) || pending.containsKey(p.key()) || pending.size()>=32)return null;
        long delay=host.delay(p);if(delay<0 || delay>ReceiptConfig.MAX_DELAY_MS)return null;
        if(!host.reserve(p.key()))return null;
        final Attempt a=new Attempt(p,host.now()+delay+TIMEOUT_MS);pending.put(p.key(),a);
        host.later(delay+TIMEOUT_MS,new Runnable() { public void run() { expire(a); } });
        host.later(delay,new Runnable() { public void run() {
            synchronized(TransferEngine.this) {
                if(!live(a))return;a.stage=Stage.QUERY;
                try { host.query(a,false); }catch(Throwable failure) { finish(a,"转账查询提交失败",false); }
            }
        } });return a;
    }
    synchronized boolean live(Attempt a) {
        if(a==null || a.stage==Stage.DONE || pending.get(a.packet.key())!=a)return false;
        if(host.now()>=a.deadline) { finish(a,"转账结果超时，未重试",false);return false; }
        if(!host.allowed(a.packet)) { finish(a,"转账开关、账号或名单已变更，停止后续处理",false);return false; }
        return true;
    }
    static boolean identity(Packet p,Query q) {
        return q!=null && q.error==0 && !q.isPayer && p.account.equals(q.receiver) && p.sender.equals(q.sender)
                && q.cents>0 && q.cents<=Integer.MAX_VALUE && (p.cents<0 || p.cents==q.cents) && ("CNY".equals(q.currency) || "1".equals(q.currency));
    }
    synchronized void queried(Attempt a,Query q) {
        if(!live(a) || (a.stage!=Stage.QUERY && a.stage!=Stage.VERIFY))return;
        if(!identity(a.packet,q) || a.acceptedQuery!=null && a.acceptedQuery.cents!=q.cents) { finish(a,"转账身份、金额或查询结果不匹配，未自动确认",false);return; }
        if(a.stage==Stage.VERIFY) {
            finish(a,q.status==2001 && !q.needsAction?"转账接收成功":"未确认转账到账，请在微信中查看",q.status==2001 && !q.needsAction);return;
        }
        if(q.status!=2000) { finish(a,"转账已处理或不可接收，未重复确认",false);return; }
        if(q.needsAction || !q.balanceAvailable) { finish(a,"转账需要在微信中手动处理",false);return; }
        a.stage=Stage.CONFIRM;a.acceptedQuery=q;
        try { host.confirm(a,q); }catch(Throwable failure) { finish(a,"转账确认提交失败，未重试",false); }
    }
    synchronized void confirmed(Attempt a,int error,boolean needsAction) {
        if(!live(a) || a.stage!=Stage.CONFIRM)return;
        if(error!=0 || needsAction) { finish(a,"转账确认失败或需要手动处理",false);return; }
        a.stage=Stage.VERIFY;
        try { host.query(a,true); }catch(Throwable failure) { finish(a,"转账结果未确认，未重试",false); }
    }
    synchronized void finish(Attempt a,String status,boolean success) {
        if(a.stage==Stage.DONE)return;a.stage=Stage.DONE;pending.remove(a.packet.key());
        try { host.finished(a,status,success); }catch(Throwable ignored) { }
    }
    synchronized void expire(Attempt a) { if(a.stage!=Stage.DONE && host.now()>=a.deadline)finish(a,"转账结果超时，未重试",false); }
    static String child(Element parent,String name,boolean required) {
        Element result=null;
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element && n.getNodeName().equals(name)) {
            if(result!=null)throw new IllegalArgumentException("Duplicate transfer field");result=(Element)n;
        }
        if(result==null) { if(required)throw new IllegalArgumentException("Missing transfer field");return ""; }
        return result.getTextContent().trim();
    }
    static Element element(Element parent,String name) {
        Element result=null;
        for(Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element && n.getNodeName().equals(name)) {
            if(result!=null)throw new IllegalArgumentException("Duplicate transfer element");result=(Element)n;
        }
        if(result==null)throw new IllegalArgumentException("Missing transfer element");return result;
    }
    static Packet parse(String account,String talker,String content,boolean sent) {
        if(sent || !ReceiptConfig.validId(account) || !ReceiptConfig.validId(talker) || talker.equals(account)
                || content==null || content.length()>131072 || !content.contains("wcpayinfo"))return null;
        String upper=content.toUpperCase(Locale.ROOT);
        if(upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY"))return null;
        try {
            int start=content.indexOf('<');if(start<0)return null;
            String prefix=content.substring(0,start).trim();if(prefix.endsWith(":"))prefix=prefix.substring(0,prefix.length()-1).trim();
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities",false); }catch(Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false); }catch(Exception ignored) { }
            javax.xml.parsers.DocumentBuilder builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Element root=builder.parse(new InputSource(new StringReader(content.substring(start)))).getDocumentElement();
            Element app=root.getTagName().equals("appmsg")?root:element(root,"appmsg");
            if(!child(app,"type",true).equals("2000"))return null;
            Element pay=element(app,"wcpayinfo");if(!child(pay,"paysubtype",true).equals("1"))return null;
            String transaction=child(pay,"transcationid",false),alternative=child(pay,"transactionid",false);
            if(transaction.isEmpty())transaction=alternative;else if(!alternative.isEmpty() && !transaction.equals(alternative))return null;
            String transfer=child(pay,"transferid",true),sender=child(pay,"payer_username",false),receiver=child(pay,"receiver_username",false);
            if(!transaction.matches("[A-Za-z0-9_-]{1,256}") || !transfer.matches("[A-Za-z0-9_-]{1,256}"))return null;
            boolean group=talker.endsWith("@chatroom");
            if(sender.isEmpty())sender=group?prefix:talker;
            if(!ReceiptConfig.validId(sender) || sender.equals(account) || sender.endsWith("@chatroom"))return null;
            if(group) { if(!account.equals(receiver) || !prefix.isEmpty() && !prefix.equals(sender))return null; }
            else { if(!sender.equals(talker))return null;if(receiver.isEmpty())receiver=account; }
            if(!receiver.equals(account))return null;
            // Actual native cards can omit both fields. The bound native query is authoritative.
            String amount=child(pay,"total_fee",false);int cents=amount.isEmpty()?-1:Integer.parseInt(amount);if(!amount.isEmpty()&&cents<=0)return null;
            String currency=child(pay,"fee_type",false);if(!currency.isEmpty()&&!currency.equals("CNY")&&!currency.equals("1"))return null;
            String invalid=child(pay,"invalidtime",false);int expiry=invalid.isEmpty()?0:Integer.parseInt(invalid);if(expiry<0)return null;
            String attach=child(pay,"attach",false);if(attach.length()>8192)return null;
            String memo=child(pay,"pay_memo",false);if(memo.length()>16384)return null;
            return new Packet(account,talker,sender,receiver,transaction,transfer,cents,expiry,attach,memo);
        } catch(Throwable invalid) { return null; }
    }
}
