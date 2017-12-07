package com.martinrevert.latorrentola;

import android.app.SearchManager;

import android.content.Intent;

import android.content.res.Configuration;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;

import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import com.martinrevert.latorrentola.constants.Constants;
import com.martinrevert.latorrentola.model.YTS.Movie;
import com.martinrevert.latorrentola.model.YTS.MovieDetails;
import com.martinrevert.latorrentola.adapter.DataAdapter;
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

public class MainActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private ProgressBar progressBar;

    private CompositeDisposable mCompositeDisposable;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        // toolbar.setLogo(R.drawable.ic_launcher_foreground_yellow);


        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // mOpciones = getResources().getStringArray(R.array.opciones);
        mDrawerLayout = findViewById(R.id.drawer_layout);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);
        if (navigationView != null) {
            setupDrawerContent(navigationView);
        }

        View header = null;
        if (navigationView != null) {
            header = navigationView.getHeaderView(0);
        }


        // mDrawerList = findViewById(R.id.left_drawer);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,
                toolbar,
                0,  /* "open drawer" description */
                0  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getActionBar().setTitle("La Torrentola");
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getActionBar().setTitle("Menu");
            }
        };


        progressBar = findViewById(R.id.progressBar);
        mCompositeDisposable = new CompositeDisposable();
        initRecyclerView();
        loadJSON();

    }

    private void initRecyclerView() {

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(layoutManager);
    }

    private void loadJSON() {
        progressBar.setVisibility(VISIBLE);
        RequestYTSInterface requestYTSInterface = new Retrofit.Builder()
                .baseUrl(Constants.YTS_BASE_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(RequestYTSInterface.class);

        mCompositeDisposable.add(requestYTSInterface.getMovieDetails("50", "6")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(this::handleResponse, this::handleError));
    }

    private void handleResponse(MovieDetails result) {
        progressBar.setVisibility(GONE);
        List<Movie> movies = result.getData().getMovies();
        DataAdapter mAdapter = new DataAdapter(movies);
        mRecyclerView.setAdapter(mAdapter);
    }

    private void handleError(Throwable error) {
        progressBar.setVisibility(GONE);
        Toast.makeText(this, "Error " + error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSearchRequested() {
        return super.onSearchRequested();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        // Assumes current activity is the searchable activity
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true); // Do not iconify the widget; expand it by default

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {

            case R.id.search:
                onSearchRequested();
                return true;
            case R.id.settings:

                startActivity(new Intent(this, OpcionesActivity.class));

                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCompositeDisposable.clear();
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        Context context = getApplicationContext();
                        switch (menuItem.getItemId()) {
                            case R.id.action:
                                Intent action = new Intent(context, SearchableActivity.class);
                                action.putExtra("GENRE", "Action");
                                startActivity(action);
                                break;
                            case R.id.adventure:
                                Intent adventure = new Intent(context, SearchableActivity.class);
                                adventure.putExtra("GENRE", "Adventure");
                                startActivity(adventure);
                                break;
                            case R.id.biography:
                                Intent biography = new Intent(context, SearchableActivity.class);
                                biography.putExtra("GENRE", "Biography");
                                startActivity(biography);
                                break;
                            case R.id.animation:
                                Intent animation = new Intent(context, SearchableActivity.class);
                                animation.putExtra("GENRE", "Animation");
                                startActivity(animation);
                                break;
                            case R.id.comedy:
                                Intent comedy = new Intent(context, SearchableActivity.class);
                                comedy.putExtra("GENRE", "Comedy");
                                startActivity(comedy);
                                break;
                            case R.id.crime:
                                Intent crime = new Intent(context, SearchableActivity.class);
                                crime.putExtra("GENRE", "Crime");
                                startActivity(crime);
                                break;
                            case R.id.documentary:
                                Intent documentary = new Intent(context, SearchableActivity.class);
                                documentary.putExtra("GENRE", "Documentary");
                                startActivity(documentary);
                                break;
                            case R.id.drama:
                                Intent drama = new Intent(context, SearchableActivity.class);
                                drama.putExtra("GENRE", "Drama");
                                startActivity(drama);
                                break;
                            case R.id.family:
                                Intent family = new Intent(context, SearchableActivity.class);
                                family.putExtra("GENRE", "Family");
                                startActivity(family);
                                break;
                            case R.id.fantasy:
                                Intent fantasy = new Intent(context, SearchableActivity.class);
                                fantasy.putExtra("GENRE", "Fantasy");
                                startActivity(fantasy);
                                break;
                            case R.id.filmnoir:
                                Intent filmnoir = new Intent(context, SearchableActivity.class);
                                filmnoir.putExtra("GENRE", "Film-Noir");
                                startActivity(filmnoir);
                                break;
                            case R.id.history:
                                Intent history = new Intent(context, SearchableActivity.class);
                                history.putExtra("GENRE", "History");
                                startActivity(history);
                                break;

                            case R.id.horror:
                                Intent horror = new Intent(context, SearchableActivity.class);
                                horror.putExtra("GENRE", "Horror");
                                startActivity(horror);
                                break;
                            case R.id.music:
                                Intent music = new Intent(context, SearchableActivity.class);
                                music.putExtra("GENRE", "Music");
                                startActivity(music);
                                break;
                            case R.id.musical:
                                Intent musical = new Intent(context, SearchableActivity.class);
                                musical.putExtra("GENRE", "Musical");
                                startActivity(musical);
                                break;
                            case R.id.mystery:
                                Intent mystery = new Intent(context, SearchableActivity.class);
                                mystery.putExtra("GENRE", "Mystery");
                                startActivity(mystery);
                                break;
                            case R.id.romance:
                                Intent romance = new Intent(context, SearchableActivity.class);
                                romance.putExtra("GENRE", "Romance");
                                startActivity(romance);
                                break;
                            case R.id.scifi:
                                Intent scifi = new Intent(context, SearchableActivity.class);
                                scifi.putExtra("GENRE", "Sci-Fi");
                                startActivity(scifi);
                                break;
                            case R.id.sport:
                                Intent sport = new Intent(context, SearchableActivity.class);
                                sport.putExtra("GENRE", "Sport");
                                startActivity(sport);
                                break;
                            case R.id.thriller:
                                Intent thriller = new Intent(context, SearchableActivity.class);
                                thriller.putExtra("GENRE", "Thriller");
                                startActivity(thriller);
                                break;
                            case R.id.war:
                                Intent war = new Intent(context, SearchableActivity.class);
                                war.putExtra("GENRE", "War");
                                startActivity(war);
                                break;
                            case R.id.western:
                                Intent western = new Intent(context, SearchableActivity.class);
                                western.putExtra("GENRE", "Western");
                                startActivity(western);
                                break;

                            case R.id.milista:
                                Intent milista = new Intent(context, SearchableActivity.class);
                                milista.putExtra("GENRE", "milista");
                                startActivity(milista);
                                break;
                            case R.id.tridimensional:
                                Intent tridi = new Intent(context, SearchableActivity.class);
                                tridi.putExtra("GENRE", "3D");
                                startActivity(tridi);
                                break;
                            case R.id.settings:
                                Intent settings = new Intent(context, OpcionesActivity.class);
                                startActivity(settings);
                                break;
                        }

                        //menuItem.setChecked(true);
                        mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
    }
}
