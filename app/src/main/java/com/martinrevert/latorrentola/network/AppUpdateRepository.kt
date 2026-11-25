package com.martinrevert.latorrentola.network

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.martinrevert.latorrentola.BuildConfig
import com.martinrevert.latorrentola.constants.Constants
import com.martinrevert.latorrentola.model.update.AppUpdateManifest
import com.martinrevert.latorrentola.utils.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Distinct failure categories encountered while downloading or verifying a release APK update.
 */
enum class AppUpdateErrorType {
    NETWORK_OR_IO,
    CHECKSUM_MISMATCH
}

/**
 * Progress and lifecycle states emitted while downloading and verifying a mandatory APK update.
 */
sealed interface ApkDownloadState {
    /** Initial state when an update is detected and awaiting user confirmation to start downloading. */
    data object Idle : ApkDownloadState

    /** Emitted repeatedly while streaming the APK bytes to local cache storage. */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : ApkDownloadState

    /** Emitted while finalizing the SHA-256 integrity comparison. */
    data object VerifyingChecksum : ApkDownloadState

    /** Emitted once the downloaded APK's SHA-256 matches the manifest and is ready for installation. */
    data class ReadyToInstall(val apkFile: File) : ApkDownloadState

    /** Emitted when a network failure or SHA-256 mismatch occurs. */
    data class Error(val errorType: AppUpdateErrorType) : ApkDownloadState
}

/**
 * Checks the self-hosted APK server for newer release versions, streams the APK update,
 * verifies its cryptographic SHA-256 digest, and prepares package installation intents.
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val UPDATE_CACHE_DIR_NAME = "apk_updates"
        private const val BUFFER_SIZE = 64 * 1024
        private val SAFE_FILENAME_REGEX = Regex("[^A-Za-z0-9._-]")
        private val SHA256_TOKEN_REGEX = Regex("^([a-fA-F0-9]{64})\\b")

        /** Computes the lowercase hexadecimal SHA-256 digest of [file]. */
        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHexLower()
        }

        /** Normalizes a raw SHA-256 string or `.sha256` file content into a 64-char lowercase hex hash. */
        fun extractExpectedSha256(raw: String): String {
            val trimmed = raw.trim()
            val match = SHA256_TOKEN_REGEX.find(trimmed)
            return match?.groupValues?.get(1)?.lowercase().orEmpty()
        }

        private fun ByteArray.toHexLower(): String {
            val hexChars = CharArray(size * 2)
            val digits = "0123456789abcdef"
            for (i in indices) {
                val v = this[i].toInt() and 0xFF
                hexChars[i * 2] = digits[v ushr 4]
                hexChars[i * 2 + 1] = digits[v and 0x0F]
            }
            return String(hexChars)
        }
    }

    /**
     * Queries the remote `manifest.json` and returns the [AppUpdateManifest] if a newer release is available.
     *
     * Mandatory update checks are strictly disabled in debug builds ([isDebugBuild] == `true`)
     * so local development and debugging are never blocked.
     */
    suspend fun checkForMandatoryUpdate(
        manifestUrl: String = Constants.APK_UPDATE_MANIFEST_URL,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        isDebugBuild: Boolean = BuildConfig.DEBUG
    ): AppUpdateManifest? = withContext(Dispatchers.IO) {
        if (isDebugBuild || manifestUrl.isBlank()) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Manifest check returned HTTP ${response.code}")
                    return@withContext null
                }
                val bodyText = response.body.string()
                if (bodyText.isBlank()) return@withContext null

                val manifest = gson.fromJson(bodyText, AppUpdateManifest::class.java) ?: return@withContext null
                val expectedSha = extractExpectedSha256(manifest.sha256)
                if (manifest.forceUpdate &&
                    manifest.versionCode > currentVersionCode &&
                    manifest.apkUrl.isNotBlank() &&
                    expectedSha.length == 64
                ) {
                    manifest.copy(sha256 = expectedSha)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to check for mandatory APK update: ${e.message}")
            null
        }
    }

    /**
     * Downloads the APK referenced by [manifest] into the app's update cache directory,
     * emitting progress updates and verifying the SHA-256 digest before emitting [ApkDownloadState.ReadyToInstall].
     */
    fun downloadAndVerifyApk(
        manifest: AppUpdateManifest,
        cacheRootDir: File = context.cacheDir
    ): Flow<ApkDownloadState> = flow {
        val updateDir = File(cacheRootDir, UPDATE_CACHE_DIR_NAME)
        if (!updateDir.exists()) {
            updateDir.mkdirs()
        }

        val rawFileName = File(manifest.apkFileName).name
            .replace(SAFE_FILENAME_REGEX, "_")
            .trimStart('.')
        val sanitizedFileName = rawFileName
            .ifBlank { "LaTorrentola-v${manifest.versionCode}-release.apk" }
        val targetFile = File(updateDir, sanitizedFileName)
        val expectedSha256 = extractExpectedSha256(manifest.sha256)
        if (expectedSha256.length != 64) {
            emit(ApkDownloadState.Error(AppUpdateErrorType.CHECKSUM_MISMATCH))
            return@flow
        }

        // Reuse an already downloaded and verified APK in cache if its SHA-256 matches
        if (targetFile.exists() && targetFile.length() > 0L) {
            emit(ApkDownloadState.VerifyingChecksum)
            val existingSha = runCatching { computeFileSha256(targetFile) }.getOrNull()
            if (existingSha != null && existingSha.equals(expectedSha256, ignoreCase = true)) {
                emit(ApkDownloadState.ReadyToInstall(targetFile))
                return@flow
            }
            targetFile.delete()
        }

        // Clean any stale partial or older APKs in the update cache directory
        updateDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }

        val tempFile = File(updateDir, "${sanitizedFileName}.part")
        try {
            val request = Request.Builder()
                .url(manifest.apkUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "APK download failed with HTTP ${response.code}")
                    emit(ApkDownloadState.Error(AppUpdateErrorType.NETWORK_OR_IO))
                    return@flow
                }

                val body = response.body
                val contentLength = body.contentLength().takeIf { it > 0L } ?: manifest.fileSize
                val digest = MessageDigest.getInstance("SHA-256")
                var totalRead = 0L
                var lastEmittedPercent = 0
                var lastEmittedBytes = 0L

                emit(
                    ApkDownloadState.Downloading(
                        bytesDownloaded = 0L,
                        totalBytes = contentLength,
                        progressPercent = 0
                    )
                )

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            totalRead += read

                            val percent = if (contentLength > 0L) {
                                ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val shouldEmit = if (contentLength > 0L) {
                                percent != lastEmittedPercent
                            } else {
                                totalRead - lastEmittedBytes >= 256 * 1024L
                            }
                            if (shouldEmit) {
                                lastEmittedPercent = percent
                                lastEmittedBytes = totalRead
                                emit(
                                    ApkDownloadState.Downloading(
                                        bytesDownloaded = totalRead,
                                        totalBytes = contentLength,
                                        progressPercent = percent
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }

                emit(ApkDownloadState.VerifyingChecksum)
                val actualSha256 = digest.digest().toHexLower()
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    AppLogger.e(
                        TAG,
                        "APK SHA-256 mismatch! Expected=$expectedSha256, Actual=$actualSha256"
                    )
                    tempFile.delete()
                    emit(ApkDownloadState.Error(AppUpdateErrorType.CHECKSUM_MISMATCH))
                    return@flow
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                val verifiedFile = if (renamed) targetFile else tempFile
                emit(ApkDownloadState.ReadyToInstall(verifiedFile))
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to download APK update: ${e.message}")
            tempFile.delete()
            emit(ApkDownloadState.Error(AppUpdateErrorType.NETWORK_OR_IO))
        }
    }.flowOn(Dispatchers.IO)

    /** Returns `true` when this package has been granted permission to request APK installations. */
    fun canRequestPackageInstalls(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    /** Creates the system settings intent allowing the user to grant unknown app installation permission. */
    fun createManageUnknownAppSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        )
    }

    /** Builds a `FileProvider`-backed [Intent.ACTION_VIEW] intent to install [apkFile]. */
    fun createInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
