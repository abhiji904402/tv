package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChunkedStorageAndOtaManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PulseCast Signage", appName)
    }

    @Test
    fun `ota update engine automatically rolls back on corrupt package verification failure`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ChunkedStorageAndOtaManager(context)

        // First install a valid v1.1.0 update
        val successState = manager.downloadVerifyAndApplyOtaUpdate(
            releaseId = "rel_valid_110",
            targetVersionCode = 2L,
            targetVersionName = "1.1.0",
            expectedChecksum = "valid_sha256_hash_12345678",
            chunkCount = 2,
            simulateCorruptPackage = false
        ) { _, _, _ -> }

        assertEquals("INSTALLED", successState.otaStatus)
        assertEquals(2L, successState.installedVersionCode)
        assertEquals("1.1.0", successState.installedVersionName)

        // Now push a corrupt v1.2.0 update and verify automatic rollback to v1.1.0
        val rolledBackState = manager.downloadVerifyAndApplyOtaUpdate(
            releaseId = "rel_corrupt_120",
            targetVersionCode = 3L,
            targetVersionName = "1.2.0",
            expectedChecksum = "CORRUPT_HEADER",
            chunkCount = 2,
            simulateCorruptPackage = true
        ) { _, _, _ -> }

        assertEquals("ROLLED_BACK", rolledBackState.otaStatus)
        assertEquals(2L, rolledBackState.installedVersionCode)
        assertEquals("1.1.0", rolledBackState.installedVersionName)
        assertTrue(rolledBackState.lastOtaMessage.contains("Auto-Rollback"))
    }
}
