package com.martinrevert.latorrentola.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import com.martinrevert.latorrentola.utils.isTvDevice

/**
 * Adaptive error presentation displaying the "Ezequiel 25:17" scene (thelord.png)
 * and humorous quote alongside the underlying Web API exception details.
 *
 * @param error Structured [UiText] error representation.
 * @param modifier Layout modifier applied to the outer container.
 * @param fillMaxSize Whether to expand to the full parent container dimensions. Defaults to true.
 * @param onRetry Optional callback invoked when the user requests an API retry.
 */
@Composable
fun ApiErrorView(
    error: UiText,
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = true,
    onRetry: (() -> Unit)? = null
) {
    ApiErrorView(
        errorMessage = error.asString(),
        modifier = modifier,
        fillMaxSize = fillMaxSize,
        onRetry = onRetry
    )
}

/**
 * Adaptive error presentation displaying the "Ezequiel 25:17" scene (thelord.png)
 * and humorous quote alongside the underlying Web API exception details.
 *
 * @param errorMessage Raw error description or HTTP status code returned by the Web API.
 * @param modifier Layout modifier applied to the outer container.
 * @param fillMaxSize Whether to expand to the full parent container dimensions. Defaults to true.
 * @param onRetry Optional callback invoked when the user requests an API retry.
 */
@Composable
fun ApiErrorView(
    errorMessage: String?,
    modifier: Modifier = Modifier,
    fillMaxSize: Boolean = true,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv && onRetry != null) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = (if (fillMaxSize) modifier.fillMaxSize() else modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.thelord),
                contentDescription = stringResource(R.string.api_error_title),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .sizeIn(
                        maxWidth = if (isTv) 180.dp else 160.dp,
                        maxHeight = if (isTv) 90.dp else 85.dp
                    )
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.api_error_title),
                style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.api_error_the_lord_quote),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = if (isTv) 16.sp else 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(if (isTv) 0.80f else 0.90f)
            )

            val displayError = errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_error)
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))

                if (isTv) {
                    TvSurface(
                        onClick = onRetry,
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.retry),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Preview(name = "Mobile Light - API Error", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Mobile Dark - API Error", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApiErrorViewMobilePreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_SYSTEM) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ApiErrorView(
                errorMessage = "HTTP 521: Web server is down",
                onRetry = {}
            )
        }
    }
}

@Preview(name = "TV Light - API Error", device = "id:tv_720p", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark - API Error", device = "id:tv_720p", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ApiErrorViewTvPreview() {
    TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_SYSTEM) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ApiErrorView(
                errorMessage = "HTTP 521: Web server is down",
                onRetry = {}
            )
        }
    }
}
