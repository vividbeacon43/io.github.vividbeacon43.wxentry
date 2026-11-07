package android.app;
public class Activity {
    public boolean closing,destroyed,visible=true,button=true;
    public android.content.Intent intent=new android.content.Intent();
    public boolean isFinishing() { return closing; }
    public boolean isDestroyed() { return destroyed; }
    public android.content.Intent getIntent() { return intent; }
}
