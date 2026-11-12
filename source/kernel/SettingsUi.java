package com.zafiro.wxentry.kernel;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Shared native switch rows. Explicit drawables avoid host-app theme overrides. */
final class SettingsUi {
    static final int BLUE=0xFF2979FF,ROSE=0xFFA82D61,GRAY=0xFFE4D3DB;
    static final int SWITCH_WIDTH=36,SWITCH_HEIGHT=20,THUMB_SIZE=16;
    static final int CONTROL_SIZE=48,CONTROL_GAP=12,ROW_HEIGHT=64;
    interface Change { boolean set(boolean checked); } // Return the saved state, including on failure.
    static int dp(Context c,int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static boolean dark(Context c) { return (c.getResources().getConfiguration().uiMode&0x30)==0x20; }
    static int textColor(Context c) { return dark(c)?0xFFEEEEEE:0xFF222222; }
    static LinearLayout column(Context c) {
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));return box;
    }
    static TextView text(Context c,String value,int size) {
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(textColor(c));return t;
    }
    static void heading(LinearLayout box,String value) {
        TextView t=text(box.getContext(),value,14);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(0,dp(box.getContext(),16),0,dp(box.getContext(),4));box.addView(t);
    }
    static GradientDrawable shape(Context c,int color,int w,int h) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,h)/2f);d.setSize(dp(c,w),dp(c,h));return d;
    }
    static final class CompactSwitch extends Switch {
        final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),edge=new Paint(Paint.ANTI_ALIAS_FLAG);
        private MenuPalette menuPalette;
        CompactSwitch(Context c) { super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        void setMenuPalette(MenuPalette palette) {
            menuPalette = palette;
            invalidate();
        }
        protected void onDraw(Canvas canvas) {
            float d=getResources().getDisplayMetrics().density,w=SWITCH_WIDTH*d,h=SWITCH_HEIGHT*d;
            float x=(getWidth()-w)/2f,y=(getHeight()-h)/2f;
            // Preserve native drag/animation geometry, while suppressing theme-controlled painting.
            int layer=canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),0);
            super.onDraw(canvas);canvas.restoreToCount(layer);
            float position=Math.max(0,Math.min(1,(getThumbDrawable().getBounds().centerX()-(x+9*d))/(18*d)));
            boolean checked=isChecked(),enabled=isEnabled();
            MenuPalette palette = menuPalette != null ? menuPalette : dark(getContext()) ? MenuPalette.DARK : MenuPalette.LIGHT;
            boolean night = palette == MenuPalette.DARK;
            int track = checked ? palette.switchOn : palette.switchOff;
            int border = checked ? palette.switchOnEdge : palette.switchOffEdge;
            if(!enabled) { track=night?0xFF505B6B:0xFFDDE2E8;border=night?0xFF7C899B:0xFFAAB3C0; }
            fill.setStyle(Paint.Style.FILL);fill.setColor(track);
            fill.setShadowLayer(0.65f*d,0,0.5f*d,0x24000000);
            canvas.drawRoundRect(x,y,x+w,y+h,h/2,h/2,fill);fill.clearShadowLayer();
            edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(0.8f*d);edge.setColor(border);
            canvas.drawRoundRect(x+0.4f*d,y+0.4f*d,x+w-0.4f*d,y+h-0.4f*d,h/2,h/2,edge);
            float radius=THUMB_SIZE*d/2,cx=x+h/2+(w-h)*Math.max(0,Math.min(1,position)),cy=y+h/2;
            fill.setColor(enabled?0xFFFFFFFF:0xFFF6F7F9);fill.setShadowLayer(1.5f*d,0,0.8f*d,0x40000000);
            canvas.drawCircle(cx,cy,radius,fill);fill.clearShadowLayer();
            edge.setColor(night?0xFFC2CAD5:0xFFB7C0CD);edge.setStrokeWidth(0.65f*d);
            canvas.drawCircle(cx,cy,radius-0.35f*d,edge);
        }
    }
    static CompactSwitch control(Context c) {
        CompactSwitch s=new CompactSwitch(c);s.setText("");s.setShowText(false);s.setSplitTrack(false);
        s.setButtonDrawable((android.graphics.drawable.Drawable)null);s.setBackground(null);
        s.setThumbTintList(null);s.setTrackTintList(null);
        StateListDrawable track=new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_checked},shape(c,ROSE,SWITCH_WIDTH,SWITCH_HEIGHT));
        track.addState(new int[]{android.R.attr.state_enabled},shape(c,GRAY,SWITCH_WIDTH,SWITCH_HEIGHT));
        track.addState(new int[]{},shape(c,GRAY,SWITCH_WIDTH,SWITCH_HEIGHT));
        s.setTrackDrawable(track);s.setThumbDrawable(shape(c,0xFFFFFFFF,18,THUMB_SIZE));
        s.setSwitchMinWidth(dp(c,SWITCH_WIDTH));s.setSwitchPadding(0);s.setThumbTextPadding(0);
        int inset=dp(c,(CONTROL_SIZE-SWITCH_WIDTH)/2);
        s.setPadding(inset,0,inset,0);s.setMinimumWidth(0);s.setMinWidth(0);s.setMinHeight(0);
        s.setGravity(Gravity.CENTER);s.setFocusable(false);return s;
    }
    static void bind(final Switch control,boolean checked,final Change change) {
        control.setOnCheckedChangeListener(null);control.setChecked(checked);
        control.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            boolean changing;
            public void onCheckedChanged(CompoundButton button,boolean value) {
                if(changing)return;changing=true;
                try { button.setChecked(change.set(value)); }
                catch(Throwable failure) { button.setChecked(!value); }
                finally { changing=false; }
            }
        });
    }
    static LinearLayout.LayoutParams controlSlot(Context c) {
        int size=dp(c,CONTROL_SIZE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(size,size);
        params.leftMargin=dp(c,CONTROL_GAP);
        return params;
    }
    static final class ToggleRow extends LinearLayout {
        final CompactSwitch control;final TextView title,subtitle;
        ToggleRow(Context c,String label,String detail,boolean checked,Change change) {
            super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(c,ROW_HEIGHT));
            setPadding(0,dp(c,8),0,dp(c,8));
            LinearLayout labels=new LinearLayout(c);labels.setOrientation(VERTICAL);
            title=text(c,label,16);subtitle=text(c,detail==null?"":detail,12);
            subtitle.setTextColor(dark(c)?0xFFB7BEC8:0xFF687180);
            labels.addView(title);if(detail!=null&&!detail.isEmpty())labels.addView(subtitle);
            addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            control=SettingsUi.control(c);control.setContentDescription(label);
            addView(control,controlSlot(c));bind(control,checked,change);
            setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if(control.isEnabled())control.performClick(); } });
        }
    }
    static ToggleRow toggle(Context c,String label,String detail,boolean checked,Change change) { return new ToggleRow(c,label,detail,checked,change); }
}
