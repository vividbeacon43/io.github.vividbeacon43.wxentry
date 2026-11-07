package com.zafiro.wxentry.kernel;
import java.util.*;
public final class RedPacketEngineTest {
    static int checks;
    static void check(boolean value,String why) { checks++;if(!value)throw new AssertionError(why); }
    static final class Task implements Comparable<Task> {
        long time,order;Runnable run;Task(long t,long o,Runnable r) { time=t;order=o;run=r; }
        public int compareTo(Task b) { int n=Long.compare(time,b.time);return n==0?Long.compare(order,b.order):n; }
    }
    static final class Fake implements RedPacketEngine.Host {
        long time,order,delay=500;String account="self";boolean on=true,persist=true,failQuery,failOpen,throwObserver;
        int queries,opens,legacy,success,finished;String lastStatus,lastTiming;
        Set<String> seen=new HashSet<String>();PriorityQueue<Task> tasks=new PriorityQueue<Task>();
        public long now() { return time; }
        public boolean allowed(RedPacketEngine.Packet p) { return on && account.equals(p.account); }
        public boolean reserve(String key) { return persist && seen.add(key); }
        public long delay() { return delay; }
        public void later(long ms,Runnable r) { tasks.add(new Task(time+ms,order++,r)); }
        public void query(RedPacketEngine.Attempt a) { queries++;if(failQuery)throw new IllegalStateException(); }
        public void open(RedPacketEngine.Attempt a,String timing) { opens++;lastTiming=timing;if(failOpen)throw new IllegalStateException(); }
        public void legacy(RedPacketEngine.Attempt a) { legacy++; }
        public void finished(RedPacketEngine.Attempt a,String status,RedPacketEngine.Result r) {
            finished++;if(r!=null)success++;lastStatus=status;if(throwObserver)throw new IllegalStateException();
        }
        void next() { Task t=tasks.remove();time=t.time;t.run.run(); }
        void done() { int guard=200;while(!tasks.isEmpty() && guard-->0)next();check(guard>0,"timers bounded"); }
    }
    static RedPacketEngine.Packet packet(String id) {
        return RedPacketEngine.parse("self","room@chatroom","wxpay://c2cbizmessagehandler/hongbao/receivehongbao?sendid="+id+"&channelid=1&msgtype=1",10);
    }
    static RedPacketEngine.Query goodQuery() {
        RedPacketEngine.Query q=new RedPacketEngine.Query();q.hbStatus=2;q.receiveStatus=0;q.timing="token";q.sender="朋友";q.senderId="peer";return q;
    }
    static RedPacketEngine.Result goodResult() {
        RedPacketEngine.Result r=new RedPacketEngine.Result();r.receiveStatus=2;r.cents=123;r.sendId="id";return r;
    }
    public static void main(String[] args) {
        String red="<msg><appmsg><nativeurl><![CDATA["+packet("personal").url+"]]></nativeurl></appmsg></msg>";
        for(int type:new int[]{436207665,469762097,49})check(packet("personal").url.equals(RedPacketEngine.messageUrl(type,0L,red)),"incoming red packet message variant recognized "+type);
        check(RedPacketEngine.messageUrl(1,0L,red)==null,"ordinary text cannot trigger receipt");
        check(RedPacketEngine.messageUrl(469762097,1L,red)==null,"self personal packet cannot trigger receipt");
        check(RedPacketEngine.messageUrl(469762097,null,red)==null,"unknown direction cannot trigger receipt");
        check(RedPacketEngine.messageUrl(469762097,0L,red.replace("/hongbao/receivehongbao","/transfer/accept"))==null,"transfer URL cannot trigger red packet claim");
        RedPacketEngine.Packet p=packet("id");check(p!=null && p.channel==1 && p.msgType==1,"ordinary URL parsed");
        check(RedPacketEngine.parse("self","peer",p.url.replace("wxpay:","https:"),1)==null,"no HTTP requests");
        check(RedPacketEngine.parse("self","peer",p.url+"&sendid=other",1)==null,"duplicate query rejected");
        check(RedPacketEngine.parse("self","peer",p.url.replace("channelid=1","channelid=-1"),1)==null,"negative channel rejected");
        check(RedPacketEngine.parse("self","peer",p.url.replace("msgtype=1","msgtype=2"),1)==null,"special packets not guessed");
        check(RedPacketEngine.parse("","peer",p.url,1)==null,"no account rejected");
        check(RedPacketEngine.parse("self","",p.url,1)==null,"no conversation rejected");
        check(RedPacketEngine.parse("self","peer",p.url.replace("sendid=id","sendid=%ZZ"),1)==null,"malformed encoding rejected");
        check(RedPacketEngine.parse("self","peer",p.url.replace("sendid=id","sendid=%69%64"),1).sendId.equals("id"),"URL decoding");
        Fake h=new Fake();RedPacketEngine e=new RedPacketEngine(h);RedPacketEngine.Attempt a=e.accept(p,true);
        check(a!=null && h.queries==0 && h.legacy==0,"silent delayed without UI");
        check(e.accept(p,true)==null,"duplicate insert suppressed");h.next();
        check(h.time==500 && h.queries==1 && h.legacy==0,"configured delay then query only");
        e.opened(a,goodResult());check(h.success==0,"open callback before query ignored");
        e.queried(a,goodQuery());check(h.opens==1 && "token".equals(h.lastTiming),"timing token forwarded once");
        e.queried(a,goodQuery());check(h.opens==1,"duplicate query callback ignored");
        RedPacketEngine.Result r=goodResult();e.opened(a,r);
        check(h.success==1 && e.pending.isEmpty(),"confirmed success removes ownership");
        check("朋友".equals(r.sender) && "peer".equals(r.senderId),"query sender fallback retained");
        e.opened(a,goodResult());h.done();check(h.success==1 && h.finished==1,"duplicate and late callbacks ignored");
        check(new RedPacketEngine(h).accept(p,true)==null,"persisted reservation survives engine recreation");
        for(int state:new int[]{1,2,-1}) {
            h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();RedPacketEngine.Query q=goodQuery();q.receiveStatus=state;e.queried(a,q);
            check(h.opens==0 && h.success==0 && e.pending.isEmpty(),"non-receivable status "+state);
        }
        for(int state:new int[]{1,3,4,5,-1}) {
            h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();RedPacketEngine.Query q=goodQuery();q.hbStatus=state;e.queried(a,q);
            check(h.opens==0 && h.success==0,"inactive packet status "+state);
        }
        for(int kind=0;kind<3;kind++) {
            h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();RedPacketEngine.Query q=goodQuery();
            if(kind==0)q.error=1;if(kind==1)q.timing="";if(kind==2)q.needsAction=true;e.queried(a,q);
            check(h.opens==0 && h.success==0,"query failure/token/confirmation barrier "+kind);
        }
        for(int kind=0;kind<5;kind++) {
            h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();e.queried(a,goodQuery());r=goodResult();
            if(kind==0)r.error=1;if(kind==1)r.receiveStatus=0;if(kind==2)r.cents=0;if(kind==3)r.sendId="other";if(kind==4)r=null;
            e.opened(a,r);check(h.success==0 && h.finished==1,"only owned confirmed positive receipt succeeds "+kind);
        }
        for(int stage=0;stage<3;stage++) {
            h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);if(stage>0)h.next();if(stage>1)e.queried(a,goodQuery());h.on=false;
            if(stage==0)h.next();else if(stage==1)e.queried(a,goodQuery());else e.opened(a,goodResult());
            check(h.success==0 && e.pending.isEmpty(),"disable during stage "+stage);
        }
        h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();h.account="other";e.queried(a,goodQuery());
        check(h.opens==0 && h.success==0,"account switch blocks open");
        h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,true);h.next();h.done();e.queried(a,goodQuery());
        check(h.opens==0 && h.finished==1 && h.success==0,"timeout never retries or replies");
        h=new Fake();h.persist=false;e=new RedPacketEngine(h);check(e.accept(p,true)==null && h.tasks.isEmpty(),"failed persistence blocks network");
        h=new Fake();h.failQuery=true;e=new RedPacketEngine(h);e.accept(p,true);h.next();check(h.finished==1 && e.pending.isEmpty(),"query submit exception cleaned");
        h=new Fake();h.failOpen=true;e=new RedPacketEngine(h);a=e.accept(p,true);h.next();e.queried(a,goodQuery());check(h.finished==1 && h.success==0,"open submit exception not success");
        h=new Fake();e=new RedPacketEngine(h);a=e.accept(p,false);h.next();check(h.legacy==1 && h.queries==0,"legacy launch only when selected");
        check(e.owned(p.url,p.sendId)==a && e.owned(p.url,"other")==null,"legacy ownership matches URL and sendId");
        e.queried(a,goodQuery());check(h.opens==0,"legacy query leaves opening to UI");e.opened(a,goodResult());check(h.success==1,"legacy success also supported");
        h=new Fake();e=new RedPacketEngine(h);for(int i=0;i<32;i++)check(e.accept(packet("id"+i),true)!=null,"bounded pending slot "+i);
        check(e.accept(packet("overflow"),true)==null,"pending limit");
        h=new Fake();h.delay=-5;e=new RedPacketEngine(h);e.accept(p,true);h.next();check(h.time==0,"negative delay clamped");
        h=new Fake();h.delay=Long.MAX_VALUE;e=new RedPacketEngine(h);e.accept(p,true);h.next();check(h.time==86400000 && h.queries==1,"24 hour delay has a separate response timeout");
        check("第一条".equals(RedPacketEngine.reply("\n 第一条 \n第二条\n",false,new Random(1))),"fixed first nonempty template");
        Set<String> selected=new HashSet<String>();Random rng=new Random(1);for(int i=0;i<50;i++)selected.add(RedPacketEngine.reply("一\n二\n三",true,rng));
        check(selected.size()==3,"random can select every custom template");
        check(RedPacketEngine.reply(" \n",true,rng)==null,"empty list never sends");
        check(RedPacketEngine.templates("x\nx\ny").size()==2,"duplicate templates removed");
        check("0.01".equals(RedPacketEngine.amount(1)) && "1.23".equals(RedPacketEngine.amount(123)),"integer currency formatting");
        System.out.println("PASS "+checks+" red packet state/ownership/template checks");
    }
}
