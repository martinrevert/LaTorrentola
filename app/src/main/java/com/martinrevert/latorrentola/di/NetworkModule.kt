package com.martinrevert.latorrentola.di

import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.network.EztvService
import com.martinrevert.latorrentola.network.FcmService
import com.martinrevert.latorrentola.network.OpenSubtitlesService
import com.martinrevert.latorrentola.network.TmdbService
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

/** Configures shared HTTP clients and Retrofit services for each backend. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Provides an HTTP logging interceptor with bodies logged only in debug builds. */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /** Provides the shared OkHttp client used by all Retrofit instances. */
    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /** Creates a Retrofit instance for the YTS API. */
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

    /** Creates a Retrofit instance for the FCM backend. */
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

    /** Creates a Retrofit instance for TMDB. */
    @Provides
    @Singleton
    @TmdbRetrofit
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.TMDB_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    /** Creates the YTS Retrofit service. */
    @Provides
    @Singleton
    fun provideYtsService(@YtsRetrofit retrofit: Retrofit): YtsService {
        return retrofit.create(YtsService::class.java)
    }

    /** Creates the FCM Retrofit service. */
    @Provides
    @Singleton
    fun provideFcmService(@FcmRetrofit retrofit: Retrofit): FcmService {
        return retrofit.create(FcmService::class.java)
    }

    /** Creates the TMDB Retrofit service. */
    @Provides
    @Singleton
    fun provideTmdbService(@TmdbRetrofit retrofit: Retrofit): TmdbService {
        return retrofit.create(TmdbService::class.java)
    }

    /** Creates a Retrofit instance for EZTV. */
    @Provides
    @Singleton
    @EztvRetrofit
    fun provideEztvRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.EZTV_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    /** Creates the EZTV Retrofit service. */
    @Provides
    @Singleton
    fun provideEztvService(@EztvRetrofit retrofit: Retrofit): EztvService {
        return retrofit.create(EztvService::class.java)
    }

    /** Provides an isolated OpenSubtitles client without an HTTP logging interceptor. */
    @Provides
    @Singleton
    @OpenSubtitlesHttpClient
    fun provideOpenSubtitlesHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** Creates a separately qualified Retrofit instance for OpenSubtitles API v1. */
    @Provides
    @Singleton
    @OpenSubtitlesRetrofit
    fun provideOpenSubtitlesRetrofit(
        @OpenSubtitlesHttpClient okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OPEN_SUBTITLES_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    /** Creates the typed OpenSubtitles API service. */
    @Provides
    @Singleton
    fun provideOpenSubtitlesService(
        @OpenSubtitlesRetrofit retrofit: Retrofit
    ): OpenSubtitlesService {
        return retrofit.create(OpenSubtitlesService::class.java)
    }

    /** OpenSubtitles API v1 HTTPS base URL. */
    private const val OPEN_SUBTITLES_BASE_URL = "https://api.opensubtitles.com/api/v1/"
}

/** Hilt qualifier for the YTS Retrofit client. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YtsRetrofit

/** Hilt qualifier for the FCM Retrofit client. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FcmRetrofit

/** Hilt qualifier for the TMDB Retrofit client. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbRetrofit

/** Hilt qualifier for the EZTV Retrofit client. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EztvRetrofit

/** Hilt qualifier for the OpenSubtitles Retrofit instance. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSubtitlesRetrofit

/** Hilt qualifier for the OpenSubtitles HTTP client without body logging. */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSubtitlesHttpClient
