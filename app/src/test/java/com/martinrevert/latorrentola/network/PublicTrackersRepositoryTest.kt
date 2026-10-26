package com.martinrevert.latorrentola.network

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PublicTrackersRepositoryTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        context = mockk()
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("public_trackers_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getString("cached_trackers", null) } returns null
        every { sharedPreferences.getLong("last_update_ms", 0L) } returns 0L
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor

        okHttpClient = mockk()
        PublicTrackersRepository.setActiveTrackersForTesting(PublicTrackersRepository.DEFAULT_TRACKERS)
    }

    @After
    fun tearDown() {
        PublicTrackersRepository.setActiveTrackersForTesting(PublicTrackersRepository.DEFAULT_TRACKERS)
    }

    @Test
    fun `DEFAULT_TRACKERS should contain valid tier-1 trackers`() {
        val trackers = PublicTrackersRepository.DEFAULT_TRACKERS
        assertThat(trackers).isNotEmpty()
        assertThat(trackers.size).isAtLeast(10)
        trackers.forEach { tracker ->
            assertThat(tracker.startsWith("udp://") || tracker.startsWith("http://")).isTrue()
        }
    }

    @Test
    fun `getActiveTrackers returns default trackers by default`() {
        val trackers = PublicTrackersRepository.getActiveTrackers()
        assertThat(trackers).contains("udp://tracker.opentrackr.org:1337/announce")
    }

    @Test
    fun `ensureTrackersInMagnet appends missing trackers to magnet`() {
        val bareMagnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=TestMovie"
        val augmented = PublicTrackersRepository.ensureTrackersInMagnet(bareMagnet)

        assertThat(augmented).startsWith(bareMagnet)
        assertThat(augmented).contains("&tr=udp://tracker.opentrackr.org:1337/announce")
        assertThat(augmented).contains("&tr=udp://open.stealth.si:80/announce")
    }

    @Test
    fun `ensureTrackersInMagnet does not duplicate existing trackers`() {
        val existingTracker = "udp://tracker.opentrackr.org:1337/announce"
        val magnetWithTracker = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=TestMovie&tr=$existingTracker"
        val augmented = PublicTrackersRepository.ensureTrackersInMagnet(magnetWithTracker)

        val occurrences = augmented.split("&tr=").size - 1
        val opentrackrCount = augmented.split(existingTracker).size - 1
        assertThat(opentrackrCount).isEqualTo(1)
        assertThat(occurrences).isEqualTo(PublicTrackersRepository.getActiveTrackers().size)
    }

    @Test
    fun `ensureTrackersInMagnet returns non-magnet URIs unmodified`() {
        val fileUrl = "https://example.com/video.mp4"
        val result = PublicTrackersRepository.ensureTrackersInMagnet(fileUrl)
        assertThat(result).isEqualTo(fileUrl)
    }

    @Test
    fun `refreshTrackersIfStale skips fetch when cache is younger than 24 hours`() = runTest {
        val recentTime = System.currentTimeMillis() - 1000L // 1 second ago
        every { sharedPreferences.getLong("last_update_ms", 0L) } returns recentTime

        val repo = PublicTrackersRepository(context, okHttpClient)
        val refreshed = repo.refreshTrackersIfStale(force = false)

        assertThat(refreshed).isFalse()
        verify(exactly = 0) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `refreshTrackersIfStale updates active trackers on successful HTTP response`() = runTest {
        val remoteList = """
            udp://new.tracker.one:1337/announce
            udp://new.tracker.two:6969/announce
            udp://new.tracker.three:80/announce
            udp://new.tracker.four:1337/announce
            udp://new.tracker.five:6969/announce
            udp://new.tracker.six:80/announce
        """.trimIndent()

        val responseBody = remoteList.toResponseBody("text/plain".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url(PublicTrackersRepository.PRIMARY_URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        val call = mockk<Call>()
        every { call.execute() } returns response
        every { okHttpClient.newCall(any()) } returns call

        val repo = PublicTrackersRepository(context, okHttpClient)
        val refreshed = repo.refreshTrackersIfStale(force = true)

        assertThat(refreshed).isTrue()
        assertThat(PublicTrackersRepository.getActiveTrackers()).contains("udp://new.tracker.one:1337/announce")
        verify { editor.putString("cached_trackers", any()) }
        verify { editor.putLong("last_update_ms", any()) }
    }

    @Test
    fun `refreshTrackersIfStale falls back to existing trackers on network failure`() = runTest {
        val call = mockk<Call>()
        every { call.execute() } throws IOException("Network unreachable")
        every { okHttpClient.newCall(any()) } returns call

        val repo = PublicTrackersRepository(context, okHttpClient)
        val refreshed = repo.refreshTrackersIfStale(force = true)

        assertThat(refreshed).isFalse()
        assertThat(PublicTrackersRepository.getActiveTrackers()).contains("udp://tracker.opentrackr.org:1337/announce")
    }
}
