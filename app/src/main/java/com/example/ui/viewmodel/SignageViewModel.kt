package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.local.SavedDisplaySettings
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
    val videoId: String = "",
    val label: String = "",
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val bytesTransferred: Long = 0L,
    val progressPercent: Int = 0
)

class SignageViewModel(
    private val localManager: ChunkedStorageAndOtaManager,
    private val vercelClient: VercelCmsApiClient = VercelCmsApiClient()
) : ViewModel() {

    private val initialSavedSettings = localManager.getSavedDisplaySettings()
    private val initialSavedVideos = localManager.getSavedPlaylistVideos()

    private val _cmsServerUrl = MutableStateFlow(localManager.getCmsServerUrl())
    val cmsServerUrl: StateFlow<String> = _cmsServerUrl.asStateFlow()

    private val _isWebCmsConnected = MutableStateFlow(false)
    val isWebCmsConnected: StateFlow<Boolean> = _isWebCmsConnected.asStateFlow()

    private val _isPairedWithCms = MutableStateFlow(true)
    val isPairedWithCms: StateFlow<Boolean> = _isPairedWithCms.asStateFlow()

    private val _localDeviceId = MutableStateFlow(localManager.getOrCreateDeviceId())
    val localDeviceId: StateFlow<String> = _localDeviceId.asStateFlow()

    private val _localPairingCode = MutableStateFlow(localManager.getOrCreatePairingCode())
    val localPairingCode: StateFlow<String> = _localPairingCode.asStateFlow()

    private val _deviceName = MutableStateFlow(localManager.getOrCreateDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _assignedVideoId = MutableStateFlow("ALL")
    val assignedVideoId: StateFlow<String> = _assignedVideoId.asStateFlow()

    // Display, Screen Orientation & Video Rotation States (Initialized from Saved Settings)
    private val _screenOrientation = MutableStateFlow(initialSavedSettings.screenOrientation)
    val screenOrientation: StateFlow<String> = _screenOrientation.asStateFlow()

    private val _videoRotateDegrees = MutableStateFlow(initialSavedSettings.videoRotateDegrees)
    val videoRotateDegrees: StateFlow<Int> = _videoRotateDegrees.asStateFlow()

    private val _liveState = MutableStateFlow(
        LiveState(
            userId = "vercel_web_cms",
            orientation = initialSavedSettings.combinedOrientation,
            scaleMode = initialSavedSettings.scaleMode,
            isMuted = initialSavedSettings.isMuted,
            kioskLocked = initialSavedSettings.kioskLocked
        )
    )
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private val _videos = MutableStateFlow<List<PlaylistVideo>>(initialSavedVideos)
    val videos: StateFlow<List<PlaylistVideo>> = _videos.asStateFlow()

    private val _allCloudVideos = MutableStateFlow<List<PlaylistVideo>>(initialSavedVideos)
    val allCloudVideos: StateFlow<List<PlaylistVideo>> = _allCloudVideos.asStateFlow()

    private val _forcedOfflineVideoId = MutableStateFlow<String?>(null)
    val forcedOfflineVideoId: StateFlow<String?> = _forcedOfflineVideoId.asStateFlow()

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

    private val _currentlyPlayingTitle = MutableStateFlow(
        initialSavedVideos.firstOrNull()?.title ?: "Standby (Empty Playlist)"
    )
    val currentlyPlayingTitle: StateFlow<String> = _currentlyPlayingTitle.asStateFlow()

    private val _isUsingBackupStream = MutableStateFlow(false)
    val isUsingBackupStream: StateFlow<Boolean> = _isUsingBackupStream.asStateFlow()

    private val _lastSyncCycleCount = MutableStateFlow(0L)
    val lastSyncCycleCount: StateFlow<Long> = _lastSyncCycleCount.asStateFlow()

    // Admin Hub & Tab State
    private val _isAdminPanelOpen = MutableStateFlow(false)
    val isAdminPanelOpen: StateFlow<Boolean> = _isAdminPanelOpen.asStateFlow()

    private val _selectedSidebarTab = MutableStateFlow(0)
    val selectedSidebarTab: StateFlow<Int> = _selectedSidebarTab.asStateFlow()

    private val _isPinDialogVisible = MutableStateFlow(false)
    val isPinDialogVisible: StateFlow<Boolean> = _isPinDialogVisible.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private var syncLoopJob: Job? = null
    private var otaProcessingInFlight = false
    private var autoCachingInFlight = false
    private val lastAutoCacheAttemptMs = mutableMapOf<String, Long>()
    private val failedAutoCacheCounts = mutableMapOf<String, Int>()
    private val knownRemoteVideoIds = mutableSetOf<String>().apply {
        initialSavedVideos.forEach { add(it.id) }
    }
    private var lastSeenPushCommand: String = ""
    private var localSettingsUpdatedAtMs: Long = initialSavedSettings.settingsUpdatedAtMs.coerceAtLeast(1L)
    private var hasLocalSettingsOverride: Boolean = initialSavedSettings.hasLocalOverrideSaved
    private val cornerTapTimestamps = mutableListOf<Long>()

    init {
        appendLog(
            LogLevel.INFO,
            "BOOT",
            "PulseCast Android TV Client ready. Orientation: ${_screenOrientation.value}, Video Rotate: ${_videoRotateDegrees.value}°, Scale: ${_liveState.value.scaleMode}"
        )
        appendLog(
            LogLevel.INFO,
            "WEB_CMS",
            "Auto-pairing & starting 2.0s real-time sync with ${_cmsServerUrl.value} (Code: ${_localPairingCode.value})"
        )
        autoPairOnStartup()
        startRealTimeSyncLoop()
    }

    private fun autoPairOnStartup() {
        viewModelScope.launch {
            vercelClient.pairDeviceWithWebCms(
                baseUrl = _cmsServerUrl.value,
                deviceId = _localDeviceId.value,
                pairingCode = _localPairingCode.value,
                deviceName = _deviceName.value,
                orientation = _screenOrientation.value,
                videoRotateDegrees = _videoRotateDegrees.value,
                scaleMode = _liveState.value.scaleMode,
                isMuted = _liveState.value.isMuted,
                settingsUpdatedAtMs = localSettingsUpdatedAtMs
            ).onSuccess {
                _isPairedWithCms.value = true
                _isWebCmsConnected.value = true
            }
        }
    }

    private fun startRealTimeSyncLoop() {
        syncLoopJob?.cancel()
        syncLoopJob = viewModelScope.launch {
            while (isActive) {
                runSingleSyncCycle()
                val delayMs = if (_isWebCmsConnected.value) 2000L else 2500L
                delay(delayMs)
            }
        }
    }

    fun forceImmediateSync() {
        viewModelScope.launch {
            appendLog(LogLevel.INFO, "WEB_CMS", "Syncing now with ${_cmsServerUrl.value}...")
            runSingleSyncCycle()
            showToast("Synced with Web Dashboard (2s Live)")
        }
    }

    fun pairNowWithWebDashboard(customPairingCode: String, customDeviceName: String, customServerUrl: String) {
        viewModelScope.launch {
            val cleanUrl = localManager.setCmsServerUrl(customServerUrl)
            _cmsServerUrl.value = cleanUrl

            val cleanCode = if (customPairingCode.filter { it.isDigit() }.length == 6) {
                localManager.setCustomPairingCode(customPairingCode)
            } else {
                _localPairingCode.value
            }
            _localPairingCode.value = cleanCode

            val cleanName = localManager.setDeviceName(customDeviceName)
            _deviceName.value = cleanName

            appendLog(LogLevel.INFO, "PAIRING", "Pairing with Web CMS ($cleanUrl) using 6-digit code $cleanCode...")
            val pairRes = vercelClient.pairDeviceWithWebCms(
                baseUrl = cleanUrl,
                deviceId = _localDeviceId.value,
                pairingCode = cleanCode,
                deviceName = cleanName,
                orientation = _screenOrientation.value,
                videoRotateDegrees = _videoRotateDegrees.value,
                scaleMode = _liveState.value.scaleMode,
                isMuted = _liveState.value.isMuted,
                settingsUpdatedAtMs = localSettingsUpdatedAtMs
            )
            pairRes.onSuccess {
                _isPairedWithCms.value = true
                _isWebCmsConnected.value = true
                appendLog(LogLevel.INFO, "PAIRING", "Real-Time Paired with Web CMS ($cleanCode)!")
                showToast("✔ Permanently Paired with Web Dashboard ($cleanCode)!")
            }.onFailure {
                appendLog(LogLevel.WARN, "PAIRING", "Pair request sent; auto-syncing every 2s...")
                showToast("Pairing Code $cleanCode active — auto-syncing every 2s")
            }
            runSingleSyncCycle()
        }
    }

    fun regenerateSixDigitPairingCode() {
        val newCode = localManager.regeneratePairingCode()
        _localPairingCode.value = newCode
        appendLog(LogLevel.INFO, "PAIRING", "Generated new 6-digit pairing code: $newCode")
        showToast("New 6-Digit Pairing Code: $newCode")
        pairNowWithWebDashboard(newCode, _deviceName.value, _cmsServerUrl.value)
    }

    private suspend fun runSingleSyncCycle() {
        _lastSyncCycleCount.value += 1L
        val currentVideos = _videos.value
        val verifiedCount = currentVideos.count {
            localManager.isRealMp4PlayableOffline(it.localFileName)
        }.toLong()

        val syncResult = vercelClient.syncHeartbeatWithWebCms(
            baseUrl = _cmsServerUrl.value,
            deviceId = _localDeviceId.value,
            pairingCode = _localPairingCode.value,
            deviceName = _deviceName.value,
            currentOrientation = _screenOrientation.value,
            currentVideoRotateDegrees = _videoRotateDegrees.value,
            currentScaleMode = _liveState.value.scaleMode,
            currentIsMuted = _liveState.value.isMuted,
            settingsUpdatedAtMs = localSettingsUpdatedAtMs,
            currentVideoTitle = _currentlyPlayingTitle.value,
            cachedVideoCount = verifiedCount,
            otaSlotState = _otaSlotState.value,
            knownClientVideos = (_allCloudVideos.value + _videos.value).distinctBy { it.id },
            deletedVideoIds = localManager.getDeletedVideoIds()
        )

        syncResult.onSuccess { payload ->
            val wasConnected = _isWebCmsConnected.value
            _isWebCmsConnected.value = true
            _isPairedWithCms.value = true

            if (!wasConnected) {
                appendLog(
                    LogLevel.INFO,
                    "WEB_CMS",
                    "Connected to Web CMS (${_cmsServerUrl.value}) — 2.0s Real-Time Sync Active"
                )
            }

            if (payload.deviceName.isNotBlank() && payload.deviceName != _deviceName.value) {
                _deviceName.value = localManager.setDeviceName(payload.deviceName)
            }

            // Strictly apply remote orientation/rotation/scale ONLY when the Web CMS has a newer timestamp
            // than our local settings timestamp. Cold serverless instances with timestamp 0 NEVER flip the screen!
            val remoteState = payload.liveState
            val isRemoteSettingsNewer = payload.remoteSettingsUpdatedAtMs > localSettingsUpdatedAtMs

            if (isRemoteSettingsNewer) {
                val prevOrient = _screenOrientation.value
                val prevRotateDeg = _videoRotateDegrees.value
                val prevScale = _liveState.value.scaleMode

                localSettingsUpdatedAtMs = payload.remoteSettingsUpdatedAtMs
                applyRemoteOrientationToState(remoteState.orientation, payload.videoRotateDegrees)
                _liveState.value = remoteState.copy(
                    orientation = computeCombinedOrientation(_screenOrientation.value, _videoRotateDegrees.value)
                )
                persistCurrentSettingsLocally()

                if (prevOrient != _screenOrientation.value) {
                    showToast("Web CMS: Screen -> ${_screenOrientation.value}")
                } else if (prevRotateDeg != _videoRotateDegrees.value) {
                    showToast("Web CMS: Video Rotated -> ${_videoRotateDegrees.value}°")
                } else if (prevScale != remoteState.scaleMode) {
                    showToast("Web CMS: Scale -> ${remoteState.scaleMode}")
                }
            } else {
                // Keep current orientation, video rotation & scale untouched while updating push/OTA metadata
                _liveState.value = _liveState.value.copy(
                    lastPushCommand = remoteState.lastPushCommand,
                    activeOtaVersionCode = remoteState.activeOtaVersionCode,
                    activeOtaVersionName = remoteState.activeOtaVersionName,
                    adminPin = remoteState.adminPin.ifBlank { _liveState.value.adminPin }
                )
            }

            _assignedVideoId.value = payload.assignedVideoId

            val isNewPushCommand = remoteState.lastPushCommand.isNotBlank() &&
                remoteState.lastPushCommand != lastSeenPushCommand &&
                lastSeenPushCommand.isNotEmpty()

            if (isNewPushCommand &&
                (remoteState.lastPushCommand.startsWith("PUSH_PLAY") ||
                    remoteState.lastPushCommand.startsWith("UPLOAD_PLAY") ||
                    remoteState.lastPushCommand.startsWith("ADD_VIDEO"))
            ) {
                _isUsingBackupStream.value = false
                _forcedOfflineVideoId.value = null
                lastAutoCacheAttemptMs.clear()
                failedAutoCacheCounts.clear()
                showToast("▶ Web CMS Video Push received — Playing now!")
                appendLog(LogLevel.INFO, "CMS_PUSH", "Executed remote Push & Play (${remoteState.lastPushCommand})")
            }
            if (remoteState.lastPushCommand.isNotBlank()) {
                lastSeenPushCommand = remoteState.lastPushCommand
            }

            // Record any explicit deletions from Web CMS and clean up deleted local files
            if (payload.deletedVideoIds.isNotEmpty()) {
                localManager.addDeletedVideoIds(payload.deletedVideoIds)
                val currentlyKnown = (_allCloudVideos.value + _videos.value).distinctBy { it.id }
                currentlyKnown.filter { payload.deletedVideoIds.contains(it.id) }.forEach { deletedVid ->
                    localManager.deleteCachedVideoFile(deletedVid.localFileName)
                }
            }
            val activeDeletedIds = localManager.getDeletedVideoIds()

            // Merge remote videos FIRST so newly uploaded Web videos are at index 0, followed by saved local vault videos
            val savedVaultVideos = localManager.getSavedPlaylistVideos()
            val remoteAllNormalized = payload.allCloudVideos
                .filter { !activeDeletedIds.contains(it.id) && !localManager.isDemoOrForbiddenUrl(it.sourceUrl) }
                .map { remoteVid ->
                    val isCached = localManager.isRealMp4PlayableOffline(remoteVid.localFileName)
                    val localBytes = localManager.getLocalFileSizeBytes(remoteVid.localFileName)
                    remoteVid.copy(
                        sourceUrl = localManager.sanitizeStreamUrl(remoteVid.sourceUrl),
                        backupUrl = "",
                        sizeBytes = if (localBytes > 0L) localBytes else remoteVid.sizeBytes,
                        isVerifiedCached = isCached
                    )
                }

            var brandNewRemoteVideo: PlaylistVideo? = null
            for (rv in remoteAllNormalized) {
                if (knownRemoteVideoIds.add(rv.id) && brandNewRemoteVideo == null) {
                    brandNewRemoteVideo = rv
                }
            }

            val mergedAllMap = linkedMapOf<String, PlaylistVideo>()
            for (rv in remoteAllNormalized) {
                if (!activeDeletedIds.contains(rv.id)) {
                    mergedAllMap[rv.id] = rv
                }
            }
            for (sv in savedVaultVideos) {
                if (!activeDeletedIds.contains(sv.id) && !mergedAllMap.containsKey(sv.id)) {
                    val isCached = localManager.isRealMp4PlayableOffline(sv.localFileName)
                    val localBytes = localManager.getLocalFileSizeBytes(sv.localFileName)
                    mergedAllMap[sv.id] = sv.copy(
                        backupUrl = "",
                        sizeBytes = if (localBytes > 0L) localBytes else sv.sizeBytes,
                        isVerifiedCached = isCached
                    )
                }
            }
            val mergedAllList = mergedAllMap.values.toList()
            localManager.savePlaylistVideos(mergedAllList)

            val assignedId = payload.assignedVideoId
            val mergedActiveList = if (assignedId.isNotBlank() && assignedId != "ALL") {
                val filtered = mergedAllList.filter { it.id == assignedId }
                if (filtered.isNotEmpty()) filtered else mergedAllList
            } else {
                mergedAllList
            }

            if (brandNewRemoteVideo != null) {
                _forcedOfflineVideoId.value = null
                lastAutoCacheAttemptMs.remove(brandNewRemoteVideo.id)
                appendLog(
                    LogLevel.INFO,
                    "WEB_UPLOAD",
                    "New video uploaded on Web CMS: '${brandNewRemoteVideo.title}' — syncing & playing now!"
                )
            }

            if (!areVideoListsEquivalent(_videos.value, mergedActiveList)) {
                _videos.value = mergedActiveList
            }
            if (!areVideoListsEquivalent(_allCloudVideos.value, mergedAllList)) {
                _allCloudVideos.value = mergedAllList
            }

            // Automatically pre-cache any unverified playlist video in the background in 100% original quality
            triggerAutoCacheForUncachedVideos(mergedActiveList)

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
                remoteState.lastPushCommand.startsWith("OTA_ROLLBACK") &&
                currentOta.installedVersionCode > 1L
            ) {
                triggerManualOtaRollback("Remote rollback command from Web CMS Dashboard")
            }
        }.onFailure { err ->
            if (_isWebCmsConnected.value) {
                appendLog(
                    LogLevel.WARN,
                    "WEB_CMS",
                    "Web CMS offline (${err.message}). Playing seamlessly from permanent local storage."
                )
            }
            _isWebCmsConnected.value = false
        }
    }

    private fun triggerAutoCacheForUncachedVideos(activeList: List<PlaylistVideo>) {
        if (autoCachingInFlight || _chunkTelemetry.value.isActive) return
        val now = System.currentTimeMillis()
        val nextUncached = activeList.firstOrNull { vid ->
            if (localManager.isRealMp4PlayableOffline(vid.localFileName)) {
                false
            } else {
                val failCount = failedAutoCacheCounts[vid.id] ?: 0
                val cooldownMs = when {
                    failCount == 0 -> 3_000L
                    failCount == 1 -> 8_000L
                    else -> 25_000L
                }
                now - (lastAutoCacheAttemptMs[vid.id] ?: 0L) > cooldownMs
            }
        } ?: return

        autoCachingInFlight = true
        lastAutoCacheAttemptMs[nextUncached.id] = now
        viewModelScope.launch {
            try {
                downloadCloudVideoForOfflineViewInternal(nextUncached, isSilentAutoSync = true)
            } finally {
                autoCachingInFlight = false
            }
        }
    }

    private fun areVideoListsEquivalent(a: List<PlaylistVideo>, b: List<PlaylistVideo>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val va = a[i]
            val vb = b[i]
            if (va.id != vb.id ||
                va.title != vb.title ||
                va.sourceUrl != vb.sourceUrl ||
                va.localFileName != vb.localFileName ||
                va.sizeBytes != vb.sizeBytes ||
                va.isVerifiedCached != vb.isVerifiedCached
            ) {
                return false
            }
        }
        return true
    }

    private fun applyRemoteOrientationToState(remoteOrientation: String, explicitRotateDegrees: Int? = null) {
        when (remoteOrientation.uppercase(Locale.US)) {
            "PORTRAIT" -> {
                _screenOrientation.value = "PORTRAIT"
                _videoRotateDegrees.value = explicitRotateDegrees?.let { ((it % 360) + 360) % 360 } ?: _videoRotateDegrees.value
            }
            "ROTATE_90" -> {
                _screenOrientation.value = "LANDSCAPE"
                _videoRotateDegrees.value = explicitRotateDegrees?.let { ((it % 360) + 360) % 360 } ?: 90
            }
            "ROTATE_180" -> {
                _screenOrientation.value = "LANDSCAPE"
                _videoRotateDegrees.value = explicitRotateDegrees?.let { ((it % 360) + 360) % 360 } ?: 180
            }
            "ROTATE_270" -> {
                _screenOrientation.value = "LANDSCAPE"
                _videoRotateDegrees.value = explicitRotateDegrees?.let { ((it % 360) + 360) % 360 } ?: 270
            }
            else -> {
                _screenOrientation.value = "LANDSCAPE"
                _videoRotateDegrees.value = explicitRotateDegrees?.let { ((it % 360) + 360) % 360 } ?: _videoRotateDegrees.value
            }
        }
    }

    // ------------------------------------------------------------------------
    // Top-Right Green Star Admin Trigger, Built-In PIN Keypad & Bottom-Nav Hub
    // ------------------------------------------------------------------------

    fun onGreenStarAdminClick(tabIndex: Int = 0) {
        _selectedSidebarTab.value = tabIndex
        if (_isAdminPanelOpen.value) {
            return
        }
        openAdminPinPrompt()
    }

    fun openAdminSidebarDirectly(tabIndex: Int = 0) {
        _selectedSidebarTab.value = tabIndex
        if (_liveState.value.kioskLocked && !_isAdminPanelOpen.value) {
            openAdminPinPrompt()
        } else {
            _isAdminPanelOpen.value = true
        }
    }

    fun selectSidebarTab(tabIndex: Int) {
        _selectedSidebarTab.value = tabIndex
    }

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
            appendLog(LogLevel.INFO, "SECURITY", "Admin password verified via built-in keypad. Opened Admin Hub.")
        } else {
            _pinError.value = "Incorrect PIN. Default PIN is $expectedPin."
        }
    }

    fun closeAdminPanel() {
        _isAdminPanelOpen.value = false
    }

    // ------------------------------------------------------------------------
    // Orientation, Video Rotate (+90° / -90° / 0° / 90° / 180° / 270°), Scale Mode & "Save Settings"
    // Every change immediately locks local timestamp & syncs so the screen NEVER flips back!
    // ------------------------------------------------------------------------

    private fun persistCurrentSettingsLocally() {
        val combinedOrient = computeCombinedOrientation(_screenOrientation.value, _videoRotateDegrees.value)
        val settingsToSave = SavedDisplaySettings(
            screenOrientation = _screenOrientation.value,
            videoRotateDegrees = _videoRotateDegrees.value,
            combinedOrientation = combinedOrient,
            scaleMode = _liveState.value.scaleMode,
            isMuted = _liveState.value.isMuted,
            kioskLocked = _liveState.value.kioskLocked,
            syncIntervalSeconds = 2,
            hasLocalOverrideSaved = true,
            settingsUpdatedAtMs = localSettingsUpdatedAtMs
        )
        localManager.saveDisplaySettings(settingsToSave)
    }

    private fun lockAndPushDisplaySettings() {
        hasLocalSettingsOverride = true
        localSettingsUpdatedAtMs = System.currentTimeMillis()
        persistCurrentSettingsLocally()
        val orient = _screenOrientation.value
        val rot = _videoRotateDegrees.value
        val scale = _liveState.value.scaleMode
        val muted = _liveState.value.isMuted
        val kiosk = _liveState.value.kioskLocked
        val ts = localSettingsUpdatedAtMs

        viewModelScope.launch {
            vercelClient.pushDeviceControlUpdate(
                baseUrl = _cmsServerUrl.value,
                deviceId = _localDeviceId.value,
                pairingCode = _localPairingCode.value,
                fields = mapOf(
                    "orientation" to orient,
                    "videoRotateDegrees" to rot,
                    "scaleMode" to scale,
                    "isMuted" to muted,
                    "kioskLocked" to kiosk,
                    "settingsUpdatedAtMs" to ts
                )
            )
        }
    }

    fun setScreenOrientationMode(screenOrient: String) {
        _screenOrientation.value = screenOrient
        val combined = computeCombinedOrientation(screenOrient, _videoRotateDegrees.value)
        _liveState.value = _liveState.value.copy(orientation = combined)
        lockAndPushDisplaySettings()
    }

    fun setVideoRotationAngle(degrees: Int) {
        val normalized = ((degrees % 360) + 360) % 360
        _videoRotateDegrees.value = normalized
        val combined = computeCombinedOrientation(_screenOrientation.value, normalized)
        _liveState.value = _liveState.value.copy(orientation = combined)
        lockAndPushDisplaySettings()
    }

    fun rotateVideoByDelta(deltaDegrees: Int) {
        val next = ((_videoRotateDegrees.value + deltaDegrees) % 360 + 360) % 360
        setVideoRotationAngle(next)
        showToast("Video Rotated: ${next}°")
    }

    fun setOrientationPreset(preset: String) {
        applyRemoteOrientationToState(preset)
        _liveState.value = _liveState.value.copy(orientation = preset)
        lockAndPushDisplaySettings()
    }

    fun setScaleMode(scaleMode: String) {
        _liveState.value = _liveState.value.copy(scaleMode = scaleMode)
        lockAndPushDisplaySettings()
    }

    fun toggleAudioMute(isMuted: Boolean) {
        _liveState.value = _liveState.value.copy(isMuted = isMuted)
        lockAndPushDisplaySettings()
    }

    fun toggleKioskLock(locked: Boolean) {
        _liveState.value = _liveState.value.copy(kioskLocked = locked)
        lockAndPushDisplaySettings()
    }

    private fun computeCombinedOrientation(screenOrient: String, rotateDeg: Int): String {
        return when {
            screenOrient == "PORTRAIT" -> "PORTRAIT"
            rotateDeg == 90 -> "ROTATE_90"
            rotateDeg == 180 -> "ROTATE_180"
            rotateDeg == 270 -> "ROTATE_270"
            else -> "LANDSCAPE"
        }
    }

    fun saveAllDisplayAndRotationSettings() {
        val combinedOrient = computeCombinedOrientation(_screenOrientation.value, _videoRotateDegrees.value)
        _liveState.value = _liveState.value.copy(orientation = combinedOrient)
        lockAndPushDisplaySettings()
        appendLog(
            LogLevel.INFO,
            "SETTINGS",
            "Saved Settings: Screen=${_screenOrientation.value}, VideoRotate=${_videoRotateDegrees.value}°, Scale=${_liveState.value.scaleMode}, Mute=${_liveState.value.isMuted}"
        )
        showToast("✔ Settings Saved & Synced (${_screenOrientation.value} • ${_videoRotateDegrees.value}° • ${_liveState.value.scaleMode})")
    }

    // ------------------------------------------------------------------------
    // Cloud Video "Download Offline View" & 100% Original Quality Playback
    // ------------------------------------------------------------------------

    fun downloadCloudVideoForOfflineView(video: PlaylistVideo) {
        if (_chunkTelemetry.value.isActive) return
        viewModelScope.launch {
            downloadCloudVideoForOfflineViewInternal(video, isSilentAutoSync = false)
        }
    }

    private suspend fun downloadCloudVideoForOfflineViewInternal(
        video: PlaylistVideo,
        isSilentAutoSync: Boolean
    ) {
        val targetName = video.localFileName.ifBlank { "${video.id}.mp4" }
        if (!isSilentAutoSync) {
            appendLog(
                LogLevel.INFO,
                "OFFLINE_DL",
                "Caching '${video.title}' in 100% original quality to local offline storage..."
            )
            _chunkTelemetry.value = ChunkTransferTelemetry(
                isActive = true,
                videoId = video.id,
                label = "Saving '${video.title}' (Original Quality)",
                currentChunk = 0,
                totalChunks = 1,
                bytesTransferred = 0L,
                progressPercent = 5
            )
        }

        val result = localManager.syncRemoteVideoToLocalCache(
            sourceUrl = video.sourceUrl,
            targetFileName = targetName
        ) { chunk, total, bytes ->
            if (bytes > 0L || !isSilentAutoSync) {
                val pct = ((chunk.toFloat() / total.coerceAtLeast(1).toFloat()) * 100f).toInt().coerceIn(5, 99)
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    videoId = video.id,
                    label = "Downloading '${video.title}'",
                    currentChunk = chunk,
                    totalChunks = total,
                    bytesTransferred = bytes,
                    progressPercent = pct
                )
            }
        }

        _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

        result.onSuccess { saved ->
            failedAutoCacheCounts.remove(video.id)
            val updatedVideo = video.copy(
                localFileName = saved.localFileName,
                sizeBytes = saved.sizeBytes,
                chunkCount = saved.chunkCount,
                isVerifiedCached = true
            )
            _videos.value = if (_videos.value.any { it.id == video.id }) {
                _videos.value.map { if (it.id == video.id) updatedVideo else it }
            } else {
                listOf(updatedVideo) + _videos.value
            }
            _allCloudVideos.value = if (_allCloudVideos.value.any { it.id == video.id }) {
                _allCloudVideos.value.map { if (it.id == video.id) updatedVideo else it }
            } else {
                listOf(updatedVideo) + _allCloudVideos.value
            }
            localManager.savePlaylistVideos((_allCloudVideos.value + _videos.value).distinctBy { it.id })
            appendLog(
                LogLevel.INFO,
                "OFFLINE_DL",
                "✔ Saved '${video.title}' offline (${saved.sizeBytes / 1024} KB in ${saved.chunkCount} chunks)"
            )
            if (!isSilentAutoSync) {
                showToast("✔ Saved '${video.title}' for Offline View!")
            }
        }.onFailure { err ->
            failedAutoCacheCounts[video.id] = (failedAutoCacheCounts[video.id] ?: 0) + 1
            if (!isSilentAutoSync) {
                appendLog(LogLevel.WARN, "OFFLINE_DL", "Awaiting cloud file '${video.title}' (${err.message})")
            }
        }
    }

    fun downloadAllCloudVideosOffline() {
        if (_chunkTelemetry.value.isActive) return
        viewModelScope.launch {
            val allList = (_allCloudVideos.value + _videos.value).distinctBy { it.id }
            if (allList.isEmpty()) {
                showToast("No videos in playlist yet. Upload a video in Web Dashboard or tap Add Local Video.")
                return@launch
            }
            for (video in allList) {
                if (!localManager.isRealMp4PlayableOffline(video.localFileName)) {
                    downloadCloudVideoForOfflineViewInternal(video, isSilentAutoSync = true)
                }
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)
            showToast("✔ All Videos Saved for Offline View!")
        }
    }

    /**
     * Switches playback to play a specific video (in preview or full screen).
     */
    fun playVideoOfflineNow(video: PlaylistVideo, closeAdminForFullScreen: Boolean = false) {
        if (!localManager.isFileVerifiedCached(video.localFileName)) {
            downloadCloudVideoForOfflineView(video)
        }
        _forcedOfflineVideoId.value = video.id
        if (_videos.value.none { it.id == video.id }) {
            _videos.value = listOf(video) + _videos.value
        }
        if (closeAdminForFullScreen) {
            _isAdminPanelOpen.value = false
        }
        showToast("▶ Playing '${video.title}'")
        appendLog(LogLevel.INFO, "OFFLINE_PLAY", "Playing '${video.title}' in original quality")
    }

    fun clearForcedSingleOfflineVideo() {
        _forcedOfflineVideoId.value = null
        showToast("Looping full active playlist")
    }

    fun ingestLocalVideoForOfflineCache(uri: Uri, fallbackTitle: String) {
        viewModelScope.launch {
            val (queriedName, _) = localManager.queryUriDisplayNameAndSize(uri)
            val cleanTitle = queriedName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: fallbackTitle.trim().ifEmpty { "Local Video ${(10..99).random()}" }
            val ext = queriedName?.substringAfterLast('.', "mp4")?.lowercase(Locale.US)?.takeIf { it.length in 2..4 } ?: "mp4"
            val fileName = "local_${System.currentTimeMillis()}.$ext"
            appendLog(LogLevel.INFO, "CACHE", "Saving '$cleanTitle' in 100% original quality to offline storage...")

            val result = localManager.ingestLocalVideoUriInChunks(
                uri = uri,
                targetFileName = fileName
            ) { chunk, total, bytes ->
                val pct = ((chunk.toFloat() / total.coerceAtLeast(1).toFloat()) * 100f).toInt().coerceIn(5, 99)
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    videoId = fileName,
                    label = "Saving '$cleanTitle' (Original Quality)",
                    currentChunk = chunk,
                    totalChunks = total,
                    bytesTransferred = bytes,
                    progressPercent = pct
                )
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

            result.onSuccess { uploaded ->
                val vidId = "local_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                localManager.removeDeletedVideoId(vidId)
                val newVideo = PlaylistVideo(
                    id = vidId,
                    userId = "local_admin",
                    title = cleanTitle,
                    sourceUrl = Uri.fromFile(uploaded.localFile).toString(),
                    backupUrl = "",
                    localFileName = uploaded.localFileName,
                    zoneId = "ALL",
                    sizeBytes = uploaded.sizeBytes,
                    chunkCount = uploaded.chunkCount,
                    isVerifiedCached = true,
                    sortOrder = 0L
                )
                val updatedVideos = listOf(newVideo) + _videos.value
                val updatedAll = (listOf(newVideo) + _allCloudVideos.value).distinctBy { it.id }
                _videos.value = updatedVideos
                _allCloudVideos.value = updatedAll
                _forcedOfflineVideoId.value = newVideo.id
                localManager.savePlaylistVideos(updatedAll)
                appendLog(
                    LogLevel.INFO,
                    "CACHE",
                    "✔ Saved '$cleanTitle' offline (${uploaded.sizeBytes / 1024} KB • 100% Original Quality)"
                )
                showToast("✔ Saved & Playing '$cleanTitle' (${uploaded.sizeBytes / 1024} KB)!")
            }.onFailure { err ->
                showToast("Could not read selected video: ${err.localizedMessage}")
            }
        }
    }

    fun deleteVideoFromPlaylist(video: PlaylistVideo) {
        localManager.addDeletedVideoIds(listOf(video.id))
        localManager.deleteCachedVideoFile(video.localFileName)
        val remainingVideos = _videos.value.filterNot { it.id == video.id }
        val remainingAll = _allCloudVideos.value.filterNot { it.id == video.id }
        _videos.value = remainingVideos
        _allCloudVideos.value = remainingAll
        localManager.savePlaylistVideos(remainingAll)
        if (_forcedOfflineVideoId.value == video.id) {
            _forcedOfflineVideoId.value = null
        }
        viewModelScope.launch {
            vercelClient.deleteVideoOnWebCms(_cmsServerUrl.value, video.id)
            appendLog(LogLevel.INFO, "PLAYLIST", "Deleted '${video.title}' permanently from playlist & storage")
            showToast("Deleted '${video.title}'")
        }
    }

    // ------------------------------------------------------------------------
    // ExoPlayer Playback Status & Watchdog
    // ------------------------------------------------------------------------

    fun onActiveVideoChanged(title: String) {
        _currentlyPlayingTitle.value = title
    }

    fun onBufferingWatchdogTriggered(stalledVideoTitle: String, reason: String) {
        _isUsingBackupStream.value = true
        appendLog(
            LogLevel.RECOVERY,
            "WATCHDOG",
            "Playback recovery on '$stalledVideoTitle' ($reason)"
        )
    }

    fun showToast(message: String) {
        _syncToastMessage.value = message
        viewModelScope.launch {
            delay(3000L)
            if (_syncToastMessage.value == message) {
                _syncToastMessage.value = null
            }
        }
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
                    "Detected new OTA Release v${release.versionName} (Build ${release.versionCode}). Downloading..."
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
                    showToast("OTA Failed Verification -> Auto-Rollback to v${finalState.installedVersionName}!")
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
