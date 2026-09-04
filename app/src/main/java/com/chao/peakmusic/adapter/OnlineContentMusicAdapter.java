package com.chao.peakmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.utils.ImageLoaderV4;

import java.util.List;

/**
 * Created by Chao on 2017-12-18.
 */

public class OnlineContentMusicAdapter extends RecyclerView.Adapter<OnlineContentMusicAdapter.Holder> {

    private onItemClick itemClick;
    private List<MusicModel> data;

    @Override
    public OnlineContentMusicAdapter.Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(OnlineContentMusicAdapter.Holder holder, int position) {
        holder.tv_title.setText(data.get(position).getName());
        holder.tv_artist.setText(data.get(position).getSinger());
        ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.iv_cover, data.get(position).getImg());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (itemClick != null && adapterPosition != RecyclerView.NO_POSITION) {
                    itemClick.itemClickListener(adapterPosition);
                }
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (itemClick != null && adapterPosition != RecyclerView.NO_POSITION) {
                itemClick.itemLongClickListener(adapterPosition);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public void setData(List<MusicModel> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    public List<MusicModel> getData() {
        return this.data;
    }

    public void setListener(onItemClick itemClick) {
        this.itemClick = itemClick;
    }

    public class Holder extends RecyclerView.ViewHolder {
        public TextView tv_title;
        public TextView tv_artist;
        public ImageView iv_cover;

        public Holder(View itemView) {
            super(itemView);
            tv_title = itemView.findViewById(R.id.tv_title);
            tv_artist = itemView.findViewById(R.id.tv_artist);
            iv_cover = itemView.findViewById(R.id.iv_cover);
        }
    }

    public interface onItemClick {
        void itemClickListener(int position);

        void itemLongClickListener(int position);
    }


}
