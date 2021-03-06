package com.martinrevert.latorrentola.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.martinrevert.latorrentola.model.YTS.Movie;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;


/**
 * Created by martin on 07/12/17.
 */
@Dao
public interface MovieDao {

    @Query("SELECT * FROM movie")
    Flowable<List<Movie>> getAll();

    @Query("SELECT * FROM movie WHERE id LIKE :id")
    Single<Movie> getMovie(Integer id);

    @Delete
    void delete(Movie movie);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMovie(Movie movie);
}