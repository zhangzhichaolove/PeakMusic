package com.chao.peakmusic.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import okhttp3.Response;

/** Validate the endpoint used by the app, not merely whether a web server answers. */
final class ApiHealthCheck {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private ApiHealthCheck() { }

    /** Null means a successful, compatible response; all details omit server body/URLs. */
    static String failure(Response response) throws IOException {
        if (!response.isSuccessful()) return "HTTP " + response.code();
        if (response.body() == null) return "响应为空";
        byte[] bytes = response.peekBody(MAX_RESPONSE_BYTES + 1L).bytes();
        if (bytes.length > MAX_RESPONSE_BYTES) return "响应过大，请检查列表分页配置";
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return "响应不是 JSON 对象";
            JsonObject body = parsed.getAsJsonObject();
            JsonElement success = body.get("success");
            if (success == null || !success.isJsonPrimitive() || !success.getAsJsonPrimitive().isBoolean())
                return "响应缺少 success 布尔字段";
            if (!success.getAsBoolean()) return "接口返回业务失败";
            if (!body.has("result") || !body.get("result").isJsonObject()) return "响应缺少 result 对象";
            JsonElement records = body.getAsJsonObject("result").get("records");
            if (records == null || !records.isJsonArray()) return "响应缺少 records 数组";
            for (JsonElement item : records.getAsJsonArray()) {
                if (!item.isJsonObject()) return "记录结构错误";
                JsonElement source = item.getAsJsonObject().get("mp3");
                if (source == null || !source.isJsonPrimitive() || !source.getAsJsonPrimitive().isString()
                        || source.getAsString().trim().isEmpty()) return "记录缺少 mp3 播放地址";
            }
            return null; // A genuinely empty catalogue is still a valid service.
        } catch (RuntimeException error) {
            return "响应不是有效的接口 JSON";
        }
    }
}
