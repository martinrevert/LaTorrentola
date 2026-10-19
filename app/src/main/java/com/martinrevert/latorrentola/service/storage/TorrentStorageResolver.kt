package com.martinrevert.latorrentola.service.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.martinrevert.latorrentola.utils.PreferenceManager
import java.io.File

/**
 * Metadata for an internal or external storage volume available for torrent downloads.
 *
 * @property id Unique volume identifier used for persistence (e.g., [TorrentStorageResolver.LOCATION_INTERNAL] or root path).
 * @property displayName Human-readable label (e.g. "Internal Storage" or "USB Drive (64 GB)").
 * @property rootDirectory Top-level writable directory for this app on the volume.
 * @property isRemovable True if the storage medium is removable (e.g. USB flash drive or SD card).
 * @property availableBytes Currently available free bytes on this volume.
 * @property totalBytes Total capacity in bytes of this volume.
 */
data class StorageLocation(
    val id: String,
    val displayName: String,
    val rootDirectory: File,
    val isRemovable: Boolean,
    val availableBytes: Long,
    val totalBytes: Long
)

/**
 * Resolves and validates torrent download and subtitle directories across internal eMMC and USB storage.
 */
object TorrentStorageResolver {

    const val LOCATION_INTERNAL = "internal"
    const val TORRENT_DIRECTORY = "torrent_downloads"

    /**
     * Enumerates all writable storage volumes currently accessible to the application.
     */
    fun getAvailableStorageLocations(context: Context): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()

        // 1. Always include Internal Storage
        val internalFilesDir = context.filesDir
        val internalStat = getStatFs(internalFilesDir)
        locations.add(
            StorageLocation(
                id = LOCATION_INTERNAL,
                displayName = "Internal Storage",
                rootDirectory = internalFilesDir,
                isRemovable = false,
                availableBytes = getStatAvailableBytes(internalStat),
                totalBytes = getStatTotalBytes(internalStat)
            )
        )

        // 2. Discover external volumes via getExternalFilesDirs(null)
        val externalDirs = try {
            ContextCompat.getExternalFilesDirs(context, null)
        } catch (_: Throwable) {
            emptyArray()
        }
        val storageManager = try {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        } catch (_: Throwable) {
            null
        }

        externalDirs.filterNotNull().forEachIndexed { index, dir ->
            if (dir.exists() && dir.canWrite()) {
                val isRemovable = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && storageManager != null) {
                        val storageVolume = storageManager.getStorageVolume(dir)
                        storageVolume?.isRemovable ?: Environment.isExternalStorageRemovable(dir)
                    } else {
                        Environment.isExternalStorageRemovable(dir)
                    }
                } catch (_: Throwable) {
                    false
                }

                // If it's a removable USB/SD drive or secondary volume
                if (isRemovable || index > 0) {
                    val stat = getStatFs(dir)
                    val available = getStatAvailableBytes(stat)
                    val total = getStatTotalBytes(stat)
                    val volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageManager != null) {
                        try {
                            storageManager.getStorageVolume(dir)?.getDescription(context)
                        } catch (_: Throwable) {
                            null
                        }
                    } else null

                    val label = volumeName ?: if (isRemovable) "USB Drive (${formatSizeSimple(total)})" else "External Storage ($index)"

                    locations.add(
                        StorageLocation(
                            id = dir.absolutePath,
                            displayName = label,
                            rootDirectory = dir,
                            isRemovable = isRemovable,
                            availableBytes = available,
                            totalBytes = total
                        )
                    )
                }
            }
        }

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
        if (selected != null && selected.rootDirectory.exists() && selected.rootDirectory.canWrite()) {
            return selected.rootDirectory
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

        // 3. Search all external files dirs
        val externalDirs = try {
            ContextCompat.getExternalFilesDirs(context, null)
        } catch (_: Throwable) {
            emptyArray()
        }
        for (ext in externalDirs) {
            if (ext != null && ext.isDirectory) {
                val candidate = File(ext, "$TORRENT_DIRECTORY/$infoHash")
                if (candidate.isDirectory) return candidate
            }
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

            // Check external storage parents
            val externalDirs = try {
                ContextCompat.getExternalFilesDirs(context, null)
            } catch (_: Throwable) {
                emptyArray()
            }
            for (ext in externalDirs) {
                if (ext != null) {
                    val extParent = File(ext, TORRENT_DIRECTORY).canonicalFile
                    if (canonicalParent == extParent) return true
                }
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
