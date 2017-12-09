package com.martinrevert.latorrentola.adapter;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
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

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private List<Movie> movies;
    private AppDatabase db;
    private CompositeDisposable disposable;
    private String type;

    public DataAdapter(List<Movie> resultados, String type) {
        disposable = new CompositeDisposable();
        this.movies = resultados;
        this.type = type;

        // Collections.sort(movies, Comparator.comparingInt(Movie::getId).reversed());
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mTitle;
        private ImageView mPoster;
        private RatingBar mRating;
        private TextView mGenres;
        private ImageButton mMyList;
        private ImageButton mShare;
        private ImageButton mNoMyList;

        ViewHolder(View view) {
            super(view);
            mTitle = view.findViewById(R.id.title);
            mPoster = view.findViewById(R.id.poster);
            mRating = view.findViewById(R.id.rating);
            mGenres = view.findViewById(R.id.genres);
            mMyList = view.findViewById(R.id.imageButtonMyList);
            mNoMyList = view.findViewById(R.id.imageButtonNoMyList);
            mShare = view.findViewById(R.id.imageButtonShare);

        }
    }

    @Override
    public DataAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.mTitle.setText(movies.get(holder.getAdapterPosition()).getTitleLong());
        holder.mRating.setRating(Float.parseFloat(movies.get(holder.getAdapterPosition()).getRating()));
        List<String> generos = movies.get(holder.getAdapterPosition()).getGenres();
        String genres = generos.toString();
        holder.mGenres.setText(genres);
        Context context = holder.mPoster.getContext();
        Picasso.with(context).load(movies.get(holder.getAdapterPosition()).getLargeCoverImage()).into(holder.mPoster);

        db = AppDatabase.getAppDatabase(context);
       /*
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Exception {
                pelidb = db.movieDao().getMovie(peli.getId());
            }
        })
                .subscribeOn(Schedulers.io())
                .subscribe();
*/
        Movie peliposition = movies.get(holder.getAdapterPosition());


        disposable.add(db.movieDao().getMovie(peliposition.getId())
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

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.5f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.5f));
                scaleDown.setDuration(100);

                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);

                scaleDown.start();

                Movie peli = movies.get(holder.getAdapterPosition());
                Runnable loadRunnable = new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        db.movieDao().delete(peli);

                    }
                };
                Thread insertThread = new Thread(loadRunnable);
                insertThread.start();
                if (type.equals("milista")) {
                    movies.remove(holder.getAdapterPosition());
                    //Todo verificar si el +1 se puede evitar con getLayoutPosition()
                    notifyItemRemoved(holder.getAdapterPosition() + 1);
                } else {
                    notifyItemChanged(holder.getAdapterPosition());
                }
            }
        });

        holder.mMyList.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.5f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.5f));
                scaleDown.setDuration(100);

                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);

                scaleDown.start();

                Movie peli = movies.get(holder.getAdapterPosition());
                //holder.mMyList.setVisibility(View.GONE);
                Runnable loadRunnable = new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        db.movieDao().insertMovie(peli);

                    }
                };
                Thread insertThread = new Thread(loadRunnable);
                insertThread.start();
                //holder.mNoMyList.setVisibility(View.VISIBLE);
                notifyItemChanged(holder.getAdapterPosition());

            }
        });


        holder.mShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(view,
                        PropertyValuesHolder.ofFloat("scaleX", 1.2f),
                        PropertyValuesHolder.ofFloat("scaleY", 1.2f));
                scaleDown.setDuration(300);

                scaleDown.setRepeatCount(ObjectAnimator.RESTART);
                scaleDown.setRepeatMode(ObjectAnimator.REVERSE);

                scaleDown.start();

                Movie peli = movies.get(holder.getAdapterPosition());
                String url = "http://www.imdb.com/title/" + peli.getImdbCode();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, url);
                sendIntent.setType("text/plain");
                context.startActivity(Intent.createChooser(sendIntent, "Compartir esta película"));

            }
        });

        holder.itemView.setOnClickListener(view1 -> {

            Movie peli = movies.get(holder.getAdapterPosition());
            String strPeli = new Gson().toJson(peli);
            Intent intent = new Intent(context, PeliActivity.class);
            intent.putExtra("PELI", strPeli);
            context.startActivity(intent);

        });


    }
/*
    @Override
    public void onViewDetachedFromWindow(ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        disposable.dispose();
    }
*/

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        disposable.dispose();
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
