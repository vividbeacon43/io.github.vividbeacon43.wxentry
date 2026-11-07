package android.text;public class SpannableStringBuilder implements Spanned {
 static class Mark{Object value;int start,end;Mark(Object v,int s,int e){value=v;start=s;end=e;}}
 final StringBuilder text;final java.util.List<Mark> marks=new java.util.ArrayList<>();
 public SpannableStringBuilder(CharSequence v){text=new StringBuilder(v.toString());if(v instanceof Spanned){Spanned s=(Spanned)v;for(Object m:s.getSpans(0,v.length(),Object.class))setSpan(m,s.getSpanStart(m),s.getSpanEnd(m),33);}}
 public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}public CharSequence subSequence(int s,int e){return text.substring(s,e);}public String toString(){return text.toString();}
 public SpannableStringBuilder append(CharSequence v){text.append(v);return this;}public void setSpan(Object v,int s,int e,int flags){marks.add(new Mark(v,s,e));}
 public <T>T[] getSpans(int s,int e,Class<T> cls){java.util.List<T> out=new java.util.ArrayList<>();for(Mark m:marks)if(cls.isInstance(m.value)&&m.start<e&&m.end>s)out.add(cls.cast(m.value));return out.toArray((T[])java.lang.reflect.Array.newInstance(cls,out.size()));}
 public int getSpanStart(Object v){for(Mark m:marks)if(m.value==v)return m.start;return -1;}public int getSpanEnd(Object v){for(Mark m:marks)if(m.value==v)return m.end;return -1;}
 public SpannableStringBuilder delete(int s,int e){text.delete(s,e);java.util.Iterator<Mark> it=marks.iterator();while(it.hasNext()){Mark m=it.next();if(m.start>=s&&m.end<=e){it.remove();continue;}m.start=m.start>=e?m.start-e+s:Math.min(m.start,s);m.end=m.end>=e?m.end-e+s:Math.min(m.end,s);}return this;}
}
