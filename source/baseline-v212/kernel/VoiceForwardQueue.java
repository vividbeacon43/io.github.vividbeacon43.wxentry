package com.zafiro.wxentry.kernel;

import java.util.*;

/** One outstanding native voice task. Android's main Handler owns all methods. */
final class VoiceForwardQueue {
    interface Ticket { int state() throws Throwable; } // 0 pending, 1 acknowledged, -1 failed.
    interface Environment {
        long now();
        void post(Runnable action,long delay);
        int randomDelaySeconds();
        boolean allowed();
        Ticket send(String recipient) throws Throwable;
        void changed(State state);
    }
    static final class State {
        final int total,sent,failed,unknown,remaining,delaySeconds;
        final boolean finished,stopping;
        State(int total,int sent,int failed,int unknown,int delay,boolean finished,boolean stopping) {
            this.total=total;this.sent=sent;this.failed=failed;this.unknown=unknown;
            remaining=total-sent-failed-unknown;delaySeconds=delay;
            this.finished=finished;this.stopping=stopping;
        }
    }
    private final Environment environment;
    private final List<String> recipients;
    private Ticket current;
    private long submittedAt;
    private int index,sent,failed,unknown,generation;
    private boolean started,stopping,finished;
    VoiceForwardQueue(Collection<String> chosen,Environment env) {
        if(env==null || chosen==null)throw new IllegalArgumentException("batch request");
        LinkedHashSet<String> unique=new LinkedHashSet<String>();
        for(String recipient:chosen)if(recipient!=null && recipient.length()>0)unique.add(recipient);
        if(unique.isEmpty())throw new IllegalArgumentException("no recipients");
        recipients=new ArrayList<String>(unique);environment=env;
    }
    boolean isFinished() { return finished; }
    void start() {
        if(started)return;started=true;changed(0);schedule(0);
    }
    void stop() {
        if(finished)return;stopping=true;changed(0);
        if(started)schedule(0);
    }
    private void schedule(long delay) {
        final int token=++generation;
        environment.post(new Runnable(){public void run(){if(token==generation && !finished)tick();}},delay);
    }
    private void changed(int delay) {
        try { environment.changed(new State(recipients.size(),sent,failed,unknown,delay,finished,stopping)); }
        catch(Throwable closedWindow) { /* UI disposal cannot duplicate or crash a native send. */ }
    }
    private void finish() { finished=true;generation++;changed(0); }
    private void afterResult() {
        changed(0);
        if(stopping || index>=recipients.size()) { finish();return; }
        int seconds=recipients.size()>5?environment.randomDelaySeconds():0;
        if(seconds<0 || seconds>5 || (recipients.size()>5 && seconds<1))
            throw new IllegalStateException("batch delay out of bounds");
        changed(seconds);schedule(seconds*1000L);
    }
    private void tick() {
        if(!environment.allowed())stopping=true;
        if(current!=null) {
            int state;
            try { state=current.state(); }
            catch(Throwable failure) { unknown++;current=null;stopping=true;finish();return; }
            if(state==0) {
                if(environment.now()-submittedAt>=90000) {
                    unknown++;current=null;stopping=true;finish();return;
                }
                changed(0);schedule(400);return;
            }
            current=null;
            if(state>0)sent++;else failed++;
            afterResult();return;
        }
        if(stopping || index>=recipients.size()) { finish();return; }
        String recipient=recipients.get(index++);
        try {
            current=environment.send(recipient);
            if(current==null)throw new IllegalStateException("native ticket unavailable");
            submittedAt=environment.now();changed(0);schedule(100);
        } catch(Throwable failure) { current=null;failed++;afterResult(); }
    }
}
