package com.martinrevert.latorrentola;

import android.app.SearchManager;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.martinrevert.latorrentola.adapter.DataAdapter;
import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.MovieDetails;
import com.martinrevert.latorrentola.network.RequestYTSInterface;

import java.util.List;

import io.fabric.sdk.android.Fabric;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class SearchableActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private ProgressBar progressBar;
    private CompositeDisposable compositeDisposable;
    private DataAdapter mAdapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_searchable);
        Fabric.with(this, new Crashlytics());
        progressBar = findViewById(R.id.progressBar);
        empty = findViewById(R.id.empty);
        empty.setText("No encontré movies con ese criterio");
        compositeDisposable = new CompositeDisposable();
        // Get the intent, verify the action and get the query
        Intent intent = getIntent();
        String query = null;
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {

            query = intent.getStringExtra(SearchManager.QUERY);
            Log.v("BUSCAR", query);
        }

        initRecyclerView();
        loadJSON(query);


    }

    private void initRecyclerView() {

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(layoutManager);

    }

    private void loadJSON(String query) {

        progressBar.setVisibility(VISIBLE);
        RequestYTSInterface requestYTSInterface = new Retrofit.Builder()
                .baseUrl(Constants.YTS_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestYTSInterface.class);

        compositeDisposable.add(requestYTSInterface.getMovieSearch("50", query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::handleResponse, this::handleError));
    }

    private void handleResponse(MovieDetails result) {
        progressBar.setVisibility(GONE);
        List<Movie> movies = result.getData().getMovies();
        mAdapter = new DataAdapter(movies);
        mRecyclerView.setAdapter(mAdapter);
        checkAdapterIsEmpty();

    }

    private void handleError(Throwable throwable) {
        progressBar.setVisibility(GONE);
        Toast.makeText(this, "Error " + throwable.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
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
        compositeDisposable.clear();
    }

}

