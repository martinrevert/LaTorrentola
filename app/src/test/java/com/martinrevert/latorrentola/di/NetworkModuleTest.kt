package com.martinrevert.latorrentola.di

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.network.YtsFallbackInterceptor
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NetworkModuleTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("network_module_test").toFile()
        mockContext = mockk()
        every { mockContext.cacheDir } returns tempDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `provideHttpCache configures cache directory with 30MB maximum size`() {
        val cache = NetworkModule.provideHttpCache(mockContext)

        assertThat(cache.directory).isEqualTo(File(tempDir, "http_cache"))
        assertThat(cache.maxSize()).isEqualTo(30L * 1024L * 1024L)
    }

    @Test
    fun `provideLoggingInterceptor returns non-null interceptor`() {
        val interceptor = NetworkModule.provideLoggingInterceptor()

        assertThat(interceptor).isNotNull()
    }

    @Test
    fun `provideOkHttpClient includes BrotliInterceptor and configured cache`() {
        val cache = NetworkModule.provideHttpCache(mockContext)
        val logging = NetworkModule.provideLoggingInterceptor()

        val client = NetworkModule.provideOkHttpClient(logging, cache)

        val hasBrotli = client.interceptors.any { it is BrotliInterceptor }
        val hasLogging = client.interceptors.any { it is HttpLoggingInterceptor }
        val loggingIndex = client.interceptors.indexOfFirst { it is HttpLoggingInterceptor }
        val brotliIndex = client.interceptors.indexOfFirst { it is BrotliInterceptor }

        assertThat(hasBrotli).isTrue()
        assertThat(hasLogging).isTrue()
        assertThat(loggingIndex).isLessThan(brotliIndex)
        assertThat(client.cache).isSameInstanceAs(cache)
    }

    @Test
    fun `provideRetrofit for YTS reuses base client and adds YtsFallbackInterceptor`() {
        val cache = NetworkModule.provideHttpCache(mockContext)
        val logging = NetworkModule.provideLoggingInterceptor()
        val baseClient = NetworkModule.provideOkHttpClient(logging, cache)
        val fallbackInterceptor = YtsFallbackInterceptor()

        val retrofit = NetworkModule.provideRetrofit(baseClient, fallbackInterceptor)

        val client = retrofit.callFactory() as OkHttpClient
        val hasBrotli = client.interceptors.any { it is BrotliInterceptor }
        val hasFallback = client.interceptors.any { it is YtsFallbackInterceptor }

        assertThat(hasBrotli).isTrue()
        assertThat(hasFallback).isTrue()
        assertThat(client.interceptors.first()).isSameInstanceAs(fallbackInterceptor)
        assertThat(client.cache).isSameInstanceAs(cache)
        assertThat(retrofit.baseUrl().toString()).isEqualTo(Constants.YTS_BASE_URL)
    }

    @Test
    fun `provideOpenSubtitlesHttpClient includes BrotliInterceptor`() {
        val client = NetworkModule.provideOpenSubtitlesHttpClient()

        val hasBrotli = client.interceptors.any { it is BrotliInterceptor }
        assertThat(hasBrotli).isTrue()
    }

    @Test
    fun `backend Retrofit instances configure expected base URLs`() {
        val cache = NetworkModule.provideHttpCache(mockContext)
        val logging = NetworkModule.provideLoggingInterceptor()
        val baseClient = NetworkModule.provideOkHttpClient(logging, cache)

        val tmdb = NetworkModule.provideTmdbRetrofit(baseClient)
        val eztv = NetworkModule.provideEztvRetrofit(baseClient)
        val torrentio = NetworkModule.provideTorrentioRetrofit(baseClient)
        val omdb = NetworkModule.provideOmdbRetrofit(baseClient)
        val fcm = NetworkModule.provideFcmRetrofit(baseClient)

        assertThat(tmdb.baseUrl().toString()).isEqualTo(Constants.TMDB_BASE_URL)
        assertThat(eztv.baseUrl().toString()).isEqualTo(Constants.EZTV_BASE_URL)
        assertThat(torrentio.baseUrl().toString()).isEqualTo(Constants.TORRENTIO_BASE_URL)
        assertThat(omdb.baseUrl().toString()).isEqualTo(Constants.OMDB_BASE_URL)
        assertThat(fcm.baseUrl().toString()).isEqualTo(Constants.FCM_BASE_URL)
    }
}
