package com.chao.peakmusic.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;

import com.chao.peakmusic.R;


/**
 * Created by Chao on 2017/7/31.
 */

public class CustomToolbar extends Toolbar {
    private ImageView iv_left;
    private ImageView iv_right;
    private TextView tv_center;
    private TextView tv_right;


    public CustomToolbar(Context context) {
        this(context, null);
    }

    public CustomToolbar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    private void initView(Context context, @Nullable AttributeSet attrs) {
        //int bg = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "background", 0);
        //int bg = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "background", 0);
        //TypedArray tar = getContext().obtainStyledAttributes(attrs, new int[]{android.R.attr.background, android.R.attr.text});
        //tar.getText(1);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CustomToolbar);
        String text_title = ta.getString(R.styleable.CustomToolbar_text_title);
        Drawable left_icon = ta.getDrawable(R.styleable.CustomToolbar_left_icon);
        Drawable right_icon = ta.getDrawable(R.styleable.CustomToolbar_right_icon);
        Drawable background = ta.getDrawable(R.styleable.CustomToolbar_android_background);
        int color = ta.getColor(R.styleable.CustomToolbar_android_background, Color.TRANSPARENT);
        ta.recycle();
        View contentView = LayoutInflater.from(getContext()).inflate(R.layout.layout_toolbar, this, true);
        iv_left = contentView.findViewById(R.id.toolbar_ivb_left);
        tv_center = contentView.findViewById(R.id.toolbar_tv_title);
        tv_right = contentView.findViewById(R.id.toolbar_tvb_right);
        iv_right = contentView.findViewById(R.id.toolbar_ivb_right);
        if (left_icon != null) setLeftImg(left_icon);
        if (right_icon != null) setRightImg(right_icon);
        if (!TextUtils.isEmpty(text_title)) tv_center.setText(text_title);
        setBackground(background);
        //addView(contentView);
        //很重要
        setContentInsetsRelative(0, 0);
    }

    public void setLeftImg(@DrawableRes int resId) {
        iv_left.setImageResource(resId);
    }

    public void setLeftImg(Drawable drawable) {
        iv_left.setImageDrawable(drawable);
    }

    public void setRightImg(@DrawableRes int resId) {
        iv_right.setImageResource(resId);
    }

    public void setRightImg(Drawable drawable) {
        iv_right.setImageDrawable(drawable);
    }

    @Override
    public void setTitle(@StringRes int resId) {
        tv_center.setText(resId);
    }

    @Override
    public void setTitle(CharSequence title) {
        tv_center.setText(title);
    }

    public void setRightText(@StringRes int resId) {
        tv_right.setText(resId);
    }

    public void setRightText(CharSequence text) {
        tv_right.setText(text);
    }

    public void setTitleColor(ColorStateList color) {
        tv_center.setTextColor(color);
    }

    public void setTitleColor(int color) {
        tv_center.setTextColor(color);
    }

    public void setRightColor(ColorStateList color) {
        tv_right.setTextColor(color);
    }

    public void setRightColor(@ColorInt int color) {
        tv_right.setTextColor(color);
    }

    public void setLeftImgOnClickListener(OnClickListener l) {
        iv_left.setOnClickListener(l);
    }

    public void setRightTxtOnClickListener(OnClickListener l) {
        tv_right.setOnClickListener(l);
    }
}
