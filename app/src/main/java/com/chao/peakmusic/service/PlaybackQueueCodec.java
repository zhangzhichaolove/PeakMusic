package com.chao.peakmusic.service;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable JSON codec for persisting the playback queue. */
final class PlaybackQueueCodec {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Item>>() { }.getType();

    private PlaybackQueueCodec() {
    }

    static String encode(List<? extends Item> items) {
        return GSON.toJson(items == null ? Collections.emptyList() : items, LIST_TYPE);
    }

    static List<Item> decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Item> items = GSON.fromJson(json, LIST_TYPE);
            return items == null ? Collections.emptyList() : items;
        } catch (JsonParseException error) {
            return new ArrayList<>();
        }
    }

    static class Item {
        String source;
        String name;
        String artist;
        boolean local;

        Item(String source, String name, String artist, boolean local) {
            this.source = source;
            this.name = name;
            this.artist = artist;
            this.local = local;
        }
    }
}
