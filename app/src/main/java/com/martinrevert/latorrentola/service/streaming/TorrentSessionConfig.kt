package com.martinrevert.latorrentola.service.streaming

import android.util.Log
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.alert
import org.libtorrent4j.swig.alert_category_t
import org.libtorrent4j.swig.settings_pack
import java.io.File

/**
 * Builds optimized libtorrent session parameters and runtime settings based on device profile.
 *
 * Tuned for low-latency sequential streaming (ExoPlayer & Google Cast) while maintaining
 * strict Scudo allocator safety (`hashing_threads = 1`) and the zero-seeding mandate.
 */
object TorrentSessionConfig {

    private const val TAG = "TorrentSessionConfig"
    private const val SESSION_STATE_FILENAME = "libtorrent_session_state.dat"

    /**
     * Builds a high-throughput [SessionParams] instance optionally restored from persisted DHT/session state.
     *
     * @param isTv Whether the device is an Android TV / Google TV device.
     * @param isLowRam Whether the device reports low RAM via ActivityManager.
     * @param sessionStateFile Optional file containing persisted bencoded libtorrent session state (DHT nodes).
     */
    fun buildSessionParams(
        isTv: Boolean,
        isLowRam: Boolean = false,
        sessionStateFile: File? = null
    ): SessionParams {
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
        val connections = if (isTv || isLowRam) 100 else 180
        val maxPeers = if (isTv || isLowRam) 250 else 500
        val unchokeSlots = if (isTv || isLowRam) 8 else 12
        val connectionSpeed = if (isTv || isLowRam) 25 else 50
        pack.connectionsLimit(connections)
        pack.setInteger(settings_pack.int_types.max_peerlist_size.swigValue(), maxPeers)
        pack.setInteger(settings_pack.int_types.unchoke_slots_limit.swigValue(), unchokeSlots)
        pack.setInteger(settings_pack.int_types.connection_speed.swigValue(), connectionSpeed)
        pack.setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 40)

        // 3. Transport & parallel tracker discovery:
        // Prefer TCP in mixed-mode algorithm so uTP LEDBAT delay-based congestion control does not
        // self-throttle TCP peers on Wi-Fi jitter. Announce to all tracker tiers and trackers in parallel.
        pack.setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.smooth_connects.swigValue(), false)
        pack.setInteger(
            settings_pack.int_types.mixed_mode_algorithm.swigValue(),
            settings_pack.bandwidth_mixed_algo_t.prefer_tcp.swigValue()
        )

        // 4. High-bandwidth request pipelining & anti-head-of-line-blocking timeouts:
        // Allow deep 16 KB block pipelines on high-BDP connections and quickly re-request stalled blocks.
        pack.setInteger(settings_pack.int_types.max_out_request_queue.swigValue(), 1500)
        pack.setInteger(settings_pack.int_types.max_allowed_in_request_queue.swigValue(), 2000)
        pack.setInteger(settings_pack.int_types.request_timeout.swigValue(), 10)
        pack.setInteger(settings_pack.int_types.piece_timeout.swigValue(), 10)
        pack.setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 7)
        pack.setInteger(settings_pack.int_types.inactivity_timeout.swigValue(), 60)
        pack.setInteger(settings_pack.int_types.whole_pieces_threshold.swigValue(), 15)
        pack.setBoolean(settings_pack.bool_types.prioritize_partial_pieces.swigValue(), true)

        // 5. ZERO SEEDING MANDATE: never seed in background once complete
        pack.activeSeeds(0)
        pack.uploadRateLimit(0)
        pack.downloadRateLimit(0)

        // 6. Memory & socket buffer watermarks for gigabit/Wi-Fi 6 throughput
        val maxWatermark = if (isTv || isLowRam) 2 * 1024 * 1024 else 4 * 1024 * 1024
        pack.setInteger(settings_pack.int_types.send_buffer_low_watermark.swigValue(), 64 * 1024)
        pack.setInteger(settings_pack.int_types.send_buffer_watermark.swigValue(), 1024 * 1024)
        pack.setInteger(settings_pack.int_types.send_buffer_watermark_factor.swigValue(), 150)
        pack.setInteger(settings_pack.int_types.max_queued_disk_bytes.swigValue(), maxWatermark)

        // 7. Optimized alert mask
        val mask = createOptimizedAlertMask()
        pack.setInteger(settings_pack.int_types.alert_mask.swigValue(), mask.to_int())

        // Default native mmap_disk_io is multi-threaded and OS page-cache coherent.
        // DO NOT call setPosixDiskIO() as it uses blocking synchronous pread/pwrite
        // causing severe disk bottlenecks, peer stalls, and file cache collisions with NanoHTTPD.
        if (sessionStateFile != null && sessionStateFile.isFile && sessionStateFile.length() in 1..65536) {
            try {
                val params = SessionParams(sessionStateFile)
                params.setSettings(pack)
                return params
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring unreadable session state file, starting fresh DHT state: ${e.message}")
            }
        }
        return SessionParams(pack)
    }

    /**
     * Resolves the file used to persist warm DHT routing table and session state across runs.
     */
    fun resolveSessionStateFile(filesDir: File): File = File(filesDir, SESSION_STATE_FILENAME)

    /**
     * Saves the current libtorrent session state (including DHT routing table nodes) to disk.
     */
    fun saveSessionState(manager: SessionManager, sessionStateFile: File) {
        try {
            val stateBytes = manager.saveState()
            if (stateBytes != null && stateBytes.isNotEmpty()) {
                sessionStateFile.writeBytes(stateBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist libtorrent session state: ${e.message}")
        }
    }

    fun createOptimizedAlertMask(): alert_category_t {
        // Listen for status, piece_progress (required for PieceFinishedAlert), error, and storage alerts.
        // NEVER enable all_categories or block_progress_notification, which emit hundreds of thousands of
        // block_finished_alert instances that exhaust and corrupt native JNI memory in Scudo.
        return alert.status_notification
            .or_(alert.piece_progress_notification)
            .or_(alert.error_notification)
            .or_(alert.storage_notification)
    }

    /**
     * Applies the optimized alert mask and unchoked upload settings to a running session.
     *
     * CRITICAL: `SessionManager.start(SessionParams)` in libtorrent4j internally overwrites `alert_mask`
     * to `alert.all_categories` before starting the native session. This method MUST be invoked
     * immediately after `manager.start(sessionParams)` to restore the Scudo-safe alert mask.
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
        pack.downloadRateLimit(0)
        pack.activeSeeds(0)
        manager.applySettings(pack)
    }
}
