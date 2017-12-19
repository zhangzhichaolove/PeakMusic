package com.chao.peakmusic.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.model.SongModel;

import java.util.ArrayList;

/**
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicAdapter extends RecyclerView.Adapter<LocalMusicAdapter.Holder> {

    private ArrayList<SongModel> data;

    @Override
    public LocalMusicAdapter.Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(LocalMusicAdapter.Holder holder, int position) {
        holder.tv_title.setText(data.get(position).getSong());
        holder.tv_artist.setText(data.get(position).getSinger().equals("<unknown>") ? data.get(position).getAlbum() : data.get(position).getSinger());
        //ImageLoaderV4.getInstance().load(holder.itemView.getContext(), holder.iv_cover, Uri.parse("content://media/external/audio/media/" + data.get(position).getAlbumId() + "/albumart"));
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    public void setData(ArrayList<SongModel> data) {
        this.data = data;
        notifyDataSetChanged();
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


}
