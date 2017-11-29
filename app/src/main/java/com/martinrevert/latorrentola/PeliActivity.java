package com.martinrevert.latorrentola;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerFragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.Torrent;
import com.martinrevert.latorrentola.model.argenteam.MovieDetails;
import com.martinrevert.latorrentola.model.argenteam.Release;
import com.martinrevert.latorrentola.model.argenteam.Results;
import com.martinrevert.latorrentola.network.RequestArgenteamInterface;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
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
    RequestArgenteamInterface requestArgenteamInterface;

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

            List <Torrent> torrentsyts = movie.getTorrents();

            for (Torrent torroyts : torrentsyts){




                String text = "YTS" + " " + torroyts.getQuality()+ " " + torroyts.getSize();

                LinearLayout linearyts = findViewById(R.id.linearyts);

                Button btntorrentyts = new Button(PeliActivity.this);
                btntorrentyts.setText(text);
                btntorrentyts.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String hash = torroyts.getHash();
                        String uriyts = null;
                        try {
                            uriyts = "magnet:?xt=urn:btih:" + hash +"&dn="+ URLEncoder.encode(movie.getTitle(),"UTF-8")+"&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80";
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        Intent sharingIntent = new Intent(
                                android.content.Intent.ACTION_VIEW);
                        sharingIntent
                                .addCategory(android.content.Intent.CATEGORY_BROWSABLE);
                        sharingIntent.setData(Uri.parse(uriyts));
                        startActivity(sharingIntent);
                    }
                });
                btntorrentyts.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                linearyts.addView(btntorrentyts);











            }

            loadJSONargenteam();
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


    private void loadJSONargenteam() {

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        // add your other interceptors …
        // add logging as last interceptor
        httpClient.addInterceptor(logging);  // <-- this is the important line!


        requestArgenteamInterface = new Retrofit.Builder()
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

    private void handleResponse(Results movieId) {
        com.martinrevert.latorrentola.model.argenteam.Movie peli;
        peli = movieId.getMovies().get(0);
        Integer id = peli.getId();
        Log.v("id", id.toString());

        mCompositeDisposable.add(requestArgenteamInterface.getMovieId(id)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::handleResponseId, this::handleErrorId));

    }

    private void handleResponseId(MovieDetails movieat) {

        //ToDo me parece que este listiterator me está jodiendo la vida
        //ver si se puede hacer con manipulación normal de pojos y un for

        List<Release> releases = movieat.getReleases();

        for (Release rel : releases) {

            if (rel.getTorrents() != null) {
                Uri uri = Uri.parse(rel.getTorrents().get(0).getUri());
                String codec = rel.getCodec();
                String tags = rel.getTags();
                String source = rel. getSource();
                String size = rel.getSize();
                Log.v("CODEC", codec + " " + tags + " " + source + " " +  size);

                LinearLayout linearargenteam = findViewById(R.id.linearargenteam);

                Button btntorrent = new Button(PeliActivity.this);
                btntorrent.setText(source + " " + codec + " " + tags +" " + size);
                btntorrent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent sharingIntent = new Intent(
                                android.content.Intent.ACTION_VIEW);
                        sharingIntent
                                .addCategory(android.content.Intent.CATEGORY_BROWSABLE);
                        sharingIntent.setData(uri);
                        startActivity(sharingIntent);
                    }
                });
                btntorrent.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                linearargenteam.addView(btntorrent);


            } else {
                Log.v("RELEASE SIN TORRENT", "NO TORRENT");
            }

        }


    }


    private void handleErrorId(Throwable throwable) {
        Log.v("ERRORARGENTEAMID", throwable.getLocalizedMessage());
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

