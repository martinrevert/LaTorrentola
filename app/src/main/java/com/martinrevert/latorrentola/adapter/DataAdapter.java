package com.martinrevert.latorrentola.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;

import com.martinrevert.latorrentola.PeliActivity;
import com.martinrevert.latorrentola.R;
import com.martinrevert.latorrentola.model.YTS.Movie;

import com.squareup.picasso.Picasso;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Movie> movies;

    public DataAdapter(List<Movie> resultados) {

        this.movies = resultados;

       // Collections.sort(movies, Comparator.comparingInt(Movie::getId).reversed());
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        holder.mTitle.setText(movies.get(position).getTitleLong());
        holder.mSummary.setText(movies.get(position).getSummary());
        Context context = holder.mPoster.getContext();
        Picasso.with(context).load(movies.get(position).getLargeCoverImage()).into(holder.mPoster);

    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mTitle, mSummary;
        private ImageView mPoster;

        public ViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.title);
            mPoster = view.findViewById(R.id.poster);
            mSummary = view.findViewById(R.id.summary);
            Context context = view.getContext();
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                 /*   int itemPosition = getLayoutPosition();
                    int id = movies.get(itemPosition).getId();
                    final Intent intent = new Intent(context, PeliActivity.class);
                    intent.putExtra("id", id);
                    intent.putExtra("imdb", movies.get(itemPosition).getImdb());
                    context.startActivity(intent); */
                    int itemPosition = getLayoutPosition();
                    Movie peli = movies.get(itemPosition);
                    String strPeli = new Gson().toJson(peli);
                    Intent intent = new Intent(context, PeliActivity.class);
                    intent.putExtra("PELI", strPeli);
                    context.startActivity(intent);

                }
            });

        }
    }
}
