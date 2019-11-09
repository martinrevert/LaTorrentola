package com.martinrevert.latorrentola.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.martinrevert.latorrentola.model.DateLastVisit;

import java.util.List;

import io.reactivex.Single;


/**
 * Created by martin on 21/12/17.
 */
@Dao
public interface DateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setDate(DateLastVisit date);

    @Query("SELECT * FROM date ORDER BY id DESC LIMIT 1")
    Single <List<DateLastVisit>> getDate();
}

