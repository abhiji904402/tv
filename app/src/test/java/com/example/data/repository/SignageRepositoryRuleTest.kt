package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SignageRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureLiveStateAndUpdate_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = SignageRepository(firestore, auth)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureLiveStateInitialized() }
        assertTrue(initResult.isSuccess)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateLiveStateFields(
                mapOf(
                    "orientation" to "ROTATE_90",
                    "scaleMode" to "ZOOM",
                    "isMuted" to true
                )
            )
        }
        assertTrue(updateResult.isSuccess)

        val liveState = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLiveState(aliceUid).first { it != null && it.orientation == "ROTATE_90" }
        }
        assertNotNull(liveState)
        assertEquals("ZOOM", liveState?.scaleMode)
        assertEquals(true, liveState?.isMuted)
    }

    @Test
    fun addPlaylistVideoAndObserve_authenticatedOwner_emitsVideo() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = SignageRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addPlaylistVideo(
                title = "Lobby Promo Loop",
                sourceUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                backupUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                localFileName = "lobby_promo.mp4",
                zoneId = "zone_lobby",
                sizeBytes = 1228800L,
                chunkCount = 2L,
                isVerifiedCached = true,
                sortOrder = 0L
            )
        }
        assertTrue(createResult.isSuccess)
        val videoId = createResult.getOrThrow()

        val videos = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVideos(aliceUid).first { list -> list.any { it.id == videoId } }
        }
        assertTrue(videos.any { it.id == videoId && it.isVerifiedCached })
    }

    @Test
    fun publishOtaReleaseAndObserve_authenticatedOwner_emitsRelease() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = SignageRepository(firestore, auth)

        val otaResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishOtaRelease(
                versionCode = 2L,
                versionName = "1.2.0",
                releaseNotes = "OTA Rollback Watchdog Upgrade",
                targetZoneId = "ALL",
                packageUrl = "https://pulsecast.cdn/ota/v1.2.0.apk",
                sha256Checksum = "a882eb632d141f5ff5b8db5fdbd36e058b17511498825a000c5ea574e8f3e1ae",
                sizeBytes = 2457600L,
                chunkCount = 4L,
                isMandatory = true,
                simulateCorruptPackage = false
            )
        }
        assertTrue(otaResult.isSuccess)
        val releaseId = otaResult.getOrThrow()

        val releases = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeOtaReleases(aliceUid).first { list -> list.any { it.id == releaseId } }
        }
        assertTrue(releases.any { it.id == releaseId && it.versionName == "1.2.0" })
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceLiveState_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SignageRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureLiveStateInitialized().getOrThrow() }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SignageRepository(firestore, auth)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getLiveState(aliceUid) }
        assertTrue(bobReadResult.isFailure)
        val ex = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun unauthenticatedAccess_observeVideos_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = SignageRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeVideos(aliceUid).first()
            }
            fail("Expected exception for unauthenticated query")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.signage@test.com"
        const val BOB_EMAIL = "bob.signage@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
