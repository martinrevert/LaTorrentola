package com.martinrevert.latorrentola.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.martinrevert.latorrentola.model.date.DateLastVisit
import com.martinrevert.latorrentola.model.stats.GenreStats
import com.martinrevert.latorrentola.model.stats.TvGenreStats
import com.martinrevert.latorrentola.model.torrent.TorrentDownload

/** Room database for local visit dates, genre statistics, torrent downloads, and watch history. */
@Database(
    entities = [DateLastVisit::class, GenreStats::class, TvGenreStats::class, TorrentDownload::class, WatchHistoryEntity::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /** Provides database access to last-visit records. */
    abstract fun dateDao(): DateDao
    /** Provides database access to genre visit statistics. */
    abstract fun genreDao(): GenreDao
    /** Provides database access to TV genre visit statistics. */
    abstract fun tvGenreDao(): TvGenreDao
    /** Provides database access to app-managed torrent downloads. */
    abstract fun torrentDownloadDao(): TorrentDownloadDao
    /** Provides database access to local watch history and playback progress. */
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        /** Lazily initialized process-wide database instance. */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Returns the singleton database, creating it on first access. */
        fun getAppDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appdatabase"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Adds watch history table for local playback progress caching. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            /** Applies the watch history table schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history` (" +
                        "`mediaId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `isEpisode` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`mediaId`))"
                )
            }
        }

        /** Drops the legacy movies table while upgrading schema version 5 to 6. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `movies`")
            }
        }

        /** Creates a separate table for TV genre usage without altering movie statistics. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tv_genre_stats` " +
                        "(`genreId` INTEGER NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`genreId`))"
                )
            }
        }

        /** Adds local torrent jobs while preserving existing user and genre data. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            /** Applies the local torrent-job schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `torrent_downloads` (" +
                        "`infoHash` TEXT NOT NULL, `magnetUri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`state` TEXT NOT NULL, `progressPercent` INTEGER NOT NULL, " +
                        "`mediaPath` TEXT, `castWhenReady` INTEGER NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`infoHash`))"
                )
            }
        }

        /** Adds the visit-date table when upgrading schema version 1 to 2. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `date` (`id` INTEGER,`date` LONG, PRIMARY KEY(`id`))")
            }
        }

        /** Adds cast data to the legacy movies table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `movies` ADD COLUMN `cast` TEXT")
            }
        }

        /** Creates the genre statistics table during schema version 3 to 4. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `genre_stats` (`genre` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`genre`))")
            }
        }

        /** Adds upload timestamps to the legacy movies table. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            /** Applies this schema change to [db]. */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `movies` ADD COLUMN `date_uploaded_unix` INTEGER")
            }
        }

        /** Clears the process singleton, primarily for controlled database lifecycle resets. */
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
