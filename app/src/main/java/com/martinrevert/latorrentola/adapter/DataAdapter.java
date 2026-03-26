package com.martinrevert.latorrentola.adapter;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.gson.Gson;

import com.martinrevert.latorrentola.PeliActivity;
import com.martinrevert.latorrentola.R;
import com.martinrevert.latorrentola.database.AppDatabase;
import com.martinrevert.latorrentola.model.YTS.Movie;

import com.squareup.picasso.Picasso;

import java.util.Date;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Movie> movies;
    private AppDatabase db;
    private CompositeDisposable disposable;
    private String type;
    private Date lastVisitDate;

    public DataAdapter(List<Movie> resultados, String type) {
        disposable = new CompositeDisposable();
        this.movies = resultados;
        this.type = type;
    }

    public void addMovies(List<Movie> newmovies) {
        movies.addAll(newmovies);
        notifyDataSetChanged();
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        notifyDataSetChanged();
    }

    public void setLastVisitDate(Date date) {
        this.lastVisitDate = date;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mTitle;
        private ImageView mPoster;
        private RatingBar mRating;
        private TextView mGenres;
        private ImageButton mMyList;
        private ImageButton mShare;
        private ImageButton mNoMyList;
        private ImageView mNewBadge;

        ViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.title);
            mPoster = view.findViewById(R.id.poster);
            mRating = view.findViewById(R.id.rating);
            mGenres = view.findViewById(R.id.genres);
            mMyList = view.findViewById(R.id.imageButtonMyList);
            mNoMyList = view.findViewById(R.id.imageButtonNoMyList);
            mShare = view.findViewById(R.id.imageButtonShare);
            mNewBadge = view.findViewById(R.id.new_badge);

        }
    }

    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Movie peli = movies.get(holder.getAdapterPosition());
        holder.mTitle.setText(peli.getTitleLong());
        holder.mRating.setRating(Float.parseFloat(peli.getRating()));
        
        List<String> generos = peli.getGenres();
        String genres = (generos == null) ? "Sin genero" : generos.toString();
        holder.mGenres.setText(genres);
        
        Context context = holder.mPoster.getContext();
        Picasso.get().load(peli.getLargeCoverImage()).into(holder.mPoster);

        db = AppDatabase.getAppDatabase(context);

        // Badge logic
        if (lastVisitDate != null && peli.getDateUploadedUnix() != null) {
            long movieUploadTime = peli.getDateUploadedUnix() * 1000; // Unix time to milliseconds
            if (movieUploadTime > lastVisitDate.getTime()) {
                holder.mNewBadge.setVisibility(View.VISIBLE);
            } else {
                holder.mNewBadge.setVisibility(View.GONE);
            }
        } else {
            holder.mNewBadge.setVisibility(View.GONE);
        }

        disposable.add(db.movieDao().getMovie(peli.getId())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(res -> {
                            holder.mNoMyList.setVisibility(View.VISIBLE);
                            holder.mMyList.setVisibility(View.GONE);
                        },
                        throwable -> {
                            holder.mMyList.setVisibility(View.VISIBLE);
                            holder.mNoMyList.setVisibility(View.GONE);
                        })
        );


        holder.mNoMyList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.5f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.5f));
                scaleDown.setDuration(100);
                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
                scaleDown.start();

                Movie peliToDelete = movies.get(pos);
                new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    db.movieDao().delete(peliToDelete);
                }).start();

                if (type != null && type.equals("milista")) {
                    movies.remove(pos);
                    notifyItemRemoved(pos);
                } else {
                    notifyItemChanged(pos);
                }
            }
        });

        holder.mMyList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.5f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.5f));
                scaleDown.setDuration(100);
                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
                scaleDown.start();

                Movie peliToInsert = movies.get(pos);
                new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    db.movieDao().insertMovie(peliToInsert);
                }).start();
                notifyItemChanged(pos);
            }
        });

        holder.mShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.2f));
                scaleDown.setDuration(300);
                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
                scaleDown.start();

                Movie peliToShare = movies.get(pos);
                String url = "http://www.imdb.com/title/" + peliToShare.getImdbCode();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, url);
                sendIntent.setType("text/plain");
                context.startActivity(Intent.createChooser(sendIntent, "Compartir esta película"));
            }
        });

        holder.itemView.setOnClickListener(view1 -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            Movie peliToDetail = movies.get(pos);
            String strPeli = new Gson().toJson(peliToDetail);
            Intent detailIntent = new Intent(context, PeliActivity.class);
            detailIntent.putExtra("PELI", strPeli);
            context.startActivity(detailIntent);
        });
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        disposable.dispose();
    }

    @Override
    public int getItemCount() {
        return (movies == null) ? 0 : movies.size();
    }
}
