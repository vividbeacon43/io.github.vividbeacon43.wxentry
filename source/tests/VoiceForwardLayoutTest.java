package com.zafiro.wxentry.kernel;
import android.view.*;
import android.widget.*;
public final class VoiceForwardLayoutTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static RelativeLayout.LayoutParams params(View view){return (RelativeLayout.LayoutParams)view.getLayoutParams();}
    public static void main(String[] args) {
        RelativeLayout parent=new RelativeLayout();TextView marker=new TextView();View bubble=new View();
        marker.parent=bubble.parent=parent;bubble.id=100;
        RelativeLayout.LayoutParams original=new RelativeLayout.LayoutParams(-2,-2);
        original.addRule(RelativeLayout.BELOW,200);original.addRule(RelativeLayout.ALIGN_BOTTOM,200);
        original.addRule(RelativeLayout.LEFT_OF,300);original.topMargin=0;original.bottomMargin=19;
        marker.params=original;
        check(VoiceForwardLayout.apply(marker,bubble),"visible native marker patched");
        check(params(marker).getRules()[RelativeLayout.BELOW]==100,"marker follows bubble");
        check(params(marker).getRules()[RelativeLayout.ALIGN_BOTTOM]==0,"conflicting bottom constraint removed");
        check(params(marker).topMargin==11,"4dp gap uses density");
        check(params(marker).getRules()[RelativeLayout.LEFT_OF]==300 && params(marker).bottomMargin==19,"horizontal placement and other margins retained");
        check(original.topMargin==0 && original.getRules()[RelativeLayout.BELOW]==200,"original params not mutated");
        VoiceForwardLayout.apply(marker,bubble);check(params(marker).topMargin==11,"repeat does not accumulate spacing");
        VoiceForwardLayout.restore(marker);
        check(params(marker).getRules()[RelativeLayout.BELOW]==200 && params(marker).getRules()[RelativeLayout.ALIGN_BOTTOM]==200 && params(marker).topMargin==0,"recycled row restored");
        marker.visibility=View.GONE;check(!VoiceForwardLayout.apply(marker,bubble),"ordinary voice without marker untouched");
        marker.visibility=View.VISIBLE;marker.text="";check(!VoiceForwardLayout.apply(marker,bubble),"empty marker untouched");
        marker.text="Forwarded a voice message";check(VoiceForwardLayout.apply(marker,bubble),"localized marker supported");
        params(marker).addRule(RelativeLayout.BELOW,999);params(marker).addRule(RelativeLayout.ALIGN_BOTTOM,888);params(marker).topMargin=77;
        VoiceForwardLayout.restore(marker);
        check(params(marker).getRules()[RelativeLayout.BELOW]==999 && params(marker).getRules()[RelativeLayout.ALIGN_BOTTOM]==888 && params(marker).topMargin==77,"later native layout changes retained");
        marker.parent=new RelativeLayout();check(!VoiceForwardLayout.apply(marker,bubble),"different parents safely skipped");
        marker.parent=parent;bubble.id=View.NO_ID;check(!VoiceForwardLayout.apply(marker,bubble),"invalid anchor safely skipped");
        bubble.id=100;bubble.visibility=View.GONE;check(!VoiceForwardLayout.apply(marker,bubble),"hidden bubble skipped");
        check(!VoiceForwardLayout.apply(null,bubble),"missing marker skipped");
        System.out.println("PASS "+checks+" voice marker layout/recycling checks");
    }
}
