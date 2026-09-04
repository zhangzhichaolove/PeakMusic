package com.chao.peakmusic.base;

import com.chao.peakmusic.BuildConfig;
import com.chao.peakmusic.utils.GeneralVar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Chao  2018/3/9 on 11:20
 * description
 */

public class ServiceFactory {

    private final Gson mGsonDateFormat;
    private final OkHttpClient okHttpClient;
    private final Map<String, Retrofit> retrofitCache = new ConcurrentHashMap<>();

    public ServiceFactory() {
        mGsonDateFormat = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd hh:mm:ss")
                .create();
        okHttpClient = createOkHttpClient();
    }

    private static class SingletonHolder {
        private static final ServiceFactory INSTANCE = new ServiceFactory();
    }

    public static ServiceFactory getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * create a service
     *
     * @param serviceClass
     * @param <S>
     * @return
     */
    public synchronized <S> S createService(Class<S> serviceClass) {
        String baseUrl = "";
        if (serviceClass == ApiUrl.class) {
            baseUrl = ApiAddressManager.getBaseUrl();
        } else {
            try {
                Field field1 = serviceClass.getField("BASE_URL");
                baseUrl = (String) field1.get(serviceClass);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.getMessage();
                e.printStackTrace();
            }
        }
        Retrofit retrofit = retrofitCache.get(baseUrl);
        if (retrofit == null) {
            Retrofit newRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(mGsonDateFormat))
                    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                    .build();
            retrofitCache.put(baseUrl, newRetrofit);
            retrofit = newRetrofit;
        }
        return retrofit.create(serviceClass);
    }

    private final static long DEFAULT_TIMEOUT = 30;

    private OkHttpClient createOkHttpClient() {
        //定制OkHttp
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        //设置超时时间
        httpClientBuilder.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        httpClientBuilder.writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        httpClientBuilder.readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
        //设置缓存
        File httpCacheDirectory = new File(GeneralVar.getApplication().getCacheDir(), "http_cache");
        httpClientBuilder.cache(new Cache(httpCacheDirectory, 10 * 1024 * 1024));

        if (BuildConfig.DEBUG) {
            httpClientBuilder.addInterceptor(new LoggingInterceptor());
        }
        return httpClientBuilder.build();
    }
}
