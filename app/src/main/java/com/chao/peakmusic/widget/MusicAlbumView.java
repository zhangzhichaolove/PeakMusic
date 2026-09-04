package com.chao.peakmusic.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** Draws album artwork inside a vinyl-style ring without allocating bitmaps per frame. */
public class MusicAlbumView extends AppCompatImageView {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path artworkClip = new Path();
    private final Rect artworkBounds = new Rect();

    public MusicAlbumView(Context context) {
        this(context, null);
    }

    public MusicAlbumView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MusicAlbumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ringPaint.setColor(Color.BLACK);
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int size = Math.min(width, height);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            width = size;
        }
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            height = size;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable artwork = getDrawable();
        if (artwork == null) {
            return;
        }
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float outerRadius = Math.min(getWidth(), getHeight()) / 3f;
        float ringWidth = outerRadius * 2f / 3f;
        float artworkRadius = outerRadius - ringWidth / 2f;

        ringPaint.setStrokeWidth(ringWidth);
        canvas.drawCircle(centerX, centerY, outerRadius, ringPaint);

        int intrinsicWidth = artwork.getIntrinsicWidth();
        int intrinsicHeight = artwork.getIntrinsicHeight();
        float diameter = artworkRadius * 2f;
        float drawWidth = diameter;
        float drawHeight = diameter;
        if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            float scale = Math.max(diameter / intrinsicWidth, diameter / intrinsicHeight);
            drawWidth = intrinsicWidth * scale;
            drawHeight = intrinsicHeight * scale;
        }
        artworkBounds.set(Math.round(centerX - drawWidth / 2f),
                Math.round(centerY - drawHeight / 2f),
                Math.round(centerX + drawWidth / 2f),
                Math.round(centerY + drawHeight / 2f));
        artworkClip.reset();
        artworkClip.addCircle(centerX, centerY, artworkRadius, Path.Direction.CW);
        int checkpoint = canvas.save();
        canvas.clipPath(artworkClip);
        artwork.setBounds(artworkBounds);
        artwork.draw(canvas);
        canvas.restoreToCount(checkpoint);
    }
}
