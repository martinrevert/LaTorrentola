package com.martinrevert.latorrentola.utils

import java.io.File
import java.util.Locale

/**
 * Returns a content type for supported video containers and downloaded WebVTT subtitles.
 *
 * @receiver File whose extension identifies its content type.
 * @return MIME type for HTTP and media playback.
 */
fun File.mediaMimeType(): String = when (extension.lowercase(Locale.ROOT)) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "ts" -> "video/mp2t"
    "avi" -> "video/x-msvideo"
    "mov" -> "video/quicktime"
    "vtt" -> "text/vtt"
    else -> "video/x-matroska"
}
