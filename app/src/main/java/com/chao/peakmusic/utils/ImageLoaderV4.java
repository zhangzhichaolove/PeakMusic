package com.chao.peakmusic.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

/**
 * Created by Chao on 2017-12-19.
 */

public class ImageLoaderV4 {
    private static ImageLoaderV4 instance;
    private RequestOptions options = new RequestOptions()
            .centerCrop()
            .dontAnimate()
            .priority(Priority.HIGH)
            .diskCacheStrategy(DiskCacheStrategy.ALL);
    private RequestOptions optionsCircle = RequestOptions.circleCropTransform()
            .dontAnimate()
            .priority(Priority.HIGH)
            .diskCacheStrategy(DiskCacheStrategy.ALL);

    private ImageLoaderV4() {
    }

    public static ImageLoaderV4 getInstance() {
        if (instance == null) {
            synchronized (ImageLoaderV4.class) {
                if (instance == null) {
                    instance = new ImageLoaderV4();
                }
            }
        }
        return instance;
    }

    public void load(Context context, ImageView imageView, Object url) {
        Glide.with(context)
                .load(url)
                .apply(options)
                .into(imageView);
    }


    public void loadCircle(Context context, ImageView imageView, Object url) {
        Glide.with(context)
                .load(url)
                .apply(optionsCircle)
                .into(imageView);
    }
}
