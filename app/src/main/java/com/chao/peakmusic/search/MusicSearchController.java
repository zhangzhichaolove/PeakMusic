package com.chao.peakmusic.search;

import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.catalog.MusicPageController;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.model.MusicModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;

/** Each input/scope change immediately invalidates old work, including the debounce window. */
public final class MusicSearchController {
    public enum State { PROMPT, LOADING, CONTENT, EMPTY, ERROR, LOADING_MORE, MORE_ERROR }
    public interface Listener { void onResult(State state, List<MusicTrackEntity> tracks); }
    private final MusicPageController pages;
    private boolean onlineQuery;
    private final Scheduler worker;
    private final Scheduler main;
    private final Listener listener;
    private Disposable request;
    private long generation;
    private boolean closed;

    public MusicSearchController(ApiUrl api, Scheduler worker, Scheduler main, Listener listener) {
        this.pages = new MusicPageController(api, worker, main, (state, songs) -> {
            List<MusicTrackEntity> tracks = new ArrayList<>();
            for (MusicModel music : songs) tracks.add(MusicTrackEntity.from(music));
            listener.onResult(State.valueOf(state.name()), tracks);
        });
        this.worker = worker;
        this.main = main;
        this.listener = listener;
    }

    public void search(String input, boolean online, List<MusicTrackEntity> local, boolean immediate) {
        if (closed) return;
        long version = ++generation;
        if (request != null) request.dispose();
        pages.cancel();
        String query = input == null ? "" : input.trim();
        onlineQuery = online && !query.isEmpty();
        if (online && query.isEmpty()) {
            listener.onResult(State.PROMPT, Collections.emptyList());
            return;
        }
        if (onlineQuery) { pages.refresh(query, immediate ? 0 : 350); return; }
        listener.onResult(State.LOADING, Collections.emptyList());
        List<MusicTrackEntity> snapshot = new ArrayList<>(local);
        Observable<List<MusicTrackEntity>> search = Observable.fromCallable(() -> filterLocal(query, snapshot));
        request = Observable.timer(immediate ? 0 : 350, TimeUnit.MILLISECONDS, worker)
                .flatMap(ignored -> search).subscribeOn(worker).observeOn(main)
                .subscribe(tracks -> {
                    if (!closed && version == generation) listener.onResult(
                            tracks.isEmpty() ? State.EMPTY : State.CONTENT, tracks);
                }, error -> {
                    if (!closed && version == generation) listener.onResult(State.ERROR, Collections.emptyList());
                });
    }

    public void loadNext() { if (onlineQuery) pages.loadNext(); }
    public MusicPageController onlinePage() { return onlineQuery ? pages : null; }

    private static List<MusicTrackEntity> filterLocal(String query, List<MusicTrackEntity> local) {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<MusicTrackEntity> matches = new ArrayList<>();
        for (MusicTrackEntity track : local) {
            if (normalized.isEmpty() || contains(track.name, normalized)
                    || contains(track.artist, normalized) || contains(track.album, normalized)) matches.add(track);
        }
        return matches;
    }

    private static boolean contains(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    public void close() {
        closed = true;
        pages.close();
        generation++;
        if (request != null) request.dispose();
    }
}
