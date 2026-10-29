package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class LiveState(
    @DocumentId val id: String = "live_state",
    val userId: String = "",
    val orientation: String = "LANDSCAPE",
    val scaleMode: String = "FILL",
    @get:PropertyName("isMuted") @PropertyName("isMuted") val isMuted: Boolean = false,
    val kioskLocked: Boolean = true,
    val activeZoneId: String = "zone_lobby",
    val adminPin: String = "1234",
    val lastPushCommand: String = "READY",
    val activeOtaVersionCode: Long = 1L,
    val activeOtaVersionName: String = "1.0.0",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlaylistVideo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceUrl: String = "",
    val backupUrl: String = "",
    val localFileName: String = "",
    val zoneId: String = "zone_lobby",
    val sizeBytes: Long = 0L,
    val chunkCount: Long = 1L,
    @get:PropertyName("isVerifiedCached") @PropertyName("isVerifiedCached") val isVerifiedCached: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SignageDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val pairingCode: String = "000000",
    val deviceName: String = "Android TV Display",
    val zoneId: String = "zone_lobby",
    @get:PropertyName("isPaired") @PropertyName("isPaired") val isPaired: Boolean = false,
    @get:PropertyName("isOnline") @PropertyName("isOnline") val isOnline: Boolean = true,
    val lastHeartbeatEpochSec: Long = 0L,
    val currentVideoTitle: String = "Idle",
    val cachedVideoCount: Long = 0L,
    val installedVersionCode: Long = 1L,
    val installedVersionName: String = "1.0.0",
    val previousSlotVersionCode: Long = 1L,
    val previousSlotVersionName: String = "1.0.0",
    val otaStatus: String = "UP_TO_DATE",
    val lastOtaMessage: String = "Slot A Verified & Healthy",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlaybackZone(
    @DocumentId val id: String = "",
    val userId: String = "",
    val zoneName: String = "",
    val locationTag: String = "",
    val operatorName: String = "",
    val operatorRole: String = "ZONE_OPERATOR",
    val canPushPlaylist: Boolean = true,
    val canRotateScreen: Boolean = true,
    val canManageOta: Boolean = false,
    val orientationOverride: String = "INHERIT",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OtaRelease(
    @DocumentId val id: String = "",
    val userId: String = "",
    val versionCode: Long = 1L,
    val versionName: String = "1.0.0",
    val releaseNotes: String = "",
    val targetZoneId: String = "ALL",
    val packageUrl: String = "",
    val sha256Checksum: String = "",
    val sizeBytes: Long = 1024L,
    val chunkCount: Long = 1L,
    @get:PropertyName("isMandatory") @PropertyName("isMandatory") val isMandatory: Boolean = true,
    val simulateCorruptPackage: Boolean = false,
    val releaseStatus: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DiagnosticLogEntry(
    val id: String,
    val timestampFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    RECOVERY,
    OTA
}
