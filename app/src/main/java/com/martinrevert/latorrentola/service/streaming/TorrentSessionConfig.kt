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

        // 1. Threading & hashing limits (leave disk queue to dynamic mmap allocation)
        // CRITICAL FOR ANDROID SCUDO ALLOCATOR:
        // Set hashing_threads = 1 on ALL devices to eliminate concurrent OpenSSL EVP context deallocations
        // (crypto/evp/evp_lib.c) that trigger "Scudo ERROR: invalid chunk state" aborts.
        val aioThreads = 2
        val hashingThreads = 1
        pack.setInteger(settings_pack.int_types.aio_threads.swigValue(), aioThreads)
        pack.setInteger(settings_pack.int_types.hashing_threads.swigValue(), hashingThreads)

        // 2. Peer connections & unchoking limits: optimized for fast streaming and swarm reciprocity
        val connections = if (isTv || isLowRam) 80 else 120
        val maxPeers = if (isTv || isLowRam) 200 else 400
        val unchokeSlots = if (isTv || isLowRam) 6 else 8
        pack.connectionsLimit(connections)
        pack.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), maxPeers)
        pack.setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), unchokeSlots)

        // 3. ZERO SEEDING MANDATE: never seed in background once complete
        pack.activeSeeds(0)
        pack.uploadRateLimit(0)

        // 4. Memory watermark limits
        pack.setInteger(settings_pack.int_types.send_buffer_watermark.swigValue(), 256 * 1024)

        // 5. Optimized alert mask
        val mask = createOptimizedAlertMask()
        pack.setInteger(settings_pack.int_types.alert_mask.swigValue(), mask.to_int())

        // Default native mmap_disk_io is multi-threaded and OS page-cache coherent.
        // DO NOT call setPosixDiskIO() as it uses blocking synchronous pread/pwrite
        // causing severe disk bottlenecks, peer stalls, and file cache collisions with NanoHTTPD.
        return SessionParams(pack)
    }

    fun createOptimizedAlertMask(): alert_category_t {
        // Only listen for status (piece/torrent finished), error, and storage alerts.
        // NEVER enable all_categories or picker/block notifications, which emit hundreds of thousands of
        // block_finished_alert instances that exhaust and corrupt native JNI memory in Scudo.
        return alert.status_notification
            .or_(alert.error_notification)
            .or_(alert.storage_notification)
    }

    /**
     * Applies the optimized alert mask and unchoked upload settings to a running session.
     */
    fun applyRuntimeOptimizations(
        manager: SessionManager,
        isPlaybackActive: Boolean,
        isTv: Boolean
    ) {
        val pack = SettingsPack()
        val mask = createOptimizedAlertMask()
        pack.setInteger(settings_pack.int_types.alert_mask.swigValue(), mask.to_int())

        // NEVER choke upload to 50KB/s: BitTorrent reciprocity chokes downloads if uploads stall!
        // Keep uploads unthrottled (0) so peers rapidly return pieces for streaming.
        pack.uploadRateLimit(0)
        pack.activeSeeds(0)
        manager.applySettings(pack)
    }
}
