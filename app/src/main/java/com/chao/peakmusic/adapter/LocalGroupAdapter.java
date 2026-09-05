package com.chao.peakmusic.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.chao.peakmusic.R;
import com.chao.peakmusic.databinding.ItemLocalGroupBinding;
import com.chao.peakmusic.local.LocalLibraryIndex.Group;
import com.chao.peakmusic.local.LocalLibraryIndex.Section;
import java.util.Objects;

public final class LocalGroupAdapter extends ListAdapter<Group, LocalGroupAdapter.Holder> {
    private final Listener listener;
    public LocalGroupAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<Group>() {
            @Override public boolean areItemsTheSame(@NonNull Group a, @NonNull Group b) { return a.section == b.section && a.key.equals(b.key); }
            @Override public boolean areContentsTheSame(@NonNull Group a, @NonNull Group b) {
                return a.title.equals(b.title) && Objects.equals(a.artist, b.artist) && a.songs.size() == b.songs.size();
            }
        });
        this.listener = listener;
    }
    public static String title(Context context, Group group) {
        return group.title.isEmpty() ? context.getString(group.section == Section.ARTISTS ? R.string.local_unknown_artist
                : group.section == Section.ALBUMS ? R.string.local_unknown_album : R.string.local_unknown_folder) : group.title;
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemLocalGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Group group = getItem(position); Context context = holder.itemView.getContext();
        holder.binding.localGroupTitle.setText(title(context, group));
        String detail = context.getString(R.string.playlist_track_count, group.songs.size());
        if (group.section == Section.ALBUMS) detail += " · " + (group.artist == null ? context.getString(R.string.local_various_artists)
                : group.artist.isEmpty() ? context.getString(R.string.local_unknown_artist) : group.artist);
        holder.binding.localGroupDetail.setText(detail);
        holder.itemView.setContentDescription(title(context, group) + " · " + detail);
    }
    final class Holder extends RecyclerView.ViewHolder {
        final ItemLocalGroupBinding binding;
        Holder(ItemLocalGroupBinding binding) {
            super(binding.getRoot()); this.binding = binding;
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.open(getItem(position));
            });
        }
    }
    public interface Listener { void open(Group group); }
}
