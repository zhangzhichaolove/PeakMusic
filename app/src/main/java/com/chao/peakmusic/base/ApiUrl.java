package com.chao.peakmusic.base;

import com.chao.peakmusic.model.MusicListModel;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Chao on 2018-09-23.
 */

public interface ApiUrl {

    String BASE_URL = "http://nas.peakchao.kdns.fr:55008/";

    default Observable<HttpResult<MusicListModel>> getMusicList(String name) { return getMusicList(name, 1); }

    @GET("music/getMusicList")
    Observable<HttpResult<MusicListModel>> getMusicList(@Query("search") String name, @Query("page") int page);

}
