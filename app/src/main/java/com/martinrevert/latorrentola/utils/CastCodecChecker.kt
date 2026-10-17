package com.martinrevert.latorrentola.utils

import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.CastDevice
import com.martinrevert.latorrentola.model.YTS.Torrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CodecWarning(
    val deviceName: String,
    val modelName: String,
    val warnings: List<String>
) {
    fun formatMessage(): String {
        val warningListStr = warnings.joinToString("\n• ", prefix = "• ")
        return "$deviceName ($modelName) puede no reproducir este contenido debido a limitaciones de hardware:\n$warningListStr"
    }
}

object CastCodecChecker {

    /**
     * Extrae de manera preventiva la resolución y el tipo MIME de video/audio mediante [MediaMetadataRetriever].
     */
    @OptIn(UnstableApi::class)
    suspend fun retrieveMediaFormats(urlOrPath: String): Pair<Format?, Format?> {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                    retriever.setDataSource(urlOrPath, HashMap())
                } else {
                    retriever.setDataSource(urlOrPath)
                }

                val videoMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val width =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

                val videoFormat = if (videoMime != null || height > 0) {
                    Format.Builder()
                        .setSampleMimeType(videoMime ?: "video/mp4")
                        .setWidth(width)
                        .setHeight(height)
                        .build()
                } else null

                Pair(videoFormat, null)
            } catch (_: Exception) {
                Pair(null, null)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Cruza el modelo de dispositivo detectado vía mDNS con los formatos de video/audio para predecir fallos.
     */
    fun checkCompatibility(castDevice: CastDevice, videoFormat: Format?, audioFormat: Format?): CodecWarning? {
        val rawModel = castDevice.modelName
        val rawName = castDevice.friendlyName
        val model = rawModel.lowercase()
        val deviceName = rawName.ifBlank { "Dispositivo Cast" }

        val isNestHub = model.contains("hub") || model.contains("display") || model.contains("nest")
        val isLegacyChromecast =
            model.contains("chromecast") && !model.contains("ultra") && !model.contains("google tv")
        val isChromecastUltra = model.contains("ultra")

        val warnings = mutableListOf<String>()

        if (videoFormat != null) {
            val videoMime = videoFormat.sampleMimeType ?: ""
            val codecs = videoFormat.codecs ?: ""

            val isHevc = videoMime.contains("hevc", ignoreCase = true) ||
                    videoMime.contains("h265", ignoreCase = true) ||
                    codecs.contains("hvc1", ignoreCase = true) ||
                    codecs.contains("hev1", ignoreCase = true)

            val isAvc = videoMime.contains("avc", ignoreCase = true) ||
                    videoMime.contains("h264", ignoreCase = true) ||
                    codecs.contains("avc1", ignoreCase = true)

            val is4k = (videoFormat.height >= 2000) || (videoFormat.width >= 3500)
            val isAbove1080p = (videoFormat.height > 1080) || (videoFormat.width > 1920)

            if (isNestHub || isLegacyChromecast) {
                if (isHevc) warnings.add("Códec de video x265 / HEVC no soportado.")
                if (isAbove1080p) warnings.add("Resolución mayor a 1080p no soportada.")
            }

            if (isChromecastUltra) {
                if (is4k && isAvc) {
                    warnings.add("Resolución 4K en códec H.264 (x264) causará lag o cierre. Requiere HEVC (x265).")
                }
            }
        }

        if (audioFormat != null) {
            val audioMime = audioFormat.sampleMimeType ?: ""
            val isAc3OrDts = audioMime.contains("ac3", ignoreCase = true) ||
                    audioMime.contains("eac3", ignoreCase = true) ||
                    audioMime.contains("dts", ignoreCase = true)

            if (isNestHub && isAc3OrDts) {
                warnings.add("Audio Dolby Digital / DTS no soportado en Smart Displays.")
            }
        }

        if (warnings.isEmpty()) return null

        return CodecWarning(
            deviceName = deviceName,
            modelName = rawModel.ifBlank { "Cast" },
            warnings = warnings
        )
    }

    /**
     * Evalúa directamente los metadatos expuestos por la API de YTS antes de iniciar la descarga.
     */
    fun checkCompatibility(castDevice: CastDevice, torrent: Torrent): CodecWarning? {
        val rawModel = castDevice.modelName
        val rawName = castDevice.friendlyName
        val model = rawModel.lowercase()
        val deviceName = rawName.ifBlank { "Dispositivo Cast" }

        val isNestHub = model.contains("hub") || model.contains("display") || model.contains("nest")
        val isLegacyChromecast =
            model.contains("chromecast") && !model.contains("ultra") && !model.contains("google tv")
        val isChromecastUltra = model.contains("ultra")

        val warnings = mutableListOf<String>()

        val codec = (torrent.videoCodec ?: "").lowercase()
        val quality = (torrent.quality ?: "").lowercase()
        val bitDepth = torrent.bitDepth ?: ""
        val audioChannels = torrent.audioChannels ?: ""

        val isHevc = codec.contains("x265") || codec.contains("hevc") || bitDepth == "10"
        val isAvc = codec.contains("x264") || codec.contains("h264") || codec.contains("avc")
        val is4k = quality.contains("2160p") || quality.contains("4k")

        if (isNestHub || isLegacyChromecast) {
            if (isHevc) warnings.add("Códec de video x265 / HEVC 10-bit no soportado por este dispositivo.")
            if (is4k) warnings.add("Resolución 4K (2160p) no soportada.")
            if (isNestHub && audioChannels == "5.1") warnings.add("Audio 5.1 / Dolby no soportado en Smart Displays.")
        }

        if (isChromecastUltra) {
            if (is4k && isAvc) {
                warnings.add("Resolución 4K en códec H.264 (x264) causará saturación en Chromecast Ultra. Se sugiere x265.")
            }
        }

        if (warnings.isEmpty()) return null

        return CodecWarning(
            deviceName = deviceName,
            modelName = rawModel.ifBlank { "Cast" },
            warnings = warnings
        )
    }
}
