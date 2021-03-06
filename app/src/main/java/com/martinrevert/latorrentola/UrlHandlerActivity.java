package com.martinrevert.latorrentola;


import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ProgressBar;



import com.martinrevert.latorrentola.adapter.DataAdapter;
import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.MovieDetails;
import com.martinrevert.latorrentola.network.RequestYTSInterface;

import java.util.List;
import java.util.Locale;
import java.util.Objects;


import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class UrlHandlerActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private RecyclerView mRecyclerView;
    private ProgressBar progressBar;

    private CompositeDisposable mCompositeDisposable;
    private TextToSpeech tts;
    private DataAdapter mAdapter;
    private boolean voice_system;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("La Torrentola");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tts = new TextToSpeech(this, this);
        tts.setLanguage(Locale.getDefault());

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        voice_system = sharedPreferences.getBoolean("voice_system", true);
        progressBar = findViewById(R.id.progressBar);
        mCompositeDisposable = new CompositeDisposable();
        initRecyclerView();

        String imdb = null;
        Intent appLinkIntent = getIntent();

        if (Objects.equals(appLinkIntent.getAction(), Intent.ACTION_SEND)) {

            imdb = appLinkIntent.getStringExtra(Intent.EXTRA_TEXT);

        } else {

            imdb = appLinkIntent.getDataString();

        }
        Log.v("LINK", imdb);
        imdb = imdb.replaceAll("[^0-9]", "");
        loadJSON(imdb);
    }

    private void initRecyclerView() {

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new DataAdapter(null, null);
        mRecyclerView.setAdapter(mAdapter);
    }

    private void loadJSON(String imdb) {
        progressBar.setVisibility(VISIBLE);
        RequestYTSInterface requestYTSInterface = new Retrofit.Builder()
                .baseUrl(Constants.YTS_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestYTSInterface.class);

        mCompositeDisposable.add(requestYTSInterface.getMovieSearch(Constants.PAGE_SIZE, "tt" + imdb)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::handleResponse, this::handleError));
    }

    private void handleResponse(MovieDetails result) {

        progressBar.setVisibility(GONE);
        List<Movie> movies = result.getData().getMovies();

        if (movies.isEmpty()) {
            if (voice_system) {
                tts.speak("Oh no!. Esta película no está disponible", TextToSpeech.QUEUE_ADD, null, null);
            }
        } else {
            if (voice_system) {
                tts.speak("Ok. Esta película si está disponible", TextToSpeech.QUEUE_ADD, null, null);
            }
            mAdapter = new DataAdapter(movies, "");
            mRecyclerView.setAdapter(mAdapter);
        }
    }

    private void handleError(Throwable error) {
        if (voice_system) {
            tts.speak("Oh no!. Esta película no está disponible", TextToSpeech.QUEUE_ADD, null, null);
        }
        progressBar.setVisibility(GONE);
        Log.v("ERROR", error.getLocalizedMessage());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (NavUtils.shouldUpRecreateTask(this, upIntent) || isTaskRoot()) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                            // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.dispose();
        tts.shutdown();
    }

    @Override
    public void onInit(int i) {
        if (voice_system) {
            tts.speak("Chequeando disponibilidad", TextToSpeech.QUEUE_ADD, null, null);
        }
    }
}
