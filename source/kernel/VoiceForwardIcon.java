package com.zafiro.wxentry.kernel;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A voice bubble and outward arrow, using the surrounding menu's monochrome line style. */
final class VoiceForwardIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    VoiceForwardIcon(Context context) {
        size=Math.max(1,Math.round(24*context.getResources().getDisplayMetrics().density));
        boolean night=(context.getResources().getConfiguration().uiMode&0x30)==0x20;
        int color=night?0xffededed:0xff191919;
        TypedArray colors=context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try { color=colors.getColor(0,color); } finally { colors.recycle(); }
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
    }
    public void draw(Canvas canvas) {
        Rect b=getBounds();int saved=canvas.save();
        canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
        Path bubble=new Path();
        bubble.moveTo(13,5);bubble.lineTo(4.2f,5);bubble.quadTo(2,5,2,7.2f);
        bubble.lineTo(2,17.8f);bubble.quadTo(2,20,4.2f,20);bubble.lineTo(5,20);
        bubble.lineTo(5,22);bubble.lineTo(8.5f,20);bubble.lineTo(16.8f,20);
        bubble.quadTo(19,20,19,17.8f);bubble.lineTo(19,13.5f);canvas.drawPath(bubble,paint);
        canvas.drawLine(5.5f,10,5.5f,14,paint);
        canvas.drawLine(8.5f,8,8.5f,16,paint);
        canvas.drawLine(11.5f,10,11.5f,14,paint);
        Path arrow=new Path();arrow.moveTo(14,10);arrow.lineTo(22,2);
        arrow.moveTo(16,2);arrow.lineTo(22,2);arrow.lineTo(22,8);canvas.drawPath(arrow,paint);
        canvas.restoreToCount(saved);
    }
    public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
    public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    public int getIntrinsicWidth() { return size; }
    public int getIntrinsicHeight() { return size; }
}
