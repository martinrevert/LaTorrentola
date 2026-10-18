package com.martinrevert.latorrentola.service.streaming

import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.alert
import org.libtorrent4j.swig.alert_category_t
import org.libtorrent4j.swig.settings_pack

/**
 * Builds optimized libtorrent session parameters and runtime settings based on device profile.
 */
object TorrentSessionConfig {

    fun buildSessionParams(isTv: Boolean, isLowRam: Boolean = false): SessionParams {
        val pack = SettingsPack()

        // 1. Threading & disk queue limits
        val aioThreads = if (isTv || isLowRam) 2 else 4
        val hashingThreads = if (isTv || isLowRam) 1 else 2
        pack.setInteger(settings_pack.int_types.aio_threads.swigValue(), aioThreads)
        pack.setInteger(settings_pack.int_types.hashing_threads.swigValue(), hashingThreads)
        pack.setInteger(settings_pack.int_types.max_queued_disk_bytes.swigValue(), 2 * 1024 * 1024)

        // 2. Peer connections & unchoking limits
        val connections = if (isTv || isLowRam) 60 else 120
        val maxPeers = if (isTv || isLowRam) 100 else 200
        val unchokeSlots = if (isTv || isLowRam) 6 else 12
        pack.connectionsLimit(connections)
        pack.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), maxPeers)
        pack.setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), unchokeSlots)

        // 3. ZERO SEEDING MANDATE: never seed in background once complete
        pack.activeSeeds(0)

        // 4. Memory watermark limits
        pack.setInteger(settings_pack.int_types.send_buffer_watermark.swigValue(), 128 * 1024)

        // 5. Optimized alert mask
        val mask = createOptimizedAlertMask()
        pack.setInteger(settings_pack.int_types.alert_mask.swigValue(), mask.to_int())

        val params = SessionParams(pack)
        if (isTv || isLowRam) {
            params.setPosixDiskIO()
        }

        return params
    }

    fun createOptimizedAlertMask(): alert_category_t {
        val mask = alert.all_categories
        val logMask = alert.session_log_notification
            .or_(alert.torrent_log_notification)
            .or_(alert.peer_log_notification)
            .or_(alert.dht_log_notification)
            .or_(alert.port_mapping_log_notification)
            .or_(alert.picker_log_notification)
        return mask.and_(logMask.inv())
    }

    /**
     * Applies the optimized alert mask and dynamic upload rate limit to a running session.
     */
    fun applyRuntimeOptimizations(
        manager: SessionManager,
        isPlaybackActive: Boolean,
        isTv: Boolean
    ) {
        val pack = SettingsPack()
        val mask = createOptimizedAlertMask()
        pack.setInteger(settings_pack.int_types.alert_mask.swigValue(), mask.to_int())

        // Throttle upload bandwidth during active playback to eliminate disk read contention
        if (isPlaybackActive) {
            val uploadLimit = if (isTv) 50 * 1024 else 150 * 1024
            pack.uploadRateLimit(uploadLimit)
        } else {
            pack.uploadRateLimit(0)
        }

        pack.activeSeeds(0)
        manager.applySettings(pack)
    }
}
