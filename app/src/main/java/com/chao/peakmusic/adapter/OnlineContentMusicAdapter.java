package com.chao.peakmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.model.MusicListModel;
import com.chao.peakmusic.utils.ImageLoaderV4;

import java.util.List;

/**
 * Created by Chao on 2017-12-18.
 */

public class OnlineContentMusicAdapter extends RecyclerView.Adapter<OnlineContentMusicAdapter.Holder> {

    private onItemClick itemClick;
    private List<MusicListModel.SongList> data;

    @Override
    public OnlineContentMusicAdapter.Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(OnlineContentMusicAdapter.Holder holder, final int position) {
        holder.tv_title.setText(data.get(position).getTitle());
        holder.tv_artist.setText(data.get(position).getArtist());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (itemClick != null) {
                    itemClick.itemClickListener(position);
                }
            }
        });
        ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.iv_cover, data.get(position).getThumb());
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public void setData(List<MusicListModel.SongList> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    public List<MusicListModel.SongList> getData() {
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
    }


}
