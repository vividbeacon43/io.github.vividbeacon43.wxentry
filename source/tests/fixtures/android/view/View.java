package android.view;
public class View {
    public static final int VISIBLE=0,GONE=8,NO_ID=-1;
    public int id=NO_ID,visibility=VISIBLE;public ViewGroup parent;public ViewGroup.LayoutParams params;
    public final android.content.res.Resources resources=new android.content.res.Resources();
    public android.content.Context getContext(){return new android.content.Context();}
    public int getId(){return id;}public int getVisibility(){return visibility;}
    public ViewGroup getParent(){return parent;}
    public ViewGroup.LayoutParams getLayoutParams(){return params;}
    public void setLayoutParams(ViewGroup.LayoutParams value){params=value;}
    public android.content.res.Resources getResources(){return resources;}
}
