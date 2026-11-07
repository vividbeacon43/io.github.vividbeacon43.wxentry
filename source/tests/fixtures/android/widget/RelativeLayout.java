package android.widget;
public class RelativeLayout extends android.view.ViewGroup {
    public static final int LEFT_OF=0,BELOW=3,ALIGN_BOTTOM=8;
    public static class LayoutParams extends android.view.ViewGroup.LayoutParams {
        final int[] rules=new int[22];public int topMargin,bottomMargin,leftMargin,rightMargin;
        public LayoutParams(int width,int height){super(width,height);}
        public LayoutParams(LayoutParams source){super(source.width,source.height);System.arraycopy(source.rules,0,rules,0,rules.length);
            topMargin=source.topMargin;bottomMargin=source.bottomMargin;leftMargin=source.leftMargin;rightMargin=source.rightMargin;}
        public int[] getRules(){return rules;}public void addRule(int key,int value){rules[key]=value;}
        public void removeRule(int key){rules[key]=0;}
    }
}
