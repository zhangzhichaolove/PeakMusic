package com.chao.peakmusic.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.R;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.utils.ToastUtils;

import java.util.ArrayList;

/** Versioned, bounded queue pages: edits never apply old row indices to a newer queue. */
public class MusicQueueActivity extends AppCompatActivity {
    private MusicAidlInterface service;
    private boolean bound;
    private int offset, total, current = -1;
    private long version = -1;
    private RecyclerView list;
    private TextView status;
    private ArrayList<String> names = new ArrayList<>(), artists = new ArrayList<>();
    private final QueueAdapter adapter = new QueueAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            try {
                if (service != null && service.getQueueVersion() != version) loadPage();
            } catch (RemoteException error) { disconnected(); }
            handler.postDelayed(this, 1000);
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = MusicAidlInterface.Stub.asInterface(binder);
            loadPage();
        }
        @Override public void onServiceDisconnected(ComponentName name) { disconnected(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        BarUtils.setWindow(this);
        setContentView(R.layout.activity_music_queue);
        Toolbar toolbar = findViewById(R.id.queue_toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.playback_queue);
        toolbar.setNavigationIcon(R.drawable.menu_setting_back);
        toolbar.setNavigationOnClickListener(view -> finish());
        BarUtils.applyPageInsets(findViewById(android.R.id.content), toolbar);
        list = findViewById(R.id.queue_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        status = findViewById(R.id.queue_status);
        if (state != null) offset = state.getInt("offset", 0);
        findViewById(R.id.queue_previous_page).setOnClickListener(view -> changePage(offset - MusicService.QUEUE_PAGE_SIZE));
        findViewById(R.id.queue_next_page).setOnClickListener(view -> changePage(offset + MusicService.QUEUE_PAGE_SIZE));
        updateNavigation();
    }

    @Override protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) disconnected();
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        if (bound) unbindService(connection);
        bound = false;
        service = null;
        super.onStop();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("offset", offset);
        super.onSaveInstanceState(state);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.queue, menu);
        return true;
    }

    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_queue_clear).setEnabled(service != null && total > 0);
        menu.findItem(R.id.action_queue_current).setEnabled(service != null && current >= 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_queue_current) {
            if (service != null) {
                loadPage();
                changePage(Math.max(0, current) / MusicService.QUEUE_PAGE_SIZE * MusicService.QUEUE_PAGE_SIZE);
                if (current >= offset) list.scrollToPosition(current - offset);
            }
            return true;
        }
        if (item.getItemId() == R.id.action_queue_clear) {
            long expected = version;
            new AlertDialog.Builder(this).setMessage(R.string.queue_clear_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear, (dialog, which) -> edit(s -> s.clearQueue(expected))).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void changePage(int nextOffset) {
        offset = nextOffset;
        loadPage();
        list.scrollToPosition(0);
    }

    private void loadPage() {
        if (service == null) return;
        try {
            Bundle page = service.getQueuePage(offset);
            offset = page.getInt("offset"); total = page.getInt("total");
            current = page.getInt("current"); version = page.getLong("version");
            int oldSize = names.size();
            names = page.getStringArrayList("names"); artists = page.getStringArrayList("artists");
            int common = Math.min(oldSize, names.size());
            // All retained rows receive the new edit version, including unchanged labels.
            if (common > 0) adapter.notifyItemRangeChanged(0, common);
            if (oldSize > names.size()) adapter.notifyItemRangeRemoved(names.size(), oldSize - names.size());
            if (names.size() > oldSize) adapter.notifyItemRangeInserted(oldSize, names.size() - oldSize);
            status.setText(total == 0 ? getString(R.string.queue_empty)
                    : getString(R.string.queue_page_summary, total, offset + 1, offset + names.size()));
            updateNavigation();
        } catch (RemoteException error) { disconnected(); }
    }

    private void updateNavigation() {
        findViewById(R.id.queue_previous_page).setEnabled(service != null && offset > 0);
        findViewById(R.id.queue_next_page).setEnabled(service != null && offset + names.size() < total);
        invalidateOptionsMenu();
    }

    private void disconnected() {
        service = null;
        status.setText(R.string.queue_connection_failed);
        updateNavigation();
    }

    private void edit(Edit operation) {
        if (service == null) { disconnected(); return; }
        try {
            if (!operation.apply(service)) ToastUtils.showToast(getString(R.string.queue_changed));
            loadPage();
        } catch (RemoteException error) { disconnected(); }
    }

    private void actions(int index, String name, long expected) {
        int selectedCurrent = current;
        new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{
                getString(R.string.queue_play_next), getString(R.string.queue_move_up),
                getString(R.string.queue_move_down), getString(R.string.queue_move_to),
                getString(R.string.queue_remove)}, (dialog, which) -> {
            if (which == 0) {
                int target = index == selectedCurrent ? index
                        : index < selectedCurrent ? selectedCurrent : selectedCurrent + 1;
                edit(s -> s.moveQueueItem(index, Math.max(0, target), expected));
            } else if (which == 1) edit(s -> s.moveQueueItem(index, Math.max(0, index - 1), expected));
            else if (which == 2) edit(s -> s.moveQueueItem(index, Math.min(total - 1, index + 1), expected));
            else if (which == 3) moveTo(index, expected);
            else edit(s -> s.removeQueueItem(index, expected));
        }).show();
    }

    private void moveTo(int index, long expected) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.queue_position_hint, total));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.queue_move_to).setView(input)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int target;
            try { target = Integer.parseInt(input.getText().toString().trim()) - 1; }
            catch (NumberFormatException error) { target = -1; }
            if (target < 0 || target >= total) { input.setError(getString(R.string.queue_position_hint, total)); return; }
            int destination = target;
            edit(s -> s.moveQueueItem(index, destination, expected));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private interface Edit { boolean apply(MusicAidlInterface service) throws RemoteException; }

    private final class QueueAdapter extends RecyclerView.Adapter<QueueHolder> {
        @NonNull @Override public QueueHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new QueueHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull QueueHolder holder, int position) {
            int index = offset + position;
            long expected = version;
            String name = names.get(position), artist = artists.get(position);
            holder.name.setText(getString(R.string.queue_track_number, index + 1, name));
            holder.artist.setText(index == current ? getString(R.string.queue_current_artist, artist) : artist);
            holder.track.setOnClickListener(view -> edit(s -> s.playQueueItem(index, expected)));
            holder.actions.setContentDescription(getString(R.string.queue_item_actions_for, name));
            holder.actions.setOnClickListener(view -> actions(index, name, expected));
        }
        @Override public int getItemCount() { return names.size(); }
    }

    private static final class QueueHolder extends RecyclerView.ViewHolder {
        final TextView name, artist;
        final View track, actions;
        QueueHolder(View view) {
            super(view);
            name = view.findViewById(R.id.queue_track_name); artist = view.findViewById(R.id.queue_track_artist);
            track = view.findViewById(R.id.queue_track); actions = view.findViewById(R.id.queue_item_actions);
        }
    }
}
