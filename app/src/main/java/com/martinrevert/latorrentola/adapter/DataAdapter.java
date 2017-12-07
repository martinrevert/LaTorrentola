package com.martinrevert.latorrentola.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.gson.Gson;

import com.martinrevert.latorrentola.PeliActivity;
import com.martinrevert.latorrentola.R;
import com.martinrevert.latorrentola.database.AppDatabase;
import com.martinrevert.latorrentola.database.AppDatabase_Impl;
import com.martinrevert.latorrentola.model.YTS.Movie;

import com.squareup.picasso.Picasso;

import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Movie> movies;

    public DataAdapter(List<Movie> resultados) {

        this.movies = resultados;

        // Collections.sort(movies, Comparator.comparingInt(Movie::getId).reversed());
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mTitle;
        private ImageView mPoster;
        private RatingBar mRating;
        private TextView mGenres;
        private ImageView mMyList;
        private ImageView mShare;

        ViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.title);
            mPoster = view.findViewById(R.id.poster);
            mRating = view.findViewById(R.id.rating);
            mGenres = view.findViewById(R.id.genres);
            mMyList = view.findViewById(R.id.imageButtonMyList);
            mShare = view.findViewById(R.id.imageButtonShare);
            Context context = view.getContext();

            mMyList.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AppDatabase db = AppDatabase.getAppDatabase(context);
                    int itemPosition = getLayoutPosition();
                    Movie peli = movies.get(itemPosition);
                    db.movieDao().insertAll(peli);

                }
            });


            mShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int itemPosition = getLayoutPosition();
                    Movie peli = movies.get(itemPosition);
                    String url = "http://www.imdb.com/title/" + peli.getImdbCode();
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, url);
                    sendIntent.setType("text/plain");
                    context.startActivity(Intent.createChooser(sendIntent, "Compartir esta película"));


                }
            });


            view.setOnClickListener(view1 -> {
                int itemPosition = getLayoutPosition();
                Movie peli = movies.get(itemPosition);
                String strPeli = new Gson().toJson(peli);
                Intent intent = new Intent(context, PeliActivity.class);
                intent.putExtra("PELI", strPeli);
                context.startActivity(intent);

            });

        }
    }


    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mTitle.setText(movies.get(position).getTitleLong());
        holder.mRating.setRating(Float.parseFloat(movies.get(position).getRating()));
        List<String> generos = movies.get(position).getGenres();
        String genres = generos.toString();
        holder.mGenres.setText(genres);
        Context context = holder.mPoster.getContext();
        Picasso.with(context).load(movies.get(position).getLargeCoverImage()).into(holder.mPoster);

    }

    @Override
    public int getItemCount() {
        try {
            return movies.size();
        } catch (NullPointerException e) {
            e.printStackTrace();
            return 0;
        }
    }


}
