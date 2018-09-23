package com.chao.peakmusic.base;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Chao  2018/3/9 on 11:48
 * description
 */

public class ApiRequest {


    /**
     * 不含有DTO的请求
     *
     * @param observable
     * @param observer
     * @param <T>
     */
    public synchronized static <T extends HttpResult> void obtain(Observable<T> observable, Observer<T> observer) {
        observable.observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .subscribe(observer);
    }


}
