package com.zafiro.wxentry.kernel;
import java.util.*;
public class VoiceForwardQueueTest {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static List<String> ids(int n){List<String> r=new ArrayList<>();for(int i=0;i<n;i++)r.add(i%2==0?"friend"+i:"group"+i+"@chatroom");return r;}
    static final class Event implements Comparable<Event> {
        long when,serial;Runnable action;
        Event(long t,long order,Runnable r){when=t;serial=order;action=r;}
        public int compareTo(Event e){int c=Long.compare(when,e.when);return c==0?Long.compare(serial,e.serial):c;}
    }
    static final class Fake implements VoiceForwardQueue.Environment {
        long now,serial;boolean allowed=true,pendingForever,ticketThrows,submitThrows,observerThrows;
        PriorityQueue<Event> tasks=new PriorityQueue<>();List<String> sent=new ArrayList<>();List<Long> times=new ArrayList<>();
        List<Integer> delays=new ArrayList<>();List<VoiceForwardQueue.State> states=new ArrayList<>();
        Set<String> failures=new HashSet<>();int randomCalls;VoiceForwardQueue.State state;
        public long now(){return now;}
        public void post(Runnable r,long delay){tasks.add(new Event(now+delay,serial++,r));}
        public int randomDelaySeconds(){return ++randomCalls%5+1;}
        public boolean allowed(){return allowed;}
        public VoiceForwardQueue.Ticket send(final String to) {
            sent.add(to);times.add(now);if(submitThrows)throw new IllegalStateException();
            final long due=now+300;
            return new VoiceForwardQueue.Ticket(){public int state(){if(ticketThrows)throw new IllegalStateException();return pendingForever||now<due?0:failures.contains(to)?-1:1;}};
        }
        public void changed(VoiceForwardQueue.State s){state=s;states.add(s);if(s.delaySeconds>0)delays.add(s.delaySeconds);if(observerThrows)throw new IllegalStateException();}
        void next(){Event e=tasks.remove();now=e.when;e.action.run();}
        void done(){int guard=10000;while(!tasks.isEmpty() && guard-->0)next();check(guard>0,"queue terminates");}
    }
    public static void main(String[] args){
        for(int n:new int[]{1,2,5}) {
            Fake f=new Fake();VoiceForwardQueue q=new VoiceForwardQueue(ids(n),f);q.start();f.done();
            check(q.isFinished()&&f.state.sent==n&&f.state.remaining==0,"all acknowledged for "+n);
            check(f.randomCalls==0&&f.delays.isEmpty(),"no random delay at or below five");
            check(f.sent.equals(ids(n)),"selection order retained");
        }
        Fake f=new Fake();VoiceForwardQueue q=new VoiceForwardQueue(ids(6),f);q.start();q.start();f.done();
        check(f.sent.size()==6&&f.state.sent==6,"six sends, start is idempotent");
        check(f.times.get(0)==0&&f.delays.size()==5,"first immediate, five delayed gaps");
        for(int d:f.delays)check(d>=1&&d<=5,"inclusive 1..5 second delay");
        for(int i=1;i<f.times.size();i++)check(f.times.get(i)-f.times.get(i-1)>=1000,"no burst for large batch");
        for(VoiceForwardQueue.State s:f.states)check(s.sent+s.failed+s.unknown+s.remaining==s.total,"progress counters balance");
        f=new Fake();List<String> duplicate=new ArrayList<>(ids(5));duplicate.add("friend0");q=new VoiceForwardQueue(duplicate,f);q.start();f.done();
        check(f.sent.size()==5&&f.delays.isEmpty(),"unique recipients determine threshold");
        f=new Fake();q=new VoiceForwardQueue(ids(6),f);q.start();q.stop();f.done();check(f.sent.isEmpty()&&f.state.remaining==6,"cancel before first send");
        f=new Fake();q=new VoiceForwardQueue(ids(6),f);q.start();f.next();q.stop();f.done();
        check(f.sent.size()==1&&f.state.sent==1&&f.state.remaining==5,"stop lets current native task finish, no next task");
        f=new Fake();q=new VoiceForwardQueue(ids(6),f);q.start();while(f.delays.isEmpty())f.next();q.stop();f.done();
        check(f.sent.size()==1&&f.state.remaining==5,"stop invalidates a scheduled delayed send");
        f=new Fake();f.failures.add("group1@chatroom");q=new VoiceForwardQueue(ids(6),f);q.start();f.done();
        check(f.sent.size()==6&&f.state.sent==5&&f.state.failed==1&&f.state.remaining==0,"one failure does not retry/duplicate or block following recipients");
        check(f.delays.size()==5,"failed recipient also keeps delay before next");
        f=new Fake();f.submitThrows=true;q=new VoiceForwardQueue(ids(2),f);q.start();f.done();check(f.state.failed==2&&f.state.sent==0,"submit failures counted");
        f=new Fake();f.pendingForever=true;q=new VoiceForwardQueue(ids(6),f);q.start();f.done();
        check(f.sent.size()==1&&f.state.unknown==1&&f.state.remaining==5&&f.state.stopping,"timeout stops batch without retrying uncertain recipient");
        f=new Fake();f.ticketThrows=true;q=new VoiceForwardQueue(ids(6),f);q.start();f.done();
        check(f.sent.size()==1&&f.state.unknown==1&&f.state.stopping,"unknown native result stops further sends");
        f=new Fake();f.allowed=false;q=new VoiceForwardQueue(ids(3),f);q.start();f.done();check(f.sent.isEmpty(),"inactive module/account never sends");
        f=new Fake();q=new VoiceForwardQueue(ids(6),f);q.start();f.next();f.allowed=false;f.done();check(f.sent.size()==1&&f.state.remaining==5,"account switch stops next recipient");
        f=new Fake();f.observerThrows=true;q=new VoiceForwardQueue(ids(1),f);q.start();f.done();check(f.state.sent==1,"disposed UI cannot crash or duplicate send");
        boolean rejected=false;try{new VoiceForwardQueue(Collections.<String>emptyList(),new Fake());}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"empty selection rejected");
        System.out.println("PASS "+checks+" batch timing/progress/cancellation checks");
    }
}
