@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import com.martinrevert.latorrentola.utils.MediaIdHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.model.TMDB.TmdbTvEpisode
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.service.TorrentBufferState
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.components.ApiErrorView
import com.martinrevert.latorrentola.ui.components.DeleteTorrentDownloadsDialog
import com.martinrevert.latorrentola.ui.components.EpisodeTorrentShimmerCard
import com.martinrevert.latorrentola.ui.components.InsufficientStorageDialog
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.components.TorrentReleaseShimmerCard
import com.martinrevert.latorrentola.ui.components.WatchProgressBar
import com.martinrevert.latorrentola.ui.components.WatchProgressOptionDialog
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.ui.theme.hazeAppBarContainerColor
import com.martinrevert.latorrentola.ui.theme.hazeAppBarGlass
import com.martinrevert.latorrentola.ui.theme.rememberHandheldHazeState
import com.martinrevert.latorrentola.utils.MediaControllerHelper
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentCardActionHandler
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import com.martinrevert.latorrentola.utils.isTvDevice
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale

/**
 * Screen displaying details and available EZTV torrent releases for an episode,
 * with full-width still photo hero banner and D-pad navigation fixes.
 *
 * @param viewModel ViewModel handling state and downloads for this episode.
 * @param seriesId TMDB TV series identifier.
 * @param seasonNumber Season number of the episode.
 * @param episodeNumber Episode number within season.
 * @param seriesName Series display name.
 * @param episodeJson Serialized [TmdbTvEpisode] payload when available.
 * @param onBackClick Navigation callback to return to previous screen.
 * @param themeMode User selected theme setting for mode propagation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun TvEpisodeDetailScreen(
    viewModel: TvEpisodeDetailViewModel,
    seriesId: Int,
    seasonNumber: Int,
    episodeNumber: Int,
    seriesName: String = "",
    episodeJson: String? = null,
    onBackClick: () -> Unit,
    themeMode: Int? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val torrentHandlingMode by viewModel.torrentHandlingMode.collectAsState()
    val watchHistoryMap by viewModel.watchHistoryMap.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val mediaController by MediaControllerHelper.rememberMediaController()
    val activeBuffer by TorrentDownloadService.activeBufferState.collectAsState()
    var torrentToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }
    val hazeState = rememberHandheldHazeState(isTv)
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstTorrentFocusRequester = remember { FocusRequester() }
    val insufficientStorageState = remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(isTv) {
        if (isTv) {
            try {
                withFrameNanos { }
                backButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(seriesId, seasonNumber, episodeNumber) {
        viewModel.loadEpisode(
            seriesId = seriesId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            seriesName = seriesName,
            episodeJson = episodeJson
        )
    }

    val content = @Composable {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            when (val state = uiState) {
                TvEpisodeDetailUiState.Loading -> {
                    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    if (isTv) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MovieDetailPlaceholder(
                                contentPadding = PaddingValues(
                                    top = statusBarHeight + 64.dp,
                                    start = 48.dp,
                                    end = 48.dp,
                                    bottom = 16.dp + navBarHeight
                                )
                            )
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(16.dp)
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                                    .focusRequester(backButtonFocusRequester)
                                    .focusHighlight(shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_desc),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                            ) {
                                MovieDetailPlaceholder(
                                    contentPadding = PaddingValues(
                                        top = statusBarHeight + 64.dp,
                                        start = 16.dp,
                                        end = 16.dp,
                                        bottom = 16.dp + navBarHeight
                                    )
                                )
                            }
                            TopAppBar(
                                title = {},
                                navigationIcon = {
                                    IconButton(
                                        onClick = onBackClick,
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.back_desc),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = hazeAppBarContainerColor(hazeState != null, 0.85f)
                                ),
                                modifier = Modifier.hazeAppBarGlass(hazeState)
                            )
                        }
                    }
                }

                is TvEpisodeDetailUiState.Error -> if (isTv) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        ApiErrorView(
                            errorMessage = state.message,
                            onRetry = {
                                viewModel.loadEpisode(
                                    seriesId = seriesId,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    seriesName = seriesName,
                                    episodeJson = episodeJson
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_desc),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
                        ) {
                            ApiErrorView(
                                errorMessage = state.message,
                                onRetry = {
                                    viewModel.loadEpisode(
                                        seriesId = seriesId,
                                        seasonNumber = seasonNumber,
                                        episodeNumber = episodeNumber,
                                        seriesName = seriesName,
                                        episodeJson = episodeJson
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back_desc),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = hazeAppBarContainerColor(hazeState != null, 0.85f)
                            ),
                            modifier = Modifier.hazeAppBarGlass(hazeState)
                        )
                    }
                }

                is TvEpisodeDetailUiState.Success -> {
                    var showOptionDialog by remember { mutableStateOf(false) }
                    val mediaId = MediaIdHelper.forEpisode(
                        imdbId = state.imdbId,
                        seriesId = seriesId,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber
                    )
                    val progress = watchHistoryMap[mediaId]
                    val progressPercent = progress?.let { if (it.isCompleted) 100 else it.progressPercent }

                    EpisodeDetailContent(
                        seriesName = state.seriesName,
                        episode = state.episode,
                        posterUrl = state.posterUrl,
                        torrents = state.torrents,
                        releasesByQuality = state.releasesByQuality,
                        isDownloaded = state.isDownloaded,
                        progressPercent = progressPercent,
                        downloadedHashes = state.downloadedHashes,
                        allDownloads = allDownloads,
                        mediaController = mediaController,
                        hazeState = hazeState,
                        backButtonFocusRequester = backButtonFocusRequester,
                        firstTorrentFocusRequester = if (isTv) firstTorrentFocusRequester else null,
                        onBackClick = onBackClick,
                        onHeaderLongClick = { showOptionDialog = true },
                        onReleaseClick = { release ->
                            val title = "%s S%02dE%02d %s".format(
                                state.seriesName,
                                state.episode.seasonNumber,
                                state.episode.episodeNumber,
                                release.title
                            )
                            val nextEpisodeInfo = viewModel.getNextEpisodeInfo()?.let { ArrayList(it.map { item -> item.toString() }) }
                            val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(release.hash, ignoreCase = true) }
                            TorrentCardActionHandler.performCardClick(
                                context = context,
                                releaseHash = release.hash,
                                releaseTitle = title,
                                releaseMagnetUri = release.magnetUrl.orEmpty(),
                                releaseSizeBytes = release.sizeBytes,
                                download = matchingDownload,
                                activeBuffer = activeBuffer,
                                mediaController = mediaController,
                                isTv = isTv,
                                torrentHandlingMode = torrentHandlingMode,
                                seriesId = seriesId,
                                seasonNumber = state.episode.seasonNumber,
                                episodeNumber = state.episode.episodeNumber,
                                posterUrl = state.posterUrl,
                                nextEpisodeInfo = nextEpisodeInfo,
                                imdbId = state.imdbId,
                                onInsufficientStorage = { available, required ->
                                    insufficientStorageState.value = Pair(available, required)
                                }
                            )
                        },
                        onReleaseLongClick = { release ->
                            val title = "%s S%02dE%02d %s".format(
                                state.seriesName,
                                state.episode.seasonNumber,
                                state.episode.episodeNumber,
                                release.title
                            )
                            torrentToDelete = release.hash to title
                        },
                        onTorrentClick = { torrent ->
                            val title = "%s S%02dE%02d %s".format(
                                state.seriesName,
                                state.episode.seasonNumber,
                                state.episode.episodeNumber,
                                torrent.title
                            )
                            val magnetUri = torrent.magnetUrl.ifBlank {
                                torrent.hash.takeIf(String::isNotBlank)?.let {
                                    TorrentLaunchHelper.buildMagnetUri(it, title)
                                }.orEmpty()
                            }
                            if (magnetUri.isNotBlank()) {
                                val nextEpisodeInfo = viewModel.getNextEpisodeInfo()?.let { ArrayList(it.map { item -> item.toString() }) }
                                val sizeBytes = torrent.sizeBytes?.toLongOrNull()
                                when (
                                    val result = TorrentLaunchHelper.launch(
                                        context,
                                        torrentHandlingMode,
                                        magnetUri,
                                        title,
                                        nextEpisodeInfo,
                                        seriesId = seriesId,
                                        seasonNumber = state.episode.seasonNumber,
                                        episodeNumber = state.episode.episodeNumber,
                                        posterUrl = state.posterUrl,
                                        requiredSizeBytes = sizeBytes,
                                        imdbId = state.imdbId
                                    )
                                ) {
                                    TorrentLaunchResult.Started -> {
                                        // Launch started
                                    }
                                    is TorrentLaunchResult.InsufficientStorage -> {
                                        insufficientStorageState.value = Pair(result.availableBytes, result.requiredBytes)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    )

                    if (showOptionDialog) {
                        WatchProgressOptionDialog(
                            title = stringResource(R.string.mark_episode_title),
                            option1Text = stringResource(R.string.mark_episode_completed),
                            option2Text = stringResource(R.string.mark_episode_uncompleted),
                            onOption1Selected = {
                                viewModel.markEpisodeAsCompleted(state.seriesName, state.episode)
                            },
                            onOption2Selected = {
                                viewModel.markEpisodeAsUncompleted()
                            },
                            onDismissRequest = { showOptionDialog = false }
                        )
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

    val storageData = insufficientStorageState.value
    if (storageData != null) {
        InsufficientStorageDialog(
            availableBytes = storageData.first,
            requiredBytes = storageData.second,
            onDismiss = { insufficientStorageState.value = null }
        )
    }

    torrentToDelete?.let { (hash, title) ->
        DeleteTorrentDownloadsDialog(
            mediaTitle = title,
            onConfirmDelete = {
                torrentToDelete = null
                viewModel.removeEpisodeDownload(hash)
                TorrentCardActionHandler.performCardDelete(context, hash, mediaController)
            },
            onKeep = {
                torrentToDelete = null
            }
        )
    }
}

/**
 * Renders the episode still photo hero banner, episode metadata, and responsive grid
 * of available torrent releases.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeDetailContent(
    seriesName: String,
    episode: TmdbTvEpisode,
    posterUrl: String?,
    torrents: List<EztvTorrent>,
    releasesByQuality: Map<VideoQualityGroup, List<TorrentRelease>> = emptyMap(),
    isDownloaded: Boolean = false,
    progressPercent: Int? = null,
    downloadedHashes: Set<String> = emptySet(),
    allDownloads: List<TorrentDownload> = emptyList(),
    mediaController: MediaController? = null,
    hazeState: HazeState? = null,
    backButtonFocusRequester: FocusRequester,
    firstTorrentFocusRequester: FocusRequester?,
    onBackClick: () -> Unit,
    onHeaderLongClick: (() -> Unit)? = null,
    onReleaseClick: ((TorrentRelease) -> Unit)? = null,
    onReleaseLongClick: ((TorrentRelease) -> Unit)? = null,
    onTorrentClick: (EztvTorrent) -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val stillUrl = episode.fullStillUrl ?: posterUrl
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val heroHeight = if (isTv) 340.dp else if (isWideScreen) 300.dp else 240.dp

    LaunchedEffect(Unit) {
        if (isTv) {
            try {
                withFrameNanos { }
                backButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(state = hazeState) else Modifier)
        ) {
        // 1. Edge-to-Edge Episode Still Hero Banner (Fixed in background)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .then(
                    if (!isTv && onHeaderLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onHeaderLongClick
                        )
                    } else Modifier
                )
        ) {
            if (!stillUrl.isNullOrBlank()) {
                AsyncImage(
                    model = stillUrl,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // Top gradient scrim for status bar and back button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Horizontal gradient scrim on TV and wide screens
            if (isTv || isWideScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Bottom gradient scrim fading to solid background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )

            // Industry-standard Red Streaming Watch Progress Bar
            WatchProgressBar(
                progressPercent = progressPercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
            )
        }

        // 2. Scrollable Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(bottom = navBarHeight + 24.dp)
        ) {
            Spacer(modifier = Modifier.height(heroHeight - 48.dp))

        // 2. Episode Information Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isTv) 48.dp else if (isWideScreen) 28.dp else 16.dp,
                    end = if (isTv) 48.dp else if (isWideScreen) 28.dp else 16.dp,
                    top = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (seriesName.isNotBlank()) {
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = stringResource(R.string.tv_episode_downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                text = "S%02dE%02d · %s".format(
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.name.orEmpty()
                ),
                style = if (isWideScreen || isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Metadata Badges (Air Date, Runtime, Rating)
            val metadataList = listOfNotNull(
                episode.airDate?.takeIf(String::isNotBlank),
                episode.runtime?.let { stringResource(R.string.tv_episode_runtime, it) },
                episode.voteAverage?.takeIf { it > 0.0 }?.let { "★ %.1f".format(Locale.US, it) }
            )
            if (metadataList.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    metadataList.forEach { badge ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            if (!episode.overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Available Torrents Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (isTv) 48.dp else if (isWideScreen) 28.dp else 16.dp,
                    end = if (isTv) 48.dp else if (isWideScreen) 28.dp else 16.dp
                )
        ) {
            Text(
                text = stringResource(R.string.tv_available_torrents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (releasesByQuality.isNotEmpty()) {
                var isFirstRelease = true
                releasesByQuality.forEach { (qualityGroup, releases) ->
                    if (releases.isNotEmpty()) {
                        val groupHeader = when (qualityGroup) {
                            VideoQualityGroup.UHD_4K -> stringResource(R.string.quality_group_4k)
                            VideoQualityGroup.FHD_1080P -> stringResource(R.string.quality_group_1080p)
                            VideoQualityGroup.HD_720P -> stringResource(R.string.quality_group_720p)
                            VideoQualityGroup.OTHER -> stringResource(R.string.quality_group_other)
                        }
                        Text(
                            text = groupHeader,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                        )

                        if (isWideScreen && !isTv) {
                            // Responsive 2-column layout on wide handhelds and tablets
                            val chunkedReleases = remember(releases) { releases.chunked(2) }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                chunkedReleases.forEach { rowReleases ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowReleases.forEach { release ->
                                            val isTorrentDownloaded = downloadedHashes.contains(release.hash.lowercase())
                                            val releaseHash = release.hash
                                            val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(releaseHash, ignoreCase = true) }
                                            Box(modifier = Modifier.weight(1f)) {
                                                TorrentReleaseShimmerCard(
                                                    release = release,
                                                    isDownloaded = isTorrentDownloaded || matchingDownload?.state == TorrentDownloadService.STATE_COMPLETED,
                                                    onClick = { onReleaseClick?.invoke(release) },
                                                    onLongClick = { onReleaseLongClick?.invoke(release) },
                                                    download = matchingDownload,
                                                    mediaController = mediaController
                                                )
                                            }
                                        }
                                        if (rowReleases.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            // 1-column layout on phones and Android TV
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                releases.forEach { release ->
                                    val isFirst = isFirstRelease
                                    isFirstRelease = false
                                    val isTorrentDownloaded = downloadedHashes.contains(release.hash.lowercase())
                                    val releaseHash = release.hash
                                    val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(releaseHash, ignoreCase = true) }
                                    TorrentReleaseShimmerCard(
                                        release = release,
                                        isDownloaded = isTorrentDownloaded || matchingDownload?.state == TorrentDownloadService.STATE_COMPLETED,
                                        onClick = { onReleaseClick?.invoke(release) },
                                        onLongClick = { onReleaseLongClick?.invoke(release) },
                                        download = matchingDownload,
                                        mediaController = mediaController,
                                        modifier = if (isFirst && firstTorrentFocusRequester != null) {
                                            Modifier
                                                .focusRequester(firstTorrentFocusRequester)
                                                .focusProperties { up = backButtonFocusRequester }
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (torrents.isEmpty()) {
                Text(
                    text = stringResource(R.string.tv_no_torrents_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    torrents.forEachIndexed { index, torrent ->
                        val isTorrentDownloaded = torrent.downloadInfoHash()?.let(downloadedHashes::contains) == true
                        EpisodeTorrentShimmerCard(
                            torrent = torrent,
                            isDownloaded = isTorrentDownloaded,
                            onClick = { onTorrentClick(torrent) },
                            modifier = if (index == 0 && firstTorrentFocusRequester != null) {
                                Modifier
                                    .focusRequester(firstTorrentFocusRequester)
                                    .focusProperties { up = backButtonFocusRequester }
                            } else Modifier
                        )
                    }
                }
            }
        }
    }
    }

    if (isTv) {
        // 3. Floating Circular Back Button for TV D-pad focus
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(24.dp)
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), CircleShape)
                .focusRequester(backButtonFocusRequester)
                .focusHighlight(shape = CircleShape)
                .focusProperties {
                    if (firstTorrentFocusRequester != null) {
                        down = firstTorrentFocusRequester
                    }
                }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back_desc),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        // 3. Floating Glass Top Navigation Bar on Handheld
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = hazeAppBarContainerColor(hazeState != null, 0.85f)
            ),
            modifier = Modifier.hazeAppBarGlass(hazeState)
        )
    }
    }
}

@LightDarkPreviews
@Composable
private fun EpisodeDetailContentPreview() {
    LaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK) {
        EpisodeDetailContent(
            seriesName = "Breaking Bad",
            episode = TmdbTvEpisode(
                id = 1,
                name = "Pilot",
                overview = "A high school chemistry teacher discovers he has lung cancer.",
                seasonNumber = 1,
                episodeNumber = 1,
                runtime = 58,
                voteAverage = 8.9,
                airDate = "2008-01-20"
            ),
            posterUrl = null,
            torrents = emptyList(),
            backButtonFocusRequester = remember { FocusRequester() },
            firstTorrentFocusRequester = null,
            onBackClick = {},
            onTorrentClick = {}
        )
    }
}
