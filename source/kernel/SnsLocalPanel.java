package com.zafiro.wxentry.kernel;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native-style local view children, tracked separately from all genuine interaction views. */
final class SnsLocalPanel {
    static final int BLUE=0xFF576B95;
    static int dp(Context c,int v){return SettingsUi.dp(c,v);}
    static View line(Context c){View view=new View(c);view.setBackgroundColor(SettingsUi.dark(c)?0xFF444444:0xFFDEDEDE);view.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.max(1,dp(c,1))));return view;}
    static boolean vertical(ViewGroup view){return view instanceof LinearLayout&&((LinearLayout)view).getOrientation()==LinearLayout.VERTICAL;}
    static void mount(ViewGroup host,View nativeRoot,View nativeLike,View nativeComments,boolean realLikes,boolean realComments,SnsLocalRuntime.Bound bound,List<SnsLocalData.Person> likes,List<SnsLocalData.Comment> comments)throws Throwable{
        Context context=host.getContext();
        if(nativeRoot!=null&&nativeLike!=null&&nativeComments!=null&&nativeLike.getParent() instanceof ViewGroup&&nativeComments.getParent() instanceof ViewGroup&&vertical((ViewGroup)nativeLike.getParent())&&vertical((ViewGroup)nativeComments.getParent())){
            if(!likes.isEmpty()){ViewGroup parent=(ViewGroup)nativeLike.getParent();LinearLayout view=likes(context,bound,likes,!realLikes,realComments&&!realLikes);parent.addView(view,parent.indexOfChild(nativeLike)+1,new LinearLayout.LayoutParams(-1,-2));bound.added(view);}
            if(!comments.isEmpty()){ViewGroup parent=(ViewGroup)nativeComments.getParent();LinearLayout view=comments(context,bound,comments,(realLikes||!likes.isEmpty())&&!realComments);parent.addView(view,parent.indexOfChild(nativeComments)+1,new LinearLayout.LayoutParams(-1,-2));bound.added(view);}
            return;
        }
        LinearLayout wrapper=new LinearLayout(context);wrapper.setOrientation(LinearLayout.VERTICAL);GradientDrawable background=new GradientDrawable();background.setColor(SettingsUi.dark(context)?0xFF292929:0xFFF7F7F7);background.setCornerRadius(dp(context,3));wrapper.setBackground(background);
        if(!likes.isEmpty())wrapper.addView(likes(context,bound,likes,!realLikes,!comments.isEmpty()));if(!comments.isEmpty())wrapper.addView(comments(context,bound,comments,false));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(context,3);params.bottomMargin=dp(context,5);host.addView(wrapper,params);bound.added(wrapper);
    }
    static ImageView avatar(final Context c,final SnsLocalRuntime.Bound bound,final SnsLocalData.Person person,final SnsLocalData.Feature feature,int size){
        ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(person.name+"的头像");image.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));
        try{SnsLocalNative.avatar(image,person.id);}catch(Throwable unavailable){GradientDrawable d=new GradientDrawable();d.setColor(0xFFBFC7D1);d.setCornerRadius(dp(c,3));image.setBackground(d);}
        image.setOnClickListener(new View.OnClickListener(){public void onClick(View view){open(view,bound,person,feature);}});return image;
    }
    static void open(View view,SnsLocalRuntime.Bound bound,SnsLocalData.Person person,SnsLocalData.Feature feature){if(SnsLocalRuntime.valid(bound,feature))SnsLocalNative.profile(SnsLocalNative.activity(view.getContext()),bound.owner,person.id);}
    static LinearLayout likes(Context c,final SnsLocalRuntime.Bound bound,List<SnsLocalData.Person> values,boolean heart,boolean divider){
        LinearLayout outer=new LinearLayout(c);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(c,8),dp(c,5),dp(c,8),dp(c,5));
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);if(heart){TextView icon=SettingsUi.text(c,"♡",17);icon.setTextColor(BLUE);icon.setPadding(0,dp(c,4),dp(c,6),0);row.addView(icon);}
        Flow flow=new Flow(c);int count=bound.likesExpanded?values.size():Math.min(10,values.size());
        for(int i=0;i<count;i++){final SnsLocalData.Person person=values.get(i);LinearLayout chip=new LinearLayout(c);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setPadding(0,0,dp(c,7),dp(c,5));chip.addView(avatar(c,bound,person,SnsLocalData.Feature.LIKES,28));TextView name=SettingsUi.text(c,person.name,14);name.setTextColor(BLUE);name.setSingleLine(true);name.setMaxEms(6);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(c,4),0,0,0);chip.addView(name);chip.setOnClickListener(new View.OnClickListener(){public void onClick(View v){open(v,bound,person,SnsLocalData.Feature.LIKES);}});flow.addView(chip,new ViewGroup.LayoutParams(-2,-2));}
        row.addView(flow,new LinearLayout.LayoutParams(0,-2,1));outer.addView(row);
        if(count<values.size()){TextView more=SettingsUi.text(c,"展开全部（"+values.size()+"）",12);more.setTextColor(BLUE);more.setOnClickListener(new View.OnClickListener(){public void onClick(View view){SnsLocalRuntime.expand(bound,true);}});outer.addView(more);}
        if(divider)outer.addView(line(c));return outer;
    }
    static LinearLayout comments(Context c,final SnsLocalRuntime.Bound bound,List<SnsLocalData.Comment> values,boolean divider){
        LinearLayout outer=new LinearLayout(c);outer.setOrientation(LinearLayout.VERTICAL);outer.setPadding(dp(c,8),dp(c,3),dp(c,8),dp(c,4));if(divider)outer.addView(line(c));int count=bound.commentsExpanded?values.size():Math.min(5,values.size());
        for(int i=0;i<count;i++){final SnsLocalData.Comment comment=values.get(i);LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.TOP);row.setPadding(0,dp(c,4),0,dp(c,4));row.addView(avatar(c,bound,comment.person,SnsLocalData.Feature.COMMENTS,24));
            final CommentText text=new CommentText(c);text.setTextSize(14);text.setTextColor(SettingsUi.textColor(c));text.setPadding(dp(c,6),0,0,0);text.setHighlightColor(0x22607090);SpannableStringBuilder content=new SpannableStringBuilder(comment.person.name+": "+comment.text);
            content.setSpan(new ClickableSpan(){public void onClick(View view){open(view,bound,comment.person,SnsLocalData.Feature.COMMENTS);}public void updateDrawState(TextPaint paint){paint.setColor(BLUE);paint.setUnderlineText(false);}},0,comment.person.name.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setText(content);text.setMovementMethod(LinkMovementMethod.getInstance());
            View.OnLongClickListener edit=new View.OnLongClickListener(){public boolean onLongClick(View view){text.longPress=true;if(SnsLocalRuntime.valid(bound,SnsLocalData.Feature.COMMENTS)){Activity act=SnsLocalNative.activity(view.getContext());if(act!=null)SnsLocalPicker.edit(act,new SnsLocalNative.Target(bound.owner,bound.key),comment);}return true;}};row.setOnLongClickListener(edit);text.setOnLongClickListener(edit);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));outer.addView(row);
        }
        if(count<values.size()){TextView more=SettingsUi.text(c,"展开其余 "+(values.size()-count)+" 条评论",12);more.setTextColor(BLUE);more.setOnClickListener(new View.OnClickListener(){public void onClick(View view){SnsLocalRuntime.expand(bound,false);}});outer.addView(more);}return outer;
    }
    // LinkMovementMethod otherwise dispatches a profile click on UP after a consumed long press.
    static final class CommentText extends TextView {
        boolean longPress;
        CommentText(Context context){super(context);}
        public boolean onTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)longPress=false;
            if(longPress&&(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)){setPressed(false);cancelLongPress();if(getText() instanceof Spannable)Selection.removeSelection((Spannable)getText());return true;}
            return super.onTouchEvent(event);
        }
    }
    static final class Flow extends ViewGroup {
        Flow(Context c){super(c);}
        protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);if(width==0)width=getResources().getDisplayMetrics().widthPixels;int x=0,y=0,row=0;
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);measureChild(child,MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),heightSpec);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();if(x>0&&x+w>width){y+=row;x=0;row=0;}x+=w;row=Math.max(row,h);}setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(y+row,heightSpec));}
        protected void onLayout(boolean changed,int left,int top,int right,int bottom){int width=right-left,x=0,y=0,row=0;for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();if(x>0&&x+w>width){y+=row;x=0;row=0;}child.layout(x,y,x+w,y+h);x+=w;row=Math.max(row,h);}}
        protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(-2,-2);}
    }
}
