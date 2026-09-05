package com.chao.peakmusic.local;

import static org.junit.Assert.*;
import com.chao.peakmusic.local.LocalLibraryIndex.Section;
import com.chao.peakmusic.model.SongModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocalLibraryIndexTest {
    @Test public void artistGroupsNormalizeUnknownsDeduplicateUrisAndKeepScanOrder() {
        SongModel a = song("a", " Artist ", "Album", 1), b = song("b", "Artist", "Album", 1);
        SongModel unknown = song("c", "<unknown>", null, 0), blank = song("d", null, null, 0);
        List<SongModel> input = new ArrayList<>(List.of(b, a, unknown, blank, a));
        LocalLibraryIndex index = new LocalLibraryIndex(input); input.clear();
        assertEquals(4, index.all.size());
        assertEquals(2, index.groups(Section.ARTISTS).size());
        assertEquals(List.of(b, a), index.find(Section.ARTISTS, "Artist").songs);
        assertEquals(List.of(unknown, blank), index.find(Section.ARTISTS, "").songs);
        assertThrows(UnsupportedOperationException.class, () -> index.all.clear());
    }

    @Test public void sameTitleAlbumsUseIdAndVolumeAndCompilationStaysTogether() {
        SongModel a = song("a", "Artist A", "Same album", 7), b = song("b", "Artist B", "Same album", 7);
        SongModel c = song("c", "Artist A", "Same album", 8), d = song("d", "Artist A", "Same album", 7);
        a.setVolumeName("primary"); b.setVolumeName("primary"); c.setVolumeName("primary"); d.setVolumeName("sdcard");
        LocalLibraryIndex index = new LocalLibraryIndex(List.of(a, b, c, d));
        assertEquals(3, index.groups(Section.ALBUMS).size());
        assertEquals(List.of(a, b), index.find(Section.ALBUMS, "primary:id:7").songs);
        assertNull(index.find(Section.ALBUMS, "primary:id:7").artist);
        assertEquals(List.of(d), index.find(Section.ALBUMS, "sdcard:id:7").songs);
    }

    @Test public void albumFallbackDoesNotMergeDifferentArtistsOrAmbiguousSeparators() {
        SongModel a = song("a", "B:C", "A", 0), b = song("b", "C", "A:B", 0);
        SongModel c = song("c", "Other", "A", 0), d = song("d", "B:C", "A", 0);
        LocalLibraryIndex index = new LocalLibraryIndex(List.of(a, b, c, d));
        assertEquals(3, index.groups(Section.ALBUMS).size());
        assertEquals(2, index.find(Section.ALBUMS, "name:1:A:B:C").songs.size());
    }

    @Test public void fullFolderAndStorageVolumeAvoidSameLeafCollisionsWithoutRawPath() {
        SongModel a = song("a", null, null, 0), b = song("b", null, null, 0);
        a.setFilePath("/storage/music/first/Disc/a.mp3"); b.setFilePath("/storage/music/second/Disc/b.mp3");
        SongModel c = song("c", null, null, 0), d = song("d", null, null, 0), unknown = song("e", null, null, 0);
        c.setVolumeName("primary"); c.setRelativePath("Music/Disc/");
        d.setVolumeName("sdcard"); d.setRelativePath("Music/Disc/");
        LocalLibraryIndex index = new LocalLibraryIndex(List.of(a, b, c, d, unknown));
        assertEquals(5, index.groups(Section.FOLDERS).size());
        assertEquals(List.of(a), index.find(Section.FOLDERS, "/storage/music/first/Disc").songs);
        assertEquals(List.of(c), index.find(Section.FOLDERS, "primary:/Music/Disc").songs);
        assertEquals(List.of(d), index.find(Section.FOLDERS, "sdcard:/Music/Disc").songs);
        assertEquals(List.of(unknown), index.find(Section.FOLDERS, "").songs);
    }

    @Test public void parcelPreservesScopedStorageDirectoryColumns() {
        SongModel track = song("a", "Artist", "Album", 10);
        track.setVolumeName("external_primary"); track.setRelativePath("Music/Album/");
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            track.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            SongModel copy = SongModel.CREATOR.createFromParcel(parcel);
            assertEquals(track.getVolumeName(), copy.getVolumeName());
            assertEquals(track.getRelativePath(), copy.getRelativePath());
            assertEquals("external_primary:/Music/Album", LocalLibraryIndex.directory(copy));
        } finally { parcel.recycle(); }
    }

    @Test public void tenThousandSongsArePartitionedOncePerCategory() {
        List<SongModel> songs = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            SongModel track = song("item" + i, "Artist " + i % 40, "Album " + i % 100, i % 100 + 1);
            track.setVolumeName("primary"); track.setRelativePath("Music/folder-" + i % 20 + "/"); songs.add(track);
        }
        LocalLibraryIndex index = new LocalLibraryIndex(songs);
        assertEquals(10000, index.all.size());
        assertEquals(40, index.groups(Section.ARTISTS).size());
        assertEquals(100, index.groups(Section.ALBUMS).size());
        assertEquals(20, index.groups(Section.FOLDERS).size());
        for (Section section : new Section[]{Section.ARTISTS, Section.ALBUMS, Section.FOLDERS}) {
            int count = 0;
            for (LocalLibraryIndex.Group group : index.groups(section)) count += group.songs.size();
            assertEquals(10000, count);
        }
    }

    private SongModel song(String uri, String artist, String album, long albumId) {
        return new SongModel(artist, uri, album, albumId, "content://media/external/audio/media/" + uri, 1000, 100);
    }
}
