package com.martinrevert.latorrentola.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.martinrevert.latorrentola.R;
import com.martinrevert.latorrentola.model.YTS.Cast;
import com.squareup.picasso.Picasso;

import java.util.List;

public class CastAdapter extends RecyclerView.Adapter<CastAdapter.ViewHolder> {

    private List<Cast> castList;

    public CastAdapter(List<Cast> castList) {
        this.castList = castList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.cast_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Cast cast = castList.get(position);
        holder.name.setText(cast.getName());
        holder.character.setText(cast.getCharacterName());
        
        if (cast.getUrlSmallImage() != null && !cast.getUrlSmallImage().isEmpty()) {
            Picasso.get()
                    .load(cast.getUrlSmallImage())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(holder.image);
        } else {
            holder.image.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }

    @Override
    public int getItemCount() {
        return castList != null ? castList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;
        TextView character;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.cast_image);
            name = itemView.findViewById(R.id.cast_name);
            character = itemView.findViewById(R.id.character_name);
        }
    }
}
