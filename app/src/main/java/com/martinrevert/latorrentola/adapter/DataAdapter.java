package com.martinrevert.latorrentola.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.martinrevert.latorrentola.model.Movie;
import com.martinrevert.latorrentola.R;
import com.martinrevert.latorrentola.model.Results;
import com.squareup.picasso.Picasso;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Movie> movies;

    public DataAdapter(Results movieList) {

        movies = movieList.getMovies();

        Collections.sort(movies, Comparator.comparingInt(Movie::getId).reversed());
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        holder.mTitle.setText(movies.get(position).getTitle());
        holder.mImdb.setText(movies.get(position).getImdb());
        holder.mSummary.setText(movies.get(position).getSummary());

        Context context = holder.mPoster.getContext();
        Picasso.with(context).load(movies.get(position).getPoster()).into(holder.mPoster);

    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mTitle, mImdb, mSummary;
        private ImageView mPoster;

        public ViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.title);
            mPoster = view.findViewById(R.id.poster);
            mSummary = view.findViewById(R.id.summary);
            mImdb = view.findViewById(R.id.imdb);


        }
    }
}
