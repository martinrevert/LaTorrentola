package com.martinrevert.latorrentola.service.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageVolumeInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `FAT32 max file size is 4 GiB minus 1 byte`() {
        assertThat(StorageVolumeInspector.FAT32_MAX_FILE_SIZE_BYTES).isEqualTo(4_294_967_295L)
    }

    @Test
    fun `canStoreFileSize returns true for files smaller than 4 GiB`() {
        val root = tempFolder.root
        val fileSize = 1_500_000_000L // 1.5 GB
        assertThat(StorageVolumeInspector.canStoreFileSize(root, fileSize)).isTrue()
    }

    @Test
    fun `benchmarkWriteSpeed completes and cleans up benchmark file`() {
        val root = tempFolder.root
        val result = StorageVolumeInspector.benchmarkWriteSpeed(root)

        // Write speed should be positive and latency non-negative
        assertThat(result.writeSpeedMbPerSec).isAtLeast(0.0)
        assertThat(result.latencyMs).isAtLeast(0L)

        // Verify temp file was cleaned up
        val files = root.listFiles { _, name -> name.startsWith(".speed_test_") }
        assertThat(files).isEmpty()
    }
}
