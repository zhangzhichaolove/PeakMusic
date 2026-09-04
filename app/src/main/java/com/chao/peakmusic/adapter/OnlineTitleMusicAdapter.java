package com.chao.peakmusic.adapter;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.databinding.ItemMusicTitleBinding;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.utils.ImageLoaderV4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Created by Chao on 2017-12-18.
 */

public class OnlineTitleMusicAdapter extends ListAdapter<MusicModel, OnlineTitleMusicAdapter.Holder> {

    private onItemClick itemClick;

    public OnlineTitleMusicAdapter() {
        super(new DiffUtil.ItemCallback<MusicModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull MusicModel oldItem,
                                           @NonNull MusicModel newItem) {
                if (oldItem.getId() > 0 || newItem.getId() > 0) {
                    return oldItem.getId() == newItem.getId();
                }
                return Objects.equals(oldItem.getMp3(), newItem.getMp3());
            }

            @Override
            public boolean areContentsTheSame(@NonNull MusicModel oldItem,
                                              @NonNull MusicModel newItem) {
                return Objects.equals(oldItem.getName(), newItem.getName())
                        && Objects.equals(oldItem.getSinger(), newItem.getSinger())
                        && Objects.equals(oldItem.getImg(), newItem.getImg());
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMusicTitleBinding.inflate(
                android.view.LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MusicModel music = getItem(position);
        holder.title.setText(music.getName());
        holder.artist.setText(music.getSinger());
        ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.cover, music.getImg());
    }

    public void setData(List<MusicModel> data) {
        submitList(data == null ? Collections.emptyList() : new ArrayList<>(data));
    }

    public void setListener(onItemClick itemClick) {
        this.itemClick = itemClick;
    }

    public final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final ImageView cover;

        Holder(ItemMusicTitleBinding binding) {
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
        }
    }

    public interface onItemClick {
        void itemClickListener(int position);
    }


}
