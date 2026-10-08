package com.martinrevert.latorrentola.ui.player

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.google.android.gms.cast.framework.CastContext
import androidx.mediarouter.R as MediaRouterR
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.google.android.gms.cast.framework.CastButtonFactory
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.EZTV.downloadInfoHash
import com.martinrevert.latorrentola.network.DownloadedSubtitle
import com.martinrevert.latorrentola.network.OpenSubtitlesException
import com.martinrevert.latorrentola.network.OpenSubtitleRepository
import com.martinrevert.latorrentola.network.OpenSubtitleResult
import com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer
import com.martinrevert.latorrentola.ui.components.NextEpisodeTorrentDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import com.martinrevert.latorrentola.utils.mediaMimeType
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import com.google.android.gms.cast.MediaTrack as GoogleMediaTrack

/** Track type exposed by the in-player audio and subtitle selectors. */
enum class PlaybackTrackType {
    /** Audio tracks in the media file or Cast receiver. */
    AUDIO,

    /** Subtitle tracks in the media file or Cast receiver. */
    SUBTITLE
}

/**
 * One selectable track exposed by either Media3 or the Cast receiver.
 *
 * @property label Label rendered in the track selection dialog.
 * @property group Media3 track group, or `null` for receiver-provided tracks.
 * @property trackIndex Index within [group], or `null` for receiver-provided tracks.
 * @property castTrackId Cast receiver track identifier, or `null` for local Media3 tracks.
 * @property isSelected Whether the option is currently active on its playback target.
 */
data class PlaybackTrackOption(
    val label: String,
    val group: TrackGroup?,
    val trackIndex: Int?,
    val castTrackId: Long?,
    val isSelected: Boolean
)

/** Keeps Cast subtitle endpoints alive after the player activity is closed. */
private object SubtitleServerRegistry {
    /** Active subtitle servers shared by player activity instances. */
    private val servers = java.util.Collections.synchronizedSet(
        mutableSetOf<VerifiedTorrentHttpServer>()
    )

    /** Retains a running server for the current Cast playback session. */
    fun retain(server: VerifiedTorrentHttpServer) {
        servers.add(server)
    }

    /** Stops all cached subtitle endpoints after Cast playback ends. */
    fun stopAll() {
        synchronized(servers) {
            servers.forEach(VerifiedTorrentHttpServer::stop)
            servers.clear()
        }
    }
}

sealed class PlayerDialogState {
    data class Tracks(
        val type: PlaybackTrackType,
        val title: String,
        val options: List<PlaybackTrackOption>,
        val selectedIndex: Int,
        val onSelected: (PlaybackTrackOption?) -> Unit
    ) : PlayerDialogState()

    data class SubtitleStyle(
        val selectedIndex: Int,
        val onSelected: (Float, Boolean) -> Unit
    ) : PlayerDialogState()

    data class SubtitleResults(
        val results: List<OpenSubtitleResult>,
        val onSelected: (OpenSubtitleResult) -> Unit
    ) : PlayerDialogState()

    data class Message(
        val title: String,
        val message: String,
        val onDismiss: () -> Unit = {}
    ) : PlayerDialogState()

    data class Progress(
        val title: String,
        val message: String
    ) : PlayerDialogState()

    data class InsufficientSpace(
        val release: EztvTorrent,
        val seriesId: Int,
        val seriesName: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val episodeName: String,
        val onErase: () -> Unit,
        val onDismiss: () -> Unit
    ) : PlayerDialogState()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerDialogContent(
    state: PlayerDialogState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    val firstItemFocusRequester = remember(state) { FocusRequester() }
    val cancelButtonFocusRequester = remember(state) { FocusRequester() }

    if (isTv) {
        LaunchedEffect(state) {
            delay(100)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val dialogBody: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    is PlayerDialogState.Tracks -> {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.options) { index, option ->
                                val isSelected = index == state.selectedIndex
                                val itemModifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .then(
                                        if (isTv) {
                                            Modifier.focusProperties {
                                                if (index == state.options.lastIndex) down = cancelButtonFocusRequester
                                                if (index == 0) up = cancelButtonFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)

                                Surface(
                                    onClick = {
                                        state.onSelected(option)
                                        onDismiss()
                                    },
                                    modifier = itemModifier,
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .focusRequester(cancelButtonFocusRequester)
                                    .then(
                                        if (isTv && state.options.isNotEmpty()) {
                                            Modifier.focusProperties {
                                                up = firstItemFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is PlayerDialogState.SubtitleStyle -> {
                        val labels = listOf(
                            stringResource(R.string.player_subtitle_style_system),
                            stringResource(R.string.player_subtitle_style_small),
                            stringResource(R.string.player_subtitle_style_medium),
                            stringResource(R.string.player_subtitle_style_large),
                            stringResource(R.string.player_subtitle_style_extra_large)
                        )
                        Text(
                            text = stringResource(R.string.player_subtitle_style),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(labels) { index, label ->
                                val isSelected = index == state.selectedIndex
                                val itemModifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .then(
                                        if (isTv) {
                                            Modifier.focusProperties {
                                                if (index == labels.lastIndex) down = cancelButtonFocusRequester
                                                if (index == 0) up = cancelButtonFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)

                                Surface(
                                    onClick = {
                                        val fractions = listOf(
                                            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
                                            0.04f,
                                            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
                                            0.067f,
                                            0.08f
                                        )
                                        if (index == 0) {
                                            state.onSelected(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION, true)
                                        } else {
                                            state.onSelected(fractions[index], false)
                                        }
                                        onDismiss()
                                    },
                                    modifier = itemModifier,
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .focusRequester(cancelButtonFocusRequester)
                                    .then(
                                        if (isTv && labels.isNotEmpty()) {
                                            Modifier.focusProperties {
                                                up = firstItemFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is PlayerDialogState.SubtitleResults -> {
                        Text(
                            text = stringResource(R.string.opensubtitles_results_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.results) { index, result ->
                                val feature = result.featureTitle?.takeIf(String::isNotBlank)
                                val label = listOfNotNull(
                                    result.language.uppercase(Locale.ROOT),
                                    result.release.takeIf(String::isNotBlank),
                                    feature
                                ).joinToString(" · ")
                                val itemModifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .then(
                                        if (isTv) {
                                            Modifier.focusProperties {
                                                if (index == state.results.lastIndex) down = cancelButtonFocusRequester
                                                if (index == 0) up = cancelButtonFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)

                                Surface(
                                    onClick = {
                                        state.onSelected(result)
                                        onDismiss()
                                    },
                                    modifier = itemModifier,
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .focusRequester(cancelButtonFocusRequester)
                                    .then(
                                        if (isTv && state.results.isNotEmpty()) {
                                            Modifier.focusProperties {
                                                up = firstItemFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(
                                    text = stringResource(android.R.string.cancel),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isTv) androidx.tv.material3.MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is PlayerDialogState.Message -> {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    state.onDismiss()
                                    onDismiss()
                                },
                                modifier = Modifier.focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                    is PlayerDialogState.Progress -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CircularProgressIndicator()
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is PlayerDialogState.InsufficientSpace -> {
                        Text(
                            text = "Insufficient Disk Space",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Not enough disk space available. Would you like to erase all saved torrents on disk to free up space?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            val cancelBtnReq = remember { FocusRequester() }
                            val eraseBtnReq = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                try { cancelBtnReq.requestFocus() } catch (_: Exception) {}
                            }
                            TextButton(
                                onClick = {
                                    state.onDismiss()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .focusRequester(cancelBtnReq)
                                    .focusProperties { right = eraseBtnReq; left = eraseBtnReq }
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    state.onErase()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .focusRequester(eraseBtnReq)
                                    .focusProperties { right = cancelBtnReq; left = cancelBtnReq }
                                    .focusHighlight(shape = MaterialTheme.shapes.small)
                            ) {
                                Text("Erase", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (isTv) {
            TvSurface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(androidx.tv.material3.MaterialTheme.shapes.extraLarge),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface,
                    contentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = androidx.tv.material3.MaterialTheme.colorScheme.surface,
                    focusedContentColor = androidx.tv.material3.MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 520.dp, max = 680.dp)
            ) {
                dialogBody()
            }
        } else {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 320.dp, max = 560.dp)
                    .fillMaxWidth(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                dialogBody()
            }
        }
    }
}

/**
 * Plays verified torrent content locally or on Cast and supports OpenSubtitles tracks.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class LocalPlayerActivity : AppCompatActivity() {

    /** Media3 player used when no Cast session is active. */
    private var localPlayer: ExoPlayer? = null

    /** Player which switches between local playback and an active Cast session. */
    private var castPlayer: CastPlayer? = null

    /** Player currently presenting local or Cast playback. */
    private var activePlayer: Player? = null

    /** Player listener controlling the TV screen-awake flag for the active playback target. */
    private var screenAwakeListener: Player.Listener? = null

    /** Player currently observed for TV screen-awake behavior. */
    private var screenAwakePlayer: Player? = null

    /** View whose controller follows the active local or remote player. */
    private var playerView: PlayerView? = null

    /** OpenSubtitles search and download integration. */
    @Inject
    lateinit var openSubtitlesRepository: OpenSubtitleRepository

    /** Preference manager for user settings. */
    @Inject
    lateinit var preferenceManager: PreferenceManager

    /** DAO for managing torrent downloads and cleanup. */
    @Inject
    lateinit var torrentDownloadDao: TorrentDownloadDao

    /** User library repository for cloud sync of watch history and playback progress. */
    @Inject
    lateinit var userLibraryRepository: UserLibraryRepository

    /** Maps local subtitle URIs to matching receiver-accessible URLs. */
    private val castSubtitleUrls = mutableMapOf<String, String>()

    /** Current Media3 item retained when a subtitle selection replaces its track list. */
    private var currentMediaItem: MediaItem? = null

    /** Current local HTTP stream URL, restored when playback transfers back from Cast. */
    private var localStreamUrl: String = ""

    /** HTTP URL on the device's local network used by Cast receivers. */
    private var castStreamUrl: String = ""

    /** Whether the selected torrent mode enabled Cast playback. */
    private var castEnabled = false

    /** Whether a Cast receiver is currently using this activity's stream servers. */
    private var castSessionActive = false

    /** Text size fraction currently applied to local subtitle cues. */
    private var subtitleTextSizeFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION

    /** User-visible title used for subtitle search and playback metadata. */
    private var mediaTitle = ""

    /** Managed torrent owning the current playback, used to scope downloaded subtitles. */
    private var torrentInfoHash = ""

    /** Prevents overlapping subtitle searches and downloads from repeated controller clicks. */
    private var subtitleOperationInProgress = false

    /** Ensures cached subtitle lookup and optional account warm-up run once per playback. */
    private var subtitleStartupCheckStarted = false

    /** OpenSubtitles controller action, animated while a request is pending. */
    private var subtitleSearchButton: ImageButton? = null

    /** Rotation animation communicating that OpenSubtitles is still being contacted. */
    private var subtitleSearchAnimator: ObjectAnimator? = null

    /** ViewModel managing next episode releases and downloads. */
    private val tvPlayerViewModel: TvPlayerViewModel by viewModels()

    /** Next episode tracking fields. */
    private var nextEpisodeSeriesId: Int = 0
    private var nextEpisodeSeriesName: String = ""
    private var nextEpisodeSeasonNumber: Int = 0
    private var nextEpisodeEpisodeNumber: Int = 0
    private var nextEpisodeEpisodeName: String = ""
    private var hasNextEpisodeInfo: Boolean = false
    private var nextEpisodeTriggered: Boolean = false
    private var currentSeriesId: Int = 0
    private var currentSeasonNumber: Int = 0
    private var currentEpisodeNumber: Int = 0
    private var currentMovieId: Int = 0

    /** Controls visibility of the next episode torrent selection overlay. */
    private var showNextEpisodeDialog by mutableStateOf(false)

    /** Overlay hosting the next episode selection composable. */
    private var nextEpisodeOverlay: ComposeView? = null

    /** Current state for player dialog overlays (track selection, subtitle style, errors, progress). */
    private var playerDialogState: PlayerDialogState? by mutableStateOf(null)

    /** Overlay hosting player dialog composables. */
    private var playerDialogOverlay: ComposeView? = null

    /**
     * Creates the player, using early-start extraction for incomplete torrents, and adds
     * playback actions to the native Media3 controller.
     *
     * @param savedInstanceState Previously saved activity state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localStreamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        castStreamUrl = intent.getStringExtra(EXTRA_CAST_URL).orEmpty()
        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        val isPartialStream = intent.getBooleanExtra(EXTRA_PARTIAL_TORRENT_STREAM, false)
        val mediaUri = if (file != null && file.isFile && file.canRead() && !isPartialStream) {
            Uri.fromFile(file)
        } else {
            localStreamUrl.takeIf(String::isNotBlank)?.let(Uri::parse)
                ?: file?.takeIf { it.isFile && it.canRead() }?.let { Uri.fromFile(it) }
        }
        if (mediaUri == null) {
            finish()
            return
        }

        mediaTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        torrentInfoHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
        currentSeriesId = intent.getIntExtra(EXTRA_SERIES_ID, 0)
        currentSeasonNumber = intent.getIntExtra(EXTRA_SEASON_NUMBER, 0)
        currentEpisodeNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 0)
        currentMovieId = intent.getIntExtra(EXTRA_MOVIE_ID, 0)

        val nextEpisodeInfo = intent.getStringArrayListExtra(EXTRA_NEXT_EPISODE_INFO)
        if (nextEpisodeInfo != null && nextEpisodeInfo.size >= 5) {
            try {
                nextEpisodeSeriesId = nextEpisodeInfo[0].toInt()
                nextEpisodeSeriesName = nextEpisodeInfo[1]
                nextEpisodeSeasonNumber = nextEpisodeInfo[2].toInt()
                nextEpisodeEpisodeNumber = nextEpisodeInfo[3].toInt()
                nextEpisodeEpisodeName = nextEpisodeInfo[4]
                hasNextEpisodeInfo = true
                Log.d(TAG, "Next episode info: $nextEpisodeSeriesName S${nextEpisodeSeasonNumber}E${nextEpisodeEpisodeNumber}")
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Error parsing next episode info", e)
                hasNextEpisodeInfo = false
            }
        }
        val castRequested = intent.getBooleanExtra(EXTRA_CAST_ENABLED, false)
        castEnabled = castRequested
        Log.d(TAG, "DEBUG CAST: castRequested=$castRequested, castStreamUrl=$castStreamUrl, localStreamUrl=$localStreamUrl")
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(intent.getStringExtra(EXTRA_MIME_TYPE) ?: file?.mediaMimeType())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(mediaTitle).build())
            .build()
        currentMediaItem = mediaItem

        val playerView = PlayerView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        this.playerView = playerView
        playerView.controllerShowTimeoutMs = PLAYER_CONTROLLER_SHOW_TIMEOUT_MS
        playerView.controllerHideOnTouch = true
        playerView.controllerAutoShow = false
        addControllerOptions(playerView)
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguages("es", "en")
                    .build()
            )
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,
                120_000,
                5_000,
                15_000
            )
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val extractorsFactory = DefaultExtractorsFactory()
        if (intent.getBooleanExtra(EXTRA_PARTIAL_TORRENT_STREAM, false)) {
            // Matroska cues commonly live at EOF; avoid seeking there before track formats exist.
            extractorsFactory.setMatroskaExtractorFlags(
                MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES
            )
        }
        val local = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
        localPlayer = local
        activePlayer = local
        playerView.player = local
        observePlaybackForScreenAwake(local)
        playerView.setShowSubtitleButton(true)
        playerView.setShowRewindButton(true)
        playerView.setShowFastForwardButton(true)
        keepSubtitleOptionsAvailable(playerView)
        local.setMediaItem(mediaItem)
        local.prepare()
        local.playWhenReady = true
        setupWatchHistoryRecovery(local)
        startProgressReporting(local)
        if (castEnabled) {
            setupCastAsync()
        }

        val root = FrameLayout(this)
        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val nextEpisodeOverlay = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                val themeMode = preferenceManager.getTheme()
                LaTorrentolaTheme(themeMode = themeMode) {
                    if (showNextEpisodeDialog) {
                        val nextData by tvPlayerViewModel.nextEpisodeData.collectAsState()
                        val releases by tvPlayerViewModel.nextEpisodeReleases.collectAsState()
                        val isLoading by tvPlayerViewModel.isLoading.collectAsState()
                        val error by tvPlayerViewModel.error.collectAsState()

                        val targetSeriesId = nextData?.seriesId?.takeIf { it > 0 } ?: nextEpisodeSeriesId
                        val targetSeriesName = nextData?.seriesName?.takeIf { it.isNotBlank() } ?: nextEpisodeSeriesName
                        val targetSeason = nextData?.seasonNumber?.takeIf { it > 0 } ?: nextEpisodeSeasonNumber
                        val targetEpisode = nextData?.episodeNumber?.takeIf { it > 0 } ?: nextEpisodeEpisodeNumber
                        val targetEpName = nextData?.episodeName?.takeIf { it.isNotBlank() } ?: nextEpisodeEpisodeName

                        NextEpisodeTorrentDialog(
                            seriesName = targetSeriesName,
                            seasonNumber = targetSeason,
                            episodeNumber = targetEpisode,
                            episodeName = targetEpName,
                            releases = releases,
                            isLoading = isLoading,
                            error = error,
                            onTorrentSelected = { torrent ->
                                showNextEpisodeDialog = false
                                visibility = View.GONE
                                checkDiskSpaceAndDownload(
                                    release = torrent,
                                    seriesId = targetSeriesId,
                                    seriesName = targetSeriesName,
                                    seasonNumber = targetSeason,
                                    episodeNumber = targetEpisode,
                                    episodeName = targetEpName
                                )
                            },
                            onDismiss = {
                                showNextEpisodeDialog = false
                                visibility = View.GONE
                                finish()
                            }
                        )
                    }
                }
            }
        }
        this.nextEpisodeOverlay = nextEpisodeOverlay
        root.addView(
            nextEpisodeOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val playerDialogOverlay = ComposeView(this).apply {
            visibility = View.GONE
            setContent {
                val themeMode = preferenceManager.getTheme()
                LaTorrentolaTheme(themeMode = themeMode) {
                    val state = playerDialogState
                    if (state != null) {
                        PlayerDialogContent(
                            state = state,
                            onDismiss = {
                                playerDialogState = null
                                visibility = View.GONE
                            }
                        )
                    }
                }
            }
        }
        this.playerDialogOverlay = playerDialogOverlay
        root.addView(
            playerDialogOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        playerView.showController()
        setupPlaybackCompletionListener()
    }

    /**
     * Listens for playback completion and triggers next episode quality selection or auto-play.
     */
    private fun setupPlaybackCompletionListener() {
        val completionListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && (localPlayer?.isPlaying == true || castPlayer?.isPlaying == true)) {
                    // Reset flag if playback resumed (e.g. user manually replayed the episode)
                    nextEpisodeTriggered = false
                } else if (playbackState == Player.STATE_ENDED && !nextEpisodeTriggered) {
                    nextEpisodeTriggered = true
                    handlePlaybackEnded()
                }
            }
        }
        localPlayer?.addListener(completionListener)
        castPlayer?.addListener(completionListener)
    }

    /**
     * Handles playback reaching the end of the media: immediately pauses the player and executes
     * next episode actions according to user preferences (DO_NOTHING, OFF, BY_SEED_PEERS, BY_QUALITY).
     */
    private fun handlePlaybackEnded() {
        // Immediately pause player to prevent looping on the same chapter
        localPlayer?.playWhenReady = false
        localPlayer?.pause()
        castPlayer?.playWhenReady = false
        castPlayer?.pause()

        val isTvEpisode = hasNextEpisodeInfo || (currentSeriesId > 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0)
        if (!isTvEpisode) {
            Log.d(TAG, "Playback ended for non-series media (movie); staying paused.")
            return
        }

        val autoPlayMode = preferenceManager.getAutoPlayQualitySelectionMethod()
        Log.d(TAG, "Playback ended for TV episode; mode=$autoPlayMode, hasNextInfo=$hasNextEpisodeInfo")

        when (autoPlayMode) {
            AutoPlayQualitySelectionMethod.DO_NOTHING -> {
                Log.d(TAG, "Auto-play setting is DO_NOTHING; playback stopped.")
            }
            AutoPlayQualitySelectionMethod.OFF -> {
                if (hasNextEpisodeInfo) {
                    tvPlayerViewModel.fetchReleasesForNextEpisode(
                        seriesId = nextEpisodeSeriesId,
                        seasonNumber = nextEpisodeSeasonNumber,
                        episodeNumber = nextEpisodeEpisodeNumber,
                        seriesName = nextEpisodeSeriesName,
                        episodeName = nextEpisodeEpisodeName
                    )
                } else {
                    tvPlayerViewModel.fetchNextEpisodeAndReleases(
                        seriesId = currentSeriesId,
                        currentSeasonNumber = currentSeasonNumber,
                        currentEpisodeNumber = currentEpisodeNumber
                    )
                }
                showNextEpisodeDialog = true
                nextEpisodeOverlay?.visibility = View.VISIBLE
                nextEpisodeOverlay?.requestFocus()
            }
            AutoPlayQualitySelectionMethod.BY_SEED_PEERS,
            AutoPlayQualitySelectionMethod.BY_QUALITY -> {
                handleAutoPlay(autoPlayMode)
            }
        }
    }

    /**
     * Resolves releases and automatically initiates download and playback for the best torrent.
     *
     * @param autoPlayMode Selection method: [AutoPlayQualitySelectionMethod.BY_SEED_PEERS] or [AutoPlayQualitySelectionMethod.BY_QUALITY].
     */
    private fun handleAutoPlay(autoPlayMode: AutoPlayQualitySelectionMethod) {
        if (hasNextEpisodeInfo) {
            tvPlayerViewModel.fetchReleasesForNextEpisode(
                seriesId = nextEpisodeSeriesId,
                seasonNumber = nextEpisodeSeasonNumber,
                episodeNumber = nextEpisodeEpisodeNumber,
                seriesName = nextEpisodeSeriesName,
                episodeName = nextEpisodeEpisodeName
            )
        } else {
            tvPlayerViewModel.fetchNextEpisodeAndReleases(
                seriesId = currentSeriesId,
                currentSeasonNumber = currentSeasonNumber,
                currentEpisodeNumber = currentEpisodeNumber
            )
        }

        lifecycleScope.launch {
            while (tvPlayerViewModel.isLoading.value) {
                kotlinx.coroutines.delay(150)
            }

            val releases = tvPlayerViewModel.nextEpisodeReleases.value
            val nextData = tvPlayerViewModel.nextEpisodeData.value

            if (releases.isEmpty()) {
                val errorMsg = tvPlayerViewModel.error.value
                    ?: getString(R.string.next_episode_no_torrents)
                Toast.makeText(this@LocalPlayerActivity, errorMsg, Toast.LENGTH_LONG).show()
                return@launch
            }

            val selectedRelease = when (autoPlayMode) {
                AutoPlayQualitySelectionMethod.BY_SEED_PEERS -> {
                    releases.maxByOrNull { it.seeds + it.peers } ?: releases.first()
                }
                AutoPlayQualitySelectionMethod.BY_QUALITY -> {
                    releases.maxByOrNull { qualityRank(it.title) } ?: releases.first()
                }
                else -> releases.first()
            }

            val targetSeriesId = nextData?.seriesId?.takeIf { it > 0 } ?: nextEpisodeSeriesId
            val targetSeriesName = nextData?.seriesName?.takeIf { it.isNotBlank() } ?: nextEpisodeSeriesName
            val targetSeason = nextData?.seasonNumber?.takeIf { it > 0 } ?: nextEpisodeSeasonNumber
            val targetEpisode = nextData?.episodeNumber?.takeIf { it > 0 } ?: nextEpisodeEpisodeNumber
            val targetEpName = nextData?.episodeName?.takeIf { it.isNotBlank() } ?: nextEpisodeEpisodeName

            val toastMsg = getString(
                R.string.next_episode_autoplay_toast,
                targetSeason,
                targetEpisode,
                extractQualityLabel(selectedRelease.title)
            )
            Toast.makeText(this@LocalPlayerActivity, toastMsg, Toast.LENGTH_SHORT).show()

            checkDiskSpaceAndDownload(
                release = selectedRelease,
                seriesId = targetSeriesId,
                seriesName = targetSeriesName,
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode,
                episodeName = targetEpName
            )
        }
    }

    /**
     * Assigns a numeric priority to release titles based on video resolution.
     *
     * @param title Torrent release title to inspect.
     * @return Quality weight with higher numbers representing preferred resolution.
     */
    private fun qualityRank(title: String): Int {
        val lower = title.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> 4
            lower.contains("1080p") -> 3
            lower.contains("720p") -> 2
            lower.contains("hdtv") -> 1
            else -> 0
        }
    }

    /**
     * Checks available storage space before proceeding with download and offers cleanup if low.
     */
    private fun checkDiskSpaceAndDownload(
        release: EztvTorrent,
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: String
    ) {
        val stat = android.os.StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBytes
        val requiredBytes = 500L * 1024 * 1024 // 500 MB threshold

        if (availableBytes < requiredBytes) {
            AlertDialog.Builder(this, R.style.Theme_LaTorrentola_Cast_Dialog)
                .setTitle("Insufficient Disk Space")
                .setMessage("Not enough disk space available. Would you like to erase all saved torrents on disk to free up space?")
                .setPositiveButton("Erase") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val downloads = torrentDownloadDao.getAll()
                            downloads.forEach { download ->
                                File(filesDir, "torrent_downloads/${download.infoHash}").deleteRecursively()
                                torrentDownloadDao.delete(download.infoHash)
                            }
                            val newStat = android.os.StatFs(filesDir.absolutePath)
                            if (newStat.availableBytes >= requiredBytes) {
                                executeDownload(
                                    release = release,
                                    seriesId = seriesId,
                                    seriesName = seriesName,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = episodeNumber,
                                    episodeName = episodeName
                                )
                            } else {
                                Toast.makeText(this@LocalPlayerActivity, "Not enough space available even after clearing saved torrents.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@LocalPlayerActivity, "Failed to clear saved torrents: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this@LocalPlayerActivity, "Download cancelled due to insufficient disk space.", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            executeDownload(
                release = release,
                seriesId = seriesId,
                seriesName = seriesName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeName = episodeName
            )
        }
    }

    /**
     * Launches the download of the chosen episode release and closes the finished player activity.
     */
    private fun executeDownload(
        release: EztvTorrent,
        seriesId: Int,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeName: String
    ) {
        lifecycleScope.launch {
            try {
                val result = tvPlayerViewModel.downloadEpisode(
                    context = this@LocalPlayerActivity,
                    release = release,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeName = episodeName
                )
                if (result == TorrentLaunchResult.Started) {
                    Toast.makeText(
                        this@LocalPlayerActivity,
                        getString(R.string.tv_episode_downloaded),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else if (result == TorrentLaunchResult.NoExternalClient) {
                    Toast.makeText(
                        this@LocalPlayerActivity,
                        getString(R.string.toast_no_torrent_client),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@LocalPlayerActivity,
                        "Failed to start download: $result",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting next episode download", e)
                Toast.makeText(
                    this@LocalPlayerActivity,
                    e.localizedMessage ?: "Download error",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Extracts a standard quality label from a torrent release title.
     *
     * @param title Title containing resolution tags.
     * @return Formatted quality descriptor.
     */
    private fun extractQualityLabel(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") -> "2160p"
            lower.contains("1080p") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("hdtv") -> "HDTV"
            else -> "SD"
        }
    }

    /**
     * Adds audio, subtitle, and OpenSubtitles actions to Media3's own controller row.
     *
     * @param view Media3 player view whose native controller receives the actions.
     */
    private fun addControllerOptions(view: PlayerView) {
        val controls = requireNotNull(
            view.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
        ) { "Media3 basic controller controls are unavailable" }
        val subtitleButton = requireNotNull(
            view.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_subtitle)
        ) { "Media3 subtitle controller button is unavailable" }
        subtitleButton.setOnClickListener { showSubtitleOptions() }
        subtitleButton.isFocusable = true
        applyTvFocusHighlight(subtitleButton)
        val buttonStyle = androidx.media3.ui.R.style.ExoStyledControls_Button_Bottom
        val subtitleButtonIndex = controls.indexOfChild(subtitleButton)

        if (castEnabled) {
            try {
                val routeButton = MediaRouteButton(
                    ContextThemeWrapper(this@LocalPlayerActivity, R.style.Theme_LaTorrentola_Cast)
                ).apply {
                    id = View.generateViewId()
                    CastButtonFactory.setUpMediaRouteButton(this@LocalPlayerActivity, this)
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = (16 * resources.displayMetrics.density).toInt()
                    marginEnd = (16 * resources.displayMetrics.density).toInt()
                }
                controls.addView(routeButton, subtitleButtonIndex + 1, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach MediaRouteButton in controls", e)
            }
        }

        listOf(
            Triple(
                R.drawable.ic_subtitle_style,
                R.string.player_subtitle_style,
                ::showSubtitleStyleOptions
            ),
            Triple(
                R.drawable.ic_subtitles_search,
                R.string.opensubtitles_search,
                ::searchSubtitles
            )
        ).forEachIndexed { index, (icon, description, action) ->
            val button = ImageButton(this, null, 0, buttonStyle).apply {
                setImageResource(icon)
                contentDescription = getString(description)
                setOnClickListener {
                    Log.i(TAG, "Player controller action clicked: ${getString(description)}")
                    action()
                }
                isFocusable = true
                isFocusableInTouchMode = true
                id = View.generateViewId()
            }
            applyTvFocusHighlight(button)
            if (icon == R.drawable.ic_subtitles_search) {
                installDirectTouchActivation(button, description)
                subtitleSearchButton = button
                if (subtitleOperationInProgress) {
                    updateSubtitleSearchButton(inProgress = true)
                }
            }
            controls.addView(button, subtitleButtonIndex + index + 1)
        }
        listOf(
            Triple(
                androidx.media3.ui.R.drawable.exo_ic_audiotrack,
                R.string.player_audio_tracks
            ) { showTrackOptions(PlaybackTrackType.AUDIO) }
        ).forEach { (icon, description, action) ->
            val audioBtn = ImageButton(this, null, 0, buttonStyle).apply {
                setImageResource(icon)
                contentDescription = getString(description)
                setOnClickListener { action() }
                isFocusable = true
                isFocusableInTouchMode = true
                id = View.generateViewId()
            }
            applyTvFocusHighlight(audioBtn)
            controls.addView(audioBtn)
        }
    }

    /**
     * Delivers one explicit click after a complete touch gesture on the OpenSubtitles control.
     *
     * @param button Search button receiving the touch gesture.
     * @param description Resource identifying the action in diagnostics.
     */
    private fun installDirectTouchActivation(button: ImageButton, description: Int) {
        var touchStartedOnButton = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        button.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchStartedOnButton = target.isEnabled
                    target.isPressed = touchStartedOnButton
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    target.isPressed = touchStartedOnButton &&
                        event.x >= -touchSlop && event.x <= target.width + touchSlop &&
                        event.y >= -touchSlop && event.y <= target.height + touchSlop
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val shouldClick = touchStartedOnButton && target.isEnabled &&
                        event.x >= -touchSlop && event.x <= target.width + touchSlop &&
                        event.y >= -touchSlop && event.y <= target.height + touchSlop
                    touchStartedOnButton = false
                    target.isPressed = false
                    if (shouldClick) {
                        Log.i(TAG, "Direct touch activated: ${getString(description)}")
                        target.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    touchStartedOnButton = false
                    target.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Keeps the native CC control available even when a file has no embedded subtitles.
     *
     * @param view Media3 view whose controller hosts the subtitle actions.
     */
    private fun keepSubtitleOptionsAvailable(view: PlayerView) {
        val button = requireNotNull(
            view.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_subtitle)
        ) { "Media3 subtitle controller button is unavailable" }
        button.visibility = View.VISIBLE
        localPlayer?.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    button.visibility = View.VISIBLE
                    prepareSubtitleTrackIfNeeded(tracks)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        prepareSubtitleTrackIfNeeded(localPlayer?.currentTracks)
                    }
                }
            }
        )
    }

    /**
     * Loads the latest torrent-cached subtitle, or warms the OpenSubtitles session.
     *
     * @param tracks Current available playback tracks, or `null` before they are initialized.
     */
    private fun prepareSubtitleTrackIfNeeded(tracks: androidx.media3.common.Tracks?) {
        val player = localPlayer ?: return
        if (subtitleStartupCheckStarted || player.playbackState != Player.STATE_READY || tracks == null) {
            return
        }

        subtitleStartupCheckStarted = true
        Log.i(TAG, "Checking torrent subtitle cache for available subtitles")
        lifecycleScope.launch {
            try {
                val cachedSubtitle = openSubtitlesRepository.findLatestDownloaded(
                    torrentInfoHash.takeIf(String::isNotBlank)
                )
                if (cachedSubtitle != null) {
                    Log.i(TAG, "Found saved subtitle for current torrent; attaching automatically")
                    if (!subtitleOperationInProgress) attachSubtitle(cachedSubtitle)
                } else {
                    Log.i(TAG, "No saved subtitle for current torrent; warming OpenSubtitles session")
                    openSubtitlesRepository.warmUp()
                    Log.i(TAG, "OpenSubtitles session warm-up completed")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "OpenSubtitles session warm-up failed: ${error.javaClass.simpleName}, " +
                        "message=${error.message}"
                )
            }
        }
    }

    /**
     * Presents selectable audio or subtitle tracks available on the active playback target.
     *
     * @param type Track kind requested by the user.
     */
    private fun showTrackOptions(type: PlaybackTrackType) {
        val options = availableTrackOptions(type)
        val isSubtitle = type == PlaybackTrackType.SUBTITLE
        val defaultLabel = getString(
            if (isSubtitle) R.string.player_subtitles_off else R.string.player_audio_auto
        )
        if (options.isEmpty()) {
            playerDialogState = PlayerDialogState.Message(
                title = getString(if (isSubtitle) R.string.player_subtitle_tracks else R.string.player_audio_tracks),
                message = getString(if (isSubtitle) R.string.player_no_subtitle_tracks else R.string.player_no_audio_tracks),
                onDismiss = { playerDialogState = null }
            )
            playerDialogOverlay?.visibility = View.VISIBLE
            playerDialogOverlay?.requestFocus()
            return
        }

        val selectedIndex = options.indexOfFirst { option ->
            if (option.castTrackId != null) {
                castActiveTrackIds().contains(option.castTrackId)
            } else {
                option.isSelected
            }
        }.let { if (it < 0) 0 else it + 1 }
        playerDialogState = PlayerDialogState.Tracks(
            type = type,
            title = getString(if (isSubtitle) R.string.player_subtitle_tracks else R.string.player_audio_tracks),
            options = options,
            selectedIndex = selectedIndex - 1,
            onSelected = { option ->
                if (castSessionActive) {
                    selectCastTrack(type, option?.castTrackId)
                } else {
                    selectLocalTrack(type, option)
                }
                playerDialogState = null
                playerDialogOverlay?.visibility = View.GONE
            }
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
    }

    /** Shows embedded subtitle tracks from the native CC control. */
    private fun showSubtitleOptions() {
        val options = availableTrackOptions(PlaybackTrackType.SUBTITLE)
        val selectedTrackIndex = options.indexOfFirst(PlaybackTrackOption::isSelected)
        val selectedIndex = if (selectedTrackIndex < 0) 0 else selectedTrackIndex + 1
        playerDialogState = PlayerDialogState.Tracks(
            type = PlaybackTrackType.SUBTITLE,
            title = getString(R.string.player_subtitle_tracks),
            options = options,
            selectedIndex = selectedIndex - 1,
            onSelected = { option ->
                if (castSessionActive) {
                    selectCastTrack(PlaybackTrackType.SUBTITLE, option?.castTrackId)
                } else {
                    selectLocalTrack(PlaybackTrackType.SUBTITLE, option)
                }
                playerDialogState = null
                playerDialogOverlay?.visibility = View.GONE
            }
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
    }

    /** Presents text-size and system-caption styling options for local playback. */
    private fun showSubtitleStyleOptions() {
        val selectedIndex = when (subtitleTextSizeFraction) {
            0.04f -> 1
            0.067f -> 3
            0.08f -> 4
            else -> 0
        }
        playerDialogState = PlayerDialogState.SubtitleStyle(
            selectedIndex = selectedIndex,
            onSelected = { fraction, applySystem ->
                val subtitleView = playerView?.subtitleView
                if (applySystem) {
                    subtitleTextSizeFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    subtitleView?.setApplyEmbeddedStyles(true)
                } else {
                    subtitleTextSizeFraction = fraction
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setApplyEmbeddedStyles(false)
                    subtitleView?.setFractionalTextSize(subtitleTextSizeFraction)
                }
                playerDialogState = null
                playerDialogOverlay?.visibility = View.GONE
            }
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
    }

    /**
     * Returns local Media3 or Cast receiver tracks for the selected track type.
     *
     * @param type Requested audio or subtitle track type.
     * @return Currently available selectable tracks.
     */
    private fun availableTrackOptions(type: PlaybackTrackType): List<PlaybackTrackOption> =
        if (castSessionActive) castTrackOptions(type) else localTrackOptions(type)

    /**
     * Returns supported local Media3 tracks for the selected track type.
     *
     * @param type Requested audio or subtitle track type.
     * @return Supported track groups and individual track indices.
     */
    private fun localTrackOptions(type: PlaybackTrackType): List<PlaybackTrackOption> {
        val media3Type = type.toMedia3TrackType()
        return localPlayer?.currentTracks
            ?.groups
            .orEmpty()
            .filter { it.type == media3Type }
            .flatMap { group ->
                (0 until group.length)
                    .filter(group::isTrackSupported)
                    .map { index ->
                        PlaybackTrackOption(
                            label = trackLabel(group.getTrackFormat(index), type),
                            group = group.mediaTrackGroup,
                            trackIndex = index,
                            castTrackId = null,
                            isSelected = group.isTrackSelected(index)
                        )
                    }
            }
    }

    /**
     * Returns tracks advertised by the active Cast receiver.
     *
     * @param type Requested audio or subtitle track type.
     * @return Receiver tracks supported by the target.
     */
    private fun castTrackOptions(type: PlaybackTrackType): List<PlaybackTrackOption> {
        val receiverType = when (type) {
            PlaybackTrackType.AUDIO -> GoogleMediaTrack.TYPE_AUDIO
            PlaybackTrackType.SUBTITLE -> GoogleMediaTrack.TYPE_TEXT
        }
        val castContext = try {
            CastContext.getSharedInstance(this)
        } catch (_: Throwable) {
            null
        } ?: return emptyList()
        return castContext
            .sessionManager
            .currentCastSession
            ?.remoteMediaClient
            ?.mediaInfo
            ?.mediaTracks
            .orEmpty()
            .filter { it.type == receiverType }
            .map { track ->
                PlaybackTrackOption(
                    label = track.name?.takeIf(String::isNotBlank)
                        ?: track.language?.let(::languageLabel)
                        ?: getString(R.string.player_track_number, track.id),
                    group = null,
                    trackIndex = null,
                    castTrackId = track.id,
                    isSelected = castActiveTrackIds().contains(track.id)
                )
            }
    }

    /**
     * Applies a selected local track, or restores the player's automatic selection.
     *
     * @param type Track kind to update.
     * @param option Selected track, or `null` for automatic audio / subtitles off.
     */
    private fun selectLocalTrack(type: PlaybackTrackType, option: PlaybackTrackOption?) {
        val player = localPlayer ?: return
        val trackType = type.toMedia3TrackType()
        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, type == PlaybackTrackType.SUBTITLE && option == null)
        if (option != null) {
            val group = option.group ?: return
            val trackIndex = option.trackIndex ?: return
            player.trackSelectionParameters = parameters
                .setTrackTypeDisabled(trackType, false)
                .setOverrideForType(TrackSelectionOverride(group, listOf(trackIndex)))
                .build()
        } else {
            player.trackSelectionParameters = parameters.build()
        }
    }

    /**
     * Updates the active receiver track while preserving active tracks of the other type.
     *
     * @param type Track kind to update.
     * @param selectedTrackId Receiver track to activate, or `null` for auto/off.
     */
    private fun selectCastTrack(type: PlaybackTrackType, selectedTrackId: Long?) {
        val castContext = try {
            CastContext.getSharedInstance(this)
        } catch (_: Throwable) {
            null
        } ?: return
        val remoteClient = castContext
            .sessionManager
            .currentCastSession
            ?.remoteMediaClient
            ?: return
        val receiverType = when (type) {
            PlaybackTrackType.AUDIO -> GoogleMediaTrack.TYPE_AUDIO
            PlaybackTrackType.SUBTITLE -> GoogleMediaTrack.TYPE_TEXT
        }
        val tracks = remoteClient.mediaInfo?.mediaTracks.orEmpty()
        val retainedTrackIds = castActiveTrackIds().filter { activeId ->
            tracks.none { it.id == activeId && it.type == receiverType }
        }
        val selectedTrackIds = buildList {
            addAll(retainedTrackIds)
            selectedTrackId?.let(::add)
        }
        remoteClient.setActiveMediaTracks(selectedTrackIds.toLongArray())
    }

    /** Returns active receiver track identifiers, if a Cast media status is available. */
    private fun castActiveTrackIds(): List<Long> {
        val castContext = try {
            CastContext.getSharedInstance(this)
        } catch (_: Throwable) {
            null
        } ?: return emptyList()
        return castContext
            .sessionManager
            .currentCastSession
            ?.remoteMediaClient
            ?.mediaStatus
            ?.activeTrackIds
            ?.toList()
            .orEmpty()
    }

    /**
     * Creates a readable language/format label for an audio or subtitle format.
     *
     * @param format Media3 format metadata.
     * @param type Track kind for extra audio channel details.
     * @return Track label shown to the user.
     */
    private fun trackLabel(format: androidx.media3.common.Format, type: PlaybackTrackType): String {
        val language = format.language?.let(::languageLabel)
        val label = format.label?.takeIf(String::isNotBlank)
        val channels = if (type == PlaybackTrackType.AUDIO && format.channelCount > 0) {
            getString(R.string.player_audio_channels, format.channelCount)
        } else {
            null
        }
        return listOfNotNull(label, language, channels)
            .distinct()
            .joinToString(" · ")
            .ifBlank { format.sampleMimeType ?: getString(R.string.player_track_unknown) }
    }

    /**
     * Converts a language tag to a localized display name.
     *
     * @param language BCP-47 language code.
     * @return Localized language name or the original code.
     */
    private fun languageLabel(language: String): String =
        Locale.forLanguageTag(language).getDisplayName(Locale.getDefault())
            .takeIf(String::isNotBlank)
            ?: language

    /** Maps the in-player track category to the corresponding Media3 track type. */
    private fun PlaybackTrackType.toMedia3TrackType(): Int = when (this) {
        PlaybackTrackType.AUDIO -> C.TRACK_TYPE_AUDIO
        PlaybackTrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
    }

    /**
     * Animates and disables the OpenSubtitles search control while a request is unresolved.
     *
     * @param inProgress Whether login, search, or subtitle download is still active.
     */
    private fun updateSubtitleSearchButton(inProgress: Boolean) {
        subtitleOperationInProgress = inProgress
        val button = subtitleSearchButton ?: return
        button.isEnabled = !inProgress
        if (inProgress) {
            button.contentDescription = getString(R.string.opensubtitles_searching)
            subtitleSearchAnimator = ObjectAnimator.ofFloat(button, View.ROTATION, 0f, 360f).apply {
                duration = 1_000L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        } else {
            subtitleSearchAnimator?.cancel()
            subtitleSearchAnimator = null
            button.rotation = 0f
            button.contentDescription = getString(R.string.opensubtitles_search)
        }
    }

    /** Searches OpenSubtitles once per user action and presents matching releases. */
    private fun searchSubtitles() {
        if (subtitleOperationInProgress) {
            Log.w(TAG, "OpenSubtitles search tap ignored: an operation is already active")
            return
        }
        Log.i(TAG, "OpenSubtitles search tapped")
        updateSubtitleSearchButton(inProgress = true)
        val progressDialog = showSubtitleProgressDialog(R.string.opensubtitles_searching)
        lifecycleScope.launch {
            var results: List<OpenSubtitleResult>? = null
            var message: String? = null
            try {
                val mediaType = if (EPISODE_PATTERN.containsMatchIn(mediaTitle)) "episode" else "movie"
                val foundResults = openSubtitlesRepository.search(mediaTitle, type = mediaType)
                results = foundResults
                Log.i(TAG, "OpenSubtitles search finished: resultCount=${foundResults.size}")
                if (foundResults.isEmpty()) {
                    message = getString(R.string.opensubtitles_no_results)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: OpenSubtitlesException) {
                Log.e(
                    TAG,
                    "OpenSubtitles search failed: code=${error.code}, httpStatus=${error.httpStatus}, " +
                        "message=${error.message}"
                )
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: IOException) {
                Log.e(TAG, "OpenSubtitles search I/O failure: ${error.javaClass.simpleName}")
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: IllegalArgumentException) {
                Log.e(TAG, "OpenSubtitles search input failure: ${error.javaClass.simpleName}")
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: Exception) {
                Log.e(TAG, "Unexpected OpenSubtitles search failure: ${error.javaClass.simpleName}")
                message = getString(R.string.opensubtitles_request_failed)
            } finally {
                progressDialog?.dismiss()
                updateSubtitleSearchButton(inProgress = false)
                playerDialogState = null
                playerDialogOverlay?.visibility = View.GONE
            }
            results?.takeIf { it.isNotEmpty() }?.let(::showSubtitleResults)
            message?.let(::showSubtitleMessage)
        }
    }

    /**
     * Shows the matching subtitle releases as an accessible selectable list.
     *
     * @param results OpenSubtitles results for this media title.
     */
    private fun showSubtitleResults(results: List<OpenSubtitleResult>) {
        Log.i(TAG, "Showing OpenSubtitles list: resultCount=${results.size}")
        playerDialogState = PlayerDialogState.SubtitleResults(
            results = results,
            onSelected = { result ->
                downloadSubtitle(result)
            }
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
    }

    /**
     * Downloads a selected subtitle and attaches it to local and Cast playback.
     *
     * @param result Subtitle result selected from the OpenSubtitles list.
     */
    private fun downloadSubtitle(result: OpenSubtitleResult) {
        if (subtitleOperationInProgress) {
            Log.w(TAG, "OpenSubtitles download tap ignored: an operation is already active")
            return
        }
        Log.i(TAG, "OpenSubtitles subtitle selected: language=${result.language}, fileId=${result.fileId}")
        updateSubtitleSearchButton(inProgress = true)
        val progressDialog = showSubtitleProgressDialog(R.string.opensubtitles_downloading)
        lifecycleScope.launch {
            var errorMessage: String? = null
            try {
                val subtitle = openSubtitlesRepository.download(
                    result,
                    torrentInfoHash = torrentInfoHash.takeIf(String::isNotBlank)
                )
                attachSubtitle(subtitle)
            } catch (error: CancellationException) {
                throw error
            } catch (error: OpenSubtitlesException) {
                Log.e(
                    TAG,
                    "OpenSubtitles download failed: code=${error.code}, httpStatus=${error.httpStatus}, " +
                        "message=${error.message}"
                )
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IOException) {
                Log.e(TAG, "OpenSubtitles download I/O failure: ${error.javaClass.simpleName}")
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "OpenSubtitles playback setup failure: ${error.javaClass.simpleName}")
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IllegalArgumentException) {
                Log.e(TAG, "OpenSubtitles download argument failure: ${error.javaClass.simpleName}")
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: Exception) {
                Log.e(TAG, "Unexpected OpenSubtitles download failure: ${error.javaClass.simpleName}")
                errorMessage = getString(R.string.opensubtitles_download_failed)
            } finally {
                progressDialog?.dismiss()
                updateSubtitleSearchButton(inProgress = false)
                playerDialogState = null
                playerDialogOverlay?.visibility = View.GONE
            }
            errorMessage?.let(::showSubtitleMessage)
        }
    }

    /**
     * Adds a saved WebVTT subtitle to the current local or Cast playback item.
     *
     * @param subtitle Cached or newly downloaded subtitle to activate.
     */
    private fun attachSubtitle(subtitle: DownloadedSubtitle) {
        val localUri = Uri.fromFile(subtitle.file)
        val castUri = if (castEnabled) {
            val server = VerifiedTorrentHttpServer(subtitle.file, subtitle.file.length()) { _, _ -> true }
            server.startServer()
            try {
                server.lanUrl().also { SubtitleServerRegistry.retain(server) }
            } catch (error: IllegalStateException) {
                server.stop()
                throw error
            }
        } else {
            null
        }
        if (castUri != null) castSubtitleUrls[localUri.toString()] = castUri
        val subtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(localUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(toLanguageTag(subtitle.language))
            .setLabel(subtitle.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .setRoleFlags(C.ROLE_FLAG_CAPTION)
            .build()
        val updatedItem = requireNotNull(currentMediaItem)
            .buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfiguration))
            .build()
        currentMediaItem = updatedItem
        val player = requireNotNull(activePlayer)
        val currentPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.setMediaItem(updatedItem, currentPosition)
        player.prepare()
        player.playWhenReady = wasPlaying
        Log.i(TAG, "OpenSubtitles subtitle attached to playback")
        Toast.makeText(this, R.string.opensubtitles_loaded, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a non-cancelable progress dialog sized for the current form factor.
     *
     * @param message Resource describing the active subtitle operation.
     * @return Visible progress dialog dismissed when the operation completes.
     */
    private fun showSubtitleProgressDialog(message: Int): AlertDialog? {
        val msgString = getString(message)
        playerDialogState = PlayerDialogState.Progress(
            title = getString(R.string.opensubtitles_title),
            message = msgString
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
        return null
    }

    private fun showSubtitleMessage(message: String) {
        playerDialogState = PlayerDialogState.Message(
            title = getString(R.string.opensubtitles_title),
            message = message,
            onDismiss = { playerDialogState = null }
        )
        playerDialogOverlay?.visibility = View.VISIBLE
        playerDialogOverlay?.requestFocus()
    }

    /**
     * Sizes subtitle dialogs for touch screens and viewing-distance layouts.
     *
     * @param dialog Subtitle dialog to show.
     */
    private fun showAdaptiveDialog(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val widthFraction = if (isTvDevice()) 0.72f else 0.92f
            val width = (resources.displayMetrics.widthPixels * widthFraction).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    /**
     * Creates the unified local/Cast player, keeping local playback available if Cast is unavailable.
     *
     * @param local Local ExoPlayer used when no receiver session is active.
     * @return Configured Cast player, or `null` if Cast services are unavailable.
     */
    @Suppress("DEPRECATION")
    private fun setupCastAsync() {
        if (isTvDevice() || isFinishing || isDestroyed) return
        Log.d(TAG, "DEBUG CAST: setupCastAsync called")
        try {
            val mainExecutor = ContextCompat.getMainExecutor(this)
            CastContext.getSharedInstance(applicationContext, mainExecutor)
                .addOnSuccessListener { castContext ->
                    Log.d(TAG, "DEBUG CAST: CastContext onSuccess listener triggered, castContext=$castContext")
                    if (isFinishing || isDestroyed) return@addOnSuccessListener
                    try {
                        val remotePlayer = CastPlayer(
                            castContext,
                            castUrlConverter(castStreamUrl, localStreamUrl)
                        )
                        castPlayer = remotePlayer
                        remotePlayer.setSessionAvailabilityListener(
                            object : SessionAvailabilityListener {
                                override fun onCastSessionAvailable() {
                                    switchToCastPlayer()
                                }

                                override fun onCastSessionUnavailable() {
                                    switchToLocalPlayer()
                                }
                            }
                        )
                        if (remotePlayer.isCastSessionAvailable()) {
                            switchToCastPlayer()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DEBUG CAST: CastPlayer setup failed: ${e.javaClass.simpleName} - ${e.message}", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "DEBUG CAST: CastContext initialization failed asynchronously: ${e.javaClass.simpleName} - ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "CastContext.getSharedInstance call failed", e)
        }
    }

    /** Transfers the current local playback item and position to an available Cast receiver. */
    private fun switchToCastPlayer() {
        val local = localPlayer ?: return
        val remote = castPlayer ?: return
        val view = playerView ?: return
        val item = currentMediaItem ?: return
        val position = local.currentPosition
        val shouldPlay = local.playWhenReady
        local.playWhenReady = false
        remote.setMediaItem(item, position)
        remote.prepare()
        remote.playWhenReady = shouldPlay
        activePlayer = remote
        view.player = remote
        castSessionActive = true
        observePlaybackForScreenAwake(remote)
    }

    /** Returns playback to the local player when the receiver session ends. */
    private fun switchToLocalPlayer() {
        val remote = castPlayer ?: return
        val local = localPlayer ?: return
        val view = playerView ?: return
        val item = currentMediaItem ?: return
        val position = remote.currentPosition.coerceAtLeast(0L)
        val shouldPlay = remote.playWhenReady
        local.setMediaItem(item, position)
        local.prepare()
        local.playWhenReady = shouldPlay
        activePlayer = local
        view.player = local
        castSessionActive = false
        observePlaybackForScreenAwake(local)
    }

    /**
     * Keeps Android TV out of Ambient mode while playback is active, including buffering.
     *
     * @param player Current local or Cast playback target.
     */
    private fun observePlaybackForScreenAwake(player: Player) {
        screenAwakePlayer?.let { observed ->
            screenAwakeListener?.let(observed::removeListener)
        }
        screenAwakePlayer = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateScreenAwakeState(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateScreenAwakeState(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateScreenAwakeState(player)
            }
        }
        screenAwakeListener = listener
        player.addListener(listener)
        updateScreenAwakeState(player)
    }

    /**
     * Applies the TV keep-screen-on flag only while the current playback target is active.
     *
     * @param player Player whose state determines whether the screen should stay awake.
     */
    private fun updateScreenAwakeState(player: Player) {
        if (!isTvDevice() || player !== activePlayer || player !== screenAwakePlayer) return
        val shouldKeepScreenAwake =
            player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
        if (shouldKeepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Maps common OpenSubtitles ISO-639-2 codes to BCP-47 tags understood by playback engines.
     *
     * @param language OpenSubtitles language code.
     * @return BCP-47 language tag used by Media3 and Cast.
     */
    private fun toLanguageTag(language: String): String {
        val code = when (language.lowercase(Locale.ROOT)) {
            "eng" -> "en"
            "spa" -> "es"
            "fra", "fre" -> "fr"
            "deu", "ger" -> "de"
            "ita" -> "it"
            "por" -> "pt"
            "jpn" -> "ja"
            "kor" -> "ko"
            "rus" -> "ru"
            "zho", "chi" -> "zh"
            else -> language.lowercase(Locale.ROOT)
        }
        return Locale.forLanguageTag(code).toLanguageTag()
    }

    /**
     * Converts app-local playback items into receiver-reachable Cast media and subtitle tracks.
     *
     * @param castUrl Receiver-reachable URL for the torrent media stream.
     * @param localUrl Loopback URL for local playback when a Cast session ends.
     * @return Media3 converter that maps downloaded subtitle tracks to their LAN URLs.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun castUrlConverter(castUrl: String, localUrl: String): MediaItemConverter {
        val delegate = DefaultMediaItemConverter()
        return object : MediaItemConverter {
            override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
                val remoteItem = mediaItem.buildUpon().setUri(Uri.parse(castUrl)).build()
                val defaultItem = delegate.toMediaQueueItem(remoteItem)
                val defaultMedia = requireNotNull(defaultItem.media)
                val tracks = remoteItem.localConfiguration?.subtitleConfigurations
                    .orEmpty()
                    .mapIndexed { index, subtitle ->
                        MediaTrack.Builder((index + 1).toLong(), MediaTrack.TYPE_TEXT)
                            .setContentId(
                                castSubtitleUrls[subtitle.uri.toString()] ?: subtitle.uri.toString()
                            )
                            .setContentType(subtitle.mimeType ?: MimeTypes.TEXT_VTT)
                            .setName(subtitle.label ?: subtitle.language.orEmpty())
                            .setLanguage(subtitle.language.orEmpty())
                            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                            .build()
                    }
                val metadata = CastMetadata(CastMetadata.MEDIA_TYPE_MOVIE).apply {
                    mediaItem.mediaMetadata.title?.toString()?.let {
                        putString(CastMetadata.KEY_TITLE, it)
                    }
                }
                val mediaInfo = MediaInfo.Builder(castUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(remoteItem.localConfiguration?.mimeType ?: MimeTypes.VIDEO_UNKNOWN)
                    .setMetadata(metadata)
                    .setMediaTracks(tracks)
                    .setCustomData(defaultMedia.customData)
                    .build()
                val queueItemBuilder = MediaQueueItem.Builder(mediaInfo)
                if (tracks.isNotEmpty()) {
                    queueItemBuilder.setActiveTrackIds(longArrayOf(tracks.first().id))
                }
                return queueItemBuilder.build()
            }

            override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
                delegate.toMediaItem(mediaQueueItem)
                    .buildUpon()
                    .setUri(Uri.parse(localUrl))
                    .build()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val playerView = playerView ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_UP,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val controllerView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
                val isVisible = controllerView?.visibility == View.VISIBLE
                if (!isVisible) {
                    playerView.showController()
                    val playButton = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play)
                        ?: playerView.findViewById<View>(androidx.media3.ui.R.id.exo_pause)
                    playButton?.requestFocus()
                    return true
                }
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                playerView.showController()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        saveCurrentProgress(activePlayer)
        super.onPause()
    }

    override fun onStop() {
        saveCurrentProgress(activePlayer)
        activePlayer?.pause()
        super.onStop()
    }

    private var progressReportingJob: kotlinx.coroutines.Job? = null
    private var initialSeekPerformed = false

    private fun getMediaId(): String {
        return when {
            currentSeriesId > 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0 ->
                "media_${currentSeriesId}_s${currentSeasonNumber}_e${currentEpisodeNumber}"
            currentMovieId > 0 ->
                currentMovieId.toString()
            torrentInfoHash.isNotBlank() ->
                torrentInfoHash
            else ->
                mediaTitle
        }
    }

    private fun saveCurrentProgress(player: Player?) {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0L) return
        val mediaId = getMediaId()
        if (mediaId.isBlank()) return
        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = mediaTitle.ifBlank { "Media Item" },
            positionMs = pos,
            durationMs = dur,
            timestamp = System.currentTimeMillis(),
            isEpisode = currentSeriesId > 0
        )
        lifecycleScope.launch {
            try {
                userLibraryRepository.savePlaybackProgress(progress)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playback progress", e)
            }
        }
    }

    private fun startProgressReporting(player: Player) {
        progressReportingJob?.cancel()
        progressReportingJob = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L)
                if (player.isPlaying) {
                    saveCurrentProgress(player)
                }
            }
        }
    }

    private fun setupWatchHistoryRecovery(player: ExoPlayer) {
        val mediaId = getMediaId()
        if (mediaId.isBlank()) return
        lifecycleScope.launch {
            try {
                val saved = userLibraryRepository.getPlaybackProgress(mediaId)
                if (saved != null && saved.positionMs > 5000L && !saved.isCompleted) {
                    val targetPosition = saved.positionMs
                    if (player.playbackState == Player.STATE_READY && !initialSeekPerformed) {
                        initialSeekPerformed = true
                        player.seekTo(targetPosition)
                    } else if (!initialSeekPerformed) {
                        player.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY && !initialSeekPerformed) {
                                    initialSeekPerformed = true
                                    player.seekTo(targetPosition)
                                    player.removeListener(this)
                                }
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching watch history recovery", e)
            }
        }
    }

    /** Releases decoder and subtitle streaming resources when this activity is destroyed. */
    override fun onDestroy() {
        progressReportingJob?.cancel()
        saveCurrentProgress(activePlayer)
        subtitleSearchAnimator?.cancel()
        subtitleSearchAnimator = null
        screenAwakePlayer?.let { player ->
            screenAwakeListener?.let(player::removeListener)
        }
        screenAwakeListener = null
        screenAwakePlayer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView?.player = null
        castPlayer?.release()
        localPlayer?.release()
        if (!castSessionActive) SubtitleServerRegistry.stopAll()
        castSubtitleUrls.clear()
        activePlayer = null
        playerView = null
        castPlayer = null
        localPlayer = null
        super.onDestroy()
    }

    private fun applyTvFocusHighlight(view: View) {
        if (!isTvDevice()) return
        val context = view.context
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val primaryColor = if (typedValue.data != 0) typedValue.data else android.graphics.Color.parseColor("#BB86FC")

        val normalBg = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        val focusedBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12f * context.resources.displayMetrics.density
            setColor(android.graphics.Color.argb(76, android.graphics.Color.red(primaryColor), android.graphics.Color.green(primaryColor), android.graphics.Color.blue(primaryColor)))
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedBg)
            addState(intArrayOf(), normalBg)
        }
        view.background = stateList
    }

    companion object {
        /** Logcat tag for local playback and subtitle search diagnostics. */
        private const val TAG = "LocalPlayer"

        /** Initial player control visibility duration before Media3 hides the controls. */
        private const val PLAYER_CONTROLLER_SHOW_TIMEOUT_MS = 5_000

        /** Intent extra containing next episode information for quality selection. */
        const val EXTRA_NEXT_EPISODE_INFO = "extra_next_episode_info"

        /** Intent extra containing an app-private, fully verified media file path. */
        const val EXTRA_FILE_PATH = "verified_media_file_path"

        /** Intent extra containing the local HTTP stream URL for verified byte ranges. */
        const val EXTRA_STREAM_URL = "verified_media_stream_url"

        /** Intent extra containing the local-network URL available to Cast receivers. */
        const val EXTRA_CAST_URL = "verified_media_cast_url"

        /** Intent extra containing the local torrent's stable info hash. */
        const val EXTRA_INFO_HASH = "torrent_info_hash"

        /** Intent extra enabling early-start extraction without cue seeking for an incomplete stream. */
        const val EXTRA_PARTIAL_TORRENT_STREAM = "partial_torrent_stream"

        /** Intent extra indicating whether Cast device selection should be available. */
        const val EXTRA_CAST_ENABLED = "torrent_cast_enabled"

        /** Intent extra containing the video MIME type. */
        const val EXTRA_MIME_TYPE = "torrent_media_mime_type"

        /** Intent extra containing the user-visible media title. */
        const val EXTRA_TITLE = "torrent_media_title"

        /** Intent extra containing series ID. */
        const val EXTRA_SERIES_ID = "extra_series_id"

        /** Intent extra containing season number. */
        const val EXTRA_SEASON_NUMBER = "extra_season_number"

        /** Intent extra containing episode number. */
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"

        /** Intent extra containing movie ID. */
        const val EXTRA_MOVIE_ID = "extra_movie_id"

        /** Identifies the season/episode naming used by the TV release-selection screen. */
        val EPISODE_PATTERN = Regex("""\bS\d{2}E\d{2}\b""", RegexOption.IGNORE_CASE)
    }
}
