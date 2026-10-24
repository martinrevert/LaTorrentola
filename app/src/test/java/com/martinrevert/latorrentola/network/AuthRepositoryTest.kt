package com.martinrevert.latorrentola.network

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var repository: AuthRepository
    private val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val activity: Activity = mockk(relaxed = true)
    private val credentialManager: CredentialManager = mockk(relaxed = true)
    private val crashlyticsManager: CrashlyticsManager = mockk(relaxed = true)
    private val firebaseUser: FirebaseUser = mockk()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        repository = AuthRepository(firebaseAuth, context, credentialManager, crashlyticsManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `currentUser should return user from firebaseAuth`() {
        every { firebaseAuth.currentUser } returns firebaseUser
        assertThat(repository.currentUser).isEqualTo(firebaseUser)
    }

    @Test
    fun `signOut should call firebaseAuth signOut and clear credential state`() = runTest {
        repository.signOut()

        verify { firebaseAuth.signOut() }
        coVerify { credentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) }
    }

    @Test
    fun `signInWithGoogle requests GetSignInWithGoogleOption in an isolated request without combining options`() = runTest {
        val requestSlot = slot<GetCredentialRequest>()
        coEvery {
            credentialManager.getCredential(
                request = capture(requestSlot),
                context = any<Context>()
            )
        } throws GetCredentialCancellationException("User cancelled")

        val result = repository.signInWithGoogle(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(GetCredentialCancellationException::class.java)

        // Verify request contains ONLY GetSignInWithGoogleOption (size 1)
        val capturedRequest = requestSlot.captured
        assertThat(capturedRequest.credentialOptions).hasSize(1)
        assertThat(capturedRequest.credentialOptions.first()).isInstanceOf(GetSignInWithGoogleOption::class.java)
    }

    @Test
    fun `signInWithGoogle does not fallback when user cancels with GetCredentialCancellationException`() = runTest {
        coEvery {
            credentialManager.getCredential(
                request = any<GetCredentialRequest>(),
                context = any<Context>()
            )
        } throws GetCredentialCancellationException("Cancelled by user")

        val result = repository.signInWithGoogle(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(GetCredentialCancellationException::class.java)

        // Only one call should have been made to CredentialManager (no fallback attempt)
        coVerify(exactly = 1) {
            credentialManager.getCredential(
                request = any<GetCredentialRequest>(),
                context = any<Context>()
            )
        }
    }

    @Test
    fun `signInWithGoogle falls back to GetGoogleIdOption in an isolated request when GetSignInWithGoogleOption fails with exception`() = runTest {
        val capturedRequests = mutableListOf<GetCredentialRequest>()
        coEvery {
            credentialManager.getCredential(
                request = capture(capturedRequests),
                context = any<Context>()
            )
        } throws NoCredentialException("No credential found")

        val result = repository.signInWithGoogle(activity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoCredentialException::class.java)

        // Exactly two requests made: first with GetSignInWithGoogleOption, second fallback with GetGoogleIdOption
        assertThat(capturedRequests).hasSize(2)
        assertThat(capturedRequests[0].credentialOptions).hasSize(1)
        assertThat(capturedRequests[0].credentialOptions.first()).isInstanceOf(GetSignInWithGoogleOption::class.java)

        assertThat(capturedRequests[1].credentialOptions).hasSize(1)
        assertThat(capturedRequests[1].credentialOptions.first()).isInstanceOf(GetGoogleIdOption::class.java)
    }

    @Test
    fun `authStateListener updates crashlytics userId when user logs in and clears on logout`() {
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        verify { firebaseAuth.addAuthStateListener(capture(listenerSlot)) }

        every { firebaseAuth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "user_test_99"
        every { firebaseUser.email } returns "test@example.com"

        listenerSlot.captured.onAuthStateChanged(firebaseAuth)
        verify { crashlyticsManager.setUserId("user_test_99") }
        verify { crashlyticsManager.setCustomKey("user_email", "test@example.com") }

        every { firebaseAuth.currentUser } returns null
        listenerSlot.captured.onAuthStateChanged(firebaseAuth)
        verify { crashlyticsManager.setUserId(null) }
    }
}
