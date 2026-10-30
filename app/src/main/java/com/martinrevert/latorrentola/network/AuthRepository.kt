package com.martinrevert.latorrentola.network

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.martinrevert.latorrentola.utils.AppLogger
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.martinrevert.latorrentola.constants.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Firebase authentication and Google Credential Manager sign-in.
 *
 * @property firebaseAuth Firebase authentication client.
 * @property context Application context used to create Credential Manager.
 */
@Singleton
class AuthRepository internal constructor(
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val credentialManager: CredentialManager,
    private val crashlyticsManager: CrashlyticsManager = CrashlyticsManager()
) {
    @Inject
    constructor(
        firebaseAuth: FirebaseAuth,
        @ApplicationContext context: Context,
        crashlyticsManager: CrashlyticsManager
    ) : this(firebaseAuth, context, CredentialManager.create(context), crashlyticsManager)

    constructor(
        firebaseAuth: FirebaseAuth,
        @ApplicationContext context: Context
    ) : this(firebaseAuth, context, CredentialManager.create(context), CrashlyticsManager())

    /** Mutable backing stream updated whenever Firebase authentication changes. */
    private val _currentUserFlow = MutableStateFlow(firebaseAuth.currentUser)
    /** Observable Firebase user state. */
    val authStateFlow: StateFlow<FirebaseUser?> = _currentUserFlow.asStateFlow()

    /** Keeps [authStateFlow] synchronized with Firebase Auth state changes. */
    init {
        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            _currentUserFlow.value = user
            if (user != null) {
                crashlyticsManager.setUserId(user.uid)
            } else {
                crashlyticsManager.setUserId(null)
            }
        }
    }

    /** Currently signed-in Firebase user, or `null` when signed out. */
    val currentUser get() = firebaseAuth.currentUser

    /**
     * Starts Google sign-in and exchanges the returned ID token for Firebase credentials.
     *
     * In accordance with Google Identity standards:
     * 1. Uses [GetSignInWithGoogleOption] in an isolated request as the standard explicit button flow.
     *    Per Google Credential Manager specifications, [GetSignInWithGoogleOption] cannot be combined with
     *    other options in the same request.
     * 2. If [GetSignInWithGoogleOption] fails due to an exception on certain devices or Android TV builds,
     *    falls back gracefully to [GetGoogleIdOption] in its own isolated request.
     * 3. Explicit user cancellations ([GetCredentialCancellationException]) are honored immediately without fallback.
     *
     * @param activityContext Context required to present the credential UI (unwrapped to Activity if needed).
     * @return Success when Firebase accepts the credential, otherwise the originating failure.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        return try {
            val targetActivity = activityContext.findActivity() ?: activityContext

            val result = try {
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                    serverClientId = Constants.WEB_CLIENT_ID
                ).build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                Log.d("AuthRepository", "Requesting credential via GetSignInWithGoogleOption")
                credentialManager.getCredential(
                    request = request,
                    context = targetActivity
                )
            } catch (e: GetCredentialCancellationException) {
                // User explicitly dismissed or cancelled the account picker; do not trigger fallback
                throw e
            } catch (e: Exception) {
                Log.w("AuthRepository", "GetSignInWithGoogleOption failed, attempting fallback to GetGoogleIdOption", e)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(Constants.WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                credentialManager.getCredential(
                    request = fallbackRequest,
                    context = targetActivity
                )
            }

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            
            Log.d("AuthRepository", "Exchanging Google ID token for Firebase credential")
            firebaseAuth.signInWithCredential(authCredential).await()
            Log.d("AuthRepository", "Firebase authentication completed successfully")
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            AppLogger.d("AuthRepository", "User cancelled credential request")
            Result.failure(e)
        } catch (e: NoCredentialException) {
            AppLogger.d("AuthRepository", "No credential available on device: ${e.message}")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            AppLogger.e("AuthRepository", "GetCredentialException during sign-in", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("AuthRepository", "Unexpected error during Google sign-in", e)
            Result.failure(e)
        }
    }

    /** Signs out of Firebase and clears the Credential Manager credential state. */
    suspend fun signOut() {
        firebaseAuth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            AppLogger.e("AuthRepository", "Error clearing credential state: ${e.message}", e)
        }
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
