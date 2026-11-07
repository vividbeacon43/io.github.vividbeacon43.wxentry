package android.app;
public class Activity {
    public boolean finishing;public int launches;public android.content.Intent launched;
    public android.content.Intent intent;
    public boolean isFinishing() { return finishing; }
    public void startActivity(android.content.Intent intent) { launched=intent;launches++; }
    public android.content.Intent getIntent() { return intent; }
    public void finish() { finishing=true; }
}
