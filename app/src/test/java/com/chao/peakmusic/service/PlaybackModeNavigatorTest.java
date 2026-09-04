package com.chao.peakmusic.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Random;

public class PlaybackModeNavigatorTest {
    private final Random random = new Random(1);

    @Test
    public void sequentialStopsAtQueueEdges() {
        assertEquals(2, PlaybackModeNavigator.next(1, 3,
                PlaybackModeNavigator.SEQUENTIAL, true, random));
        assertEquals(-1, PlaybackModeNavigator.next(2, 3,
                PlaybackModeNavigator.SEQUENTIAL, true, random));
        assertEquals(-1, PlaybackModeNavigator.previous(0, 3,
                PlaybackModeNavigator.SEQUENTIAL));
    }

    @Test
    public void repeatAllWrapsAtQueueEdges() {
        assertEquals(0, PlaybackModeNavigator.next(2, 3,
                PlaybackModeNavigator.REPEAT_ALL, true, random));
        assertEquals(2, PlaybackModeNavigator.previous(0, 3,
                PlaybackModeNavigator.REPEAT_ALL));
    }

    @Test
    public void repeatOneOnlyRepeatsOnAutomaticCompletion() {
        assertEquals(1, PlaybackModeNavigator.next(1, 3,
                PlaybackModeNavigator.REPEAT_ONE, true, random));
        assertEquals(2, PlaybackModeNavigator.next(1, 3,
                PlaybackModeNavigator.REPEAT_ONE, false, random));
    }

    @Test
    public void shuffleNeverSelectsCurrentTrackWhenPossible() {
        for (int i = 0; i < 20; i++) {
            int next = PlaybackModeNavigator.next(1, 3,
                    PlaybackModeNavigator.SHUFFLE, true, random);
            org.junit.Assert.assertNotEquals(1, next);
        }
    }
}
