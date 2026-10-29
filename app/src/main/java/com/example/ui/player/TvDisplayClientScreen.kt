package com.example.ui.player

import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LiveState
import com.example.data.model.LogLevel
import com.example.data.model.PlaylistVideo
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BroadcastIndigo
import com.example.ui.theme.ControlDeckSurface
import com.example.ui.theme.ControlDeckSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WatchdogAmber
import com.example.ui.viewmodel.ChunkTransferTelemetry
import com.example.ui.viewmodel.SignageViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun TvDisplayClientScreen(
    viewModel: SignageViewModel,
    localManager: ChunkedStorageAndOtaManager,
    cmsServerUrl: String,
    isWebCmsConnected: Boolean,
    deviceName: String,
    assignedVideoId: String,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    pairingCode: String,
    deviceId: String,
    otaSlotState: OtaSlotState,
    otaProgressPercent: Int?,
    chunkTelemetry: ChunkTransferTelemetry,
    diagnosticLogs: List<DiagnosticLogEntry>,
    isUsingBackupStream: Boolean,
    lastSyncCycleCount: Long,
    isPinDialogVisible: Boolean,
    isAdminPanelOpen: Boolean,
    pinError: String?
) {
    val context = LocalContext.current
    var isBufferingNow by remember { mutableStateOf(false) }
    var currentMediaIndex by remember { mutableIntStateOf(0) }

    // Kiosk Mode Lock back handler
    BackHandler(enabled = true) {
        if (isAdminPanelOpen) {
            viewModel.closeAdminPanel()
        } else if (isPinDialogVisible) {
            viewModel.dismissAdminPinPrompt()
        } else if (liveState.kioskLocked) {
            viewModel.showToast("Kiosk Mode Locked — Tap top-right corner 5x to unlock Admin Panel")
        }
    }

    // Resilient streaming DataSource with fast 4000ms connection/read timeouts
    val dataSourceFactory = remember(context) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(4000)
            .setReadTimeoutMs(4000)
            .setAllowCrossProtocolRedirects(true)
        DefaultDataSource.Factory(context, httpFactory)
    }

    // Media3 ExoPlayer configured for gapless looping (ConcatenatingMediaSource + REPEAT_MODE_ALL)
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    // Apply mute state immediately
    LaunchedEffect(liveState.isMuted) {
        exoPlayer.volume = if (liveState.isMuted) 0f else 1f
    }

    // Rebuild ConcatenatingMediaSource whenever the playlist changes
    LaunchedEffect(videos, isUsingBackupStream) {
        if (videos.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            viewModel.onActiveVideoChanged("Standby (Empty Playlist)")
        } else {
            val concatenatingSource = ConcatenatingMediaSource()
            videos.forEach { video ->
                val localUri = if (video.id.startsWith("local_")) {
                    localManager.getLocalVideoUri(video.localFileName)
                } else null
                val uriToPlay = when {
                    isUsingBackupStream -> Uri.parse(video.backupUrl.ifBlank { SignageViewModel.VERIFIED_BACKUP_STREAM_URL })
                    localUri != null -> localUri
                    else -> Uri.parse(video.sourceUrl.ifBlank { SignageViewModel.VERIFIED_BACKUP_STREAM_URL })
                }
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(
                        MediaItem.Builder()
                            .setUri(uriToPlay)
                            .setMediaId(video.id)
                            .build()
                    )
                concatenatingSource.addMediaSource(mediaSource)
            }
            exoPlayer.setMediaSource(concatenatingSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            val firstTitle = videos.firstOrNull()?.title ?: "Playing"
            viewModel.onActiveVideoChanged(firstTitle)
        }
    }

    // Player Listener for track transitions and IOException/404 auto-recovery
    DisposableEffect(exoPlayer, videos) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBufferingNow = (playbackState == Player.STATE_BUFFERING)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = exoPlayer.currentMediaItemIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
                currentMediaIndex = idx
                videos.getOrNull(idx)?.let { activeVid ->
                    viewModel.onActiveVideoChanged(activeVid.title)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val stalledVid = videos.getOrNull(currentMediaIndex)?.title ?: "Stream"
                viewModel.onBufferingWatchdogTriggered(
                    stalledVideoTitle = stalledVid,
                    reason = "ExoPlayer ${error.errorCodeName}"
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 3.5-Second Buffering Watchdog Coroutine
    LaunchedEffect(isBufferingNow, videos) {
        if (isBufferingNow && videos.isNotEmpty() && !isUsingBackupStream) {
            delay(3500L)
            if (isBufferingNow) {
                val stalledTitle = videos.getOrNull(currentMediaIndex)?.title ?: "Active Ad Stream"
                viewModel.onBufferingWatchdogTriggered(
                    stalledVideoTitle = stalledTitle,
                    reason = "Buffering exceeded 3.5s threshold"
                )
            }
        }
    }

    val rotationDegrees = when (liveState.orientation) {
        "PORTRAIT", "ROTATE_90" -> 90f
        "ROTATE_180" -> 180f
        "ROTATE_270" -> 270f
        else -> 0f
    }

    val resizeMode = when (liveState.scaleMode) {
        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU ||
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_SETTINGS)
                ) {
                    viewModel.openAdminPinPrompt()
                    true
                } else {
                    false
                }
            }
    ) {
        // 1. Video Player Canvas or Empty-State Broadcast Standby Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotationDegrees
                },
            contentAlignment = Alignment.Center
        ) {
            if (videos.isEmpty()) {
                StandbyPairingBroadcastCanvas(
                    cmsServerUrl = cmsServerUrl,
                    isWebCmsConnected = isWebCmsConnected,
                    deviceName = deviceName,
                    pairingCode = pairingCode,
                    deviceId = deviceId,
                    liveState = liveState,
                    otaSlotState = otaSlotState,
                    lastSyncCycleCount = lastSyncCycleCount,
                    onOpenAdminPin = { viewModel.openAdminPinPrompt() },
                    onForceSync = { viewModel.forceImmediateSync() }
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = exoPlayer
                            this.resizeMode = resizeMode
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.resizeMode = resizeMode
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tv_exoplayer_view")
                )
            }
        }

        // 2. Top-Left Minimal Telemetry HUD Pill (Shows Web CMS Connection Dot, Pairing Code, Offline Verified ✔, Orientation)
        val activeVideo = videos.getOrNull(currentMediaIndex)
        val isActiveVideoCached = activeVideo?.let {
            it.isVerifiedCached || localManager.isFileVerifiedCached(it.localFileName)
        } ?: false

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(ObsidianBackground.copy(alpha = 0.82f))
                .border(1.dp, BorderSlate, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isWebCmsConnected) SignalGreen else AlertCrimson)
            )
            Text(
                text = "PAIR: $pairingCode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (videos.isNotEmpty()) {
                Text(text = "•", color = TextMuted)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = activeVideo?.title ?: "Looping",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    if (isActiveVideoCached) {
                        Text(
                            text = "✔",
                            color = SignalGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Text(
                text = "• ${liveState.orientation} (${liveState.scaleMode})",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
            Icon(
                imageVector = if (liveState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (liveState.isMuted) "Audio Muted" else "Audio Active",
                tint = if (liveState.isMuted) WatchdogAmber else SignalGreen,
                modifier = Modifier.size(16.dp)
            )
        }

        // 3. 600KB Chunk Sync / Background OTA Progress Banner
        AnimatedVisibility(
            visible = chunkTelemetry.isActive || otaProgressPercent != null || otaSlotState.otaStatus == "ROLLED_BACK",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (otaSlotState.otaStatus == "ROLLED_BACK") {
                        AlertCrimson.copy(alpha = 0.92f)
                    } else {
                        ControlDeckSurface.copy(alpha = 0.92f)
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .border(
                        1.dp,
                        if (otaSlotState.otaStatus == "ROLLED_BACK") AlertCrimson else ElectricCyan,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (chunkTelemetry.isActive) {
                        Text(
                            text = "${chunkTelemetry.label} — Chunk ${chunkTelemetry.currentChunk}/${chunkTelemetry.totalChunks} (600KB)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val pct = if (chunkTelemetry.totalChunks > 0) {
                            chunkTelemetry.currentChunk.toFloat() / chunkTelemetry.totalChunks.toFloat()
                        } else 0.5f
                        LinearProgressIndicator(
                            progress = { pct.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricCyan,
                            trackColor = BorderSlate
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (otaSlotState.otaStatus == "ROLLED_BACK") {
                                    Icons.Default.SettingsBackupRestore
                                } else {
                                    Icons.Default.SystemUpdateAlt
                                },
                                contentDescription = "OTA Status",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (otaSlotState.otaStatus == "ROLLED_BACK") {
                                    "OTA AUTO-ROLLBACK EXECUTED (${otaSlotState.activeSlot})"
                                } else {
                                    "BACKGROUND OTA UPDATE (${otaSlotState.otaStatus})"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = otaSlotState.lastOtaMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (otaProgressPercent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (otaProgressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = ElectricCyan,
                                trackColor = BorderSlate
                            )
                        }
                    }
                }
            }
        }

        // 4. Hidden 5-Tap Top-Right Corner Gesture Zone (96dp touch target for Kiosk Admin Unlock)
        val cornerInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .size(96.dp)
                .clickable(
                    interactionSource = cornerInteraction,
                    indication = null,
                    onClick = { viewModel.onCornerSecretTap() }
                )
                .testTag("hidden_admin_corner_tap_zone"),
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                color = ObsidianBackground.copy(alpha = 0.65f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .padding(16.dp)
                    .border(1.dp, BorderSlate.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (liveState.kioskLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Tap 5 times for Admin Panel",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "5x PIN",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // 5. PIN Verification Dialog
        if (isPinDialogVisible) {
            AdminPinVerificationDialog(
                pinError = pinError,
                onVerifyPin = { entered -> viewModel.verifyAdminPin(entered) },
                onDismiss = { viewModel.dismissAdminPinPrompt() }
            )
        }

        // 6. Protected Admin Settings Panel Overlay
        if (isAdminPanelOpen) {
            ProtectedAdminSettingsModal(
                cmsServerUrl = cmsServerUrl,
                isWebCmsConnected = isWebCmsConnected,
                deviceName = deviceName,
                pairingCode = pairingCode,
                assignedVideoId = assignedVideoId,
                liveState = liveState,
                videos = videos,
                otaSlotState = otaSlotState,
                diagnosticLogs = diagnosticLogs,
                localManager = localManager,
                onUpdateCmsUrl = { viewModel.updateCmsServerUrl(it) },
                onUpdateDeviceName = { viewModel.updateDeviceName(it) },
                onRegeneratePairingCode = { viewModel.regenerateSixDigitPairingCode() },
                onForceSync = { viewModel.forceImmediateSync() },
                onSetOrientation = { viewModel.setOrientation(it) },
                onSetScaleMode = { viewModel.setScaleMode(it) },
                onToggleMute = { viewModel.toggleAudioMute(it) },
                onToggleKioskLock = { viewModel.toggleKioskLock(it) },
                onIngestLocalVideo = { uri, title -> viewModel.ingestLocalVideoForOfflineCache(uri, title) },
                onDeleteVideo = { viewModel.deleteVideoFromPlaylist(it) },
                onManualOtaRollback = { viewModel.triggerManualOtaRollback() },
                onClearLogs = { viewModel.clearDiagnosticLogs() },
                onClose = { viewModel.closeAdminPanel() }
            )
        }
    }
}

@Composable
private fun StandbyPairingBroadcastCanvas(
    cmsServerUrl: String,
    isWebCmsConnected: Boolean,
    deviceName: String,
    pairingCode: String,
    deviceId: String,
    liveState: LiveState,
    otaSlotState: OtaSlotState,
    lastSyncCycleCount: Long,
    onOpenAdminPin: () -> Unit,
    onForceSync: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BroadcastIndigo.copy(alpha = 0.25f),
                        ObsidianBackground
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 64.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = BorderSlate.copy(alpha = 0.16f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = BorderSlate.copy(alpha = 0.16f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .padding(24.dp)
                .border(1.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface.copy(alpha = 0.94f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "ANDROID TV & DIGITAL DISPLAY CLIENT",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Web CMS Connection Pill
                Surface(
                    color = if (isWebCmsConnected) SignalGreen.copy(alpha = 0.15f) else WatchdogAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        1.dp,
                        if (isWebCmsConnected) SignalGreen.copy(alpha = 0.4f) else WatchdogAmber.copy(alpha = 0.4f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isWebCmsConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isWebCmsConnected) SignalGreen else WatchdogAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isWebCmsConnected) {
                                "LIVE CONNECTED TO $cmsServerUrl"
                            } else {
                                "POLLING WEB CMS: $cmsServerUrl"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isWebCmsConnected) SignalGreen else WatchdogAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Controlled remotely from your Web CMS Dashboard ($cmsServerUrl). Enter this 6-digit pairing code in the Web Dashboard to control orientation, scale, and video playback live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 6-Digit Pairing Code Display Box
                Surface(
                    color = ObsidianBackground,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "6-DIGIT SCREEN PAIRING CODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pairingCode.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displayLarge,
                            color = ElectricCyan,
                            letterSpacing = 6.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TelemetryStatChip(label = "2.5s Sync Polls", value = "#$lastSyncCycleCount")
                    TelemetryStatChip(label = "Orientation", value = liveState.orientation)
                    TelemetryStatChip(
                        label = "OTA Slot",
                        value = "${otaSlotState.activeSlot} (v${otaSlotState.installedVersionName})"
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAdminPin,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_admin_pin_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Admin Panel (PIN)")
                    }

                    Button(
                        onClick = onForceSync,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sync_web_cms_now_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Web CMS Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = SignalGreen
        )
    }
}

@Composable
private fun AdminPinVerificationDialog(
    pinError: String?,
    onVerifyPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Protected Admin Access",
                    tint = ElectricCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Protected Admin Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Enter operator PIN code (Default: 1234) to configure Web CMS URL, orientation overrides, offline cache, and OTA rollback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { if (it.length <= 8) enteredPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Operator PIN") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_pin_input")
                )

                if (!pinError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pinError,
                        color = AlertCrimson,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onVerifyPin(enteredPin) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("verify_pin_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Text("Unlock Admin", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedAdminSettingsModal(
    cmsServerUrl: String,
    isWebCmsConnected: Boolean,
    deviceName: String,
    pairingCode: String,
    assignedVideoId: String,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    otaSlotState: OtaSlotState,
    diagnosticLogs: List<DiagnosticLogEntry>,
    localManager: ChunkedStorageAndOtaManager,
    onUpdateCmsUrl: (String) -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onRegeneratePairingCode: () -> Unit,
    onForceSync: () -> Unit,
    onSetOrientation: (String) -> Unit,
    onSetScaleMode: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleKioskLock: (Boolean) -> Unit,
    onIngestLocalVideo: (Uri, String) -> Unit,
    onDeleteVideo: (PlaylistVideo) -> Unit,
    onManualOtaRollback: () -> Unit,
    onClearLogs: () -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Web CMS & Display",
        "Offline Cache (${videos.size})",
        "OTA & Rollback",
        "Logs (${diagnosticLogs.size})"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .fillMaxHeight(0.92f)
                .border(1.dp, BorderSlate, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ControlDeckSurfaceVariant)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Column {
                            Text(
                                text = "TV Client Protected Admin Settings",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Code: $pairingCode • Web CMS: $cmsServerUrl (${if (isWebCmsConnected) "CONNECTED" else "OFFLINE"})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isWebCmsConnected) SignalGreen else WatchdogAmber
                            )
                        }
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Admin Panel"
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ControlDeckSurfaceVariant
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    when (selectedTab) {
                        0 -> AdminDisplayAndKioskTab(
                            cmsServerUrl = cmsServerUrl,
                            deviceName = deviceName,
                            pairingCode = pairingCode,
                            assignedVideoId = assignedVideoId,
                            liveState = liveState,
                            onUpdateCmsUrl = onUpdateCmsUrl,
                            onUpdateDeviceName = onUpdateDeviceName,
                            onRegeneratePairingCode = onRegeneratePairingCode,
                            onForceSync = onForceSync,
                            onSetOrientation = onSetOrientation,
                            onSetScaleMode = onSetScaleMode,
                            onToggleMute = onToggleMute,
                            onToggleKioskLock = onToggleKioskLock
                        )
                        1 -> AdminPlaylistManagerTab(
                            videos = videos,
                            localManager = localManager,
                            onIngestLocalVideo = onIngestLocalVideo,
                            onDeleteVideo = onDeleteVideo
                        )
                        2 -> AdminOtaRollbackTab(
                            otaSlotState = otaSlotState,
                            onManualOtaRollback = onManualOtaRollback
                        )
                        3 -> AdminDiagnosticLogsTab(
                            logs = diagnosticLogs,
                            onClearLogs = onClearLogs
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminDisplayAndKioskTab(
    cmsServerUrl: String,
    deviceName: String,
    pairingCode: String,
    assignedVideoId: String,
    liveState: LiveState,
    onUpdateCmsUrl: (String) -> Unit,
    onUpdateDeviceName: (String) -> Unit,
    onRegeneratePairingCode: () -> Unit,
    onForceSync: () -> Unit,
    onSetOrientation: (String) -> Unit,
    onSetScaleMode: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleKioskLock: (Boolean) -> Unit
) {
    var urlInput by remember(cmsServerUrl) { mutableStateOf(cmsServerUrl) }
    var nameInput by remember(deviceName) { mutableStateOf(deviceName) }
    val orientations = listOf("LANDSCAPE", "PORTRAIT", "ROTATE_90", "ROTATE_180", "ROTATE_270")
    val scales = listOf("FILL", "FIT", "ZOOM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Web CMS Dashboard Connection (2.5s Live Sync)",
            style = MaterialTheme.typography.titleMedium,
            color = ElectricCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Web CMS Server URL") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onUpdateCmsUrl(urlInput) },
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBackground
                )
            ) {
                Text("Save & Sync", fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("TV Screen Name (Shown in Web CMS)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onUpdateDeviceName(nameInput) },
                modifier = Modifier.height(52.dp)
            ) {
                Text("Rename TV")
            }
            OutlinedButton(
                onClick = onRegeneratePairingCode,
                modifier = Modifier.height(52.dp)
            ) {
                Text("New Code ($pairingCode)")
            }
        }

        HorizontalDivider(color = BorderSlate)

        Text(
            text = "Device Orientation (Synced with Web CMS • Assigned Video: $assignedVideoId)",
            style = MaterialTheme.typography.titleMedium,
            color = ElectricCyan
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            orientations.forEach { orient ->
                FilterChip(
                    selected = liveState.orientation == orient,
                    onClick = { onSetOrientation(orient) },
                    label = { Text(orient) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricCyan
                    )
                )
            }
        }

        Text(
            text = "Video Aspect Ratio & Scale Mode",
            style = MaterialTheme.typography.titleMedium,
            color = ElectricCyan
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scales.forEach { mode ->
                FilterChip(
                    selected = liveState.scaleMode == mode,
                    onClick = { onSetScaleMode(mode) },
                    label = { Text(mode) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BroadcastIndigo.copy(alpha = 0.25f),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        HorizontalDivider(color = BorderSlate)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kiosk Mode Lock",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Prevents accidental exit or back button navigation on unmanned displays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Switch(
                checked = liveState.kioskLocked,
                onCheckedChange = onToggleKioskLock
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mute Display Audio",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Synchronized in real time with Web CMS ($cmsServerUrl)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Switch(
                checked = liveState.isMuted,
                onCheckedChange = onToggleMute
            )
        }
    }
}

@Composable
private fun AdminPlaylistManagerTab(
    videos: List<PlaylistVideo>,
    localManager: ChunkedStorageAndOtaManager,
    onIngestLocalVideo: (Uri, String) -> Unit,
    onDeleteVideo: (PlaylistVideo) -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onIngestLocalVideo(uri, "Local Cached Spot")
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Synced & Offline Verified Videos (✔)",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan
            )
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cache Local Video (600KB Chunks)")
            }
        }

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Playlist is empty. Upload or assign videos from the Web CMS Dashboard (https://tv-br.vercel.app/).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(videos, key = { it.id }) { video ->
                    val isCached = video.isVerifiedCached || localManager.isFileVerifiedCached(video.localFileName)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ControlDeckSurfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (isCached) {
                                        Surface(
                                            color = SignalGreen.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "✔ Offline Verified",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SignalGreen,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Local: ${video.localFileName} • ${video.chunkCount} x 600KB chunks",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            IconButton(onClick = { onDeleteVideo(video) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Video",
                                    tint = AlertCrimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOtaRollbackTab(
    otaSlotState: OtaSlotState,
    onManualOtaRollback: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A/B Partition OTA Software Update Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan
                )
                Text(
                    text = "Active Partition: ${otaSlotState.activeSlot} • Installed Version: v${otaSlotState.installedVersionName} (Build ${otaSlotState.installedVersionCode})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Known-Good Backup Slot: v${otaSlotState.previousSlotVersionName} (Build ${otaSlotState.previousSlotVersionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalGreen
                )
                Text(
                    text = "Last OTA Event: ${otaSlotState.lastOtaMessage}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        Button(
            onClick = onManualOtaRollback,
            colors = ButtonDefaults.buttonColors(
                containerColor = WatchdogAmber,
                contentColor = ObsidianBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("admin_manual_ota_rollback_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Execute Instant Rollback to v${otaSlotState.previousSlotVersionName}",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AdminDiagnosticLogsTab(
    logs: List<DiagnosticLogEntry>,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Telemetry & Watchdog Diagnostic Stream",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan
            )
            TextButton(onClick = onClearLogs) {
                Text("Clear Logs")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianBackground)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs, key = { it.id }) { entry ->
                val badgeColor = when (entry.level) {
                    LogLevel.INFO -> ElectricCyan
                    LogLevel.WARN -> WatchdogAmber
                    LogLevel.ERROR -> AlertCrimson
                    LogLevel.RECOVERY -> SignalGreen
                    LogLevel.OTA -> BroadcastIndigo
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.timestampFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
