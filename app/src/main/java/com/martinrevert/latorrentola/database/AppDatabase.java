package com.martinrevert.latorrentola.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;

import com.martinrevert.latorrentola.model.YTS.Movie;

/**
 * Created by martin on 07/12/17.
 */
@Database(version = 1, entities = {Movie.class})
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    abstract public MovieDao movieDao();

    public static AppDatabase getAppDatabase(Context context) {
        if (INSTANCE == null) {
            INSTANCE =
                    Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "appdatabase")
                            .build();

        }

        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

}