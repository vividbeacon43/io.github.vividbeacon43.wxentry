package android.os;
public class Handler {
    static long serial;
    static class Task implements Comparable<Task> {
        long at,order;Runnable run;Task(long when,Runnable r) { at=when;order=serial++;run=r; }
        public int compareTo(Task t) { int n=Long.compare(at,t.at);return n==0?Long.compare(order,t.order):n; }
    }
    static java.util.PriorityQueue<Task> tasks=new java.util.PriorityQueue<>();
    public Handler(Looper loop) {}
    public boolean post(Runnable r) { return postDelayed(r,0); }
    public boolean postDelayed(Runnable r,long ms) { tasks.add(new Task(SystemClock.now+ms,r));return true; }
    public static void advance(long ms) {
        long end=SystemClock.now+ms;int guard=10000;
        while(!tasks.isEmpty() && tasks.peek().at<=end && guard-->0) { Task task=tasks.remove();SystemClock.now=task.at;task.run.run(); }
        if(guard<=0)throw new AssertionError("Unbounded handler callbacks");SystemClock.now=end;
    }
}
