package com.martinrevert.latorrentola.ui.ott

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import coil3.BitmapImage
import coil3.Image
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Utility for extracting and caching a cohesive brand surface color from an OTT provider logo.
 *
 * It samples the chromatic pixels of the logo, computes a weighted average color based on
 * saturation and luminance, and maps the resulting color into an ambient surface gradient
 * suitable for dark and light theme containers.
 */
object OttLogoColorExtractor {

    data class CombinedLogoColor(val r: Int, val g: Int, val b: Int)

    data class OttSurfaceColors(
        val topColor: Color,
        val bottomColor: Color
    )

    private val cache = ConcurrentHashMap<String, CombinedLogoColor>()

    /**
     * Clears cached logo colors (e.g., for testing or memory pressure).
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Retrieves cached surface colors for [key] if available.
     */
    fun getColors(key: String, isDark: Boolean): OttSurfaceColors? {
        val combined = cache[key] ?: return null
        return toSurfaceColors(combined, isDark)
    }

    /**
     * Extracts brand color from a Coil [Image], caches it under [key], and returns [OttSurfaceColors].
     */
    fun extractAndCache(key: String, image: Image, isDark: Boolean): OttSurfaceColors {
        return try {
            val combined = extractFromCoilImage(image)
            cache[key] = combined
            toSurfaceColors(combined, isDark)
        } catch (_: Throwable) {
            val fallback = CombinedLogoColor(50, 55, 65)
            cache[key] = fallback
            toSurfaceColors(fallback, isDark)
        }
    }

    /**
     * Extracts brand color from a [Bitmap], caches it under [key], and returns [OttSurfaceColors].
     */
    fun extractAndCache(key: String, bitmap: Bitmap, isDark: Boolean): OttSurfaceColors {
        return try {
            val combined = extractFromBitmap(bitmap)
            cache[key] = combined
            toSurfaceColors(combined, isDark)
        } catch (_: Throwable) {
            val fallback = CombinedLogoColor(50, 55, 65)
            cache[key] = fallback
            toSurfaceColors(fallback, isDark)
        }
    }

    /**
     * Extracts brand color from a Coil [Image].
     */
    fun extractFromCoilImage(image: Image): CombinedLogoColor {
        return try {
            val bitmap = (image as? BitmapImage)?.bitmap ?: runCatching { image.toBitmap() }.getOrNull()
            if (bitmap != null) {
                extractFromBitmap(bitmap)
            } else {
                CombinedLogoColor(50, 55, 65)
            }
        } catch (_: Throwable) {
            CombinedLogoColor(50, 55, 65)
        }
    }

    /**
     * Extracts the combined brand color from a [Bitmap] by sampling up to a 32x32 grid.
     * Safely converts [Bitmap.Config.HARDWARE] bitmaps to software memory before sampling.
     */
    fun extractFromBitmap(bitmap: Bitmap): CombinedLogoColor {
        return try {
            val isHardware = bitmap.config == Bitmap.Config.HARDWARE
            val readableBitmap = if (isHardware) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return CombinedLogoColor(50, 55, 65)
            } else {
                bitmap
            }

            val width = readableBitmap.width
            val height = readableBitmap.height
            if (width <= 0 || height <= 0) {
                if (isHardware) readableBitmap.recycle()
                return CombinedLogoColor(50, 55, 65)
            }

            val sampleCols = 32.coerceAtMost(width)
            val sampleRows = 32.coerceAtMost(height)
            val pixels = IntArray(sampleCols * sampleRows)

            var idx = 0
            val stepX = width.toFloat() / sampleCols
            val stepY = height.toFloat() / sampleRows

            for (r in 0 until sampleRows) {
                val y = (r * stepY).toInt().coerceIn(0, height - 1)
                for (c in 0 until sampleCols) {
                    val x = (c * stepX).toInt().coerceIn(0, width - 1)
                    pixels[idx++] = readableBitmap.getPixel(x, y)
                }
            }

            if (isHardware) {
                readableBitmap.recycle()
            }

            extractFromPixels(pixels)
        } catch (_: Throwable) {
            CombinedLogoColor(50, 55, 65)
        }
    }

    /**
     * Combines pixels into a single representative brand color.
     *
     * Non-transparent pixels with chromatic content (saturation > 0.15, brightness > 0.12)
     * are prioritized and weighted by saturation squared * value so bold brand colors
     * dominate. Falls back to averaging all non-transparent pixels for monochrome logos.
     */
    fun extractFromPixels(pixels: IntArray): CombinedLogoColor {
        var totalChromaticWeight = 0.0
        var sumChromaticR = 0.0
        var sumChromaticG = 0.0
        var sumChromaticB = 0.0

        var totalNonTransparent = 0
        var sumAllR = 0.0
        var sumAllG = 0.0
        var sumAllB = 0.0

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 40) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            totalNonTransparent++
            sumAllR += r
            sumAllG += g
            sumAllB += b

            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            val delta = max - min
            val sat = if (max == 0f) 0f else delta / max
            val value = max

            if (sat > 0.15f && value > 0.12f) {
                val weight = (sat * sat * value).toDouble()
                sumChromaticR += r * weight
                sumChromaticG += g * weight
                sumChromaticB += b * weight
                totalChromaticWeight += weight
            }
        }

        if (totalChromaticWeight > 0.0) {
            val finalR = (sumChromaticR / totalChromaticWeight).roundToInt().coerceIn(0, 255)
            val finalG = (sumChromaticG / totalChromaticWeight).roundToInt().coerceIn(0, 255)
            val finalB = (sumChromaticB / totalChromaticWeight).roundToInt().coerceIn(0, 255)
            return CombinedLogoColor(finalR, finalG, finalB)
        }

        if (totalNonTransparent > 0) {
            val finalR = (sumAllR / totalNonTransparent).roundToInt().coerceIn(0, 255)
            val finalG = (sumAllG / totalNonTransparent).roundToInt().coerceIn(0, 255)
            val finalB = (sumAllB / totalNonTransparent).roundToInt().coerceIn(0, 255)
            return CombinedLogoColor(finalR, finalG, finalB)
        }

        return CombinedLogoColor(50, 55, 65)
    }

    /**
     * Converts a [CombinedLogoColor] into an ambient vertical gradient pair for Dark or Light theme.
     */
    fun toSurfaceColors(combined: CombinedLogoColor, isDark: Boolean): OttSurfaceColors {
        val (h, s, _) = rgbToHsl(combined.r, combined.g, combined.b)

        return if (isDark) {
            val targetSat = if (s < 0.05f) 0f else s.coerceIn(0.30f, 0.70f)
            val (topR, topG, topB) = hslToRgb(h, targetSat, 0.20f)
            val (bottomR, bottomG, bottomB) = hslToRgb(h, targetSat * 0.8f, 0.10f)
            OttSurfaceColors(
                topColor = Color(topR, topG, topB),
                bottomColor = Color(bottomR, bottomG, bottomB)
            )
        } else {
            val targetSat = if (s < 0.05f) 0f else s.coerceIn(0.18f, 0.40f)
            val (topR, topG, topB) = hslToRgb(h, targetSat, 0.88f)
            val (bottomR, bottomG, bottomB) = hslToRgb(h, targetSat * 0.6f, 0.95f)
            OttSurfaceColors(
                topColor = Color(topR, topG, topB),
                bottomColor = Color(bottomR, bottomG, bottomB)
            )
        }
    }

    fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val l = (max + min) / 2f
        val s = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when {
            delta == 0f -> 0f
            max == rf -> (60f * (((gf - bf) / delta) % 6f) + 360f) % 360f
            max == gf -> (60f * (((bf - rf) / delta) + 2f)) % 360f
            else -> (60f * (((rf - gf) / delta) + 4f)) % 360f
        }
        return floatArrayOf(h, s, l)
    }

    fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val red = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val green = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val blue = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return Triple(red, green, blue)
    }
}
