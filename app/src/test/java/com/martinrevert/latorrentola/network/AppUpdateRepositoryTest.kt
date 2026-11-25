package com.martinrevert.latorrentola.network

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.martinrevert.latorrentola.model.update.AppUpdateManifest
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppUpdateRepositoryTest {

    private lateinit var context: Context
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var tempDir: File
    private lateinit var repository: AppUpdateRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("apk_update_test").toFile()
        context = mockk(relaxed = true) {
            every { cacheDir } returns tempDir
            every { packageName } returns "com.martinrevert.latorrentola"
        }
        okHttpClient = mockk()
        repository = AppUpdateRepository(context, okHttpClient)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `checkForMandatoryUpdate returns null immediately when isDebugBuild is true`() = runTest {
        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "https://updates.example.com/apks/manifest.json",
            currentVersionCode = 50000,
            isDebugBuild = true
        )

        assertThat(result).isNull()
    }

    @Test
    fun `checkForMandatoryUpdate returns null immediately when manifestUrl is blank`() = runTest {
        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "   ",
            currentVersionCode = 50000,
            isDebugBuild = false
        )

        assertThat(result).isNull()
    }

    @Test
    fun `checkForMandatoryUpdate returns manifest when remote versionCode is newer in release build`() = runTest {
        val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val json = """
            {
              "versionCode": 50250,
              "versionName": "3.0.20250-main-release",
              "apkFileName": "main-LaTorrentola-v3.0.20250-release.apk",
              "apkUrl": "https://updates.example.com/apks/main-LaTorrentola-v3.0.20250-release.apk",
              "sha256Url": "https://updates.example.com/apks/main-LaTorrentola-v3.0.20250-release.apk.sha256",
              "sha256": "$sha256  main-LaTorrentola-v3.0.20250-release.apk",
              "fileSize": 1024,
              "forceUpdate": true,
              "publishedAt": "2026-11-25T16:00:00Z"
            }
        """.trimIndent()

        mockHttpCall(code = 200, bodyBytes = json.toByteArray(), contentType = "application/json")

        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "https://updates.example.com/apks/manifest.json",
            currentVersionCode = 50100,
            isDebugBuild = false
        )

        assertThat(result).isNotNull()
        assertThat(result?.versionCode).isEqualTo(50250)
        assertThat(result?.versionName).isEqualTo("3.0.20250-main-release")
        assertThat(result?.sha256).isEqualTo(sha256)
    }

    @Test
    fun `checkForMandatoryUpdate returns null when remote versionCode is equal or older`() = runTest {
        val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val json = """
            {
              "versionCode": 50100,
              "versionName": "3.0.20100-main-release",
              "apkFileName": "main-LaTorrentola-v3.0.20100-release.apk",
              "apkUrl": "https://updates.example.com/apks/main-LaTorrentola-v3.0.20100-release.apk",
              "sha256": "$sha256",
              "fileSize": 1024,
              "forceUpdate": true
            }
        """.trimIndent()

        mockHttpCall(code = 200, bodyBytes = json.toByteArray(), contentType = "application/json")

        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "https://updates.example.com/apks/manifest.json",
            currentVersionCode = 50100,
            isDebugBuild = false
        )

        assertThat(result).isNull()
    }

    @Test
    fun `checkForMandatoryUpdate returns null when forceUpdate is false`() = runTest {
        val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val json = """
            {
              "versionCode": 50300,
              "versionName": "3.0.20300-main-release",
              "apkFileName": "main-LaTorrentola-v3.0.20300-release.apk",
              "apkUrl": "https://updates.example.com/apks/main-LaTorrentola-v3.0.20300-release.apk",
              "sha256": "$sha256",
              "fileSize": 1024,
              "forceUpdate": false
            }
        """.trimIndent()

        mockHttpCall(code = 200, bodyBytes = json.toByteArray(), contentType = "application/json")

        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "https://updates.example.com/apks/manifest.json",
            currentVersionCode = 50100,
            isDebugBuild = false
        )

        assertThat(result).isNull()
    }

    @Test
    fun `checkForMandatoryUpdate rejects 64-character non-hex sha256 string`() = runTest {
        val invalid64CharNonHex = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"
        assertThat(AppUpdateRepository.extractExpectedSha256(invalid64CharNonHex)).isEmpty()

        val json = """
            {
              "versionCode": 50300,
              "versionName": "3.0.20300-main-release",
              "apkFileName": "main-LaTorrentola-v3.0.20300-release.apk",
              "apkUrl": "https://updates.example.com/apks/main-LaTorrentola-v3.0.20300-release.apk",
              "sha256": "$invalid64CharNonHex",
              "fileSize": 1024,
              "forceUpdate": true
            }
        """.trimIndent()

        mockHttpCall(code = 200, bodyBytes = json.toByteArray(), contentType = "application/json")

        val result = repository.checkForMandatoryUpdate(
            manifestUrl = "https://updates.example.com/apks/manifest.json",
            currentVersionCode = 50100,
            isDebugBuild = false
        )

        assertThat(result).isNull()
    }

    @Test
    fun `downloadAndVerifyApk emits Downloading, VerifyingChecksum, and ReadyToInstall when SHA256 matches`() = runTest {
        val apkBytes = "fake-signed-apk-binary-payload-content".toByteArray()
        val expectedSha256 = sha256Hex(apkBytes)
        val manifest = AppUpdateManifest(
            versionCode = 50300,
            versionName = "3.0.20300-main-release",
            apkFileName = "main-LaTorrentola-v3.0.20300-release.apk",
            apkUrl = "https://updates.example.com/apks/main-LaTorrentola-v3.0.20300-release.apk",
            sha256 = expectedSha256,
            fileSize = apkBytes.size.toLong()
        )

        mockHttpCall(
            code = 200,
            bodyBytes = apkBytes,
            contentType = "application/vnd.android.package-archive"
        )

        repository.downloadAndVerifyApk(manifest, cacheRootDir = tempDir).test {
            val initial = awaitItem()
            assertThat(initial).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat((initial as ApkDownloadState.Downloading).progressPercent).isEqualTo(0)

            val completedDownload = awaitItem()
            assertThat(completedDownload).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat((completedDownload as ApkDownloadState.Downloading).progressPercent).isEqualTo(100)

            val verifying = awaitItem()
            assertThat(verifying).isEqualTo(ApkDownloadState.VerifyingChecksum)

            val ready = awaitItem()
            assertThat(ready).isInstanceOf(ApkDownloadState.ReadyToInstall::class.java)
            val downloadedFile = (ready as ApkDownloadState.ReadyToInstall).apkFile
            assertThat(downloadedFile.exists()).isTrue()
            assertThat(downloadedFile.readBytes()).isEqualTo(apkBytes)

            awaitComplete()
        }
    }

    @Test
    fun `downloadAndVerifyApk deletes file and emits CHECKSUM_MISMATCH when SHA256 does not match`() = runTest {
        val apkBytes = "corrupted-apk-payload".toByteArray()
        val wrongSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        val manifest = AppUpdateManifest(
            versionCode = 50300,
            versionName = "3.0.20300-main-release",
            apkFileName = "main-LaTorrentola-v3.0.20300-release.apk",
            apkUrl = "https://updates.example.com/apks/main-LaTorrentola-v3.0.20300-release.apk",
            sha256 = wrongSha256,
            fileSize = apkBytes.size.toLong()
        )

        mockHttpCall(
            code = 200,
            bodyBytes = apkBytes,
            contentType = "application/vnd.android.package-archive"
        )

        repository.downloadAndVerifyApk(manifest, cacheRootDir = tempDir).test {
            assertThat(awaitItem()).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat(awaitItem()).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat(awaitItem()).isEqualTo(ApkDownloadState.VerifyingChecksum)

            val errorState = awaitItem()
            assertThat(errorState).isEqualTo(ApkDownloadState.Error(AppUpdateErrorType.CHECKSUM_MISMATCH))

            val updateDir = File(tempDir, "apk_updates")
            assertThat(updateDir.listFiles()?.toList().orEmpty()).isEmpty()

            awaitComplete()
        }
    }

    @Test
    fun `downloadAndVerifyApk emits NETWORK_OR_IO error when server returns HTTP 500`() = runTest {
        val manifest = AppUpdateManifest(
            versionCode = 50300,
            versionName = "3.0.20300-main-release",
            apkFileName = "main-LaTorrentola-v3.0.20300-release.apk",
            apkUrl = "https://updates.example.com/apks/main-LaTorrentola-v3.0.20300-release.apk",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            fileSize = 1024L
        )

        mockHttpCall(
            code = 500,
            bodyBytes = ByteArray(0),
            contentType = "text/plain"
        )

        repository.downloadAndVerifyApk(manifest, cacheRootDir = tempDir).test {
            assertThat(awaitItem()).isEqualTo(ApkDownloadState.Error(AppUpdateErrorType.NETWORK_OR_IO))
            awaitComplete()
        }
    }

    @Test
    fun `downloadAndVerifyApk sanitizes dot-only apkFileName to fallback file name`() = runTest {
        val apkBytes = "valid-apk-payload".toByteArray()
        val expectedSha256 = sha256Hex(apkBytes)
        val manifest = AppUpdateManifest(
            versionCode = 50400,
            versionName = "3.0.20400-main-release",
            apkFileName = "..",
            apkUrl = "https://updates.example.com/apks/release.apk",
            sha256 = expectedSha256,
            fileSize = apkBytes.size.toLong()
        )

        mockHttpCall(
            code = 200,
            bodyBytes = apkBytes,
            contentType = "application/vnd.android.package-archive"
        )

        repository.downloadAndVerifyApk(manifest, cacheRootDir = tempDir).test {
            assertThat(awaitItem()).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat(awaitItem()).isInstanceOf(ApkDownloadState.Downloading::class.java)
            assertThat(awaitItem()).isEqualTo(ApkDownloadState.VerifyingChecksum)

            val ready = awaitItem() as ApkDownloadState.ReadyToInstall
            assertThat(ready.apkFile.name).isEqualTo("LaTorrentola-v50400-release.apk")
            assertThat(ready.apkFile.isFile).isTrue()
            awaitComplete()
        }
    }

    private fun mockHttpCall(code: Int, bodyBytes: ByteArray, contentType: String) {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } answers {
            val req = firstArg<Request>()
            every { call.execute() } returns Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(bodyBytes.toResponseBody(contentType.toMediaType()))
                .build()
            call
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
