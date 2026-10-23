package com.martinrevert.latorrentola.model.torrent

/** Configures how a selected torrent is handled by the app. */
enum class TorrentHandlingMode {
    /** Opens the magnet URI in the user's external torrent client. */
    EXTERNAL_CLIENT,

    /** Downloads locally and streams only byte ranges whose torrent pieces are verified. */
    LOCAL_PLAYBACK,

    /**
     * Legacy mode for directly targeting Chromecast.
     * Deprecated: Torrent playback now always initiates through ExoPlayer local playback,
     * and Chromecast streaming is activated on-demand via the player's MediaRouter Cast button.
     */
    @Deprecated("Chromecast is now initiated on demand via the in-player MediaRouter button.")
    CHROMECAST
}
