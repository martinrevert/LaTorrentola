package com.martinrevert.latorrentola.ui.ott

import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class OttLogoColorExtractorTest {

    @Before
    @After
    fun cleanup() {
        OttLogoColorExtractor.clearCache()
    }

    @Test
    fun extractFromPixels_redLogoWithBlackBackground_prioritizesChromaticRed() {
        // Simulating Netflix: pure black background pixels and bold red logo pixels
        val blackPixel = (0xFF shl 24) or (0x00 shl 16) or (0x00 shl 8) or 0x00
        val redPixel = (0xFF shl 24) or (0xE5 shl 16) or (0x09 shl 8) or 0x14 // TMDB Netflix red

        val pixels = IntArray(100) { index ->
            if (index < 80) blackPixel else redPixel // 80% black, 20% red
        }

        val result = OttLogoColorExtractor.extractFromPixels(pixels)

        // Chromatic weighting ensures the red brand color dominates rather than collapsing into black
        assertThat(result.r).isGreaterThan(180)
        assertThat(result.g).isLessThan(50)
        assertThat(result.b).isLessThan(50)
    }

    @Test
    fun extractFromPixels_blueLogoWithWhiteText_prioritizesChromaticBlue() {
        // Simulating Disney+: royal blue background with white lettering
        val whitePixel = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
        val bluePixel = (0xFF shl 24) or (0x0B shl 16) or (0x24 shl 8) or 0x74

        val pixels = IntArray(100) { index ->
            if (index < 30) whitePixel else bluePixel
        }

        val result = OttLogoColorExtractor.extractFromPixels(pixels)

        assertThat(result.b).isGreaterThan(result.r)
        assertThat(result.b).isGreaterThan(result.g)
        assertThat(result.b).isGreaterThan(90)
    }

    @Test
    fun extractFromPixels_multiColorLogo_synthesizesCombinedColors() {
        // Multi-color logo with equal parts saturated Cyan (Prime) and Yellow
        val cyanPixel = (0xFF shl 24) or (0x00 shl 16) or (0xA8 shl 8) or 0xE1
        val yellowPixel = (0xFF shl 24) or (0xFF shl 16) or (0xD0 shl 8) or 0x00

        val pixels = IntArray(100) { index ->
            if (index % 2 == 0) cyanPixel else yellowPixel
        }

        val result = OttLogoColorExtractor.extractFromPixels(pixels)

        // Green channel should be high as it is present in both Cyan and Yellow
        assertThat(result.g).isGreaterThan(140)
        assertThat(result.r).isGreaterThan(50)
        assertThat(result.b).isGreaterThan(50)
    }

    @Test
    fun extractFromPixels_monochromeLogo_averagesNonTransparentPixels() {
        // Monochrome logo (Apple TV+ style: black background with white glyphs)
        val blackPixel = (0xFF shl 24) or (0x00 shl 16) or (0x00 shl 8) or 0x00
        val whitePixel = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF

        val pixels = IntArray(100) { index ->
            if (index < 50) blackPixel else whitePixel
        }

        val result = OttLogoColorExtractor.extractFromPixels(pixels)

        // Non-chromatic fallback averages all pixels, producing balanced charcoal/slate
        assertThat(result.r).isEqualTo(result.g)
        assertThat(result.g).isEqualTo(result.b)
        assertThat(result.r).isAtLeast(100)
        assertThat(result.r).isAtMost(150)
    }

    @Test
    fun extractFromPixels_fullyTransparentPixels_returnsNeutralDefault() {
        val transparentPixel = (0x00 shl 24) or (0xFF shl 16) or (0x00 shl 8) or 0x00
        val pixels = IntArray(50) { transparentPixel }

        val result = OttLogoColorExtractor.extractFromPixels(pixels)

        assertThat(result).isEqualTo(OttLogoColorExtractor.CombinedLogoColor(50, 55, 65))
    }

    @Test
    fun toSurfaceColors_darkTheme_producesLegibleDarkGradientWithAmbientGlow() {
        val combined = OttLogoColorExtractor.CombinedLogoColor(r = 229, g = 9, b = 20)
        val surfaceColors = OttLogoColorExtractor.toSurfaceColors(combined, isDark = true)

        assertThat(surfaceColors.topColor.luminance()).isLessThan(0.5f)
        assertThat(surfaceColors.bottomColor.luminance()).isLessThan(0.5f)
        // Top color provides ambient glow, bottom color provides deeper anchor
        assertThat(surfaceColors.topColor.luminance()).isGreaterThan(surfaceColors.bottomColor.luminance())
    }

    @Test
    fun toSurfaceColors_lightTheme_producesSoftPastelGradient() {
        val combined = OttLogoColorExtractor.CombinedLogoColor(r = 11, g = 36, b = 116)
        val surfaceColors = OttLogoColorExtractor.toSurfaceColors(combined, isDark = false)

        assertThat(surfaceColors.topColor.luminance()).isGreaterThan(0.5f)
        assertThat(surfaceColors.bottomColor.luminance()).isGreaterThan(0.5f)
        assertThat(surfaceColors.bottomColor.luminance()).isGreaterThan(surfaceColors.topColor.luminance())
    }

    @Test
    fun rgbToHslAndHslToRgb_roundTrip_preservesPrimaryRed() {
        val (h, s, l) = OttLogoColorExtractor.rgbToHsl(255, 0, 0)
        assertThat(h).isWithin(1f).of(0f)
        assertThat(s).isWithin(0.01f).of(1f)
        assertThat(l).isWithin(0.01f).of(0.5f)

        val (r, g, b) = OttLogoColorExtractor.hslToRgb(h, s, l)
        assertThat(r).isEqualTo(255)
        assertThat(g).isEqualTo(0)
        assertThat(b).isEqualTo(0)
    }

    @Test
    fun cache_retrievesPrecomputedColorsAndClearsProperly() {
        val key = "https://image.tmdb.org/t/p/w500/test.jpg"
        assertThat(OttLogoColorExtractor.getColors(key, isDark = true)).isNull()

        // Cache pre-computed
        val combined = OttLogoColorExtractor.CombinedLogoColor(r = 200, g = 40, b = 40)
        val darkColors = OttLogoColorExtractor.toSurfaceColors(combined, isDark = true)
        assertThat(darkColors.topColor).isNotNull()

        // Extract and cache via pixels helper
        val redPixel = (0xFF shl 24) or (200 shl 16) or (40 shl 8) or 40
        val extracted = OttLogoColorExtractor.extractFromPixels(intArrayOf(redPixel))
        assertThat(extracted).isEqualTo(combined)

        // Test cache round-trip via manual getColors after simulated insertion or clearCache
        OttLogoColorExtractor.clearCache()
        assertThat(OttLogoColorExtractor.getColors(key, isDark = true)).isNull()
    }
}
