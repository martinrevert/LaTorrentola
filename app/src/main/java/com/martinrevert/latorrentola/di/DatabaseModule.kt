package com.martinrevert.latorrentola.di

import android.content.Context
import com.martinrevert.latorrentola.database.AppDatabase
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.DownloadRecordDao
import com.martinrevert.latorrentola.database.FavoriteDao
import com.martinrevert.latorrentola.database.GenreDao
import com.martinrevert.latorrentola.database.SavedAccountDao
import com.martinrevert.latorrentola.database.SyncMetadataDao
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.database.TvGenreDao
import com.martinrevert.latorrentola.database.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the app's Room database and DAO dependencies to Hilt. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Provides the singleton Room database for the application. */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getAppDatabase(context)
    }

    /** Provides the last-visit DAO from [appDatabase]. */
    @Provides
    fun provideDateDao(appDatabase: AppDatabase): DateDao {
        return appDatabase.dateDao()
    }

    /** Provides the genre statistics DAO from [appDatabase]. */
    @Provides
    fun provideGenreDao(appDatabase: AppDatabase): GenreDao {
        return appDatabase.genreDao()
    }

    /** Provides the TV genre statistics DAO from [appDatabase]. */
    @Provides
    fun provideTvGenreDao(appDatabase: AppDatabase): TvGenreDao {
        return appDatabase.tvGenreDao()
    }

    /** Provides the local torrent-job DAO. */
    @Provides
    fun provideTorrentDownloadDao(appDatabase: AppDatabase): TorrentDownloadDao {
        return appDatabase.torrentDownloadDao()
    }

    /** Provides the local watch history DAO. */
    @Provides
    fun provideWatchHistoryDao(appDatabase: AppDatabase): WatchHistoryDao {
        return appDatabase.watchHistoryDao()
    }

    /** Provides the saved accounts DAO for multi-account support. */
    @Provides
    fun provideSavedAccountDao(appDatabase: AppDatabase): SavedAccountDao {
        return appDatabase.savedAccountDao()
    }

    /** Provides the user favorites DAO. */
    @Provides
    fun provideFavoriteDao(appDatabase: AppDatabase): FavoriteDao {
        return appDatabase.favoriteDao()
    }

    /** Provides the download records DAO. */
    @Provides
    fun provideDownloadRecordDao(appDatabase: AppDatabase): DownloadRecordDao {
        return appDatabase.downloadRecordDao()
    }

    /** Provides the cloud sync metadata DAO. */
    @Provides
    fun provideSyncMetadataDao(appDatabase: AppDatabase): SyncMetadataDao {
        return appDatabase.syncMetadataDao()
    }
}
