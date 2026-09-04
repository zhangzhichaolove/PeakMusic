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
        return url.toString();
    }

    public static void testConnection(String value, ConnectionCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        String normalized = normalize(value);
        if (normalized == null) {
            callback.onResult(false, null);
            return;
        }
        TEST_CLIENT.newCall(new Request.Builder().url(normalized).get().build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException error) {
                        mainHandler.post(() -> callback.onResult(false, error.getMessage()));
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        int code = response.code();
                        response.close();
                        mainHandler.post(() -> callback.onResult(true, "HTTP " + code));
                    }
                });
    }

    public interface ConnectionCallback {
        void onResult(boolean reachable, String detail);
    }

    private static SharedPreferences preferences() {
        Context context = GeneralVar.getApplication();
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
