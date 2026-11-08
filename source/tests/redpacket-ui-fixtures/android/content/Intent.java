package android.content;
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public String packageName, className;
    public int flags;
    public final java.util.Map<String,Object> extras = new java.util.HashMap<String,Object>();
    public Intent setClassName(String pkg, String cls) { packageName=pkg; className=cls; return this; }
    public Intent putExtra(String name, String value) { extras.put(name,value); return this; }
    public Intent putExtra(String name, long value) { extras.put(name,value); return this; }
    public Intent addFlags(int value) { flags |= value; return this; }
}
