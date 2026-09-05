package com.chao.peakmusic.catalog;

import static org.junit.Assert.*;
import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.model.MusicListModel;
import com.chao.peakmusic.model.MusicModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Test;

public class MusicPageControllerTest {
    private final TestScheduler clock = new TestScheduler();
    private final List<String> sent = new ArrayList<>();
    private final List<PublishSubject<HttpResult<MusicListModel>>> pending = new ArrayList<>();
    private final MusicPageController pages = new MusicPageController((query, page) -> {
        sent.add(query + ":" + page);
        PublishSubject<HttpResult<MusicListModel>> reply = PublishSubject.create(); pending.add(reply); return reply;
    }, clock, clock, (state, songs) -> { });

    @Test public void appendsPagesWithoutDuplicateSongsAndStopsAtServerLastPage() {
        start(); reply(0, response(1, 2, "1", "2"));
        assertTrue(pages.hasMore()); pages.loadNext(); pages.loadNext(); clock.triggerActions();
        assertEquals(Arrays.asList(":1", ":2"), sent);
        reply(1, response(2, 2, "2", "3"));
        assertEquals(3, pages.songs().size()); assertFalse(pages.hasMore());
        pages.loadNext(); clock.triggerActions(); assertEquals(2, sent.size());
    }
    @Test public void failedSecondPageKeepsRowsAndRetriesSamePage() {
        start(); reply(0, response(1, 3, "1")); pages.loadNext(); clock.triggerActions();
        pending.get(1).onError(new RuntimeException()); clock.triggerActions();
        assertEquals(MusicPageController.State.MORE_ERROR, pages.state()); assertEquals(1, pages.songs().size());
        pages.loadNext(); clock.triggerActions(); assertEquals(":2", sent.get(2));
        reply(2, response(2, 3, "2")); assertEquals(2, pages.songs().size());
    }
    @Test public void queryChangeImmediatelyCancelsOldPageEvenDuringDebounce() {
        start(); reply(0, response(1, 3, "1")); pages.loadNext(); clock.triggerActions();
        pages.refresh("new", 350); assertFalse(pending.get(1).hasObservers());
        reply(1, response(2, 3, "stale")); assertTrue(pages.songs().isEmpty());
        clock.advanceTimeBy(350, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("new:1", sent.get(2)); reply(2, response(1, 1, "new"));
        assertEquals("new", pages.songs().get(0).getId());
    }
    @Test public void emptyPageStopsEvenIfServerTotalIsStale() {
        start(); reply(0, response(1, 3)); assertFalse(pages.hasMore()); assertEquals(MusicPageController.State.EMPTY, pages.state());
    }
    @Test public void wrongPageAndMalformedResponsesDoNotAdvanceCursor() {
        start(); reply(0, new HttpResult<>()); assertEquals(MusicPageController.State.ERROR, pages.state());
        pages.loadNext(); clock.triggerActions(); reply(1, response(1, 3, "1"));
        pages.loadNext(); clock.triggerActions(); reply(2, response(1, 3, "1"));
        assertEquals(MusicPageController.State.MORE_ERROR, pages.state());
        pages.loadNext(); clock.triggerActions(); assertEquals(":2", sent.get(3));
    }
    @Test public void missingPageCountUsesTotalAndSizeNotDeduplicatedRows() {
        start(); HttpResult<MusicListModel> data = response(1, 0, "1", "1");
        data.getResult().setTotal(3); data.getResult().setSize(2); reply(0, data);
        assertTrue(pages.hasMore()); pages.loadNext(); clock.triggerActions();
        data = response(2, 0, "3"); data.getResult().setTotal(3); data.getResult().setSize(2); reply(1, data);
        assertFalse(pages.hasMore());
    }
    @Test public void missingAllMetadataStopsOnRepeatedPageInsteadOfLoopingForever() {
        start(); reply(0, response(0, 0, "1")); assertTrue(pages.hasMore());
        pages.loadNext(); clock.triggerActions(); reply(1, response(0, 0, "1")); assertFalse(pages.hasMore());
    }
    @Test public void closeCancelsPendingAndNewRequests() {
        start(); pages.close(); assertFalse(pending.get(0).hasObservers());
        reply(0, response(1, 1, "stale")); pages.refresh("new", 0); pages.loadNext(); clock.triggerActions();
        assertTrue(pages.songs().isEmpty()); assertEquals(1, sent.size());
    }
    private void start() { pages.refresh("", 0); clock.triggerActions(); }
    private void reply(int i, HttpResult<MusicListModel> data) { pending.get(i).onNext(data); clock.triggerActions(); }
    private HttpResult<MusicListModel> response(int current, int count, String... ids) {
        List<MusicModel> records = new ArrayList<>();
        for (String id : ids) { MusicModel song = new MusicModel(); song.setId(id); song.setMp3("https://example.com/" + id + ".mp3"); records.add(song); }
        MusicListModel result = new MusicListModel(); result.setCurrent(current); result.setPages(count); result.setRecords(records);
        HttpResult<MusicListModel> response = new HttpResult<>(); response.setSuccess(true); response.setResult(result); return response;
    }
}
