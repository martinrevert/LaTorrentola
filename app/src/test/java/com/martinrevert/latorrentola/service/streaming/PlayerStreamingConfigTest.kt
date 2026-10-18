package com.martinrevert.latorrentola.service.streaming

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerStreamingConfigTest {

    @Test
    fun `buildLoadControl creates valid load control for TV`() {
        val loadControl = PlayerStreamingConfig.buildLoadControl(isTv = true)
        assertThat(loadControl).isNotNull()
    }

    @Test
    fun `buildLoadControl creates valid load control for Mobile`() {
        val loadControl = PlayerStreamingConfig.buildLoadControl(isTv = false)
        assertThat(loadControl).isNotNull()
    }

    @Test
    fun `buildLoadErrorHandlingPolicy has high retry count`() {
        val policy = PlayerStreamingConfig.buildLoadErrorHandlingPolicy()
        val retryCount = policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)
        assertThat(retryCount).isAtLeast(5)
    }

    @Test
    fun `http read timeout is at least 30 seconds to absorb piece verification`() {
        assertThat(PlayerStreamingConfig.HTTP_READ_TIMEOUT_MS).isAtLeast(30_000)
    }

    @Test
    fun `http connect timeout is at least 10 seconds`() {
        assertThat(PlayerStreamingConfig.HTTP_CONNECT_TIMEOUT_MS).isAtLeast(10_000)
    }
}
