package com.martinrevert.latorrentola;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.martinrevert.latorrentola.database.AppDatabase;
import com.martinrevert.latorrentola.database.MovieDao;
import com.martinrevert.latorrentola.model.YTS.Movie;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class MovieDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private MovieDao movieDao;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        movieDao = database.movieDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void insertAndGetAllMovies() {
        Movie movie = new Movie();
        movie.setId(1);
        movie.setTitle("Test Movie");

        movieDao.insertMovie(movie);

        List<Movie> movies = movieDao.getAll().blockingFirst();
        assertThat(movies).hasSize(1);
        assertThat(movies.get(0).getTitle()).isEqualTo("Test Movie");
    }

    @Test
    public void deleteMovie() {
        Movie movie = new Movie();
        movie.setId(1);
        movie.setTitle("Test Movie");
        movieDao.insertMovie(movie);

        movieDao.delete(movie);

        List<Movie> movies = movieDao.getAll().blockingFirst();
        assertThat(movies).isEmpty();
    }
}
