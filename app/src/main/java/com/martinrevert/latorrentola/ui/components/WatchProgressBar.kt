package com.martinrevert.latorrentola.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme

/**
 * Industry-standard streaming red color (#E50914) explicitly mandated in AGENTS.md
 * as a formal exception to theme colors for all watch history progression bars and player scrubbers.
 */
val WatchProgressRed = Color(0xFFE50914)

/**
 * Standard semi-transparent white track background for watch history catalog indicators.
 */
val WatchProgressTrackColor = Color(0x33FFFFFF)

/**
 * Standalone, reactive Compose component that renders a movie/episode watch progression bar.
 *
 * @param progressPercent Progress from 0 to 100. If null or <= 0, the bar is not rendered.
 * @param modifier Custom layout modifier (e.g. alignment inside a Box).
 * @param height Thickness of the progress indicator line, defaults to 4.dp.
 */
@Composable
fun WatchProgressBar(
    progressPercent: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    if (progressPercent != null && progressPercent > 0) {
        val fraction = (progressPercent / 100f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            color = WatchProgressRed,
            trackColor = WatchProgressTrackColor,
            strokeCap = StrokeCap.Butt
        )
    }
}

/**
 * Standalone, reactive player timeline scrubber/slider enforcing the streaming red
 * accent for thumb and active track across all local and remote Cast player surfaces.
 *
 * @param value Current playback fraction from 0f to 1f.
 * @param onValueChange Callback invoked as the user scrubs.
 * @param onValueChangeFinished Callback invoked when user releases the scrub thumb.
 * @param modifier Custom layout modifier.
 * @param enabled Whether scrubbing is currently interactive.
 */
@Composable
fun WatchProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = WatchProgressRed,
            activeTrackColor = WatchProgressRed,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    )
}

@Preview(name = "Mobile Light - WatchProgressBar")
@Composable
private fun WatchProgressBarPreviewLight() {
    LaTorrentolaTheme(themeMode = 1) {
        Box(modifier = Modifier.padding(16.dp).background(Color.Black)) {
            WatchProgressBar(progressPercent = 65)
        }
    }
}

@Preview(name = "Mobile Dark - WatchProgressBar")
@Composable
private fun WatchProgressBarPreviewDark() {
    LaTorrentolaTheme(themeMode = 2) {
        Box(modifier = Modifier.padding(16.dp).background(Color.Black)) {
            WatchProgressBar(progressPercent = 40)
        }
    }
}

@Preview(name = "TV Form Factor - WatchProgressBar")
@Composable
private fun WatchProgressBarPreviewTv() {
    TvLaTorrentolaTheme(themeMode = 2) {
        Box(modifier = Modifier.padding(16.dp).background(Color.Black)) {
            WatchProgressBar(progressPercent = 80)
        }
    }
}

@Preview(name = "Mobile Dark - WatchProgressSlider")
@Composable
private fun WatchProgressSliderPreview() {
    LaTorrentolaTheme(themeMode = 2) {
        Column(modifier = Modifier.padding(16.dp)) {
            WatchProgressSlider(value = 0.45f, onValueChange = {})
        }
    }
}

@Preview(name = "TV Form Factor - WatchProgressSlider")
@Composable
private fun WatchProgressSliderTvPreview() {
    TvLaTorrentolaTheme(themeMode = 2) {
        Column(modifier = Modifier.padding(16.dp)) {
            WatchProgressSlider(value = 0.70f, onValueChange = {})
        }
    }
}
