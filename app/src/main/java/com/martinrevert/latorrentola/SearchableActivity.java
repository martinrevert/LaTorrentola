package com.martinrevert.latorrentola;

import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
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
    private LinearLayoutManager layoutManager;
    private ProgressBar progressBar;
    private CompositeDisposable compositeDisposable;
    private DataAdapter mAdapter;
    private TextView empty;

    private TextToSpeech tts;
    private String query = null;
    private Intent intent;

    private AppDatabase db;
    private boolean voice_system;
    private int onpause;
    private int currentpage = 1;
    private boolean is3D = false;
    private boolean isGenre = false;
    private boolean isLoading;
    private boolean isLastPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searchable);
        onpause = 0;
        Fabric.with(this, new Crashlytics());

        layoutManager = new LinearLayoutManager(getApplicationContext());
        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.getDefault());
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        voice_system = sharedPreferences.getBoolean("voice_system", true);
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
        loadJSON(query, currentpage);

    }

    private void initRecyclerView() {

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addOnScrollListener(recyclerViewOnScrollListener);
        mAdapter = new DataAdapter(null, null);
        mRecyclerView.setAdapter(mAdapter);

    }

    private RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int visibleItemCount = layoutManager.getChildCount();
            int totalItemCount = layoutManager.getItemCount();
            int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

            if (!isLoading && !isLastPage) {
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= Constants.PAGE_SIZE * currentpage) {
                    currentpage = currentpage + 1;
                    loadJSON(query, currentpage);
                }
            }
        }
    };

    private void loadJSON(String query, int currentpage) {
        isLoading = true;
        progressBar.setVisibility(VISIBLE);
        RequestYTSInterface requestYTSInterface = new Retrofit.Builder()
                .baseUrl(Constants.YTS_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestYTSInterface.class);

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            compositeDisposable.add(requestYTSInterface.getMovieSearch(Constants.PAGE_SIZE, query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(this::handleResponse, this::handleError));
        } else {
            switch (query) {
                case "3D":
                    is3D = true;
                    compositeDisposable.add(requestYTSInterface.getTridiSearch(Constants.PAGE_SIZE, query, currentpage)
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
                    isGenre = true;
                    compositeDisposable.add(requestYTSInterface.getGenreSearch(Constants.PAGE_SIZE, query, currentpage)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeOn(Schedulers.io())
                            .subscribe(this::handleResponse, this::handleError));
                    break;
            }
        }

    }

    private void handleDBResponse(List<Movie> pelisdb) {
        progressBar.setVisibility(View.GONE);
        isLoading = false;
        if (pelisdb.isEmpty()) {
            if (voice_system) {
                tts.speak("No encontramos peliculas en su lista personal", TextToSpeech.QUEUE_ADD, null, null);
            }
            checkAdapterIsEmpty();
        } else {
            if (voice_system) {
                tts.speak("Estas son sus peliculas almacenadas en su lista personal", TextToSpeech.QUEUE_ADD, null, null);
            }
            mAdapter = new DataAdapter(pelisdb, "milista");
            mRecyclerView.setAdapter(mAdapter);
            checkAdapterIsEmpty();
        }

    }

    private void handleDBError(Throwable error) {
        isLoading = false;
    }

    private void handleResponse(MovieDetails result) {
        progressBar.setVisibility(GONE);
        isLoading = false;
        List<Movie> movies = result.getData().getMovies();

        if (movies.isEmpty()) {
            if (voice_system) {
                tts.speak("No encontramos peliculas como " + query, TextToSpeech.QUEUE_ADD, null, null);
            }
            checkAdapterIsEmpty();
        } else {
            if (voice_system) {
                tts.speak("Estos son sus resultados con " + query, TextToSpeech.QUEUE_ADD, null, null);
            }

            if (is3D || isGenre) {
                if (mAdapter.getItemCount() < Constants.PAGE_SIZE) {
                    mAdapter = new DataAdapter(movies, "");
                    mRecyclerView.setAdapter(mAdapter);
                } else {
                    mAdapter.addMovies(movies);
                    mRecyclerView.scrollToPosition(currentpage * 50);
                }
            } else {
                mAdapter = new DataAdapter(movies, "");
                mRecyclerView.setAdapter(mAdapter);
                checkAdapterIsEmpty();
            }
        }


    }

    private void handleError(Throwable throwable) {
        progressBar.setVisibility(GONE);
        isLoading = false;
        checkAdapterIsEmpty();
        if (voice_system) {
            tts.speak("No encontramos peliculas como " + query, TextToSpeech.QUEUE_ADD, null, null);
        }
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
    public void onPause() {
        super.onPause();
        onpause = onpause + 1;
        if (onpause > 1) {

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.dispose();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        AppDatabase.destroyInstance();
    }


    @Override
    public void onResume() {
        super.onResume();
        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.getDefault());


    }

    @Override
    public void onInit(int i) {
        if (voice_system) {
            tts.speak("Buscando" + query, TextToSpeech.QUEUE_ADD, null, null);
        }
    }
}

