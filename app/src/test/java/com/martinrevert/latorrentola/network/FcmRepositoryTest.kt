package com.martinrevert.latorrentola.network

import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.utils.PreferenceManager
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class FcmRepositoryTest {

    private val fcmService: FcmService = mockk()
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private lateinit var repository: FcmRepository

    @Before
    fun setUp() {
        repository = FcmRepository(fcmService, preferenceManager)
    }

    @Test
    fun `subscribe marks token synced on success`() = runTest {
        coEvery { fcmService.subscribe("token_123") } returns Response.success(Unit)

        val result = repository.subscribe("token_123")

        assertThat(result).isTrue()
        verify { preferenceManager.setFcmTokenSynced(true) }
    }

    @Test
    fun `subscribe returns false on non-cancellation exception`() = runTest {
        coEvery { fcmService.subscribe("token_123") } throws RuntimeException("boom")

        val result = repository.subscribe("token_123")

        assertThat(result).isFalse()
    }

    @Test(expected = CancellationException::class)
    fun `subscribe rethrows CancellationException`() = runTest {
        coEvery { fcmService.subscribe("token_123") } throws CancellationException("Job was cancelled")

        repository.subscribe("token_123")
    }

    @Test
    fun `unsubscribe clears token synced on success`() = runTest {
        coEvery { fcmService.unsubscribe("token_123") } returns Response.success(Unit)

        val result = repository.unsubscribe("token_123")

        assertThat(result).isTrue()
        verify { preferenceManager.setFcmTokenSynced(false) }
    }

    @Test(expected = CancellationException::class)
    fun `unsubscribe rethrows CancellationException`() = runTest {
        coEvery { fcmService.unsubscribe("token_123") } throws CancellationException("Job was cancelled")

        repository.unsubscribe("token_123")
    }
}
