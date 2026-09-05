package com.chao.peakmusic.base;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Interceptor;
import okhttp3.Response;

/** Explicitly enabled diagnostics, never request headers/bodies or raw logcat output. */
public final class LoggingInterceptor implements Interceptor {
    private static final AtomicLong IDS = new AtomicLong();
    @Override public Response intercept(Chain chain) throws IOException {
        android.content.Context context = com.chao.peakmusic.utils.GeneralVar.getApplication();
        long ticket = ApiResponseLogStore.begin(context);
        if (ticket < 0) return chain.proceed(chain.request());
        long id = IDS.incrementAndGet(), start = System.nanoTime();
        try {
            Response response = chain.proceed(chain.request());
            return ApiResponseLogStore.logResponse(context, response, id, ticket, (System.nanoTime() - start) / 1_000_000);
        } catch (IOException error) {
            ApiResponseLogStore.logFailure(context, chain.request(), id, ticket, (System.nanoTime() - start) / 1_000_000, error);
            throw error;
        }
    }
}
