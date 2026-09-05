package com.chao.peakmusic.catalog;

import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.model.MusicListModel;
import com.chao.peakmusic.model.MusicModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;

/** One catalogue/query, one request at a time; only successful pages advance the cursor. */
public final class MusicPageController {
    public enum State { LOADING, CONTENT, EMPTY, ERROR, LOADING_MORE, MORE_ERROR }
    public interface Listener { void onPage(State state, List<MusicModel> songs); }
    private final ApiUrl api;
    private final Scheduler worker;
    private final Scheduler main;
    private final Listener listener;
    private final LinkedHashMap<String, MusicModel> songs = new LinkedHashMap<>();
    private Disposable request;
    private long generation;
    private int page;
    private int total;
    private String query = "";
    private boolean loading;
    private boolean hasMore;
    private boolean closed;
    private State state = State.EMPTY;

    public MusicPageController(ApiUrl api, Scheduler worker, Scheduler main, Listener listener) {
        this.api = api; this.worker = worker; this.main = main; this.listener = listener;
    }

    public void refresh(String query, long debounceMs) {
        if (closed) return;
        cancel();
        this.query = query;
        page = 0; total = 0; songs.clear(); hasMore = true;
        fetch(debounceMs);
    }

    public void loadNext() {
        if (!closed && !loading && hasMore) fetch(0);
    }

    private void fetch(long delayMs) {
        loading = true;
        int requestedPage = page + 1;
        long version = generation;
        String requestedQuery = query;
        publish(page == 0 ? State.LOADING : State.LOADING_MORE);
        request = Observable.timer(delayMs, TimeUnit.MILLISECONDS, worker)
                .flatMap(ignored -> api.getMusicList(requestedQuery, requestedPage)).take(1)
                .switchIfEmpty(Observable.error(new IllegalArgumentException("Missing music page")))
                .subscribeOn(worker).observeOn(main).subscribe(response -> {
                    if (closed || version != generation) return;
                    try { accept(response, requestedPage); }
                    catch (IllegalArgumentException error) { failed(); }
                }, error -> { if (!closed && version == generation) failed(); });
    }

    private void accept(HttpResult<MusicListModel> response, int requestedPage) {
        MusicListModel result = response == null ? null : response.getResult();
        if (response == null || !response.isSuccess() || result == null || result.getRecords() == null
                || (result.getCurrent() > 0 && result.getCurrent() != requestedPage)) {
            throw new IllegalArgumentException("Invalid music page");
        }
        int before = songs.size();
        for (MusicModel song : result.getRecords()) {
            if (song != null && song.getMp3() != null && !song.getMp3().trim().isEmpty()) {
                songs.put(MusicTrackEntity.keyOf(song), song);
            }
        }
        page = requestedPage;
        total = Math.max(0, result.getTotal());
        int pages = result.getPages();
        if (pages <= 0 && total > 0 && result.getSize() > 0) {
            pages = (int) (((long) total + result.getSize() - 1) / result.getSize());
        }
        // Prefer server metadata, not the filtered/deduplicated row count. Old endpoints may omit it.
        hasMore = !result.getRecords().isEmpty() && (pages > 0 ? page < pages
                : songs.size() > before && (result.getSize() <= 0 || result.getRecords().size() >= result.getSize()));
        loading = false;
        publish(songs.isEmpty() ? State.EMPTY : State.CONTENT);
    }

    private void failed() {
        loading = false;
        publish(page == 0 ? State.ERROR : State.MORE_ERROR);
    }
    private void publish(State next) { state = next; listener.onPage(state, songs()); }
    public State state() { return state; }
    public boolean hasMore() { return hasMore; }
    public int total() { return total; }
    public List<MusicModel> songs() { return Collections.unmodifiableList(new ArrayList<>(songs.values())); }
    public void cancel() { generation++; if (request != null) request.dispose(); request = null; loading = false; }
    public void close() { closed = true; cancel(); }
}
