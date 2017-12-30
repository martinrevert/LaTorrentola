package com.martinrevert.latorrentola.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

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

