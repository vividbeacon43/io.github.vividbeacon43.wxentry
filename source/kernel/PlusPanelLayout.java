package com.zafiro.wxentry.kernel;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Presence comes from the live tree, never from a past popup instance. */
final class PlusPanelLayout {
    static final String BOX_TAG="wxentry.panel",ROW_TAG="wxentry.panel.row";
    static View entry(View root,int depth) {
        if(root==null||depth>10)return null;
        if(ROW_TAG.equals(root.getTag()))return root;
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){View found=entry(group.getChildAt(i),depth+1);if(found!=null)return found;}
        }
        return null;
    }
    static boolean present(View root) { return entry(root,0)!=null; }
    static void reveal(View root) { View row=entry(root,0);if(row!=null)row.setVisibility(View.VISIBLE); }
    static boolean mount(ViewGroup host,View grid,View row,View divider,ViewGroup.LayoutParams rowParams,ViewGroup.LayoutParams lineParams,ViewGroup.LayoutParams gridParams) {
        if(present(host)||grid.getParent()!=host)return false;
        row.setTag(ROW_TAG);
        if(BOX_TAG.equals(host.getTag())) {
            try { host.addView(row,0,rowParams);host.addView(divider,1,lineParams);return true; }
            catch(Throwable failure){if(row.getParent()==host)host.removeView(row);if(divider.getParent()==host)host.removeView(divider);return false;}
        }
        int index=host.indexOfChild(grid);ViewGroup.LayoutParams original=grid.getLayoutParams();int height=original==null?-2:original.height;
        LinearLayout wrapper=new LinearLayout(host.getContext());wrapper.setOrientation(LinearLayout.VERTICAL);wrapper.setTag(BOX_TAG);
        try {
            wrapper.addView(row,rowParams);wrapper.addView(divider,lineParams);
            host.removeView(grid);wrapper.addView(grid,gridParams);
            if(original!=null){original.height=-2;host.addView(wrapper,index,original);}else host.addView(wrapper,index,new ViewGroup.LayoutParams(-1,-2));
            return true;
        }catch(Throwable failure) {
            if(wrapper.getParent()==host)host.removeView(wrapper);
            if(grid.getParent()==wrapper)wrapper.removeView(grid);
            if(grid.getParent()==null){if(original!=null)original.height=height;host.addView(grid,Math.max(0,Math.min(index,host.getChildCount())),original==null?new ViewGroup.LayoutParams(-1,height):original);}
            return false;
        }
    }
}
