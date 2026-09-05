package com.chao.peakmusic.adapter;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.databinding.ItemMusicBinding;
import com.chao.peakmusic.utils.ImageLoaderV4;

public class MusicLibraryAdapter extends ListAdapter<MusicTrackEntity, MusicLibraryAdapter.Holder> {
    private Listener listener;
    private boolean selecting;
    private boolean selectionLocked;
    private java.util.Set<String> selected = java.util.Collections.emptySet();
    private static final Object SELECTION = new Object();

    public void setSelection(boolean selecting, java.util.Set<String> keys, boolean locked) {
        java.util.Set<String> copy = new java.util.HashSet<>(keys);
        if (this.selecting == selecting && selected.equals(copy) && selectionLocked == locked) return;
        boolean all = this.selecting != selecting || selectionLocked != locked;
        java.util.Set<String> previous = selected;
        this.selecting = selecting; selected = copy; selectionLocked = locked;
        if (all) notifyItemRangeChanged(0, getItemCount(), SELECTION);
        else for (int i = 0; i < getItemCount(); i++)
            if (previous.contains(getItem(i).source) != selected.contains(getItem(i).source))
                notifyItemChanged(i, SELECTION);
    }

    public MusicLibraryAdapter() {
        super(new DiffUtil.ItemCallback<MusicTrackEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull MusicTrackEntity oldItem,
                                           @NonNull MusicTrackEntity newItem) {
                return oldItem.source.equals(newItem.source);
            }

            @Override
            public boolean areContentsTheSame(@NonNull MusicTrackEntity oldItem,
                                              @NonNull MusicTrackEntity newItem) {
                return safe(oldItem.name).equals(safe(newItem.name))
                        && safe(oldItem.artist).equals(safe(newItem.artist))
                        && safe(oldItem.imageUrl).equals(safe(newItem.imageUrl))
                        && safe(oldItem.sourceId).equals(safe(newItem.sourceId))
                        && safe(oldItem.sourceBaseUrl).equals(safe(newItem.sourceBaseUrl))
                        && oldItem.local == newItem.local;
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMusicBinding.inflate(
                android.view.LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MusicTrackEntity track = getItem(position);
        bindSelection(holder, track);
        holder.selection.setContentDescription(holder.itemView.getContext().getString(R.string.select_track, track.name));
        holder.selection.setOnClickListener(view -> {
            int index = holder.getBindingAdapterPosition();
            if (listener != null && index != RecyclerView.NO_POSITION) listener.onClick(index, getItem(index));
        });
        holder.title.setText(track.name);
        holder.artist.setText(track.artist);
        holder.source.setText(com.chao.peakmusic.utils.MusicSourceLabels.label(holder.itemView.getContext(), track));
        holder.source.setVisibility(android.view.View.VISIBLE);
        ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.cover,
                track.imageUrl == null || track.imageUrl.isEmpty()
                        ? R.drawable.default_cover : track.imageUrl);
        holder.itemView.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onClick(adapterPosition, getItem(adapterPosition));
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition != RecyclerView.NO_POSITION) {
                listener.onLongClick(adapterPosition, getItem(adapterPosition));
            }
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull java.util.List<Object> payloads) {
        if (payloads.contains(SELECTION)) bindSelection(holder, getItem(position));
        else onBindViewHolder(holder, position);
    }

    private void bindSelection(Holder holder, MusicTrackEntity track) {
        holder.selection.setVisibility(selecting ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.selection.setChecked(selected.contains(track.source));
        holder.selection.setEnabled(!selectionLocked);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final android.widget.CheckBox selection;
        final TextView source;
        final ImageView cover;

        Holder(ItemMusicBinding binding) {
            super(binding.getRoot());
            title = binding.tvTitle;
            artist = binding.tvArtist;
            selection = binding.trackSelected;
            source = binding.tvSource;
            cover = binding.ivCover;
        }
    }

    public interface Listener {
        void onClick(int position, MusicTrackEntity track);

        void onLongClick(int position, MusicTrackEntity track);
    }
}
