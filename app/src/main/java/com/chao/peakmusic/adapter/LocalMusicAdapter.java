package com.chao.peakmusic.adapter;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.databinding.ItemMusicBinding;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.ScanningUtils;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicAdapter extends ListAdapter<SongModel, LocalMusicAdapter.Holder> {

    private onItemClick itemClick;

    public LocalMusicAdapter() {
        super(new DiffUtil.ItemCallback<SongModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull SongModel oldItem,
                                           @NonNull SongModel newItem) {
                return Objects.equals(oldItem.getPath(), newItem.getPath());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SongModel oldItem,
                                              @NonNull SongModel newItem) {
                return Objects.equals(oldItem.getSong(), newItem.getSong())
                        && Objects.equals(oldItem.getSinger(), newItem.getSinger())
                        && Objects.equals(oldItem.getAlbum(), newItem.getAlbum())
                        && oldItem.getAlbumId() == newItem.getAlbumId();
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
        SongModel song = getItem(position);
        holder.title.setText(song.getSong());
        String artist = song.getSinger() == null ? "" : song.getSinger().trim();
        holder.artist.setText(artist.isEmpty() || "<unknown>".equalsIgnoreCase(artist)
                ? holder.itemView.getContext().getString(R.string.local_unknown_artist) : artist);
        Object cover = song.getAlbumId() > 0
                ? ScanningUtils.getInstance(holder.itemView.getContext())
                .getMediaStoreAlbumCoverUri(song.getAlbumId())
                : R.drawable.default_cover;
        ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.cover, cover);
    }

    public void setData(ArrayList<SongModel> data) {
        submitList(data == null ? new ArrayList<>() : new ArrayList<>(data));
    }

    public void setListener(onItemClick itemClick) {
        this.itemClick = itemClick;
    }

    public final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final ImageView cover;

        Holder(ItemMusicBinding binding) {
            super(binding.getRoot());
            title = binding.tvTitle;
            artist = binding.tvArtist;
            cover = binding.ivCover;
            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (itemClick != null && position != RecyclerView.NO_POSITION) {
                    itemClick.itemClickListener(position);
                }
            });
            itemView.setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (itemClick != null && position != RecyclerView.NO_POSITION) {
                    itemClick.itemLongClickListener(position);
                }
                return true;
            });
        }
    }

    public interface onItemClick {
        void itemClickListener(int position);

        void itemLongClickListener(int position);
    }


}
