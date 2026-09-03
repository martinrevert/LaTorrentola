package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.isTvDevice
import androidx.compose.ui.focus.focusRestorer
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedHashes by viewModel.downloadedHashes.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((uiState as? DetailUiState.Success)?.movie?.title ?: stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
                    }
                },
                actions = {
                    val state = uiState
                    if (state is DetailUiState.Success) {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(state.movie) },
                            modifier = Modifier.focusHighlight(shape = CircleShape)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is DetailUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is DetailUiState.Success -> {
                    MovieDetailContent(
                        movie = state.movie,
                        downloadedHashes = downloadedHashes,
                        onTorrentClick = { torrent ->
                            viewModel.markAsDownloaded(state.movie, torrent.hash ?: "", torrent.quality ?: "")
                        }
                    )
                }
                is DetailUiState.Error -> {
                    Text(text = state.message.asString())
                }
            }
        }
    }
}

@Composable
fun MovieDetailContent(
    movie: Movie,
    downloadedHashes: Set<String>,
    onTorrentClick: (Torrent) -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isTv && !movie.ytTrailerCode.isNullOrEmpty()) {
            // TV Layout: Side-by-side Video and Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(modifier = Modifier.weight(0.6f)) {
                    YoutubePlayer(
                        youtubeVideoId = movie.ytTrailerCode,
                        lifecycleOwner = LocalLifecycleOwner.current
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
                    MovieMetadata(movie = movie)
                }
            }
        } else {
            // Phone/Tablet Layout: Stacked Video and Summary
            if (!movie.ytTrailerCode.isNullOrEmpty()) {
                YoutubePlayer(
                    youtubeVideoId = movie.ytTrailerCode,
                    lifecycleOwner = LocalLifecycleOwner.current
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(text = stringResource(R.string.summary_header), style = MaterialTheme.typography.titleLarge)
            val summaryText = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull
            Text(text = summaryText ?: stringResource(R.string.no_summary), style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = stringResource(R.string.details_title), style = MaterialTheme.typography.titleLarge)
            MovieMetadata(movie = movie)
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
            movie.torrents?.forEach { torrent ->
                TorrentItem(
                    movie = movie,
                    torrent = torrent,
                    isDownloaded = downloadedHashes.contains(torrent.hash),
                    onTorrentClick = onTorrentClick
                )
            }
        }
    }
}

@Composable
fun MovieMetadata(movie: Movie, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = stringResource(R.string.metadata_year, movie.year ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
        Text(text = stringResource(R.string.metadata_language, movie.language ?: "N/A"), style = MaterialTheme.typography.bodyLarge)
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
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
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
                    
                    // Disable D-pad focus on the player itself
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

                    val options = IFramePlayerOptions.Builder(context)
                        .controls(0) // Hide web controls
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

        // Native Overlay: Single Focusable Play/Pause Button
        val isPlaying = playerState == PlayerConstants.PlayerState.PLAYING
        val context = LocalContext.current
        val isTv = remember(context) { context.isTvDevice() }

        if (isTv) {
            androidx.tv.material3.IconButton(
                onClick = {
                    val player = youTubePlayerInstance
                    if (player != null) {
                        if (isPlaying) player.pause() else player.play()
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
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
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
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

@Composable
fun CastItem(cast: Cast) {
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

@Composable
fun TorrentItem(
    movie: Movie,
    torrent: Torrent,
    isDownloaded: Boolean,
    onTorrentClick: (Torrent) -> Unit
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

    if (isTv) {
        androidx.tv.material3.Button(
            onClick = onTorrentClickInternal,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .padding(vertical = 4.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .focusHighlight(shape = MaterialTheme.shapes.extraLarge)
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
