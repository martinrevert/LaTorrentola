package com.martinrevert.latorrentola.service

import android.content.Context
import androidx.media3.cast.DefaultCastOptionsProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class AppCastOptionsProviderTest {

    @Test
    fun `getCastOptions returns valid options with receiver app id and stop on end enabled`() {
        val provider = AppCastOptionsProvider()
        val mockContext = mockk<Context>(relaxed = true)

        val castOptions = provider.getCastOptions(mockContext)

        assertThat(castOptions).isNotNull()
        assertThat(castOptions.receiverApplicationId).isNotEmpty()
        assertThat(castOptions.stopReceiverApplicationWhenEndingSession).isTrue()
    }

    @Test
    fun `getAdditionalSessionProviders returns null`() {
        val provider = AppCastOptionsProvider()
        val mockContext = mockk<Context>(relaxed = true)

        assertThat(provider.getAdditionalSessionProviders(mockContext)).isNull()
    }
}
