package com.chao.peakmusic.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PlaybackQueueCodecTest {
    @Test
    public void roundTripsUnicodeAndLocalState() {
        List<PlaybackQueueCodec.Item> queue = Arrays.asList(
                new PlaybackQueueCodec.Item("content://media/1", "本地歌曲", "歌手", true),
                new PlaybackQueueCodec.Item("https://example.com/a.mp3", "Online", "Artist", false));

        List<PlaybackQueueCodec.Item> restored = PlaybackQueueCodec.decode(
                PlaybackQueueCodec.encode(queue));

        assertEquals(2, restored.size());
        assertEquals("本地歌曲", restored.get(0).name);
        assertTrue(restored.get(0).local);
        assertEquals("https://example.com/a.mp3", restored.get(1).source);
    }

    @Test
    public void invalidStateReturnsEmptyQueue() {
        assertTrue(PlaybackQueueCodec.decode("not-json").isEmpty());
        assertTrue(PlaybackQueueCodec.decode(null).isEmpty());
    }
}
