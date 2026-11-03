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

/** Room database for local visit dates, genre statistics, torrent downloads, user library, and watch history. */
@Database(
    entities = [
        DateLastVisit::class,
        GenreStats::class,
        TvGenreStats::class,
        TorrentDownload::class,
        WatchHistoryEntity::class,
        SavedAccountEntity::class,
        FavoriteMovieEntity::class,
        FavoriteTvSeriesEntity::class,
        DownloadedMovieEntity::class,
        DownloadedEpisodeEntity::class,
        SyncMetadataEntity::class
    ],
    version = 14,
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
    /** Provides database access to saved local account profiles. */
    abstract fun savedAccountDao(): SavedAccountDao
    /** Provides database access to user favorites. */
    abstract fun favoriteDao(): FavoriteDao
    /** Provides database access to user completed download markers. */
    abstract fun downloadRecordDao(): DownloadRecordDao
    /** Provides database access to cloud sync metadata. */
    abstract fun syncMetadataDao(): SyncMetadataDao

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
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Adds canonical IMDb identifier columns to user favorites, downloads, and torrent jobs. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `favorites_movies` ADD COLUMN `imdbId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `favorites_tv` ADD COLUMN `imdbId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloaded_movies` ADD COLUMN `imdbId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloaded_episodes` ADD COLUMN `imdbId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `imdbId` TEXT")
            }
        }

        /** Adds multi-account saved accounts, isolated favorites, download records, sync metadata, and migrates watch history to composite primary key. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Saved accounts for multi-account / OTT avatar profile switcher
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_accounts` (" +
                        "`uid` TEXT NOT NULL, `displayName` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                        "`photoUrl` TEXT, `lastActiveTimestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`uid`))"
                )

                // 2. Favorite movies
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites_movies` (" +
                        "`userId` TEXT NOT NULL, `movieId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`year` INTEGER NOT NULL, `rating` TEXT, `mediumCoverImage` TEXT, " +
                        "`genres` TEXT NOT NULL, `movieJson` TEXT, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`, `movieId`))"
                )

                // 3. Favorite TV series
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites_tv` (" +
                        "`userId` TEXT NOT NULL, `seriesId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`posterPath` TEXT, `firstAirDate` TEXT, `voteAverage` REAL, " +
                        "`genreIds` TEXT NOT NULL, `numberOfSeasons` INTEGER, `numberOfEpisodes` INTEGER, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`, `seriesId`))"
                )

                // 4. Downloaded movies
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloaded_movies` (" +
                        "`userId` TEXT NOT NULL, `hash` TEXT NOT NULL, `movieId` INTEGER NOT NULL, " +
                        "`movieTitle` TEXT NOT NULL, `quality` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `movieJson` TEXT, " +
                        "PRIMARY KEY(`userId`, `hash`))"
                )

                // 5. Downloaded episodes
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloaded_episodes` (" +
                        "`userId` TEXT NOT NULL, `hash` TEXT NOT NULL, `seriesId` INTEGER NOT NULL, " +
                        "`seriesName` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL, " +
                        "`episodeNumber` INTEGER NOT NULL, `episodeName` TEXT NOT NULL, " +
                        "`releaseTitle` TEXT NOT NULL, `quality` TEXT NOT NULL, " +
                        "`magnetUrl` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `stillPath` TEXT, " +
                        "PRIMARY KEY(`userId`, `hash`))"
                )

                // 6. Sync metadata
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_metadata` (" +
                        "`userId` TEXT NOT NULL, `collectionKey` TEXT NOT NULL, " +
                        "`lastSyncTimestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`, `collectionKey`))"
                )

                // 7. Migrate watch_history to include userId and composite primary key (userId, mediaId)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history_new` (" +
                        "`userId` TEXT NOT NULL, `mediaId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, `isEpisode` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`userId`, `mediaId`))"
                )
                db.execSQL(
                    "INSERT INTO `watch_history_new` (`userId`, `mediaId`, `title`, `positionMs`, `durationMs`, `timestamp`, `isEpisode`) " +
                        "SELECT '', `mediaId`, `title`, `positionMs`, `durationMs`, `timestamp`, `isEpisode` FROM `watch_history`"
                )
                db.execSQL("DROP TABLE `watch_history`")
                db.execSQL("ALTER TABLE `watch_history_new` RENAME TO `watch_history`")
            }
        }

        /** Adds posterUrl column to torrent_downloads. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `posterUrl` TEXT")
            }
        }

        /** Adds seeds and peers count columns to torrent_downloads. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `numSeeds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `numPeers` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds metadata columns to torrent_downloads for better Auto-Play. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `seriesId` INTEGER")
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `seasonNumber` INTEGER")
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `episodeNumber` INTEGER")
                db.execSQL("ALTER TABLE `torrent_downloads` ADD COLUMN `movieId` INTEGER")
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
