package com.martinrevert.latorrentola;

import android.app.SearchManager;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import com.martinrevert.latorrentola.adapter.DataAdapter;
import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.database.AppDatabase;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.MovieDetails;
import com.martinrevert.latorrentola.network.RequestYTSInterface;

import java.util.List;
import java.util.Locale;

import io.fabric.sdk.android.Fabric;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class SearchableActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private RecyclerView mRecyclerView;
    private ProgressBar progressBar;
    private CompositeDisposable compositeDisposable;
    private DataAdapter mAdapter;
    private TextView empty;

    private TextToSpeech tts;
    private String query = null;
    private Intent intent;

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searchable);
        Fabric.with(this, new Crashlytics());
        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.getDefault());
        progressBar = findViewById(R.id.progressBar);
        empty = findViewById(R.id.empty);
        empty.setText("No encontré movies con ese criterio");

        Toolbar toolbar = findViewById(R.id.toolbar);

        db = AppDatabase.getAppDatabase(this);
        compositeDisposable = new CompositeDisposable();
        // Get the intent, verify the action and get the query

        intent = getIntent();

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            toolbar.setTitle(query);
            Log.v("BUSCAR", query);
        } else {
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                query = bundle.getString("GENRE", "");
            }
        }
        setSupportActionBar(toolbar);
        initRecyclerView();
        loadJSON(query);

    }

    private void initRecyclerView() {

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new DataAdapter(null, null);
        mRecyclerView.setAdapter(mAdapter);

    }

    private void loadJSON(String query) {

        progressBar.setVisibility(VISIBLE);
        RequestYTSInterface requestYTSInterface = new Retrofit.Builder()
                .baseUrl(Constants.YTS_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestYTSInterface.class);

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            compositeDisposable.add(requestYTSInterface.getMovieSearch("50", query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(this::handleResponse, this::handleError));
        } else {
            switch (query) {
                case "3D":
                    compositeDisposable.add(requestYTSInterface.getTridiSearch("50", query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(this::handleResponse, this::handleError));
                    break;
                case "milista":
                    compositeDisposable.add(db.movieDao().getAll()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(this::handleDBResponse, this::handleDBError));

                    break;
                default:
                    compositeDisposable.add(requestYTSInterface.getGenreSearch("50", query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(this::handleResponse, this::handleError));
                    break;
            }
        }

    }

    private void handleDBResponse(List<Movie> pelisdb) {
        progressBar.setVisibility(View.GONE);
        if (pelisdb.isEmpty()) {
            tts.speak("No encontramos peliculas en su lista personal", TextToSpeech.QUEUE_ADD, null, null);
            checkAdapterIsEmpty();
        } else {
            tts.speak("Estas son sus peliculas almacenadas en su lista personal", TextToSpeech.QUEUE_ADD, null, null);
            mAdapter = new DataAdapter(pelisdb, "milista");
            mRecyclerView.setAdapter(mAdapter);
            checkAdapterIsEmpty();
        }

    }

    private void handleDBError(Throwable error) {

    }

    private void handleResponse(MovieDetails result) {
        progressBar.setVisibility(GONE);
        List<Movie> movies = result.getData().getMovies();

        if (movies.isEmpty()) {
            tts.speak("No encontramos peliculas como " + query, TextToSpeech.QUEUE_ADD, null, null);
            checkAdapterIsEmpty();
        } else {
            tts.speak("Estos son sus resultados con " + query, TextToSpeech.QUEUE_ADD, null, null);
            mAdapter = new DataAdapter(movies, "");
            mRecyclerView.setAdapter(mAdapter);
            checkAdapterIsEmpty();
        }


    }

    private void handleError(Throwable throwable) {
        progressBar.setVisibility(GONE);
        checkAdapterIsEmpty();
        tts.speak("No encontramos peliculas como " + query, TextToSpeech.QUEUE_ADD, null, null);
        Log.v("ERROR BUSQUEDA", throwable.getLocalizedMessage());
    }

    private void checkAdapterIsEmpty() {
        if (mAdapter != null) {
            if (mAdapter.getItemCount() == 0) {
                empty.setVisibility(View.VISIBLE);

            } else {

                empty.setVisibility(View.GONE);
            }
        } else {
            empty.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        tts.shutdown();
        AppDatabase.destroyInstance();
    }

    @Override
    public void onInit(int i) {

        tts.speak("Buscando" + query, TextToSpeech.QUEUE_ADD, null, null);
    }
}

