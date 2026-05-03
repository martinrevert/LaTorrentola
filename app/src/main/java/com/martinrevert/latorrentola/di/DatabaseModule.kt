package com.martinrevert.latorrentola.di

import android.content.Context
import com.martinrevert.latorrentola.database.AppDatabase
import com.martinrevert.latorrentola.database.DateDao
import com.martinrevert.latorrentola.database.MovieDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getAppDatabase(context)
    }

    @Provides
    fun provideMovieDao(appDatabase: AppDatabase): MovieDao {
        return appDatabase.movieDao()
    }

    @Provides
    fun provideDateDao(appDatabase: AppDatabase): DateDao {
        return appDatabase.dateDao()
    }
}
