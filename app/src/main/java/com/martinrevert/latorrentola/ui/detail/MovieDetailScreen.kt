@file:Suppress("FunctionName")

package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import com.martinrevert.latorrentola.model.omdb.MovieRatings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalInspectionMode
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import coil3.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.martinrevert.latorrentola.R
import androidx.media3.session.MediaController
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import com.martinrevert.latorrentola.service.TorrentBufferState
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.ui.components.DeleteTorrentDownloadsDialog
import com.martinrevert.latorrentola.utils.MediaControllerHelper
import com.martinrevert.latorrentola.utils.TorrentCardActionHandler
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.ui.components.MovieTorrentShimmerCard
import com.martinrevert.latorrentola.ui.components.TorrentReleaseShimmerCard
import com.martinrevert.latorrentola.model.TMDB.TmdbActorDetail
import com.martinrevert.latorrentola.model.TMDB.TmdbCastCredit
import com.martinrevert.latorrentola.model.TMDB.TmdbMovieResult
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.YTS.Cast
import com.martinrevert.latorrentola.model.torrent.TorrentHandlingMode
import com.martinrevert.latorrentola.ui.components.ActorDetailBottomSheet
import com.martinrevert.latorrentola.ui.components.InsufficientStorageDialog
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Surface
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.UiText
import com.martinrevert.latorrentola.utils.IntentAppsFinder
import com.martinrevert.latorrentola.utils.TorrentLaunchHelper
import com.martinrevert.latorrentola.utils.TorrentLaunchResult

/**
 * Connects movie detail state and user actions to the detail presentation.
 *
 * @param viewModel Movie details and actor lookup state.
 * @param onBackClick Returns to the previous destination.
 * @param onTvSeriesClick Opens a TV series selected from actor filmography.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onTvSeriesClick: (Int) -> Unit = {},
    themeMode: Int = PreferenceManager.THEME_SYSTEM
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedHashes by viewModel.downloadedHashes.collectAsState()
    val torrentHandlingMode by viewModel.torrentHandlingMode.collectAsState()
    val selectedActorDetail by viewModel.selectedActorDetail.collectAsState()
    val isActorLoading by viewModel.isActorLoading.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val mediaController by MediaControllerHelper.rememberMediaController()
    val activeBuffer by TorrentDownloadService.activeBufferState.collectAsState()
    var showActorSheet by remember { mutableStateOf(false) }
    val actorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val isTv = remember(context) { context.isTvDevice() }
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoice()
        }
    }

    LaunchedEffect(uiState) {
        (uiState as? DetailUiState.OpenTvDetail)?.let { tvState ->
            onTvSeriesClick(tvState.seriesId)
        }
    }

    MovieDetailScreenContent(
        uiState = uiState,
        downloadedHashes = downloadedHashes,
        allDownloads = allDownloads,
        mediaController = mediaController,
        activeBuffer = activeBuffer,
        torrentHandlingMode = torrentHandlingMode,
        isWideScreen = isWideScreen,
        isTv = isTv,
        themeMode = themeMode,
        selectedActorDetail = selectedActorDetail,
        isActorLoading = isActorLoading,
        showActorSheet = showActorSheet,
        actorSheetState = actorSheetState,
        onBackClick = onBackClick,
        onShareClick = { movie ->
            val imdbUrl = "https://www.imdb.com/title/${movie.imdbCode}"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                val shareText = resources.getString(
                    R.string.share_movie_text,
                    movie.title,
                    imdbUrl
                )
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, resources.getString(
                R.string.share_movie_chooser)))
        },
        onFavoriteToggle = { movie -> viewModel.toggleFavorite(movie) },
        onTorrentClick = { movie, torrent ->
            viewModel.markAsDownloaded(movie, torrent.hash ?: "", torrent.quality ?: "")
        },
        onReleaseClick = { movie, release ->
            viewModel.markAsDownloaded(movie, release.hash, release.qualityGroup.name)
        },
        onDeleteTorrent = { hash ->
            viewModel.removeMovieDownload(hash)
        },
        onAddLanguageToFilter = { language ->
            viewModel.addLanguageToFilter(language) { error ->
                Toast.makeText(context, error.asString(resources), Toast.LENGTH_LONG).show()
            }
        },
        onCastClick = { actorName ->
            showActorSheet = true
            viewModel.fetchActorDetails(actorName)
        },
        onDismissActorSheet = {
            showActorSheet = false
            viewModel.clearSelectedActor()
        },
        onActorCreditClick = { credit ->
            showActorSheet = false
            viewModel.clearSelectedActor()
            if (credit.mediaType == "tv") {
                onTvSeriesClick(credit.id)
            } else {
                viewModel.fetchAndOpenMovieByTmdbId(credit.id, credit.displayTitle)
            }
        }
    )
}

/**
 * Hosts the responsive detail screen, including app-bar and actor-sheet state.
 *
 * @param torrentHandlingMode Configured external or local torrent handling behavior.
 * @param onActorCreditClick Opens the selected movie or TV credit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieDetailScreenContent(
    uiState: DetailUiState,
    downloadedHashes: Set<String>,
    allDownloads: List<TorrentDownload> = emptyList(),
    mediaController: MediaController? = null,
    activeBuffer: TorrentBufferState? = null,
    torrentHandlingMode: TorrentHandlingMode = TorrentHandlingMode.EXTERNAL_CLIENT,
    isWideScreen: Boolean,
    isTv: Boolean,
    themeMode: Int = PreferenceManager.THEME_SYSTEM,
    selectedActorDetail: Result<TmdbActorDetail>?,
    isActorLoading: Boolean,
    showActorSheet: Boolean,
    actorSheetState: SheetState,
    onBackClick: () -> Unit,
    onShareClick: (Movie) -> Unit,
    onFavoriteToggle: (Movie) -> Unit,
    onTorrentClick: (Movie, Torrent) -> Unit,
    onReleaseClick: (Movie, TorrentRelease) -> Unit = { _, _ -> },
    onDeleteTorrent: (String) -> Unit = {},
    onAddLanguageToFilter: (String) -> Unit,
    onCastClick: (String) -> Unit,
    onDismissActorSheet: () -> Unit,
    onActorCreditClick: (TmdbCastCredit) -> Unit
) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val contentFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val insufficientStorageState = remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var torrentToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (isTv) {
        when (uiState) {
            is DetailUiState.Loading, is DetailUiState.OpenTvDetail -> {
                MovieDetailPlaceholder(contentPadding = PaddingValues(top = 48.dp, start = 48.dp, end = 48.dp))
            }
            is DetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message.asString(), color = MaterialTheme.colorScheme.error)
                }
            }
            is DetailUiState.Success -> {
                TvMovieDetailContent(
                    movie = uiState.movie,
                    tmdbMovie = uiState.tmdbMovie,
                    director = uiState.director,
                    studioLogoUrl = uiState.studioLogoUrl,
                    studioName = uiState.studioName,
                    ratings = uiState.ratings,
                    backdropUrl = uiState.backdropUrl,
                    isFavorite = uiState.isFavorite,
                    releasesByQuality = uiState.releasesByQuality,
                    downloadedHashes = downloadedHashes,
                    allDownloads = allDownloads,
                    mediaController = mediaController,
                    torrentHandlingMode = torrentHandlingMode,
                    onBackClick = onBackClick,
                    onShareClick = { onShareClick(uiState.movie) },
                    onFavoriteToggle = { onFavoriteToggle(uiState.movie) },
                    onTorrentClick = { onTorrentClick(uiState.movie, it) },
                    onReleaseClick = { release ->
                        val moviePoster = uiState.movie.mediumCoverImage ?: uiState.movie.largeCoverImage
                        val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(release.hash, ignoreCase = true) }
                        TorrentCardActionHandler.performCardClick(
                            context = context,
                            releaseHash = release.hash,
                            releaseTitle = uiState.movie.title ?: release.title,
                            releaseMagnetUri = release.magnetUrl.orEmpty(),
                            releaseSizeBytes = release.sizeBytes,
                            download = matchingDownload,
                            activeBuffer = activeBuffer,
                            mediaController = mediaController,
                            isTv = true,
                            torrentHandlingMode = torrentHandlingMode,
                            movieId = uiState.movie.id,
                            posterUrl = moviePoster,
                            onInsufficientStorage = { available, required ->
                                insufficientStorageState.value = Pair(available, required)
                            },
                            onDownloadStarted = {
                                onReleaseClick(uiState.movie, release)
                            }
                        )
                    },
                    onReleaseLongClick = { release ->
                        torrentToDelete = release.hash to (uiState.movie.title ?: release.title)
                    },
                    onAddLanguageToFilter = onAddLanguageToFilter,
                    onCastClick = onCastClick,
                    onInsufficientStorage = { available, required ->
                        insufficientStorageState.value = Pair(available, required)
                    },
                    themeMode = themeMode
                )
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    modifier = Modifier
                        .focusProperties { down = contentFocusRequester }
                        .hazeGlass(input = HazeInput.Sources(hazeState)),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor),
                    title = { Text((uiState as? DetailUiState.Success)?.movie?.title ?: stringResource(R.string.details_title)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .focusProperties { down = contentFocusRequester }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                                    }
                                }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                        }
                    },
                    actions = {
                        if (uiState is DetailUiState.Success) {
                            IconButton(
                                onClick = { onShareClick(uiState.movie) },
                                modifier = Modifier
                                    .focusHighlight(shape = CircleShape)
                                    .focusProperties { down = contentFocusRequester }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                                        }
                                    }
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_desc)
                                )
                            }
                            IconButton(
                                onClick = { onFavoriteToggle(uiState.movie) },
                                modifier = Modifier
                                    .focusHighlight(shape = CircleShape)
                                    .focusProperties { down = contentFocusRequester }
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                                        }
                                    }
                            ) {
                                Icon(
                                    if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.favorite_desc)
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val detailContentPadding = PaddingValues(
                top = 64.dp + statusBarHeight + 16.dp,
                bottom = 16.dp + navBarHeight,
                start = 16.dp,
                end = 16.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                .consumeWindowInsets(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                when (uiState) {
                    is DetailUiState.Loading, is DetailUiState.OpenTvDetail -> {
                        MovieDetailPlaceholder(contentPadding = detailContentPadding)
                    }
                    is DetailUiState.Success -> {
                        MovieDetailContent(
                            movie = uiState.movie,
                            releasesByQuality = uiState.releasesByQuality,
                            downloadedHashes = downloadedHashes,
                            allDownloads = allDownloads,
                            mediaController = mediaController,
                            torrentHandlingMode = torrentHandlingMode,
                            isWideScreen = isWideScreen,
                            isTv = isTv,
                            onTorrentClick = { onTorrentClick(uiState.movie, it) },
                            onReleaseClick = { release ->
                                val moviePoster = uiState.movie.mediumCoverImage ?: uiState.movie.largeCoverImage
                                val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(release.hash, ignoreCase = true) }
                                TorrentCardActionHandler.performCardClick(
                                    context = context,
                                    releaseHash = release.hash,
                                    releaseTitle = uiState.movie.title ?: release.title,
                                    releaseMagnetUri = release.magnetUrl.orEmpty(),
                                    releaseSizeBytes = release.sizeBytes,
                                    download = matchingDownload,
                                    activeBuffer = activeBuffer,
                                    mediaController = mediaController,
                                    isTv = isTv,
                                    torrentHandlingMode = torrentHandlingMode,
                                    movieId = uiState.movie.id,
                                    posterUrl = moviePoster,
                                    onInsufficientStorage = { available, required ->
                                        insufficientStorageState.value = Pair(available, required)
                                    },
                                    onDownloadStarted = {
                                        onReleaseClick(uiState.movie, release)
                                    }
                                )
                            },
                            onReleaseLongClick = { release ->
                                torrentToDelete = release.hash to (uiState.movie.title ?: release.title)
                            },
                            onAddLanguageToFilter = onAddLanguageToFilter,
                            onCastClick = onCastClick,
                            onInsufficientStorage = { available, required ->
                                insufficientStorageState.value = Pair(available, required)
                            },
                            contentFocusRequester = contentFocusRequester,
                            contentPadding = detailContentPadding,
                            scrollState = scrollState
                        )
                    }
                    is DetailUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.message.asString())
                        }
                    }
                }
            }
        }
    }

    if (showActorSheet) {
        ActorDetailBottomSheet(
            actorDetailState = selectedActorDetail,
            isLoading = isActorLoading,
            onDismiss = onDismissActorSheet,
            sheetState = actorSheetState,
            hazeState = hazeState,
            onCreditClick = onActorCreditClick,
            themeMode = themeMode
        )
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
                onDeleteTorrent(hash)
                TorrentCardActionHandler.performCardDelete(context, hash, mediaController)
            },
            onKeep = {
                torrentToDelete = null
            }
        )
    }
}


/**
 * 10-foot TV movie detail layout with edge-to-edge backdrop, cinematic hero, action buttons,
 * smooth D-pad navigation, cast row, and torrent options.
 */
@Composable
private fun TvMovieDetailContent(
    movie: Movie,
    tmdbMovie: TmdbMovieResult?,
    director: String? = null,
    studioLogoUrl: String? = null,
    studioName: String? = null,
    ratings: MovieRatings? = null,
    backdropUrl: String?,
    isFavorite: Boolean,
    releasesByQuality: Map<VideoQualityGroup, List<TorrentRelease>>,
    downloadedHashes: Set<String>,
    allDownloads: List<TorrentDownload>,
    mediaController: MediaController?,
    torrentHandlingMode: TorrentHandlingMode,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onTorrentClick: (Torrent) -> Unit,
    onReleaseClick: (TorrentRelease) -> Unit,
    onReleaseLongClick: (TorrentRelease) -> Unit,
    onAddLanguageToFilter: (String) -> Unit,
    onCastClick: (String) -> Unit,
    onInsufficientStorage: (Long, Long) -> Unit,
    themeMode: Int? = null,
    modifier: Modifier = Modifier
) {
    var showTrailerDialog by remember { mutableStateOf(false) }
    val firstActionFocusRequester = remember { FocusRequester() }
    val hasTrailer = !movie.ytTrailerCode.isNullOrEmpty()

    var isRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isRevealed = true
        firstActionFocusRequester.requestFocus()
    }

    val posterScale by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0.70f,
        animationSpec = tween(durationMillis = 650, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "posterScale"
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "posterAlpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100),
        label = "contentAlpha"
    )
    val contentTranslationY by animateFloatAsState(
        targetValue = if (isRevealed) 0f else 32f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "contentTranslationY"
    )
    val backdropAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "backdropAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 1. Edge-to-Edge Dynamic TMDB Backdrop
        if (!backdropUrl.isNullOrEmpty()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .graphicsLayer { alpha = backdropAlpha }
            )
            // Top gradient scrim for transparent navigation and status bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Left horizontal gradient scrim for high-contrast reading
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.85f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Bottom vertical gradient scrim fading seamlessly to solid surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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
        }

        // 2. 10-foot Cinematic Scrollable Content
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.focusHighlight(shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Hero Section: Morphing Poster Card on the Left, Title & Details on the Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Movie Poster Card (The animated morph/zoom anchor from catalog)
                val posterUrl = movie.largeCoverImage
                    ?: movie.mediumCoverImage
                    ?: tmdbMovie?.fullPosterUrl

                if (!posterUrl.isNullOrEmpty()) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .width(190.dp)
                            .aspectRatio(2 / 3f)
                            .graphicsLayer {
                                scaleX = posterScale
                                scaleY = posterScale
                                alpha = posterAlpha
                            }
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.medium
                            )
                    ) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Movie Info Column (Title, Badges, Action Buttons, Synopsis)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = contentAlpha
                            translationY = contentTranslationY
                        }
                ) {
                    // Movie Title
                    Text(
                        text = movie.title ?: tmdbMovie?.title.orEmpty(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Metadata Badges Row (Year, Duration, MPA, Language, Genres)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val releaseYear = movie.year?.takeIf { it != 0 }?.toString()
                            ?: tmdbMovie?.releaseDate?.take(4)
                        if (!releaseYear.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = releaseYear,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!movie.runtime.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = movie.runtime,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!movie.mpaRating.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = movie.mpaRating,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        movie.language?.let { lang ->
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = lang.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        movie.genres?.take(3)?.forEach { genre ->
                            val translatedGenre = GenreTranslation.getGenreText(genre).asString()
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = translatedGenre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Director & Studio Information
                    if (!director.isNullOrBlank() || !studioLogoUrl.isNullOrBlank() || !studioName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!director.isNullOrBlank()) {
                                Text(
                                    text = stringResource(R.string.directed_by, director),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!studioLogoUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = studioLogoUrl,
                                        contentDescription = studioName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .height(24.dp)
                                            .widthIn(max = 110.dp)
                                    )
                                }
                            } else if (!studioName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = studioName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // External Ratings Row (IMDb, Rotten Tomatoes, Metacritic)
                    val effectiveImdbRating = ratings?.imdbRating
                        ?: movie.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let { "$it/10" }
                        ?: tmdbMovie?.voteAverage?.takeIf { it > 0.0 }?.let { "%.1f/10".format(Locale.US, it) }
                    val rottenTomatoesRating = ratings?.rottenTomatoesRating
                    val metacriticRating = ratings?.metacriticRating

                    if (effectiveImdbRating != null || rottenTomatoesRating != null || metacriticRating != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            effectiveImdbRating?.let { imdb ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_imdb_logo),
                                        contentDescription = "IMDb",
                                        modifier = Modifier.height(18.dp)
                                    )
                                    Text(
                                        text = imdb,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            rottenTomatoesRating?.let { rt ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_rotten_tomatoes),
                                        contentDescription = "Rotten Tomatoes",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = rt,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            metacriticRating?.let { meta ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_metacritic),
                                        contentDescription = "Metacritic",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Row (Ver Trailer, Favoritos, Compartir)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup()
                    ) {
                        // Trailer Button
                        if (hasTrailer) {
                            TvButton(
                                onClick = { showTrailerDialog = true },
                                modifier = Modifier
                                    .focusRequester(firstActionFocusRequester)
                                    .focusHighlight(shape = MaterialTheme.shapes.small),
                                colors = TvButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.watch_trailer),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Favorite Button
                        TvButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier
                                .then(if (!hasTrailer) Modifier.focusRequester(firstActionFocusRequester) else Modifier)
                                .focusHighlight(shape = MaterialTheme.shapes.small),
                            colors = TvButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isFavorite) stringResource(R.string.remove_favorite_desc) else stringResource(R.string.add_favorite_desc),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Share Button
                        TvButton(
                            onClick = onShareClick,
                            modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small),
                            colors = TvButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = TvButtonDefaults.shape(shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.share_desc),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Synopsis / Summary
                    val summaryText = movie.summary?.ifEmpty { movie.descriptionFull }
                        ?: movie.descriptionFull
                        ?: tmdbMovie?.overview
                    if (!summaryText.isNullOrBlank()) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 750.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Cast Section
            if (!movie.cast.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.cast_header),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier
                        .focusRestorer()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(movie.cast) { cast ->
                        CastItem(cast = cast, onCastClick = onCastClick)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Torrents & Releases Section
            Text(
                text = stringResource(R.string.torrents_header),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                if (releasesByQuality.isNotEmpty()) {
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
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                            releases.forEach { release ->
                                val releaseHash = release.hash
                                val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(releaseHash, ignoreCase = true) }
                                TorrentReleaseShimmerCard(
                                    release = release,
                                    isDownloaded = downloadedHashes.contains(release.hash.lowercase()) || matchingDownload?.state == TorrentDownloadService.STATE_COMPLETED,
                                    onClick = { onReleaseClick(release) },
                                    onLongClick = { onReleaseLongClick(release) },
                                    download = matchingDownload,
                                    mediaController = mediaController,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    movie.torrents?.forEach { torrent ->
                        TorrentItem(
                            movie = movie,
                            torrent = torrent,
                            isDownloaded = downloadedHashes.contains(torrent.hash),
                            onTorrentClick = onTorrentClick,
                            handlingMode = torrentHandlingMode,
                            onInsufficientStorage = onInsufficientStorage
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        // 3. YouTube Trailer Dialog for TV (Focus-isolated)
        if (showTrailerDialog && !movie.ytTrailerCode.isNullOrEmpty()) {
            TvTrailerDialog(
                youtubeVideoId = movie.ytTrailerCode,
                onDismiss = { showTrailerDialog = false },
                themeMode = themeMode
            )
        }
    }
}

/**
 * Focus-isolated modal dialog for playing YouTube trailers on Android TV without corrupting D-pad navigation.
 */
@Composable
private fun TvTrailerDialog(
    youtubeVideoId: String,
    onDismiss: () -> Unit,
    themeMode: Int? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val effectiveThemeMode = remember(context, themeMode) {
            themeMode ?: PreferenceManager(context).getTheme()
        }
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            val playFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                playFocusRequester.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim),
                contentAlignment = Alignment.Center
            ) {
                YoutubePlayer(
                    youtubeVideoId = youtubeVideoId,
                    lifecycleOwner = LocalLifecycleOwner.current,
                    focusRequester = playFocusRequester
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                        .focusHighlight(shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close_trailer),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


/**
 * Renders a movie's trailer, summary, metadata, cast, and available torrents.
 *
 * @param torrentHandlingMode Configured behavior when a torrent release is selected.
 * @param scrollState Scroll state for managing vertical scroll offset during focus changes.
 */
@Composable
fun MovieDetailContent(
    movie: Movie,
    releasesByQuality: Map<VideoQualityGroup, List<TorrentRelease>> = emptyMap(),
    downloadedHashes: Set<String>,
    allDownloads: List<TorrentDownload> = emptyList(),
    mediaController: MediaController? = null,
    torrentHandlingMode: TorrentHandlingMode = TorrentHandlingMode.EXTERNAL_CLIENT,
    isWideScreen: Boolean,
    isTv: Boolean,
    onTorrentClick: (Torrent) -> Unit,
    onReleaseClick: ((TorrentRelease) -> Unit)? = null,
    onReleaseLongClick: ((TorrentRelease) -> Unit)? = null,
    onAddLanguageToFilter: (String) -> Unit,
    onCastClick: (String) -> Unit,
    onInsufficientStorage: (Long, Long) -> Unit = { _, _ -> },
    contentFocusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    scrollState: ScrollState = rememberScrollState()
) {
    val firstTorrentFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
    ) {
        if (isWideScreen && !movie.ytTrailerCode.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(modifier = Modifier.weight(0.6f)) {
                    YoutubePlayer(
                        youtubeVideoId = movie.ytTrailerCode,
                        lifecycleOwner = LocalLifecycleOwner.current,
                        focusRequester = contentFocusRequester,
                        onFocused = {
                            coroutineScope.launch { scrollState.animateScrollTo(0) }
                        }
                    )
                }
                Column(modifier = Modifier.weight(0.4f)) {
                    Text(text = stringResource(R.string.summary_header), style = MaterialTheme.typography.titleLarge)
                    val summaryText = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull
                    Text(
                        text = summaryText ?: stringResource(R.string.no_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MovieMetadata(
                        movie = movie,
                        onAddLanguageToFilter = onAddLanguageToFilter,
                        focusRequester = if (movie.ytTrailerCode.isEmpty()) contentFocusRequester else null,
                        onFocused = {
                            if (movie.ytTrailerCode.isEmpty()) {
                                coroutineScope.launch { scrollState.animateScrollTo(0) }
                            }
                        }
                    )
                }
            }
        } else {
            if (!movie.ytTrailerCode.isNullOrEmpty()) {
                YoutubePlayer(
                    youtubeVideoId = movie.ytTrailerCode,
                    lifecycleOwner = LocalLifecycleOwner.current,
                    focusRequester = contentFocusRequester,
                    onFocused = {
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(text = stringResource(R.string.summary_header), style = MaterialTheme.typography.titleLarge)
            val summaryText = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull
            Text(text = summaryText ?: stringResource(R.string.no_summary), style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = stringResource(R.string.details_title), style = MaterialTheme.typography.titleLarge)
            MovieMetadata(
                movie = movie,
                onAddLanguageToFilter = onAddLanguageToFilter,
                focusRequester = if (movie.ytTrailerCode.isNullOrEmpty()) contentFocusRequester else null,
                onFocused = {
                    if (movie.ytTrailerCode.isNullOrEmpty()) {
                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (!movie.cast.isNullOrEmpty()) {
            CastSection(
                castList = movie.cast,
                onCastClick = onCastClick,
                nextFocusRequester = firstTorrentFocusRequester.takeIf {
                    isTv && (!movie.torrents.isNullOrEmpty() || releasesByQuality.isNotEmpty())
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(text = stringResource(R.string.torrents_header), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
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
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        releases.forEach { release ->
                            val isFirst = isFirstRelease && movie.ytTrailerCode.isNullOrEmpty()
                            val focusReq = when {
                                isTv && isFirstRelease && !movie.cast.isNullOrEmpty() -> firstTorrentFocusRequester
                                isFirst -> contentFocusRequester
                                else -> null
                            }
                            isFirstRelease = false
                            val releaseHash = release.hash
                            val matchingDownload = allDownloads.firstOrNull { it.infoHash.equals(releaseHash, ignoreCase = true) }
                            TorrentReleaseShimmerCard(
                                release = release,
                                isDownloaded = downloadedHashes.contains(release.hash.lowercase()) || matchingDownload?.state == TorrentDownloadService.STATE_COMPLETED,
                                onClick = { onReleaseClick?.invoke(release) },
                                onLongClick = { onReleaseLongClick?.invoke(release) },
                                download = matchingDownload,
                                mediaController = mediaController,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .then(if (focusReq != null) Modifier.focusRequester(focusReq) else Modifier),
                                focusRequester = focusReq
                            )
                        }
                    }
                }
            } else {
                movie.torrents?.forEachIndexed { index, torrent ->
                    val isFirstItem = index == 0 && movie.ytTrailerCode.isNullOrEmpty()
                    TorrentItem(
                        movie = movie,
                        torrent = torrent,
                        isDownloaded = downloadedHashes.contains(torrent.hash),
                        onTorrentClick = onTorrentClick,
                        handlingMode = torrentHandlingMode,
                        onInsufficientStorage = onInsufficientStorage,
                        focusRequester = when {
                            isTv && index == 0 && !movie.cast.isNullOrEmpty() ->
                                firstTorrentFocusRequester
                            isFirstItem -> contentFocusRequester
                            else -> null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays localized movie metadata and an action to exclude its language.
 *
 * @param onFocused Callback invoked when the exclude button gains focus.
 */
@Composable
fun MovieMetadata(
    movie: Movie, 
    modifier: Modifier = Modifier,
    onAddLanguageToFilter: (String) -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        if (!movie.genres.isNullOrEmpty()) {
            val translatedGenres = movie.genres.map { GenreTranslation.getGenreText(it).asString() }
            Text(
                text = translatedGenres.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(text = stringResource(R.string.metadata_year, movie.year ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.metadata_language, movie.language ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
            movie.language?.let { lang ->
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onAddLanguageToFilter(lang) },
                    modifier = Modifier
                        .height(28.dp)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .focusHighlight(shape = MaterialTheme.shapes.small)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onFocused?.invoke()
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.exclude_button_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Text(text = stringResource(R.string.metadata_rating, movie.rating ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
        if (!movie.runtime.isNullOrEmpty()) {
            Text(text = stringResource(R.string.metadata_runtime, movie.runtime), style = MaterialTheme.typography.bodyLarge)
        }
        if (!movie.mpaRating.isNullOrEmpty()) {
            Text(text = stringResource(R.string.metadata_mpa, movie.mpaRating), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Embeds a YouTube trailer with native play/pause controls and lifecycle handling.
 *
 * @param onFocused Callback invoked when the play/pause button gains focus to align scroll position.
 */
@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    lifecycleOwner: LifecycleOwner,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    var playerState by remember { mutableStateOf(PlayerConstants.PlayerState.UNKNOWN) }
    var youTubePlayerInstance by remember { mutableStateOf<YouTubePlayer?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)
                    
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                    val options = IFramePlayerOptions.Builder(context)
                        .controls(0)
                        .build()

                    initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            youTubePlayerInstance = youTubePlayer
                            youTubePlayer.cueVideo(youtubeVideoId, 0f)
                        }

                        override fun onStateChange(
                            youTubePlayer: YouTubePlayer,
                            state: PlayerConstants.PlayerState
                        ) {
                            playerState = state
                        }
                    }, options)
                }
            }
        )

        val isPlaying = playerState == PlayerConstants.PlayerState.PLAYING
        val context = LocalContext.current
        val isTv = remember(context) { context.isTvDevice() }

        val playButtonModifier = Modifier
            .size(64.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused?.invoke()
                }
            }

        if (isTv) {
            androidx.tv.material3.IconButton(
                onClick = {
                    val player = youTubePlayerInstance
                    if (player != null) {
                        if (isPlaying) player.pause() else player.play()
                    }
                },
                modifier = playButtonModifier
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f), CircleShape),
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent
                )
            ) {
                androidx.tv.material3.Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            IconButton(
                onClick = {
                    val player = youTubePlayerInstance
                    if (player != null) {
                        if (isPlaying) player.pause() else player.play()
                    }
                },
                modifier = playButtonModifier
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f), CircleShape)
                    .focusHighlight(shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

/** Displays the cast as a horizontally scrolling list. */
@Composable
fun CastSection(
    castList: List<Cast>,
    onCastClick: (String) -> Unit,
    nextFocusRequester: FocusRequester? = null
) {
    Text(text = stringResource(R.string.cast_header), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(
        modifier = Modifier
            .focusRestorer()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(castList) { cast ->
            CastItem(cast = cast, onCastClick = onCastClick)
        }
    }
}

/** Displays an interactive cast member portrait and name. */
@Composable
fun CastItem(
    cast: Cast,
    onCastClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Surface(
            onClick = { cast.name?.let { onCastClick(it) } },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            interactionSource = interactionSource,
            modifier = modifier
                .width(105.dp)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = cast.urlSmallImage,
                    contentDescription = cast.name,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isFocused) 3.dp else 1.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = cast.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (!cast.characterName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cast.characterName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .width(80.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { cast.name?.let { onCastClick(it) } },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = cast.urlSmallImage,
                contentDescription = cast.name,
                placeholder = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = cast.name ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!cast.characterName.isNullOrBlank()) {
                Text(
                    text = cast.characterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
            }
        }
    }
}

/**
 * Displays torrent quality and dispatches the release using the configured handling mode.
 *
 * @param handlingMode Configured external, local-download, or unsupported Cast mode.
 */
@Composable
fun TorrentItem(
    movie: Movie,
    torrent: Torrent,
    isDownloaded: Boolean,
    onTorrentClick: (Torrent) -> Unit,
    focusRequester: FocusRequester? = null,
    handlingMode: TorrentHandlingMode = TorrentHandlingMode.EXTERNAL_CLIENT,
    onInsufficientStorage: (available: Long, required: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val onTorrentClickInternal = {
        val hash = torrent.hash
        if (!hash.isNullOrBlank()) {
            try {
                val title = movie.title ?: resources.getString(R.string.torrent_download_title)
                val magnetUri = TorrentLaunchHelper.buildMagnetUri(hash, title)
                val moviePoster = movie.mediumCoverImage ?: movie.largeCoverImage
                val sizeBytes = torrent.sizeBytes?.toLongOrNull()
                when (val result = TorrentLaunchHelper.launch(context, handlingMode, magnetUri, title, movieId = movie.id, posterUrl = moviePoster, requiredSizeBytes = sizeBytes)) {
                    TorrentLaunchResult.Started -> {
                        onTorrentClick(torrent)
                    }
                    is TorrentLaunchResult.InsufficientStorage -> {
                        onInsufficientStorage(result.availableBytes, result.requiredBytes)
                    }
                    else -> {}
                }
            } catch (_: IllegalArgumentException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

    MovieTorrentShimmerCard(
        movie = movie,
        torrent = torrent,
        isDownloaded = isDownloaded,
        onClick = onTorrentClickInternal,
        modifier = buttonModifier,
        focusRequester = focusRequester
    )
}

/** TV preview of the movie detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TV Light", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "TV Dark", showBackground = true, device = "id:tv_720p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MovieDetailScreenTvPreview() {
    val sampleMovie = Movie(
        id = 1,
        title = "Inception",
        year = 2010,
        rating = "8.8",
        runtime = "148 min",
        genres = listOf("Action", "Sci-Fi", "Adventure"),
        summary = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
        ytTrailerCode = "YoHD9XEInc0",
        language = "English",
        torrents = listOf(
            Torrent(quality = "1080p", size = "2.2 GB", type = "bluray", hash = "HASH1"),
            Torrent(quality = "720p", size = "1.1 GB", type = "bluray", hash = "HASH2")
        ),
        cast = listOf(
            Cast(name = "Leonardo DiCaprio", characterName = "Cobb", urlSmallImage = ""),
            Cast(name = "Joseph Gordon-Levitt", characterName = "Arthur", urlSmallImage = "")
        )
    )

    TvLaTorrentolaTheme {
        MovieDetailScreenContent(
            uiState = DetailUiState.Success(sampleMovie, isFavorite = true),
            downloadedHashes = setOf("HASH1"),
            isWideScreen = true,
            isTv = true,
            selectedActorDetail = null,
            isActorLoading = false,
            showActorSheet = false,
            actorSheetState = rememberModalBottomSheetState(),
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {},
            onCastClick = {},
            onDismissActorSheet = {},
            onActorCreditClick = { _ -> }
        )
    }
}

/** Light and dark previews of the movie detail screen across form factors. */
@OptIn(ExperimentalMaterial3Api::class)
@com.martinrevert.latorrentola.ui.theme.LightDarkPreviews
@Composable
fun MovieDetailScreenPreview() {
    val sampleMovie = Movie(
        id = 1,
        title = "Inception",
        year = 2010,
        rating = "8.8",
        runtime = "148 min",
        genres = listOf("Action", "Sci-Fi", "Adventure"),
        summary = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
        ytTrailerCode = "YoHD9XEInc0",
        language = "English",
        torrents = listOf(
            Torrent(quality = "1080p", size = "2.2 GB", type = "bluray", hash = "HASH1"),
            Torrent(quality = "720p", size = "1.1 GB", type = "bluray", hash = "HASH2")
        ),
        cast = listOf(
            Cast(name = "Leonardo DiCaprio", characterName = "Cobb", urlSmallImage = ""),
            Cast(name = "Joseph Gordon-Levitt", characterName = "Arthur", urlSmallImage = "")
        )
    )

    LaTorrentolaTheme {
        MovieDetailScreenContent(
            uiState = DetailUiState.Success(sampleMovie, isFavorite = true),
            downloadedHashes = setOf("HASH1"),
            isWideScreen = false,
            isTv = false,
            selectedActorDetail = null,
            isActorLoading = false,
            showActorSheet = false,
            actorSheetState = rememberModalBottomSheetState(),
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {},
            onCastClick = {},
            onDismissActorSheet = {},
            onActorCreditClick = { _ -> }
        )
    }
}

/** Tablet preview of the movie detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Tablet Light", showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tablet Dark", showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MovieDetailScreenTabletPreview() {
    val sampleMovie = Movie(
        id = 1,
        title = "Inception",
        year = 2010,
        rating = "8.8",
        runtime = "148 min",
        genres = listOf("Action", "Sci-Fi", "Adventure"),
        summary = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
        ytTrailerCode = "YoHD9XEInc0",
        language = "English",
        torrents = listOf(
            Torrent(quality = "1080p", size = "2.2 GB", type = "bluray", hash = "HASH1"),
            Torrent(quality = "720p", size = "1.1 GB", type = "bluray", hash = "HASH2")
        ),
        cast = listOf(
            Cast(name = "Leonardo DiCaprio", characterName = "Cobb", urlSmallImage = ""),
            Cast(name = "Joseph Gordon-Levitt", characterName = "Arthur", urlSmallImage = "")
        )
    )

    LaTorrentolaTheme {
        MovieDetailScreenContent(
            uiState = DetailUiState.Success(sampleMovie, isFavorite = true),
            downloadedHashes = setOf("HASH1"),
            isWideScreen = true,
            isTv = false,
            selectedActorDetail = null,
            isActorLoading = false,
            showActorSheet = false,
            actorSheetState = rememberModalBottomSheetState(),
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {},
            onCastClick = {},
            onDismissActorSheet = {},
            onActorCreditClick = { _ -> }
        )
    }
}
