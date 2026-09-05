package com.chao.peakmusic.data;

import static org.junit.Assert.*;
import android.os.Parcel;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.base.SourcedMusicApi;
import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.model.MusicListModel;
import com.google.gson.Gson;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicSourceTest {
    private MusicModel music(String base, String id, String url) {
        MusicModel music = new MusicModel(); music.setId(id); music.setMp3(url); music.bindApiSource(base);
        return music;
    }
    @Test public void idIsStableAcrossSignedUrlChangesButScopedToTheApiNotCdn() {
        MusicTrackEntity a = MusicTrackEntity.from(music("https://example.com/a/", "42", "https://cdn.example/song?token=old"));
        MusicTrackEntity refreshed = MusicTrackEntity.from(music("https://example.com/a/", "42", "https://cdn.example/song?token=new"));
        MusicTrackEntity b = MusicTrackEntity.from(music("https://example.com/b/", "42", a.getPlaybackUrl()));
        assertEquals(a.source, refreshed.source);
        assertNotEquals(a.getPlaybackUrl(), refreshed.getPlaybackUrl());
        assertNotEquals(a.source, b.source);
        assertEquals(a.getPlaybackUrl(), b.getPlaybackUrl());
    }
    @Test public void baseNormalizationRetainsSchemePortAndPathDistinctions() {
        assertEquals(MusicSource.apiId("https://EXAMPLE.com:443/api"), MusicSource.apiId("https://example.com/api/"));
        for (String other : List.of("http://example.com/api/", "https://example.com:8443/api/", "https://example.com/other/"))
            assertNotEquals(MusicSource.apiId("https://example.com/api/"), MusicSource.apiId(other));
    }
    @Test public void missingIdFallsBackToResolvedUrlWhileNumericZeroAndStringIdsAreRealIds() {
        for (String id : List.of("0", "42", "abc-42")) {
            MusicModel parsed = new Gson().fromJson("{\"id\":\"" + id + "\",\"mp3\":\"song.mp3\"}", MusicModel.class);
            parsed.bindApiSource("https://example.com/api/");
            assertTrue(MusicTrackEntity.keyOf(parsed).endsWith(":id:" + id));
        }
        MusicModel numeric = new Gson().fromJson("{\"id\":42}", MusicModel.class);
        assertEquals("42", numeric.getId());
        assertTrue(MusicTrackEntity.keyOf(music("https://example.com/api/", null, "song.mp3"))
                .endsWith(":url:https://example.com/api/song.mp3"));
    }
    @Test public void lateResponseAndRelativeCoverLyricsAudioStayOnIssuingApi() {
        PublishSubject<HttpResult<MusicListModel>> response = PublishSubject.create();
        var result = new SourcedMusicApi((query, pageNumber) -> response, "https://example.com/a/").getMusicList("query").test();
        ApiAddressManager.saveBaseUrl("https://example.com/b/");
        MusicModel track = new MusicModel(); track.setMp3("song.mp3"); track.setImg("cover.jpg"); track.setLrc("lyrics.lrc");
        MusicListModel list = new MusicListModel(); list.setRecords(List.of(track));
        HttpResult<MusicListModel> body = new HttpResult<>(); body.setResult(list); response.onNext(body);
        result.assertValueCount(1).assertNoErrors();
        assertEquals("https://example.com/a/song.mp3", track.getMp3());
        assertEquals("https://example.com/a/cover.jpg", track.getImg());
        assertEquals("https://example.com/a/lyrics.lrc", track.getLrc());
        track.bindApiSource("https://different.example/");
        assertEquals("https://example.com/a/song.mp3", track.getMp3());
    }
    @Test public void apiJsonCannotSupplyClientProvenanceOrStoredKey() {
        MusicModel track = new Gson().fromJson("{\"id\":42,\"mp3\":\"song.mp3\",\"sourceId\":\"local\","
                + "\"sourceBaseUrl\":\"https://forged.example/\",\"storedLibraryKey\":\"old-favorite\"}", MusicModel.class);
        track.bindApiSource("https://example.com/api/");
        assertEquals(MusicSource.apiId("https://example.com/api/"), track.getSourceId());
        assertNull(track.getStoredLibraryKey());
        assertEquals("https://example.com/api/song.mp3", track.getMp3());
    }
    @Test public void modelAndEntityParcelsPreserveEstablishedIdentityAndPlaybackMetadata() {
        MusicModel original = music("https://example.com/api/", "42", "song.mp3");
        original.setLrc("lyrics.lrc"); original.setImg("cover.jpg");
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            MusicModel restored = MusicModel.CREATOR.createFromParcel(parcel);
            assertEquals(MusicTrackEntity.keyOf(original), MusicTrackEntity.keyOf(restored));
            MusicTrackEntity entity = MusicTrackEntity.from(restored);
            parcel.setDataPosition(0); entity.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            MusicTrackEntity copy = MusicTrackEntity.CREATOR.createFromParcel(parcel);
            assertEquals(entity.source, copy.source);
            assertEquals("https://example.com/api/song.mp3", copy.getPlaybackUrl());
            assertEquals(entity.source, MusicTrackEntity.keyOf(copy.toOnlineMusic()));
            assertEquals(original.getLrc(), copy.toOnlineMusic().getLrc());
        } finally { parcel.recycle(); }
    }
    @Test public void localUriKeysAndUnknownLegacyKeysRemainUsableWithoutGuessingSource() {
        String uri = "content://media/external/audio/media/42";
        SongModel song = new SongModel("artist", "song", "album", 1, uri, 60000, 1000);
        MusicTrackEntity local = MusicTrackEntity.from(song);
        assertEquals(uri, local.source); assertEquals(uri, local.getPlaybackUrl()); assertEquals(MusicSource.LOCAL, local.sourceId);
        assertNotEquals(local.source, MusicTrackEntity.keyOf(music("https://example.com/", "42", uri)));
        MusicTrackEntity legacy = new MusicTrackEntity(); legacy.source = "/old.mp3";
        assertEquals("/old.mp3", legacy.getPlaybackUrl());
        assertEquals(legacy.source, MusicTrackEntity.keyOf(legacy.toOnlineMusic()));
        ApiAddressManager.saveBaseUrl("https://new.example/");
        assertEquals("/old.mp3", legacy.toOnlineMusic().getMp3());
    }
}
