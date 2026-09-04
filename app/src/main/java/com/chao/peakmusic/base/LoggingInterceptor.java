package com.chao.peakmusic.base;

import android.util.Log;

import com.chao.peakmusic.BuildConfig;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Chao  2018/3/9 on 11:41
 * description 网络请求日志打印拦截器
 */

public class LoggingInterceptor implements Interceptor {
    private static final String TAG = "Interceptor";
    private static final AtomicLong REQUEST_IDS = new AtomicLong();

    @Override
    public Response intercept(Chain chain) throws IOException { //这个chain里面包含了request和response，所以你要什么都可以从这里拿
        Request request = chain.request();
        if (!BuildConfig.DEBUG) {
            return chain.proceed(request);
        }
        long requestId = REQUEST_IDS.incrementAndGet();
        long t1 = System.nanoTime();//请求发起的时间
        String method = request.method();
        if ("POST".equals(method)) {
            StringBuilder sb = new StringBuilder();
            if (request.body() instanceof FormBody) {
                FormBody body = (FormBody) request.body();
                for (int i = 0; i < body.size(); i++) {
                    sb.append(body.encodedName(i) + "=" + body.encodedValue(i) + ",");
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                Log.d(TAG, String.format("#%d POST %s 参数:{%s}", requestId,
                        redact(request.url().toString()), redact(sb.toString())));
            } else {
                Log.d(TAG, String.format("#%d POST %s", requestId,
                        redact(request.url().toString())));
            }
        } else {
            Log.d(TAG, String.format("#%d %s %s", requestId, method,
                    redact(request.url().toString())));
        }
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();
        long size = response.body() == null ? 0 : response.body().contentLength();
        Log.d(TAG, String.format("#%d HTTP %d %.1fms size=%d", requestId,
                response.code(), (t2 - t1) / 1e6d, size));
        return ApiResponseLogStore.logResponse(
                com.chao.peakmusic.utils.GeneralVar.getApplication(), response, requestId);
    }

    private static String redact(String value) {
        return value.replaceAll("(?i)(token|authorization|cookie|password|secret|api_key)=([^&]+)",
                "$1=██");
    }
}
