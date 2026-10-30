@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.ott

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.TMDB.TmdbWatchProvider
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.runtime.withFrameNanos

/**
 * Screen displaying the global grid of OTT streaming provider logos.
 *
 * @param viewModel Exposes providers and remembers last clicked item.
 * @param isTvMode Whether browsing in TV Series mode or Movie mode.
 * @param onProviderClick Triggered when user selects a streaming provider.
 * @param onBackClick Returns to the previous destination.
 * @param themeMode User selected theme setting for mode propagation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OttProvidersScreen(
    viewModel: OttProvidersViewModel,
    isTvMode: Boolean,
    onProviderClick: (TmdbWatchProvider) -> Unit,
    onBackClick: () -> Unit,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }

    LaunchedEffect(isTvMode) {
        viewModel.loadProviders(isTvMode)
    }

    val content = @Composable {
        val uiState by viewModel.uiState.collectAsState()
        val lastClickedProviderId by viewModel.lastClickedProviderId.collectAsState()
        val gridState = rememberLazyGridState()
        val firstCardFocusRequester = remember { FocusRequester() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                if (isTvMode) R.string.ott_providers_title_tv
                                else R.string.ott_providers_title_movies
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .focusProperties { down = firstCardFocusRequester }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (val state = uiState) {
                is OttProvidersUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is OttProvidersUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = state.message.asString(context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isTv) {
                                Surface(
                                    onClick = { viewModel.loadProviders(isTvMode) },
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small)
                                ) {
                                    Text(
                                        text = stringResource(R.string.retry),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.loadProviders(isTvMode) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(text = stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
                is OttProvidersUiState.Success -> {
                    if (state.providers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ott_empty_catalog),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LaunchedEffect(state.providers) {
                            if (isTv && lastClickedProviderId == null && state.providers.isNotEmpty()) {
                                try {
                                    withFrameNanos { }
                                    firstCardFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        }

                        LaunchedEffect(lastClickedProviderId) {
                            val targetId = lastClickedProviderId
                            if (isTv && targetId != null && state.providers.isNotEmpty()) {
                                val index = state.providers.indexOfFirst { it.providerId == targetId }
                                if (index >= 0) {
                                    val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
                                    if (!isVisible) {
                                        gridState.scrollToItem(index)
                                    }
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isTv) 180.dp else 140.dp),
                            state = gridState,
                            contentPadding = PaddingValues(
                                top = innerPadding.calculateTopPadding() + 16.dp,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                                start = 16.dp,
                                end = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.providers, key = { it.providerId }) { provider ->
                                val isFirstItem = state.providers.firstOrNull()?.providerId == provider.providerId
                                val cardModifier = if (isFirstItem && isTv) {
                                    Modifier.focusRequester(firstCardFocusRequester)
                                } else Modifier

                                OttProviderCard(
                                    provider = provider,
                                    isTv = isTv,
                                    shouldRequestFocus = isTv && lastClickedProviderId == provider.providerId,
                                    onFocusRestored = { viewModel.clearLastClickedProviderId() },
                                    onClick = {
                                        viewModel.setLastClickedProviderId(provider.providerId)
                                        onProviderClick(provider)
                                    },
                                    modifier = cardModifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            content()
        }
    }
}

/**
 * Individual provider tile with TMDB CDN logo and provider title.
 */
@Composable
private fun OttProviderCard(
    provider: TmdbWatchProvider,
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shouldRequestFocus: Boolean = false,
    onFocusRestored: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    if (shouldRequestFocus) {
        LaunchedEffect(Unit) {
            try {
                withFrameNanos { }
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (isTv) {
        Surface(
            onClick = onClick,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && shouldRequestFocus) {
                        onFocusRestored()
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = provider.fullLogoUrl,
                    contentDescription = provider.providerName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = provider.providerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = provider.fullLogoUrl,
                    contentDescription = provider.providerName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = provider.providerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun OttProvidersScreenPreview() {
    val sampleProviders = listOf(
        TmdbWatchProvider(providerId = 8, providerName = "Netflix", logoPath = "/t2yyOv40HZeVlLjYsCsPHnWLk4W.jpg"),
        TmdbWatchProvider(providerId = 119, providerName = "Amazon Prime Video", logoPath = "/emthp39XA2zhcoYLK2OWQGI0eHb.jpg"),
        TmdbWatchProvider(providerId = 337, providerName = "Disney+", logoPath = "/7rwgEs15tFwyR9NPQ5vpzxTj19Q.jpg")
    )
    LaTorrentolaTheme {
        androidx.compose.material3.Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sampleProviders, key = { it.providerId }) { provider ->
                        OttProviderCard(
                            provider = provider,
                            isTv = false,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "TV Providers", showBackground = true, device = "id:tv_720p")
@Composable
private fun OttProvidersScreenTvPreview() {
    val sampleProviders = listOf(
        TmdbWatchProvider(providerId = 8, providerName = "Netflix", logoPath = "/t2yyOv40HZeVlLjYsCsPHnWLk4W.jpg"),
        TmdbWatchProvider(providerId = 119, providerName = "Amazon Prime Video", logoPath = "/emthp39XA2zhcoYLK2OWQGI0eHb.jpg"),
        TmdbWatchProvider(providerId = 337, providerName = "Disney+", logoPath = "/7rwgEs15tFwyR9NPQ5vpzxTj19Q.jpg")
    )
    TvLaTorrentolaTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sampleProviders, key = { it.providerId }) { provider ->
                        OttProviderCard(
                            provider = provider,
                            isTv = true,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}
