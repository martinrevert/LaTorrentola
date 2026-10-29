package com.martinrevert.latorrentola.service.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TorrentStorageResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = mockk(relaxed = true)
    private lateinit var internalDir: File

    @Before
    fun setUp() {
        internalDir = tempFolder.newFolder("internal_files")
        every { context.filesDir } returns internalDir
        every { context.getSharedPreferences("latorrentola_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.getString("storage_location", "internal") } returns "internal"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resolveStorageDirectory returns internal filesDir when location is internal`() {
        val resolved = TorrentStorageResolver.resolveStorageDirectory(context, TorrentStorageResolver.LOCATION_INTERNAL)
        assertThat(resolved).isEqualTo(internalDir)
    }

    @Test
    fun `resolveStorageDirectory falls back to internal filesDir when configured drive missing`() {
        val nonExistentPath = File(tempFolder.root, "missing_usb_drive").absolutePath
        val resolved = TorrentStorageResolver.resolveStorageDirectory(context, nonExistentPath)
        assertThat(resolved).isEqualTo(internalDir)
    }

    @Test
    fun `resolveTorrentDirectory constructs correct relative path`() {
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val torrentDir = TorrentStorageResolver.resolveTorrentDirectory(context, hash, TorrentStorageResolver.LOCATION_INTERNAL)
        assertThat(torrentDir.name).isEqualTo(hash)
        assertThat(torrentDir.parentFile?.name).isEqualTo(TorrentStorageResolver.TORRENT_DIRECTORY)
        assertThat(torrentDir.parentFile?.parentFile).isEqualTo(internalDir)
    }

    @Test
    fun `findTorrentDirectory returns directory if it exists on disk`() {
        val hash = "abcdef1234567890abcdef1234567890abcdef12"
        val torrentDir = File(internalDir, "${TorrentStorageResolver.TORRENT_DIRECTORY}/$hash")
        torrentDir.mkdirs()

        val found = TorrentStorageResolver.findTorrentDirectory(context, hash)
        assertThat(found).isNotNull()
        assertThat(found?.absolutePath).isEqualTo(torrentDir.absolutePath)
    }

    @Test
    fun `findTorrentDirectory returns null if directory does not exist anywhere`() {
        val hash = "nonexistent_hash_12345"
        val found = TorrentStorageResolver.findTorrentDirectory(context, hash)
        assertThat(found).isNull()
    }

    @Test
    fun `isValidTorrentDirectory returns true for directory within torrent_downloads`() {
        val validDir = File(internalDir, "${TorrentStorageResolver.TORRENT_DIRECTORY}/target_hash")
        assertThat(TorrentStorageResolver.isValidTorrentDirectory(context, validDir)).isTrue()
    }

    @Test
    fun `isValidTorrentDirectory returns false for arbitrary directory outside torrent_downloads`() {
        val rogueDir = File(tempFolder.root, "arbitrary_folder")
        assertThat(TorrentStorageResolver.isValidTorrentDirectory(context, rogueDir)).isFalse()
    }

    @Test
    fun `getAvailableStorageLocations includes internal storage location`() {
        val locations = TorrentStorageResolver.getAvailableStorageLocations(context)
        assertThat(locations).isNotEmpty()
        val internal = locations.firstOrNull { it.id == TorrentStorageResolver.LOCATION_INTERNAL }
        assertThat(internal).isNotNull()
        assertThat(internal?.isRemovable).isFalse()
    }

    @Test
    fun `isWritableDirectory returns true for writable folder and false for impossible path`() {
        val writable = tempFolder.newFolder("test_writable")
        assertThat(TorrentStorageResolver.isWritableDirectory(writable)).isTrue()

        val regularFile = tempFolder.newFile("regular_file.txt")
        val impossibleDir = File(regularFile, "invalid_child_dir")
        assertThat(TorrentStorageResolver.isWritableDirectory(impossibleDir)).isFalse()
    }

    @Test
    fun `getAvailableStorageLocations filters unmounted volumes and includes mounted removable volume`() {
        val storageManager: android.os.storage.StorageManager = mockk(relaxed = true)
        every { context.getSystemService(Context.STORAGE_SERVICE) } returns storageManager

        val mountedUsbDir = tempFolder.newFolder("mounted_usb")
        val mountedVolume: android.os.storage.StorageVolume = mockk(relaxed = true)
        every { mountedVolume.isPrimary } returns false
        every { mountedVolume.state } returns android.os.Environment.MEDIA_MOUNTED
        every { mountedVolume.isRemovable } returns true
        every { mountedVolume.directory } returns mountedUsbDir
        every { mountedVolume.getDescription(any()) } returns "SanDisk USB"

        val unmountedUsbDir = tempFolder.newFolder("unmounted_usb")
        val unmountedVolume: android.os.storage.StorageVolume = mockk(relaxed = true)
        every { unmountedVolume.isPrimary } returns false
        every { unmountedVolume.state } returns android.os.Environment.MEDIA_UNMOUNTED
        every { unmountedVolume.isRemovable } returns true
        every { unmountedVolume.directory } returns unmountedUsbDir

        val primaryVolume: android.os.storage.StorageVolume = mockk(relaxed = true)
        every { primaryVolume.isPrimary } returns true
        every { primaryVolume.state } returns android.os.Environment.MEDIA_MOUNTED

        every { storageManager.storageVolumes } returns listOf(primaryVolume, unmountedVolume, mountedVolume)

        val locations = TorrentStorageResolver.getAvailableStorageLocations(context)
        val usb = locations.firstOrNull { it.id == mountedUsbDir.absolutePath }
        assertThat(usb).isNotNull()
        assertThat(usb?.displayName).isEqualTo("SanDisk USB")
        assertThat(usb?.isRemovable).isTrue()

        // Unmounted volume should not be in locations
        assertThat(locations.any { it.id == unmountedUsbDir.absolutePath }).isFalse()
    }
}
