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
            if(!likes.isEmpty()){ViewGroup parent=(ViewGroup)nativeLike.getParent();View view=likes(context,bound,likes,realLikes?SnsLocalNative.likesText(nativeLike):null);SnsLocalNative.likeStyle(view,nativeLike);parent.addView(view,parent.indexOfChild(nativeLike)+1,new LinearLayout.LayoutParams(-1,-2));bound.added(view);if(realLikes)bound.hideLike(nativeLike);else if(realComments)separator(parent,parent.indexOfChild(view)+1,bound);}
            if(!comments.isEmpty()){ViewGroup parent=(ViewGroup)nativeComments.getParent();LinearLayout view=comments(context,bound,comments,false);int at=parent.indexOfChild(nativeComments)+1;if((realLikes||!likes.isEmpty())&&!realComments){separator(parent,at,bound);at++;}parent.addView(view,at,new LinearLayout.LayoutParams(-1,-2));bound.added(view);}
            return;
        }
        View wrapper=SnsLocalNative.interaction(context),slot=(View)de.robv.android.xposed.XposedHelpers.callMethod(wrapper,"getLikeLayout");
        ViewGroup parent=(ViewGroup)slot.getParent();if(!likes.isEmpty()){int at=parent.indexOfChild(slot);ViewGroup.LayoutParams params=slot.getLayoutParams();parent.removeView(slot);View local=likes(context,bound,likes,null);SnsLocalNative.likeStyle(local,slot);parent.addView(local,at,params);}else slot.setVisibility(View.GONE);
        ViewGroup commentSlot=(ViewGroup)de.robv.android.xposed.XposedHelpers.callMethod(wrapper,"getCommentLayout");commentSlot.setVisibility(comments.isEmpty()?View.GONE:View.VISIBLE);if(!comments.isEmpty())commentSlot.addView(comments(context,bound,comments,false));
        View divider=(View)de.robv.android.xposed.XposedHelpers.callMethod(wrapper,"getDivider");divider.setVisibility(!likes.isEmpty()&&!comments.isEmpty()?View.VISIBLE:View.GONE);
        host.addView(wrapper,SnsLocalNative.interactionParams());bound.added(wrapper);
    }
    static void separator(ViewGroup parent,int at,SnsLocalRuntime.Bound bound)throws Throwable{
        View fresh=SnsLocalNative.interaction(parent.getContext());View line=(View)de.robv.android.xposed.XposedHelpers.callMethod(fresh,"getDivider");ViewGroup.LayoutParams params=line.getLayoutParams();((ViewGroup)line.getParent()).removeView(line);line.setVisibility(View.VISIBLE);parent.addView(line,at,params);bound.added(line);
    }
    static ImageView avatar(final Context c,final SnsLocalRuntime.Bound bound,final SnsLocalData.Person person,final SnsLocalData.Feature feature,int size){
        ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(person.name+"的头像");image.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));
        try{SnsLocalNative.avatar(image,person.id);}catch(Throwable unavailable){GradientDrawable d=new GradientDrawable();d.setColor(0xFFBFC7D1);d.setCornerRadius(dp(c,3));image.setBackground(d);}
        image.setOnClickListener(new View.OnClickListener(){public void onClick(View view){open(view,bound,person,feature);}});return image;
    }
    static void open(View view,SnsLocalRuntime.Bound bound,SnsLocalData.Person person,SnsLocalData.Feature feature){if(SnsLocalRuntime.valid(bound,feature))SnsLocalNative.profile(SnsLocalNative.activity(view.getContext()),bound.owner,person.id);}
    static View likes(Context c,final SnsLocalRuntime.Bound bound,List<SnsLocalData.Person> values,CharSequence genuine)throws Throwable{
        View outer=SnsLocalNative.likesView(c);TextView label=SnsLocalNative.likesLabel(outer);final int color=SnsLocalNative.linkColor(c);
        SpannableStringBuilder text=new SpannableStringBuilder();boolean hasGenuine=genuine!=null&&genuine.length()>1;
        if(hasGenuine)text.append(genuine);else{text.append(" ");text.setSpan(SnsLocalNative.heart(c),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        int count=Math.min(bound.likesVisible,values.size());
        for(int i=0;i<count;i++){if(hasGenuine||i>0)text.append(", ");final SnsLocalData.Person person=values.get(i);int start=text.length();text.append(person.name);text.setSpan(new ClickableSpan(){public void onClick(View view){open(view,bound,person,SnsLocalData.Feature.LIKES);}public void updateDrawState(TextPaint paint){SnsLocalNative.nameStyle(paint);paint.setColor(color);paint.setUnderlineText(false);}},start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        if(count<values.size()){text.append(", … ");int start=text.length();text.append("展开更多（共"+values.size()+"人）");text.setSpan(new ClickableSpan(){public void onClick(View view){if(SnsLocalRuntime.valid(bound,SnsLocalData.Feature.LIKES))SnsLocalRuntime.expand(bound,true);}public void updateDrawState(TextPaint paint){paint.setColor(color);paint.setUnderlineText(false);}},start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        label.setText(text,TextView.BufferType.SPANNABLE);label.setTextColor(color);label.setHighlightColor(0x22607090);label.setMovementMethod(LinkMovementMethod.getInstance());
        ((View)de.robv.android.xposed.XposedHelpers.getObjectField(outer,"h")).setVisibility(View.GONE);outer.setVisibility(View.VISIBLE);return outer;
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
}
