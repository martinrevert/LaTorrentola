package com.martinrevert.latorrentola.service.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.libtorrent4j.swig.alert
import org.libtorrent4j.swig.settings_pack

class TorrentSessionConfigTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var nativeLoaded = false

    @Before
    fun checkNative() {
        nativeLoaded = try {
            System.loadLibrary("libtorrent4j")
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Test
    fun `resolveSessionStateFile returns expected file inside filesDir`() {
        val dir = tempFolder.newFolder("files")
        val stateFile = TorrentSessionConfig.resolveSessionStateFile(dir)
        assertThat(stateFile.parentFile).isEqualTo(dir)
        assertThat(stateFile.name).isEqualTo("libtorrent_session_state.dat")
    }

    @Test
    fun `buildSessionParams sets TV specific limits and high-throughput streaming flags`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val params = TorrentSessionConfig.buildSessionParams(isTv = true)
        val sp = params.settings

        assertThat(sp.connectionsLimit()).isEqualTo(100)
        assertThat(sp.getInteger(settings_pack.int_types.max_peerlist_size.swigValue())).isEqualTo(250)
        assertThat(sp.getInteger(settings_pack.int_types.unchoke_slots_limit.swigValue())).isEqualTo(8)
        assertThat(sp.getInteger(settings_pack.int_types.connection_speed.swigValue())).isEqualTo(25)
        assertThat(sp.getInteger(settings_pack.int_types.torrent_connect_boost.swigValue())).isEqualTo(40)
        assertThat(sp.getBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue())).isTrue()
        assertThat(sp.getBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue())).isTrue()
        assertThat(sp.getBoolean(settings_pack.bool_types.prioritize_partial_pieces.swigValue())).isTrue()
        assertThat(sp.getInteger(settings_pack.int_types.max_out_request_queue.swigValue())).isEqualTo(1500)
        assertThat(sp.getInteger(settings_pack.int_types.request_timeout.swigValue())).isEqualTo(10)
        assertThat(sp.getInteger(settings_pack.int_types.piece_timeout.swigValue())).isEqualTo(10)
        assertThat(sp.activeSeeds()).isEqualTo(0)
        assertThat(sp.uploadRateLimit()).isEqualTo(0)
        assertThat(sp.getInteger(settings_pack.int_types.aio_threads.swigValue())).isEqualTo(2)
        assertThat(sp.getInteger(settings_pack.int_types.hashing_threads.swigValue())).isEqualTo(1)
    }

    @Test
    fun `buildSessionParams sets Mobile specific limits and high-throughput streaming flags`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val params = TorrentSessionConfig.buildSessionParams(isTv = false)
        val sp = params.settings

        assertThat(sp.connectionsLimit()).isEqualTo(180)
        assertThat(sp.getInteger(settings_pack.int_types.max_peerlist_size.swigValue())).isEqualTo(500)
        assertThat(sp.getInteger(settings_pack.int_types.unchoke_slots_limit.swigValue())).isEqualTo(12)
        assertThat(sp.getInteger(settings_pack.int_types.connection_speed.swigValue())).isEqualTo(50)
        assertThat(sp.getBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue())).isTrue()
        assertThat(sp.getBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue())).isTrue()
        assertThat(sp.getInteger(settings_pack.int_types.send_buffer_watermark.swigValue())).isEqualTo(1024 * 1024)
        assertThat(sp.activeSeeds()).isEqualTo(0)
        assertThat(sp.uploadRateLimit()).isEqualTo(0)
        assertThat(sp.getInteger(settings_pack.int_types.aio_threads.swigValue())).isEqualTo(2)
        assertThat(sp.getInteger(settings_pack.int_types.hashing_threads.swigValue())).isEqualTo(1)
    }

    @Test
    fun `createOptimizedAlertMask includes piece_progress_notification and excludes block_progress_notification`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val mask = TorrentSessionConfig.createOptimizedAlertMask()
        assertThat(mask.to_int()).isGreaterThan(0)
        val pieceMask = alert.piece_progress_notification.to_int()
        val blockMask = alert.block_progress_notification.to_int()
        assertThat(mask.to_int() and pieceMask).isEqualTo(pieceMask)
        assertThat(mask.to_int() and blockMask).isEqualTo(0)
    }

    @Test
    fun `test libtorrent4j AlertListener types`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val listener = object : org.libtorrent4j.AlertListener {
            override fun types(): IntArray? = null
            override fun alert(alert: org.libtorrent4j.alerts.Alert<*>) {
                if (alert is org.libtorrent4j.alerts.PieceFinishedAlert) {
                    val p = alert.pieceIndex()
                    assertThat(p).isNotNull()
                } else if (alert is org.libtorrent4j.alerts.TorrentFinishedAlert) {
                    assertThat(alert).isNotNull()
                }
            }
        }
        assertThat(listener).isNotNull()
        val sm = org.libtorrent4j.SessionManager()
        sm.addListener(listener)
        sm.removeListener(listener)
    }
}
