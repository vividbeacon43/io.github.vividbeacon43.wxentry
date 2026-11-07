package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL,STROKE}
 public int color,shadowColor;public float strokeWidth,shadowRadius;public Style style;
 public Paint(int flags){}public void setStyle(Style s){style=s;}public void setColor(int c){color=c;}public void setStrokeWidth(float w){strokeWidth=w;}
 public void setShadowLayer(float r,float dx,float dy,int c){shadowRadius=r;shadowColor=c;}public void clearShadowLayer(){shadowRadius=0;}
}
