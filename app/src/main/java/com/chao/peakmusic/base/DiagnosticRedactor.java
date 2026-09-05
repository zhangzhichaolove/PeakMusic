package com.chao.peakmusic.base;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;

/** Known credential fields and URL locations are never persisted. Not an anonymity guarantee. */
public final class DiagnosticRedactor {
    private static final Pattern URL = Pattern.compile("(?i)(?:https?|file|content)://[^\\s\\\"<>]+");
    private static final Pattern AUTH = Pattern.compile("(?i)\\b(?:Bearer|Basic)\\s+[^\\s,;]+");
    private static final Pattern JWT = Pattern.compile("[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");
    public static final String OMITTED = "{\"body_omitted\":\"Non-JSON, malformed or overly complex body\"}";
    private DiagnosticRedactor() { }

    public static String origin(String address) {
        HttpUrl url = HttpUrl.parse(address);
        if (url == null) return "(URL omitted)";
        return url.newBuilder().username("").password("").encodedPath("/").query(null).fragment(null).build().toString();
    }

    public static String json(String input) {
        if (input == null || input.length() > 2 * 1024 * 1024) return OMITTED;
        try (JsonReader in = new JsonReader(new StringReader(input)); StringWriter output = new StringWriter();
             JsonWriter out = new JsonWriter(output)) {
            in.setStrictness(Strictness.STRICT);
            if (in.peek() != JsonToken.BEGIN_OBJECT && in.peek() != JsonToken.BEGIN_ARRAY) return OMITTED;
            copy(in, out, 0, new int[]{0});
            if (in.peek() != JsonToken.END_DOCUMENT) return OMITTED;
            out.flush(); return output.toString();
        } catch (IOException | RuntimeException error) { return OMITTED; }
    }

    private static void copy(JsonReader in, JsonWriter out, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > 50000) throw new IOException("Diagnostic complexity limit");
        switch (in.peek()) {
            case BEGIN_OBJECT:
                in.beginObject(); out.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName(); out.name(string(name));
                    if (sensitive(name)) { in.skipValue(); out.value("(redacted)"); }
                    else copy(in, out, depth + 1, nodes);
                }
                in.endObject(); out.endObject(); break;
            case BEGIN_ARRAY:
                in.beginArray(); out.beginArray();
                while (in.hasNext()) copy(in, out, depth + 1, nodes);
                in.endArray(); out.endArray(); break;
            case STRING: out.value(string(in.nextString())); break;
            case NUMBER: out.jsonValue(in.nextString()); break;
            case BOOLEAN: out.value(in.nextBoolean()); break;
            case NULL: in.nextNull(); out.nullValue(); break;
            default: throw new IOException("Unexpected JSON token");
        }
    }

    private static boolean sensitive(String field) {
        String key = field;
        try { key = URLDecoder.decode(key, "UTF-8"); } catch (Exception ignored) { }
        key = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.contains("token") || key.contains("secret") || key.contains("password") || key.contains("passwd")
                || key.contains("authorization") || key.contains("cookie") || key.contains("credential") || key.contains("session")
                || key.contains("apikey") || key.contains("accesskey") || key.contains("privatekey") || key.equals("key")
                || key.contains("signature") || key.equals("sign") || key.equals("email") || key.equals("phone")
                || key.equals("mobile") || key.equals("username") || key.equals("account") || key.equals("address")
                || key.equals("ip") || key.equals("clientip") || key.equals("filepath") || key.equals("path");
    }

    @android.annotation.SuppressLint("SdCardPath") // Match/redact literal input, not filesystem access.
    private static String string(String value) {
        String trimmed = value.trim();
        // JSON-in-a-string cannot bypass field filtering; don't attempt unbounded nested parsing.
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "(embedded data omitted)";
        if (trimmed.startsWith("/storage/") || trimmed.startsWith("/sdcard/") || trimmed.startsWith("/data/user/")) return "(local path)";
        Matcher urls = URL.matcher(value); StringBuffer result = new StringBuffer();
        while (urls.find()) urls.appendReplacement(result, Matcher.quoteReplacement(origin(urls.group())));
        urls.appendTail(result);
        return JWT.matcher(AUTH.matcher(result.toString()).replaceAll("(redacted)")).replaceAll("(redacted)");
    }
}
