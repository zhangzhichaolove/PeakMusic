package com.chao.peakmusic.base;

import com.chao.peakmusic.data.MusicSource;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.MusicListModel;
import io.reactivex.rxjava3.core.Observable;

/** Bind list/search results to the API that issued the request, even after settings change. */
public final class SourcedMusicApi implements ApiUrl {
    private final ApiUrl delegate;
    private final String baseUrl;
    public SourcedMusicApi(ApiUrl delegate, String baseUrl) {
        this.delegate = delegate;
        this.baseUrl = MusicSource.normalize(baseUrl);
    }
    @Override public Observable<HttpResult<MusicListModel>> getMusicList(String query, int page) {
        return delegate.getMusicList(query, page).map(response -> {
            MusicListModel list = response.getResult();
            if (list != null && list.getRecords() != null) for (MusicModel music : list.getRecords()) {
                if (music != null) music.bindApiSource(baseUrl);
            }
            return response;
        });
    }
}
