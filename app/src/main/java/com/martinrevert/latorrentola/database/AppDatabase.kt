package com.martinrevert.latorrentola.database;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import android.content.Context;


import com.martinrevert.latorrentola.model.date.DateLastVisit;
import com.martinrevert.latorrentola.model.YTS.Movie;

/**
 * Created by martin on 07/12/17.
 */
@Database(version = 2, entities = {Movie.class, DateLastVisit.class}, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    abstract public MovieDao movieDao();

    abstract public DateDao dateDao();

    public static AppDatabase getAppDatabase(Context context) {
        if (INSTANCE == null) {
            INSTANCE =
                    Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "appdatabase")
                            .addMigrations(MIGRATION_1_2)
                            .build();
        }

        return INSTANCE;
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE `date` (`id` INTEGER,`date` LONG, PRIMARY KEY(`id`))");
        }
    };

    public static void destroyInstance() {
        INSTANCE = null;
    }

}