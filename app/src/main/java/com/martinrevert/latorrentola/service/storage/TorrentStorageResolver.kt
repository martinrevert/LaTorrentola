package com.martinrevert.latorrentola.service.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import com.martinrevert.latorrentola.R
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.io.File

/**
 * Metadata for an internal or external storage volume available for torrent downloads and media browsing.
 *
 * @property id Unique volume identifier used for persistence (e.g., [TorrentStorageResolver.LOCATION_INTERNAL] or root path).
 * @property displayName Human-readable label (e.g. "Internal Storage" or "USB Drive (64 GB)").
 * @property rootDirectory Top-level directory for browsing files on this volume.
 * @property isRemovable True if the storage medium is removable (e.g. USB flash drive or SD card).
 * @property availableBytes Currently available free bytes on this volume.
 * @property totalBytes Total capacity in bytes of this volume.
 * @property downloadDirectory Target directory to store downloaded torrents (defaults to [rootDirectory] or app-specific directory if root is read-only).
 */
data class StorageLocation(
    val id: String,
    val displayName: String,
    val rootDirectory: File,
    val isRemovable: Boolean,
    val availableBytes: Long,
    val totalBytes: Long,
    val downloadDirectory: File = rootDirectory
)

/**
 * Resolves and validates torrent download, subtitle directories, and removable USB media across Android devices.
 */
object TorrentStorageResolver {

    private const val TAG = "TorrentStorageResolver"
    const val LOCATION_INTERNAL = "internal"
    const val TORRENT_DIRECTORY = "torrent_downloads"

    private fun logI(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {}
    }

    private fun logW(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(TAG, msg, tr) else Log.w(TAG, msg)
        } catch (_: Throwable) {}
    }

    private data class DiscoveredVolume(
        val mountRoot: File,
        val appDir: File? = null,
        val description: String? = null,
        val isRemovable: Boolean = true
    )

    /**
     * Enumerates all storage volumes (Internal shared/private, Removable USB, External SD)
     * currently mounted and physically accessible on the device.
     * Uses Android's official StorageManager and external files directories APIs,
     * ensuring phantom/unmounted USBs are never reported.
     */
    fun getAvailableStorageLocations(context: Context): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()
        val seenPaths = mutableSetOf<String>()

        // 1. Always include Internal Storage
        val internalFilesDir = context.filesDir
        val internalStat = getStatFs(internalFilesDir)
        val internalSharedDir = try {
            Environment.getExternalStorageDirectory()
        } catch (_: Throwable) {
            null
        }
        val internalBrowseDir = if (internalSharedDir != null && internalSharedDir.exists() && internalSharedDir.canRead()) {
            internalSharedDir
        } else {
            internalFilesDir
        }

        val internalLabel = try {
            context.getString(R.string.settings_storage_internal)
        } catch (_: Throwable) {
            "Internal Storage"
        }

        locations.add(
            StorageLocation(
                id = LOCATION_INTERNAL,
                displayName = internalLabel,
                rootDirectory = internalBrowseDir,
                isRemovable = false,
                availableBytes = getStatAvailableBytes(internalStat),
                totalBytes = getStatTotalBytes(internalStat),
                downloadDirectory = internalFilesDir
            )
        )

        try {
            seenPaths.add(internalFilesDir.canonicalPath)
            if (internalSharedDir != null && internalSharedDir.exists()) {
                seenPaths.add(internalSharedDir.canonicalPath)
            }
        } catch (_: Throwable) {
            seenPaths.add(internalFilesDir.absolutePath)
        }

        val discoveredVolumes = mutableListOf<DiscoveredVolume>()

        // 2. Discover via StorageManager.storageVolumes (authoritative API on Android 7.0+ / 10+)
        val storageManager = try {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        } catch (_: Throwable) {
            null
        }

        if (storageManager != null) {
            try {
                for (volume in storageManager.storageVolumes) {
                    if (volume.isPrimary) continue

                    val state = volume.state
                    if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                        continue
                    }

                    val dir: File? = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || Build.VERSION.SDK_INT == 0) {
                        try {
                            volume.directory
                        } catch (_: Throwable) {
                            null
                        }
                    } else {
                        null
                    }) ?: run {
                        try {
                            val getPath = volume.javaClass.getMethod("getPath")
                            (getPath.invoke(volume) as? String)?.let { File(it) }
                        } catch (_: Throwable) {
                            null
                        }
                    } ?: volume.uuid?.let { File("/storage", it) }

                    if (dir != null && dir.exists()) {
                        val desc = try {
                            volume.getDescription(context)
                        } catch (_: Throwable) {
                            null
                        }
                        logI("StorageVolumes: found volume dir=${dir.absolutePath}, state=$state, desc=$desc, removable=${volume.isRemovable}")
                        discoveredVolumes.add(
                            DiscoveredVolume(
                                mountRoot = dir,
                                description = desc,
                                isRemovable = volume.isRemovable
                            )
                        )
                    }
                }
            } catch (e: Throwable) {
                logW("Error enumerating storageVolumes", e)
            }
        }

        // 3. Discover via context.getExternalFilesDirs(null) for mounted secondary drives
        val externalDirs = try {
            context.getExternalFilesDirs(null)
        } catch (_: Throwable) {
            emptyArray()
        }

        for (extDir in externalDirs) {
            if (extDir == null || !extDir.exists()) continue
            val path = extDir.absolutePath
            val androidDataIdx = path.indexOf("/Android/data", ignoreCase = true)
            val volumeRoot = if (androidDataIdx > 0) {
                File(path.substring(0, androidDataIdx))
            } else {
                extDir
            }

            if (!volumeRoot.exists()) continue

            val sv = try {
                storageManager?.getStorageVolume(volumeRoot)
            } catch (_: Throwable) {
                null
            }

            if (sv != null) {
                if (sv.isPrimary) continue
                if (sv.state != Environment.MEDIA_MOUNTED && sv.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
            }

            val isRemovable = sv?.isRemovable
                ?: try { Environment.isExternalStorageRemovable(volumeRoot) } catch (_: Throwable) { true }

            if (!isRemovable && sv?.isPrimary == true) continue

            val desc = try {
                sv?.getDescription(context)
            } catch (_: Throwable) {
                null
            }
            logI("getExternalFilesDirs: found extDir=${extDir.absolutePath}, volumeRoot=${volumeRoot.absolutePath}, removable=$isRemovable")
            discoveredVolumes.add(
                DiscoveredVolume(
                    mountRoot = volumeRoot,
                    appDir = extDir,
                    description = desc,
                    isRemovable = isRemovable
                )
            )
        }

        // Deduplicate and assemble StorageLocations
        for (vol in discoveredVolumes) {
            val canonical = try { vol.mountRoot.canonicalPath } catch (_: Throwable) { vol.mountRoot.absolutePath }
            if (seenPaths.contains(canonical)) continue
            seenPaths.add(canonical)

            val probeDir = if (vol.appDir != null && vol.appDir.exists()) vol.appDir else vol.mountRoot
            val stat = getStatFs(probeDir)
            val available = getStatAvailableBytes(stat)
            val total = getStatTotalBytes(stat)

            // Validate that the volume is genuinely mounted and readable
            if (total <= 0L && !vol.mountRoot.canRead() && (vol.appDir == null || !vol.appDir.exists())) {
                logW("Skipping non-readable or unmounted volume: ${vol.mountRoot.absolutePath}")
                continue
            }

            val label = vol.description
                ?: if (vol.isRemovable) {
                    if (total > 0L) {
                        try {
                            context.getString(R.string.usb_drive_formatted, formatSizeSimple(total))
                        } catch (_: Throwable) {
                            "USB Drive (${formatSizeSimple(total)})"
                        }
                    } else {
                        try {
                            context.getString(R.string.usb_drive_named, vol.mountRoot.name)
                        } catch (_: Throwable) {
                            "USB Drive (${vol.mountRoot.name})"
                        }
                    }
                } else {
                    try {
                        context.getString(R.string.external_storage_named, vol.mountRoot.name)
                    } catch (_: Throwable) {
                        "External Storage (${vol.mountRoot.name})"
                    }
                }

            val appDirCandidate = vol.appDir ?: File(vol.mountRoot, "Android/data/${context.packageName}/files")
            val downloadDir = if (isWritableDirectory(vol.mountRoot)) {
                vol.mountRoot
            } else {
                try { appDirCandidate.mkdirs() } catch (_: Throwable) {}
                if (isWritableDirectory(appDirCandidate)) {
                    appDirCandidate
                } else {
                    vol.mountRoot
                }
            }

            locations.add(
                StorageLocation(
                    id = vol.mountRoot.absolutePath,
                    displayName = label,
                    rootDirectory = vol.mountRoot,
                    isRemovable = vol.isRemovable,
                    availableBytes = available,
                    totalBytes = total,
                    downloadDirectory = downloadDir
                )
            )
        }

        logI("getAvailableStorageLocations: returning ${locations.size} locations: ${locations.map { "${it.displayName} [${it.rootDirectory.absolutePath}]" }}")
        return locations
    }

    /**
     * Resolves the root directory to store torrent files based on user preferences.
     * If the selected external drive is currently disconnected, falls back to internal storage safely.
     */
    fun resolveStorageDirectory(context: Context, locationId: String? = null): File {
        val targetId = locationId ?: try {
            PreferenceManager(context).getStorageLocation()
        } catch (_: Throwable) {
            LOCATION_INTERNAL
        }
        if (targetId == LOCATION_INTERNAL) {
            return context.filesDir
        }

        val availableLocations = getAvailableStorageLocations(context)
        val selected = availableLocations.firstOrNull { it.id == targetId }
        if (selected != null) {
            val downloadDir = selected.downloadDirectory
            if (isWritableDirectory(downloadDir)) {
                return downloadDir
            }
            if (isWritableDirectory(selected.rootDirectory)) {
                return selected.rootDirectory
            }
        }

        // Fallback to internal storage if configured volume is unavailable
        return context.filesDir
    }

    /**
     * Resolves the full directory for a specific torrent info-hash.
     */
    fun resolveTorrentDirectory(context: Context, infoHash: String, locationId: String? = null): File {
        val root = resolveStorageDirectory(context, locationId)
        return File(root, "$TORRENT_DIRECTORY/$infoHash")
    }

    /**
     * Searches across all active storage locations (internal and all external volumes)
     * to locate an existing torrent directory for [infoHash].
     */
    fun findTorrentDirectory(context: Context, infoHash: String): File? {
        // 1. Check currently active preference location first
        try {
            val activeDir = resolveTorrentDirectory(context, infoHash)
            if (activeDir.isDirectory) return activeDir
        } catch (_: Throwable) {}

        // 2. Check internal storage
        val internalDir = File(context.filesDir, "$TORRENT_DIRECTORY/$infoHash")
        if (internalDir.isDirectory) return internalDir

        // 3. Search all available storage locations
        val locations = try {
            getAvailableStorageLocations(context)
        } catch (_: Throwable) {
            emptyList()
        }
        for (loc in locations) {
            val candidateDownload = File(loc.downloadDirectory, "$TORRENT_DIRECTORY/$infoHash")
            if (candidateDownload.isDirectory) return candidateDownload

            val candidateRoot = File(loc.rootDirectory, "$TORRENT_DIRECTORY/$infoHash")
            if (candidateRoot.isDirectory) return candidateRoot
        }

        return null
    }

    /**
     * Returns true if [directory] is located within a legitimate [TORRENT_DIRECTORY]
     * on either internal storage or any mounted external volume.
     */
    fun isValidTorrentDirectory(context: Context, directory: File): Boolean {
        return try {
            val canonicalTarget = directory.canonicalFile
            val canonicalParent = canonicalTarget.parentFile ?: return false

            // Check internal storage parent
            val internalParent = File(context.filesDir, TORRENT_DIRECTORY).canonicalFile
            if (canonicalParent == internalParent) return true

            // Check all available locations
            val locations = try {
                getAvailableStorageLocations(context)
            } catch (_: Throwable) {
                emptyList()
            }
            for (loc in locations) {
                val dlParent = File(loc.downloadDirectory, TORRENT_DIRECTORY).canonicalFile
                if (canonicalParent == dlParent) return true

                val rootParent = File(loc.rootDirectory, TORRENT_DIRECTORY).canonicalFile
                if (canonicalParent == rootParent) return true
            }

            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns the available free bytes on the active (or specified) storage volume.
     */
    fun getAvailableStorageBytes(context: Context, locationId: String? = null): Long {
        return try {
            val dir = resolveStorageDirectory(context, locationId)
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Safely verifies if an application can write to [dir].
     */
    fun isWritableDirectory(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (!dir.exists()) return false
            val testFile = File(dir, ".torrent_test_${System.currentTimeMillis()}.tmp")
            val created = testFile.createNewFile()
            if (created || testFile.exists()) {
                testFile.delete()
                true
            } else {
                dir.canWrite()
            }
        } catch (_: Throwable) {
            try { dir.canWrite() } catch (_: Throwable) { false }
        }
    }

    private fun getStatFs(dir: File): StatFs? {
        return try {
            if (dir.exists()) StatFs(dir.absolutePath) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getStatAvailableBytes(stat: StatFs?): Long {
        return try {
            stat?.availableBytes ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun getStatTotalBytes(stat: StatFs?): Long {
        return try {
            stat?.totalBytes ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun formatSizeSimple(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(java.util.Locale.US, "%.0f GB", gb)
        else "${(bytes / (1024 * 1024))} MB"
    }
}
