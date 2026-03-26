package com.martinrevert.latorrentola.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import android.content.Context;


import com.martinrevert.latorrentola.model.date.DateLastVisit;
import com.martinrevert.latorrentola.model.YTS.Movie;

/**
 * Created by martin on 07/12/17.
 */
@Database(version = 4, entities = {Movie.class, DateLastVisit.class}, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    abstract public MovieDao movieDao();

    abstract public DateDao dateDao();

    public static AppDatabase getAppDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), 
                            AppDatabase.class, "appdatabase")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        // Method kept for compatibility but no longer closes the singleton
    }
}
