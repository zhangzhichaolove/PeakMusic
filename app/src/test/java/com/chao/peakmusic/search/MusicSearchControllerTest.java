package com.chao.peakmusic.search;

import static org.junit.Assert.*;

import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.model.MusicListModel;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class MusicSearchControllerTest {
    private final TestScheduler clock = new TestScheduler();
    private final List<String> queries = new ArrayList<>();
    private final List<PublishSubject<HttpResult<MusicListModel>>> requests = new ArrayList<>();
    private MusicSearchController controller;
    private MusicSearchController.State state;
    private List<MusicTrackEntity> results;

    @Before public void setUp() {
        controller = new MusicSearchController((query, pageNumber) -> {
            queries.add(query);
            PublishSubject<HttpResult<MusicListModel>> request = PublishSubject.create();
            requests.add(request);
            return request;
        }, clock, clock, (nextState, tracks) -> { state = nextState; results = tracks; });
    }

    @Test public void debounceOnlySendsLastTrimmedQuery() {
        controller.search(" A ", true, Collections.emptyList(), false);
        clock.advanceTimeBy(200, TimeUnit.MILLISECONDS);
        controller.search(" B ", true, Collections.emptyList(), false);
        clock.advanceTimeBy(349, TimeUnit.MILLISECONDS);
        assertTrue(queries.isEmpty());
        clock.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        assertEquals(Collections.singletonList("B"), queries);
    }

    @Test public void newInputCancelsOldRequestBeforeDebounceAndRejectsOldError() {
        controller.search("A", true, Collections.emptyList(), true);
        clock.triggerActions();
        assertTrue(requests.get(0).hasObservers());
        controller.search("B", true, Collections.emptyList(), false);
        assertFalse(requests.get(0).hasObservers());
        requests.get(0).onError(new RuntimeException());
        clock.triggerActions();
        assertEquals(MusicSearchController.State.LOADING, state);
        clock.advanceTimeBy(350, TimeUnit.MILLISECONDS);
        requests.get(1).onNext(emptyResponse());
        clock.triggerActions();
        assertEquals(MusicSearchController.State.EMPTY, state);
    }

    @Test public void blankOnlineInputCancelsAndDoesNotFetchWholeCatalogue() {
        controller.search("A", true, Collections.emptyList(), true);
        clock.triggerActions();
        controller.search("  ", true, Collections.emptyList(), false);
        clock.advanceTimeBy(1, TimeUnit.SECONDS);
        assertFalse(requests.get(0).hasObservers());
        assertEquals(1, queries.size());
        assertEquals(MusicSearchController.State.PROMPT, state);
    }

    @Test public void localScopeFiltersNameArtistAlbumWithoutNetwork() {
        MusicTrackEntity track = new MusicTrackEntity();
        track.name = "Song"; track.artist = "ARTIST"; track.album = "Collection";
        for (String query : new String[]{"song", "artist", "collection"}) {
            controller.search(query, false, Collections.singletonList(track), true);
            clock.triggerActions();
            assertEquals(MusicSearchController.State.CONTENT, state);
            assertSame(track, results.get(0));
        }
        assertTrue(queries.isEmpty());
    }

    @Test public void switchScopeCancelsOnlineAndKeepsLocalResult() {
        controller.search("A", true, Collections.emptyList(), true);
        clock.triggerActions();
        MusicTrackEntity track = new MusicTrackEntity(); track.name = "A";
        controller.search("A", false, Collections.singletonList(track), true);
        requests.get(0).onNext(emptyResponse());
        clock.triggerActions();
        assertFalse(requests.get(0).hasObservers());
        assertSame(track, results.get(0));
    }

    @Test public void malformedOrFailedBusinessResponseShowsRetryableError() {
        controller.search("A", true, Collections.emptyList(), true);
        clock.triggerActions();
        requests.get(0).onNext(new HttpResult<>());
        clock.triggerActions();
        assertEquals(MusicSearchController.State.ERROR, state);
        controller.search("A", true, Collections.emptyList(), true);
        clock.triggerActions();
        requests.get(1).onNext(emptyResponse());
        clock.triggerActions();
        assertEquals(MusicSearchController.State.EMPTY, state);
    }

    @Test public void closeCancelsDebounceAndCallbacks() {
        controller.search("A", true, Collections.emptyList(), false);
        controller.close();
        clock.advanceTimeBy(1, TimeUnit.SECONDS);
        assertTrue(queries.isEmpty());
    }

    private static HttpResult<MusicListModel> emptyResponse() {
        HttpResult<MusicListModel> response = new HttpResult<>();
        response.setSuccess(true);
        MusicListModel page = new MusicListModel(); page.setRecords(Collections.emptyList());
        response.setResult(page);
        return response;
    }
}
