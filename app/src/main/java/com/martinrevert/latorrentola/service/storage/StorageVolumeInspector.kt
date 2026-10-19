package com.martinrevert.latorrentola.service.storage

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Result of a quick storage write benchmark to verify drive performance.
 *
 * @property writeSpeedMbPerSec Estimated sequential/chunk write throughput in MB/s.
 * @property latencyMs Time taken to flush writes to disk.
 * @property isSlow True if the storage medium has poor random write performance (< 3 MB/s).
 */
data class StorageSpeedResult(
    val writeSpeedMbPerSec: Double,
    val latencyMs: Long,
    val isSlow: Boolean
)

/**
 * Inspects filesystem characteristics of storage volumes (e.g., FAT32 4 GiB limits and drive write speeds).
 */
object StorageVolumeInspector {

    /** Maximum single file size allowed by the FAT32 filesystem (4 GiB - 1 byte). */
    const val FAT32_MAX_FILE_SIZE_BYTES = 4_294_967_295L // 4 GiB - 1

    /** Minimum recommended random write speed for concurrent streaming and downloading. */
    const val MIN_RECOMMENDED_WRITE_SPEED_MBPS = 3.0

    /**
     * Determines whether the given directory resides on a FAT32/vfat filesystem.
     * Parses `/proc/mounts` on Linux/Android.
     */
    fun isFat32(directory: File): Boolean {
        return try {
            val canonicalPath = directory.canonicalPath
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                val lines = mountsFile.readLines()
                // Find mount point with the longest prefix match for the path
                val matchingMount = lines
                    .map { it.split("\\s+".toRegex()) }
                    .filter { it.size >= 3 }
                    .filter { canonicalPath.startsWith(it[1]) }
                    .maxByOrNull { it[1].length }

                if (matchingMount != null) {
                    val fsType = matchingMount[2].lowercase()
                    return fsType == "vfat" || fsType == "fat32" || fsType == "msdos"
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validates whether a file of [requiredFileSizeBytes] can be saved to [targetDirectory].
     * Returns false if the directory is on a FAT32 filesystem and the file size exceeds 4 GiB.
     */
    fun canStoreFileSize(targetDirectory: File, requiredFileSizeBytes: Long): Boolean {
        if (requiredFileSizeBytes >= FAT32_MAX_FILE_SIZE_BYTES) {
            if (isFat32(targetDirectory)) {
                return false
            }
        }
        return true
    }

    /**
     * Performs a fast write self-test (writing 2 MB in 16 KB blocks) to test drive throughput and latency.
     * Safely deletes the temporary benchmark file upon completion.
     */
    fun benchmarkWriteSpeed(targetDirectory: File): StorageSpeedResult {
        val testFile = File(targetDirectory, ".speed_test_${System.currentTimeMillis()}.tmp")
        val chunkSize = 16 * 1024 // 16 KB chunks matching BitTorrent block size
        val totalBytes = 2 * 1024 * 1024 // 2 MB total
        val numChunks = totalBytes / chunkSize
        val chunkData = ByteArray(chunkSize) { (it % 127).toByte() }

        var start = 0L
        var end = 0L

        return try {
            if (!targetDirectory.exists()) targetDirectory.mkdirs()
            start = System.currentTimeMillis()
            FileOutputStream(testFile).use { fos ->
                for (i in 0 until numChunks) {
                    fos.write(chunkData)
                }
                fos.fd.sync() // Ensure flushed to physical media
            }
            end = System.currentTimeMillis()

            val durationMs = maxOf(1L, end - start)
            val durationSec = durationMs / 1000.0
            val speedMbPerSec = (totalBytes.toDouble() / (1024.0 * 1024.0)) / durationSec

            StorageSpeedResult(
                writeSpeedMbPerSec = speedMbPerSec,
                latencyMs = durationMs,
                isSlow = speedMbPerSec < MIN_RECOMMENDED_WRITE_SPEED_MBPS
            )
        } catch (_: Exception) {
            StorageSpeedResult(
                writeSpeedMbPerSec = 0.0,
                latencyMs = -1L,
                isSlow = true
            )
        } finally {
            try {
                if (testFile.exists()) testFile.delete()
            } catch (_: Exception) {}
        }
    }
}
