package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LiveState
import com.example.data.model.OtaRelease
import com.example.data.model.PlaybackZone
import com.example.data.model.PlaylistVideo
import com.example.data.model.SignageDevice
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SignageRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // ------------------------------------------------------------------------
    // 1. Live Broadcast State (users/{userId}/cms_global/live_state)
    // ------------------------------------------------------------------------

    fun observeLiveState(userId: String): Flow<LiveState?> {
        val docRef = db.collection("users").document(userId)
            .collection("cms_global").document("live_state")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(LiveState::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun getLiveState(userId: String): Result<LiveState?> {
        val docRef = db.collection("users").document(userId)
            .collection("cms_global").document("live_state")
        return try {
            val snapshot = docRef.get().await()
            val state = if (snapshot.exists()) {
                snapshot.toObject(LiveState::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun ensureLiveStateInitialized(): Result<LiveState> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("cms_global").document("live_state")
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val parsed = existing.toObject(LiveState::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: LiveState(userId = uid)
                Result.success(parsed)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "orientation" to "LANDSCAPE",
                    "scaleMode" to "FILL",
                    "isMuted" to false,
                    "kioskLocked" to true,
                    "activeZoneId" to "zone_lobby",
                    "adminPin" to "1234",
                    "lastPushCommand" to "INITIALIZED",
                    "activeOtaVersionCode" to 1L,
                    "activeOtaVersionName" to "1.0.0",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(LiveState(userId = uid))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateLiveStateFields(updates: Map<String, Any>): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("cms_global").document("live_state")
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                ensureLiveStateInitialized().getOrThrow()
            }
            val payload = updates + mapOf("updatedAt" to FieldValue.serverTimestamp())
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // 2. Playlist Videos (users/{userId}/videos/{videoId})
    // ------------------------------------------------------------------------

    fun observeVideos(userId: String): Flow<List<PlaylistVideo>> {
        val colRef = db.collection("users").document(userId).collection("videos")
        return colRef.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PlaylistVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<PlaylistVideo> { it.sortOrder }.thenBy { it.createdAt ?: Timestamp(0, 0) })
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun addPlaylistVideo(
        title: String,
        sourceUrl: String,
        backupUrl: String,
        localFileName: String,
        zoneId: String,
        sizeBytes: Long,
        chunkCount: Long,
        isVerifiedCached: Boolean,
        sortOrder: Long
    ): Result<String> {
        val uid = requireUserId()
        val videoId = "vid_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("videos").document(videoId)
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120).ifEmpty { "Untitled Creative" },
            "sourceUrl" to sourceUrl.trim().take(1000),
            "backupUrl" to backupUrl.trim().take(1000).ifEmpty { sourceUrl.trim().take(1000) },
            "localFileName" to localFileName.trim().take(200).ifEmpty { "$videoId.mp4" },
            "zoneId" to zoneId.trim().take(128).ifEmpty { "zone_lobby" },
            "sizeBytes" to sizeBytes.coerceAtLeast(0L),
            "chunkCount" to chunkCount.coerceAtLeast(1L),
            "isVerifiedCached" to isVerifiedCached,
            "sortOrder" to sortOrder.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markVideoVerifiedCached(videoId: String, localFileName: String, isCached: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("videos").document(videoId)
        return try {
            docRef.update(
                mapOf(
                    "localFileName" to localFileName.take(200),
                    "isVerifiedCached" to isCached,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylistVideo(videoId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("videos").document(videoId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // 3. Signage Display Devices (users/{userId}/devices/{deviceId})
    // ------------------------------------------------------------------------

    fun observeDevices(userId: String): Flow<List<SignageDevice>> {
        val colRef = db.collection("users").document(userId).collection("devices")
        return colRef.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SignageDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastHeartbeatEpochSec }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun registerOrHeartbeatDevice(
        deviceId: String,
        pairingCode: String,
        deviceName: String,
        zoneId: String,
        isPaired: Boolean,
        isOnline: Boolean,
        currentVideoTitle: String,
        cachedVideoCount: Long,
        installedVersionCode: Long,
        installedVersionName: String,
        previousSlotVersionCode: Long,
        previousSlotVersionName: String,
        otaStatus: String,
        lastOtaMessage: String
    ): Result<Unit> {
        val uid = requireUserId()
        val safeId = deviceId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifEmpty { "tv_default" }
        val docRef = db.collection("users").document(uid).collection("devices").document(safeId)
        val epochSec = System.currentTimeMillis() / 1000L
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "pairingCode" to pairingCode.take(6).padStart(6, '0'),
                    "deviceName" to deviceName.take(100).ifEmpty { "Android TV Display" },
                    "zoneId" to zoneId.take(128).ifEmpty { "zone_lobby" },
                    "isPaired" to isPaired,
                    "isOnline" to isOnline,
                    "lastHeartbeatEpochSec" to epochSec,
                    "currentVideoTitle" to currentVideoTitle.take(120),
                    "cachedVideoCount" to cachedVideoCount.coerceAtLeast(0L),
                    "installedVersionCode" to installedVersionCode.coerceAtLeast(1L),
                    "installedVersionName" to installedVersionName.take(40).ifEmpty { "1.0.0" },
                    "previousSlotVersionCode" to previousSlotVersionCode.coerceAtLeast(0L),
                    "previousSlotVersionName" to previousSlotVersionName.take(40),
                    "otaStatus" to otaStatus,
                    "lastOtaMessage" to lastOtaMessage.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "lastHeartbeatEpochSec" to epochSec,
                    "isOnline" to isOnline,
                    "currentVideoTitle" to currentVideoTitle.take(120),
                    "cachedVideoCount" to cachedVideoCount.coerceAtLeast(0L),
                    "installedVersionCode" to installedVersionCode.coerceAtLeast(1L),
                    "installedVersionName" to installedVersionName.take(40).ifEmpty { "1.0.0" },
                    "previousSlotVersionCode" to previousSlotVersionCode.coerceAtLeast(0L),
                    "previousSlotVersionName" to previousSlotVersionName.take(40),
                    "otaStatus" to otaStatus,
                    "lastOtaMessage" to lastOtaMessage.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDevicePairingAndZone(
        deviceId: String,
        deviceName: String,
        zoneId: String,
        isPaired: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            docRef.update(
                mapOf(
                    "deviceName" to deviceName.trim().take(100).ifEmpty { "Paired TV Display" },
                    "zoneId" to zoneId.trim().take(128).ifEmpty { "zone_lobby" },
                    "isPaired" to isPaired,
                    "isOnline" to true,
                    "lastHeartbeatEpochSec" to (System.currentTimeMillis() / 1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // 4. Playback Zones & Granular Permissions (users/{userId}/zones/{zoneId})
    // ------------------------------------------------------------------------

    fun observeZones(userId: String): Flow<List<PlaybackZone>> {
        val colRef = db.collection("users").document(userId).collection("zones")
        return colRef.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PlaybackZone::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.zoneName }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun createZone(
        zoneId: String = "zone_${UUID.randomUUID().toString().replace("-", "").take(8)}",
        zoneName: String,
        locationTag: String,
        operatorName: String,
        operatorRole: String,
        canPushPlaylist: Boolean,
        canRotateScreen: Boolean,
        canManageOta: Boolean,
        orientationOverride: String = "INHERIT"
    ): Result<String> {
        val uid = requireUserId()
        val safeId = zoneId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docRef = db.collection("users").document(uid).collection("zones").document(safeId)
        val payload = mapOf(
            "userId" to uid,
            "zoneName" to zoneName.trim().take(80).ifEmpty { "Main Lobby" },
            "locationTag" to locationTag.trim().take(120).ifEmpty { "Floor 1" },
            "operatorName" to operatorName.trim().take(80).ifEmpty { "Network Admin" },
            "operatorRole" to operatorRole,
            "canPushPlaylist" to canPushPlaylist,
            "canRotateScreen" to canRotateScreen,
            "canManageOta" to canManageOta,
            "orientationOverride" to orientationOverride,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateZonePermissions(
        zoneId: String,
        operatorRole: String,
        canPushPlaylist: Boolean,
        canRotateScreen: Boolean,
        canManageOta: Boolean,
        orientationOverride: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("zones").document(zoneId)
        return try {
            docRef.update(
                mapOf(
                    "operatorRole" to operatorRole,
                    "canPushPlaylist" to canPushPlaylist,
                    "canRotateScreen" to canRotateScreen,
                    "canManageOta" to canManageOta,
                    "orientationOverride" to orientationOverride,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteZone(zoneId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("zones").document(zoneId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------------
    // 5. OTA Software Update Releases (users/{userId}/ota_releases/{releaseId})
    // ------------------------------------------------------------------------

    fun observeOtaReleases(userId: String): Flow<List<OtaRelease>> {
        val colRef = db.collection("users").document(userId).collection("ota_releases")
        return colRef.whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(OtaRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.versionCode }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun publishOtaRelease(
        versionCode: Long,
        versionName: String,
        releaseNotes: String,
        targetZoneId: String,
        packageUrl: String,
        sha256Checksum: String,
        sizeBytes: Long,
        chunkCount: Long,
        isMandatory: Boolean,
        simulateCorruptPackage: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val releaseId = "ota_v${versionCode}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        val docRef = db.collection("users").document(uid).collection("ota_releases").document(releaseId)
        val payload = mapOf(
            "userId" to uid,
            "versionCode" to versionCode.coerceAtLeast(1L),
            "versionName" to versionName.trim().take(40).ifEmpty { "1.$versionCode.0" },
            "releaseNotes" to releaseNotes.trim().take(500).ifEmpty { "OTA Client Update & Watchdog Patch" },
            "targetZoneId" to targetZoneId.trim().take(128).ifEmpty { "ALL" },
            "packageUrl" to packageUrl.trim().take(1000).ifEmpty { "https://pulsecast.cdn/ota/client-v$versionName.apk" },
            "sha256Checksum" to sha256Checksum.trim().take(128).ifEmpty { "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" },
            "sizeBytes" to sizeBytes.coerceAtLeast(1L),
            "chunkCount" to chunkCount.coerceAtLeast(1L),
            "isMandatory" to isMandatory,
            "simulateCorruptPackage" to simulateCorruptPackage,
            "releaseStatus" to "ACTIVE",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(releaseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateOtaReleaseStatus(releaseId: String, status: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("ota_releases").document(releaseId)
        return try {
            docRef.update(
                mapOf(
                    "releaseStatus" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
