package com.martinrevert.latorrentola.utils

import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.torrentio.TorrentioStream
import java.util.Locale

/**
 * Parses Torrentio stream strings and metadata, identifying audio quality,
 * video resolution groups, seeder counts, file sizes, and enforcing anti-season-pack
 * and anti-EZTV filtering rules.
 */
object TorrentioMetadataParser {

    private val SEEDS_REGEX = Regex("""👤\s*(\d+)""")
    private val SIZE_REGEX = Regex("""💾\s*([^⚙️\n\r]+)""")
    private val PROVIDER_REGEX = Regex("""⚙️\s*([^\n\r]+)""")
    private val SIZE_UNITS_REGEX = Regex("""([\d.]+)\s*([KMGTP]?B)""", RegexOption.IGNORE_CASE)

    // Season pack and multi-episode markers
    private val SEASON_PACK_REGEX = Regex(
        """\b(?:complete(?:\s+series|\s+season)?|season\s*\d+\s*complete|s\d+\s*-\s*s\d+|s\d+\s*-\s*\d+|s\d+e\d+\s*-\s*e?\d+|temporada\s*\d+\s*completa|full\s*season|all\s*seasons|integrale)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Determines whether a torrent represents an entire season or multi-episode collection.
     * The app strictly works with single episode releases to prevent multi-gigabyte pack downloads.
     */
    fun isSeasonPack(title: String?, filename: String? = null): Boolean {
        val combined = "${title.orEmpty()} ${filename.orEmpty()}"
        if (combined.isBlank()) return false
        return SEASON_PACK_REGEX.containsMatchIn(combined)
    }

    /**
     * Checks if a stream originates from EZTV (either indicated by provider tag or title).
     * EZTV streams are excluded from Torrentio for TV series to prevent duplicating native EZTV results.
     */
    fun isEztvStream(stream: TorrentioStream): Boolean {
        val title = stream.title.orEmpty()
        val provider = extractProvider(title)
        if (provider.contains("EZTV", ignoreCase = true)) return true
        if (title.contains("EZTV", ignoreCase = true)) return true
        val filename = stream.behaviorHints?.filename.orEmpty()
        return filename.contains("EZTV", ignoreCase = true)
    }

    /** Extracts the number of seeders from the stream title (e.g. "👤 281"). */
    fun extractSeeds(title: String?): Int {
        if (title.isNullOrBlank()) return 0
        return SEEDS_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    /** Extracts the formatted human-readable size string (e.g. "8.91 GB"). */
    fun extractSize(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return SIZE_REGEX.find(title)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    /** Converts a human-readable size string (e.g. "8.91 GB" or "928 MB") into bytes. */
    fun parseSizeToBytes(sizeStr: String?): Long? {
        if (sizeStr.isNullOrBlank()) return null
        val match = SIZE_UNITS_REGEX.find(sizeStr) ?: return null
        val number = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2)?.uppercase(Locale.US) ?: return null

        val multiplier = when (unit) {
            "TB" -> 1024L * 1024L * 1024L * 1024L
            "GB" -> 1024L * 1024L * 1024L
            "MB" -> 1024L * 1024L
            "KB" -> 1024L
            else -> 1L
        }
        return (number * multiplier).toLong()
    }

    /** Extracts the provider or indexer name (e.g. "1337x", "ThePirateBay", "YTS"). */
    fun extractProvider(title: String?): String {
        if (title.isNullOrBlank()) return "Torrentio"
        return PROVIDER_REGEX.find(title)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Torrentio"
    }

    /**
     * Categorizes a release into a [VideoQualityGroup] and extracts its resolution label.
     */
    fun extractResolution(name: String?, title: String?, filename: String? = null): Pair<VideoQualityGroup, String> {
        val combined = "${name.orEmpty()} ${title.orEmpty()} ${filename.orEmpty()}".uppercase(Locale.US)
        return when {
            combined.contains("4K") || combined.contains("2160P") || combined.contains("UHD") -> {
                VideoQualityGroup.UHD_4K to "4K"
            }
            combined.contains("1080P") || combined.contains("FHD") -> {
                VideoQualityGroup.FHD_1080P to "1080p"
            }
            combined.contains("720P") || combined.contains("HD") -> {
                VideoQualityGroup.HD_720P to "720p"
            }
            else -> {
                val otherLabel = when {
                    combined.contains("480P") -> "480p"
                    combined.contains("BDRIP") -> "BDRip"
                    combined.contains("HDRIP") -> "HDRip"
                    combined.contains("DVDRIP") -> "DVDRip"
                    else -> "SD"
                }
                VideoQualityGroup.OTHER to otherLabel
            }
        }
    }

    /** Extracts video codec name (e.g. "AV1", "x265", "x264", "VP9"). */
    fun extractVideoCodec(title: String?, filename: String? = null): String? {
        val combined = "${title.orEmpty()} ${filename.orEmpty()}".uppercase(Locale.US)
        return when {
            combined.contains("AV1") -> "AV1"
            combined.contains("HEVC") || combined.contains("X265") || combined.contains("H.265") || combined.contains("H265") -> "x265"
            combined.contains("X264") || combined.contains("H.264") || combined.contains("H264") || combined.contains("AVC") -> "x264"
            combined.contains("VP9") -> "VP9"
            combined.contains("XVID") -> "XviD"
            else -> null
        }
    }

    /** Extracts visual tags such as HDR10, DV (Dolby Vision), 10-BIT. */
    fun extractVisualTags(name: String?, title: String?, filename: String? = null): List<String> {
        val combined = "${name.orEmpty()} ${title.orEmpty()} ${filename.orEmpty()}".uppercase(Locale.US)
        val tags = mutableListOf<String>()

        if (combined.contains("DV") || combined.contains("DOLBY VISION") || combined.contains("DOVI")) {
            tags.add("DV")
        }
        if (combined.contains("HDR10+")) {
            tags.add("HDR10+")
        } else if (combined.contains("HDR10")) {
            tags.add("HDR10")
        } else if (combined.contains("HDR")) {
            tags.add("HDR")
        }
        if (combined.contains("10BIT") || combined.contains("10-BIT")) {
            tags.add("10-BIT")
        }
        return tags.distinct()
    }

    /**
     * Extracts and standardizes audio codec quality.
     *
     * Rule: YTS torrents are explicitly identified as AAC (e.g. "AAC 5.1" or "AAC").
     * Torrentio and other indexers extract high-fidelity audio formats (DTS-HD MA, TrueHD, DDP, AC3, Opus, FLAC).
     */
    fun extractAudioCodec(
        title: String?,
        filename: String? = null,
        isYts: Boolean = false,
        audioChannels: String? = null
    ): String {
        if (isYts) {
            val channels = when {
                audioChannels?.contains("5.1") == true -> " 5.1"
                audioChannels?.contains("2.0") == true -> " 2.0"
                audioChannels?.contains("7.1") == true -> " 7.1"
                else -> ""
            }
            return "AAC$channels"
        }

        val rawCombined = "${title.orEmpty()} ${filename.orEmpty()}".uppercase(Locale.US)
        // Normalize common release filename separators for consistent matching
        val combined = rawCombined.replace('.', ' ').replace('_', ' ')

        return when {
            combined.contains("TRUEHD ATMOS") || (combined.contains("TRUEHD") && combined.contains("ATMOS")) -> "TrueHD Atmos"
            combined.contains("TRUEHD 7 1") || combined.contains("TRUEHD 7.1") -> "TrueHD 7.1"
            combined.contains("TRUEHD 5 1") || combined.contains("TRUEHD 5.1") || combined.contains("TRUEHD") -> "TrueHD"
            combined.contains("ATMOS") && (combined.contains("DDP") || combined.contains("EAC3")) -> "DDP 5.1 Atmos"
            combined.contains("DTS HD MA 7 1") || combined.contains("DTS-HD MA 7.1") -> "DTS-HD MA 7.1"
            combined.contains("DTS HD MA") || combined.contains("DTS-HD MA") || combined.contains("DTS-HD.MA") -> "DTS-HD MA 5.1"
            combined.contains("DTS HD") || combined.contains("DTS-HD") -> "DTS-HD"
            combined.contains("DTS:X") || combined.contains("DTS X") || combined.contains("DTS-X") -> "DTS:X"
            combined.contains("DTS 5 1") || combined.contains("DTS 5.1") || combined.contains("DTS") -> "DTS 5.1"
            combined.contains("DDP 7 1") || combined.contains("DDP7 1") || combined.contains("DDP 7.1") || combined.contains("DDP7.1") -> "DDP 7.1"
            combined.contains("DDP 5 1") || combined.contains("DDP5 1") || combined.contains("DDP 5.1") || combined.contains("DDP5.1") ||
                    combined.contains("EAC3") || combined.contains("E-AC-3") || combined.contains("DD+ 5 1") || combined.contains("DD+ 5.1") -> "DDP 5.1"
            combined.contains("DDP") || combined.contains("DD+") -> "DDP"
            combined.contains("AC3 5 1") || combined.contains("AC3 5.1") || combined.contains("DD 5 1") || combined.contains("DD 5.1") || combined.contains("DD5 1") -> "AC3 5.1"
            combined.contains("AC3 2 0") || combined.contains("AC3 2.0") || combined.contains("DD 2 0") || combined.contains("DD 2.0") -> "AC3 2.0"
            combined.contains("AC3") || combined.contains("AC-3") || combined.contains("DOLBY DIGITAL") -> "AC3"
            combined.contains("FLAC 5 1") || combined.contains("FLAC 5.1") -> "FLAC 5.1"
            combined.contains("FLAC") -> "FLAC"
            combined.contains("OPUS 5 1") || combined.contains("OPUS 5.1") -> "Opus 5.1"
            combined.contains("OPUS") -> "Opus"
            combined.contains("AAC 5 1") || combined.contains("AAC 5.1") -> "AAC 5.1"
            combined.contains("AAC 2 0") || combined.contains("AAC 2.0") -> "AAC 2.0"
            combined.contains("AAC") -> "AAC"
            combined.contains("7 1") || combined.contains("7.1") -> "7.1 Audio"
            combined.contains("5 1") || combined.contains("5.1") || combined.contains("6CH") -> "5.1 Audio"
            else -> "Audio Estándar"
        }
    }
}
