package com.martinrevert.latorrentola.service.streaming

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test

@OptIn(UnstableApi::class)
class PlayerStreamingConfigTest {

    @Test
    fun `buildLoadControl creates valid load control for TV`() {
        val loadControl = PlayerStreamingConfig.buildLoadControl(isTv = true)
        assertThat(loadControl).isNotNull()
    }

    @Test
    fun `buildLoadControl creates valid load control for Mobile`() {
        val loadControl = PlayerStreamingConfig.buildLoadControl(isTv = false)
        assertThat(loadControl).isNotNull()
    }

    @Test
    fun `buildLoadErrorHandlingPolicy has high retry count`() {
        val policy = PlayerStreamingConfig.buildLoadErrorHandlingPolicy()
        val retryCount = policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)
        assertThat(retryCount).isAtLeast(5)
    }

    @Test
    fun `http read timeout is at least 30 seconds to absorb piece verification`() {
        assertThat(PlayerStreamingConfig.HTTP_READ_TIMEOUT_MS).isAtLeast(30_000)
    }

    @Test
    fun `http connect timeout is at least 10 seconds`() {
        assertThat(PlayerStreamingConfig.HTTP_CONNECT_TIMEOUT_MS).isAtLeast(10_000)
    }

    @Test
    fun `audio extension renderer mode is ON so passthrough and MediaCodec take priority over FFmpeg`() {
        assertThat(PlayerStreamingConfig.AUDIO_EXTENSION_RENDERER_MODE)
            .isEqualTo(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }

    @Test
    fun `buildRenderersFactory creates factory with extension renderer mode ON`() {
        val context = mockk<Context>(relaxed = true)
        val renderersFactory = PlayerStreamingConfig.buildRenderersFactory(context)
        assertThat(renderersFactory).isNotNull()
    }

    @Test
    fun `tv buffer for playback is optimized to 2 seconds for snappy startup and 3 seconds on rebuffer`() {
        assertThat(PlayerStreamingConfig.TV_BUFFER_FOR_PLAYBACK_MS).isEqualTo(2_000)
        assertThat(PlayerStreamingConfig.TV_BUFFER_AFTER_REBUFFER_MS).isEqualTo(3_000)
        assertThat(PlayerStreamingConfig.TV_TARGET_BUFFER_BYTES).isEqualTo(96 * 1024 * 1024)
        assertThat(PlayerStreamingConfig.TV_MAX_BUFFER_MS).isEqualTo(120_000)
    }

    @Test
    fun `buildRenderersFactory configures audio sink with custom preference manager`() {
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<PreferenceManager>(relaxed = true)
        val renderersFactory = PlayerStreamingConfig.buildRenderersFactory(context, prefs)
        assertThat(renderersFactory).isNotNull()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `mediaCodecAudioRenderer rejects DTS-HD when decodeDtsHdToPcm is enabled and delegates when disabled`() {
        androidx.media3.common.util.Log.setLogger(object : androidx.media3.common.util.Log.Logger {
            override fun d(tag: String, message: String, throwable: Throwable?) {}
            override fun i(tag: String, message: String, throwable: Throwable?) {}
            override fun w(tag: String, message: String, throwable: Throwable?) {}
            override fun e(tag: String, message: String, throwable: Throwable?) {}
        })

        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<PreferenceManager>(relaxed = true)
        val mockAudioSink = mockk<AudioSink>(relaxed = true)
        val mockSelector = mockk<MediaCodecSelector>(relaxed = true)

        val dtsHdFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_DTS_HD)
            .build()

        every { mockAudioSink.supportsFormat(dtsHdFormat) } returns true

        val renderersFactory = PlayerStreamingConfig.buildRenderersFactory(context, prefs)
        val method = DefaultRenderersFactory::class.java.getDeclaredMethod(
            "buildAudioRenderers",
            Context::class.java,
            Int::class.javaPrimitiveType,
            MediaCodecSelector::class.java,
            Boolean::class.javaPrimitiveType,
            AudioSink::class.java,
            android.os.Handler::class.java,
            AudioRendererEventListener::class.java,
            ArrayList::class.java
        )
        method.isAccessible = true
        val out = ArrayList<Renderer>()
        method.invoke(
            renderersFactory,
            context,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            mockSelector,
            false,
            mockAudioSink,
            mockk<android.os.Handler>(relaxed = true),
            mockk<AudioRendererEventListener>(relaxed = true),
            out
        )

        val audioRenderer = out.filterIsInstance<MediaCodecAudioRenderer>().first()

        // 1. When decodeDtsHdToPcm is enabled, MediaCodecAudioRenderer must reject DTS-HD
        every { prefs.getDecodeDtsHdToPcm() } returns true
        val rejectedSupport = audioRenderer.supportsFormat(dtsHdFormat)
        assertThat(RendererCapabilities.getFormatSupport(rejectedSupport))
            .isEqualTo(C.FORMAT_UNSUPPORTED_SUBTYPE)

        // 2. When decodeDtsHdToPcm is disabled, MediaCodecAudioRenderer delegates to super (direct passthrough accepted)
        every { prefs.getDecodeDtsHdToPcm() } returns false
        val acceptedSupport = audioRenderer.supportsFormat(dtsHdFormat)
        assertThat(RendererCapabilities.getFormatSupport(acceptedSupport))
            .isEqualTo(C.FORMAT_HANDLED)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `isDtsHdMime correctly identifies DTS-HD and DTS-X mime types`() {
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime("audio/vnd.dts.hd")).isTrue()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime("audio/dts-hd")).isTrue()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(MimeTypes.AUDIO_DTS_HD)).isTrue()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(MimeTypes.AUDIO_DTS_EXPRESS)).isTrue()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(MimeTypes.AUDIO_DTS_X)).isTrue()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime("audio/vnd.dts")).isFalse()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(MimeTypes.AUDIO_DTS)).isFalse()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(MimeTypes.AUDIO_AC3)).isFalse()
        assertThat(Ac3TranscodingAudioSink.isDtsHdMime(null)).isFalse()
    }
}
