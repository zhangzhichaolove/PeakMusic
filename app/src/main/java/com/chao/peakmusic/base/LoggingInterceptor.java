package com.chao.peakmusic.base;

import android.util.Log;

import com.blankj.utilcode.util.LogUtils;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Created by Chao  2018/3/9 on 11:41
 * description 网络请求日志打印拦截器
 */

public class LoggingInterceptor implements Interceptor {
    private static final String TAG = "Interceptor";

    @Override
    public Response intercept(Chain chain) throws IOException { //这个chain里面包含了request和response，所以你要什么都可以从这里拿
        Request request = chain.request();
        long t1 = System.nanoTime();//请求发起的时间
        String method = request.method();
        if ("POST".equals(method)) {
            StringBuilder sb = new StringBuilder();
            if (request.body() instanceof FormBody) {
                FormBody body = (FormBody) request.body();
                for (int i = 0; i < body.size(); i++) {
                    sb.append(body.encodedName(i) + "=" + body.encodedValue(i) + ",");
                }
                sb.delete(sb.length() - 1, sb.length());
                Log.e(TAG, String.format(" %n发送POST请求 %s on %s %n请求头:%n%s请求参数:{%s}", request.url(), chain.connection(), request.headers(), sb.toString()));
            } else {
                Log.e(TAG, String.format(" %n发送POST请求 %s on %s %n请求头:%n%s%n请求参数:%s", request.url(), chain.connection(), request.headers(), stringifyRequestBody(request)));
            }
        } else {
            Log.e(TAG, String.format(" %n发送GET请求 %s on %s%n%s", request.url(), chain.connection(), request.headers()));
        }
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();//收到响应的时间 //这里不能直接使用response.body().string()的方式输出日志 //因为response.body().string()之后，response中的流会被关闭，程序会报错，我们需要创建出一 //个新的response给应用层处理
        ResponseBody responseBody = response.peekBody(1024 * 1024);
        //Log.e("TAG", String.format("接收响应: [%s] %n返回json:【%s】 %.1fms %n%s", response.request().url(), responseBody.string(), (t2 - t1) / 1e6d, response.headers()));
        Log.e(TAG, String.format(" %n接收响应: [%s] %.1fms %n%s", response.request().url(), (t2 - t1) / 1e6d, response.headers()));
        LogUtils.json(TAG, responseBody.string());
        return response;
    }

    private String stringifyRequestBody(Request request) {
        try {
            final Request copy = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }
}
