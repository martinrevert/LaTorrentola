package com.martinrevert.latorrentola.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests conversion and normalization of downloaded subtitle text for playback clients. */
class OpenSubtitleFormatConverterTest {
    /** Adds the required WebVTT header and converts SubRip timestamps. */
    @Test
    fun convertsSubRipTimestampsToWebVtt() {
        val result = OpenSubtitleFormatConverter.toWebVtt(
            "1\r\n00:00:01,250 --> 00:00:02,500\r\nHello"
        )

        assertEquals(
            "WEBVTT\n\n1\n00:00:01.250 --> 00:00:02.500\nHello\n",
            result
        )
    }

    /** Removes a UTF-8 BOM and preserves an existing WebVTT header. */
    @Test
    fun normalizesExistingWebVtt() {
        val result = OpenSubtitleFormatConverter.toWebVtt(
            "\uFEFFWEBVTT\r\n\r\n00:00:01.000 --> 00:00:02.000\r\nHello"
        )

        assertEquals(
            "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n",
            result
        )
    }

    /** Rejects empty subtitle content rather than creating an invalid playback asset. */
    @Test
    fun rejectsEmptySubtitleText() {
        try {
            OpenSubtitleFormatConverter.toWebVtt("\uFEFF\r\n")
            throw AssertionError("Expected an empty subtitle to be rejected")
        } catch (error: OpenSubtitlesException) {
            assertEquals(OpenSubtitlesErrorCode.INVALID_SUBTITLE, error.code)
        }
    }
}
