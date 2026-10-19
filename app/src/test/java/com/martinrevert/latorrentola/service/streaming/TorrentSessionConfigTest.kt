package com.martinrevert.latorrentola.service.streaming

import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.libtorrent4j.swig.settings_pack

class TorrentSessionConfigTest {

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
    fun `buildSessionParams sets TV specific limits`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val params = TorrentSessionConfig.buildSessionParams(isTv = true)
        val sp = params.settings

        assertThat(sp.connectionsLimit()).isEqualTo(120)
        assertThat(sp.activeSeeds()).isEqualTo(0)
        assertThat(sp.getInteger(settings_pack.int_types.aio_threads.swigValue())).isEqualTo(2)
        assertThat(sp.getInteger(settings_pack.int_types.hashing_threads.swigValue())).isEqualTo(1)
    }

    @Test
    fun `buildSessionParams sets Mobile specific limits`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val params = TorrentSessionConfig.buildSessionParams(isTv = false)
        val sp = params.settings

        assertThat(sp.connectionsLimit()).isEqualTo(200)
        assertThat(sp.activeSeeds()).isEqualTo(0)
        assertThat(sp.getInteger(settings_pack.int_types.aio_threads.swigValue())).isEqualTo(4)
        assertThat(sp.getInteger(settings_pack.int_types.hashing_threads.swigValue())).isEqualTo(2)
    }

    @Test
    fun `createOptimizedAlertMask produces valid mask`() {
        assumeTrue("Native libtorrent4j not available on host JVM", nativeLoaded)
        val mask = TorrentSessionConfig.createOptimizedAlertMask()
        assertThat(mask.to_int()).isGreaterThan(0)
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
