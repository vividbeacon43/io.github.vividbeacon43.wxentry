package android.os;
public class Handler {
    public static java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();
    public Handler(Looper l) {}
    public boolean postDelayed(Runnable r,long delay) { queue.add(r);return true; }
    public static void next() { if(!queue.isEmpty())queue.remove().run(); }
}
