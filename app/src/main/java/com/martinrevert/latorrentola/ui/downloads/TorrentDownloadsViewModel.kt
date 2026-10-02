package com.martinrevert.latorrentola.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.martinrevert.latorrentola.database.TorrentDownloadDao
import com.martinrevert.latorrentola.service.TorrentDownloadService
import com.martinrevert.latorrentola.model.torrent.TorrentDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes persisted torrent jobs and their lifecycle actions to the download manager. */
@HiltViewModel
class TorrentDownloadsViewModel @Inject constructor(
    torrentDownloadDao: TorrentDownloadDao
) : ViewModel() {

    /** All locally managed downloads ordered by their most recent state change. */
    val downloads: StateFlow<List<TorrentDownload>> = torrentDownloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Sends a pause or resume request to the download service.
     *
     * @param context Activity context used to reach the explicit service.
     * @param download Job whose active transfer is to be changed.
     */
    fun togglePause(context: Context, download: TorrentDownload) {
        val action = if (
            download.state == TorrentDownloadService.STATE_PAUSED ||
            download.state == TorrentDownloadService.STATE_FAILED
        ) {
            TorrentDownloadService.ACTION_RESUME
        } else {
            TorrentDownloadService.ACTION_PAUSE
        }
        sendCommand(context, action, download.infoHash)
    }

    /**
     * Removes an app-managed torrent and its private payload.
     *
     * @param context Activity context used to reach the explicit service.
     * @param download Job to remove.
     */
    fun delete(context: Context, download: TorrentDownload) {
        sendCommand(context, TorrentDownloadService.ACTION_DELETE, download.infoHash)
    }

    /**
     * Sends an explicit lifecycle command to the foreground torrent service.
     *
     * @param context Context used to start the service.
     * @param action Service action to perform.
     * @param infoHash Stable local torrent job identifier.
     */
    private fun sendCommand(context: Context, action: String, infoHash: String) {
        val intent = Intent(context, TorrentDownloadService::class.java).apply {
            this.action = action
            putExtra(TorrentDownloadService.EXTRA_INFO_HASH, infoHash)
        }
        context.startService(intent)
    }
}
