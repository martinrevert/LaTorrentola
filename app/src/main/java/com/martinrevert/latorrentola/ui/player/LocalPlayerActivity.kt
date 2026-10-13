@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.martinrevert.latorrentola.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.martinrevert.latorrentola.ui.components.CastCodecWarningObserver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.martinrevert.latorrentola.model.user.PlaybackProgress
import com.martinrevert.latorrentola.network.DownloadedSubtitle
import com.martinrevert.latorrentola.network.OpenSubtitleRepository
import com.martinrevert.latorrentola.network.OpenSubtitleResult
import com.martinrevert.latorrentola.network.OpenSubtitlesException
import com.martinrevert.latorrentola.network.TmdbRepository
import com.martinrevert.latorrentola.network.UserLibraryRepository
import com.martinrevert.latorrentola.service.PlaybackService
import com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer
import com.martinrevert.latorrentola.ui.components.NextEpisodeTorrentDialog
import com.martinrevert.latorrentola.ui.theme.LaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.TvLaTorrentolaTheme
import com.martinrevert.latorrentola.ui.theme.focusHighlight
import com.martinrevert.latorrentola.utils.AutoPlayQualitySelectionMethod
import com.martinrevert.latorrentola.utils.PreferenceManager
import com.martinrevert.latorrentola.utils.mediaMimeType
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    private val tvPlayerViewModel: TvPlayerViewModel by viewModels()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    private val castSubtitleUrls = mutableMapOf<String, String>()
    private var currentMediaItem: MediaItem? = null

    private var localStreamUrl = ""
    private var castStreamUrl = ""
    private var mediaTitle = ""
    private var torrentInfoHash = ""

    private var currentSeriesId = 0
    private var currentSeasonNumber = 0
    private var currentEpisodeNumber = 0
    private var currentMovieId = 0

    private var nextEpisodeSeriesId = 0
    private var nextEpisodeSeriesName = ""
    private var nextEpisodeSeasonNumber = 0
    private var nextEpisodeEpisodeNumber = 0
    private var nextEpisodeEpisodeName = ""
    private var hasNextEpisodeInfo = false
    private var nextEpisodeTriggered = false

    private var showNextEpisodeDialog by mutableStateOf(false)
    private var playerBottomSheetState by mutableStateOf<PlayerBottomSheetState?>(null)
    private var showStatsForNerds by mutableStateOf(false)
    private var customCaptionStyle by mutableStateOf<CaptionStyleCompat?>(null)
    private var customSubtitleSizeFraction by mutableFloatStateOf(0.0533f)
    private var customSubtitleBottomOffset by mutableFloatStateOf(0.08f)

    private var subtitleOperationInProgress by mutableStateOf(false)
    private var subtitleStartupCheckStarted = false

    private var pendingMediaUri: Uri? = null
    private var pendingMediaItem: MediaItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        connectToMediaSession()

        setContent {
            val themeMode = preferenceManager.getTheme()
            val isTv = isTvDevice()

            val content = @Composable {
                val controller = mediaController
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (controller != null) {
                        var showCastBottomSheet by remember { mutableStateOf(false) }

                        var deviceInfo by remember(controller) { mutableStateOf(controller.deviceInfo) }
                        DisposableEffect(controller) {
                            val listener = object : Player.Listener {
                                override fun onDeviceInfoChanged(info: DeviceInfo) {
                                    deviceInfo = info
                                }
                            }
                            controller.addListener(listener)
                            onDispose { controller.removeListener(listener) }
                        }

                        val presentationState = rememberPresentationState(controller)
                        val videoModifier = Modifier
                            .fillMaxSize()
                            .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)

                        PlayerSurface(
                            player = controller,
                            modifier = videoModifier
                        )

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
                                        prepareSubtitleTrackIfNeeded(tracks)
                                    }
                                }
                                controller.addListener(listener)
                                selectDefaultSubtitleIfNeeded(controller)
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

                            if (showStatsForNerds) {
                                StatsForNerdsOverlay(
                                    controller = controller,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .align(Alignment.TopCenter)
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
                            CastBottomSheet(
                                onDismissRequest = { showCastBottomSheet = false },
                                mediaTitle = mediaTitle
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
                                // Trigger next episode stream
                            },
                            onDismiss = {
                                showNextEpisodeDialog = false
                                finish()
                            }
                        )
                    }

                    val state = playerBottomSheetState
                    if (state != null) {
                        PlayerBottomSheetContent(
                            state = state,
                            onDismiss = { playerBottomSheetState = null }
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
        handleIntent(intent)
        val controller = mediaController
        if (controller != null) {
            setupControllerMedia(controller)
        }
    }

    private fun handleIntent(intent: Intent) {
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

        if (mediaUri != null) {
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
                } catch (e: NumberFormatException) {
                    hasNextEpisodeInfo = false
                }
            }

            val isEpisode = currentSeriesId > 0
            val mediaId = when {
                currentSeriesId > 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0 ->
                    "media_${currentSeriesId}_s${currentSeasonNumber}_e${currentEpisodeNumber}"
                currentMovieId > 0 ->
                    currentMovieId.toString()
                torrentInfoHash.isNotBlank() ->
                    torrentInfoHash
                else ->
                    mediaTitle
            }

            val itemBundle = Bundle().apply {
                putBoolean(PlaybackService.EXTRA_IS_EPISODE, isEpisode)
                if (castStreamUrl.isNotBlank()) {
                    putString(PlaybackService.EXTRA_CAST_URL, castStreamUrl)
                }
            }

            pendingMediaItem = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(mediaUri)
                .setMimeType(intent.getStringExtra(EXTRA_MIME_TYPE) ?: file?.mediaMimeType())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(mediaTitle)
                        .setExtras(itemBundle)
                        .build()
                )
                .build()
            currentMediaItem = pendingMediaItem
        }
    }

    private fun connectToMediaSession() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                if (controller != null) {
                    setupControllerMedia(controller)
                } else if (pendingMediaUri == null) {
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
                if (pendingMediaUri == null) {
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupControllerMedia(controller: MediaController) {
        lifecycleScope.launch {
            val activeItem = controller.currentMediaItem
            val newItem = pendingMediaItem

            if (newItem != null && (activeItem == null || activeItem.mediaId != newItem.mediaId)) {
                val savedProgress = userLibraryRepository.getPlaybackProgress(newItem.mediaId)
                val startPosition = if (savedProgress != null && savedProgress.positionMs > 5000L && !savedProgress.isCompleted) {
                    Log.i(TAG, "Restoring playback position: ${savedProgress.positionMs}ms for ${newItem.mediaId}")
                    savedProgress.positionMs
                } else {
                    0L
                }

                val deviceLang = Locale.getDefault().language
                val defaultTrackParams = controller.trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguage(deviceLang)
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                controller.trackSelectionParameters = defaultTrackParams

                controller.setMediaItem(newItem, startPosition)
                controller.prepare()
                controller.playWhenReady = true
            } else if (activeItem != null) {
                currentMediaItem = activeItem
                val titleFromMetadata = activeItem.mediaMetadata.title?.toString()
                if (!titleFromMetadata.isNullOrBlank()) {
                    mediaTitle = titleFromMetadata
                }
            } else if (pendingMediaUri == null) {
                finish()
                return@launch
            }

            setupPlaybackCompletionListener(controller)

            controller.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    selectDefaultSubtitleIfNeeded(controller)
                    prepareSubtitleTrackIfNeeded(tracks)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        prepareSubtitleTrackIfNeeded(controller.currentTracks)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error encountered: ${error.errorCodeName} (${error.errorCode}): ${error.message}", error)
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                        lifecycleScope.launch {
                            delay(1500L)
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

    private fun handlePlaybackEnded() {
        mediaController?.pause()
        val isTvEpisode = hasNextEpisodeInfo || (currentSeriesId > 0 && currentSeasonNumber > 0 && currentEpisodeNumber > 0)
        if (!isTvEpisode) return

        val autoPlayMode = preferenceManager.getAutoPlayQualitySelectionMethod()
        when (autoPlayMode) {
            AutoPlayQualitySelectionMethod.DO_NOTHING -> {}
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
            }
            else -> {}
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
            } catch (_: Exception) {}
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
                            Log.i(TAG, "Live updated custom TextTrackStyle on active Cast session")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to apply Cast subtitle style: ${e.message}")
        }
    }

    private fun trackLabel(format: androidx.media3.common.Format, type: PlaybackTrackType): String {
        val rawLanguage = format.language?.takeIf { it.isNotBlank() && it != "und" }
        val customLabel = format.label?.takeIf(String::isNotBlank)

        // 1. Language Detection (from language code OR customLabel/id fallback)
        val detectedLangCode = rawLanguage ?: run {
            val textToScan = "${customLabel.orEmpty()} ${format.id.orEmpty()}".lowercase(Locale.ROOT)
            when {
                textToScan.contains("esp") || textToScan.contains("spa") || textToScan.contains("latino") || textToScan.contains("castellano") -> "es"
                textToScan.contains("eng") || textToScan.contains("ingl") -> "en"
                textToScan.contains("por") || textToScan.contains("portu") -> "pt"
                textToScan.contains("jpn") || textToScan.contains("jap") -> "ja"
                textToScan.contains("fra") || textToScan.contains("fren") || textToScan.contains("fran") -> "fr"
                textToScan.contains("ger") || textToScan.contains("deu") || textToScan.contains("alem") -> "de"
                textToScan.contains("ita") -> "it"
                textToScan.contains("rus") -> "ru"
                else -> null
            }
        }

        val languageStr = detectedLangCode?.let { lang ->
            val cleanLang = lang.lowercase(Locale.ROOT)
            val locale = Locale.forLanguageTag(cleanLang)
            val displayLang = locale.getDisplayName(Locale.getDefault()).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val flag = when (cleanLang) {
                "es", "spa", "spanish" -> "🇦🇷"
                "en", "eng", "english" -> "🇺🇸"
                "pt", "por", "pob", "portuguese" -> "🇧🇷"
                "fr", "fre", "fra", "french" -> "🇫🇷"
                "de", "ger", "deu", "german" -> "🇩🇪"
                "it", "ita", "italian" -> "🇮🇹"
                "ja", "jpn", "japanese" -> "🇯🇵"
                "ru", "rus", "russian" -> "🇷🇺"
                "zh", "zho", "chi", "chinese" -> "🇨🇳"
                "ko", "kor", "korean" -> "🇰🇷"
                else -> "🌐"
            }
            if (displayLang.isNotBlank() && displayLang != cleanLang) "$flag $displayLang" else "$flag $cleanLang"
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
                languageStr ?: "🌐 Idioma Desconocido",
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
        return controller.currentTracks.groups
            .filter { it.type == media3Type }
            .flatMap { group ->
                (0 until group.length).map { index ->
                    val format = group.getTrackFormat(index)
                    PlaybackTrackOption(
                        label = trackLabel(format, type),
                        group = group.mediaTrackGroup,
                        trackIndex = index,
                        castTrackId = null,
                        isSelected = group.isTrackSelected(index)
                    )
                }
            }
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
        if (option?.group != null && option.trackIndex != null) {
            controller.trackSelectionParameters = parameters
                .setOverrideForType(TrackSelectionOverride(option.group, listOf(option.trackIndex)))
                .build()
        } else {
            controller.trackSelectionParameters = parameters.build()
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
            try {
                val isEpisode = EPISODE_PATTERN.containsMatchIn(mediaTitle) || currentSeasonNumber > 0
                val mediaType = if (isEpisode) "episode" else "movie"

                var effectiveSeriesId = currentSeriesId
                var effectiveMovieId = currentMovieId
                var effectiveSeason = currentSeasonNumber
                var effectiveEpisode = currentEpisodeNumber

                // 1. Recover IDs from Room DB if not present in Intent
                if (torrentInfoHash.isNotBlank() && effectiveSeriesId <= 0 && effectiveMovieId <= 0) {
                    try {
                        val dbRecord = torrentDownloadDao.get(torrentInfoHash)
                        if (dbRecord != null) {
                            if (dbRecord.movieId != null && dbRecord.movieId > 0) {
                                effectiveMovieId = dbRecord.movieId
                            }
                            if (dbRecord.seriesId != null && dbRecord.seriesId > 0) {
                                effectiveSeriesId = dbRecord.seriesId
                                effectiveSeason = dbRecord.seasonNumber ?: effectiveSeason
                                effectiveEpisode = dbRecord.episodeNumber ?: effectiveEpisode
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read download record for IMDb lookup: ${e.message}")
                    }
                }

                // 2. Fetch IMDb ID from TMDB repository
                var resolvedImdbId: String? = null
                try {
                    if (effectiveSeriesId > 0 && isEpisode) {
                        resolvedImdbId = tmdbRepository.getTvImdbId(effectiveSeriesId)
                    } else if (effectiveMovieId > 0) {
                        resolvedImdbId = tmdbRepository.getMovieImdbId(effectiveMovieId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch IMDb ID from TMDB repository: ${e.message}")
                }

                // 3. Fallback: Search TMDB by clean title to get the exact IMDb ID if not found
                if (resolvedImdbId.isNullOrBlank()) {
                    val ttMatch = Regex("""tt\d{7,8}""", RegexOption.IGNORE_CASE).find(mediaTitle)
                    if (ttMatch != null) {
                        resolvedImdbId = ttMatch.value
                    } else if (isEpisode) {
                        try {
                            val cleanTitle = mediaTitle.replace(EPISODE_PATTERN, "").trim()
                            val tvPage = tmdbRepository.searchTvSeries(cleanTitle)
                            val firstTv = tvPage.results?.firstOrNull()
                            if (firstTv != null && firstTv.id > 0) {
                                resolvedImdbId = tmdbRepository.getTvImdbId(firstTv.id)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to resolve IMDb ID via TMDB title search: ${e.message}")
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
                        Log.w(TAG, "OpenSubtitles searchByImdbId failed: ${e.message}")
                    }
                }

                // 5. String query search ONLY as a last resort
                if (!imdbResults.isNullOrEmpty()) {
                    results = imdbResults
                } else {
                    Log.w(TAG, "No IMDb ID results found. Falling back to text query search for: $mediaTitle")
                    val cleanTitle = mediaTitle.replace(EPISODE_PATTERN, "").trim().ifBlank { mediaTitle }
                    results = openSubtitlesRepository.search(
                        query = cleanTitle,
                        seasonNumber = effectiveSeason.takeIf { it > 0 },
                        episodeNumber = effectiveEpisode.takeIf { it > 0 },
                        type = mediaType
                    )
                }

                if (results.isNullOrEmpty()) {
                    message = getString(R.string.opensubtitles_no_results)
                }
            } catch (error: OpenSubtitlesException) {
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: Exception) {
                message = getString(R.string.opensubtitles_request_failed)
            } finally {
                subtitleOperationInProgress = false
            }

            if (!results.isNullOrEmpty()) {
                val currentFile = intent.getStringExtra(EXTRA_FILE_PATH)?.let { File(it).name }
                    ?.takeIf { it.isNotBlank() } ?: mediaTitle

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
                playerBottomSheetState = null
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

    override fun onPause() {
        saveCurrentProgress(mediaController)
        super.onPause()
    }

    override fun onStop() {
        saveCurrentProgress(mediaController)
        super.onStop()
    }

    private fun saveCurrentProgress(player: Player?) {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0L) return

        val item = p.currentMediaItem ?: return
        val mediaId = item.mediaId
        if (mediaId.isBlank() || mediaId == MediaItem.DEFAULT_MEDIA_ID) return

        val title = item.mediaMetadata.title?.toString() ?: mediaTitle
        val isEpisode = currentSeriesId > 0

        val progress = PlaybackProgress(
            mediaId = mediaId,
            title = title.ifBlank { "Media Item" },
            positionMs = pos,
            durationMs = dur,
            timestamp = System.currentTimeMillis(),
            isEpisode = isEpisode
        )

        lifecycleScope.launch {
            try {
                userLibraryRepository.savePlaybackProgress(progress)
                Log.i(TAG, "Saved progress on Activity pause/stop: $pos / $dur ms for $mediaId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playback progress", e)
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
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
        private const val TAG = "LocalPlayerActivity"
    }
}

@Composable
fun ControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
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
        .background(if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent, CircleShape)

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

@OptIn(UnstableApi::class)
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

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            if (isTv) {
                delay(100)
                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
            }
            delay(5000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (isTv && event.type == KeyEventType.KeyDown) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (!controlsVisible && (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                                keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                                keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY ||
                                keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                        controlsVisible = true
                        try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val hasSubtitleTracks = controller.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
                    val hasAudioTracks = controller.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 1. Subtítulos Incrustados
                        ControlButton(
                            onClick = onShowSubtitles,
                            icon = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            enabled = hasSubtitleTracks,
                            tint = if (hasSubtitleTracks) Color.White else Color.Gray.copy(alpha = 0.5f),
                            focusRequester = subtitlesFocusRequester
                        )
                        // 2. OpenSubtitles
                        ControlButton(
                            onClick = onSearchSubtitles,
                            icon = Icons.Default.Download,
                            contentDescription = "Search Subtitles",
                            focusRequester = searchFocusRequester
                        )
                        // 3. Estilos de Subtítulo
                        ControlButton(
                            onClick = onShowSubtitleStyle,
                            icon = Icons.Default.ColorLens,
                            contentDescription = "Subtitle Style",
                            focusRequester = styleFocusRequester
                        )
                        // 4. Pistas de Audio
                        ControlButton(
                            onClick = onShowAudioTracks,
                            icon = Icons.Default.GraphicEq,
                            contentDescription = "Audio Tracks",
                            enabled = hasAudioTracks,
                            tint = if (hasAudioTracks) Color.White else Color.Gray.copy(alpha = 0.5f),
                            focusRequester = audioFocusRequester
                        )
                        // 5. Stats for Nerds
                        ControlButton(
                            onClick = onToggleStats,
                            icon = Icons.Default.BugReport,
                            contentDescription = "Stats",
                            tint = if (showStats) Color.Green else Color.White,
                            focusRequester = statsFocusRequester
                        )
                        // 6. Cast (Handheld only)
                        if (!isTv) {
                            val isCasting = controller.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
                            ControlButton(
                                onClick = onShowCast,
                                icon = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White
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
                        onClick = { controller.seekBack() },
                        icon = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        focusRequester = rewindFocusRequester,
                        iconSize = 48.dp
                    )
                    ControlButton(
                        onClick = { if (controller.isPlaying) controller.pause() else controller.play() },
                        icon = if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        focusRequester = playPauseFocusRequester,
                        iconSize = 64.dp
                    )
                    ControlButton(
                        onClick = { controller.seekForward() },
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
                    val duration = controller.duration.coerceAtLeast(1L)
                    val pos = controller.currentPosition.coerceAtLeast(0L)

                    Text(
                        text = "${formatTime(pos)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )

                    Slider(
                        value = if (duration > 0) pos.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            controller.seekTo((fraction * duration).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(sliderFocusRequester)
                            .focusHighlight(shape = MaterialTheme.shapes.small)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun StatsForNerdsOverlay(
    controller: Player,
    modifier: Modifier = Modifier
) {
    var videoSizeText by remember { mutableStateOf("") }
    var audioText by remember { mutableStateOf("") }
    var bufferText by remember { mutableStateOf("") }

    LaunchedEffect(controller) {
        while (true) {
            val videoGroup = controller.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            val vFormat = videoGroup?.getTrackFormat(0)

            val audioGroup = controller.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            val aFormat = audioGroup?.getTrackFormat(0)

            val size = controller.videoSize
            videoSizeText = "${size.width}x${size.height} @ ${vFormat?.frameRate?.toInt() ?: 0}fps (${vFormat?.codecs ?: vFormat?.sampleMimeType ?: "N/A"})"
            audioText = "${aFormat?.sampleMimeType ?: "N/A"} (${aFormat?.channelCount ?: 0} ch)"
            val buf = (controller.bufferedPosition - controller.currentPosition).coerceAtLeast(0L) / 1000L
            bufferText = "${buf}s buffered"
            delay(1000L)
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Stats for Nerds", style = MaterialTheme.typography.titleSmall, color = Color.Green, fontWeight = FontWeight.Bold)
            Text("Video: $videoSizeText", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("Audio: $audioText", style = MaterialTheme.typography.bodySmall, color = Color.White)
            Text("Buffer: $bufferText", style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheetContent(
    state: PlayerBottomSheetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember(context) { context.isTvDevice() }

    if (isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
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
    } else {
        PlayerMobileBottomSheet(state = state, onDismiss = onDismiss)
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

@OptIn(androidx.media3.common.util.UnstableApi::class)
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
                            val isTvDevice = isTv

                            LaunchedEffect(Unit) {
                                if (isTvDevice) {
                                    delay(150)
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
                                    .background(Color.Black.copy(alpha = 0.9f))
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
                            val isTvDevice = isTv

                            LaunchedEffect(Unit) {
                                if (isTvDevice) {
                                    delay(150)
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
                                        val isSelected = Math.abs(selectedSizeFraction - fraction) < 0.005f
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
                                        val isSelected = Math.abs(selectedBottomOffset - offsetVal) < 0.01f
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
                                                    .border(1.dp, Color.Gray, CircleShape)
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
                                                    .border(1.dp, Color.Gray, CircleShape)
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val firstResultFocusRequester = remember { FocusRequester() }
                            val isTvDevice = isTv

                            LaunchedEffect(Unit) {
                                if (isTvDevice) {
                                    delay(150)
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
                                    val flag = when (result.language.lowercase(Locale.ROOT)) {
                                        "en", "eng" -> "🇺🇸"
                                        "es", "spa" -> "🇦🇷"
                                        "pt", "por", "pob" -> "🇧🇷"
                                        "fr", "fre", "fra" -> "🇫🇷"
                                        "de", "ger", "deu" -> "🇩🇪"
                                        "it", "ita" -> "🇮🇹"
                                        else -> result.language.uppercase(Locale.ROOT)
                                    }
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
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
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
                                    tint = if (colorVal == android.graphics.Color.WHITE) Color.Black else Color.White,
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
