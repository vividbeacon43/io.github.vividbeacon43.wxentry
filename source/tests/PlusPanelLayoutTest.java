package com.zafiro.wxentry.kernel;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class PlusPanelLayoutTest {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static ViewGroup.LayoutParams lp(){return new ViewGroup.LayoutParams(-1,-2);}
    static boolean mount(ViewGroup host,View grid){return PlusPanelLayout.mount(host,grid,new View(),new View(),lp(),lp(),lp());}
    static final class FailingHost extends ViewGroup {
        boolean fail;
        public void addView(View child,int index,LayoutParams params){
            if(fail&&child instanceof LinearLayout){fail=false;throw new IllegalStateException("synthetic attach failure");}
            super.addView(child,index,params);
        }
    }
    public static void main(String[] args){
        ViewGroup host=new ViewGroup(),grid=new ViewGroup();View before=new View(),after=new View(),native1=new View(),native2=new View();
        grid.addView(native1,lp());grid.addView(native2,lp());
        ViewGroup.LayoutParams original=new ViewGroup.LayoutParams(320,240);
        host.addView(before,lp());host.addView(grid,original);host.addView(after,lp());
        ok(mount(host,grid),"initial mount");ViewGroup box=(ViewGroup)host.getChildAt(1);
        ok(host.getChildCount()==3&&host.getChildAt(0)==before&&host.getChildAt(2)==after,"native sibling order");
        ok(grid.getParent()==box&&box.getChildAt(2)==grid,"native grid retained");
        ok(grid.getChildAt(0)==native1&&grid.getChildAt(1)==native2,"native actions retained");
        ok(PlusPanelLayout.present(host),"live row present");
        ok(!mount(host,grid)&&box.getChildCount()==3,"repeat mount no duplicate");
        View entry=PlusPanelLayout.entry(host,0);entry.setVisibility(View.GONE);PlusPanelLayout.reveal(host);
        ok(entry.getVisibility()==View.VISIBLE,"reused row made visible");
        box.removeView(entry);ok(!PlusPanelLayout.present(host),"wrapper alone is not entry");
        ok(mount(box,grid),"missing entry repaired within existing wrapper");
        ok(grid.getParent()==box&&host.getChildAt(1)==box,"repair does not nest wrappers");
        box.removeView(grid);host.removeView(box);host.addView(grid,1,original);
        ok(!PlusPanelLayout.present(host),"cleared popup has no entry");
        ok(mount(host,grid),"same native popup recreated and repaired");
        ok(grid.getChildCount()==2&&grid.getChildAt(1)==native2,"recreation keeps native actions");
        FailingHost failureHost=new FailingHost();View g=new View();ViewGroup.LayoutParams sized=new ViewGroup.LayoutParams(300,280);
        failureHost.addView(new View(),lp());failureHost.addView(g,sized);failureHost.fail=true;
        ok(!mount(failureHost,g),"attach failure reported");
        ok(g.getParent()==failureHost&&failureHost.indexOfChild(g)==1,"native grid restored after failure");
        ok(g.getLayoutParams()==sized&&sized.height==280,"native dimensions restored after failure");
        ok(!PlusPanelLayout.present(failureHost),"failed injection is retryable");
        ok(mount(failureHost,g),"retry after attach failure succeeds");
        ViewGroup unrelated=new ViewGroup();ok(!mount(unrelated,g),"wrong parent ignored");
        ok(g.getParent()!=unrelated,"wrong parent does not move grid");
        System.out.println("PASS "+checks+" plus popup presence, reuse, and transactional recovery checks");
    }
}
