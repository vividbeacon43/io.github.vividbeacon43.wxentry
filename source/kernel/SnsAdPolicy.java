package com.zafiro.wxentry.kernel;
import java.util.*;

/** Presentation filtering only; the input and native storage are never mutated. */
final class SnsAdPolicy {
    interface Marker<T>{boolean isAd(T item)throws Throwable;}
    static <T> List<T> filter(List<T> source,boolean enabled,Marker<T> marker)throws Throwable{
        if(!enabled||source==null||source.isEmpty())return source;
        ArrayList<T> kept=null;
        for(int i=0;i<source.size();i++){
            T item=source.get(i);boolean ad=item!=null&&marker.isAd(item);
            if(ad){if(kept==null){kept=new ArrayList<T>(source.size());kept.addAll(source.subList(0,i));}}
            else if(kept!=null)kept.add(item);
        }
        return kept==null?source:kept;
    }
}
