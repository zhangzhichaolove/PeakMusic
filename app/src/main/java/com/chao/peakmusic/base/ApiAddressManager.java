package com.chao.peakmusic.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.chao.peakmusic.utils.GeneralVar;

import okhttp3.HttpUrl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Persists and validates the music API base URL. */
public final class ApiAddressManager {
    private static final String PREFERENCES_NAME = "api_address";
    private static final String KEY_MUSIC_API = "music_api";
    private static final OkHttpClient TEST_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();

    private ApiAddressManager() {
    }

    public static String getBaseUrl() {
        return preferences().getString(KEY_MUSIC_API, ApiUrl.BASE_URL);
    }

    public static boolean saveBaseUrl(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return false;
        }
        preferences().edit().putString(KEY_MUSIC_API, normalized).apply();
        return true;
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        if (!candidate.endsWith("/")) {
            candidate += "/";
        }
        HttpUrl url = HttpUrl.parse(candidate);
        if (url == null || !("http".equals(url.scheme()) || "https".equals(url.scheme()))) {
            return null;
        }
        if (!url.username().isEmpty() || !url.password().isEmpty() || url.query() != null || url.fragment() != null) {
            return null;
        }
        return url.toString();
    }

    static HttpUrl connectionUrl(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : HttpUrl.get(normalized).resolve("music/getMusicList?search=");
    }

    public static Call testConnection(String value, ConnectionCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        HttpUrl endpoint = connectionUrl(value);
        if (endpoint == null) {
            callback.onResult(false, "API 地址无效");
            return null;
        }
        Call request = TEST_CLIENT.newCall(new Request.Builder().url(endpoint).get().build());
        request.enqueue(new Callback() {
            private void deliver(boolean valid, String detail) {
                mainHandler.post(() -> { if (!request.isCanceled()) callback.onResult(valid, detail); });
            }
            @Override public void onFailure(Call call, IOException error) {
                deliver(false, "请求失败，请检查网络、地址或证书");
            }
            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    String failure = ApiHealthCheck.failure(closeable);
                    deliver(failure == null, failure == null ? "HTTP " + closeable.code() + " · 列表结构正确" : failure);
                } catch (IOException error) {
                    deliver(false, "读取接口响应失败");
                }
            }
        });
        return request;
    }

    public interface ConnectionCallback {
        void onResult(boolean reachable, String detail);
    }

    private static SharedPreferences preferences() {
        Context context = GeneralVar.getApplication();
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
