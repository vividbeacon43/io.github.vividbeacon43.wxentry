package android.graphics;
public class Canvas {
 public static class Draw {public String shape;public float x,y,w,h,r,shadow,stroke;public int color;public Paint.Style style;Draw(String name,float x,float y,float w,float h,float radius,Paint p){shape=name;this.x=x;this.y=y;this.w=w;this.h=h;r=radius;shadow=p.shadowRadius;stroke=p.strokeWidth;color=p.color;style=p.style;}}
 public java.util.List<Draw> draws=new java.util.ArrayList<>();public int nativeLayerAlpha=-1;
 public int saveLayerAlpha(float l,float t,float r,float b,int alpha){nativeLayerAlpha=alpha;return 1;}public void restoreToCount(int count){}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){draws.add(new Draw("track",l,t,r-l,b-t,rx,p));}
 public void drawCircle(float cx,float cy,float radius,Paint p){draws.add(new Draw("thumb",cx,cy,0,0,radius,p));}
}
