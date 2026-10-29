package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LiveState
import com.example.data.model.LogLevel
import com.example.data.model.OtaRelease
import com.example.data.model.PlaylistVideo
import com.example.data.remote.VercelCmsApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChunkTransferTelemetry(
    val isActive: Boolean = false,
    val label: String = "",
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val bytesTransferred: Long = 0L
)

class SignageViewModel(
    private val localManager: ChunkedStorageAndOtaManager,
    private val vercelClient: VercelCmsApiClient = VercelCmsApiClient()
) : ViewModel() {

    companion object {
        const val VERIFIED_BACKUP_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        const val SECONDARY_BACKUP_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    }

    private val _cmsServerUrl = MutableStateFlow(localManager.getCmsServerUrl())
    val cmsServerUrl: StateFlow<String> = _cmsServerUrl.asStateFlow()

    private val _isWebCmsConnected = MutableStateFlow(false)
    val isWebCmsConnected: StateFlow<Boolean> = _isWebCmsConnected.asStateFlow()

    private val _localDeviceId = MutableStateFlow(localManager.getOrCreateDeviceId())
    val localDeviceId: StateFlow<String> = _localDeviceId.asStateFlow()

    private val _localPairingCode = MutableStateFlow(localManager.getOrCreatePairingCode())
    val localPairingCode: StateFlow<String> = _localPairingCode.asStateFlow()

    private val _deviceName = MutableStateFlow(localManager.getOrCreateDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _assignedVideoId = MutableStateFlow("ALL")
    val assignedVideoId: StateFlow<String> = _assignedVideoId.asStateFlow()

    private val _liveState = MutableStateFlow(LiveState(userId = "vercel_web_cms"))
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private val _videos = MutableStateFlow<List<PlaylistVideo>>(emptyList())
    val videos: StateFlow<List<PlaylistVideo>> = _videos.asStateFlow()

    private val _otaSlotState = MutableStateFlow(localManager.getOtaSlotState())
    val otaSlotState: StateFlow<OtaSlotState> = _otaSlotState.asStateFlow()

    private val _otaProgressPercent = MutableStateFlow<Int?>(null)
    val otaProgressPercent: StateFlow<Int?> = _otaProgressPercent.asStateFlow()

    private val _chunkTelemetry = MutableStateFlow(ChunkTransferTelemetry())
    val chunkTelemetry: StateFlow<ChunkTransferTelemetry> = _chunkTelemetry.asStateFlow()

    private val _syncToastMessage = MutableStateFlow<String?>(null)
    val syncToastMessage: StateFlow<String?> = _syncToastMessage.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _currentlyPlayingTitle = MutableStateFlow("Standby (Empty Playlist)")
    val currentlyPlayingTitle: StateFlow<String> = _currentlyPlayingTitle.asStateFlow()

    private val _isUsingBackupStream = MutableStateFlow(false)
    val isUsingBackupStream: StateFlow<Boolean> = _isUsingBackupStream.asStateFlow()

    private val _lastSyncCycleCount = MutableStateFlow(0L)
    val lastSyncCycleCount: StateFlow<Long> = _lastSyncCycleCount.asStateFlow()

    private val _isPinDialogVisible = MutableStateFlow(false)
    val isPinDialogVisible: StateFlow<Boolean> = _isPinDialogVisible.asStateFlow()

    private val _isAdminPanelOpen = MutableStateFlow(false)
    val isAdminPanelOpen: StateFlow<Boolean> = _isAdminPanelOpen.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private var syncLoopJob: Job? = null
    private var otaProcessingInFlight = false
    private var lastSeenPushCommand: String = ""
    private val cornerTapTimestamps = mutableListOf<Long>()

    init {
        appendLog(
            LogLevel.INFO,
            "BOOT",
            "PulseCast Android TV Display Client initialized. Slot: ${_otaSlotState.value.activeSlot} (v${_otaSlotState.value.installedVersionName})"
        )
        appendLog(
            LogLevel.INFO,
            "WEB_CMS",
            "Connecting 2.5s real-time sync loop to Web CMS: ${_cmsServerUrl.value}"
        )
        startRealTimeSyncLoop()
    }

    private fun startRealTimeSyncLoop() {
        syncLoopJob?.cancel()
        syncLoopJob = viewModelScope.launch {
            while (isActive) {
                runSingleSyncCycle()
                delay(2500L)
            }
        }
    }

    fun forceImmediateSync() {
        viewModelScope.launch {
            appendLog(LogLevel.INFO, "WEB_CMS", "Forcing immediate sync with ${_cmsServerUrl.value}...")
            runSingleSyncCycle()
            showToast("Synced with ${_cmsServerUrl.value}")
        }
    }

    fun updateCmsServerUrl(newUrl: String) {
        val cleaned = localManager.setCmsServerUrl(newUrl)
        _cmsServerUrl.value = cleaned
        appendLog(LogLevel.INFO, "WEB_CMS", "Updated Web CMS Server URL -> $cleaned")
        forceImmediateSync()
    }

    fun updateDeviceName(newName: String) {
        val cleaned = localManager.setDeviceName(newName)
        _deviceName.value = cleaned
        appendLog(LogLevel.INFO, "DEVICE", "Updated TV Display Name -> $cleaned")
        forceImmediateSync()
    }

    fun regenerateSixDigitPairingCode() {
        val newCode = localManager.regeneratePairingCode()
        _localPairingCode.value = newCode
        appendLog(LogLevel.INFO, "PAIRING", "Generated new 6-digit pairing code: $newCode")
        showToast("New 6-Digit Pairing Code: $newCode")
        forceImmediateSync()
    }

    private suspend fun runSingleSyncCycle() {
        _lastSyncCycleCount.value += 1L
        val currentVideos = _videos.value
        val verifiedCount = currentVideos.count {
            it.isVerifiedCached || localManager.isFileVerifiedCached(it.localFileName)
        }.toLong()

        // 1. Poll & Heartbeat with Web CMS Dashboard (https://tv-br.vercel.app/api/heartbeat)
        val syncResult = vercelClient.syncHeartbeatWithWebCms(
            baseUrl = _cmsServerUrl.value,
            deviceId = _localDeviceId.value,
            pairingCode = _localPairingCode.value,
            deviceName = _deviceName.value,
            currentVideoTitle = _currentlyPlayingTitle.value,
            cachedVideoCount = verifiedCount,
            otaSlotState = _otaSlotState.value
        )

        syncResult.onSuccess { payload ->
            val wasConnected = _isWebCmsConnected.value
            _isWebCmsConnected.value = true
            if (!wasConnected) {
                appendLog(
                    LogLevel.INFO,
                    "WEB_CMS",
                    "Connected to Web CMS (${_cmsServerUrl.value}) — Code: ${_localPairingCode.value}"
                )
            }

            if (payload.deviceName.isNotBlank() && payload.deviceName != _deviceName.value) {
                _deviceName.value = localManager.setDeviceName(payload.deviceName)
            }

            val prevState = _liveState.value
            val nextState = payload.liveState
            _liveState.value = nextState
            _assignedVideoId.value = payload.assignedVideoId

            if (prevState.orientation != nextState.orientation) {
                appendLog(
                    LogLevel.INFO,
                    "DISPLAY",
                    "Web CMS changed device orientation -> ${nextState.orientation}"
                )
                showToast("Web CMS: Orientation -> ${nextState.orientation}")
            }
            if (prevState.scaleMode != nextState.scaleMode) {
                appendLog(
                    LogLevel.INFO,
                    "DISPLAY",
                    "Web CMS changed scale mode -> ${nextState.scaleMode}"
                )
                showToast("Web CMS: Scale -> ${nextState.scaleMode}")
            }
            if (prevState.isMuted != nextState.isMuted) {
                appendLog(
                    LogLevel.INFO,
                    "AUDIO",
                    if (nextState.isMuted) "Web CMS muted TV audio" else "Web CMS unmuted TV audio"
                )
                showToast(if (nextState.isMuted) "Web CMS: Audio Muted" else "Web CMS: Audio Live")
            }

            if (nextState.lastPushCommand.isNotBlank() &&
                nextState.lastPushCommand != lastSeenPushCommand
            ) {
                if (lastSeenPushCommand.isNotEmpty() &&
                    nextState.lastPushCommand.startsWith("PUSH_PLAY")
                ) {
                    _isUsingBackupStream.value = false
                    showToast("Web CMS Push & Play received!")
                    appendLog(LogLevel.INFO, "CMS_PUSH", "Executed remote Push & Play command (${nextState.lastPushCommand})")
                }
                lastSeenPushCommand = nextState.lastPushCommand
            }

            // Merge remote playlist videos while preserving local cached status
            val mergedVideos = payload.videos.map { remoteVid ->
                val isCached = localManager.isFileVerifiedCached(remoteVid.localFileName)
                remoteVid.copy(isVerifiedCached = isCached || remoteVid.isVerifiedCached)
            }
            _videos.value = mergedVideos

            // Check for remote OTA software updates or rollback triggers
            val latestActiveRelease = payload.otaReleases.firstOrNull { it.releaseStatus == "ACTIVE" }
            val currentOta = _otaSlotState.value
            if (!otaProcessingInFlight &&
                latestActiveRelease != null &&
                latestActiveRelease.id != currentOta.lastProcessedReleaseId &&
                latestActiveRelease.versionCode > currentOta.installedVersionCode
            ) {
                triggerBackgroundOtaInstallation(latestActiveRelease)
            } else if (!otaProcessingInFlight &&
                nextState.lastPushCommand.startsWith("OTA_ROLLBACK") &&
                currentOta.installedVersionCode > 1L
            ) {
                triggerManualOtaRollback("Remote rollback command from Web CMS Dashboard")
            }
        }.onFailure { err ->
            if (_isWebCmsConnected.value) {
                appendLog(
                    LogLevel.WARN,
                    "WEB_CMS",
                    "Web CMS unreachable (${err.message}). Continuing offline playback from local cache."
                )
            }
            _isWebCmsConnected.value = false
        }

        // 2. Ensure all active playlist videos are cached in 600KB chunks locally for offline playback (✔)
        val activeVideos = _videos.value
        for (video in activeVideos) {
            val isLocalOk = localManager.isFileVerifiedCached(video.localFileName)
            if (!isLocalOk && !_chunkTelemetry.value.isActive) {
                appendLog(
                    LogLevel.INFO,
                    "CACHE",
                    "Caching '${video.title}' to local offline storage in 600KB chunks..."
                )
                val result = localManager.syncRemoteVideoToLocalCache(
                    sourceUrl = video.sourceUrl,
                    targetFileName = video.localFileName.ifBlank { "${video.id}.mp4" }
                ) { chunk, total, bytes ->
                    _chunkTelemetry.value = ChunkTransferTelemetry(
                        isActive = true,
                        label = "Caching ${video.title} (600KB chunks)",
                        currentChunk = chunk,
                        totalChunks = total,
                        bytesTransferred = bytes
                    )
                }
                _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)
                result.onSuccess { cached ->
                    _videos.value = _videos.value.map {
                        if (it.id == video.id) {
                            it.copy(localFileName = cached.localFileName, isVerifiedCached = true)
                        } else it
                    }
                    appendLog(
                        LogLevel.INFO,
                        "CACHE",
                        "Verified offline cache (✔) for '${video.title}' (${cached.chunkCount} x 600KB chunks, SHA-256 ${cached.sha256Checksum.take(8)})"
                    )
                }
            }
        }
    }

    fun showToast(message: String) {
        _syncToastMessage.value = message
        viewModelScope.launch {
            delay(3200L)
            if (_syncToastMessage.value == message) {
                _syncToastMessage.value = null
            }
        }
    }

    // ------------------------------------------------------------------------
    // 5-Tap Hidden Corner Gesture & PIN-Protected Admin Panel
    // ------------------------------------------------------------------------

    fun onCornerSecretTap() {
        val now = System.currentTimeMillis()
        cornerTapTimestamps.removeAll { now - it > 3000L }
        cornerTapTimestamps.add(now)
        if (cornerTapTimestamps.size >= 5) {
            cornerTapTimestamps.clear()
            openAdminPinPrompt()
        }
    }

    fun openAdminPinPrompt() {
        _pinError.value = null
        _isPinDialogVisible.value = true
        appendLog(LogLevel.INFO, "SECURITY", "Admin PIN verification dialog opened")
    }

    fun dismissAdminPinPrompt() {
        _isPinDialogVisible.value = false
        _pinError.value = null
    }

    fun verifyAdminPin(enteredPin: String) {
        val expectedPin = _liveState.value.adminPin.ifBlank { "1234" }
        if (enteredPin.trim() == expectedPin) {
            _isPinDialogVisible.value = false
            _pinError.value = null
            _isAdminPanelOpen.value = true
            appendLog(LogLevel.INFO, "SECURITY", "Admin PIN verified. Unlocked Protected Admin Settings Panel.")
        } else {
            _pinError.value = "Invalid PIN code. Default operator PIN is $expectedPin."
            appendLog(LogLevel.WARN, "SECURITY", "Rejected invalid Admin PIN attempt")
        }
    }

    fun closeAdminPanel() {
        _isAdminPanelOpen.value = false
    }

    // ------------------------------------------------------------------------
    // Local Admin Overrides (Also Pushed to Web CMS Dashboard)
    // ------------------------------------------------------------------------

    fun setOrientation(orientation: String) {
        _liveState.value = _liveState.value.copy(orientation = orientation)
        viewModelScope.launch {
            vercelClient.pushDeviceControlUpdate(
                baseUrl = _cmsServerUrl.value,
                deviceId = _localDeviceId.value,
                fields = mapOf("orientation" to orientation)
            )
            appendLog(LogLevel.INFO, "DISPLAY", "Device orientation set to $orientation & synced to Web CMS")
            showToast("Orientation: $orientation")
        }
    }

    fun setScaleMode(scaleMode: String) {
        _liveState.value = _liveState.value.copy(scaleMode = scaleMode)
        viewModelScope.launch {
            vercelClient.pushDeviceControlUpdate(
                baseUrl = _cmsServerUrl.value,
                deviceId = _localDeviceId.value,
                fields = mapOf("scaleMode" to scaleMode)
            )
            appendLog(LogLevel.INFO, "DISPLAY", "Video scale mode set to $scaleMode & synced to Web CMS")
            showToast("Scale Mode: $scaleMode")
        }
    }

    fun toggleAudioMute(isMuted: Boolean) {
        _liveState.value = _liveState.value.copy(isMuted = isMuted)
        viewModelScope.launch {
            vercelClient.pushDeviceControlUpdate(
                baseUrl = _cmsServerUrl.value,
                deviceId = _localDeviceId.value,
                fields = mapOf("isMuted" to isMuted)
            )
            appendLog(LogLevel.INFO, "AUDIO", if (isMuted) "Display audio muted" else "Display audio unmuted")
            showToast(if (isMuted) "Audio Muted" else "Audio Unmuted")
        }
    }

    fun toggleKioskLock(locked: Boolean) {
        _liveState.value = _liveState.value.copy(kioskLocked = locked)
        viewModelScope.launch {
            vercelClient.updateGlobalStateFields(
                baseUrl = _cmsServerUrl.value,
                fields = mapOf("kioskLocked" to locked)
            )
            appendLog(LogLevel.INFO, "KIOSK", "Kiosk mode lock set to $locked")
            showToast(if (locked) "Kiosk Mode Locked" else "Kiosk Mode Unlocked")
        }
    }

    fun ingestLocalVideoForOfflineCache(uri: Uri, title: String) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifEmpty { "Offline Cached Ad ${(10..99).random()}" }
            val fileName = "local_${System.currentTimeMillis()}.mp4"
            appendLog(LogLevel.INFO, "CACHE", "Ingesting '$cleanTitle' in 600KB chunks to local offline storage...")

            val result = localManager.ingestLocalVideoUriInChunks(
                uri = uri,
                targetFileName = fileName
            ) { chunk, total, bytes ->
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    label = "Caching $cleanTitle (600KB chunks)",
                    currentChunk = chunk,
                    totalChunks = total,
                    bytesTransferred = bytes
                )
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

            result.onSuccess { uploaded ->
                val newVideo = PlaylistVideo(
                    id = "local_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    userId = "local_admin",
                    title = cleanTitle,
                    sourceUrl = VERIFIED_BACKUP_STREAM_URL,
                    backupUrl = SECONDARY_BACKUP_STREAM_URL,
                    localFileName = uploaded.localFileName,
                    zoneId = "ALL",
                    sizeBytes = uploaded.sizeBytes,
                    chunkCount = uploaded.chunkCount,
                    isVerifiedCached = true,
                    sortOrder = _videos.value.size.toLong()
                )
                _videos.value = _videos.value + newVideo
                appendLog(
                    LogLevel.INFO,
                    "CACHE",
                    "Saved '$cleanTitle' to offline storage (${uploaded.chunkCount} x 600KB chunks ✔)"
                )
                showToast("Cached '$cleanTitle' Offline (✔)")
            }
        }
    }

    fun deleteVideoFromPlaylist(video: PlaylistVideo) {
        _videos.value = _videos.value.filterNot { it.id == video.id }
        viewModelScope.launch {
            vercelClient.deleteVideoOnWebCms(_cmsServerUrl.value, video.id)
            appendLog(LogLevel.INFO, "PLAYLIST", "Removed '${video.title}' from playlist")
            showToast("Removed '${video.title}'")
        }
    }

    // ------------------------------------------------------------------------
    // ExoPlayer 3.5s Buffering Watchdog & Backup Stream Auto-Recovery
    // ------------------------------------------------------------------------

    fun onActiveVideoChanged(title: String) {
        _currentlyPlayingTitle.value = title
    }

    fun onBufferingWatchdogTriggered(stalledVideoTitle: String, reason: String) {
        _isUsingBackupStream.value = true
        appendLog(
            LogLevel.RECOVERY,
            "WATCHDOG",
            "3.5s Watchdog / Stream Error on '$stalledVideoTitle' ($reason) -> Auto-recovered via verified backup stream!"
        )
        showToast("Watchdog Auto-Recovery: Switched to verified backup stream")
    }

    // ------------------------------------------------------------------------
    // Over-the-Air (OTA) Software Update & Rollback Engine
    // ------------------------------------------------------------------------

    private fun triggerBackgroundOtaInstallation(release: OtaRelease) {
        if (otaProcessingInFlight) return
        otaProcessingInFlight = true

        viewModelScope.launch {
            try {
                appendLog(
                    LogLevel.OTA,
                    "OTA_CLIENT",
                    "Detected new OTA Release v${release.versionName} (Build ${release.versionCode}) from Web CMS. Starting background download..."
                )
                val finalState = localManager.downloadVerifyAndApplyOtaUpdate(
                    releaseId = release.id,
                    targetVersionCode = release.versionCode,
                    targetVersionName = release.versionName,
                    expectedChecksum = release.sha256Checksum,
                    chunkCount = release.chunkCount.toInt(),
                    simulateCorruptPackage = release.simulateCorruptPackage
                ) { status, message, progressPct ->
                    _otaSlotState.value = _otaSlotState.value.copy(
                        otaStatus = status,
                        lastOtaMessage = message
                    )
                    _otaProgressPercent.value = progressPct
                    appendLog(LogLevel.OTA, "OTA_$status", message)
                }

                _otaSlotState.value = finalState
                delay(1200)
                _otaProgressPercent.value = null

                if (finalState.otaStatus == "ROLLED_BACK") {
                    showToast("OTA Failed Verification -> Automatic Rollback to v${finalState.installedVersionName}!")
                } else {
                    showToast("OTA Updated to v${finalState.installedVersionName} (${finalState.activeSlot})")
                }
            } finally {
                otaProcessingInFlight = false
            }
        }
    }

    fun triggerManualOtaRollback(reason: String = "Manual operator rollback to previous verified slot") {
        if (otaProcessingInFlight) return
        viewModelScope.launch {
            appendLog(LogLevel.OTA, "ROLLBACK", "Executing A/B slot rollback: $reason")
            val rolledBack = localManager.rollbackToPreviousSlot(reason)
            _otaSlotState.value = rolledBack
            appendLog(LogLevel.OTA, "ROLLBACK", rolledBack.lastOtaMessage)
            showToast("Rolled back to v${rolledBack.installedVersionName} (${rolledBack.activeSlot})")
        }
    }

    // ------------------------------------------------------------------------
    // Diagnostic Log Ring Buffer
    // ------------------------------------------------------------------------

    fun appendLog(level: LogLevel, tag: String, message: String) {
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = DiagnosticLogEntry(
            id = UUID.randomUUID().toString(),
            timestampFormatted = timeFmt,
            level = level,
            tag = tag,
            message = message
        )
        _diagnosticLogs.value = (listOf(entry) + _diagnosticLogs.value).take(80)
    }

    fun clearDiagnosticLogs() {
        _diagnosticLogs.value = emptyList()
        appendLog(LogLevel.INFO, "LOGS", "Diagnostic log buffer cleared by operator")
    }

    override fun onCleared() {
        super.onCleared()
        syncLoopJob?.cancel()
    }
}
