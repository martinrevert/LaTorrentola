package com.martinrevert.latorrentola.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import com.martinrevert.latorrentola.model.YTS.Movie;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
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