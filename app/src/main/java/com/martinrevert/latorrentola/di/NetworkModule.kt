package com.martinrevert.latorrentola.di

import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.network.FcmService
import com.martinrevert.latorrentola.network.YtsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @YtsRetrofit
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.YTS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    @FcmRetrofit
    fun provideFcmRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.FCM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideYtsService(@YtsRetrofit retrofit: Retrofit): YtsService {
        return retrofit.create(YtsService::class.java)
    }

    @Provides
    @Singleton
    fun provideFcmService(@FcmRetrofit retrofit: Retrofit): FcmService {
        return retrofit.create(FcmService::class.java)
    }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YtsRetrofit

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FcmRetrofit
