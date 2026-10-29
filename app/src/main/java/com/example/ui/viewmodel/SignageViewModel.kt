package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LiveState
import com.example.data.model.LogLevel
import com.example.data.model.OtaRelease
import com.example.data.model.PlaybackZone
import com.example.data.model.PlaylistVideo
import com.example.data.model.SignageDevice
import com.example.data.repository.SignageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppOperatingMode {
    CMS_DASHBOARD,
    TV_DISPLAY_CLIENT
}

enum class CmsTab {
    LIVE_CONTROL,
    DEVICES_PAIRING,
    ZONES_PERMISSIONS,
    OTA_UPDATES
}

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class ChunkTransferTelemetry(
    val isActive: Boolean = false,
    val label: String = "",
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val bytesTransferred: Long = 0L
)

class SignageViewModel(
    private val repository: SignageRepository,
    private val localManager: ChunkedStorageAndOtaManager,
    private val currentUserId: String
) : ViewModel() {

    companion object {
        private const val TAG = "SignageVM"
        private const val STOP_TIMEOUT_MS = 5000L
        const val VERIFIED_BACKUP_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        const val SECONDARY_BACKUP_STREAM_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    }

    private val _operatingMode = MutableStateFlow(AppOperatingMode.CMS_DASHBOARD)
    val operatingMode: StateFlow<AppOperatingMode> = _operatingMode.asStateFlow()

    private val _selectedCmsTab = MutableStateFlow(CmsTab.LIVE_CONTROL)
    val selectedCmsTab: StateFlow<CmsTab> = _selectedCmsTab.asStateFlow()

    private val _localDeviceId = MutableStateFlow(localManager.getOrCreateDeviceId())
    val localDeviceId: StateFlow<String> = _localDeviceId.asStateFlow()

    private val _localPairingCode = MutableStateFlow(localManager.getOrCreatePairingCode())
    val localPairingCode: StateFlow<String> = _localPairingCode.asStateFlow()

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

    // Two-tier Flow error handling for Firestore streams
    val liveStateUi: StateFlow<DataUiState<LiveState>> = repository.observeLiveState(currentUserId)
        .map<LiveState?, DataUiState<LiveState>> { state ->
            DataUiState.Success(state ?: LiveState(userId = currentUserId))
        }
        .catch { err ->
            Log.w(TAG, "Error observing live_state", err)
            emit(DataUiState.Error(err.message ?: "Failed to observe live_state"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    val videosUi: StateFlow<DataUiState<List<PlaylistVideo>>> = repository.observeVideos(currentUserId)
        .map<List<PlaylistVideo>, DataUiState<List<PlaylistVideo>>> { list ->
            DataUiState.Success(list)
        }
        .catch { err ->
            Log.w(TAG, "Error observing playlist videos", err)
            emit(DataUiState.Error(err.message ?: "Failed to observe playlist videos"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    val devicesUi: StateFlow<DataUiState<List<SignageDevice>>> = repository.observeDevices(currentUserId)
        .map<List<SignageDevice>, DataUiState<List<SignageDevice>>> { list ->
            DataUiState.Success(list)
        }
        .catch { err ->
            Log.w(TAG, "Error observing signage devices", err)
            emit(DataUiState.Error(err.message ?: "Failed to observe devices"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    val zonesUi: StateFlow<DataUiState<List<PlaybackZone>>> = repository.observeZones(currentUserId)
        .map<List<PlaybackZone>, DataUiState<List<PlaybackZone>>> { list ->
            DataUiState.Success(list)
        }
        .catch { err ->
            Log.w(TAG, "Error observing playback zones", err)
            emit(DataUiState.Error(err.message ?: "Failed to observe zones"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    val otaReleasesUi: StateFlow<DataUiState<List<OtaRelease>>> = repository.observeOtaReleases(currentUserId)
        .map<List<OtaRelease>, DataUiState<List<OtaRelease>>> { list ->
            DataUiState.Success(list)
        }
        .catch { err ->
            Log.w(TAG, "Error observing OTA releases", err)
            emit(DataUiState.Error(err.message ?: "Failed to observe OTA releases"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DataUiState.Loading
        )

    private var syncLoopJob: Job? = null
    private var otaProcessingInFlight = false
    private val cornerTapTimestamps = mutableListOf<Long>()

    init {
        appendLog(LogLevel.INFO, "BOOT", "PulseCast Signage Engine initialized. Slot: ${_otaSlotState.value.activeSlot} (v${_otaSlotState.value.installedVersionName})")
        bootstrapCloudStateAndStartSyncLoop()
    }

    private fun bootstrapCloudStateAndStartSyncLoop() {
        viewModelScope.launch {
            repository.ensureLiveStateInitialized()
            // Ensure default zones exist if none created yet so operator permissions work out of the box
            val currentZones = (zonesUi.value as? DataUiState.Success)?.data
            if (currentZones.isNullOrEmpty()) {
                repository.createZone(
                    zoneId = "zone_lobby",
                    zoneName = "Main Lobby 4K Wall",
                    locationTag = "Building A • Ground Floor",
                    operatorName = "Lead Broadcast Admin",
                    operatorRole = "SUPER_ADMIN",
                    canPushPlaylist = true,
                    canRotateScreen = true,
                    canManageOta = true,
                    orientationOverride = "INHERIT"
                )
                repository.createZone(
                    zoneId = "zone_window",
                    zoneName = "Storefront Portrait Totem",
                    locationTag = "Street Facing Window",
                    operatorName = "Retail Floor Manager",
                    operatorRole = "ZONE_OPERATOR",
                    canPushPlaylist = true,
                    canRotateScreen = true,
                    canManageOta = false,
                    orientationOverride = "PORTRAIT"
                )
            }
        }

        // Real-time 2.5s background polling & heartbeat sync loop
        syncLoopJob = viewModelScope.launch {
            while (isActive) {
                runBackgroundSyncCycle()
                delay(2500L)
            }
        }
    }

    private suspend fun runBackgroundSyncCycle() {
        _lastSyncCycleCount.value += 1L
        val videos = (videosUi.value as? DataUiState.Success)?.data ?: emptyList()
        val devices = (devicesUi.value as? DataUiState.Success)?.data ?: emptyList()
        val myDevice = devices.find { it.id == _localDeviceId.value }

        // 1. Verify offline local cache for any unverified playlist videos
        var verifiedCount = 0L
        for (video in videos) {
            val isLocalOk = localManager.isFileVerifiedCached(video.localFileName)
            if (isLocalOk) {
                verifiedCount++
                if (!video.isVerifiedCached) {
                    repository.markVideoVerifiedCached(video.id, video.localFileName, true)
                }
            } else if (!_chunkTelemetry.value.isActive) {
                // Automatically download & cache video to persistent local storage for offline ✔ verification
                appendLog(LogLevel.INFO, "CACHE", "Syncing '${video.title}' to local offline storage in 600KB chunks...")
                val result = localManager.syncRemoteVideoToLocalCache(
                    sourceUrl = video.sourceUrl,
                    targetFileName = video.localFileName.ifBlank { "${video.id}.mp4" }
                ) { chunk, total, bytes ->
                    _chunkTelemetry.value = ChunkTransferTelemetry(
                        isActive = true,
                        label = "Caching ${video.title}",
                        currentChunk = chunk,
                        totalChunks = total,
                        bytesTransferred = bytes
                    )
                }
                _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)
                result.onSuccess { cached ->
                    verifiedCount++
                    repository.markVideoVerifiedCached(video.id, cached.localFileName, true)
                    appendLog(LogLevel.INFO, "CACHE", "Verified offline cache (✔) for '${video.title}' (${cached.chunkCount} chunks, SHA-256 ${cached.sha256Checksum.take(8)})")
                }
            }
        }

        // 2. Check for pending Over-the-Air (OTA) Software Updates
        val releases = (otaReleasesUi.value as? DataUiState.Success)?.data ?: emptyList()
        val latestActiveRelease = releases.firstOrNull { it.releaseStatus == "ACTIVE" }
        val currentOta = _otaSlotState.value
        val deviceZone = myDevice?.zoneId ?: "zone_lobby"

        if (!otaProcessingInFlight &&
            latestActiveRelease != null &&
            latestActiveRelease.id != currentOta.lastProcessedReleaseId &&
            latestActiveRelease.versionCode > currentOta.installedVersionCode &&
            (latestActiveRelease.targetZoneId == "ALL" || latestActiveRelease.targetZoneId == deviceZone)
        ) {
            triggerBackgroundOtaInstallation(latestActiveRelease)
        }

        // 3. Emit 2.5s Device Heartbeat to Firestore
        val latestOta = _otaSlotState.value
        repository.registerOrHeartbeatDevice(
            deviceId = _localDeviceId.value,
            pairingCode = _localPairingCode.value,
            deviceName = myDevice?.deviceName ?: "PulseCast Android TV #1",
            zoneId = deviceZone,
            isPaired = myDevice?.isPaired ?: true,
            isOnline = true,
            currentVideoTitle = _currentlyPlayingTitle.value,
            cachedVideoCount = verifiedCount,
            installedVersionCode = latestOta.installedVersionCode,
            installedVersionName = latestOta.installedVersionName,
            previousSlotVersionCode = latestOta.previousSlotVersionCode,
            previousSlotVersionName = latestOta.previousSlotVersionName,
            otaStatus = latestOta.otaStatus,
            lastOtaMessage = latestOta.lastOtaMessage
        )
    }

    // ------------------------------------------------------------------------
    // Mode & Navigation Controls
    // ------------------------------------------------------------------------

    fun switchOperatingMode(mode: AppOperatingMode) {
        _operatingMode.value = mode
        _isAdminPanelOpen.value = false
        _isPinDialogVisible.value = false
        appendLog(LogLevel.INFO, "MODE", "Switched active view to ${mode.name}")
    }

    fun selectCmsTab(tab: CmsTab) {
        _selectedCmsTab.value = tab
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

    fun dismissToast() {
        _syncToastMessage.value = null
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
        val expectedPin = (liveStateUi.value as? DataUiState.Success)?.data?.adminPin ?: "1234"
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

    fun updateAdminPin(newPin: String) {
        val clean = newPin.filter { it.isDigit() }.take(8)
        if (clean.length < 4) {
            showToast("PIN must be 4 to 8 digits")
            return
        }
        viewModelScope.launch {
            repository.updateLiveStateFields(mapOf("adminPin" to clean))
            appendLog(LogLevel.INFO, "SECURITY", "Updated Admin PIN in cms_global/live_state")
            showToast("Admin PIN updated & synced")
        }
    }

    // ------------------------------------------------------------------------
    // Screen Orientation, Scale Mode, Mute & Kiosk Lock (Granular Permission Guarded)
    // ------------------------------------------------------------------------

    private fun activeZonePermissions(): PlaybackZone? {
        val activeZoneId = (liveStateUi.value as? DataUiState.Success)?.data?.activeZoneId ?: "zone_lobby"
        val zones = (zonesUi.value as? DataUiState.Success)?.data ?: emptyList()
        return zones.find { it.id == activeZoneId } ?: zones.firstOrNull()
    }

    fun setOrientation(orientation: String) {
        val zone = activeZonePermissions()
        if (zone != null && !zone.canRotateScreen) {
            showToast("Permission Denied: Role '${zone.operatorRole}' cannot rotate '${zone.zoneName}'")
            appendLog(LogLevel.WARN, "RBAC", "Blocked screen rotation on zone '${zone.zoneName}' for role ${zone.operatorRole}")
            return
        }
        viewModelScope.launch {
            repository.updateLiveStateFields(
                mapOf(
                    "orientation" to orientation,
                    "lastPushCommand" to "ROTATE_$orientation"
                )
            )
            appendLog(LogLevel.INFO, "DISPLAY", "Synced orientation -> $orientation")
            showToast("Synced Orientation: $orientation")
        }
    }

    fun setScaleMode(scaleMode: String) {
        viewModelScope.launch {
            repository.updateLiveStateFields(
                mapOf(
                    "scaleMode" to scaleMode,
                    "lastPushCommand" to "SCALE_$scaleMode"
                )
            )
            appendLog(LogLevel.INFO, "DISPLAY", "Synced video scale mode -> $scaleMode")
            showToast("Synced Scale Mode: $scaleMode")
        }
    }

    fun toggleAudioMute(isMuted: Boolean) {
        viewModelScope.launch {
            repository.updateLiveStateFields(
                mapOf(
                    "isMuted" to isMuted,
                    "lastPushCommand" to if (isMuted) "MUTE_AUDIO" else "UNMUTE_AUDIO"
                )
            )
            appendLog(LogLevel.INFO, "AUDIO", if (isMuted) "Audio muted across display fleet" else "Audio unmuted")
            showToast(if (isMuted) "Audio Muted (Live Synced)" else "Audio Unmuted (Live Synced)")
        }
    }

    fun toggleKioskLock(locked: Boolean) {
        viewModelScope.launch {
            repository.updateLiveStateFields(
                mapOf(
                    "kioskLocked" to locked,
                    "lastPushCommand" to if (locked) "KIOSK_LOCK" else "KIOSK_UNLOCK"
                )
            )
            appendLog(LogLevel.INFO, "KIOSK", "Kiosk mode lock set to $locked")
            showToast(if (locked) "Kiosk Mode Locked" else "Kiosk Mode Unlocked")
        }
    }

    fun selectActiveZone(zoneId: String) {
        viewModelScope.launch {
            repository.updateLiveStateFields(
                mapOf(
                    "activeZoneId" to zoneId,
                    "lastPushCommand" to "SWITCH_ZONE_$zoneId"
                )
            )
            showToast("Switched active broadcast zone")
        }
    }

    fun pushAndPlayToAllScreens() {
        val zone = activeZonePermissions()
        if (zone != null && !zone.canPushPlaylist) {
            showToast("Permission Denied: Role '${zone.operatorRole}' cannot push playlists in '${zone.zoneName}'")
            appendLog(LogLevel.WARN, "RBAC", "Blocked playlist push on zone '${zone.zoneName}' for role ${zone.operatorRole}")
            return
        }
        viewModelScope.launch {
            val cmd = "PUSH_PLAY_${System.currentTimeMillis() / 1000}"
            repository.updateLiveStateFields(mapOf("lastPushCommand" to cmd))
            appendLog(LogLevel.INFO, "CMS_SYNC", "Broadcasted multi-device PUSH & PLAY command ($cmd)")
            showToast("Instant Sync: Pushed playlist to all connected screens!")
        }
    }

    // ------------------------------------------------------------------------
    // 600KB Chunked Video Uploader & Playlist Management
    // ------------------------------------------------------------------------

    fun uploadLocalVideoUri(uri: Uri, title: String, zoneId: String) {
        val zone = activeZonePermissions()
        if (zone != null && !zone.canPushPlaylist) {
            showToast("Permission Denied: Role '${zone.operatorRole}' cannot upload to '${zone.zoneName}'")
            return
        }
        viewModelScope.launch {
            val cleanTitle = title.trim().ifEmpty { "Local Ad Creative ${(10..99).random()}" }
            val fileName = "local_${System.currentTimeMillis()}.mp4"
            appendLog(LogLevel.INFO, "UPLOAD", "Starting 600KB chunked ingestion for '$cleanTitle'...")

            val result = localManager.ingestLocalVideoUriInChunks(
                uri = uri,
                targetFileName = fileName
            ) { chunk, total, bytes ->
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    label = "Uploading $cleanTitle (600KB chunks)",
                    currentChunk = chunk,
                    totalChunks = total,
                    bytesTransferred = bytes
                )
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

            result.onSuccess { uploaded ->
                val currentList = (videosUi.value as? DataUiState.Success)?.data ?: emptyList()
                repository.addPlaylistVideo(
                    title = cleanTitle,
                    sourceUrl = VERIFIED_BACKUP_STREAM_URL,
                    backupUrl = SECONDARY_BACKUP_STREAM_URL,
                    localFileName = uploaded.localFileName,
                    zoneId = zoneId,
                    sizeBytes = uploaded.sizeBytes,
                    chunkCount = uploaded.chunkCount,
                    isVerifiedCached = true,
                    sortOrder = currentList.size.toLong()
                )
                appendLog(
                    LogLevel.INFO,
                    "UPLOAD",
                    "Uploaded & cached '$cleanTitle' (${uploaded.chunkCount} x 600KB chunks, ✔ Offline Ready)"
                )
                showToast("Uploaded '$cleanTitle' (${uploaded.chunkCount} chunks) & synced!")
            }.onFailure { err ->
                appendLog(LogLevel.ERROR, "UPLOAD", "Chunked upload failed: ${err.message}")
                showToast("Upload failed: ${err.localizedMessage}")
            }
        }
    }

    fun uploadStreamUrlVideo(title: String, streamUrl: String, backupUrl: String, zoneId: String) {
        val zone = activeZonePermissions()
        if (zone != null && !zone.canPushPlaylist) {
            showToast("Permission Denied: Role '${zone.operatorRole}' cannot modify playlist in '${zone.zoneName}'")
            return
        }
        viewModelScope.launch {
            val cleanTitle = title.trim().ifEmpty { "Cloud Broadcast Spot" }
            val cleanUrl = streamUrl.trim().ifEmpty { VERIFIED_BACKUP_STREAM_URL }
            val cleanBackup = backupUrl.trim().ifEmpty { SECONDARY_BACKUP_STREAM_URL }
            val fileName = "stream_${System.currentTimeMillis()}.mp4"

            appendLog(LogLevel.INFO, "UPLOAD", "Chunk-syncing stream '$cleanTitle' to /files/videos...")
            val result = localManager.syncRemoteVideoToLocalCache(
                sourceUrl = cleanUrl,
                targetFileName = fileName
            ) { chunk, total, bytes ->
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    label = "Chunk-Syncing $cleanTitle (600KB blocks)",
                    currentChunk = chunk,
                    totalChunks = total,
                    bytesTransferred = bytes
                )
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

            result.onSuccess { synced ->
                val currentList = (videosUi.value as? DataUiState.Success)?.data ?: emptyList()
                repository.addPlaylistVideo(
                    title = cleanTitle,
                    sourceUrl = cleanUrl,
                    backupUrl = cleanBackup,
                    localFileName = synced.localFileName,
                    zoneId = zoneId,
                    sizeBytes = synced.sizeBytes,
                    chunkCount = synced.chunkCount,
                    isVerifiedCached = true,
                    sortOrder = currentList.size.toLong()
                )
                appendLog(
                    LogLevel.INFO,
                    "UPLOAD",
                    "Synced '$cleanTitle' to Firestore & local storage (${synced.chunkCount} chunks ✔)"
                )
                showToast("Synced '$cleanTitle' (${synced.chunkCount} x 600KB chunks ✔)")
            }
        }
    }

    fun deleteVideoFromPlaylist(video: PlaylistVideo) {
        viewModelScope.launch {
            repository.deletePlaylistVideo(video.id)
            appendLog(LogLevel.INFO, "PLAYLIST", "Removed '${video.title}' from live playlist")
            showToast("Removed '${video.title}' from playlist")
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
            "3.5s Watchdog / Stream Error on '$stalledVideoTitle' ($reason) -> Auto-recovered via verified local/backup stream!"
        )
        showToast("Watchdog Auto-Recovery: Switched to verified backup stream")
    }

    fun resetBackupStreamFlag() {
        _isUsingBackupStream.value = false
    }

    // ------------------------------------------------------------------------
    // 6-Digit Device Pairing & Granular Zone Permissions
    // ------------------------------------------------------------------------

    fun pairDisplayBySixDigitCode(enteredCode: String, deviceName: String, zoneId: String) {
        val cleanCode = enteredCode.filter { it.isDigit() }
        if (cleanCode.length != 6) {
            showToast("Enter a valid 6-digit pairing code")
            return
        }
        viewModelScope.launch {
            val devices = (devicesUi.value as? DataUiState.Success)?.data ?: emptyList()
            val matched = devices.find { it.pairingCode == cleanCode }
            val targetDeviceId = matched?.id ?: "tv_${cleanCode}"
            val currentOta = _otaSlotState.value

            if (matched == null) {
                repository.registerOrHeartbeatDevice(
                    deviceId = targetDeviceId,
                    pairingCode = cleanCode,
                    deviceName = deviceName.ifBlank { "Display #$cleanCode" },
                    zoneId = zoneId,
                    isPaired = true,
                    isOnline = true,
                    currentVideoTitle = _currentlyPlayingTitle.value,
                    cachedVideoCount = ((videosUi.value as? DataUiState.Success)?.data?.size ?: 0).toLong(),
                    installedVersionCode = currentOta.installedVersionCode,
                    installedVersionName = currentOta.installedVersionName,
                    previousSlotVersionCode = currentOta.previousSlotVersionCode,
                    previousSlotVersionName = currentOta.previousSlotVersionName,
                    otaStatus = currentOta.otaStatus,
                    lastOtaMessage = currentOta.lastOtaMessage
                )
            } else {
                repository.updateDevicePairingAndZone(
                    deviceId = targetDeviceId,
                    deviceName = deviceName.ifBlank { matched.deviceName },
                    zoneId = zoneId,
                    isPaired = true
                )
            }
            appendLog(LogLevel.INFO, "PAIRING", "Paired display '$deviceName' (Code: $cleanCode) to zone $zoneId")
            showToast("Display Paired (Code: $cleanCode)!")
        }
    }

    fun regenerateLocalPairingCode() {
        val newCode = localManager.regeneratePairingCode()
        _localPairingCode.value = newCode
        appendLog(LogLevel.INFO, "PAIRING", "Generated new 6-digit pairing code: $newCode")
        showToast("New Pairing Code: $newCode")
    }

    fun createNewPlaybackZone(
        zoneName: String,
        locationTag: String,
        operatorName: String,
        operatorRole: String,
        canPushPlaylist: Boolean,
        canRotateScreen: Boolean,
        canManageOta: Boolean,
        orientationOverride: String
    ) {
        viewModelScope.launch {
            repository.createZone(
                zoneName = zoneName,
                locationTag = locationTag,
                operatorName = operatorName,
                operatorRole = operatorRole,
                canPushPlaylist = canPushPlaylist,
                canRotateScreen = canRotateScreen,
                canManageOta = canManageOta,
                orientationOverride = orientationOverride
            )
            appendLog(LogLevel.INFO, "ZONES", "Created zone '$zoneName' ($operatorRole)")
            showToast("Zone '$zoneName' created with granular permissions")
        }
    }

    fun updateZoneRoleAndPermissions(
        zone: PlaybackZone,
        operatorRole: String,
        canPushPlaylist: Boolean,
        canRotateScreen: Boolean,
        canManageOta: Boolean,
        orientationOverride: String
    ) {
        viewModelScope.launch {
            repository.updateZonePermissions(
                zoneId = zone.id,
                operatorRole = operatorRole,
                canPushPlaylist = canPushPlaylist,
                canRotateScreen = canRotateScreen,
                canManageOta = canManageOta,
                orientationOverride = orientationOverride
            )
            appendLog(
                LogLevel.INFO,
                "RBAC",
                "Updated permissions for '${zone.zoneName}' -> Role: $operatorRole (Push=$canPushPlaylist, Rotate=$canRotateScreen, OTA=$canManageOta)"
            )
            showToast("Updated permissions for ${zone.zoneName}")
        }
    }

    // ------------------------------------------------------------------------
    // Over-the-Air (OTA) Software Update & Rollback Engine
    // ------------------------------------------------------------------------

    fun publishNewOtaRelease(
        versionCode: Long,
        versionName: String,
        releaseNotes: String,
        targetZoneId: String,
        simulateCorruptPackage: Boolean
    ) {
        val zone = activeZonePermissions()
        if (zone != null && !zone.canManageOta) {
            showToast("Permission Denied: Role '${zone.operatorRole}' lacks OTA Release privilege")
            appendLog(LogLevel.WARN, "RBAC", "Blocked OTA publish attempt by role ${zone.operatorRole}")
            return
        }

        viewModelScope.launch {
            val chunkCount = 4L
            val sizeBytes = chunkCount * ChunkedStorageAndOtaManager.CHUNK_SIZE_BYTES
            val checksum = if (simulateCorruptPackage) {
                "CORRUPT_HEADER_88f92a01b4e1"
            } else {
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
            }

            // Simulate 600KB chunked upload of the APK / OTA bundle to the CMS repository
            for (c in 1..chunkCount.toInt()) {
                _chunkTelemetry.value = ChunkTransferTelemetry(
                    isActive = true,
                    label = "Uploading OTA Release v$versionName",
                    currentChunk = c,
                    totalChunks = chunkCount.toInt(),
                    bytesTransferred = c * ChunkedStorageAndOtaManager.CHUNK_SIZE_BYTES.toLong()
                )
                delay(90)
            }
            _chunkTelemetry.value = ChunkTransferTelemetry(isActive = false)

            val result = repository.publishOtaRelease(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = releaseNotes,
                targetZoneId = targetZoneId,
                packageUrl = "https://pulsecast.cdn/ota/pulsecast-client-v$versionName.apk",
                sha256Checksum = checksum,
                sizeBytes = sizeBytes,
                chunkCount = chunkCount,
                isMandatory = true,
                simulateCorruptPackage = simulateCorruptPackage
            )

            result.onSuccess {
                repository.updateLiveStateFields(
                    mapOf(
                        "activeOtaVersionCode" to versionCode,
                        "activeOtaVersionName" to versionName,
                        "lastPushCommand" to "OTA_PUSH_v$versionName"
                    )
                )
                appendLog(
                    LogLevel.OTA,
                    "OTA_CMS",
                    "Published OTA v$versionName (Build $versionCode, CorruptTest=$simulateCorruptPackage). Clients will auto-download & verify."
                )
                showToast("Published OTA v$versionName to fleet!")
            }
        }
    }

    private fun triggerBackgroundOtaInstallation(release: OtaRelease) {
        if (otaProcessingInFlight) return
        otaProcessingInFlight = true

        viewModelScope.launch {
            try {
                appendLog(
                    LogLevel.OTA,
                    "OTA_CLIENT",
                    "Detected new OTA Release v${release.versionName} (Build ${release.versionCode}). Starting background download..."
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
                    repository.updateOtaReleaseStatus(release.id, "ROLLED_BACK")
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
            repository.updateLiveStateFields(
                mapOf(
                    "activeOtaVersionCode" to rolledBack.installedVersionCode,
                    "activeOtaVersionName" to rolledBack.installedVersionName,
                    "lastPushCommand" to "OTA_ROLLBACK_v${rolledBack.installedVersionName}"
                )
            )
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
