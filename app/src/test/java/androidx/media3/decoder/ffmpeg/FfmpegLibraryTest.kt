package androidx.media3.decoder.ffmpeg

import androidx.media3.common.MimeTypes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FfmpegLibraryTest {

    @Test
    fun `getCodecName correctly maps torrent audio mime types to FFmpeg codec names`() {
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AC3)).isEqualTo("ac3")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_E_AC3)).isEqualTo("eac3")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_E_AC3_JOC)).isEqualTo("eac3")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_DTS)).isEqualTo("dca")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_DTS_HD)).isEqualTo("dca")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_TRUEHD)).isEqualTo("truehd")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_OPUS)).isEqualTo("opus")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_VORBIS)).isEqualTo("vorbis")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_FLAC)).isEqualTo("flac")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_ALAC)).isEqualTo("alac")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_MPEG)).isEqualTo("mp3")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AAC)).isEqualTo("aac")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AMR_NB)).isEqualTo("amrnb")
        assertThat(FfmpegLibrary.getCodecName(MimeTypes.AUDIO_AMR_WB)).isEqualTo("amrwb")
    }

    @Test
    fun `getCodecName returns null for unsupported or empty mime types`() {
        assertThat(FfmpegLibrary.getCodecName("audio/unknown")).isNull()
        assertThat(FfmpegLibrary.getCodecName("")).isNull()
    }
}
