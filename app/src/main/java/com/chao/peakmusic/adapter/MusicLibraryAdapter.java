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
                        && safe(oldItem.imageUrl).equals(safe(newItem.imageUrl));
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
        holder.title.setText(track.name);
        holder.artist.setText(track.artist);
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

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final ImageView cover;

        Holder(ItemMusicBinding binding) {
            super(binding.getRoot());
            title = binding.tvTitle;
            artist = binding.tvArtist;
            cover = binding.ivCover;
        }
    }

    public interface Listener {
        void onClick(int position, MusicTrackEntity track);

        void onLongClick(int position, MusicTrackEntity track);
    }
}
