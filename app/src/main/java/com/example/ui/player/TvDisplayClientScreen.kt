package com.example.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.KeyEvent
import android.view.LayoutInflater
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.model.DiagnosticLogEntry
import com.example.data.model.LiveState
import com.example.data.model.LogLevel
import com.example.data.model.PlaylistVideo
import com.example.system.KioskPermissionHelper
import com.example.system.KioskPermissionsStatus
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
    isAutoSyncEnabled: Boolean = true,
    isPairedWithCms: Boolean,
    deviceName: String,
    assignedVideoId: String,
    screenOrientation: String,
    videoRotateDegrees: Int,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    allCloudVideos: List<PlaylistVideo>,
    forcedOfflineVideoId: String?,
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
    selectedSidebarTab: Int,
    pinError: String?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBufferingNow by remember { mutableStateOf(false) }
    var currentMediaIndex by remember { mutableIntStateOf(0) }
    var permissionsStatus by remember { mutableStateOf(KioskPermissionHelper.checkAllPermissions(context)) }
    var quarantinedVideoIds by remember { mutableStateOf(setOf<String>()) }

    // Apply system Activity screen orientation when user changes Landscape / Portrait
    LaunchedEffect(screenOrientation) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val targetOrientation = if (screenOrientation.equals("PORTRAIT", ignoreCase = true)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (activity.requestedOrientation != targetOrientation) {
            activity.requestedOrientation = targetOrientation
        }
    }

    // Back button closes Admin Hub or PIN dialog first
    BackHandler(enabled = true) {
        if (isAdminPanelOpen) {
            viewModel.closeAdminPanel()
        } else if (isPinDialogVisible) {
            viewModel.dismissAdminPinPrompt()
        } else if (liveState.kioskLocked) {
            viewModel.showToast("Kiosk Locked — Tap the top-right ★ Green Star to unlock Admin")
        }
    }

    // Resilient DataSource with Chrome User-Agent and redirect support
    val dataSourceFactory = remember(context) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; SignageTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setAllowCrossProtocolRedirects(true)
        DefaultDataSource.Factory(context, httpFactory)
    }

    // Media3 ExoPlayer configured with software decoder fallback & native gapless looping (REPEAT_MODE_ALL)
    val exoPlayer = remember(context) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
            }
    }

    // Automatically manage hardware MediaCodec across background/foreground transitions and ensure hands-free auto-play on resume
    DisposableEffect(lifecycleOwner, context, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    permissionsStatus = KioskPermissionHelper.checkAllPermissions(context)
                    exoPlayer.playWhenReady = true
                    if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                        exoPlayer.prepare()
                    }
                    exoPlayer.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Release hardware MediaCodec cleanly when app is backgrounded so Android ResourceManager never force-kills it
                    exoPlayer.playWhenReady = false
                    exoPlayer.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isAdminPanelOpen) {
        while (isAdminPanelOpen) {
            permissionsStatus = KioskPermissionHelper.checkAllPermissions(context)
            delay(1500L)
        }
    }

    LaunchedEffect(liveState.isMuted) {
        exoPlayer.volume = if (liveState.isMuted) 0f else 1f
    }

    val effectiveVideos = remember(videos, allCloudVideos, forcedOfflineVideoId) {
        val cleanVideos = localManager.sortAndNormalizeReelsInLine(
            videos.filter { !localManager.isDemoOrForbiddenUrl(it.sourceUrl) }
        )
        val cleanAll = localManager.sortAndNormalizeReelsInLine(
            allCloudVideos.filter { !localManager.isDemoOrForbiddenUrl(it.sourceUrl) }
        )
        if (!forcedOfflineVideoId.isNullOrBlank()) {
            val combined = localManager.sortAndNormalizeReelsInLine((cleanVideos + cleanAll).distinctBy { it.id })
            val match = combined.filter { it.id == forcedOfflineVideoId }
            if (match.isNotEmpty()) match else cleanVideos
        } else {
            cleanVideos
        }
    }

    // Play ONLY verified local offline media files in ExoPlayer in strict sequential order (reel-1, reel-2, reel-3, reel-4...)
    // so ExoPlayer NEVER makes direct HTTP requests or throws HttpDataSource$InvalidResponseCodeException (404 / 403) or ParserException.
    val playableVideos = remember(effectiveVideos, videos, allCloudVideos, chunkTelemetry.isActive, quarantinedVideoIds) {
        effectiveVideos
            .filter { v ->
                v.id !in quarantinedVideoIds &&
                    v.localFileName.lowercase(java.util.Locale.US) !in quarantinedVideoIds &&
                    localManager.isRealMp4PlayableOffline(v.localFileName) &&
                    localManager.getLocalVideoUri(v.localFileName) != null
            }
            .distinctBy { it.localFileName.trim().lowercase(java.util.Locale.US) }
            .sortedWith { a, b ->
                val na = localManager.extractReelNumber(a.title)
                    ?: localManager.extractReelNumber(a.localFileName)
                    ?: Int.MAX_VALUE
                val nb = localManager.extractReelNumber(b.title)
                    ?: localManager.extractReelNumber(b.localFileName)
                    ?: Int.MAX_VALUE
                if (na != nb) na.compareTo(nb) else a.sortOrder.compareTo(b.sortOrder)
            }
    }

    // Key playlist signature STRICTLY by ordered local filenames (reel-1.mp4|reel-2.mp4...) so cloud ID/metadata
    // updates during 2.0s background sync NEVER reset or blink the video player!
    val playlistSignature = remember(playableVideos) {
        playableVideos.joinToString("|") { v ->
            v.localFileName.trim().lowercase(java.util.Locale.US)
        }
    }

    val loadedMediaKeys = remember { mutableListOf<String>() }

    LaunchedEffect(playlistSignature) {
        try {
            if (playableVideos.isEmpty()) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                loadedMediaKeys.clear()
                val statusTitle = if (effectiveVideos.isEmpty()) {
                    "Standby (Upload Video on Web CMS)"
                } else {
                    "Syncing Video: ${effectiveVideos.first().title}"
                }
                viewModel.onActiveVideoChanged(statusTitle)
            } else {
                val targetKeys = playableVideos.map { it.localFileName.trim().lowercase(java.util.Locale.US) }
                val isPrefixAppend = loadedMediaKeys.isNotEmpty() &&
                    targetKeys.size > loadedMediaKeys.size &&
                    targetKeys.subList(0, loadedMediaKeys.size) == loadedMediaKeys.toList() &&
                    exoPlayer.mediaItemCount == loadedMediaKeys.size

                if (isPrefixAppend) {
                    // Seamlessly append newly cached reels (e.g. reel-2, reel-3, reel-4) in line WITHOUT touching or blinking reel-1!
                    for (i in loadedMediaKeys.size until playableVideos.size) {
                        val video = playableVideos[i]
                        val fileKey = video.localFileName.trim().lowercase(java.util.Locale.US)
                        val playableOfflineUri = localManager.getLocalVideoUri(video.localFileName) ?: continue
                        val mediaItem = MediaItem.Builder()
                            .setUri(playableOfflineUri)
                            .setMediaId(fileKey)
                            .build()
                        exoPlayer.addMediaItem(mediaItem)
                        loadedMediaKeys.add(fileKey)
                    }
                    if (!exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_READY) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                } else if (targetKeys != loadedMediaKeys.toList() || exoPlayer.mediaItemCount == 0) {
                    // Preserve currently playing local file & position if it still exists in the updated sequential list
                    val currentMediaKey = exoPlayer.currentMediaItem?.mediaId
                    val preservedPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val wasPlaying = exoPlayer.isPlaying

                    val newMediaItems = mutableListOf<MediaItem>()
                    val newKeys = mutableListOf<String>()
                    playableVideos.forEach { video ->
                        val fileKey = video.localFileName.trim().lowercase(java.util.Locale.US)
                        val playableOfflineUri = localManager.getLocalVideoUri(video.localFileName) ?: return@forEach
                        val mediaItem = MediaItem.Builder()
                            .setUri(playableOfflineUri)
                            .setMediaId(fileKey)
                            .build()
                        newMediaItems.add(mediaItem)
                        newKeys.add(fileKey)
                    }

                    if (newMediaItems.isNotEmpty()) {
                        loadedMediaKeys.clear()
                        loadedMediaKeys.addAll(newKeys)
                        val newIndex = if (currentMediaKey != null) {
                            newKeys.indexOf(currentMediaKey).takeIf { it >= 0 } ?: 0
                        } else {
                            0
                        }
                        val seekPos = if (currentMediaKey != null && newKeys.getOrNull(newIndex) == currentMediaKey) {
                            preservedPositionMs
                        } else {
                            0L
                        }
                        exoPlayer.setMediaItems(newMediaItems, newIndex, seekPos)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            if (wasPlaying || exoPlayer.playWhenReady) {
                                exoPlayer.play()
                            }
                        }
                        val activeTitle = playableVideos.getOrNull(newIndex)?.title
                            ?: playableVideos.firstOrNull()?.title
                            ?: "Playing"
                        viewModel.onActiveVideoChanged(activeTitle)
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    DisposableEffect(exoPlayer, playableVideos) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                try {
                    isBufferingNow = (playbackState == Player.STATE_BUFFERING)
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                    if (playbackState == Player.STATE_READY && !exoPlayer.isPlaying) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    } else if (playbackState == Player.STATE_ENDED && playableVideos.isNotEmpty()) {
                        exoPlayer.seekTo(0, 0L)
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                } catch (_: Throwable) {
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    val idx = exoPlayer.currentMediaItemIndex.coerceIn(0, (playableVideos.size - 1).coerceAtLeast(0))
                    currentMediaIndex = idx
                    playableVideos.getOrNull(idx)?.let { activeVid ->
                        viewModel.onActiveVideoChanged(activeVid.title)
                    }
                } catch (_: Throwable) {
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                try {
                    val failedMediaKey = exoPlayer.currentMediaItem?.mediaId
                    val activeVid = playableVideos.firstOrNull {
                        it.id == failedMediaKey ||
                            it.localFileName.trim().lowercase(java.util.Locale.US) == failedMediaKey
                    } ?: playableVideos.getOrNull(currentMediaIndex)
                    if (activeVid != null) {
                        // Quarantine this video immediately so ExoPlayer never retries a malformed file in a loop
                        quarantinedVideoIds = quarantinedVideoIds +
                            activeVid.id +
                            activeVid.localFileName.trim().lowercase(java.util.Locale.US)
                        localManager.deleteCachedVideoFile(activeVid.localFileName)
                    }
                } catch (_: Throwable) {
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // 24x7 Non-Stop Playback Watchdog: ensures video NEVER stops or pauses while in foreground
    LaunchedEffect(exoPlayer, playableVideos, lifecycleOwner) {
        while (true) {
            delay(2500L)
            if (playableVideos.isNotEmpty() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                try {
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                    exoPlayer.playWhenReady = true
                    if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                        exoPlayer.prepare()
                        exoPlayer.play()
                    } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0, 0L)
                        exoPlayer.play()
                    } else if (exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.isPlaying) {
                        exoPlayer.play()
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val resizeMode = when (liveState.scaleMode) {
        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    val activePreviewTitle = playableVideos.getOrNull(currentMediaIndex)?.title
        ?: effectiveVideos.firstOrNull()?.title
        ?: "Standby"

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
                    viewModel.onGreenStarAdminClick(0)
                    true
                } else {
                    false
                }
            }
    ) {
        // 1. Video Player Canvas or Broadcast Standby / Caching Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (playableVideos.isEmpty()) {
                StandbyPairingBroadcastCanvas(
                    isWebCmsConnected = isWebCmsConnected,
                    deviceName = deviceName,
                    pairingCode = pairingCode,
                    screenOrientation = screenOrientation,
                    videoRotateDegrees = videoRotateDegrees,
                    liveState = liveState,
                    permissionsStatus = permissionsStatus,
                    lastSyncCycleCount = lastSyncCycleCount,
                    onOpenAdminHubWithPin = { tabIdx -> viewModel.onGreenStarAdminClick(tabIdx) }
                )
            } else if (!isAdminPanelOpen) {
                RotatableTextureVideoPlayer(
                    exoPlayer = exoPlayer,
                    videoRotateDegrees = videoRotateDegrees,
                    scaleMode = liveState.scaleMode,
                    useController = false,
                    testTag = "tv_exoplayer_view"
                )
            }
        }

        // 2. Ultra-Minimal Top-Right Status Dot (Green = Live Synced, White = Disconnected) + Small Green Star (★)
        if (!isAdminPanelOpen) {
            Surface(
                color = ObsidianBackground.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 10.dp, end = 12.dp)
                    .border(
                        1.dp,
                        if (isWebCmsConnected) SignalGreen.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.45f),
                        RoundedCornerShape(50)
                    )
                    .clickable { viewModel.onGreenStarAdminClick(0) }
                    .testTag("top_right_green_star_admin_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Minimal Connection Status Dot: Green when connected & syncing live, White when disconnected
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isWebCmsConnected) SignalGreen else Color.White)
                            .testTag("minimal_sync_status_dot")
                    )

                    // Small Green Star (★) to open Built-In PIN Keypad & Admin Hub
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Open Admin Password Keypad",
                        tint = SignalGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Compact Offline Download / OTA Progress Toast Banner (Bottom-Left)
        AnimatedVisibility(
            visible = !isAdminPanelOpen && (chunkTelemetry.isActive || otaProgressPercent != null || otaSlotState.otaStatus == "ROLLED_BACK"),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (otaSlotState.otaStatus == "ROLLED_BACK") {
                        AlertCrimson.copy(alpha = 0.94f)
                    } else {
                        ControlDeckSurface.copy(alpha = 0.94f)
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .border(
                        1.dp,
                        if (otaSlotState.otaStatus == "ROLLED_BACK") AlertCrimson else ElectricCyan,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (chunkTelemetry.isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = chunkTelemetry.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${chunkTelemetry.progressPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = SignalGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (chunkTelemetry.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricCyan,
                            trackColor = BorderSlate
                        )
                    } else {
                        Text(
                            text = otaSlotState.lastOtaMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 4. Full-Screen Admin Hub with Top Back Button, Live Video Preview & Bottom Navigation Bar
        AnimatedVisibility(
            visible = isAdminPanelOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            AdminFullScreenHub(
                selectedTab = selectedSidebarTab,
                onSelectTab = { viewModel.selectSidebarTab(it) },
                exoPlayer = exoPlayer,
                hasPlayableVideo = playableVideos.isNotEmpty(),
                activePreviewTitle = activePreviewTitle,
                resizeMode = resizeMode,
                permissionsStatus = permissionsStatus,
                onRefreshPermissions = {
                    permissionsStatus = KioskPermissionHelper.checkAllPermissions(context)
                },
                cmsServerUrl = cmsServerUrl,
                isWebCmsConnected = isWebCmsConnected,
                isAutoSyncEnabled = isAutoSyncEnabled,
                isPairedWithCms = isPairedWithCms,
                deviceName = deviceName,
                pairingCode = pairingCode,
                assignedVideoId = assignedVideoId,
                screenOrientation = screenOrientation,
                videoRotateDegrees = videoRotateDegrees,
                liveState = liveState,
                videos = videos,
                allCloudVideos = allCloudVideos,
                forcedOfflineVideoId = forcedOfflineVideoId,
                chunkTelemetry = chunkTelemetry,
                otaSlotState = otaSlotState,
                diagnosticLogs = diagnosticLogs,
                lastSyncCycleCount = lastSyncCycleCount,
                localManager = localManager,
                onPairNowWithWebDashboard = { code, name, url ->
                    viewModel.pairNowWithWebDashboard(code, name, url)
                },
                onConnectToWebCms = { viewModel.connectToWebCms() },
                onDisconnectFromWebCms = { viewModel.disconnectFromWebCms() },
                onRegeneratePairingCode = { viewModel.regenerateSixDigitPairingCode() },
                onForceSync = { viewModel.forceImmediateSync() },
                onSetScreenOrientation = { viewModel.setScreenOrientationMode(it) },
                onSetVideoRotationAngle = { viewModel.setVideoRotationAngle(it) },
                onRotateVideoByDelta = { delta -> viewModel.rotateVideoByDelta(delta) },
                onSetScaleMode = { viewModel.setScaleMode(it) },
                onToggleMute = { viewModel.toggleAudioMute(it) },
                onToggleKioskLock = { viewModel.toggleKioskLock(it) },
                onSaveSettings = { viewModel.saveAllDisplayAndRotationSettings() },
                onDownloadCloudVideoOffline = { viewModel.downloadCloudVideoForOfflineView(it) },
                onDownloadAllCloudVideosOffline = { viewModel.downloadAllCloudVideosOffline() },
                onPlayVideoOfflineNow = { vid, closeAdmin ->
                    viewModel.playVideoOfflineNow(vid, closeAdminForFullScreen = closeAdmin)
                },
                onClearForcedOfflineVideo = { viewModel.clearForcedSingleOfflineVideo() },
                onIngestLocalVideo = { uri, title -> viewModel.ingestLocalVideoForOfflineCache(uri, title) },
                onDeleteVideo = { viewModel.deleteVideoFromPlaylist(it) },
                onManualOtaRollback = { viewModel.triggerManualOtaRollback() },
                onClearLogs = { viewModel.clearDiagnosticLogs() },
                onClose = { viewModel.closeAdminPanel() }
            )
        }

        // 5. Built-In Password Keypad Modal (Opens when tapping the ★ Green Star)
        if (isPinDialogVisible) {
            BuiltInKeypadPasswordDialog(
                pinError = pinError,
                expectedPinLength = 4,
                onVerifyPin = { entered -> viewModel.verifyAdminPin(entered) },
                onDismiss = { viewModel.dismissAdminPinPrompt() }
            )
        }
    }
}

@Composable
private fun StandbyPairingBroadcastCanvas(
    isWebCmsConnected: Boolean,
    deviceName: String,
    pairingCode: String,
    screenOrientation: String,
    videoRotateDegrees: Int,
    liveState: LiveState,
    permissionsStatus: KioskPermissionsStatus,
    lastSyncCycleCount: Long,
    onOpenAdminHubWithPin: (Int) -> Unit
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
                .widthIn(max = 540.dp)
                .padding(20.dp)
                .border(1.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
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
                        text = "PULSECAST ANDROID TV DISPLAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (isWebCmsConnected) SignalGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        1.dp,
                        if (isWebCmsConnected) SignalGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isWebCmsConnected) SignalGreen else Color.White)
                        )
                        Text(
                            text = if (isWebCmsConnected) {
                                "LIVE SYNC (2.0s)"
                            } else {
                                "OFFLINE STANDBY"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isWebCmsConnected) SignalGreen else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = ObsidianBackground,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "6-DIGIT PAIRING CODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pairingCode.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displayMedium,
                            color = ElectricCyan,
                            letterSpacing = 5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TelemetryStatChip(label = "2.0s Sync", value = "#$lastSyncCycleCount")
                    TelemetryStatChip(label = "24x7 Perms", value = "${permissionsStatus.grantedCount}/5")
                    TelemetryStatChip(label = "Display", value = "$screenOrientation (${videoRotateDegrees}°)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onOpenAdminHubWithPin(0) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("standby_open_admin_star_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalGreen,
                        contentColor = ObsidianBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Admin Settings (PIN: 1234)", fontWeight = FontWeight.Bold, maxLines = 1)
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
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = SignalGreen
        )
    }
}

// ----------------------------------------------------------------------------
// Built-In On-Screen Password Keypad Modal (Opens when tapping the ★ Green Star)
// ----------------------------------------------------------------------------

@Composable
private fun BuiltInKeypadPasswordDialog(
    pinError: String?,
    expectedPinLength: Int = 4,
    onVerifyPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    fun appendDigit(digit: String) {
        if (enteredPin.length < 8) {
            val next = enteredPin + digit
            enteredPin = next
            if (next.length == expectedPinLength) {
                onVerifyPin(next)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .border(1.5.dp, SignalGreen.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (val code = event.nativeKeyEvent.keyCode) {
                            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                                appendDigit((code - KeyEvent.KEYCODE_0).toString())
                                true
                            }
                            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                                appendDigit((code - KeyEvent.KEYCODE_NUMPAD_0).toString())
                                true
                            }
                            KeyEvent.KEYCODE_DEL -> {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                                true
                            }
                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                                onVerifyPin(enteredPin)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row with Back button and Star badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pin_dialog_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", color = TextSecondary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Admin Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Enter Password on Built-In Keypad (Default: 1234)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 4-Slot PIN Display Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianBackground)
                                .border(
                                    1.5.dp,
                                    if (isFilled) SignalGreen else BorderSlate,
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isFilled) "●" else "",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalGreen
                            )
                        }
                    }
                }

                if (!pinError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pinError,
                        color = AlertCrimson,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Built-In 3x4 Numeric Keypad Grid (No OS keyboard required!)
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("CLR", "0", "DEL")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    keypadRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { keyLabel ->
                                Surface(
                                    color = when (keyLabel) {
                                        "CLR" -> AlertCrimson.copy(alpha = 0.16f)
                                        "DEL" -> WatchdogAmber.copy(alpha = 0.16f)
                                        else -> ObsidianBackground
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .border(
                                            1.dp,
                                            when (keyLabel) {
                                                "CLR" -> AlertCrimson.copy(alpha = 0.45f)
                                                "DEL" -> WatchdogAmber.copy(alpha = 0.45f)
                                                else -> BorderSlate
                                            },
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            when (keyLabel) {
                                                "CLR" -> enteredPin = ""
                                                "DEL" -> if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                                else -> appendDigit(keyLabel)
                                            }
                                        }
                                        .testTag("keypad_btn_$keyLabel")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (keyLabel == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                tint = WatchdogAmber,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = keyLabel,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when (keyLabel) {
                                                    "CLR" -> AlertCrimson
                                                    else -> Color.White
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { onVerifyPin(enteredPin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("verify_pin_submit_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalGreen,
                        contentColor = ObsidianBackground
                    )
                ) {
                    Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Admin Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Full-Screen Admin Hub with Top Back Button & Bottom Navigation Bar
// ----------------------------------------------------------------------------

private data class BottomNavPageItem(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

@OptIn(UnstableApi::class)
@Composable
private fun InlineLiveVideoPreviewCard(
    exoPlayer: ExoPlayer,
    hasPlayableVideo: Boolean,
    activePreviewTitle: String,
    videoRotateDegrees: Int,
    scaleMode: String,
    resizeMode: Int,
    onOpenFullScreen: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE VIDEO PREVIEW (100% ORIGINAL QUALITY)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$activePreviewTitle • Rotate: ${videoRotateDegrees}° • Scale: $scaleMode",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onOpenFullScreen,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Full Screen TV", fontSize = 11.sp, maxLines = 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBackground)
                    .border(1.dp, BorderSlate, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPlayableVideo) {
                    RotatableTextureVideoPlayer(
                        exoPlayer = exoPlayer,
                        videoRotateDegrees = videoRotateDegrees,
                        scaleMode = scaleMode,
                        useController = true,
                        testTag = "admin_inline_video_preview"
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No video playing yet — upload in Web Dashboard or tap 'Add Local Video' in Videos tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun formatVideoBytesReadable(bytes: Long): String {
    if (bytes <= 0L) return "Original Quality"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.2f MB", mb)
    } else {
        "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }
}

/**
 * Inflates a TextureView-backed Media3 PlayerView (R.layout.exo_texture_player_view) inside a
 * dimension-aware BoxWithConstraints container so that:
 * 1) Rotating the video by 0°, 90°, 180° (upside-down TV), or 270° rotates the actual video texture
 *    instead of distorting the aspect ratio box.
 * 2) When rotated 90° or 270°, the unrotated layout width/height are swapped (maxHeight x maxWidth)
 *    so that after a 90°/270° rotation the video fills the exact screen bounds without clipping.
 * 3) Scale to Fit (FIT), Full Screen (FILL), and Zoom Video (ZOOM) work at every angle.
 */
@OptIn(UnstableApi::class)
@Composable
private fun RotatableTextureVideoPlayer(
    exoPlayer: ExoPlayer,
    videoRotateDegrees: Int,
    scaleMode: String,
    useController: Boolean,
    testTag: String
) {
    val normDegrees = ((videoRotateDegrees % 360) + 360) % 360
    val isQuarterTurn = normDegrees == 90 || normDegrees == 270
    val normalizedScaleMode = scaleMode.trim().uppercase(java.util.Locale.US)
    val exoResizeMode = when (normalizedScaleMode) {
        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    val zoomMultiplier = if (normalizedScaleMode == "ZOOM") 1.15f else 1.0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = if (isQuarterTurn) maxHeight else maxWidth
        val targetHeight = if (isQuarterTurn) maxWidth else maxHeight

        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.exo_texture_player_view, null, false) as PlayerView).apply {
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    this.useController = useController
                    if (useController) {
                        controllerShowTimeoutMs = 2000
                    }
                    player = exoPlayer
                    resizeMode = exoResizeMode
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                if (playerView.useController != useController) {
                    playerView.useController = useController
                }
                if (playerView.resizeMode != exoResizeMode) {
                    playerView.resizeMode = exoResizeMode
                    playerView.requestLayout()
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier
                .requiredSize(width = targetWidth, height = targetHeight)
                .graphicsLayer {
                    rotationZ = normDegrees.toFloat()
                    scaleX = zoomMultiplier
                    scaleY = zoomMultiplier
                }
                .testTag(testTag)
        )
    }
}

@Composable
private fun AdminFullScreenHub(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    exoPlayer: ExoPlayer,
    hasPlayableVideo: Boolean,
    activePreviewTitle: String,
    resizeMode: Int,
    permissionsStatus: KioskPermissionsStatus,
    onRefreshPermissions: () -> Unit,
    cmsServerUrl: String,
    isWebCmsConnected: Boolean,
    isAutoSyncEnabled: Boolean,
    isPairedWithCms: Boolean,
    deviceName: String,
    pairingCode: String,
    assignedVideoId: String,
    screenOrientation: String,
    videoRotateDegrees: Int,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    allCloudVideos: List<PlaylistVideo>,
    forcedOfflineVideoId: String?,
    chunkTelemetry: ChunkTransferTelemetry,
    otaSlotState: OtaSlotState,
    diagnosticLogs: List<DiagnosticLogEntry>,
    lastSyncCycleCount: Long,
    localManager: ChunkedStorageAndOtaManager,
    onPairNowWithWebDashboard: (String, String, String) -> Unit,
    onConnectToWebCms: () -> Unit,
    onDisconnectFromWebCms: () -> Unit,
    onRegeneratePairingCode: () -> Unit,
    onForceSync: () -> Unit,
    onSetScreenOrientation: (String) -> Unit,
    onSetVideoRotationAngle: (Int) -> Unit,
    onRotateVideoByDelta: (Int) -> Unit,
    onSetScaleMode: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleKioskLock: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onDownloadCloudVideoOffline: (PlaylistVideo) -> Unit,
    onDownloadAllCloudVideosOffline: () -> Unit,
    onPlayVideoOfflineNow: (PlaylistVideo, Boolean) -> Unit,
    onClearForcedOfflineVideo: () -> Unit,
    onIngestLocalVideo: (Uri, String) -> Unit,
    onDeleteVideo: (PlaylistVideo) -> Unit,
    onManualOtaRollback: () -> Unit,
    onClearLogs: () -> Unit,
    onClose: () -> Unit
) {
    val combinedVideos = remember(videos, allCloudVideos) {
        (allCloudVideos + videos).distinctBy { it.id }
    }

    val navItems = listOf(
        BottomNavPageItem(0, "Rotate", Icons.Default.ScreenRotation),
        BottomNavPageItem(1, "Pair", Icons.Default.Link),
        BottomNavPageItem(2, "Videos", Icons.Default.CloudDownload),
        BottomNavPageItem(3, "24x7 Boot", Icons.Default.Security),
        BottomNavPageItem(4, "OTA", Icons.Default.SystemUpdateAlt)
    )

    Scaffold(
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                color = ControlDeckSurfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Prominent Top-Left Back Button to return to Player
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .clickable { onClose() }
                            .testTag("admin_hub_back_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Video Player",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // Center Status Dot (Green = Connected, White = Offline) + Pairing Code
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isWebCmsConnected) SignalGreen else Color.White)
                        )
                        Column {
                            Text(
                                text = "Admin Settings • Code: $pairingCode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isWebCmsConnected) {
                                    "Live 2s Sync Connected • ${screenOrientation} ${videoRotateDegrees}° (${liveState.scaleMode})"
                                } else {
                                    "Offline Mode • ${screenOrientation} ${videoRotateDegrees}° (${liveState.scaleMode})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isWebCmsConnected) SignalGreen else Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Quick Save Button on Top Right
                    Button(
                        onClick = onSaveSettings,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalGreen,
                            contentColor = ObsidianBackground
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("admin_top_save_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ControlDeckSurfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(1.dp, BorderSlate)
            ) {
                navItems.forEach { item ->
                    val selected = selectedTab == item.index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelectTab(item.index) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ObsidianBackground,
                            selectedTextColor = SignalGreen,
                            indicatorColor = SignalGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("bottom_nav_tab_${item.index}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                when (selectedTab) {
                    0 -> AdminOrientationAndRotatePage(
                        exoPlayer = exoPlayer,
                        hasPlayableVideo = hasPlayableVideo,
                        activePreviewTitle = activePreviewTitle,
                        resizeMode = resizeMode,
                        screenOrientation = screenOrientation,
                        videoRotateDegrees = videoRotateDegrees,
                        liveState = liveState,
                        onSetScreenOrientation = onSetScreenOrientation,
                        onSetVideoRotationAngle = onSetVideoRotationAngle,
                        onRotateVideoByDelta = onRotateVideoByDelta,
                        onSetScaleMode = onSetScaleMode,
                        onToggleMute = onToggleMute,
                        onToggleKioskLock = onToggleKioskLock,
                        onSaveSettings = onSaveSettings,
                        onOpenFullScreen = onClose
                    )
                    1 -> AdminPairingAndSyncPage(
                        cmsServerUrl = cmsServerUrl,
                        isWebCmsConnected = isWebCmsConnected,
                        isAutoSyncEnabled = isAutoSyncEnabled,
                        deviceName = deviceName,
                        pairingCode = pairingCode,
                        lastSyncCycleCount = lastSyncCycleCount,
                        onPairNow = onPairNowWithWebDashboard,
                        onConnectToWebCms = onConnectToWebCms,
                        onDisconnectFromWebCms = onDisconnectFromWebCms,
                        onRegenerateCode = onRegeneratePairingCode,
                        onForceSync = onForceSync
                    )
                    2 -> AdminCloudAndOfflineVideosPage(
                        exoPlayer = exoPlayer,
                        hasPlayableVideo = hasPlayableVideo,
                        activePreviewTitle = activePreviewTitle,
                        videoRotateDegrees = videoRotateDegrees,
                        scaleMode = liveState.scaleMode,
                        resizeMode = resizeMode,
                        videos = combinedVideos,
                        forcedOfflineVideoId = forcedOfflineVideoId,
                        chunkTelemetry = chunkTelemetry,
                        localManager = localManager,
                        onDownloadCloudVideoOffline = onDownloadCloudVideoOffline,
                        onDownloadAllCloudVideosOffline = onDownloadAllCloudVideosOffline,
                        onPlayVideoOfflineNow = onPlayVideoOfflineNow,
                        onClearForcedOfflineVideo = onClearForcedOfflineVideo,
                        onIngestLocalVideo = onIngestLocalVideo,
                        onDeleteVideo = onDeleteVideo,
                        onOpenFullScreen = onClose
                    )
                    3 -> Admin24x7PermissionsAndBootPage(
                        permissionsStatus = permissionsStatus,
                        onRefreshPermissions = onRefreshPermissions
                    )
                    4 -> AdminOtaAndLogsPage(
                        otaSlotState = otaSlotState,
                        logs = diagnosticLogs,
                        onManualOtaRollback = onManualOtaRollback,
                        onClearLogs = onClearLogs
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 0: Orientation, Video Rotate (+90° / -90° / 0° / 90° / 180° / 270°) & Scale Mode
// ----------------------------------------------------------------------------

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminOrientationAndRotatePage(
    exoPlayer: ExoPlayer,
    hasPlayableVideo: Boolean,
    activePreviewTitle: String,
    resizeMode: Int,
    screenOrientation: String,
    videoRotateDegrees: Int,
    liveState: LiveState,
    onSetScreenOrientation: (String) -> Unit,
    onSetVideoRotationAngle: (Int) -> Unit,
    onRotateVideoByDelta: (Int) -> Unit,
    onSetScaleMode: (String) -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleKioskLock: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onOpenFullScreen: () -> Unit
) {
    val rotationAngles = listOf(0, 90, 180, 270)
    val scaleOptions = listOf(
        Triple("FIT", "Scale to Fit", "Shows full video without cropping"),
        Triple("FILL", "Full Screen", "Fills entire TV screen edge-to-edge"),
        Triple("ZOOM", "Zoom Crop", "Centers & zooms video")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Video Preview Monitor right at the top of Rotate Tab
        InlineLiveVideoPreviewCard(
            exoPlayer = exoPlayer,
            hasPlayableVideo = hasPlayableVideo,
            activePreviewTitle = activePreviewTitle,
            videoRotateDegrees = videoRotateDegrees,
            scaleMode = liveState.scaleMode,
            resizeMode = resizeMode,
            onOpenFullScreen = onOpenFullScreen
        )

        // 1. Video Rotate Card (+90° / -90° / 0° / 90° / 180° / 270°)
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Rotate Video (+90° / -90°)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Surface(
                        color = SignalGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Current: ${videoRotateDegrees}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Quick -90° and +90° Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onRotateVideoByDelta(-90) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("rotate_video_minus_90_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ControlDeckSurfaceVariant,
                            contentColor = ElectricCyan
                        )
                    ) {
                        Icon(imageVector = Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate -90°", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Button(
                        onClick = { onRotateVideoByDelta(90) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("rotate_video_plus_90_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate +90°", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // Direct Angle Chips (0° / 90° / 180° / 270°)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rotationAngles.forEach { angle ->
                        val isSelected = videoRotateDegrees == angle
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSetVideoRotationAngle(angle) },
                            label = { Text("${angle}°") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SignalGreen.copy(alpha = 0.22f),
                                selectedLabelColor = SignalGreen
                            )
                        )
                    }
                }
            }
        }

        // 2. Video Scale Mode Card (Scale to Fit / Full Screen / Zoom)
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. Video Scale Mode (Scale to Fit / Full Screen)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scaleOptions.forEach { (mode, title, _) ->
                        val isSelected = liveState.scaleMode == mode
                        Button(
                            onClick = { onSetScaleMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("scale_mode_btn_$mode"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) SignalGreen else ControlDeckSurfaceVariant,
                                contentColor = if (isSelected) ObsidianBackground else Color.White
                            )
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. Screen Orientation (Landscape / Portrait) & Audio / Kiosk Switches
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3. TV Screen Orientation (Landscape / Portrait — Locked Until You Change It)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("LANDSCAPE", "PORTRAIT").forEach { mode ->
                        val selected = screenOrientation.equals(mode, ignoreCase = true)
                        Button(
                            onClick = { onSetScreenOrientation(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) ElectricCyan else ControlDeckSurfaceVariant,
                                contentColor = if (selected) ObsidianBackground else Color.White
                            )
                        ) {
                            Text(mode, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                HorizontalDivider(color = BorderSlate)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mute TV Audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Mute or unmute video sound", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(checked = liveState.isMuted, onCheckedChange = onToggleMute)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("24x7 Kiosk Lock (PIN: 1234)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Auto-relaunch if closed & require PIN on ★ Star", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(checked = liveState.kioskLocked, onCheckedChange = onToggleKioskLock)
                }
            }
        }

        Button(
            onClick = onSaveSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_orientation_settings_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalGreen,
                contentColor = ObsidianBackground
            )
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save Settings ($screenOrientation • ${videoRotateDegrees}° • ${liveState.scaleMode})",
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 1: Real-Time Web Dashboard Pairing & 2.0s Sync Status
// ----------------------------------------------------------------------------

@Composable
private fun AdminPairingAndSyncPage(
    cmsServerUrl: String,
    isWebCmsConnected: Boolean,
    isAutoSyncEnabled: Boolean,
    deviceName: String,
    pairingCode: String,
    lastSyncCycleCount: Long,
    onPairNow: (String, String, String) -> Unit,
    onConnectToWebCms: () -> Unit,
    onDisconnectFromWebCms: () -> Unit,
    onRegenerateCode: () -> Unit,
    onForceSync: () -> Unit
) {
    var codeInput by remember(pairingCode) { mutableStateOf(pairingCode) }
    var nameInput by remember(deviceName) { mutableStateOf(deviceName) }
    var urlInput by remember(cmsServerUrl) { mutableStateOf(cmsServerUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (isWebCmsConnected) SignalGreen.copy(alpha = 0.7f) else BorderSlate,
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isWebCmsConnected) SignalGreen else Color.White)
                            )
                            Text(
                                text = "PAIRING CODE: ${pairingCode.chunked(3).joinToString(" ")}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isWebCmsConnected) SignalGreen else ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                isWebCmsConnected -> "● CONNECTED & LIVE — Syncing every 2.0s (#$lastSyncCycleCount)"
                                !isAutoSyncEnabled -> "○ DISCONNECTED BY OPERATOR — Offline Video Playback Active"
                                else -> "○ AUTO-CONNECTING IN BACKGROUND (#$lastSyncCycleCount)..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWebCmsConnected) SignalGreen else Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(onClick = onRegenerateCode) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Code", maxLines = 1)
                    }
                }

                // Explicit Connect / Disconnect Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isWebCmsConnected || isAutoSyncEnabled) {
                        Button(
                            onClick = onDisconnectFromWebCms,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("disconnect_web_cms_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertCrimson.copy(alpha = 0.22f),
                                contentColor = AlertCrimson
                            )
                        ) {
                            Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect CMS", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = onConnectToWebCms,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("connect_web_cms_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalGreen,
                                contentColor = ObsidianBackground
                            )
                        ) {
                            Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Live (Green)", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = onForceSync,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        OutlinedTextField(
            value = codeInput,
            onValueChange = { if (it.length <= 6) codeInput = it.filter { ch -> ch.isDigit() } },
            label = { Text("6-Digit Pairing Code (Auto-Pairs in Background)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("TV Display Name (Shown in Web Dashboard)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Web CMS Dashboard URL (Vercel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onPairNow(codeInput, nameInput, urlInput) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("pair_with_web_cms_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalGreen,
                contentColor = ObsidianBackground
            )
        ) {
            Icon(imageVector = Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pair & Connect Live (2s Sync)", fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 2: Cloud Videos, Live Video Preview & "Download Offline View"
// ----------------------------------------------------------------------------

@Composable
private fun AdminCloudAndOfflineVideosPage(
    exoPlayer: ExoPlayer,
    hasPlayableVideo: Boolean,
    activePreviewTitle: String,
    videoRotateDegrees: Int,
    scaleMode: String,
    resizeMode: Int,
    videos: List<PlaylistVideo>,
    forcedOfflineVideoId: String?,
    chunkTelemetry: ChunkTransferTelemetry,
    localManager: ChunkedStorageAndOtaManager,
    onDownloadCloudVideoOffline: (PlaylistVideo) -> Unit,
    onDownloadAllCloudVideosOffline: () -> Unit,
    onPlayVideoOfflineNow: (PlaylistVideo, Boolean) -> Unit,
    onClearForcedOfflineVideo: () -> Unit,
    onIngestLocalVideo: (Uri, String) -> Unit,
    onDeleteVideo: (PlaylistVideo) -> Unit,
    onOpenFullScreen: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onIngestLocalVideo(uri, "Offline Device Video")
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Inline Live Video Preview Player inside the Videos tab
        InlineLiveVideoPreviewCard(
            exoPlayer = exoPlayer,
            hasPlayableVideo = hasPlayableVideo,
            activePreviewTitle = activePreviewTitle,
            videoRotateDegrees = videoRotateDegrees,
            scaleMode = scaleMode,
            resizeMode = resizeMode,
            onOpenFullScreen = onOpenFullScreen
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDownloadAllCloudVideosOffline,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalGreen,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("download_all_cloud_videos_button")
            ) {
                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download All Offline", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Local Video", maxLines = 1)
            }

            if (forcedOfflineVideoId != null) {
                TextButton(onClick = onClearForcedOfflineVideo) {
                    Text("Loop All (${videos.size})", color = ElectricCyan, maxLines = 1)
                }
            }
        }

        if (chunkTelemetry.isActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricCyan, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chunkTelemetry.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${chunkTelemetry.progressPercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = SignalGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (chunkTelemetry.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = ElectricCyan,
                        trackColor = BorderSlate
                    )
                }
            }
        }

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No videos uploaded yet.\nUpload in Web Dashboard (https://tv-br.vercel.app) or tap 'Add Local Video' above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    val isRealOfflineSaved = localManager.isRealMp4PlayableOffline(video.localFileName)
                    val localBytes = localManager.getLocalFileSizeBytes(video.localFileName)
                    val effectiveBytes = if (localBytes > 0L) localBytes else video.sizeBytes
                    val isThisVideoForced = forcedOfflineVideoId == video.id

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isThisVideoForced) ElectricCyan else if (isRealOfflineSaved) SignalGreen.copy(alpha = 0.5f) else BorderSlate,
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = if (isRealOfflineSaved) {
                                                SignalGreen.copy(alpha = 0.2f)
                                            } else {
                                                ElectricCyan.copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = if (isRealOfflineSaved) {
                                                    "✔ Offline (${formatVideoBytesReadable(effectiveBytes)})"
                                                } else {
                                                    "☁ Cloud (${formatVideoBytesReadable(effectiveBytes)})"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isRealOfflineSaved) SignalGreen else ElectricCyan,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "100% Original Quality • Saved Permanently Until Deleted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onDownloadCloudVideoOffline(video) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("download_offline_button_${video.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isRealOfflineSaved) ControlDeckSurfaceVariant else ElectricCyan,
                                        contentColor = if (isRealOfflineSaved) SignalGreen else ObsidianBackground
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRealOfflineSaved) {
                                            Icons.Default.DownloadDone
                                        } else {
                                            Icons.Default.CloudDownload
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isRealOfflineSaved) "✔ Offline Saved" else "Download Offline",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = { onPlayVideoOfflineNow(video, false) },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("preview_video_button_${video.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isThisVideoForced) SignalGreen else ControlDeckSurfaceVariant,
                                        contentColor = if (isThisVideoForced) ObsidianBackground else ElectricCyan
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Preview",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = { onPlayVideoOfflineNow(video, true) },
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("play_offline_view_button_${video.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BroadcastIndigo,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Full Screen",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 3: 24x7 Kiosk Permissions, Default HOME Launcher & Auto-Boot Startup
// ----------------------------------------------------------------------------

@Composable
private fun Admin24x7PermissionsAndBootPage(
    permissionsStatus: KioskPermissionsStatus,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current

    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPermissions()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Grant all 5 permissions below for automatic TV boot startup & uninterrupted 24x7 operation:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        // 1. Default HOME Launcher Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A141D)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (permissionsStatus.isDefaultHomeLauncher) SignalGreen.copy(alpha = 0.7f) else ElectricCyan.copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Default HOME Launcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (permissionsStatus.isDefaultHomeLauncher) {
                                "ACTIVE: PulseCast is set as your Default Home Launcher!"
                            } else {
                                "Set PulseCast as Default Home Launcher so it opens automatically when TV starts!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (permissionsStatus.isDefaultHomeLauncher) SignalGreen else WatchdogAmber
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                homeRoleLauncher.launch(KioskPermissionHelper.requestDefaultHomeLauncherPopup(context))
                            } catch (_: Exception) {
                                KioskPermissionHelper.openHomeLauncherSettings(context)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("set_default_launcher_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Set Default Launcher",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { KioskPermissionHelper.openHomeLauncherSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("open_home_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Home Settings",
                            color = ElectricCyan,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Device Admin App (24x7 Lock) Card
        KioskPermissionRowCard(
            icon = Icons.Default.Security,
            title = "Device Admin App (24x7 Lock)",
            subtitle = if (permissionsStatus.isDeviceAdminEnabled) {
                "ACTIVE: Device Admin 24x7 Lock privilege is enabled"
            } else {
                "Tap to activate Device Admin privilege in Android Settings"
            },
            buttonLabel = if (permissionsStatus.isDeviceAdminEnabled) "✔ Admin Active" else "Enable Admin",
            isGranted = permissionsStatus.isDeviceAdminEnabled,
            testTag = "enable_device_admin_button",
            onClick = {
                try {
                    deviceAdminLauncher.launch(KioskPermissionHelper.buildDeviceAdminPopupIntent(context))
                } catch (_: Exception) {
                    KioskPermissionHelper.launchDeviceAdminPopup(context)
                }
            }
        )

        // 3. Accessibility Service (24x7 Guard) Card
        KioskPermissionRowCard(
            icon = Icons.Default.AccessibilityNew,
            title = "Accessibility Service (24x7 Guard)",
            subtitle = if (permissionsStatus.isAccessibilityGuardEnabled) {
                "ACTIVE: PulseCast 24x7 Guard service is monitoring playback"
            } else {
                "Tap to open Accessibility Settings & enable PulseCast"
            },
            buttonLabel = if (permissionsStatus.isAccessibilityGuardEnabled) "✔ Guard Active" else "Open Accessibility",
            isGranted = permissionsStatus.isAccessibilityGuardEnabled,
            testTag = "open_accessibility_button",
            onClick = { KioskPermissionHelper.openAccessibilitySettings(context) }
        )

        // 4. System Overlay (Display Over Apps) Card
        KioskPermissionRowCard(
            icon = Icons.Default.Layers,
            title = "System Overlay (Display Over Apps)",
            subtitle = if (permissionsStatus.isSystemOverlayGranted) {
                "ACTIVE: Display Over Other Apps permission granted for auto-start"
            } else {
                "Tap to open Overlay Permission Settings directly"
            },
            buttonLabel = if (permissionsStatus.isSystemOverlayGranted) "✔ Overlay Granted" else "Grant Overlay",
            isGranted = permissionsStatus.isSystemOverlayGranted,
            testTag = "grant_overlay_button",
            onClick = { KioskPermissionHelper.openOverlayPermissionPopup(context) }
        )

        // 5. 24x7 Unrestricted Battery Card
        KioskPermissionRowCard(
            icon = Icons.Default.BatteryChargingFull,
            title = "24x7 Unrestricted Battery",
            subtitle = if (permissionsStatus.isUnrestrictedBatteryAllowed) {
                "ACTIVE: Battery optimization disabled for 24x7 uninterrupted playback"
            } else {
                "Tap to disable battery optimization for 24x7 playback"
            },
            buttonLabel = if (permissionsStatus.isUnrestrictedBatteryAllowed) "✔ 24x7 Allowed" else "Allow 24x7",
            isGranted = permissionsStatus.isUnrestrictedBatteryAllowed,
            testTag = "allow_24x7_battery_button",
            onClick = { KioskPermissionHelper.openUnrestrictedBatteryPopup(context) }
        )
    }
}

@Composable
private fun KioskPermissionRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    isGranted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A111A)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) SignalGreen.copy(alpha = 0.5f) else BorderSlate,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) SignalGreen else WatchdogAmber,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGranted) SignalGreen else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = if (isGranted) SignalGreen.copy(alpha = 0.15f) else Color(0xFF0E1726),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (isGranted) SignalGreen else ElectricCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onClick() }
                    .testTag(testTag)
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isGranted) SignalGreen else ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// PAGE 4: OTA Software Update Rollback & Live Diagnostic Logs
// ----------------------------------------------------------------------------

@Composable
private fun AdminOtaAndLogsPage(
    otaSlotState: OtaSlotState,
    logs: List<DiagnosticLogEntry>,
    onManualOtaRollback: () -> Unit,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "OTA Firmware Status: ${otaSlotState.activeSlot} (v${otaSlotState.installedVersionName})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan
                )
                Text(
                    text = "Backup Slot: v${otaSlotState.previousSlotVersionName} • ${otaSlotState.lastOtaMessage}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onManualOtaRollback,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WatchdogAmber,
                        contentColor = ObsidianBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("admin_manual_ota_rollback_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Instant Rollback to v${otaSlotState.previousSlotVersionName}", fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live 2.0s Sync & Watchdog Diagnostic Logs",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )
            TextButton(onClick = onClearLogs) {
                Text("Clear")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(ControlDeckSurface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.timestampFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
