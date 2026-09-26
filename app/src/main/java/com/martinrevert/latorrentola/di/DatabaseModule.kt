package com.martinrevert.latorrentola.di

import android.content.Context
import com.martinrevert.latorrentola.database.AppDatabase
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.GenreDao
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
}
