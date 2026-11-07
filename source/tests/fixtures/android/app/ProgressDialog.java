package android.app;
public class ProgressDialog implements android.content.DialogInterface {
    public static ProgressDialog last;public boolean shown;android.content.DialogInterface.OnCancelListener cancel;
    public ProgressDialog(Activity a) { last=this; }
    public void setMessage(String text) {}
    public void setCancelable(boolean value) {}
    public void setOnCancelListener(android.content.DialogInterface.OnCancelListener listener) { cancel=listener; }
    public void show() { shown=true; }
    public void dismiss() { shown=false; }
    public boolean isShowing() { return shown; }
    public void cancel() { shown=false;if(cancel!=null)cancel.onCancel(this); }
}
