package com.martinrevert.latorrentola;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;


import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.android.youtube.player.YouTubePlayerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.argenteam.MovieDetails;
import com.martinrevert.latorrentola.model.argenteam.Results;
import com.martinrevert.latorrentola.network.RequestArgenteamInterface;
import com.martinrevert.latorrentola.network.RequestYTSInterface;

import java.lang.reflect.Type;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class PeliActivity extends AppCompatActivity implements YouTubePlayer.OnInitializedListener {

    String imdb;
    String youtube_video_trailer;
    private CompositeDisposable mCompositeDisposable;
    YouTubePlayerFragment youTubePlayerFragment;
    Movie movie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peli);

        youTubePlayerFragment = (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_player_view);

        Bundle bundle = getIntent().getExtras();
        String peliStr = null;
        if (bundle != null) {
            peliStr = bundle.getString("PELI" + "");
            Gson gson = new Gson();
            Type type = new TypeToken<Movie>() {
            }.getType();

            movie = gson.fromJson(peliStr, type);

            imdb = movie.getImdbCode().substring(2);

            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitle(movie.getTitle());
            setSupportActionBar(toolbar);

            youtube_video_trailer = movie.getYtTrailerCode();

            Log.v("YTS", movie.getTitleLong());

            youTubePlayerFragment.initialize(Constants.YOUTUBE_API_KEY, this);

            mCompositeDisposable = new CompositeDisposable();

            loadJSON();
        } else {

        }

    }

    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player,
                                        boolean wasRestored) {
        if (!wasRestored) {
            player.cueVideo(youtube_video_trailer);
        }
    }

    @Override
    public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

    }



    private void loadJSON() {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        // add your other interceptors …
        // add logging as last interceptor
        httpClient.addInterceptor(logging);  // <-- this is the important line!

        RequestArgenteamInterface requestArgenteamInterface = new Retrofit.Builder()
                .baseUrl(Constants.ARGENTEAM_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build().create(RequestArgenteamInterface.class);

        mCompositeDisposable.add(requestArgenteamInterface.getMovie(imdb)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::handleResponse, this::handleError));

    }

    private void handleResponse(Results moviedetails) {
        com.martinrevert.latorrentola.model.argenteam.Movie peli;
        peli = moviedetails.getMovies().get(0);
        String titulo = peli.getTitle();
        Log.v("titulo", titulo);


    }

    private void handleError(Throwable error) {

        Log.v("ERRORARGENTEAM", error.getLocalizedMessage());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.clear();
    }
}

