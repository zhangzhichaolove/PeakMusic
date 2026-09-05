package com.chao.peakmusic.catalog;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.chao.peakmusic.R;

/** Shared catalogue/search paging feedback; an append failure never hides the existing songs. */
public final class MusicPageFooter {
    private final View root;
    private final TextView message;
    private final Button more;
    public MusicPageFooter(View parent, Runnable loadNext) {
        root = parent.findViewById(R.id.music_page_footer);
        message = root.findViewById(R.id.music_page_message);
        more = root.findViewById(R.id.music_page_more);
        more.setOnClickListener(view -> loadNext.run());
    }
    public void render(MusicPageController pages) {
        boolean visible = pages != null && (!pages.songs().isEmpty() || pages.state() == MusicPageController.State.EMPTY && pages.hasMore());
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        boolean loading = pages.state() == MusicPageController.State.LOADING_MORE;
        boolean failed = pages.state() == MusicPageController.State.MORE_ERROR;
        String count = root.getContext().getString(pages.total() > 0 ? R.string.music_page_count_total : R.string.music_page_count,
                pages.songs().size(), pages.total());
        message.setText(failed ? root.getContext().getString(R.string.music_page_failed) : count);
        more.setText(loading ? R.string.music_loading : failed ? R.string.retry : R.string.music_load_more);
        more.setEnabled(!loading);
        more.setVisibility(pages.hasMore() ? View.VISIBLE : View.GONE);
    }
}
