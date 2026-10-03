package com.martinrevert.latorrentola.ui.player

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import com.google.android.gms.cast.framework.CastButtonFactory
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.network.OpenSubtitlesException
import com.martinrevert.latorrentola.network.OpenSubtitleRepository
import com.martinrevert.latorrentola.network.OpenSubtitleResult
import com.martinrevert.latorrentola.service.VerifiedTorrentHttpServer
import com.martinrevert.latorrentola.utils.mediaMimeType
import com.martinrevert.latorrentola.utils.isTvDevice
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import com.google.android.gms.cast.MediaTrack as GoogleMediaTrack

/** Track type exposed by the in-player audio and subtitle selectors. */
private enum class PlaybackTrackType {
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
private data class PlaybackTrackOption(
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

/**
 * Plays verified torrent content locally or on Cast and supports OpenSubtitles tracks.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class LocalPlayerActivity : ComponentActivity() {

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
        val mediaUri = localStreamUrl.takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: file?.takeIf { it.isFile && it.canRead() }?.let { Uri.fromFile(it) }
        if (mediaUri == null) {
            finish()
            return
        }

        mediaTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        torrentInfoHash = intent.getStringExtra(EXTRA_INFO_HASH).orEmpty()
        val castRequested = intent.getBooleanExtra(EXTRA_CAST_ENABLED, false)
        castEnabled = castRequested && castStreamUrl.isNotBlank()
        if (castRequested && !castEnabled) {
            Toast.makeText(this, R.string.torrent_cast_unavailable, Toast.LENGTH_LONG).show()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .setMimeType(intent.getStringExtra(EXTRA_MIME_TYPE) ?: file?.mediaMimeType())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(mediaTitle).build())
            .build()
        currentMediaItem = mediaItem

        val playerView = PlayerView(this)
        this.playerView = playerView
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
                20_000,
                60_000,
                5_000,
                10_000
            )
            .setTargetBufferBytes(32 * 1024 * 1024)
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
        keepSubtitleOptionsAvailable(playerView)
        local.setMediaItem(mediaItem)
        local.prepare()
        local.playWhenReady = true
        if (castEnabled && buildCastPlayer() == null) castEnabled = false

        val root = FrameLayout(this)
        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        if (castEnabled) {
            val routeButton = MediaRouteButton(
                ContextThemeWrapper(this, MediaRouterR.style.Theme_MediaRouter)
            )
            routeButton.id = View.generateViewId()
            CastButtonFactory.setUpMediaRouteButton(applicationContext, routeButton)
            root.addView(
                routeButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
            )
        }
        setContentView(root)
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
        val buttonStyle = androidx.media3.ui.R.style.ExoStyledControls_Button_Bottom
        val subtitleButtonIndex = controls.indexOfChild(subtitleButton)
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
            controls.addView(
                ImageButton(this, null, 0, buttonStyle).apply {
                    setImageResource(icon)
                    contentDescription = getString(description)
                    setOnClickListener { action() }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    id = View.generateViewId()
                },
                subtitleButtonIndex + index + 1
            )
        }
        listOf(
            Triple(
                androidx.media3.ui.R.drawable.exo_ic_audiotrack,
                R.string.player_audio_tracks
            ) { showTrackOptions(PlaybackTrackType.AUDIO) }
        ).forEach { (icon, description, action) ->
            controls.addView(
                ImageButton(this, null, 0, buttonStyle).apply {
                    setImageResource(icon)
                    contentDescription = getString(description)
                    setOnClickListener { action() }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    id = View.generateViewId()
                }
            )
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
                }
            }
        )
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
            val dialog = AlertDialog.Builder(this)
                .setTitle(
                    if (isSubtitle) R.string.player_subtitle_tracks
                    else R.string.player_audio_tracks
                )
                .setMessage(
                    if (isSubtitle) R.string.player_no_subtitle_tracks
                    else R.string.player_no_audio_tracks
                )
                .setPositiveButton(android.R.string.ok, null)
                .create()
            showAdaptiveDialog(dialog)
            return
        }

        val labels = listOf(defaultLabel) + options.map(PlaybackTrackOption::label)
        val selectedIndex = options.indexOfFirst { option ->
            if (option.castTrackId != null) {
                castActiveTrackIds().contains(option.castTrackId)
            } else {
                option.isSelected
            }
        }.let { if (it < 0) 0 else it + 1 }
        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (isSubtitle) R.string.player_subtitle_tracks
                else R.string.player_audio_tracks
            )
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { choice, index ->
                if (castSessionActive) {
                    selectCastTrack(type, options.getOrNull(index - 1)?.castTrackId)
                } else {
                    selectLocalTrack(type, options.getOrNull(index - 1))
                }
                choice.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showAdaptiveDialog(dialog)
    }

    /** Shows embedded subtitle tracks from the native CC control. */
    private fun showSubtitleOptions() {
        val options = availableTrackOptions(PlaybackTrackType.SUBTITLE)
        val labels = buildList {
            add(getString(R.string.player_subtitles_off))
            addAll(options.map(PlaybackTrackOption::label))
        }
        val selectedTrackIndex = options.indexOfFirst(PlaybackTrackOption::isSelected)
        val selectedIndex = if (selectedTrackIndex < 0) 0 else selectedTrackIndex + 1
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.player_subtitle_tracks)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { choice, index ->
                if (castSessionActive) {
                    selectCastTrack(
                        PlaybackTrackType.SUBTITLE,
                        options.getOrNull(index - 1)?.castTrackId
                    )
                } else {
                    selectLocalTrack(
                        PlaybackTrackType.SUBTITLE,
                        options.getOrNull(index - 1)
                    )
                }
                choice.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showAdaptiveDialog(dialog)
    }

    /** Presents text-size and system-caption styling options for local playback. */
    private fun showSubtitleStyleOptions() {
        val labels = arrayOf(
            getString(R.string.player_subtitle_style_system),
            getString(R.string.player_subtitle_style_small),
            getString(R.string.player_subtitle_style_medium),
            getString(R.string.player_subtitle_style_large),
            getString(R.string.player_subtitle_style_extra_large)
        )
        val fractions = listOf(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
            0.04f,
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
            0.067f,
            0.08f
        )
        val selectedIndex = fractions.indexOf(subtitleTextSizeFraction).coerceAtLeast(0)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.player_subtitle_style)
            .setSingleChoiceItems(labels, selectedIndex) { choice, index ->
                val subtitleView = playerView?.subtitleView
                if (index == 0) {
                    subtitleTextSizeFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    subtitleView?.setApplyEmbeddedStyles(true)
                } else {
                    subtitleTextSizeFraction = fractions[index]
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setApplyEmbeddedStyles(false)
                    subtitleView?.setFractionalTextSize(subtitleTextSizeFraction)
                }
                choice.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showAdaptiveDialog(dialog)
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
        return CastContext.getSharedInstance(this)
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
        val remoteClient = CastContext.getSharedInstance(this)
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
    private fun castActiveTrackIds(): List<Long> =
        CastContext.getSharedInstance(this)
            .sessionManager
            .currentCastSession
            ?.remoteMediaClient
            ?.mediaStatus
            ?.activeTrackIds
            ?.toList()
            .orEmpty()

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

    /** Searches OpenSubtitles once per user action and presents matching releases. */
    private fun searchSubtitles() {
        if (subtitleOperationInProgress) return
        subtitleOperationInProgress = true
        val progressDialog = showSubtitleProgressDialog(R.string.opensubtitles_searching)
        lifecycleScope.launch {
            var results: List<OpenSubtitleResult>? = null
            var message: String? = null
            try {
                val mediaType = if (EPISODE_PATTERN.containsMatchIn(mediaTitle)) "episode" else "movie"
                val foundResults = openSubtitlesRepository.search(mediaTitle, type = mediaType)
                results = foundResults
                if (foundResults.isEmpty()) {
                    message = getString(R.string.opensubtitles_no_results)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: OpenSubtitlesException) {
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: IOException) {
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } catch (error: IllegalArgumentException) {
                message = error.message ?: getString(R.string.opensubtitles_request_failed)
            } finally {
                progressDialog.dismiss()
                subtitleOperationInProgress = false
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
        val labels = results.map { result ->
            val feature = result.featureTitle?.takeIf(String::isNotBlank)
            listOfNotNull(
                result.language.uppercase(Locale.ROOT),
                result.release.takeIf(String::isNotBlank),
                feature
            ).joinToString(" · ")
        }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.opensubtitles_results_title)
            .setItems(labels) { _, index -> downloadSubtitle(results[index]) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showAdaptiveDialog(dialog)
    }

    /**
     * Downloads a selected subtitle and attaches it to local and Cast playback.
     *
     * @param result Subtitle result selected from the OpenSubtitles list.
     */
    private fun downloadSubtitle(result: OpenSubtitleResult) {
        if (subtitleOperationInProgress) return
        subtitleOperationInProgress = true
        val progressDialog = showSubtitleProgressDialog(R.string.opensubtitles_downloading)
        lifecycleScope.launch {
            var errorMessage: String? = null
            try {
                val subtitle = openSubtitlesRepository.download(
                    result,
                    torrentInfoHash = torrentInfoHash.takeIf(String::isNotBlank)
                )
                val localUri = Uri.fromFile(subtitle.file)
                val castUri = if (castEnabled) {
                    val server = VerifiedTorrentHttpServer(
                        subtitle.file,
                        subtitle.file.length()
                    ) { _, _ -> true }
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
                if (castUri != null) {
                    castSubtitleUrls[localUri.toString()] = castUri
                }
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
                Toast.makeText(this@LocalPlayerActivity, R.string.opensubtitles_loaded, Toast.LENGTH_SHORT)
                    .show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: OpenSubtitlesException) {
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IOException) {
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IllegalStateException) {
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } catch (error: IllegalArgumentException) {
                errorMessage = error.message ?: getString(R.string.opensubtitles_download_failed)
            } finally {
                progressDialog.dismiss()
                subtitleOperationInProgress = false
            }
            errorMessage?.let(::showSubtitleMessage)
        }
    }

    /**
     * Shows a non-cancelable progress dialog sized for the current form factor.
     *
     * @param message Resource describing the active subtitle operation.
     * @return Visible progress dialog dismissed when the operation completes.
     */
    private fun showSubtitleProgressDialog(message: Int): AlertDialog =
        AlertDialog.Builder(this)
            .setTitle(R.string.opensubtitles_title)
            .setMessage(message)
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()
            .also(::showAdaptiveDialog)

    /**
     * Presents subtitle service or download errors without concealing the actionable message.
     *
     * @param message Actionable subtitle service error.
     */
    private fun showSubtitleMessage(message: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.opensubtitles_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        showAdaptiveDialog(dialog)
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
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun buildCastPlayer(): CastPlayer? {
        return try {
            val remotePlayer = CastPlayer(
                CastContext.getSharedInstance(this),
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
            if (remotePlayer.isCastSessionAvailable()) switchToCastPlayer()
            remotePlayer
        } catch (_: IllegalStateException) {
            Toast.makeText(this, R.string.torrent_cast_framework_unavailable, Toast.LENGTH_LONG)
                .show()
            null
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.torrent_cast_framework_unavailable, Toast.LENGTH_LONG)
                .show()
            null
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

    /** Releases decoder and subtitle streaming resources when this activity is destroyed. */
    override fun onDestroy() {
        screenAwakePlayer?.let { player ->
            screenAwakeListener?.let(player::removeListener)
        }
        screenAwakeListener = null
        screenAwakePlayer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    companion object {
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

        /** Identifies the season/episode naming used by the TV release-selection screen. */
        val EPISODE_PATTERN = Regex("""\bS\d{2}E\d{2}\b""", RegexOption.IGNORE_CASE)
    }
}
