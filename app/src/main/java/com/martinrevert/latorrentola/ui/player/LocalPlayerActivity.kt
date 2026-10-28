@file:Suppress("FunctionName")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.martinrevert.latorrentola.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Subtitles
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.martinrevert.latorrentola.ui.components.CastCodecWarningObserver
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.mediarouter.media.MediaRouter
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.martinrevert.latorrentola.utils.MediaControllerHelper
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.PlayerSurface
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import com.martinrevert.latorrentola.ui.components.CastBottomSheet
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.Surface as TvSurface
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack
import com.google.common.util.concurrent.ListenableFuture
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.model.EZTV.EztvTorrent
import com.martinrevert.latorrentola.model.torrent.TorrentRelease
import com.martinrevert.latorrentola.model.torrent.VideoQualityGroup
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.utils.MediaIdHelper
import com.martinrevert.latorrentola.network.DownloadedSubtitle
import com.martinrevert.latorrentola.network.OpenSubtitleRepository
import com.martinrevert.latorrentola.network.OpenSubtitleResult
import com.martinrevert.latorrentola.network.OpenSubtitlesException
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.network.YtsRepository
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer
import com.martinrevert.latorrentola.ui.components.DeleteTorrentDownloadsDialog
import com.martinrevert.latorrentola.ui.components.InsufficientStorageDialog
import com.martinrevert.latorrentola.ui.components.NextEpisodeTorrentDialog
import com.martinrevert.latorrentola.ui.components.WatchProgressBar
import com.martinrevert.latorrentola.ui.components.WatchProgressSlider
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.service.storage.remote.RemoteStorageManager
import com.martinrevert.latorrentola.service.storage.remote.RemoteFileItem
import com.martinrevert.latorrentola.service.storage.TorrentStorageResolver
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import com.martinrevert.latorrentola.utils.MediaMetadataResolver
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.TorrentLaunchResult
import com.martinrevert.latorrentola.utils.mediaMimeType
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/** Track type exposed by the in-player audio and subtitle selectors. */
enum class PlaybackTrackType {
    AUDIO,
    SUBTITLE
}

data class PlaybackTrackOption(
    val label: String,
    val group: TrackGroup?,
    val trackIndex: Int?,
    val castTrackId: Long?,
    val isSelected: Boolean
)

private object SubtitleServerRegistry {
    private val servers = java.util.Collections.synchronizedSet(mutableSetOf<VerifiedTorrentHttpServer>())

    fun retain(server: VerifiedTorrentHttpServer) {
        servers.add(server)
    }

    fun stopAll() {
        synchronized(servers) {
            servers.forEach(VerifiedTorrentHttpServer::stop)
            servers.clear()
        }
    }
}

sealed class PlayerBottomSheetState {
    data class Tracks(
        val type: PlaybackTrackType,
        val title: String,
        val options: List<PlaybackTrackOption>,
        val selectedIndex: Int,
        val onSelected: (PlaybackTrackOption?) -> Unit
    ) : PlayerBottomSheetState()

    data class AdvancedSubtitleStyle(
        val currentFontName: String,
        val currentFontSizeFraction: Float,
        val currentForegroundColor: Int,
        val currentBackgroundColor: Int,
        val currentEdgeType: Int,
        val currentBottomOffset: Float,
        val onLiveChange: (fontName: String, sizeFraction: Float, fgColor: Int, bgColor: Int, edgeType: Int, bottomOffset: Float) -> Unit,
        val onSave: (fontName: String, sizeFraction: Float, fgColor: Int, bgColor: Int, edgeType: Int, bottomOffset: Float) -> Unit,
        val onCancel: () -> Unit
    ) : PlayerBottomSheetState()

    data class SubtitleResults(
        val results: List<OpenSubtitleResult>,
        val currentFileName: String,
        val onSelected: (OpenSubtitleResult) -> Unit
    ) : PlayerBottomSheetState()

    data class Message(
        val title: String,
        val message: String,
        val onDismiss: () -> Unit = {}
    ) : PlayerBottomSheetState()

    data class Progress(
        val title: String,
        val message: String
    ) : PlayerBottomSheetState()
}

val GOOGLE_SUBTITLE_FONTS = listOf(
    "Roboto",
    "Open Sans",
    "Lato",
    "Montserrat",
    "Source Sans 3",
    "Noto Sans",
    "Ubuntu",
    "PT Sans",
    "Inter",
    "Poppins"
)

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("OPT_IN_USAGE_ERROR")
@AndroidEntryPoint
class LocalPlayerActivity : FragmentActivity() {

    @Inject
    lateinit var openSubtitlesRepository: OpenSubtitleRepository

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var torrentDownloadDao: TorrentDownloadDao

    @Inject
    lateinit var userLibraryRepository: UserLibraryRepository

    @Inject
    lateinit var tmdbRepository: TmdbRepository

    @Inject
    lateinit var remoteStorageManager: RemoteStorageManager

    @Inject
    lateinit var ytsRepository: YtsRepository

    @Inject
    lateinit var mediaMetadataResolver: MediaMetadataResolver

    private val tvPlayerViewModel: TvPlayerViewModel by viewModels()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    private val castSubtitleUrls = mutableMapOf<String, String>()
    private var currentMediaItem: MediaItem? = null

    private var localStreamUrl = ""
    private var castStreamUrl = ""
    private var mediaTitle by mutableStateOf("")
    private var torrentInfoHash = ""

    private var currentSeriesId by mutableIntStateOf(0)
    private var currentSeasonNumber by mutableIntStateOf(0)
    private var currentEpisodeNumber by mutableIntStateOf(0)
    private var currentMovieId by mutableIntStateOf(0)

    private var nextEpisodeSeriesId = 0
    private var nextEpisodeSeriesName = ""
    private var nextEpisodeSeasonNumber = 0
    private var nextEpisodeEpisodeNumber = 0
    private var nextEpisodeEpisodeName = ""
    private var hasNextEpisodeInfo = false
    private var nextEpisodeTriggered = false

    private var showNextEpisodeDialog by mutableStateOf(false)
    private var showDeletePromptDialog by mutableStateOf(false)
    private var insufficientStorageData by mutableStateOf<Pair<Long, Long>?>(null)
    private var playerBottomSheetState by mutableStateOf<PlayerBottomSheetState?>(null)
    private var showStatsForNerds by mutableStateOf(false)
    private var customCaptionStyle by mutableStateOf<CaptionStyleCompat?>(null)
    private var customSubtitleSizeFraction by mutableFloatStateOf(0.0533f)
    private var customSubtitleBottomOffset by mutableFloatStateOf(0.08f)
    private var cachedAudioTrackOptions by mutableStateOf<List<PlaybackTrackOption>>(emptyList())

    private var subtitleOperationInProgress by mutableStateOf(false)
    private var subtitleStartupCheckStarted = false

    private var progressTickerJob: Job? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var isCastConnecting by mutableStateOf(false)
    private var connectingDeviceName by mutableStateOf("")
    private var castErrorMessage by mutableStateOf<String?>(null)

    private var pendingMediaUri: Uri? = null
    private var pendingMediaItem: MediaItem? = null
    private var posterUrl by mutableStateOf<String?>(null)
    private var lastKnownPlaybackPositionMs = 0L
    private var lastStreamRecoveryAttemptMillis = 0L

    private fun buildEnrichedMediaMetadata(
        title: String,
        isEpisode: Boolean,
        seasonNumber: Int,
        episodeNumber: Int,
        posterUrl: String?,
        extras: Bundle
    ): MediaMetadata {
        val appBrand = getString(R.string.app_name)
        val subtitleText = if (isEpisode && seasonNumber > 0 && episodeNumber > 0) {
            val sText = getString(R.string.tv_season_number, seasonNumber)
            val eText = getString(R.string.tv_episode_number, episodeNumber)
            "$sText • $eText • $appBrand"
        } else {
            appBrand
        }

        return MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(subtitleText)
            .setSubtitle(subtitleText)
            .setAlbumTitle(appBrand)
            .setExtras(extras)
            .apply {
                if (!posterUrl.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(posterUrl))
                }
            }
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeActivityRef = java.lang.ref.WeakReference(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        handleIntent(intent)

        val fontName = preferenceManager.getSubtitleFontName()
        val sizeFraction = preferenceManager.getSubtitleSizeFraction()
        val fg = preferenceManager.getSubtitleForegroundColor()
        val bg = preferenceManager.getSubtitleBackgroundColor()
        val edge = preferenceManager.getSubtitleEdgeType()
        val offset = preferenceManager.getSubtitleBottomOffset()

        val fontResolver = createFontFamilyResolver(this)
        val fontFamily = FontFamily(Font(googleFont = GoogleFont(fontName), fontProvider = fontProvider))
        val typeface = fontResolver.resolve(fontFamily).value as? android.graphics.Typeface

        customCaptionStyle = CaptionStyleCompat(
            fg,
            bg,
            android.graphics.Color.TRANSPARENT,
            edge,
            android.graphics.Color.BLACK,
            typeface
        )
        customSubtitleSizeFraction = sizeFraction
        customSubtitleBottomOffset = offset

        setupCastSessionListener()
        connectToMediaSession()

        setContent {
            val themeMode = preferenceManager.getTheme()
            val isTv = isTvDevice()

            val content = @Composable {
                val controller = mediaController
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim),
                    contentAlignment = Alignment.Center
                ) {
                    if (controller != null) {
                        var showCastBottomSheet by remember { mutableStateOf(false) }

                        var deviceInfo by remember(controller) { mutableStateOf(controller.deviceInfo) }
                        var playbackState by remember(controller) { mutableIntStateOf(controller.playbackState) }
                        DisposableEffect(controller) {
                            val listener = object : Player.Listener {
                                override fun onDeviceInfoChanged(info: DeviceInfo) {
                                    deviceInfo = info
                                }
                                override fun onPlaybackStateChanged(state: Int) {
                                    playbackState = state
                                }
                            }
                            controller.addListener(listener)
                            onDispose { controller.removeListener(listener) }
                        }

                        val presentationState = rememberPresentationState(controller)
                        val videoModifier = Modifier
                            .fillMaxSize()
                            .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)

                        val isCasting = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

                        LaunchedEffect(isCasting) {
                            if (isCasting) {
                                if (showCastBottomSheet) {
                                    showCastBottomSheet = false
                                }
                                try {
                                    controller.trackSelectionParameters = controller.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverrides()
                                        .build()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed clearing overrides upon cast transition: ${e.message}")
                                }
                            } else {
                                // Returning from Cast back to local ExoPlayer: guarantee preferred audio & subtitles are sustained
                                selectDefaultAudioTrackIfNeeded(controller)
                                selectDefaultSubtitleIfNeeded(controller)
                            }
                        }

                        var surfaceRebindKey by remember { mutableIntStateOf(0) }
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    surfaceRebindKey++
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        if (isCasting) {
                            RemoteCastCanvas(
                                controller = controller,
                                mediaTitle = mediaTitle,
                                posterUrl = posterUrl ?: controller.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
                                seasonNumber = currentSeasonNumber,
                                episodeNumber = currentEpisodeNumber,
                                onDisconnect = {
                                    CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.let { castSession ->
                                        saveCastSessionProgress(castSession)
                                    }
                                    val mediaRouter = MediaRouter.getInstance(this@LocalPlayerActivity)
                                    mediaRouter.selectRoute(mediaRouter.defaultRoute)
                                },
                                onShowCast = { showCastBottomSheet = true },
                                onShowSubtitles = { showSubtitleOptions() },
                                onSearchSubtitles = { searchSubtitles() },
                                onShowSubtitleStyle = { showSubtitleStyleOptions() },
                                onShowAudioTracks = { showAudioOptions() },
                                showStats = showStatsForNerds,
                                onToggleStats = { showStatsForNerds = !showStatsForNerds },
                                onBack = { finish() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            androidx.compose.runtime.key(surfaceRebindKey) {
                                PlayerSurface(
                                    player = controller,
                                    modifier = videoModifier
                                )
                            }

                            if (playbackState == Player.STATE_BUFFERING) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp
                                )
                            }

                            var currentCues by remember(controller) { mutableStateOf<List<Cue>>(controller.currentCues.cues) }
                            DisposableEffect(controller) {
                                val listener = object : Player.Listener {
                                    override fun onCues(cueGroup: CueGroup) {
                                        val normalizedCues = cueGroup.cues.map { cue ->
                                            cue.buildUpon()
                                                .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
                                                .build()
                                        }
                                        currentCues = normalizedCues
                                    }
                                    override fun onTracksChanged(tracks: Tracks) {
                                        selectDefaultSubtitleIfNeeded(controller)
                                        selectDefaultAudioTrackIfNeeded(controller)
                                        prepareSubtitleTrackIfNeeded(tracks)
                                    }
                                }
                                controller.addListener(listener)
                                selectDefaultSubtitleIfNeeded(controller)
                                selectDefaultAudioTrackIfNeeded(controller)
                                onDispose {
                                    controller.removeListener(listener)
                                }
                            }

                            AndroidView(
                                factory = { context ->
                                    val view = SubtitleView(context)
                                    view.apply {
                                        setUserDefaultStyle()
                                        setFractionalTextSize(customSubtitleSizeFraction)
                                        setBottomPaddingFraction(customSubtitleBottomOffset)
                                    }
                                },
                                update = { subtitleView ->
                                    val style = customCaptionStyle
                                    if (style != null) {
                                        subtitleView.setStyle(style)
                                    } else {
                                        subtitleView.setUserDefaultStyle()
                                    }
                                    subtitleView.setFractionalTextSize(customSubtitleSizeFraction)
                                    subtitleView.setBottomPaddingFraction(customSubtitleBottomOffset)
                                    subtitleView.setCues(currentCues)
                                },
                                modifier = videoModifier
                            )

                            PlayerControlsOverlay(
                                controller = controller,
                                mediaTitle = mediaTitle,
                                showStats = showStatsForNerds,
                                onToggleStats = { showStatsForNerds = !showStatsForNerds },
                                onShowSubtitles = { showSubtitleOptions() },
                                onSearchSubtitles = { searchSubtitles() },
                                onShowSubtitleStyle = { showSubtitleStyleOptions() },
                                onShowAudioTracks = { showAudioOptions() },
                                onShowCast = { showCastBottomSheet = true }
                            )
                        }

                        AnimatedVisibility(
                            visible = isCastConnecting,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 32.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                                tonalElevation = 6.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.cast_connecting_banner, connectingDeviceName.ifBlank { "Cast" }),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        castErrorMessage?.let { errorMsg ->
                            LaunchedEffect(errorMsg) {
                                delay(4.seconds)
                                castErrorMessage = null
                            }
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 2 },
                                exit = fadeOut() + slideOutVertically { it / 2 },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                                    tonalElevation = 6.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = errorMsg,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (showStatsForNerds) {
                            StatsForNerdsOverlay(
                                controller = controller,
                                torrentInfoHash = torrentInfoHash,
                                torrentDownloadDao = torrentDownloadDao,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 56.dp)
                            )
                        }

                        CastCodecWarningObserver(
                            player = controller,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 72.dp)
                        )

                        if (showCastBottomSheet) {
                            val sheetSubtitle = when {
                                currentSeasonNumber > 0 && currentEpisodeNumber > 0 ->
                                    "T$currentSeasonNumber:E$currentEpisodeNumber"
                                else -> ""
                            }
                            CastBottomSheet(
                                onDismissRequest = { showCastBottomSheet = false },
                                mediaTitle = mediaTitle,
                                mediaSubtitle = sheetSubtitle,
                                posterUrl = posterUrl ?: controller.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
                                player = controller
                            )
                        }

                        // Screen Awake Power Management
                        DisposableEffect(controller) {
                            val listener = object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    updateScreenAwake(controller)
                                }
                                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                                    updateScreenAwake(controller)
                                }
                            }
                            controller.addListener(listener)
                            updateScreenAwake(controller)
                            onDispose {
                                controller.removeListener(listener)
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    if (showDeletePromptDialog) {
                        DeleteTorrentDownloadsDialog(
                            mediaTitle = mediaTitle,
                            onConfirmDelete = {
                                val hashToDelete = torrentInfoHash
                                showDeletePromptDialog = false
                                try {
                                    mediaController?.stop()
                                    mediaController?.clearMediaItems()
                                    SubtitleServerRegistry.stopAll()
                                } catch (_: Exception) {}
                                lifecycleScope.launch {
                                    if (hashToDelete.isNotBlank()) {
                                        TorrentDownloadService.deleteTorrentSynchronously(
                                            this@LocalPlayerActivity,
                                            hashToDelete,
                                            torrentDownloadDao
                                        )
                                    }
                                    proceedAfterPlaybackEnded()
                                }
                            },
                            onKeep = {
                                showDeletePromptDialog = false
                                proceedAfterPlaybackEnded()
                            },
                            onDismiss = {
                                showDeletePromptDialog = false
                                proceedAfterPlaybackEnded()
                            }
                        )
                    }

                    if (showNextEpisodeDialog) {
                        val nextData by tvPlayerViewModel.nextEpisodeData.collectAsState()
                        val releases by tvPlayerViewModel.nextEpisodeReleases.collectAsState()
                        val evaluation by tvPlayerViewModel.nextEpisodeEvaluation.collectAsState()
                        val isLoading by tvPlayerViewModel.isLoading.collectAsState()
                        val error by tvPlayerViewModel.error.collectAsState()

                        val targetSeriesId = nextData?.seriesId?.takeIf { it > 0 } ?: nextEpisodeSeriesId.takeIf { it > 0 } ?: currentSeriesId
                        val targetSeriesName = nextData?.seriesName?.takeIf { it.isNotBlank() } ?: nextEpisodeSeriesName.takeIf { it.isNotBlank() } ?: mediaTitle
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
                            evaluation = evaluation,
                            onTorrentSelected = { torrent ->
                                lifecycleScope.launch {
                                    val result = tvPlayerViewModel.downloadEpisode(
                                        this@LocalPlayerActivity,
                                        torrent,
                                        targetSeriesId,
                                        targetSeriesName,
                                        targetSeason,
                                        targetEpisode,
                                        targetEpName
                                    )
                                    if (result == TorrentLaunchResult.Started) {
                                        showNextEpisodeDialog = false
                                    } else if (result is TorrentLaunchResult.InsufficientStorage) {
                                        insufficientStorageData = Pair(result.availableBytes, result.requiredBytes)
                                        showNextEpisodeDialog = false
                                    } else if (result == TorrentLaunchResult.NoExternalClient || result == TorrentLaunchResult.CastUnavailable) {
                                        showNextEpisodeDialog = false
                                    }
                                }
                            },
                            onDismiss = {
                                showNextEpisodeDialog = false
                                finish()
                            }
                        )
                    }

                    insufficientStorageData?.let { (available, required) ->
                        InsufficientStorageDialog(
                            availableBytes = available,
                            requiredBytes = required,
                            onDismiss = { insufficientStorageData = null },
                            themeMode = themeMode
                        )
                    }

                    val state = playerBottomSheetState
                    if (state != null) {
                        PlayerBottomSheetContent(
                            state = state,
                            onDismiss = { playerBottomSheetState = null },
                            themeMode = themeMode
                        )
                    }
                }
            }

            if (isTv) {
                TvLaTorrentolaTheme(themeMode = themeMode) { content() }
            } else {
                LaTorrentolaTheme(themeMode = themeMode) { content() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNextEpisodeDialog = false
        handleIntent(intent)
        val controller = mediaController
        if (controller != null) {
            setupControllerMedia(controller)
        }
    }

    private fun handleIntent(intent: Intent) {
        val serverJson = intent.getStringExtra(EXTRA_REMOTE_SERVER_JSON)
        if (!serverJson.isNullOrBlank()) {
            try {
                val serverConfig = com.google.gson.Gson().fromJson(
                    serverJson,
                    com.martinrevert.latorrentola.service.storage.remote.RemoteServerConfig::class.java
                )
                if (serverConfig != null) {
                    remoteStorageManager.registerServer(serverConfig)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize remote server JSON: ${e.message}")
            }
        }

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

        pendingMediaUri = mediaUri

        if (castStreamUrl.isBlank() && file != null && file.isFile && file.canRead()) {
            try {
                val server = VerifiedTorrentHttpServer(file, file.length()) { _, _ -> true }
                server.startServer()
                SubtitleServerRegistry.retain(server)
                castStreamUrl = server.lanUrl()
                Log.i(TAG, "Started VerifiedTorrentHttpServer for local file Cast: $castStreamUrl")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start VerifiedTorrentHttpServer for local file: ${e.message}")
            }
        } else if (castStreamUrl.isBlank() && mediaUri?.scheme == com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.SCHEME_REMOTE) {
            val serverId = intent.getStringExtra(EXTRA_REMOTE_SERVER_ID).orEmpty().ifBlank { mediaUri.host.orEmpty() }
            val remotePath = intent.getStringExtra(EXTRA_REMOTE_FILE_PATH).orEmpty().ifBlank { mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_PATH).orEmpty() }
            if (serverId.isNotBlank() && remotePath.isNotBlank()) {
                var serverConfig = remoteStorageManager.getServer(serverId)
                if (serverConfig == null) {
                    val typeStr = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_TYPE)
                    val host = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_HOST)
                    if (!typeStr.isNullOrBlank() && !host.isNullOrBlank()) {
                        val type = try {
                            com.martinrevert.latorrentola.service.storage.remote.RemoteServerType.valueOf(typeStr)
                        } catch (_: Exception) {
                            com.martinrevert.latorrentola.service.storage.remote.RemoteServerType.NFS
                        }
                        val port = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_PORT)?.toIntOrNull()
                            ?: if (type == com.martinrevert.latorrentola.service.storage.remote.RemoteServerType.SMB) 445 else 2049
                        val share = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_SHARE).orEmpty()
                        val user = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_USER).takeUnless { it.isNullOrBlank() }
                        val pass = mediaUri.getQueryParameter(com.martinrevert.latorrentola.service.storage.remote.RemoteMediaDataSource.PARAM_PASS).takeUnless { it.isNullOrBlank() }
                        serverConfig = com.martinrevert.latorrentola.service.storage.remote.RemoteServerConfig(
                            id = serverId,
                            name = serverId,
                            type = type,
                            host = host,
                            port = port,
                            shareOrExport = share,
                            username = user,
                            password = pass
                        )
                        remoteStorageManager.registerServer(serverConfig)
                    }
                }
                if (serverConfig != null) {
                    try {
                        val fileItem = com.martinrevert.latorrentola.service.storage.remote.RemoteFileItem(
                            path = remotePath,
                            name = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { remotePath.substringAfterLast('/') },
                            isDirectory = false,
                            sizeBytes = 0L,
                            lastModified = 0L,
                            serverConfigId = serverId
                        )
                        val server = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            remoteStorageManager.startRemoteStreamServer(serverConfig, fileItem)
                        }
                        castStreamUrl = server.lanUrl()
                        Log.i(TAG, "Started RemoteMediaHttpServer for remote Cast: $castStreamUrl")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to start RemoteMediaHttpServer for remote Cast: ${e.message}")
                    }
                }
            }
        }

        val titleExtra = intent.getStringExtra(EXTRA_TITLE)
            ?: intent.getStringExtra(TorrentDownloadService.EXTRA_TITLE)
        if (!titleExtra.isNullOrBlank()) {
            mediaTitle = titleExtra
        }
        val rawInfoHash = intent.getStringExtra(EXTRA_INFO_HASH)
            ?: intent.getStringExtra(TorrentDownloadService.EXTRA_INFO_HASH)
            ?: ""
        torrentInfoHash = rawInfoHash.ifBlank {
            extractInfoHash(
                localStreamUrl,
                castStreamUrl,
                file?.absolutePath,
                mediaUri?.toString()
            )
        }
        val posterExtra = intent.getStringExtra(EXTRA_POSTER_URL)
            ?: intent.getStringExtra(TorrentDownloadService.EXTRA_POSTER_URL)
        if (!posterExtra.isNullOrBlank()) {
            posterUrl = posterExtra
        }
        val sId = intent.getIntExtra(EXTRA_SERIES_ID, 0)
            .takeIf { it > 0 } ?: intent.getIntExtra(TorrentDownloadService.EXTRA_SERIES_ID, 0)
        if (sId > 0) currentSeriesId = sId
        val sNum = intent.getIntExtra(EXTRA_SEASON_NUMBER, 0)
            .takeIf { it > 0 } ?: intent.getIntExtra(TorrentDownloadService.EXTRA_SEASON_NUMBER, 0)
        if (sNum > 0) currentSeasonNumber = sNum
        val eNum = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 0)
            .takeIf { it > 0 } ?: intent.getIntExtra(TorrentDownloadService.EXTRA_EPISODE_NUMBER, 0)
        if (eNum > 0) currentEpisodeNumber = eNum
        val mId = intent.getIntExtra(EXTRA_MOVIE_ID, 0)
            .takeIf { it > 0 } ?: intent.getIntExtra(TorrentDownloadService.EXTRA_MOVIE_ID, 0)
        if (mId > 0) currentMovieId = mId

        if (torrentInfoHash.isNotBlank()) {
            try {
                val dbRecord = runBlocking(Dispatchers.IO) { torrentDownloadDao.get(torrentInfoHash) }
                if (dbRecord != null) {
                    if (currentMovieId <= 0 && (dbRecord.movieId ?: 0) > 0) {
                        currentMovieId = dbRecord.movieId ?: 0
                    }
                    if (currentSeriesId <= 0 && (dbRecord.seriesId ?: 0) > 0) {
                        currentSeriesId = dbRecord.seriesId ?: 0
                    }
                    if (currentSeasonNumber <= 0 && (dbRecord.seasonNumber ?: 0) > 0) {
                        currentSeasonNumber = dbRecord.seasonNumber ?: 0
                    }
                    if (currentEpisodeNumber <= 0 && (dbRecord.episodeNumber ?: 0) > 0) {
                        currentEpisodeNumber = dbRecord.episodeNumber ?: 0
                    }
                    if (posterUrl.isNullOrBlank() && !dbRecord.posterUrl.isNullOrBlank()) {
                        posterUrl = dbRecord.posterUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recover series/movie metadata from DB: ${e.message}")
            }
        }

        if (mediaUri != null) {

            if (currentSeasonNumber <= 0 || currentEpisodeNumber <= 0) {
                val (detectedSeason, detectedEpisode) = extractSeasonAndEpisode(mediaTitle, file?.name, mediaUri.toString())
                if (detectedSeason > 0 && detectedEpisode > 0) {
                    if (currentSeasonNumber <= 0) currentSeasonNumber = detectedSeason
                    if (currentEpisodeNumber <= 0) currentEpisodeNumber = detectedEpisode
                }
            }

            val nextEpisodeInfo = intent.getStringArrayListExtra(EXTRA_NEXT_EPISODE_INFO)
            if (nextEpisodeInfo != null && nextEpisodeInfo.size >= 5) {
                try {
                    nextEpisodeSeriesId = nextEpisodeInfo[0].toInt()
                    nextEpisodeSeriesName = nextEpisodeInfo[1]
                    nextEpisodeSeasonNumber = nextEpisodeInfo[2].toInt()
                    nextEpisodeEpisodeNumber = nextEpisodeInfo[3].toInt()
                    nextEpisodeEpisodeName = nextEpisodeInfo[4]
                    hasNextEpisodeInfo = true
                } catch (e: NumberFormatException) {
                    hasNextEpisodeInfo = false
                }
            }

            if (currentSeriesId > 0 && !hasNextEpisodeInfo) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val info = tvPlayerViewModel.getNextEpisodeInfo(
                            seriesId = currentSeriesId,
                            seriesName = mediaTitle,
                            seasonNumber = currentSeasonNumber,
                            episodeNumber = currentEpisodeNumber
                        )
                        if (info != null && info.size >= 5) {
                            nextEpisodeSeriesId = info[0].toIntOrNull() ?: currentSeriesId
                            nextEpisodeSeriesName = info[1]
                            nextEpisodeSeasonNumber = info[2].toIntOrNull() ?: currentSeasonNumber
                            nextEpisodeEpisodeNumber = info[3].toIntOrNull() ?: (currentEpisodeNumber + 1)
                            nextEpisodeEpisodeName = info[4]
                            hasNextEpisodeInfo = true
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to resolve next episode info asynchronously: ${e.message}")
                    }
                }
            } else if (currentSeriesId <= 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0 && !hasNextEpisodeInfo) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val cleanTitle = extractCleanSearchTitles(mediaTitle).firstOrNull() ?: mediaTitle
                        val searchResult = tmdbRepository.searchTvSeries(cleanTitle).results?.firstOrNull()
                        if (searchResult != null && searchResult.id > 0) {
                            currentSeriesId = searchResult.id
                            val info = tvPlayerViewModel.getNextEpisodeInfo(
                                seriesId = currentSeriesId,
                                seriesName = searchResult.name.orEmpty(),
                                seasonNumber = currentSeasonNumber,
                                episodeNumber = currentEpisodeNumber
                            )
                            if (info != null && info.size >= 5) {
                                nextEpisodeSeriesId = info[0].toIntOrNull() ?: currentSeriesId
                                nextEpisodeSeriesName = info[1]
                                nextEpisodeSeasonNumber = info[2].toIntOrNull() ?: currentSeasonNumber
                                nextEpisodeEpisodeNumber = info[3].toIntOrNull() ?: (currentEpisodeNumber + 1)
                                nextEpisodeEpisodeName = info[4]
                                hasNextEpisodeInfo = true
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to resolve series and next episode info via TMDB: ${e.message}")
                    }
                }
            }

            val isEpisode = currentSeriesId > 0
            val mediaId = if (isEpisode) {
                MediaIdHelper.forEpisode(
                    seriesId = currentSeriesId,
                    seasonNumber = currentSeasonNumber,
                    episodeNumber = currentEpisodeNumber,
                    title = mediaTitle
                )
            } else {
                MediaIdHelper.forMovie(
                    movieId = currentMovieId,
                    title = mediaTitle
                )
            }

            val itemBundle = Bundle().apply {
                putBoolean(PlaybackService.EXTRA_IS_EPISODE, isEpisode)
                if (castStreamUrl.isNotBlank()) {
                    putString(PlaybackService.EXTRA_CAST_URL, castStreamUrl)
                }
                if (localStreamUrl.isNotBlank()) {
                    putString(EXTRA_STREAM_URL, localStreamUrl)
                }
                if (file != null && file.exists()) {
                    putString(EXTRA_FILE_PATH, file.absolutePath)
                }
                if (torrentInfoHash.isNotBlank()) {
                    putString(EXTRA_INFO_HASH, torrentInfoHash)
                }
                if (currentMovieId > 0) {
                    putInt(EXTRA_MOVIE_ID, currentMovieId)
                }
                if (currentSeriesId > 0) {
                    putInt(EXTRA_SERIES_ID, currentSeriesId)
                }
                if (currentSeasonNumber > 0) {
                    putInt(EXTRA_SEASON_NUMBER, currentSeasonNumber)
                }
                if (currentEpisodeNumber > 0) {
                    putInt(EXTRA_EPISODE_NUMBER, currentEpisodeNumber)
                }
                val inferredMime = intent.getStringExtra(EXTRA_MIME_TYPE) ?: file?.mediaMimeType()
                if (!inferredMime.isNullOrBlank()) {
                    putString(EXTRA_MIME_TYPE, inferredMime)
                }
                if (!posterUrl.isNullOrBlank()) {
                    putString(EXTRA_POSTER_URL, posterUrl)
                }
            }

            val inferredMimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
                ?: file?.mediaMimeType()
                ?: intent.getStringExtra(EXTRA_REMOTE_FILE_PATH)?.mediaMimeType()
                ?: intent.getStringExtra(EXTRA_TITLE)?.mediaMimeType()

            val mediaMetadata = buildEnrichedMediaMetadata(
                title = mediaTitle,
                isEpisode = isEpisode,
                seasonNumber = currentSeasonNumber,
                episodeNumber = currentEpisodeNumber,
                posterUrl = posterUrl,
                extras = itemBundle
            )

            pendingMediaItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(mediaUri)
                .setMimeType(inferredMimeType)
                .setMediaMetadata(mediaMetadata)
                .build()
            currentMediaItem = pendingMediaItem

            if (posterUrl.isNullOrBlank()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val resolved = resolvePosterUrl(
                        movieId = currentMovieId,
                        seriesId = currentSeriesId,
                        seasonNumber = currentSeasonNumber,
                        episodeNumber = currentEpisodeNumber,
                        title = mediaTitle,
                        infoHash = torrentInfoHash
                    )
                    if (!resolved.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            posterUrl = resolved
                            currentMediaItem?.let { item ->
                                val updatedMetadata = item.mediaMetadata.buildUpon()
                                    .setArtworkUri(Uri.parse(resolved))
                                    .build()
                                val updatedItem = item.buildUpon().setMediaMetadata(updatedMetadata).build()
                                currentMediaItem = updatedItem
                                pendingMediaItem = updatedItem
                            }
                        }
                    }
                }
            }
        }
        extractAudioTracksFromFile(intent.getStringExtra(EXTRA_FILE_PATH))
    }

    private suspend fun resolvePosterUrl(
        movieId: Int,
        seriesId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String,
        infoHash: String
    ): String? {
        try {
            if (infoHash.isNotBlank()) {
                val dbRecord = torrentDownloadDao.get(infoHash)
                if (dbRecord != null) {
                    if (!dbRecord.posterUrl.isNullOrBlank()) return dbRecord.posterUrl
                    val mId = dbRecord.movieId ?: 0
                    if (mId > 0) {
                        try {
                            val summary = ytsRepository.getMovieSummary(mId)
                            val movie = summary.data?.movie
                            val poster = movie?.largeCoverImage ?: movie?.mediumCoverImage
                            if (!poster.isNullOrBlank()) return poster
                        } catch (_: Exception) {}
                    }
                    val sId = dbRecord.seriesId ?: 0
                    if (sId > 0) {
                        try {
                            val seriesSummary = tmdbRepository.getTvDetails(sId)
                            val poster = seriesSummary.fullPosterUrl
                            if (!poster.isNullOrBlank()) return poster
                        } catch (_: Exception) {}
                    }
                }
            }

            if (movieId > 0) {
                try {
                    val summary = ytsRepository.getMovieSummary(movieId)
                    val movie = summary.data?.movie
                    val poster = movie?.largeCoverImage ?: movie?.mediumCoverImage
                    if (!poster.isNullOrBlank()) return poster
                } catch (_: Exception) {}
            }

            if (seriesId > 0) {
                try {
                    val seriesSummary = tmdbRepository.getTvDetails(seriesId)
                    val poster = seriesSummary.fullPosterUrl
                    if (!poster.isNullOrBlank()) return poster
                } catch (_: Exception) {}
            }

            val cleanTitles = extractCleanSearchTitles(title)
            val cleanTitle = cleanTitles.firstOrNull() ?: title
            if (cleanTitle.isNotBlank()) {
                if (seasonNumber > 0 || episodeNumber > 0) {
                    try {
                        val searchResult = tmdbRepository.searchTvSeries(cleanTitle).results?.firstOrNull()
                        if (searchResult != null) {
                            val poster = searchResult.fullPosterUrl
                            if (!poster.isNullOrBlank()) return poster
                        }
                    } catch (_: Exception) {}
                } else {
                    try {
                        val movieSearch = ytsRepository.searchMovies(cleanTitle, 1)
                        val movie = movieSearch.data?.movies?.firstOrNull()
                        val poster = movie?.largeCoverImage ?: movie?.mediumCoverImage
                        if (!poster.isNullOrBlank()) return poster
                    } catch (_: Exception) {}

                    try {
                        val tvSearch = tmdbRepository.searchTvSeries(cleanTitle).results?.firstOrNull()
                        val tvPoster = tvSearch?.fullPosterUrl
                        if (!tvPoster.isNullOrBlank()) return tvPoster
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to resolve poster URL asynchronously: ${e.message}")
        }
        return null
    }

    private fun connectToMediaSession() {
        controllerFuture = MediaControllerHelper.buildControllerAsync(this)
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                if (controller != null) {
                    setupControllerMedia(controller)
                } else if (pendingMediaUri == null && !intent.getBooleanExtra(EXTRA_FROM_PIP, false)) {
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
                if (pendingMediaUri == null && !intent.getBooleanExtra(EXTRA_FROM_PIP, false)) {
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupControllerMedia(controller: MediaController) {
        lifecycleScope.launch {
            val activeItem = controller.currentMediaItem
            val newItem = pendingMediaItem
            val isFromPip = intent.getBooleanExtra(EXTRA_FROM_PIP, false)
            Log.i(TAG, "setupControllerMedia: activeItem=${activeItem?.mediaId}, newItem=${newItem?.mediaId}, isFromPip=$isFromPip, isPlaying=${controller.isPlaying}, playWhenReady=${controller.playWhenReady}, state=${controller.playbackState}")

            // Fast-path for PIP expansion: zero interruption to active PlaybackService playback
            if (isFromPip && activeItem != null) {
                currentMediaItem = activeItem
                val titleFromMetadata = activeItem.mediaMetadata.title?.toString()
                if (!titleFromMetadata.isNullOrBlank() && mediaTitle.isBlank()) {
                    mediaTitle = titleFromMetadata
                }
                val artworkUri = activeItem.mediaMetadata.artworkUri?.toString()
                if (!artworkUri.isNullOrBlank() && posterUrl.isNullOrBlank()) {
                    posterUrl = artworkUri
                }
                if (currentSeasonNumber <= 0 || currentEpisodeNumber <= 0) {
                    val (s, e) = extractSeasonAndEpisode(mediaTitle, activeItem.mediaId)
                    if (currentSeasonNumber <= 0 && s > 0) currentSeasonNumber = s
                    if (currentEpisodeNumber <= 0 && e > 0) currentEpisodeNumber = e
                }
                if (posterUrl.isNullOrBlank() && torrentInfoHash.isNotBlank()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        torrentDownloadDao.get(torrentInfoHash)?.let { dbRecord ->
                            if (!dbRecord.posterUrl.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    posterUrl = dbRecord.posterUrl
                                }
                            }
                            if (currentSeasonNumber <= 0 && (dbRecord.seasonNumber ?: 0) > 0) {
                                withContext(Dispatchers.Main) {
                                    currentSeasonNumber = dbRecord.seasonNumber ?: 0
                                }
                            }
                            if (currentEpisodeNumber <= 0 && (dbRecord.episodeNumber ?: 0) > 0) {
                                withContext(Dispatchers.Main) {
                                    currentEpisodeNumber = dbRecord.episodeNumber ?: 0
                                }
                            }
                        }
                    }
                }
                startProgressTicker(controller)
                updateCachedAudioTracks(controller.currentTracks)
                prepareSubtitleTrackIfNeeded(controller.currentTracks)
                setupPlaybackCompletionListener(controller)

                controller.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        selectDefaultSubtitleIfNeeded(controller)
                        selectDefaultAudioTrackIfNeeded(controller)
                        prepareSubtitleTrackIfNeeded(tracks)
                        updateCachedAudioTracks(tracks)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startProgressTicker(controller)
                        } else {
                            saveCurrentProgress(controller)
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            saveCurrentProgress(controller)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error encountered during playback: ${error.message}", error)
                    }
                })
                return@launch
            }

            val activeUri = activeItem?.localConfiguration?.uri
            val newUri = newItem?.localConfiguration?.uri
            val activeExtraUrl = activeItem?.mediaMetadata?.extras?.getString(EXTRA_STREAM_URL)
            val newExtraUrl = newItem?.mediaMetadata?.extras?.getString(EXTRA_STREAM_URL)
            val uriChanged = (activeUri != null && newUri != null && activeUri != newUri) ||
                (!activeExtraUrl.isNullOrBlank() && !newExtraUrl.isNullOrBlank() && activeExtraUrl != newExtraUrl)

            val isNewMedia = when {
                activeItem == null -> true
                newItem == null -> false
                activeItem.mediaId == newItem.mediaId -> false
                activeUri != null && newUri != null && activeUri == newUri -> false
                !activeExtraUrl.isNullOrBlank() && !newExtraUrl.isNullOrBlank() && activeExtraUrl == newExtraUrl -> false
                else -> true
            }
            if (isNewMedia) {
                lastKnownPlaybackPositionMs = 0L
                nextEpisodeTriggered = false
                if (activeItem != null) {
                    saveCurrentProgress(controller)
                    controller.stop()
                    controller.clearMediaItems()
                }
            }

            var itemToConfigure = newItem ?: activeItem
            if (itemToConfigure != null && (currentMovieId <= 0 && currentSeriesId <= 0)) {
                try {
                    val resolved = withContext(Dispatchers.IO) {
                        mediaMetadataResolver.resolve(
                            rawTitle = mediaTitle,
                            filePath = intent.getStringExtra(EXTRA_FILE_PATH),
                            streamUrl = localStreamUrl.ifBlank { castStreamUrl },
                            infoHash = torrentInfoHash,
                            existingMovieId = currentMovieId,
                            existingSeriesId = currentSeriesId,
                            existingSeasonNumber = currentSeasonNumber,
                            existingEpisodeNumber = currentEpisodeNumber,
                            existingPosterUrl = posterUrl
                        )
                    }
                    currentMovieId = resolved.movieId
                    currentSeriesId = resolved.seriesId
                    currentSeasonNumber = resolved.seasonNumber
                    currentEpisodeNumber = resolved.episodeNumber
                    if (posterUrl.isNullOrBlank() && !resolved.posterUrl.isNullOrBlank()) {
                        posterUrl = resolved.posterUrl
                    }
                    if (resolved.title.isNotBlank()) {
                        mediaTitle = resolved.title
                    }

                    val itemBundle = Bundle().apply {
                        putBoolean(PlaybackService.EXTRA_IS_EPISODE, resolved.isEpisode)
                        if (castStreamUrl.isNotBlank()) {
                            putString(PlaybackService.EXTRA_CAST_URL, castStreamUrl)
                        }
                        if (localStreamUrl.isNotBlank()) {
                            putString(EXTRA_STREAM_URL, localStreamUrl)
                        }
                        val localFilePath = intent.getStringExtra(EXTRA_FILE_PATH)
                            ?: itemToConfigure.mediaMetadata.extras?.getString(EXTRA_FILE_PATH)
                        val localFile = localFilePath?.let(::File)
                        if (localFile != null && localFile.exists()) {
                            putString(EXTRA_FILE_PATH, localFile.absolutePath)
                        }
                        if (torrentInfoHash.isNotBlank()) {
                            putString(EXTRA_INFO_HASH, torrentInfoHash)
                        }
                        if (resolved.movieId > 0) {
                            putInt(EXTRA_MOVIE_ID, resolved.movieId)
                        }
                        if (resolved.seriesId > 0) {
                            putInt(EXTRA_SERIES_ID, resolved.seriesId)
                        }
                        if (resolved.seasonNumber > 0) {
                            putInt(EXTRA_SEASON_NUMBER, resolved.seasonNumber)
                        }
                        if (resolved.episodeNumber > 0) {
                            putInt(EXTRA_EPISODE_NUMBER, resolved.episodeNumber)
                        }
                        val inferredMime = intent.getStringExtra(EXTRA_MIME_TYPE)
                            ?: localFile?.mediaMimeType()
                            ?: itemToConfigure.localConfiguration?.mimeType
                        if (!inferredMime.isNullOrBlank()) {
                            putString(EXTRA_MIME_TYPE, inferredMime)
                        }
                        if (!posterUrl.isNullOrBlank()) {
                            putString(EXTRA_POSTER_URL, posterUrl)
                        }
                    }
                    val mediaMetadata = buildEnrichedMediaMetadata(
                        title = mediaTitle,
                        isEpisode = resolved.isEpisode,
                        seasonNumber = resolved.seasonNumber,
                        episodeNumber = resolved.episodeNumber,
                        posterUrl = posterUrl,
                        extras = itemBundle
                    )

                    itemToConfigure = itemToConfigure.buildUpon()
                        .setMediaId(resolved.mediaId)
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    pendingMediaItem = itemToConfigure
                    currentMediaItem = itemToConfigure
                    Log.i(TAG, "Resolved canonical media ID for playback: ${resolved.mediaId}")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed resolving canonical media info: ${e.message}")
                }
            }

            val needsReconfiguration = itemToConfigure != null && (
                isNewMedia ||
                uriChanged ||
                controller.playbackState == Player.STATE_IDLE ||
                controller.playbackState == Player.STATE_ENDED
            )

            if (needsReconfiguration) {
                val savedProgress = userLibraryRepository.getPlaybackProgress(itemToConfigure.mediaId)
                val currentPos = if (isNewMedia) {
                    0L
                } else {
                    lastKnownPlaybackPositionMs.takeIf { it > 5000L }
                        ?: controller.currentPosition.takeIf { it > 5000L }
                        ?: 0L
                }
                val dbPos = if (savedProgress != null && savedProgress.positionMs > 5000L && !savedProgress.isCompleted) {
                    savedProgress.positionMs
                } else {
                    0L
                }
                val startPosition = maxOf(currentPos, dbPos)
                if (startPosition > 0L) {
                    Log.i(TAG, "Restoring playback position: ${startPosition}ms for ${itemToConfigure.mediaId}")
                }

                val deviceLang = Locale.getDefault().language
                val defaultTrackParams = controller.trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("en", "eng")
                    .setPreferredTextLanguage(deviceLang)
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                controller.trackSelectionParameters = defaultTrackParams

                var itemToPlay = itemToConfigure
                if (itemToPlay.localConfiguration?.subtitleConfigurations.isNullOrEmpty() && torrentInfoHash.isNotBlank()) {
                    try {
                        val cachedSubtitle = openSubtitlesRepository.findLatestDownloaded(torrentInfoHash)
                        if (cachedSubtitle != null) {
                            val localUri = Uri.fromFile(cachedSubtitle.file)
                            val displayLabel = cachedSubtitle.label.ifBlank { "OpenSubtitles (${cachedSubtitle.language.uppercase(Locale.ROOT)})" }
                            val subConfig = MediaItem.SubtitleConfiguration.Builder(localUri)
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(cachedSubtitle.language)
                                .setLabel(displayLabel)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .setRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                                .build()

                            itemToPlay = itemToPlay.buildUpon()
                                .setSubtitleConfigurations(listOf(subConfig))
                                .build()
                            currentMediaItem = itemToPlay
                            Log.i(TAG, "Attached pre-downloaded subtitle on startup: ${cachedSubtitle.file.name}")
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to check pre-downloaded subtitle: ${e.message}")
                    }
                }

                controller.setMediaItem(itemToPlay, startPosition)
                controller.prepare()
                controller.playWhenReady = true
                startProgressTicker(controller)
            } else if (activeItem != null) {
                currentMediaItem = activeItem
                val titleFromMetadata = activeItem.mediaMetadata.title?.toString()
                if (!titleFromMetadata.isNullOrBlank() && mediaTitle.isBlank()) {
                    mediaTitle = titleFromMetadata
                }
                val artworkUri = activeItem.mediaMetadata.artworkUri?.toString()
                if (!artworkUri.isNullOrBlank() && posterUrl.isNullOrBlank()) {
                    posterUrl = artworkUri
                }
                if (currentSeasonNumber <= 0 || currentEpisodeNumber <= 0) {
                    val (s, e) = extractSeasonAndEpisode(mediaTitle, activeItem.mediaId)
                    if (currentSeasonNumber <= 0 && s > 0) currentSeasonNumber = s
                    if (currentEpisodeNumber <= 0 && e > 0) currentEpisodeNumber = e
                }
                if (posterUrl.isNullOrBlank() && torrentInfoHash.isNotBlank()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        torrentDownloadDao.get(torrentInfoHash)?.let { dbRecord ->
                            if (!dbRecord.posterUrl.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    posterUrl = dbRecord.posterUrl
                                }
                            }
                            if (currentSeasonNumber <= 0 && (dbRecord.seasonNumber ?: 0) > 0) {
                                withContext(Dispatchers.Main) {
                                    currentSeasonNumber = dbRecord.seasonNumber ?: 0
                                }
                            }
                            if (currentEpisodeNumber <= 0 && (dbRecord.episodeNumber ?: 0) > 0) {
                                withContext(Dispatchers.Main) {
                                    currentEpisodeNumber = dbRecord.episodeNumber ?: 0
                                }
                            }
                        }
                    }
                }
                startProgressTicker(controller)
                updateCachedAudioTracks(controller.currentTracks)
                prepareSubtitleTrackIfNeeded(controller.currentTracks)
            } else if (pendingMediaUri == null) {
                finish()
                return@launch
            }

            setupPlaybackCompletionListener(controller)

            controller.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    selectDefaultSubtitleIfNeeded(controller)
                    selectDefaultAudioTrackIfNeeded(controller)
                    prepareSubtitleTrackIfNeeded(tracks)
                    updateCachedAudioTracks(tracks)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) {
                        saveCurrentProgress(controller)
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        prepareSubtitleTrackIfNeeded(controller.currentTracks)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error encountered: ${error.errorCodeName} (${error.errorCode}): ${error.message}", error)
                    if (controller.currentPosition > 5000L) {
                        lastKnownPlaybackPositionMs = controller.currentPosition
                    }
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) {

                        val isLocalStream = localStreamUrl.contains("127.0.0.1") || localStreamUrl.contains("localhost")
                        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED && isLocalStream && torrentInfoHash.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            if (now - lastStreamRecoveryAttemptMillis > 3000L) {
                                lastStreamRecoveryAttemptMillis = now
                                Log.w(TAG, "Local stream connection failed for hash $torrentInfoHash, requesting stream server restart")
                                val playIntent = Intent(this@LocalPlayerActivity, TorrentDownloadService::class.java).apply {
                                    action = TorrentDownloadService.ACTION_PLAY_STREAM
                                    putExtra(TorrentDownloadService.EXTRA_INFO_HASH, torrentInfoHash)
                                }
                                startService(playIntent)
                            }
                            return
                        }

                        lifecycleScope.launch {
                            delay(1500.milliseconds)
                            if (controller.playbackState == Player.STATE_IDLE) {
                                Log.i(TAG, "Attempting auto-recovery for transient playback error")
                                controller.prepare()
                                controller.playWhenReady = true
                            }
                        }
                    }
                }
            })

            selectDefaultSubtitleIfNeeded(controller)
            selectDefaultAudioTrackIfNeeded(controller)
            prepareSubtitleTrackIfNeeded(controller.currentTracks)
        }
    }

    private fun updateScreenAwake(player: Player) {
        val isLocal = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL
        if (player.isPlaying && isLocal) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupPlaybackCompletionListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.isPlaying) {
                    nextEpisodeTriggered = false
                } else if (playbackState == Player.STATE_ENDED && !nextEpisodeTriggered) {
                    nextEpisodeTriggered = true
                    handlePlaybackEnded()
                }
            }
        })
    }

    private fun selectDefaultSubtitleIfNeeded(controller: Player) {
        if (controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        val textGroups = controller.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) return

        val anySelected = textGroups.any { group ->
            (0 until group.length).any { group.isTrackSelected(it) }
        }

        if (!anySelected) {
            val deviceLang = Locale.getDefault().language.lowercase(Locale.ROOT)
            var targetGroup: TrackGroup? = null
            var targetIndex: Int? = null

            for (group in textGroups) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language?.lowercase(Locale.ROOT).orEmpty()
                    if (lang == deviceLang || (deviceLang == "es" && lang.startsWith("es")) || (deviceLang == "en" && lang.startsWith("en"))) {
                        targetGroup = group.mediaTrackGroup
                        targetIndex = i
                        break
                    }
                }
                if (targetGroup != null) break
            }

            if (targetGroup == null) {
                val firstGroup = textGroups.firstOrNull { group ->
                    (0 until group.length).any { group.isTrackSupported(it) }
                }
                if (firstGroup != null) {
                    targetGroup = firstGroup.mediaTrackGroup
                    targetIndex = (0 until firstGroup.length).firstOrNull { firstGroup.isTrackSupported(it) } ?: 0
                }
            }

            if (targetGroup != null && targetIndex != null) {
                val params = controller.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(targetGroup, listOf(targetIndex)))
                    .build()
                controller.trackSelectionParameters = params
                Log.i(TAG, "Auto-selected subtitle track: lang=${targetGroup.getFormat(targetIndex).language}")
            }
        }
    }

    private fun selectDefaultAudioTrackIfNeeded(controller: Player) {
        if (controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) return
        val audioGroups = controller.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return

        var isEnglishSelected = false
        var englishGroup: TrackGroup? = null
        var englishTrackIndex: Int? = null

        for (group in audioGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.lowercase(Locale.ROOT).orEmpty()
                val label = format.label?.lowercase(Locale.ROOT).orEmpty()
                val isEnglish = lang == "en" || lang == "eng" || lang.startsWith("en") ||
                        label.contains("english") || label.contains("inglés") || label.contains("ingles")
                if (isEnglish) {
                    if (group.isTrackSelected(i)) {
                        isEnglishSelected = true
                    }
                    if (englishGroup == null) {
                        englishGroup = group.mediaTrackGroup
                        englishTrackIndex = i
                    }
                }
            }
        }

        // If English is available and not currently selected, prioritize English by default
        if (!isEnglishSelected && englishGroup != null && englishTrackIndex != null) {
            val params = controller.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages("en", "eng")
                .setOverrideForType(TrackSelectionOverride(englishGroup, listOf(englishTrackIndex)))
                .build()
            controller.trackSelectionParameters = params
            Log.i(TAG, "selectDefaultAudioTrackIfNeeded: English audio auto-selected at track index $englishTrackIndex")
        }
    }

    private fun handlePlaybackEnded() {
        saveCurrentProgress(mediaController)
        mediaController?.pause()
        if (torrentInfoHash.isNotBlank()) {
            showDeletePromptDialog = true
        } else {
            proceedAfterPlaybackEnded()
        }
    }

    private fun proceedAfterPlaybackEnded() {
        val effectiveSeriesId = currentSeriesId.takeIf { it > 0 } ?: nextEpisodeSeriesId
        val isTvEpisode = hasNextEpisodeInfo || (effectiveSeriesId > 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0)
        if (!isTvEpisode) {
            finish()
            return
        }

        val autoPlayMode = preferenceManager.getAutoPlayQualitySelectionMethod()
        if (autoPlayMode == AutoPlayQualitySelectionMethod.DO_NOTHING) {
            return
        }

        showNextEpisodeDialog = true
        lifecycleScope.launch {
            try {
                val evaluation = tvPlayerViewModel.evaluateNextEpisode(
                    seriesId = effectiveSeriesId,
                    seriesName = nextEpisodeSeriesName.ifBlank { mediaTitle },
                    currentSeasonNumber = currentSeasonNumber,
                    currentEpisodeNumber = currentEpisodeNumber
                )

                if (evaluation.state == com.martinrevert.latorrentola.network.SeriesContinuationState.NEXT_EPISODE_READY &&
                    evaluation.releases.isNotEmpty() &&
                    (autoPlayMode == AutoPlayQualitySelectionMethod.BY_QUALITY ||
                        autoPlayMode == AutoPlayQualitySelectionMethod.BY_SEED_PEERS)
                ) {
                    val releases = evaluation.releases
                    val selectedRelease = if (autoPlayMode == AutoPlayQualitySelectionMethod.BY_QUALITY) {
                        releases.maxByOrNull { release ->
                            when (release.qualityGroup) {
                                VideoQualityGroup.UHD_4K -> 4
                                VideoQualityGroup.FHD_1080P -> 3
                                VideoQualityGroup.HD_720P -> 2
                                VideoQualityGroup.OTHER -> 1
                            }
                        } ?: releases.first()
                    } else {
                        releases.maxByOrNull { it.seeds + it.peers } ?: releases.first()
                    }

                    val result = tvPlayerViewModel.downloadEpisode(
                        this@LocalPlayerActivity,
                        selectedRelease,
                        evaluation.seriesId,
                        evaluation.seriesName,
                        evaluation.nextSeasonNumber,
                        evaluation.nextEpisodeNumber,
                        evaluation.nextEpisodeName
                    )

                    if (result == TorrentLaunchResult.Started) {
                        showNextEpisodeDialog = false
                    } else if (result is TorrentLaunchResult.InsufficientStorage) {
                        showNextEpisodeDialog = false
                        insufficientStorageData = Pair(result.availableBytes, result.requiredBytes)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed evaluating next episode: ${e.message}", e)
            }
        }
    }

    private fun fetchNextEpisodeData() {
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
    }

    private fun prepareSubtitleTrackIfNeeded(tracks: Tracks?) {
        val player = mediaController ?: return
        if (subtitleStartupCheckStarted || player.playbackState != Player.STATE_READY || tracks == null) {
            return
        }
        subtitleStartupCheckStarted = true
        lifecycleScope.launch {
            try {
                val cachedSubtitle = openSubtitlesRepository.findLatestDownloaded(
                    torrentInfoHash.takeIf(String::isNotBlank)
                )
                if (cachedSubtitle != null && !subtitleOperationInProgress) {
                    attachSubtitle(cachedSubtitle)
                } else {
                    openSubtitlesRepository.warmUp()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun updateCachedAudioTracks(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isNotEmpty()) {
            cachedAudioTrackOptions = audioGroups.flatMap { group ->
                (0 until group.length).map { index ->
                    val format = group.getTrackFormat(index)
                    PlaybackTrackOption(
                        label = trackLabel(format, PlaybackTrackType.AUDIO),
                        group = group.mediaTrackGroup,
                        trackIndex = index,
                        castTrackId = format.id?.toLongOrNull(),
                        isSelected = group.isTrackSelected(index)
                    )
                }
            }
        }
    }

    private fun extractAudioTracksFromFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.isFile || file.length() <= 0L) return@launch
                val extractor = android.media.MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                val extracted = mutableListOf<PlaybackTrackOption>()
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        val lang = if (format.containsKey(android.media.MediaFormat.KEY_LANGUAGE)) format.getString(android.media.MediaFormat.KEY_LANGUAGE) else null
                        val channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 2
                        val displayLang = lang?.takeIf { it.isNotBlank() && it != "und" }?.let {
                            val loc = Locale.forLanguageTag(it)
                            "${getLanguageFlag(it, it)} ${loc.getDisplayName(Locale.getDefault())}"
                        } ?: "Audio ${extracted.size + 1}"
                        val codec = when {
                            mime.contains("eac3") -> "E-AC-3"
                            mime.contains("ac3") -> "AC-3"
                            mime.contains("dts") -> "DTS"
                            mime.contains("truehd") -> "Dolby TrueHD"
                            mime.contains("aac") -> "AAC"
                            mime.contains("flac") -> "FLAC"
                            mime.contains("opus") -> "Opus"
                            mime.contains("vorbis") -> "Vorbis"
                            else -> mime.substringAfter("audio/").uppercase(Locale.ROOT)
                        }
                        extracted.add(
                            PlaybackTrackOption(
                                label = "$displayLang ($codec $channels ch)",
                                group = null,
                                trackIndex = i,
                                castTrackId = (i + 1).toLong(),
                                isSelected = extracted.isEmpty()
                            )
                        )
                    }
                }
                extractor.release()
                if (extracted.isNotEmpty() && cachedAudioTrackOptions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        cachedAudioTrackOptions = extracted
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(TAG, "Could not extract audio tracks via MediaExtractor: ${e.message}")
            }
        }
    }

    private fun showSubtitleOptions() {
        val controller = mediaController ?: return
        val options = availableTrackOptions(PlaybackTrackType.SUBTITLE)

        val textDisabled = controller.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val anySelected = !textDisabled && options.any { it.isSelected }

        val offOption = PlaybackTrackOption(
            label = getString(R.string.player_subtitles_off),
            group = null,
            trackIndex = null,
            castTrackId = null,
            isSelected = textDisabled || !anySelected
        )

        val allOptions = listOf(offOption) + options
        val selectedIndex = allOptions.indexOfFirst { it.isSelected }.let { if (it < 0) 0 else it }

        playerBottomSheetState = PlayerBottomSheetState.Tracks(
            type = PlaybackTrackType.SUBTITLE,
            title = getString(R.string.player_subtitle_tracks),
            options = allOptions,
            selectedIndex = selectedIndex,
            onSelected = { option ->
                selectTrack(PlaybackTrackType.SUBTITLE, option)
                playerBottomSheetState = null
            }
        )
    }

    private fun showAudioOptions() {
        val controller = mediaController ?: return
        val options = availableTrackOptions(PlaybackTrackType.AUDIO)
        if (options.isEmpty()) {
            playerBottomSheetState = PlayerBottomSheetState.Message(
                title = getString(R.string.player_audio_tracks),
                message = getString(R.string.player_no_audio_tracks),
                onDismiss = { playerBottomSheetState = null }
            )
            return
        }
        val selectedIndex = options.indexOfFirst { it.isSelected }.let { if (it < 0) 0 else it }

        playerBottomSheetState = PlayerBottomSheetState.Tracks(
            type = PlaybackTrackType.AUDIO,
            title = getString(R.string.player_audio_tracks),
            options = options,
            selectedIndex = selectedIndex,
            onSelected = { option ->
                selectTrack(PlaybackTrackType.AUDIO, option)
                playerBottomSheetState = null
            }
        )
    }

    private fun showSubtitleStyleOptions() {
        val fontResolver = createFontFamilyResolver(this)

        val initialFont = preferenceManager.getSubtitleFontName()
        val initialSize = preferenceManager.getSubtitleSizeFraction()
        val initialFg = preferenceManager.getSubtitleForegroundColor()
        val initialBg = preferenceManager.getSubtitleBackgroundColor()
        val initialEdge = preferenceManager.getSubtitleEdgeType()
        val initialOffset = preferenceManager.getSubtitleBottomOffset()

        fun applyStyle(fontName: String, sizeFraction: Float, fg: Int, bg: Int, edge: Int, offset: Float) {
            val fontFamily = FontFamily(Font(googleFont = GoogleFont(fontName), fontProvider = fontProvider))
            val typeface = fontResolver.resolve(fontFamily).value as? android.graphics.Typeface
            customCaptionStyle = CaptionStyleCompat(
                fg,
                bg,
                android.graphics.Color.TRANSPARENT,
                edge,
                android.graphics.Color.BLACK,
                typeface
            )
            customSubtitleSizeFraction = sizeFraction
            customSubtitleBottomOffset = offset
        }

        playerBottomSheetState = PlayerBottomSheetState.AdvancedSubtitleStyle(
            currentFontName = initialFont,
            currentFontSizeFraction = initialSize,
            currentForegroundColor = initialFg,
            currentBackgroundColor = initialBg,
            currentEdgeType = initialEdge,
            currentBottomOffset = initialOffset,
            onLiveChange = { fontName, sizeFraction, fg, bg, edge, offset ->
                applyStyle(fontName, sizeFraction, fg, bg, edge, offset)
                applyCastSubtitleStyleIfConnected()
            },
            onSave = { fontName, sizeFraction, fg, bg, edge, offset ->
                preferenceManager.setSubtitleFontName(fontName)
                preferenceManager.setSubtitleSizeFraction(sizeFraction)
                preferenceManager.setSubtitleForegroundColor(fg)
                preferenceManager.setSubtitleBackgroundColor(bg)
                preferenceManager.setSubtitleEdgeType(edge)
                preferenceManager.setSubtitleBottomOffset(offset)
                applyStyle(fontName, sizeFraction, fg, bg, edge, offset)
                applyCastSubtitleStyleIfConnected()
                playerBottomSheetState = null
            },
            onCancel = {
                applyStyle(initialFont, initialSize, initialFg, initialBg, initialEdge, initialOffset)
                applyCastSubtitleStyleIfConnected()
                playerBottomSheetState = null
            }
        )
    }

    private fun applyCastSubtitleStyleIfConnected() {
        try {
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(this)
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this, mainExecutor)
                .addOnSuccessListener { castContext ->
                    val session = castContext.sessionManager.currentCastSession
                    if (session != null && session.isConnected) {
                        val remoteMediaClient = session.remoteMediaClient
                        if (remoteMediaClient != null) {
                            val fg = preferenceManager.getSubtitleForegroundColor()
                            val bg = preferenceManager.getSubtitleBackgroundColor()
                            val edge = preferenceManager.getSubtitleEdgeType()
                            val size = preferenceManager.getSubtitleSizeFraction()

                            val castBgColor = if (bg == android.graphics.Color.TRANSPARENT || android.graphics.Color.alpha(bg) == 0) {
                                android.graphics.Color.argb(1, 0, 0, 0)
                            } else {
                                bg
                            }

                            val textTrackStyle = com.google.android.gms.cast.TextTrackStyle().apply {
                                foregroundColor = fg
                                backgroundColor = castBgColor
                                windowType = com.google.android.gms.cast.TextTrackStyle.WINDOW_TYPE_NONE
                                windowColor = android.graphics.Color.argb(1, 0, 0, 0)
                                edgeType = when (edge) {
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_OUTLINE
                                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DROP_SHADOW
                                    CaptionStyleCompat.EDGE_TYPE_RAISED -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_RAISED
                                    CaptionStyleCompat.EDGE_TYPE_DEPRESSED -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_DEPRESSED
                                    else -> com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_NONE
                                }
                                edgeColor = android.graphics.Color.BLACK
                                fontScale = (size / 0.0533f).coerceIn(0.5f, 2.5f)
                                fontGenericFamily = com.google.android.gms.cast.TextTrackStyle.FONT_FAMILY_SANS_SERIF
                            }
                            remoteMediaClient.setTextTrackStyle(textTrackStyle)

                            val mediaStatus = remoteMediaClient.mediaStatus
                            if (mediaStatus != null) {
                                val tracks = mediaStatus.mediaInfo?.mediaTracks.orEmpty()
                                val activeTrackIds = mediaStatus.activeTrackIds ?: longArrayOf()
                                val textTrack = tracks.firstOrNull { it.type == MediaTrack.TYPE_TEXT }
                                if (textTrack != null && !activeTrackIds.contains(textTrack.id)) {
                                    val newActive = activeTrackIds.filter { id ->
                                        tracks.firstOrNull { it.id == id }?.type != MediaTrack.TYPE_TEXT
                                    }.toMutableList()
                                    newActive.add(textTrack.id)
                                    remoteMediaClient.setActiveMediaTracks(newActive.toLongArray())
                                    Log.i(TAG, "Activated Cast text track ID: ${textTrack.id}")
                                }
                            }

                            Log.i(TAG, "Live updated custom TextTrackStyle on active Cast session")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Cast subtitle style: ${e.message}")
        }
    }

    private fun trackLabel(format: androidx.media3.common.Format, type: PlaybackTrackType): String {
        val rawLanguage = format.language?.takeIf { it.isNotBlank() && it != "und" }
        val customLabel = format.label?.takeIf(String::isNotBlank)

        val textToScan = "${customLabel.orEmpty()} ${format.id.orEmpty()}".lowercase(Locale.ROOT)
        val flag = getLanguageFlag(rawLanguage, textToScan)

        val displayLang = rawLanguage?.let { lang ->
            val locale = Locale.forLanguageTag(lang)
            locale.getDisplayName(Locale.getDefault()).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        val languageStr = if (!displayLang.isNullOrBlank() && displayLang.lowercase(Locale.ROOT) != rawLanguage.lowercase(Locale.ROOT)) {
            "$flag $displayLang"
        } else {
            val fallbackName = when {
                textToScan.contains("latino") -> "Español (Latinoamérica)"
                textToScan.contains("esp") || textToScan.contains("spa") || textToScan.contains("castellano") -> "Español"
                textToScan.contains("eng") || textToScan.contains("ingl") -> "Inglés"
                textToScan.contains("por") || textToScan.contains("portu") -> "Portugués"
                textToScan.contains("jpn") || textToScan.contains("jap") -> "Japonés"
                textToScan.contains("fra") || textToScan.contains("fren") -> "Francés"
                textToScan.contains("ger") || textToScan.contains("deu") -> "Alemán"
                textToScan.contains("ita") -> "Italiano"
                textToScan.contains("rus") -> "Ruso"
                else -> null
            }
            if (fallbackName != null) "$flag $fallbackName" else "$flag Desconocido"
        }

        if (type == PlaybackTrackType.AUDIO) {
            val mime = format.sampleMimeType?.lowercase(Locale.ROOT) ?: ""
            val codecs = format.codecs?.lowercase(Locale.ROOT) ?: ""
            val labelScan = customLabel?.lowercase(Locale.ROOT) ?: ""

            val codecName = when {
                mime.contains("eac3") || codecs.contains("ec-3") || labelScan.contains("eac3") || labelScan.contains("dd+") -> "E-AC-3 (Dolby Digital Plus)"
                mime.contains("ac3") || codecs.contains("ac-3") || labelScan.contains("ac3") || labelScan.contains("dolby") -> "AC-3 (Dolby Digital)"
                mime.contains("dts") || labelScan.contains("dts") -> "DTS"
                mime.contains("truehd") || labelScan.contains("truehd") -> "Dolby TrueHD"
                mime.contains("flac") || labelScan.contains("flac") -> "FLAC"
                mime.contains("opus") || labelScan.contains("opus") -> "Opus"
                mime.contains("vorbis") -> "Vorbis"
                mime.contains("mp3") || mime.contains("mpeg") -> "MP3"
                mime.contains("aac") || codecs.contains("mp4a") || labelScan.contains("aac") -> "AAC"
                else -> mime.substringAfter("audio/").uppercase(Locale.ROOT).ifBlank { "Audio" }
            }

            val detectedChannels = if (format.channelCount > 0) {
                format.channelCount
            } else {
                when {
                    labelScan.contains("7.1") || labelScan.contains("8ch") -> 8
                    labelScan.contains("5.1") || labelScan.contains("6ch") -> 6
                    labelScan.contains("2.0") || labelScan.contains("stereo") || labelScan.contains("2ch") -> 2
                    labelScan.contains("1.0") || labelScan.contains("mono") -> 1
                    else -> -1
                }
            }

            val channelStr = when (detectedChannels) {
                1 -> "1.0 (Mono)"
                2 -> "2.0 (Estéreo)"
                6 -> "5.1 (Surround)"
                8 -> "7.1 (Surround)"
                in 3..5 -> "$detectedChannels Canales"
                in 7..12 -> "$detectedChannels.1 Surround"
                else -> null
            }

            val parts = listOfNotNull(
                customLabel,
                languageStr,
                codecName,
                channelStr
            ).distinct()

            return parts.joinToString(" · ")
        } else {
            val parts = listOfNotNull(customLabel ?: format.id?.takeIf(String::isNotBlank), languageStr).distinct()
            return if (parts.isNotEmpty()) parts.joinToString(" · ") else format.sampleMimeType ?: getString(R.string.player_track_unknown)
        }
    }

    private fun availableTrackOptions(type: PlaybackTrackType): List<PlaybackTrackOption> {
        val controller = mediaController ?: return emptyList()
        val media3Type = when (type) {
            PlaybackTrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            PlaybackTrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
        }
        val controllerOptions = controller.currentTracks.groups
            .filter { it.type == media3Type }
            .flatMap { group ->
                (0 until group.length).map { index ->
                    val format = group.getTrackFormat(index)
                    PlaybackTrackOption(
                        label = trackLabel(format, type),
                        group = group.mediaTrackGroup,
                        trackIndex = index,
                        castTrackId = format.id?.toLongOrNull(),
                        isSelected = group.isTrackSelected(index)
                    )
                }
            }

        if (controllerOptions.isNotEmpty()) {
            if (type == PlaybackTrackType.AUDIO) {
                cachedAudioTrackOptions = controllerOptions
            }
            return controllerOptions
        }

        // When casting, check remote media client tracks or cached audio tracks
        if (controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            val rmc = CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.remoteMediaClient
            val mediaTracks = rmc?.mediaInfo?.mediaTracks.orEmpty()
            val activeTrackIds = rmc?.mediaStatus?.activeTrackIds ?: longArrayOf()
            val castTargetType = if (type == PlaybackTrackType.AUDIO) MediaTrack.TYPE_AUDIO else MediaTrack.TYPE_TEXT
            val matchedCastTracks = mediaTracks.filter { it.type == castTargetType }
            if (matchedCastTracks.isNotEmpty()) {
                val castOptions = matchedCastTracks.map { track ->
                    val lang = track.language?.takeIf { it.isNotBlank() && it != "und" }
                    val name = track.name?.takeIf(String::isNotBlank)
                    val label = name ?: lang?.let {
                        val locale = Locale.forLanguageTag(it)
                        "${getLanguageFlag(it, it)} ${locale.getDisplayName(Locale.getDefault())}"
                    } ?: "${if (type == PlaybackTrackType.AUDIO) "Audio" else "Subtítulo"} ${track.id}"
                    PlaybackTrackOption(
                        label = label,
                        group = null,
                        trackIndex = null,
                        castTrackId = track.id,
                        isSelected = activeTrackIds.contains(track.id)
                    )
                }
                if (type == PlaybackTrackType.AUDIO) {
                    cachedAudioTrackOptions = castOptions
                }
                return castOptions
            }

            if (type == PlaybackTrackType.AUDIO && cachedAudioTrackOptions.isNotEmpty()) {
                return cachedAudioTrackOptions
            }
        }

        return emptyList()
    }

    private fun selectTrack(type: PlaybackTrackType, option: PlaybackTrackOption?) {
        val controller = mediaController ?: return
        val media3Type = when (type) {
            PlaybackTrackType.AUDIO -> C.TRACK_TYPE_AUDIO
            PlaybackTrackType.SUBTITLE -> C.TRACK_TYPE_TEXT
        }
        val parameters = controller.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(media3Type)
            .setTrackTypeDisabled(media3Type, type == PlaybackTrackType.SUBTITLE && option == null)

        if (type == PlaybackTrackType.AUDIO && option != null) {
            val selectedLang = option.group?.let { g ->
                option.trackIndex?.let { idx -> g.getFormat(idx).language }
            } ?: run {
                val rmc = CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.remoteMediaClient
                rmc?.mediaInfo?.mediaTracks?.firstOrNull { it.id == option.castTrackId }?.language
            }
            if (!selectedLang.isNullOrBlank() && selectedLang != "und") {
                parameters.setPreferredAudioLanguage(selectedLang)
            }
        }

        if (option?.group != null && option.trackIndex != null && controller.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            controller.trackSelectionParameters = parameters
                .setOverrideForType(TrackSelectionOverride(option.group, listOf(option.trackIndex)))
                .build()
        } else {
            controller.trackSelectionParameters = parameters.build()
        }

        if (type == PlaybackTrackType.AUDIO && cachedAudioTrackOptions.isNotEmpty() && option != null) {
            cachedAudioTrackOptions = cachedAudioTrackOptions.map {
                it.copy(isSelected = it.label == option.label && (option.trackIndex == null || it.trackIndex == option.trackIndex))
            }
        }

        // Active Cast track switching
        if (controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            try {
                val rmc = CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.remoteMediaClient
                if (rmc != null) {
                    val activeIds = rmc.mediaStatus?.activeTrackIds?.toMutableList() ?: mutableListOf()
                    val allTracks = rmc.mediaInfo?.mediaTracks.orEmpty()
                    val castType = if (type == PlaybackTrackType.AUDIO) MediaTrack.TYPE_AUDIO else MediaTrack.TYPE_TEXT
                    val typeTrackIds = allTracks.filter { it.type == castType }.map { it.id }.toSet()

                    activeIds.removeAll(typeTrackIds)

                    val targetCastId = option?.castTrackId ?: run {
                        if (option?.group != null && option.trackIndex != null) {
                            val format = option.group.getFormat(option.trackIndex)
                            format.id?.toLongOrNull() ?: allTracks.firstOrNull {
                                it.type == castType && (it.language == format.language || it.name == format.label)
                            }?.id
                        } else null
                    }

                    if (targetCastId != null && targetCastId > 0L) {
                        activeIds.add(targetCastId)
                    }
                    rmc.setActiveMediaTracks(activeIds.toLongArray())
                    Log.i(TAG, "Cast setActiveMediaTracks called for $type: activeIds=$activeIds")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set active Cast media tracks: ${e.message}")
            }
        }
    }

    private fun searchSubtitles() {
        if (subtitleOperationInProgress) return
        subtitleOperationInProgress = true
        playerBottomSheetState = PlayerBottomSheetState.Progress(
            title = getString(R.string.opensubtitles_search),
            message = getString(R.string.opensubtitles_searching)
        )
        lifecycleScope.launch {
            var results: List<OpenSubtitleResult>? = null
            var message: String? = null
            var dbRecord: com.martinrevert.latorrentola.model.torrent.TorrentDownload? = null
            try {
                var effectiveSeriesId = currentSeriesId
                var effectiveMovieId = currentMovieId
                var effectiveSeason = currentSeasonNumber
                var effectiveEpisode = currentEpisodeNumber

                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val fileName = filePath?.let { File(it).name }

                // 1. Recover season and episode numbers if not provided in Intent
                if (effectiveSeason <= 0 || effectiveEpisode <= 0) {
                    val (extractedSeason, extractedEpisode) = extractSeasonAndEpisode(mediaTitle, fileName)
                    if (effectiveSeason <= 0) effectiveSeason = extractedSeason
                    if (effectiveEpisode <= 0) effectiveEpisode = extractedEpisode
                }

                val isEpisode = (effectiveSeason > 0 && effectiveEpisode > 0) ||
                        EPISODE_PATTERN.containsMatchIn(mediaTitle) ||
                        (fileName != null && EPISODE_PATTERN.containsMatchIn(fileName)) ||
                        currentSeasonNumber > 0
                val mediaType = if (isEpisode) "episode" else "movie"

                // 2. Recover IDs from Room DB if not present in Intent
                if (torrentInfoHash.isNotBlank()) {
                    try {
                        dbRecord = torrentDownloadDao.get(torrentInfoHash)
                        if (dbRecord != null) {
                            if (dbRecord.movieId != null && dbRecord.movieId > 0 && effectiveMovieId <= 0) {
                                effectiveMovieId = dbRecord.movieId
                            }
                            if (dbRecord.seriesId != null && dbRecord.seriesId > 0 && effectiveSeriesId <= 0) {
                                effectiveSeriesId = dbRecord.seriesId
                                if (effectiveSeason <= 0) effectiveSeason = dbRecord.seasonNumber ?: 0
                                if (effectiveEpisode <= 0) effectiveEpisode = dbRecord.episodeNumber ?: 0
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to read download record for IMDb lookup: ${e.message}")
                    }
                }

                // 3. PRIORITY #1: Resolve IMDb ID
                var resolvedImdbId: String? = null

                // 3a. Direct 'tt' identifier in title or filename/path
                val ttMatch = Regex("""\btt\d{7,8}\b""", RegexOption.IGNORE_CASE).find(mediaTitle)
                    ?: fileName?.let { Regex("""\btt\d{7,8}\b""", RegexOption.IGNORE_CASE).find(it) }
                if (ttMatch != null) {
                    resolvedImdbId = ttMatch.value
                }

                // 3b. Fetch IMDb ID from known TMDB ID
                if (resolvedImdbId.isNullOrBlank()) {
                    try {
                        if (effectiveSeriesId > 0 && isEpisode) {
                            resolvedImdbId = tmdbRepository.getTvImdbId(effectiveSeriesId)
                        } else if (effectiveMovieId > 0) {
                            resolvedImdbId = tmdbRepository.getMovieImdbId(effectiveMovieId)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to fetch IMDb ID from TMDB repository: ${e.message}")
                    }
                }

                // 3c. Search TMDB/YTS by clean title to find the IMDb ID if still unknown
                if (resolvedImdbId.isNullOrBlank()) {
                    val searchCandidates = extractCleanSearchTitles(mediaTitle).ifEmpty {
                        fileName?.let { extractCleanSearchTitles(it) }.orEmpty()
                    }
                    for (candidate in searchCandidates) {
                        if (isEpisode) {
                            try {
                                val tvPage = tmdbRepository.searchTvSeries(candidate)
                                val firstTv = tvPage.results?.firstOrNull()
                                if (firstTv != null && firstTv.id > 0) {
                                    val imdb = tmdbRepository.getTvImdbId(firstTv.id)
                                    if (!imdb.isNullOrBlank()) {
                                        resolvedImdbId = imdb
                                        if (effectiveSeriesId <= 0) effectiveSeriesId = firstTv.id
                                        Log.i(TAG, "Resolved TV IMDb ID $resolvedImdbId from TMDB for '$candidate'")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Failed to resolve TV IMDb ID via TMDB for '$candidate': ${e.message}")
                            }
                        } else {
                            try {
                                val movieSearch = ytsRepository.searchMovies(candidate, 1)
                                val firstMovie = movieSearch.data?.movies?.firstOrNull()
                                val imdb = firstMovie?.imdbCode
                                if (!imdb.isNullOrBlank()) {
                                    resolvedImdbId = imdb
                                    if (effectiveMovieId <= 0) effectiveMovieId = firstMovie.id
                                    Log.i(TAG, "Resolved Movie IMDb ID $resolvedImdbId from YTS for '$candidate'")
                                    break
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Failed to resolve Movie IMDb ID via YTS for '$candidate': ${e.message}")
                            }
                        }
                    }
                }

                // 4. Perform OpenSubtitles search PRIORITIZING IMDb ID FIRST
                var imdbResults: List<OpenSubtitleResult>? = null
                if (!resolvedImdbId.isNullOrBlank()) {
                    Log.i(TAG, "Searching OpenSubtitles by IMDb ID: $resolvedImdbId (isEpisode=$isEpisode, S=$effectiveSeason, E=$effectiveEpisode)")
                    try {
                        imdbResults = if (isEpisode && effectiveSeason > 0 && effectiveEpisode > 0) {
                            openSubtitlesRepository.searchByImdbId(
                                imdbId = resolvedImdbId,
                                seasonNumber = effectiveSeason,
                                episodeNumber = effectiveEpisode
                            )
                        } else {
                            openSubtitlesRepository.searchByImdbId(imdbId = resolvedImdbId)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "OpenSubtitles searchByImdbId failed: ${e.message}")
                    }

                    // If episode-specific search returned nothing, try parent show IMDb ID search as secondary attempt
                    if (imdbResults.isNullOrEmpty() && isEpisode && effectiveSeason > 0 && effectiveEpisode > 0) {
                        try {
                            Log.i(TAG, "No episode-specific subtitles returned. Retrying parent IMDb ID without season filter: $resolvedImdbId")
                            val parentResults = openSubtitlesRepository.searchByImdbId(imdbId = resolvedImdbId)
                            if (parentResults.isNotEmpty()) {
                                imdbResults = filterFallbackResults(
                                    rawResults = parentResults,
                                    expectedTitle = "",
                                    expectedYear = null,
                                    expectedSeason = effectiveSeason,
                                    expectedEpisode = effectiveEpisode
                                ).ifEmpty { parentResults }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Parent IMDb ID search retry failed: ${e.message}")
                        }
                    }
                }

                // 5. String query search ONLY as a last resort
                if (!imdbResults.isNullOrEmpty()) {
                    results = imdbResults
                } else {
                    Log.w(TAG, "No IMDb ID results found. Falling back to text query search for: $mediaTitle")
                    val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(mediaTitle)
                    val expectedYear = yearMatch?.value?.toIntOrNull()
                    val cleanTitles = extractCleanSearchTitles(mediaTitle)
                    val primaryQuery = cleanTitles.firstOrNull() ?: mediaTitle
                        .replace(Regex("""\b(19\d\d|20\d\d)\b"""), "")
                        .replace(Regex("""[()\[\]]"""), "")
                        .replace(EPISODE_PATTERN, "")
                        .trim()
                        .ifBlank { mediaTitle }

                    val rawResults = openSubtitlesRepository.search(
                        query = primaryQuery,
                        seasonNumber = effectiveSeason.takeIf { it > 0 },
                        episodeNumber = effectiveEpisode.takeIf { it > 0 },
                        type = mediaType
                    )

                    results = filterFallbackResults(
                        rawResults = rawResults,
                        expectedTitle = primaryQuery,
                        expectedYear = expectedYear,
                        expectedSeason = effectiveSeason.takeIf { it > 0 },
                        expectedEpisode = effectiveEpisode.takeIf { it > 0 }
                    )
                }

                if (results.isEmpty()) {
                    message = getString(R.string.opensubtitles_no_results)
                }
            } catch (error: OpenSubtitlesException) {
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = getString(R.string.opensubtitles_request_failed)
            } finally {
                subtitleOperationInProgress = false
            }

            if (!results.isNullOrEmpty()) {
                val currentFile = intent.getStringExtra(EXTRA_FILE_PATH)?.let { File(it).name }
                    ?.takeIf { it.isNotBlank() }
                    ?: dbRecord?.mediaPath?.let { File(it).name }?.takeIf { it.isNotBlank() }
                    ?: dbRecord?.title?.takeIf { it.isNotBlank() }
                    ?: intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                    ?: mediaTitle

                val deviceLang = Locale.getDefault().language.lowercase(Locale.ROOT)
                val sortedResults = results.sortedByDescending { result ->
                    val lang = result.language.lowercase(Locale.ROOT)
                    lang == deviceLang ||
                            (deviceLang == "es" && lang == "spa") ||
                            (deviceLang == "en" && lang == "eng") ||
                            (deviceLang == "pt" && (lang == "por" || lang == "pob"))
                }

                playerBottomSheetState = PlayerBottomSheetState.SubtitleResults(
                    results = sortedResults,
                    currentFileName = currentFile,
                    onSelected = { downloadSubtitle(it) }
                )
            } else if (message != null) {
                playerBottomSheetState = PlayerBottomSheetState.Message(
                    title = getString(R.string.opensubtitles_search),
                    message = message,
                    onDismiss = { playerBottomSheetState = null }
                )
            } else {
                playerBottomSheetState = null
            }
        }
    }

    private fun downloadSubtitle(result: OpenSubtitleResult) {
        if (subtitleOperationInProgress) return
        subtitleOperationInProgress = true
        playerBottomSheetState = PlayerBottomSheetState.Progress(
            title = getString(R.string.opensubtitles_search),
            message = getString(R.string.opensubtitles_downloading)
        )
        lifecycleScope.launch {
            var errorMessage: String? = null
            try {
                val subtitle = openSubtitlesRepository.download(result, torrentInfoHash)
                attachSubtitle(subtitle)

                // Co-locate downloaded subtitle if playing external USB or remote SMB/NFS media
                val remoteServerId = intent.getStringExtra(EXTRA_REMOTE_SERVER_ID)
                val remoteFilePath = intent.getStringExtra(EXTRA_REMOTE_FILE_PATH)
                val localFile = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
                if (!remoteServerId.isNullOrBlank() && !remoteFilePath.isNullOrBlank()) {
                    try {
                        val vttText = subtitle.file.readText()
                        val item = RemoteFileItem(
                            path = remoteFilePath,
                            name = remoteFilePath.substringAfterLast('/'),
                            isDirectory = false,
                            serverConfigId = remoteServerId
                        )
                        remoteStorageManager.saveColocatedSubtitle(item, vttText, subtitle.language)
                        Log.i(TAG, "Saved co-located subtitle to remote server: $remoteFilePath")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to co-locate subtitle on remote server: ${e.message}")
                    }
                } else if (localFile != null && localFile.exists() && !TorrentStorageResolver.isValidTorrentDirectory(this@LocalPlayerActivity, localFile.parentFile ?: localFile)) {
                    try {
                        val vttText = subtitle.file.readText()
                        val item = RemoteFileItem(
                            path = localFile.absolutePath,
                            name = localFile.name,
                            isDirectory = false,
                            serverConfigId = "usb"
                        )
                        remoteStorageManager.saveColocatedSubtitle(item, vttText, subtitle.language)
                        Log.i(TAG, "Saved co-located subtitle to USB storage: ${localFile.name}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to co-locate subtitle on USB drive: ${e.message}")
                    }
                }

                playerBottomSheetState = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: OpenSubtitlesException) {
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: Exception) {
                errorMessage = getString(R.string.opensubtitles_download_failed)
            } finally {
                subtitleOperationInProgress = false
            }

            if (errorMessage != null) {
                playerBottomSheetState = PlayerBottomSheetState.Message(
                    title = getString(R.string.opensubtitles_search),
                    message = errorMessage,
                    onDismiss = { playerBottomSheetState = null }
                )
            }
        }
    }

    private fun attachSubtitle(subtitle: DownloadedSubtitle) {
        val controller = mediaController ?: return
        val localUri = Uri.fromFile(subtitle.file)
        val displayLabel = subtitle.label.ifBlank { "OpenSubtitles (${subtitle.language.uppercase(Locale.ROOT)})" }
        val subtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(localUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(subtitle.language)
            .setLabel(displayLabel)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .setRoleFlags(C.ROLE_FLAG_CAPTION)
            .build()

        val item = currentMediaItem ?: return
        val currentPosition = controller.currentPosition.coerceAtLeast(0L)
        val shouldPlay = controller.playWhenReady

        val updatedItem = item.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfiguration))
            .build()
        currentMediaItem = updatedItem

        controller.setMediaItem(updatedItem, currentPosition)
        controller.prepare()
        controller.playWhenReady = shouldPlay

        val params = controller.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(subtitle.language)
            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
            .build()
        controller.trackSelectionParameters = params
        Log.i(TAG, "Attached subtitle: ${subtitle.file.name}, lang: ${subtitle.language}")
    }

    private fun extractInfoHash(vararg candidates: String?): String {
        val hashPattern = Regex("(?i)[a-f0-9]{40}")
        for (candidate in candidates) {
            if (candidate.isNullOrBlank()) continue
            val match = hashPattern.find(candidate)
            if (match != null) {
                return match.value.lowercase(Locale.ROOT)
            }
        }
        return ""
    }

    override fun onStart() {
        super.onStart()
        isPlayerForeground = true
    }

    override fun onResume() {
        super.onResume()
        isPlayerForeground = true
    }

    override fun onPause() {
        saveCurrentProgress(mediaController)
        super.onPause()
    }

    override fun onStop() {
        isPlayerForeground = false
        mediaController?.clearVideoSurface()
        saveCurrentProgress(mediaController)
        super.onStop()
    }

    private fun startProgressTicker(player: Player) {
        progressTickerJob?.cancel()
        progressTickerJob = lifecycleScope.launch {
            while (isActive) {
                delay(10.seconds)
                if (player.isPlaying) {
                    saveCurrentProgress(player)
                    CastContext.getSharedInstance()?.sessionManager?.currentCastSession?.let { castSession ->
                        saveCastSessionProgress(castSession)
                    }
                }
            }
        }
    }

    private fun setupCastSessionListener() {
        try {
            val mainExecutor = ContextCompat.getMainExecutor(this)
            CastContext.getSharedInstance(this, mainExecutor).addOnSuccessListener { castContext ->
                val listener = object : SessionManagerListener<CastSession> {
                    override fun onSessionStarted(session: CastSession, sessionId: String) {
                        isCastConnecting = false
                        attachRemoteMediaClientCallback(session)
                    }
                    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                        isCastConnecting = false
                        attachRemoteMediaClientCallback(session)
                    }
                    override fun onSessionEnding(session: CastSession) {
                        saveCastSessionProgress(session)
                        detachRemoteMediaClientCallback(session)
                        isCastConnecting = false
                    }
                    override fun onSessionEnded(session: CastSession, error: Int) {
                        saveCastSessionProgress(session)
                        detachRemoteMediaClientCallback(session)
                        isCastConnecting = false
                    }
                    override fun onSessionStartFailed(session: CastSession, error: Int) {
                        isCastConnecting = false
                        val devName = session.castDevice?.friendlyName ?: "Cast"
                        castErrorMessage = getString(R.string.cast_connection_failed, devName)
                    }
                    override fun onSessionResumeFailed(session: CastSession, error: Int) {
                        isCastConnecting = false
                        val devName = session.castDevice?.friendlyName ?: "Cast"
                        castErrorMessage = getString(R.string.cast_connection_failed, devName)
                    }
                    override fun onSessionSuspended(session: CastSession, reason: Int) {}
                    override fun onSessionStarting(session: CastSession) {
                        val devName = session.castDevice?.friendlyName ?: "Cast"
                        connectingDeviceName = devName
                        isCastConnecting = true
                    }
                    override fun onSessionResuming(session: CastSession, sessionId: String) {
                        val devName = session.castDevice?.friendlyName ?: "Cast"
                        connectingDeviceName = devName
                        isCastConnecting = true
                    }
                }
                castSessionManagerListener = listener
                castContext.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
                castContext.sessionManager.currentCastSession?.let { attachRemoteMediaClientCallback(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CastContext unavailable for session listener: ${e.message}")
        }
    }

    private fun attachRemoteMediaClientCallback(session: CastSession) {
        val rmc = session.remoteMediaClient ?: return
        if (remoteMediaClientCallback == null) {
            val callback = object : RemoteMediaClient.Callback() {
                override fun onStatusUpdated() {
                    val status = rmc.mediaStatus ?: return
                    if (status.playerState == MediaStatus.PLAYER_STATE_PAUSED) {
                        saveCastSessionProgress(session)
                    }
                    if (status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                        status.idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                        saveCastSessionProgress(session)
                        if (!nextEpisodeTriggered) {
                            nextEpisodeTriggered = true
                            lifecycleScope.launch(Dispatchers.Main) {
                                handlePlaybackEnded()
                            }
                        }
                    }
                }
            }
            remoteMediaClientCallback = callback
            rmc.registerCallback(callback)
            applyCastSubtitleStyleIfConnected()
        }
    }

    private fun detachRemoteMediaClientCallback(session: CastSession) {
        val rmc = session.remoteMediaClient ?: return
        remoteMediaClientCallback?.let { rmc.unregisterCallback(it) }
        remoteMediaClientCallback = null
    }

    private fun saveCurrentProgress(player: Player?) {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0L) return
        if (pos > 5000L) {
            lastKnownPlaybackPositionMs = pos
        }

        val item = p.currentMediaItem ?: return
        val mediaId = item.mediaId
        if (mediaId.isBlank() || mediaId == MediaItem.DEFAULT_MEDIA_ID) return

        val title = item.mediaMetadata.title?.toString() ?: mediaTitle
        val isEpisode = currentSeriesId > 0

        val isNearlyFinished = pos >= (dur * 0.95f).toLong()
        val finalPos = if (isNearlyFinished) dur else pos

        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title.ifBlank { "Media Item" },
            positionMs = finalPos,
            durationMs = dur,
            timestamp = System.currentTimeMillis(),
            isEpisode = isEpisode
        )

        lifecycleScope.launch {
            try {
                userLibraryRepository.savePlaybackProgress(progress)
                Log.i(TAG, "Saved progress: $finalPos / $dur ms for $mediaId (completed=${progress.isCompleted})")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playback progress", e)
            }
        }
    }

    private fun saveCastSessionProgress(session: CastSession) {
        val rmc = session.remoteMediaClient ?: return
        val pos = rmc.approximateStreamPosition
        val dur = rmc.streamDuration
        if (dur <= 0L || pos <= 0L) return
        lastKnownPlaybackPositionMs = pos

        val controller = mediaController ?: return
        val item = controller.currentMediaItem ?: return
        val mediaId = item.mediaId
        if (mediaId.isBlank() || mediaId == MediaItem.DEFAULT_MEDIA_ID) return

        val title = item.mediaMetadata.title?.toString() ?: mediaTitle
        val isEpisode = currentSeriesId > 0

        val isNearlyFinished = pos >= (dur * 0.95f).toLong()
        val finalPos = if (isNearlyFinished) dur else pos

        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title.ifBlank { "Media Item" },
            positionMs = finalPos,
            durationMs = dur,
            timestamp = System.currentTimeMillis(),
            isEpisode = isEpisode
        )

        lifecycleScope.launch {
            try {
                userLibraryRepository.savePlaybackProgress(progress)
                Log.i(TAG, "Saved Cast progress: $finalPos / $dur ms for $mediaId (completed=${progress.isCompleted})")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving Cast playback progress", e)
            }
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val controller = mediaController
        val isCasting = controller?.deviceInfo?.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        if (isCasting && event.action == android.view.KeyEvent.ACTION_DOWN) {
            val mediaRouter = MediaRouter.getInstance(this)
            val selectedRoute = mediaRouter.selectedRoute
            if (!selectedRoute.isDefault && selectedRoute.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        selectedRoute.requestUpdateVolume(1)
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        selectedRoute.requestUpdateVolume(-1)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        isPlayerForeground = false
        mediaController?.clearVideoSurface()
        progressTickerJob?.cancel()
        progressTickerJob = null
        castSessionManagerListener?.let { listener ->
            try {
                val castContext = CastContext.getSharedInstance()
                castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
            } catch (_: Exception) {}
        }
        MediaControllerHelper.releaseFuture(controllerFuture)
        controllerFuture = null
        mediaController = null
        if (activeActivityRef?.get() == this) {
            activeActivityRef = null
        }
        super.onDestroy()
    }

    override fun finish() {
        isPlayerForeground = false
        mediaController?.clearVideoSurface()
        if (activeActivityRef?.get() == this) {
            activeActivityRef = null
        }
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        var isPlayerForeground by mutableStateOf(false)
            internal set

        private var activeActivityRef: java.lang.ref.WeakReference<LocalPlayerActivity>? = null

        fun isPlayerActivityForeground(): Boolean {
            val activity = activeActivityRef?.get()
            return isPlayerForeground || (activity != null && !activity.isFinishing && !activity.isDestroyed &&
                activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        }

        fun isPlayerActivityActive(): Boolean = isPlayerActivityForeground()

        fun finishBackgroundPlayer(exceptInfoHash: String? = null) {
            activeActivityRef?.get()?.let { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    val currentHash = activity.torrentInfoHash
                    if (exceptInfoHash.isNullOrBlank() || !currentHash.equals(exceptInfoHash, ignoreCase = true)) {
                        val isPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity.isInPictureInPictureMode
                        if (isPip || activity.lifecycle.currentState < androidx.lifecycle.Lifecycle.State.RESUMED) {
                            Log.i(TAG, "Finishing background/PiP LocalPlayerActivity (currentHash=$currentHash, exceptHash=$exceptInfoHash)")
                            activity.finish()
                        }
                    }
                }
            }
        }

        val EPISODE_PATTERN = Regex("""\bS\d{2}E\d{2}\b""", RegexOption.IGNORE_CASE)
        const val EXTRA_FILE_PATH = "verified_media_file_path"
        const val EXTRA_STREAM_URL = "verified_media_stream_url"
        const val EXTRA_CAST_URL = "verified_media_cast_url"
        const val EXTRA_INFO_HASH = "torrent_info_hash"
        const val EXTRA_PARTIAL_TORRENT_STREAM = "partial_torrent_stream"
        const val EXTRA_CAST_ENABLED = "torrent_cast_enabled"
        const val EXTRA_MIME_TYPE = "torrent_media_mime_type"
        const val EXTRA_TITLE = "torrent_media_title"
        const val EXTRA_SERIES_ID = "extra_series_id"
        const val EXTRA_SEASON_NUMBER = "extra_season_number"
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_NEXT_EPISODE_INFO = "extra_next_episode_info"
        const val EXTRA_REMOTE_SERVER_ID = "extra_remote_server_id"
        const val EXTRA_REMOTE_FILE_PATH = "extra_remote_file_path"
        const val EXTRA_REMOTE_SERVER_JSON = "extra_remote_server_json"
        const val EXTRA_POSTER_URL = "extra_poster_url"
        const val EXTRA_FROM_PIP = "extra_from_pip"
        const val EXTRA_MEDIA_ID = "extra_media_id"
        private const val TAG = "LocalPlayerActivity"

        fun extractSeasonAndEpisode(vararg candidates: String?): Pair<Int, Int> {
            for (candidate in candidates) {
                if (candidate.isNullOrBlank()) continue
                val sMatch = Regex("""\bS(\d{1,2})[\s.]?E(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(candidate)
                    ?: Regex("""\b(\d{1,2})x(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(candidate)
                if (sMatch != null) {
                    val season = sMatch.groupValues[1].toIntOrNull() ?: 0
                    val episode = sMatch.groupValues[2].toIntOrNull() ?: 0
                    if (season > 0 && episode > 0) {
                        return Pair(season, episode)
                    }
                }
            }
            return Pair(0, 0)
        }

        fun extractCleanSearchTitles(rawTitle: String): List<String> {
            if (rawTitle.isBlank()) return emptyList()

            // 1. Strip video extensions
            var text = rawTitle.replace(Regex("""\.(mp4|mkv|avi|mov|wmv|webm|ts|m4v)$""", RegexOption.IGNORE_CASE), "")

            // 2. Strip quality, codec, release tags and everything that follows them
            text = text.replace(
                Regex(
                    """\b(1080p|720p|480p|2160p|4k|uhd|bluray|blu-ray|web-dl|webrip|web|dvdrip|dvd|hdtv|h264|x264|h265|x265|hevc|avc|10bit|aac|ac3|dts|ddp5\.?1|cakes|eztv|yts|yify|rarbg|ettv|galaxytv|tgx)\b.*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )

            // 3. Strip bracketed release tags
            text = text.replace(Regex("""\[.*?\]|\(.*?\)|[\{\}]"""), " ")

            // 4. Split by episode tags (e.g. S38E01, 38x01) or major title separators (- or /)
            val chunks = text.split(Regex("""\bS\d{1,2}[\s.]?E\d{1,2}\b|\b\d{1,2}x\d{1,2}\b|[-/]""", RegexOption.IGNORE_CASE))

            val results = mutableListOf<String>()
            for (chunk in chunks) {
                val cleanChunk = chunk
                    .replace(Regex("""\b(19\d\d|20\d\d)\b.*$"""), "")
                    .replace(Regex("""[._]"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (cleanChunk.length >= 2 && !results.contains(cleanChunk)) {
                    results.add(cleanChunk)
                }
            }

            // Also include the full string without episode tags if not already present
            val fullClean = text
                .replace(Regex("""\bS\d{1,2}[\s.]?E\d{1,2}\b|\b\d{1,2}x\d{1,2}\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""[._]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (fullClean.length >= 2 && !results.contains(fullClean)) {
                results.add(fullClean)
            }

            return results
        }

        fun normalizeTitleForComparison(title: String): String {
            return title
                .lowercase(Locale.ROOT)
                .replace(Regex("""\b(19\d\d|20\d\d)\b"""), " ")
                .replace(Regex("""[^a-z0-9]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        fun extractYearFromRelease(release: String): Int? {
            return Regex("""\b(19\d\d|20\d\d)\b""").find(release)?.value?.toIntOrNull()
        }

        fun extractTitleFromRelease(release: String): String {
            val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(release)
            if (yearMatch != null) {
                return release.substring(0, yearMatch.range.first)
            }
            val qualityMatch = Regex("""\b(1080p|720p|480p|2160p|4k|bluray|web-dl|webrip|dvdrip|h264|x264|hevc|x265)\b""", RegexOption.IGNORE_CASE).find(release)
            if (qualityMatch != null) {
                return release.substring(0, qualityMatch.range.first)
            }
            return release.substringBeforeLast('.')
        }

        fun filterFallbackResults(
            rawResults: List<OpenSubtitleResult>,
            expectedTitle: String,
            expectedYear: Int?,
            expectedSeason: Int? = null,
            expectedEpisode: Int? = null
        ): List<OpenSubtitleResult> {
            if (rawResults.isEmpty()) return emptyList()

            val normalizedExpectedTitle = normalizeTitleForComparison(expectedTitle)

            return rawResults.filter { result ->
                val rawResultTitle = result.featureTitle
                    ?: extractTitleFromRelease(result.release.ifBlank { result.fileName })
                val normalizedResultTitle = normalizeTitleForComparison(rawResultTitle)

                if (normalizedExpectedTitle.isNotBlank()) {
                    val isTitleMatch = normalizedResultTitle == normalizedExpectedTitle ||
                            normalizedResultTitle.contains(normalizedExpectedTitle) ||
                            normalizedExpectedTitle.contains(normalizedResultTitle)
                    if (!isTitleMatch) {
                        Log.d(TAG, "Fallback subtitle search filtered out result: title '$normalizedResultTitle' != '$normalizedExpectedTitle'")
                        return@filter false
                    }
                }

                if (expectedSeason != null && expectedEpisode != null) {
                    val fullReleaseText = "${result.release} ${result.fileName} ${result.featureTitle.orEmpty()}"
                    val sMatch = Regex("""\bS(\d{1,2})[\s.]?E(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(fullReleaseText)
                        ?: Regex("""\b(\d{1,2})x(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(fullReleaseText)
                    if (sMatch != null) {
                        val s = sMatch.groupValues[1].toIntOrNull()
                        val e = sMatch.groupValues[2].toIntOrNull()
                        if (s != null && e != null && (s != expectedSeason || e != expectedEpisode)) {
                            Log.d(TAG, "Fallback subtitle search filtered out result: episode S${s}E${e} != S${expectedSeason}E${expectedEpisode}")
                            return@filter false
                        }
                    }
                }

                val resultYear = result.featureYear
                    ?: extractYearFromRelease(result.release.ifBlank { result.fileName })

                if (resultYear != null && expectedYear != null && kotlin.math.abs(resultYear - expectedYear) > 1) {
                    Log.d(TAG, "Fallback subtitle search filtered out result: year $resultYear != $expectedYear")
                    return@filter false
                }

                true
            }
        }
    }
}

@Composable
fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val isTv = LocalContext.current.isTvDevice()
    var isFocused by remember { mutableStateOf(false) }

    val itemModifier = modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .onFocusChanged { isFocused = it.isFocused }
        .background(if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f) else Color.Transparent, CircleShape)

    if (isTv) {
        TvIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = itemModifier
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
    } else {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = itemModifier
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun AdaptiveOptionItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .background(containerColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
fun AdaptiveChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .background(containerColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
fun PlayerControlsOverlay(
    controller: Player,
    mediaTitle: String,
    showStats: Boolean,
    onToggleStats: () -> Unit,
    onShowSubtitles: () -> Unit,
    onSearchSubtitles: () -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onShowCast: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isSliderFocused by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    var quickSeekDelta by remember { mutableIntStateOf(0) }
    var showQuickSeek by remember { mutableStateOf(false) }
    var quickSeekTriggerTime by remember { mutableLongStateOf(0L) }

    var doubleTapSeekDelta by remember { mutableIntStateOf(0) }
    var doubleTapTriggerTime by remember { mutableLongStateOf(0L) }

    val isTv = LocalContext.current.isTvDevice()

    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val statsFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val subtitlesFocusRequester = remember { FocusRequester() }
    val styleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val sliderFocusRequester = remember { FocusRequester() }

    var isPlaying by remember(controller) { mutableStateOf(controller.isPlaying) }
    var currentPosition by remember(controller) { mutableLongStateOf(controller.currentPosition) }
    var duration by remember(controller) { mutableLongStateOf(controller.duration) }
    var playbackTracks by remember(controller) { mutableStateOf(controller.currentTracks) }
    var isCasting by remember(controller) {
        mutableStateOf(controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE)
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onTracksChanged(tracks: Tracks) {
                playbackTracks = tracks
            }
            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                isCasting = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = controller.duration
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller, isPlaying) {
        while (true) {
            currentPosition = controller.currentPosition
            duration = controller.duration
            delay(250.milliseconds)
        }
    }

    // Auto-hide timeout resets whenever user interacts; paused while scrubbing or slider focused
    LaunchedEffect(controlsVisible, lastInteractionTime, isScrubbing, isSliderFocused) {
        if (controlsVisible && !isScrubbing && !isSliderFocused) {
            delay(6.seconds)
            if (!isScrubbing && !isSliderFocused) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(quickSeekTriggerTime) {
        if (showQuickSeek) {
            delay(2.seconds)
            showQuickSeek = false
        }
    }

    LaunchedEffect(doubleTapTriggerTime) {
        if (doubleTapSeekDelta != 0) {
            delay(800.milliseconds)
            doubleTapSeekDelta = 0
        }
    }

    LaunchedEffect(Unit) {
        if (isTv) {
            delay(150.milliseconds)
            try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val gestureModifier = if (isTv) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            controlsVisible = !controlsVisible
            lastInteractionTime = System.currentTimeMillis()
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { offset ->
                    val width = size.width
                    if (offset.x < width * 0.35f) {
                        controller.seekBack()
                        doubleTapSeekDelta = -10
                        doubleTapTriggerTime = System.currentTimeMillis()
                    } else if (offset.x > width * 0.65f) {
                        controller.seekForward()
                        doubleTapSeekDelta = 10
                        doubleTapTriggerTime = System.currentTimeMillis()
                    } else {
                        controlsVisible = !controlsVisible
                    }
                    lastInteractionTime = System.currentTimeMillis()
                },
                onTap = {
                    controlsVisible = !controlsVisible
                    lastInteractionTime = System.currentTimeMillis()
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    if (isTv && !controlsVisible) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                controller.seekBack()
                                quickSeekDelta = -10
                                showQuickSeek = true
                                quickSeekTriggerTime = System.currentTimeMillis()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                controller.seekForward()
                                quickSeekDelta = 10
                                showQuickSeek = true
                                quickSeekTriggerTime = System.currentTimeMillis()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                controlsVisible = true
                                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .then(gestureModifier)
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .padding(24.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mediaTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val hasSubtitleTracks = playbackTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                    val hasAudioTracks = playbackTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 1. Subtítulos Incrustados
                        ControlButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onShowSubtitles()
                            },
                            icon = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            enabled = hasSubtitleTracks,
                            tint = if (hasSubtitleTracks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            focusRequester = subtitlesFocusRequester
                        )
                        // 2. OpenSubtitles
                        ControlButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onSearchSubtitles()
                            },
                            icon = Icons.Default.Download,
                            contentDescription = "Search Subtitles",
                            focusRequester = searchFocusRequester
                        )
                        // 3. Estilos de Subtítulo
                        ControlButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onShowSubtitleStyle()
                            },
                            icon = Icons.Default.ColorLens,
                            contentDescription = "Subtitle Style",
                            focusRequester = styleFocusRequester
                        )
                        // 4. Pistas de Audio
                        ControlButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onShowAudioTracks()
                            },
                            icon = Icons.Default.GraphicEq,
                            contentDescription = "Audio Tracks",
                            enabled = hasAudioTracks,
                            tint = if (hasAudioTracks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            focusRequester = audioFocusRequester
                        )
                        // 5. Stats for Nerds
                        ControlButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onToggleStats()
                            },
                            icon = Icons.Default.BugReport,
                            contentDescription = "Stats",
                            tint = if (showStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            focusRequester = statsFocusRequester
                        )
                        // 6. Cast (Handheld only)
                        if (!isTv) {
                            ControlButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    onShowCast()
                                },
                                icon = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = if (isCasting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Center Bar
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            controller.seekBack()
                        },
                        icon = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        focusRequester = rewindFocusRequester,
                        iconSize = 48.dp
                    )
                    ControlButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isPlaying) {
                                isPlaying = false
                                controller.pause()
                            } else {
                                isPlaying = true
                                controller.play()
                            }
                        },
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        focusRequester = playPauseFocusRequester,
                        iconSize = 64.dp
                    )
                    ControlButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            controller.seekForward()
                        },
                        icon = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        focusRequester = forwardFocusRequester,
                        iconSize = 48.dp
                    )
                }

                // Bottom Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    val dur = duration.coerceAtLeast(1L)
                    val currentFraction = (currentPosition.coerceAtLeast(0L).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    val displayFraction = if (isScrubbing) scrubFraction else currentFraction
                    val displayPositionMs = if (isScrubbing) (scrubFraction * dur).toLong() else currentPosition.coerceAtLeast(0L)

                    Text(
                        text = "${formatTime(displayPositionMs)} / ${formatTime(dur)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    WatchProgressSlider(
                        value = displayFraction.coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            isScrubbing = true
                            scrubFraction = fraction
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onValueChangeFinished = {
                            val targetMs = (scrubFraction * dur).toLong()
                            controller.seekTo(targetMs)
                            isScrubbing = false
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(sliderFocusRequester)
                            .focusHighlight(shape = MaterialTheme.shapes.small)
                            .onFocusChanged {
                                isSliderFocused = it.isFocused
                                if (it.isFocused) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showQuickSeek && !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier = Modifier.widthIn(min = 280.dp, max = 400.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (quickSeekDelta > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (quickSeekDelta > 0) "+10s" else "-10s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val dur = duration.coerceAtLeast(1L)
                    val seekPercent = ((currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f) * 100).toInt()
                    WatchProgressBar(
                        progressPercent = seekPercent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = doubleTapSeekDelta != 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (doubleTapSeekDelta > 0) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 48.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (doubleTapSeekDelta > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (doubleTapSeekDelta > 0) "+10s" else "-10s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteCastCanvas(
    controller: Player,
    mediaTitle: String,
    posterUrl: String? = null,
    seasonNumber: Int = 0,
    episodeNumber: Int = 0,
    onDisconnect: () -> Unit,
    onShowCast: () -> Unit,
    onShowSubtitles: () -> Unit,
    onSearchSubtitles: () -> Unit,
    onShowSubtitleStyle: () -> Unit,
    onShowAudioTracks: () -> Unit,
    showStats: Boolean,
    onToggleStats: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaRouter = remember { MediaRouter.getInstance(context) }
    val selectedRoute = remember(mediaRouter.selectedRoute) { mediaRouter.selectedRoute }
    val castDevice = remember(selectedRoute) { CastDevice.getFromBundle(selectedRoute.extras) }
    val deviceName = selectedRoute.name
    val modelName = castDevice?.modelName ?: stringResource(R.string.cast_desc)

    val initialCastSession = remember {
        try {
            CastContext.getSharedInstance(context).sessionManager.currentCastSession
        } catch (_: Exception) { null }
    }
    val initialRmc = remember(initialCastSession) { initialCastSession?.remoteMediaClient }

    val initialIsPlaying = remember(controller, initialRmc) {
        val status = initialRmc?.mediaStatus
        when (status?.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> true
            MediaStatus.PLAYER_STATE_PAUSED -> false
            else -> initialRmc?.isPlaying ?: controller.isPlaying
        }
    }
    val initialDuration = remember(controller, initialRmc) {
        val dur = initialRmc?.streamDuration ?: 0L
        if (dur > 0L) dur else controller.duration
    }
    val initialPosition = remember(controller, initialRmc) {
        val pos = initialRmc?.approximateStreamPosition ?: 0L
        if (pos > 0L) pos else controller.currentPosition
    }

    var isPlaying by remember { mutableStateOf(initialIsPlaying) }
    var currentPosition by remember { mutableLongStateOf(initialPosition) }
    var duration by remember { mutableLongStateOf(initialDuration) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    DisposableEffect(controller, context) {
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                val rmc = try {
                    CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient
                } catch (_: Exception) { null }

                if (rmc == null || !rmc.hasMediaSession()) {
                    isPlaying = playing
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                val rmc = try {
                    CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient
                } catch (_: Exception) { null }

                if (rmc == null || !rmc.hasMediaSession()) {
                    duration = controller.duration
                }
            }
        }
        controller.addListener(playerListener)

        val castContext = try {
            CastContext.getSharedInstance(context)
        } catch (_: Exception) { null }

        var currentRmc = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

        val rmcCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val status = currentRmc?.mediaStatus
                if (status != null) {
                    when (status.playerState) {
                        MediaStatus.PLAYER_STATE_PLAYING -> isPlaying = true
                        MediaStatus.PLAYER_STATE_PAUSED -> isPlaying = false
                    }
                    val dur = currentRmc?.streamDuration ?: 0L
                    if (dur > 0L) duration = dur
                    val pos = currentRmc?.approximateStreamPosition ?: 0L
                    if (pos > 0L && !isScrubbing) currentPosition = pos
                }
            }
        }

        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                currentRmc?.unregisterCallback(rmcCallback)
                currentRmc = session.remoteMediaClient
                currentRmc?.registerCallback(rmcCallback)
                currentRmc?.mediaStatus?.let { status ->
                    if (status.playerState == MediaStatus.PLAYER_STATE_PLAYING) isPlaying = true
                    if (status.playerState == MediaStatus.PLAYER_STATE_PAUSED) isPlaying = false
                }
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                currentRmc?.unregisterCallback(rmcCallback)
                currentRmc = session.remoteMediaClient
                currentRmc?.registerCallback(rmcCallback)
                currentRmc?.mediaStatus?.let { status ->
                    if (status.playerState == MediaStatus.PLAYER_STATE_PLAYING) isPlaying = true
                    if (status.playerState == MediaStatus.PLAYER_STATE_PAUSED) isPlaying = false
                }
            }
            override fun onSessionEnding(session: CastSession) {
                currentRmc?.unregisterCallback(rmcCallback)
                currentRmc = null
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                currentRmc?.unregisterCallback(rmcCallback)
                currentRmc = null
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
        }

        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        currentRmc?.registerCallback(rmcCallback)

        onDispose {
            controller.removeListener(playerListener)
            currentRmc?.unregisterCallback(rmcCallback)
            castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }

    LaunchedEffect(controller, isScrubbing) {
        while (!isScrubbing) {
            val rmc = try {
                CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient
            } catch (_: Exception) { null }

            if (rmc != null && rmc.hasMediaSession()) {
                val status = rmc.mediaStatus
                if (status != null) {
                    if (status.playerState == MediaStatus.PLAYER_STATE_PLAYING && !isPlaying) {
                        isPlaying = true
                    } else if (status.playerState == MediaStatus.PLAYER_STATE_PAUSED && isPlaying) {
                        isPlaying = false
                    }
                }
                val pos = rmc.approximateStreamPosition
                if (pos > 0L) {
                    currentPosition = pos
                }
                val dur = rmc.streamDuration
                if (dur > 0L) {
                    duration = dur
                }
            } else {
                if (controller.isPlaying != isPlaying) {
                    isPlaying = controller.isPlaying
                }
                currentPosition = controller.currentPosition.coerceAtLeast(0L)
                duration = controller.duration.coerceAtLeast(0L)
            }
            delay(250.milliseconds)
        }
    }

    val togglePlayPause = {
        val rmc = try {
            CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient
        } catch (_: Exception) { null }

        if (isPlaying) {
            isPlaying = false
            if (rmc != null && rmc.hasMediaSession()) {
                rmc.pause()
            }
            controller.pause()
        } else {
            isPlaying = true
            if (rmc != null && rmc.hasMediaSession()) {
                rmc.play()
            }
            controller.play()
        }
    }

    val seekDelta = { deltaMs: Long ->
        val dur = duration.coerceAtLeast(0L)
        val newPos = (currentPosition + deltaMs).coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
        currentPosition = newPos
        controller.seekTo(newPos)
        try {
            CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient?.seek(
                MediaSeekOptions.Builder().setPosition(newPos).build()
            )
        } catch (_: Exception) {}
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isWide = screenWidth > screenHeight || (screenWidth >= 600.dp && screenHeight >= 480.dp)

        // Ambient background poster overlay with dark gradient scrim
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    )
            )
        }

        val horizontalPadding = if (isWide) 20.dp else 16.dp
        val verticalPadding = if (isWide) 8.dp else 12.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Back Button (Far Left)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2. Action buttons + Media Router customizado (Far Right - in parity with ExoPlayer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (screenWidth < 360.dp) 0.dp else 2.dp)
                ) {
                    // 1. Subtítulos Incrustados / Pistas
                    IconButton(
                        onClick = onShowSubtitles,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = stringResource(R.string.player_subtitle_tracks),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 2. OpenSubtitles
                    IconButton(
                        onClick = onSearchSubtitles,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.player_search_opensubtitles),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 3. Estilos de Subtítulo
                    IconButton(
                        onClick = onShowSubtitleStyle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = stringResource(R.string.player_subtitle_style),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 4. Pistas de Audio
                    IconButton(
                        onClick = onShowAudioTracks,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = stringResource(R.string.player_audio_tracks),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 5. Stats for Nerds
                    IconButton(
                        onClick = onToggleStats,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.player_stats_nerds),
                            tint = if (showStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 6. Media Router customizado (Rightmost - exact same spot as in ExoPlayer)
                    Surface(
                        onClick = onShowCast,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (screenWidth >= 380.dp) 8.dp else 6.dp,
                                vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CastConnected,
                                contentDescription = stringResource(R.string.cast_bottom_sheet_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            if (screenWidth >= 380.dp) {
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = if (screenWidth >= 480.dp) 90.dp else 50.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (isWide) {
                // 2-pane Landscape / Foldable Unfolded Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Artwork Poster Card
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.40f),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxHeight(0.88f)
                                .aspectRatio(2f / 3f)
                        ) {
                            if (!posterUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = mediaTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right Pane: Info, Timeline, Controls
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.60f)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Title & Episode Info
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = mediaTitle.ifBlank { stringResource(R.string.cast_bottom_sheet_title) },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            if (seasonNumber > 0 && episodeNumber > 0) {
                                Text(
                                    text = "${stringResource(R.string.tv_season_number, seasonNumber)} • ${stringResource(R.string.tv_episode_number, episodeNumber)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(
                                    text = stringResource(R.string.cast_remote_canvas_title, deviceName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Timeline & Seekbar
                        val dur = duration.coerceAtLeast(0L)
                        val pos = if (isScrubbing) (scrubFraction * dur).toLong() else currentPosition.coerceAtLeast(0L)
                        val sliderProgress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            WatchProgressSlider(
                                value = sliderProgress,
                                onValueChange = { fraction ->
                                    isScrubbing = true
                                    scrubFraction = fraction
                                },
                                onValueChangeFinished = {
                                    val targetMs = (scrubFraction * dur).toLong()
                                    currentPosition = targetMs
                                    controller.seekTo(targetMs)
                                    try {
                                        CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient?.seek(
                                            MediaSeekOptions.Builder().setPosition(targetMs).build()
                                        )
                                    } catch (_: Exception) {}
                                    isScrubbing = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(pos),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTime(dur),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Transport Controls: Rewind 10s, Play/Pause, Forward 10s
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledTonalIconButton(
                                onClick = { seekDelta(-10_000L) },
                                modifier = Modifier.size(46.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = stringResource(R.string.player_rewind_10),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            FilledIconButton(
                                onClick = togglePlayPause,
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            FilledTonalIconButton(
                                onClick = { seekDelta(10_000L) },
                                modifier = Modifier.size(46.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = stringResource(R.string.player_forward_10),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // "Ver en este teléfono" button
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cast_remote_canvas_play_locally),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout (Adaptive for standard phones and foldable cover screens)
                Spacer(modifier = Modifier.weight(0.6f))

                // Responsive Featured Media Artwork Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier
                        .fillMaxHeight(0.32f)
                        .aspectRatio(2f / 3f)
                ) {
                    if (!posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = mediaTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = mediaTitle.ifBlank { stringResource(R.string.cast_bottom_sheet_title) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (seasonNumber > 0 && episodeNumber > 0) {
                    Text(
                        text = "${stringResource(R.string.tv_season_number, seasonNumber)} • ${stringResource(R.string.tv_episode_number, episodeNumber)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = stringResource(R.string.cast_remote_canvas_title, deviceName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Timeline & Seekbar
                val dur = duration.coerceAtLeast(0L)
                val pos = if (isScrubbing) (scrubFraction * dur).toLong() else currentPosition.coerceAtLeast(0L)
                val sliderProgress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    WatchProgressSlider(
                        value = sliderProgress,
                        onValueChange = { fraction ->
                            isScrubbing = true
                            scrubFraction = fraction
                        },
                        onValueChangeFinished = {
                            val targetMs = (scrubFraction * dur).toLong()
                            currentPosition = targetMs
                            controller.seekTo(targetMs)
                            try {
                                CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient?.seek(
                                    MediaSeekOptions.Builder().setPosition(targetMs).build()
                                )
                            } catch (_: Exception) {}
                            isScrubbing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(pos),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(dur),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Transport Controls: Rewind 10s, Play/Pause, Forward 10s
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = { seekDelta(-10_000L) },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.player_rewind_10),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    FilledIconButton(
                        onClick = togglePlayPause,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause_desc) else stringResource(R.string.play_desc),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    FilledTonalIconButton(
                        onClick = { seekDelta(10_000L) },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.player_forward_10),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // "Ver en este teléfono" button
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.cast_remote_canvas_play_locally),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun StatsForNerdsOverlay(
    controller: Player,
    torrentInfoHash: String = "",
    torrentDownloadDao: TorrentDownloadDao? = null,
    modifier: Modifier = Modifier
) {
    val isCasting = controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
    var line1 by remember { mutableStateOf("") }
    var line2 by remember { mutableStateOf("") }
    var line3 by remember { mutableStateOf("") }
    var line4 by remember { mutableStateOf("") }
    var lineTorrent by remember { mutableStateOf("") }

    LaunchedEffect(controller, isCasting, torrentInfoHash) {
        while (true) {
            if (isCasting) {
                val castSession = CastContext.getSharedInstance()?.sessionManager?.currentCastSession
                val dev = castSession?.castDevice
                val rmc = castSession?.remoteMediaClient

                line1 = "Dispositivo: ${dev?.friendlyName ?: "Cast"} (${dev?.modelName ?: "Google Cast"})"
                val stateStr = when (rmc?.playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING -> "Playing"
                    MediaStatus.PLAYER_STATE_PAUSED -> "Paused"
                    MediaStatus.PLAYER_STATE_BUFFERING -> "Buffering"
                    else -> "Conectado"
                }
                val ip = dev?.inetAddress?.hostAddress ?: "Red local"
                val speed = rmc?.mediaStatus?.playbackRate ?: 1.0
                val vol = ((rmc?.mediaStatus?.streamVolume ?: 1.0) * 100).toInt()
                line2 = "Estado: $stateStr (${speed}x) · Vol: $vol% · IP: $ip"

                val contentUrl = rmc?.mediaInfo?.contentUrl ?: rmc?.mediaInfo?.contentId.orEmpty()
                val mime = rmc?.mediaInfo?.contentType ?: "video/mp4"
                line3 = if (contentUrl.isNotBlank()) "Formato: $mime · URL: $contentUrl" else "Formato: $mime"

                val activeTrackIds = rmc?.mediaStatus?.activeTrackIds ?: longArrayOf()
                val tracks = rmc?.mediaInfo?.mediaTracks.orEmpty()
                val activeSub = tracks.firstOrNull { activeTrackIds.contains(it.id) && it.type == MediaTrack.TYPE_TEXT }
                val subLabel = activeSub?.name ?: activeSub?.language ?: "Ninguno / Nativo"
                line4 = "Subtítulo: $subLabel"
            } else {
                val videoGroup = controller.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                val vFormat = videoGroup?.getTrackFormat(0)

                val audioGroup = controller.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                val aFormat = audioGroup?.getTrackFormat(0)

                val size = controller.videoSize
                line1 = "Video: ${size.width}x${size.height} @ ${vFormat?.frameRate?.toInt() ?: 0}fps (${vFormat?.codecs ?: vFormat?.sampleMimeType ?: "N/A"})"
                line2 = "Audio: ${aFormat?.sampleMimeType ?: "N/A"} (${aFormat?.channelCount ?: 0} ch)"
                val buf = (controller.bufferedPosition - controller.currentPosition).coerceAtLeast(0L) / 1000L
                line3 = "Buffer: ${buf}s buffered"
                line4 = ""
            }

            if (torrentInfoHash.isNotBlank() && torrentDownloadDao != null) {
                try {
                    val torrent = torrentDownloadDao.get(torrentInfoHash)
                    if (torrent != null) {
                        lineTorrent = "Torrent: ${torrent.progressPercent}% verificado · Semillas: ${torrent.numSeeds} | Pares: ${torrent.numPeers} (${torrent.state})"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore non-cancellation errors
                }
            }

            delay(1.seconds)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isCasting) "Stats for Nerds (Google Cast)" else "Stats for Nerds (ExoPlayer)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (line1.isNotBlank()) Text(line1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (line2.isNotBlank()) Text(line2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (line3.isNotBlank()) Text(line3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (line4.isNotBlank()) Text(line4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (lineTorrent.isNotBlank()) Text(lineTorrent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PlayerBottomSheetContent(
    state: PlayerBottomSheetState,
    onDismiss: () -> Unit,
    themeMode: Int? = null
) {
    val context = LocalContext.current
    val effectiveThemeMode = remember(context, themeMode) {
        themeMode ?: PreferenceManager(context).getTheme()
    }
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        TvLaTorrentolaTheme(themeMode = effectiveThemeMode) {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        BottomSheetInnerContent(state = state, onDismiss = onDismiss)
                    }
                }
            }
        }
    } else {
        LaTorrentolaTheme(themeMode = effectiveThemeMode) {
            PlayerMobileBottomSheet(state = state, onDismiss = onDismiss)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMobileBottomSheet(
    state: PlayerBottomSheetState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BottomSheetInnerContent(state = state, onDismiss = onDismiss)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BottomSheetInnerContent(
    state: PlayerBottomSheetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }
    
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state) {
                        is PlayerBottomSheetState.Tracks -> {
                            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            val firstFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(Unit) {
                                if (isTv) {
                                    delay(150.milliseconds)
                                    try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }

                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(state.options) { index, option ->
                                    AdaptiveOptionItem(
                                        selected = option.isSelected,
                                        label = option.label,
                                        onClick = {
                                            state.onSelected(option)
                                            onDismiss()
                                        },
                                        focusRequester = if (index == 0) firstFocusRequester else null
                                    )
                                }
                            }
                        }
                        is PlayerBottomSheetState.AdvancedSubtitleStyle -> {
                            var selectedFont by remember { mutableStateOf(state.currentFontName) }
                            var selectedSizeFraction by remember { mutableFloatStateOf(state.currentFontSizeFraction) }
                            var selectedFgColor by remember { mutableIntStateOf(state.currentForegroundColor) }
                            var selectedBgColor by remember { mutableIntStateOf(state.currentBackgroundColor) }
                            var selectedEdgeType by remember { mutableIntStateOf(state.currentEdgeType) }
                            var selectedBottomOffset by remember { mutableFloatStateOf(state.currentBottomOffset) }

                            var showColorPickerForFg by remember { mutableStateOf(false) }
                            var showColorPickerForBg by remember { mutableStateOf(false) }

                            val fontResolver = LocalFontFamilyResolver.current
                            val fontFamily = FontFamily(
                                Font(
                                    googleFont = GoogleFont(selectedFont),
                                    fontProvider = fontProvider
                                )
                            )
                            val typeface = fontResolver.resolve(fontFamily).value as? android.graphics.Typeface

                            // Trigger live preview updates on player background
                            LaunchedEffect(selectedFont, selectedSizeFraction, selectedFgColor, selectedBgColor, selectedEdgeType, selectedBottomOffset) {
                                state.onLiveChange(selectedFont, selectedSizeFraction, selectedFgColor, selectedBgColor, selectedEdgeType, selectedBottomOffset)
                            }

                            Text(
                                text = "Subtitle Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Visor / Compact Preview Area (Realistic text size & offset)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(65.dp)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { context ->
                                        val view = SubtitleView(context)
                                        view.apply {
                                            val sp = when {
                                                selectedSizeFraction <= 0.045f -> 14f
                                                selectedSizeFraction <= 0.06f -> 18f
                                                selectedSizeFraction <= 0.075f -> 24f
                                                else -> 30f
                                            }
                                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp)
                                            setBottomPaddingFraction(selectedBottomOffset)
                                            setCues(listOf(Cue.Builder().setText("Sample Subtitle / Vista Previa").build()))
                                        }
                                    },
                                    update = { subtitleView ->
                                        val sp = when {
                                            selectedSizeFraction <= 0.045f -> 14f
                                            selectedSizeFraction <= 0.06f -> 18f
                                            selectedSizeFraction <= 0.075f -> 24f
                                            else -> 30f
                                        }
                                        subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp)
                                        subtitleView.setBottomPaddingFraction(selectedBottomOffset)
                                        subtitleView.setStyle(
                                            CaptionStyleCompat(
                                                selectedFgColor,
                                                selectedBgColor,
                                                android.graphics.Color.TRANSPARENT,
                                                selectedEdgeType,
                                                android.graphics.Color.BLACK,
                                                typeface
                                            )
                                        )
                                        subtitleView.setCues(listOf(Cue.Builder().setText("Sample Subtitle / Vista Previa").build()))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(60.dp)
                                )
                            }

                            val firstStyleFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(Unit) {
                                if (isTv) {
                                    delay(150.milliseconds)
                                    try { firstStyleFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }

                            // Controls in a scrollable Column
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Font Family
                                Text("Font Family", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(GOOGLE_SUBTITLE_FONTS) { index, fontName ->
                                        val isSelected = fontName == selectedFont
                                        AdaptiveChoiceChip(
                                            selected = isSelected,
                                            onClick = { selectedFont = fontName },
                                            label = fontName,
                                            focusRequester = if (index == 0) firstStyleFocusRequester else null
                                        )
                                    }
                                }

                                // 2. Font Size
                                Text("Text Size", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val sizes = listOf(
                                        "Small" to 0.04f,
                                        "Medium" to 0.0533f,
                                        "Large" to 0.067f,
                                        "X-Large" to 0.08f
                                    )
                                    sizes.forEach { (label, fraction) ->
                                        val isSelected = abs(selectedSizeFraction - fraction) < 0.005f
                                        AdaptiveChoiceChip(
                                            selected = isSelected,
                                            onClick = { selectedSizeFraction = fraction },
                                            label = label
                                        )
                                    }
                                }

                                // 3. Vertical Position (Offset)
                                Text("Vertical Position (Offset)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val offsets = listOf(
                                        "Low" to 0.02f,
                                        "Default" to 0.08f,
                                        "Medium Up" to 0.15f,
                                        "High" to 0.25f
                                    )
                                    items(offsets) { (label, offsetVal) ->
                                        val isSelected = abs(selectedBottomOffset - offsetVal) < 0.01f
                                        AdaptiveChoiceChip(
                                            selected = isSelected,
                                            onClick = { selectedBottomOffset = offsetVal },
                                            label = label
                                        )
                                    }
                                }

                                // 4. Colors (Text & Background)
                                Text("Colors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { showColorPickerForFg = true },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(Color(selectedFgColor), CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            )
                                            Text("Text Color", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }

                                    Surface(
                                        onClick = { showColorPickerForBg = true },
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .background(if (selectedBgColor == 0) Color.Transparent else Color(selectedBgColor), CircleShape)
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            )
                                            Text("Background", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                // 5. Edge Type
                                Text("Border Edge", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val edges = listOf(
                                        "None" to CaptionStyleCompat.EDGE_TYPE_NONE,
                                        "Outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                        "Drop Shadow" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                        "Raised" to CaptionStyleCompat.EDGE_TYPE_RAISED,
                                        "Depressed" to CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                                    )
                                    items(edges) { (label, typeVal) ->
                                        val isSelected = selectedEdgeType == typeVal
                                        AdaptiveChoiceChip(
                                            selected = isSelected,
                                            onClick = { selectedEdgeType = typeVal },
                                            label = label
                                        )
                                    }
                                }
                            }

                            // Fixed Bottom Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    state.onCancel()
                                }) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    state.onSave(selectedFont, selectedSizeFraction, selectedFgColor, selectedBgColor, selectedEdgeType, selectedBottomOffset)
                                }) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Primary Color Picker Dialogs
                            if (showColorPickerForFg) {
                                ColorPickerDialog(
                                    title = "Select Text Color",
                                    colors = listOf(
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.YELLOW,
                                        android.graphics.Color.CYAN,
                                        android.graphics.Color.GREEN,
                                        android.graphics.Color.MAGENTA,
                                        android.graphics.Color.BLACK
                                    ),
                                    selectedColor = selectedFgColor,
                                    onColorSelected = {
                                        selectedFgColor = it
                                        showColorPickerForFg = false
                                    },
                                    onDismiss = { showColorPickerForFg = false }
                                )
                            }

                            if (showColorPickerForBg) {
                                ColorPickerDialog(
                                    title = "Select Background Color",
                                    colors = listOf(
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.parseColor("#80000000"),
                                        android.graphics.Color.BLACK,
                                        android.graphics.Color.parseColor("#80333333"),
                                        android.graphics.Color.parseColor("#80000280")
                                    ),
                                    selectedColor = selectedBgColor,
                                    onColorSelected = {
                                        selectedBgColor = it
                                        showColorPickerForBg = false
                                    },
                                    onDismiss = { showColorPickerForBg = false }
                                )
                            }
                        }
                        is PlayerBottomSheetState.SubtitleResults -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.opensubtitles_results_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (state.currentFileName.isNotBlank()) {
                                    Text(
                                        text = state.currentFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val firstResultFocusRequester = remember { FocusRequester() }

                            LaunchedEffect(Unit) {
                                if (isTv) {
                                    delay(150.milliseconds)
                                    try { firstResultFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(state.results) { index, result ->
                                    val feature = result.featureTitle?.takeIf(String::isNotBlank)
                                    val textToScan = "${result.release} ${result.fileName} ${result.featureTitle.orEmpty()}"
                                    val flag = getLanguageFlag(result.language, textToScan)
                                    val label = listOfNotNull(
                                        flag,
                                        result.release.takeIf(String::isNotBlank) ?: result.fileName.takeIf(String::isNotBlank),
                                        feature
                                    ).joinToString(" · ")

                                    AdaptiveOptionItem(
                                        selected = false,
                                        label = label,
                                        onClick = { state.onSelected(result) },
                                        focusRequester = if (index == 0) firstResultFocusRequester else null
                                    )
                                }
                            }
                        }
                        is PlayerBottomSheetState.Message -> {
                            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(state.message)
                            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                                Text("OK")
                            }
                        }
                        is PlayerBottomSheetState.Progress -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text(state.message, textAlign = TextAlign.Center)
                        }
        }
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.forEach { colorVal ->
                        val isSelected = selectedColor == colorVal
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (colorVal == 0) Color.Transparent else Color(colorVal), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .focusHighlight(shape = CircleShape)
                                .clickable { onColorSelected(colorVal) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (colorVal == android.graphics.Color.WHITE) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun getLanguageFlag(langCode: String?, textToScan: String? = null): String {
    val cleanLang = langCode?.lowercase(Locale.ROOT)?.trim().orEmpty()
    val cleanText = textToScan?.lowercase(Locale.ROOT).orEmpty()
    val combined = "$cleanLang $cleanText"

    // 1. Spanish: Latin American (🇦🇷) vs Plain / Spain (🇪🇸)
    val isSpanish = cleanLang.startsWith("es") || cleanLang == "spa" || cleanLang == "spanish" ||
            combined.contains("esp") || combined.contains("spa") || combined.contains("castellano") || combined.contains("latino")

    if (isSpanish) {
        val isLatinAmerican = cleanLang.contains("419") || cleanLang.contains("ar") || cleanLang.contains("mx") ||
                cleanLang.contains("cl") || cleanLang.contains("co") || cleanLang.contains("pe") || cleanLang.contains("ve") ||
                cleanLang.contains("lat") || combined.contains("latino") || combined.contains("latin") ||
                combined.contains("latam") || combined.contains("hispanoamerica") || combined.contains("es-419")

        return if (isLatinAmerican) "🇦🇷" else "🇪🇸"
    }

    // 2. Portuguese: Brazil (🇧🇷) vs Portugal / Plain (🇵🇹)
    val isPortuguese = cleanLang.startsWith("pt") || cleanLang == "por" || cleanLang == "pob" || cleanLang == "portuguese" || combined.contains("portu")
    if (isPortuguese) {
        val isBrazil = cleanLang.contains("br") || cleanLang == "pob" || combined.contains("brazil") || combined.contains("brasil")
        return if (isBrazil) "🇧🇷" else "🇵🇹"
    }

    // 3. English: UK (🇬🇧) vs US / Plain (🇺🇸)
    val isEnglish = cleanLang.startsWith("en") || cleanLang == "eng" || cleanLang == "english" || combined.contains("ingl")
    if (isEnglish) {
        val isUk = cleanLang.contains("gb") || cleanLang.contains("uk") || combined.contains("british")
        return if (isUk) "🇬🇧" else "🇺🇸"
    }

    // 4. Chinese: Taiwan / Traditional (🇹🇼) vs China / Plain (🇨🇳)
    val isChinese = cleanLang.startsWith("zh") || cleanLang == "zho" || cleanLang == "chi" || cleanLang == "chinese" || combined.contains("chino")
    if (isChinese) {
        val isTaiwan = cleanLang.contains("tw") || cleanLang.contains("hk") || cleanLang.contains("hant") || combined.contains("taiwan")
        return if (isTaiwan) "🇹🇼" else "🇨🇳"
    }

    // 5. Comprehensive language / country lookup table
    return when {
        cleanLang.startsWith("fr") || cleanLang == "fra" || cleanLang == "fre" || combined.contains("french") || combined.contains("franc") -> "🇫🇷"
        cleanLang.startsWith("de") || cleanLang == "deu" || cleanLang == "ger" || combined.contains("german") || combined.contains("deutsch") || combined.contains("alem") -> "🇩🇪"
        cleanLang.startsWith("it") || cleanLang == "ita" || combined.contains("italian") || combined.contains("italiano") -> "🇮🇹"
        cleanLang.startsWith("ja") || cleanLang == "jpn" || combined.contains("japanese") || combined.contains("jap") -> "🇯🇵"
        cleanLang.startsWith("ru") || cleanLang == "rus" || combined.contains("russian") || combined.contains("ruso") -> "🇷🇺"
        cleanLang.startsWith("ko") || cleanLang == "kor" || combined.contains("korean") || combined.contains("coreano") -> "🇰🇷"
        cleanLang.startsWith("ar") || cleanLang == "ara" || combined.contains("arabic") || combined.contains("arabe") -> "🇸🇦"
        cleanLang.startsWith("hi") || cleanLang == "hin" || combined.contains("hindi") -> "🇮🇳"
        cleanLang.startsWith("bn") || cleanLang == "ben" || combined.contains("bengali") -> "🇧🇩"
        cleanLang.startsWith("nl") || cleanLang == "nld" || cleanLang == "dut" || combined.contains("dutch") || combined.contains("holand") -> "🇳🇱"
        cleanLang.startsWith("pl") || cleanLang == "pol" || combined.contains("polish") || combined.contains("polaco") -> "🇵🇱"
        cleanLang.startsWith("tr") || cleanLang == "tur" || combined.contains("turkish") || combined.contains("turco") -> "🇹🇷"
        cleanLang.startsWith("sv") || cleanLang == "swe" || combined.contains("swedish") || combined.contains("sueco") -> "🇸🇪"
        cleanLang.startsWith("no") || cleanLang == "nor" || cleanLang == "nob" || cleanLang == "nno" || combined.contains("norwegian") || combined.contains("noruego") -> "🇳🇴"
        cleanLang.startsWith("da") || cleanLang == "dan" || combined.contains("danish") || combined.contains("danes") -> "🇩🇰"
        cleanLang.startsWith("fi") || cleanLang == "fin" || combined.contains("finnish") || combined.contains("finland") -> "🇫🇮"
        cleanLang.startsWith("el") || cleanLang == "ell" || cleanLang == "gre" || combined.contains("greek") || combined.contains("griego") -> "🇬🇷"
        cleanLang.startsWith("he") || cleanLang == "heb" || combined.contains("hebrew") || combined.contains("hebreo") -> "🇮🇱"
        cleanLang.startsWith("hu") || cleanLang == "hun" || combined.contains("hungarian") || combined.contains("hungaro") -> "🇭🇺"
        cleanLang.startsWith("cs") || cleanLang == "ces" || cleanLang == "cze" || combined.contains("czech") || combined.contains("checo") -> "🇨🇿"
        cleanLang.startsWith("ro") || cleanLang == "ron" || cleanLang == "rum" || combined.contains("romanian") || combined.contains("rumano") -> "🇷🇴"
        cleanLang.startsWith("uk") || cleanLang == "ukr" || combined.contains("ukrainian") || combined.contains("ucraniano") -> "🇺🇦"
        cleanLang.startsWith("th") || cleanLang == "tha" || combined.contains("thai") || combined.contains("tailand") -> "🇹🇭"
        cleanLang.startsWith("vi") || cleanLang == "vie" || combined.contains("vietnamese") || combined.contains("vietnam") -> "🇻🇳"
        cleanLang.startsWith("id") || cleanLang == "ind" || combined.contains("indonesian") -> "🇮🇩"
        cleanLang.startsWith("ms") || cleanLang == "msa" || cleanLang == "may" || combined.contains("malay") -> "🇲🇾"
        cleanLang.startsWith("fil") || cleanLang == "tl" || cleanLang == "tgl" || combined.contains("tagalog") || combined.contains("filipino") -> "🇵🇭"
        cleanLang.startsWith("fa") || cleanLang == "fas" || cleanLang == "per" || combined.contains("persian") || combined.contains("farsi") -> "🇮🇷"
        cleanLang.startsWith("hr") || cleanLang == "hrv" || combined.contains("croatian") || combined.contains("croata") -> "🇭🇷"
        cleanLang.startsWith("sr") || cleanLang == "srp" || combined.contains("serbian") || combined.contains("serbio") -> "🇸🇷"
        cleanLang.startsWith("bs") || cleanLang == "bos" || combined.contains("bosnian") -> "🇧🇦"
        cleanLang.startsWith("sk") || cleanLang == "slk" || cleanLang == "slo" || combined.contains("slovak") || combined.contains("eslovaco") -> "🇸🇰"
        cleanLang.startsWith("sl") || cleanLang == "slv" || combined.contains("slovenian") || combined.contains("esloveno") -> "🇸🇮"
        cleanLang.startsWith("bg") || cleanLang == "bul" || combined.contains("bulgarian") || combined.contains("bulgaro") -> "🇧🇬"
        cleanLang.startsWith("ca") || cleanLang == "cat" || combined.contains("catalan") -> "🇪🇸"
        cleanLang.startsWith("gl") || cleanLang == "glg" || combined.contains("galician") || combined.contains("gallego") -> "🇪🇸"
        cleanLang.startsWith("eu") || cleanLang == "eus" || cleanLang == "baq" || combined.contains("basque") || combined.contains("vasco") -> "🇪🇸"
        cleanLang.startsWith("is") || cleanLang == "isl" || cleanLang == "ice" || combined.contains("icelandic") || combined.contains("islandes") -> "🇮🇸"
        cleanLang.startsWith("et") || cleanLang == "est" || combined.contains("estonian") -> "🇪🇪"
        cleanLang.startsWith("lv") || cleanLang == "lav" || combined.contains("latvian") -> "🇱🇻"
        cleanLang.startsWith("lt") || cleanLang == "lit" || combined.contains("lithuanian") -> "🇱🇹"
        cleanLang.startsWith("sw") || cleanLang == "swa" || combined.contains("swahili") -> "🇰🇪"
        cleanLang.startsWith("af") || cleanLang == "afr" || combined.contains("afrikaans") -> "🇿🇦"
        cleanLang.startsWith("sq") || cleanLang == "sqi" || cleanLang == "alb" || combined.contains("albanian") -> "🇦🇱"
        cleanLang.startsWith("mk") || cleanLang == "mkd" || cleanLang == "mac" || combined.contains("macedonian") -> "🇲🇰"
        cleanLang.startsWith("ka") || cleanLang == "kat" || cleanLang == "geo" || combined.contains("georgian") -> "🇬🇪"
        cleanLang.startsWith("hy") || cleanLang == "hye" || cleanLang == "arm" || combined.contains("armenian") -> "🇦🇲"
        cleanLang.startsWith("az") || cleanLang == "aze" || combined.contains("azerbaijani") -> "🇦🇿"
        cleanLang.startsWith("kk") || cleanLang == "kaz" || combined.contains("kazakh") -> "🇰🇿"
        cleanLang.startsWith("uz") || cleanLang == "uzb" || combined.contains("uzbek") -> "🇺🇿"
        cleanLang.startsWith("mn") || cleanLang == "mon" || combined.contains("mongolian") -> "🇲🇳"
        cleanLang.startsWith("ur") || cleanLang == "urd" || combined.contains("urdu") -> "🇵🇰"
        cleanLang.startsWith("ta") || cleanLang == "tam" || combined.contains("tamil") -> "🇮🇳"
        cleanLang.startsWith("te") || cleanLang == "tel" || combined.contains("telugu") -> "🇮🇳"
        cleanLang.startsWith("ml") || cleanLang == "mal" || combined.contains("malayalam") -> "🇮🇳"
        cleanLang.startsWith("kn") || cleanLang == "kan" || combined.contains("kannada") -> "🇮🇳"
        cleanLang.startsWith("mr") || cleanLang == "mar" || combined.contains("marathi") -> "🇮🇳"
        cleanLang.startsWith("gu") || cleanLang == "guj" || combined.contains("gujarati") -> "🇮🇳"
        cleanLang.startsWith("pa") || cleanLang == "pan" || combined.contains("punjabi") -> "🇮🇳"
        else -> "🇺🇳"
    }
}