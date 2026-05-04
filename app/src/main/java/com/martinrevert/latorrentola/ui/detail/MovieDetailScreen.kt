package com.martinrevert.latorrentola.ui.detail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.YTS.Movie
import com.martinrevert.latorrentola.model.YTS.Torrent
import com.martinrevert.latorrentola.model.YTS.Cast
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    viewModel: DetailViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((uiState as? DetailUiState.Success)?.movie?.title ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val state = uiState
                    if (state is DetailUiState.Success) {
                        IconButton(onClick = { viewModel.toggleFavorite(state.movie) }) {
                            Icon(
                                if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite"
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
                    MovieDetailContent(movie = state.movie)
                }
                is DetailUiState.Error -> {
                    Text(text = state.message)
                }
            }
        }
    }
}

@Composable
fun MovieDetailContent(movie: Movie) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // YouTube Player
        if (!movie.ytTrailerCode.isNullOrEmpty()) {
            YoutubePlayer(
                youtubeVideoId = movie.ytTrailerCode,
                lifecycleOwner = LocalLifecycleOwner.current
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(text = "Summary", style = MaterialTheme.typography.titleLarge)
        val summaryText = movie.summary?.ifEmpty { movie.descriptionFull } ?: movie.descriptionFull
        Text(text = summaryText ?: "No summary available", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (!movie.cast.isNullOrEmpty()) {
            CastSection(castList = movie.cast)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(text = "Details", style = MaterialTheme.typography.titleLarge)
        Text(text = "Year: ${movie.year}")
        Text(text = "Language: ${movie.language}")
        Text(text = "Rating: ${movie.rating}")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Torrents", style = MaterialTheme.typography.titleLarge)
        movie.torrents?.forEach { torrent ->
            TorrentItem(movie = movie, torrent = torrent)
        }
    }
}

@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
            .clip(MaterialTheme.shapes.medium),
        factory = { context ->
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)

                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(youtubeVideoId, 0f)
                    }
                })
            }
        }
    )
}

@Composable
fun CastSection(castList: List<Cast>) {
    Text(text = "Cast", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(
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
fun TorrentItem(movie: Movie, torrent: Torrent) {
    val context = LocalContext.current
    Button(
        onClick = {
            val hash = torrent.hash
            if (hash != null) {
                try {
                    val encodedTitle = URLEncoder.encode(movie.title ?: "Movie", "UTF-8")
                    val magnetUri = "magnet:?xt=urn:btih:$hash" +
                            "&dn=$encodedTitle" +
                            "&tr=udp://open.demonii.com:1337/announce" +
                            "&tr=udp://tracker.openbittorrent.com:80"
                    
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(magnetUri)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No torrent client installed", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error creating magnet link", Toast.LENGTH_SHORT).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(text = "${torrent.quality} - ${torrent.size} (${torrent.type})")
    }
}
