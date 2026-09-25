package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil3.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.YTS.Cast
import com.martinrevert.latorrentola.ui.components.MovieDetailPlaceholder
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.GenreTranslation
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.LifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Surface
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.utils.UiText
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedHashes by viewModel.downloadedHashes.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = remember(context) { context.isTvDevice() }
    val isWideScreen = configuration.screenWidthDp >= 600 || isTv

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoice()
        }
    }

    MovieDetailScreenContent(
        uiState = uiState,
        downloadedHashes = downloadedHashes,
        isWideScreen = isWideScreen,
        isTv = isTv,
        onBackClick = onBackClick,
        onShareClick = { movie ->
            val imdbUrl = "https://www.imdb.com/title/${movie.imdbCode}"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                val shareText = context.getString(
                    R.string.share_movie_text,
                    movie.title,
                    imdbUrl
                )
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(
                R.string.share_movie_chooser)))
        },
        onFavoriteToggle = { movie -> viewModel.toggleFavorite(movie) },
        onTorrentClick = { movie, torrent ->
            viewModel.markAsDownloaded(movie, torrent.hash ?: "", torrent.quality ?: "")
        },
        onAddLanguageToFilter = { language ->
            viewModel.addLanguageToFilter(language) { error ->
                Toast.makeText(context, error.asString(context), Toast.LENGTH_LONG).show()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieDetailScreenContent(
    uiState: DetailUiState,
    downloadedHashes: Set<String>,
    isWideScreen: Boolean,
    isTv: Boolean,
    onBackClick: () -> Unit,
    onShareClick: (Movie) -> Unit,
    onFavoriteToggle: (Movie) -> Unit,
    onTorrentClick: (Movie, Torrent) -> Unit,
    onAddLanguageToFilter: (String) -> Unit
) {
    val hazeState = rememberHazeState()
    val isInspection = LocalInspectionMode.current
    val isPreAndroid12 = !isInspection && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val topBarContainerColor = if (isPreAndroid12) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        Color.Transparent
    }

    val contentFocusRequester = remember { FocusRequester() }

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
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                },
                actions = {
                    val state = uiState
                    if (state is DetailUiState.Success) {
                        IconButton(
                            onClick = { onShareClick(state.movie) },
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .focusProperties { down = contentFocusRequester }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_desc)
                            )
                        }
                        IconButton(
                            onClick = { onFavoriteToggle(state.movie) },
                            modifier = Modifier
                                .focusHighlight(shape = CircleShape)
                                .focusProperties { down = contentFocusRequester }
                        ) {
                            Icon(
                                if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
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
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    MovieDetailPlaceholder(contentPadding = detailContentPadding)
                }
                is DetailUiState.Success -> {
                    MovieDetailContent(
                        movie = state.movie,
                        downloadedHashes = downloadedHashes,
                        isWideScreen = isWideScreen,
                        isTv = isTv,
                        onTorrentClick = { onTorrentClick(state.movie, it) },
                        onAddLanguageToFilter = onAddLanguageToFilter,
                        contentFocusRequester = contentFocusRequester,
                        contentPadding = detailContentPadding
                    )
                }
                is DetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message.asString())
                    }
                }
            }
        }
    }
}


@Composable
fun MovieDetailContent(
    movie: Movie,
    downloadedHashes: Set<String>,
    isWideScreen: Boolean,
    isTv: Boolean,
    onTorrentClick: (Torrent) -> Unit,
    onAddLanguageToFilter: (String) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                        focusRequester = contentFocusRequester
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
                        focusRequester = if (movie.ytTrailerCode.isNullOrEmpty()) contentFocusRequester else null
                    )
                }
            }
        } else {
            if (!movie.ytTrailerCode.isNullOrEmpty()) {
                YoutubePlayer(
                    youtubeVideoId = movie.ytTrailerCode,
                    lifecycleOwner = LocalLifecycleOwner.current,
                    focusRequester = contentFocusRequester
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
                focusRequester = if (movie.ytTrailerCode.isNullOrEmpty()) contentFocusRequester else null
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (!movie.cast.isNullOrEmpty()) {
            CastSection(castList = movie.cast)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(text = stringResource(R.string.torrents_header), style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isTv) Alignment.CenterHorizontally else Alignment.Start
        ) {
            movie.torrents?.forEachIndexed { index, torrent ->
                val isFirstItem = index == 0 && movie.ytTrailerCode.isNullOrEmpty()
                TorrentItem(
                    movie = movie,
                    torrent = torrent,
                    isDownloaded = downloadedHashes.contains(torrent.hash),
                    onTorrentClick = onTorrentClick,
                    focusRequester = if (isFirstItem) contentFocusRequester else null
                )
            }
        }
    }
}

@Composable
fun MovieMetadata(
    movie: Movie, 
    modifier: Modifier = Modifier,
    onAddLanguageToFilter: (String) -> Unit,
    focusRequester: FocusRequester? = null
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
                        .focusHighlight(shape = MaterialTheme.shapes.small),
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

@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    lifecycleOwner: LifecycleOwner,
    focusRequester: FocusRequester? = null
) {
    var playerState by remember { mutableStateOf(PlayerConstants.PlayerState.UNKNOWN) }
    var youTubePlayerInstance by remember { mutableStateOf<YouTubePlayer?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
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

        if (isTv) {
            androidx.tv.material3.IconButton(
                onClick = {
                    val player = youTubePlayerInstance
                    if (player != null) {
                        if (isPlaying) player.pause() else player.play()
                    }
                },
                modifier = playButtonModifier
                    .background(Color.Black.copy(alpha = 0.1f), CircleShape),
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent
                )
            ) {
                androidx.tv.material3.Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                    tint = Color.White,
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
                    .background(Color.Black.copy(alpha = 0.15f), CircleShape)
                    .focusHighlight(shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CastSection(castList: List<Cast>) {
    Text(text = stringResource(R.string.cast_header), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(
        modifier = Modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(castList) { cast ->
            CastItem(cast = cast)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CastItem(cast: Cast) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()

        Surface(
            onClick = { },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .width(90.dp)
                .padding(4.dp)
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = cast.urlSmallImage,
                    contentDescription = cast.name,
                    placeholder = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cast.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.width(80.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = cast.characterName ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TorrentItem(
    movie: Movie,
    torrent: Torrent,
    isDownloaded: Boolean,
    onTorrentClick: (Torrent) -> Unit,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    val onTorrentClickInternal = {
        onTorrentClick(torrent)
        val hash = torrent.hash
        if (hash != null) {
            try {
                val encodedTitle = URLEncoder.encode(movie.title ?: "Movie", "UTF-8")
                val magnetUri = "magnet:?xt=urn:btih:$hash" +
                        "&dn=$encodedTitle" +
                        "&tr=udp://open.demonii.com:1337/announce" +
                        "&tr=udp://tracker.openbittorrent.com:80"
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = magnetUri.toUri()
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_no_torrent_client), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_magnet_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val buttonModifier = Modifier
        .then(if (isTv) Modifier.fillMaxWidth(0.5f) else Modifier.fillMaxWidth())
        .padding(vertical = 4.dp)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

    if (isTv) {
        Button(
            onClick = onTorrentClickInternal,
            modifier = buttonModifier
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.tv.material3.Text(
                    text = "${torrent.quality} - ${torrent.size} (${torrent.type})",
                    textAlign = TextAlign.Center
                )
                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.tv.material3.Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = stringResource(R.string.downloaded_desc),
                        tint = Color.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    } else {
        Button(
            onClick = onTorrentClickInternal,
            modifier = buttonModifier.focusHighlight(shape = MaterialTheme.shapes.extraLarge)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "${torrent.quality} - ${torrent.size} (${torrent.type})")
                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = stringResource(R.string.downloaded_desc),
                        tint = Color.Yellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

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

    LaTorrentolaTheme {
        MovieDetailScreenContent(
            uiState = DetailUiState.Success(sampleMovie, isFavorite = true),
            downloadedHashes = setOf("HASH1"),
            isWideScreen = true,
            isTv = true,
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {}
        )
    }
}

@PreviewLightDark
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
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {}
        )
    }
}

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
            onBackClick = {},
            onShareClick = {},
            onFavoriteToggle = {},
            onTorrentClick = { _, _ -> },
            onAddLanguageToFilter = {}
        )
    }
}


