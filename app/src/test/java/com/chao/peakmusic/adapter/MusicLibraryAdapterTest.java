package com.chao.peakmusic.adapter;

import static org.junit.Assert.*;
import androidx.recyclerview.widget.RecyclerView;
import com.chao.peakmusic.data.MusicTrackEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicLibraryAdapterTest {
    @Test public void selectionUpdatesOnlyCheckboxPayloadsAndChangedRows() {
        MusicLibraryAdapter adapter = new MusicLibraryAdapter();
        MusicTrackEntity a = new MusicTrackEntity(), b = new MusicTrackEntity();
        a.source = "api:a:id:0"; b.source = "api:b:id:0";
        adapter.submitList(List.of(a, b));
        List<String> events = new ArrayList<>();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { fail("Selection must not invalidate the entire data set"); }
            @Override public void onItemRangeChanged(int start, int count, Object payload) {
                assertNotNull(payload); events.add(start + ":" + count);
            }
        });
        adapter.setSelection(true, Set.of(a.source), false);
        assertEquals(List.of("0:2"), events); events.clear();
        adapter.setSelection(true, Set.of(a.source, b.source), false);
        assertEquals(List.of("1:1"), events); events.clear();
        adapter.setSelection(true, Set.of(a.source, b.source), false);
        assertTrue(events.isEmpty());
        adapter.setSelection(true, Set.of(a.source, b.source), true);
        assertEquals(List.of("0:2"), events);
    }
}
