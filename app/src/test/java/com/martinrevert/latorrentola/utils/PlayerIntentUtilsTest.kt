package com.martinrevert.latorrentola.utils

import android.content.Intent
import android.net.Uri
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.ui.player.LocalPlayerActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PlayerIntentUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            val uri = mockk<Uri>()
            every { uri.toString() } returns str
            uri
        }
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            val uri = mockk<Uri>()
            every { uri.toString() } returns "file://${file.path}"
            uri
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `buildMediaItem from movie Intent generates canonical movie mediaId and metadata`() {
        val intent = mockk<Intent>()
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_STREAM_URL) } returns "http://127.0.0.1:8080/movie.mp4"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_FILE_PATH) } returns null
        every { intent.getBooleanExtra(LocalPlayerActivity.EXTRA_PARTIAL_TORRENT_STREAM, false) } returns false
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE) } returns "Inception"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_POSTER_URL) } returns "http://example.com/poster.jpg"
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_SERIES_ID, 0) } returns 0
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, 0) } returns 0
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, 0) } returns 0
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, 0) } returns 12230
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_MIME_TYPE) } returns "video/mp4"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_CAST_URL) } returns null
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_INFO_HASH) } returns "hash12230"

        val mediaItem = PlayerIntentUtils.buildMediaItem(intent)

        assertEquals("12230", mediaItem.mediaId)
        assertEquals("Inception", mediaItem.mediaMetadata.title?.toString())
        assertEquals("video/mp4", mediaItem.localConfiguration?.mimeType)
        assertNotNull(mediaItem.mediaMetadata.extras)
    }

    @Test
    fun `buildMediaItem from episode Intent generates canonical episode mediaId and extras`() {
        val intent = mockk<Intent>()
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_STREAM_URL) } returns "http://127.0.0.1:8080/ep1.mkv"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_FILE_PATH) } returns null
        every { intent.getBooleanExtra(LocalPlayerActivity.EXTRA_PARTIAL_TORRENT_STREAM, false) } returns true
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_TITLE) } returns "Severance S01E01"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_POSTER_URL) } returns null
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_SERIES_ID, 0) } returns 111803
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_SEASON_NUMBER, 0) } returns 1
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_EPISODE_NUMBER, 0) } returns 1
        every { intent.getIntExtra(LocalPlayerActivity.EXTRA_MOVIE_ID, 0) } returns 0
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_MIME_TYPE) } returns "video/x-matroska"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_CAST_URL) } returns "http://192.168.1.50:8080/ep1.mkv"
        every { intent.getStringExtra(LocalPlayerActivity.EXTRA_INFO_HASH) } returns "hashSeverance"

        val mediaItem = PlayerIntentUtils.buildMediaItem(intent)

        assertEquals("media_111803_s1_e1", mediaItem.mediaId)
        assertEquals("Severance S01E01", mediaItem.mediaMetadata.title?.toString())
        assertEquals("video/x-matroska", mediaItem.localConfiguration?.mimeType)
        assertNotNull(mediaItem.mediaMetadata.extras)
    }

    @Test
    fun `buildMediaItem from TorrentDownload record builds valid mediaItem with file uri and extras`() {
        val download = TorrentDownload(
            infoHash = "092ea1d9309572ad7caf0868c3a94217dad15769",
            title = "The Matrix (1999)",
            magnetUri = "magnet:?xt=urn:btih:092ea1d9309572ad7caf0868c3a94217dad15769",
            state = "COMPLETED",
            progressPercent = 100,
            mediaPath = "/storage/downloads/matrix.mp4",
            castWhenReady = false,
            updatedAtMillis = System.currentTimeMillis(),
            movieId = 603
        )
        val file = File("matrix.mp4")

        val mediaItem = PlayerIntentUtils.buildMediaItem(download, file)

        assertEquals("603", mediaItem.mediaId)
        assertEquals("The Matrix (1999)", mediaItem.mediaMetadata.title?.toString())
        assertEquals("video/mp4", mediaItem.localConfiguration?.mimeType)
        assertNotNull(mediaItem.mediaMetadata.extras)
    }
}
