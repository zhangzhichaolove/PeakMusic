package com.chao.peakmusic.base;

import com.chao.peakmusic.model.MusicDetailsResultModel;
import com.chao.peakmusic.model.MusicListModel;

import java.util.List;

import io.reactivex.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Chao on 2018-09-23.
 */

public interface ApiUrl {

//    String BASE_URL = "http://api.apiopen.top/";
//
//    @GET("musicBroadcastingDetails?channelname=public_tuijian_spring")
//    Observable<HttpResult<MusicListModel>> getMusicHome();
//
//    @GET("musicDetails")
//    Observable<HttpResult<MusicDetailsResultModel>> getMusicDetails(@Query("id") int id);


    String BASE_URL = "http://web.peakchao.top:250/";


    @GET("music/getMusicBanner")
    Observable<HttpResult<MusicListModel>> getMusicBanner();

    @GET("music/getMusicList")
    Observable<HttpResult<MusicListModel>> getMusicList(@Query("search") String name);

}
