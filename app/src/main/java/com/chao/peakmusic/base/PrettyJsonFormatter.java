package com.chao.peakmusic.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

/** Formats JSON for debug output while preserving non-JSON server responses verbatim. */
public final class PrettyJsonFormatter {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private PrettyJsonFormatter() {
    }

    public static String format(String body) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        try {
            return GSON.toJson(JsonParser.parseString(body));
        } catch (RuntimeException ignored) {
            return body;
        }
    }
}
