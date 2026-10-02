package com.martinrevert.latorrentola.model.torrent

/** Configures how a selected torrent is handled by the app. */
enum class TorrentHandlingMode {
    /** Opens the magnet URI in the user's external torrent client. */
    EXTERNAL_CLIENT,

    /** Downloads locally and streams only byte ranges whose torrent pieces are verified. */
    LOCAL_PLAYBACK,

    /** Downloads locally and offers a LAN stream to a selected Chromecast-compatible receiver. */
    CHROMECAST
}
