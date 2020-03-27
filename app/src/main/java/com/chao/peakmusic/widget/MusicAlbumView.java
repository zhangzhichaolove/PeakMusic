package com.chao.peakmusic.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.chao.peakmusic.utils.FormatDrawable;


/**
 * 音乐专辑胶片View
 * Created by Chao on 2017-12-17.
 */

public class MusicAlbumView extends androidx.appcompat.widget.AppCompatImageView {
    private Paint paint;
    private int radiusWidth = 80;
    private int strokeWidth = dp2px(radiusWidth * 2 / 3);
    private int circleWidth = dp2px(radiusWidth * 1 / 3);
    private Rect mSrcRect, mDestRect;
    private Bitmap bitmap = null;

    public MusicAlbumView(Context context) {
        this(context, null);
    }

    public MusicAlbumView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MusicAlbumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint = new Paint();
        paint.setAntiAlias(true);//抗锯齿
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //宽高根据strokeWidth计算
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int sizeWidth = MeasureSpec.getSize(widthMeasureSpec);
        int sizeHeight = MeasureSpec.getSize(heightMeasureSpec);
        int width = 0;
        int height = 0;
        switch (widthMode) {
            //父容器不对View有任何限制，要多大给多大，这种情况一般用于系统内部，表示一种测量的状态。
            case MeasureSpec.UNSPECIFIED:
                //父容器指定了一个可用大小即SpecSize，View大小不能大于这个值，具体是什么值要看不同View的具体实现。它对应于LayoutParams中的Wrap_content
            case MeasureSpec.AT_MOST:
                width = strokeWidth + dp2px(5) + dp2px(radiusWidth * 2);
                height = strokeWidth + dp2px(5) + dp2px(radiusWidth * 2);
                break;
            //已经测量出View所需要的精确大小，这个时候View的最终大小就是SpecSize所指定的值。它对应于LayoutParams中的match_parent和具体的数值这两种模式。
            case MeasureSpec.EXACTLY:
                sizeWidth = Math.min(sizeWidth, sizeHeight);
                radiusWidth = (sizeWidth - dp2px(5)) * 3 / 8;
                radiusWidth = px2dip(radiusWidth);
                strokeWidth = dp2px(radiusWidth * 2 / 3);
                circleWidth = dp2px(radiusWidth * 1 / 3);
                width = sizeWidth;
                height = sizeWidth;
                break;
            default:
                break;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //super.onDraw(canvas);
        BitmapDrawable drawable = null;
        if (getDrawable() == null && bitmap == null) {
            return;
        }
        if (getDrawable() instanceof BitmapDrawable) {
            drawable = (BitmapDrawable) getDrawable();
        }
        if (drawable == null) {
            bitmap = FormatDrawable.getInstance().drawable2Bitmap(getDrawable());
            bitmap = toRoundBitmap(bitmap);
        } else {
            bitmap = toRoundBitmap(drawable.getBitmap());
        }
        //第一个Rect 代表要绘制的bitmap 区域，第二个 Rect 代表的是要将bitmap 绘制在屏幕的什么地方
        mSrcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        mDestRect = new Rect((getWidth() - strokeWidth) / 2 - circleWidth, (getHeight() - strokeWidth) / 2 - circleWidth,
                (getWidth() - strokeWidth) / 2 - circleWidth + strokeWidth * 2, (getHeight() - strokeWidth) / 2 - circleWidth + strokeWidth * 2);
        drawCircleBorder(canvas, strokeWidth, Color.BLACK);
        paint.setColor(Color.RED);
        //canvas.drawRect(mDestRect, paint);
        //paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(dp2px(1));
        //canvas.drawCircle((getWidth() - strokeWidth) / 2, (getHeight() - strokeWidth) / 2, 5, paint);
        //canvas.drawRect((getWidth() - dp2px(radiusWidth * 2)) / 2 - circleWidth - dp2px(5 / 2), (getHeight() - dp2px(radiusWidth * 2)) / 2 - circleWidth - dp2px(5 / 2),
        //        (getWidth() - dp2px(radiusWidth * 2)) / 2 + dp2px(radiusWidth) * 2 + circleWidth + dp2px(5 / 2), (getHeight() - dp2px(radiusWidth * 2)) / 2 + dp2px(radiusWidth) * 2 + circleWidth + dp2px(5 / 2), paint);
        canvas.drawBitmap(bitmap, mSrcRect, mDestRect, paint);
        //canvas.drawBitmap(bitmap, (getWidth() - bitmap.getWidth()) / 2, (getHeight() - bitmap.getHeight()) / 2, paint);
    }

    /**
     * 转换图片成圆形
     *
     * @param bitmap 传入Bitmap对象
     * @return
     */
    public Bitmap toRoundBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float roundPx;
        float left, top, right, bottom, dst_left, dst_top, dst_right, dst_bottom;
        if (width <= height) {
            roundPx = width / 2;
            left = 0;
            top = 0;
            right = width;
            bottom = width;
            height = width;
            dst_left = 0;
            dst_top = 0;
            dst_right = width;
            dst_bottom = width;
        } else {
            roundPx = height / 2;
            float clip = (width - height) / 2;
            left = clip;
            right = width - clip;
            top = 0;
            bottom = height;
            width = height;
            dst_left = 0;
            dst_top = 0;
            dst_right = height;
            dst_bottom = height;
        }

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect src = new Rect((int) left, (int) top, (int) right, (int) bottom);
        final Rect dst = new Rect((int) dst_left, (int) dst_top, (int) dst_right, (int) dst_bottom);
        final RectF rectF = new RectF(dst);

        paint.setAntiAlias(true);// 设置画笔无锯齿

        canvas.drawARGB(0, 0, 0, 0); // 填充整个Canvas
        paint.setColor(color);

        // 以下有两种方法画圆,drawRounRect和drawCircle
        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);// 画圆角矩形，第一个参数为图形显示区域，第二个参数和第三个参数分别是水平圆角半径和垂直圆角半径。
        canvas.drawCircle(roundPx, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));// 设置两张图片相交时的模式,参考http://trylovecatch.iteye.com/blog/1189452
        canvas.drawBitmap(bitmap, src, dst, paint); //以Mode.SRC_IN模式合并bitmap和已经draw了的Circle

        return output;
    }

    /**
     * 边缘画圆
     */
    private void drawCircleBorder(Canvas canvas, int radius, int color) {
        paint.setFilterBitmap(true);
        paint.setDither(true);
        paint.setColor(color);
        /* 设置paint的　style　为STROKE：空心 */
        paint.setStyle(Paint.Style.STROKE);
        /* 设置paint的外框宽度 */
        paint.setStrokeWidth(strokeWidth + dp2px(5));
        canvas.drawCircle(getWidth() / 2, getHeight() / 2, dp2px(radiusWidth), paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    private int getScreenWidth() {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay().getWidth();
    }

    private int dp2px(float dpValue) {
        float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public int px2dip(float pxValue) {
        final float scale = getContext().getResources()
                .getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

}
