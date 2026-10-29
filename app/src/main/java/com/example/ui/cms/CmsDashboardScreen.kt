package com.example.ui.cms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.local.OtaSlotState
import com.example.data.model.LiveState
import com.example.data.model.OtaRelease
import com.example.data.model.PlaybackZone
import com.example.data.model.PlaylistVideo
import com.example.data.model.SignageDevice
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
import com.example.ui.viewmodel.AppOperatingMode
import com.example.ui.viewmodel.ChunkTransferTelemetry
import com.example.ui.viewmodel.CmsTab
import com.example.ui.viewmodel.SignageViewModel

@Composable
fun CmsDashboardScreen(
    viewModel: SignageViewModel,
    localManager: ChunkedStorageAndOtaManager,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    devices: List<SignageDevice>,
    zones: List<PlaybackZone>,
    otaReleases: List<OtaRelease>,
    selectedTab: CmsTab,
    chunkTelemetry: ChunkTransferTelemetry,
    otaSlotState: OtaSlotState,
    otaProgressPercent: Int?,
    localPairingCode: String,
    onSignOut: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CmsTopHeaderBar(
                liveState = liveState,
                connectedDeviceCount = devices.count { it.isOnline },
                otaSlotState = otaSlotState,
                onLaunchTvPlayerMode = {
                    viewModel.switchOperatingMode(AppOperatingMode.TV_DISPLAY_CLIENT)
                },
                onSignOut = onSignOut
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ControlDeckSurface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == CmsTab.LIVE_CONTROL,
                    onClick = { viewModel.selectCmsTab(CmsTab.LIVE_CONTROL) },
                    icon = { Icon(Icons.Default.PlayCircleFilled, contentDescription = "Playlist & Live Control") },
                    label = { Text("Live & Ads") },
                    modifier = Modifier.testTag("nav_tab_live_control")
                )
                NavigationBarItem(
                    selected = selectedTab == CmsTab.DEVICES_PAIRING,
                    onClick = { viewModel.selectCmsTab(CmsTab.DEVICES_PAIRING) },
                    icon = { Icon(Icons.Default.Devices, contentDescription = "Devices & Pairing") },
                    label = { Text("Screens (${devices.size})") },
                    modifier = Modifier.testTag("nav_tab_devices")
                )
                NavigationBarItem(
                    selected = selectedTab == CmsTab.ZONES_PERMISSIONS,
                    onClick = { viewModel.selectCmsTab(CmsTab.ZONES_PERMISSIONS) },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Zone Permissions") },
                    label = { Text("Zones & RBAC") },
                    modifier = Modifier.testTag("nav_tab_zones")
                )
                NavigationBarItem(
                    selected = selectedTab == CmsTab.OTA_UPDATES,
                    onClick = { viewModel.selectCmsTab(CmsTab.OTA_UPDATES) },
                    icon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = "OTA Updates") },
                    label = { Text("OTA & Rollback") },
                    modifier = Modifier.testTag("nav_tab_ota")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Active 600KB Chunk Upload / Cache Progress Banner
            if (chunkTelemetry.isActive) {
                Surface(
                    color = ControlDeckSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                                color = ElectricCyan
                            )
                            Text(
                                text = "Chunk ${chunkTelemetry.currentChunk}/${chunkTelemetry.totalChunks} (600KB/chunk)",
                                style = MaterialTheme.typography.labelMedium,
                                color = SignalGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (chunkTelemetry.totalChunks > 0) {
                                    chunkTelemetry.currentChunk.toFloat() / chunkTelemetry.totalChunks.toFloat()
                                } else 0.5f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricCyan,
                            trackColor = BorderSlate
                        )
                    }
                }
            }

            when (selectedTab) {
                CmsTab.LIVE_CONTROL -> CmsLiveControlAndPlaylistTab(
                    viewModel = viewModel,
                    localManager = localManager,
                    liveState = liveState,
                    videos = videos,
                    zones = zones
                )
                CmsTab.DEVICES_PAIRING -> CmsDevicesAndPairingTab(
                    viewModel = viewModel,
                    devices = devices,
                    zones = zones,
                    localPairingCode = localPairingCode
                )
                CmsTab.ZONES_PERMISSIONS -> CmsZonesAndPermissionsTab(
                    viewModel = viewModel,
                    zones = zones,
                    liveState = liveState
                )
                CmsTab.OTA_UPDATES -> CmsOtaUpdatesAndRollbackTab(
                    viewModel = viewModel,
                    otaReleases = otaReleases,
                    devices = devices,
                    zones = zones,
                    otaSlotState = otaSlotState,
                    otaProgressPercent = otaProgressPercent
                )
            }
        }
    }
}

@Composable
private fun CmsTopHeaderBar(
    liveState: LiveState,
    connectedDeviceCount: Int,
    otaSlotState: OtaSlotState,
    onLaunchTvPlayerMode: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = ControlDeckSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderSlate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                }
                Column {
                    Text(
                        text = "PulseCast CMS Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SignalGreen)
                        )
                        Text(
                            text = "cms_global/live_state • $connectedDeviceCount Online • v${otaSlotState.installedVersionName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLaunchTvPlayerMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBackground
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("launch_tv_display_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("TV Display Mode", fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign Out",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CmsLiveControlAndPlaylistTab(
    viewModel: SignageViewModel,
    localManager: ChunkedStorageAndOtaManager,
    liveState: LiveState,
    videos: List<PlaylistVideo>,
    zones: List<PlaybackZone>
) {
    var creativeTitle by remember { mutableStateOf("") }
    var streamUrlInput by remember { mutableStateOf("") }
    var selectedZoneId by remember(liveState.activeZoneId) { mutableStateOf(liveState.activeZoneId) }

    // Zero-permission Android Photo Picker for selecting local video files
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadLocalVideoUri(
                uri = uri,
                title = creativeTitle.ifBlank { "Device Video Creative" },
                zoneId = selectedZoneId
            )
            creativeTitle = ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Preview Monitor & Multi-Device Push Control Deck
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LIVE PREVIEW MONITOR",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Real-time mirror of cms_global/live_state",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { viewModel.pushAndPlayToAllScreens() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalGreen,
                                contentColor = ObsidianBackground
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("push_and_play_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push & Play All", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulated 16:9 Live Preview Monitor Frame showing Orientation, Scale, and Active Video
                    val previewRotation = when (liveState.orientation) {
                        "PORTRAIT", "ROTATE_90" -> 90f
                        "ROTATE_180" -> 180f
                        "ROTATE_270" -> 270f
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianBackground)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer { rotationZ = previewRotation }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (videos.isEmpty()) TextMuted else SignalGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (videos.isEmpty()) {
                                    "STANDBY — NO ACTIVE VIDEOS IN PLAYLIST"
                                } else {
                                    "LOOPING ${videos.size} CREATIVE(S): ${videos.first().title}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (videos.isEmpty()) TextSecondary else SignalGreen
                            )
                            Text(
                                text = "${liveState.orientation} • Scale: ${liveState.scaleMode} • Audio: ${if (liveState.isMuted) "MUTED" else "LIVE"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Instant Screen Orientation & Rotation Controls
                    Text(
                        text = "Instant Screen Orientation & Rotation",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("LANDSCAPE", "PORTRAIT", "ROTATE_90", "ROTATE_180", "ROTATE_270").forEach { orient ->
                            FilterChip(
                                selected = liveState.orientation == orient,
                                onClick = { viewModel.setOrientation(orient) },
                                label = { Text(orient) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scale Mode & Audio Mute Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FILL", "FIT", "ZOOM").forEach { scale ->
                                FilterChip(
                                    selected = liveState.scaleMode == scale,
                                    onClick = { viewModel.setScaleMode(scale) },
                                    label = { Text("Scale: $scale") }
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleAudioMute(!liveState.isMuted) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ControlDeckSurfaceVariant)
                                .testTag("cms_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (liveState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Toggle Remote Audio Mute",
                                tint = if (liveState.isMuted) WatchdogAmber else SignalGreen
                            )
                        }
                    }
                }
            }
        }

        // 2. High-Speed 600KB Chunked Video Uploader Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Text(
                            text = "HIGH-SPEED CHUNKED VIDEO UPLOADER (600KB CHUNKS)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                    Text(
                        text = "Ingests video files in 600KB chunks to local persistent storage (/files/videos) and mirrors metadata to Firestore cms_global/live_state.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = creativeTitle,
                        onValueChange = { creativeTitle = it },
                        label = { Text("Ad Creative Title (e.g., Holiday 4K Promo)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_video_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        label = { Text("Stream MP4 URL (Leave blank for verified broadcast stream)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_stream_url_input")
                    )

                    if (zones.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Target Playback Zone:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            zones.forEach { zone ->
                                FilterChip(
                                    selected = selectedZoneId == zone.id,
                                    onClick = {
                                        selectedZoneId = zone.id
                                        viewModel.selectActiveZone(zone.id)
                                    },
                                    label = { Text(zone.zoneName) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_local_video_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Device Video")
                        }

                        Button(
                            onClick = {
                                viewModel.uploadStreamUrlVideo(
                                    title = creativeTitle.ifBlank { "Broadcast Spot #${videos.size + 1}" },
                                    streamUrl = streamUrlInput.ifBlank { SignageViewModel.VERIFIED_BACKUP_STREAM_URL },
                                    backupUrl = SignageViewModel.SECONDARY_BACKUP_STREAM_URL,
                                    zoneId = selectedZoneId
                                )
                                creativeTitle = ""
                                streamUrlInput = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("chunk_upload_sync_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("600KB Chunk Upload", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Live Playlist Header & Items (Starts Empty with Zero Default Demo Videos)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE PLAYLIST (${videos.size} CREATIVES)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "✔ = Verified Offline Storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = SignalGreen
                )
            }
        }

        if (videos.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ControlDeckSurface.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Playlist is Empty (No Default Demo Videos)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Use the 600KB Chunked Video Uploader above to ingest a local video file or cloud stream.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                val isCached = video.isVerifiedCached || localManager.isFileVerifiedCached(video.localFileName)
                Card(
                    colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                        color = SignalGreen.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "✔ Offline Ready",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SignalGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Zone: ${video.zoneId} • ${video.chunkCount} x 600KB chunks • File: ${video.localFileName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.deleteVideoFromPlaylist(video) },
                            modifier = Modifier.testTag("delete_video_${video.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove Video",
                                tint = AlertCrimson
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CmsDevicesAndPairingTab(
    viewModel: SignageViewModel,
    devices: List<SignageDevice>,
    zones: List<PlaybackZone>,
    localPairingCode: String
) {
    var pairingCodeInput by remember { mutableStateOf(localPairingCode) }
    var deviceNameInput by remember { mutableStateOf("Lobby 4K Android TV") }
    var targetZoneId by remember { mutableStateOf(zones.firstOrNull()?.id ?: "zone_lobby") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Text(
                            text = "6-DIGIT DEVICE PAIRING & FLEET LINK",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                    Text(
                        text = "This device's live pairing code is $localPairingCode. Enter any 6-digit screen code below to pair it to a playback zone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { if (it.length <= 6) pairingCodeInput = it.filter { c -> c.isDigit() } },
                            label = { Text("6-Digit Code") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pairing_code_input")
                        )
                        OutlinedTextField(
                            value = deviceNameInput,
                            onValueChange = { deviceNameInput = it },
                            label = { Text("Screen Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("pairing_device_name_input")
                        )
                    }

                    if (zones.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            zones.forEach { zone ->
                                FilterChip(
                                    selected = targetZoneId == zone.id,
                                    onClick = { targetZoneId = zone.id },
                                    label = { Text(zone.zoneName) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.pairDisplayBySixDigitCode(
                                enteredCode = pairingCodeInput,
                                deviceName = deviceNameInput,
                                zoneId = targetZoneId
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pair_device_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pair Screen & Start 2.5s Sync Loop", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "CONNECTED DISPLAY FLEET (${devices.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(devices, key = { it.id }) { device ->
            val nowSec = System.currentTimeMillis() / 1000L
            val isFreshHeartbeat = (nowSec - device.lastHeartbeatEpochSec) < 25L && device.isOnline
            val dotColor = if (isFreshHeartbeat) SignalGreen else AlertCrimson

            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Column {
                                Text(
                                    text = device.deviceName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Code: ${device.pairingCode} • Zone: ${device.zoneId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            color = dotColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (isFreshHeartbeat) "CONNECTED" else "OFFLINE",
                                style = MaterialTheme.typography.labelMedium,
                                color = dotColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = BorderSlate)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Playing: ${device.currentVideoTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "✔ Cached: ${device.cachedVideoCount} videos",
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OTA Firmware: v${device.installedVersionName} (${device.otaStatus}) • Backup: v${device.previousSlotVersionName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CmsZonesAndPermissionsTab(
    viewModel: SignageViewModel,
    zones: List<PlaybackZone>,
    liveState: LiveState
) {
    var newZoneName by remember { mutableStateOf("") }
    var newLocationTag by remember { mutableStateOf("") }
    var newOperatorName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("ZONE_OPERATOR") }
    var canPush by remember { mutableStateOf(true) }
    var canRotate by remember { mutableStateOf(true) }
    var canOta by remember { mutableStateOf(false) }

    val roles = listOf("SUPER_ADMIN", "ZONE_OPERATOR", "CONTENT_UPLOADER", "VIEWER")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "CREATE PLAYBACK ZONE & OPERATOR PERMISSIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Assign granular role-based access controls (RBAC) for specific remote playback zones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text("Playback Zone Name (e.g., Airport Gate B12)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zone_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newLocationTag,
                            onValueChange = { newLocationTag = it },
                            label = { Text("Location Tag") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newOperatorName,
                            onValueChange = { newOperatorName = it },
                            label = { Text("Assigned Operator") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roles.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = {
                                    selectedRole = role
                                    when (role) {
                                        "SUPER_ADMIN" -> {
                                            canPush = true; canRotate = true; canOta = true
                                        }
                                        "ZONE_OPERATOR" -> {
                                            canPush = true; canRotate = true; canOta = false
                                        }
                                        "CONTENT_UPLOADER" -> {
                                            canPush = true; canRotate = false; canOta = false
                                        }
                                        "VIEWER" -> {
                                            canPush = false; canRotate = false; canOta = false
                                        }
                                    }
                                },
                                label = { Text(role) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canPush, onCheckedChange = { canPush = it })
                            Text("Push Playlist", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canRotate, onCheckedChange = { canRotate = it })
                            Text("Rotate Screen", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = canOta, onCheckedChange = { canOta = it })
                            Text("OTA Admin", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.createNewPlaybackZone(
                                zoneName = newZoneName.ifBlank { "Zone #${zones.size + 1}" },
                                locationTag = newLocationTag.ifBlank { "Concourse Level" },
                                operatorName = newOperatorName.ifBlank { "Regional Manager" },
                                operatorRole = selectedRole,
                                canPushPlaylist = canPush,
                                canRotateScreen = canRotate,
                                canManageOta = canOta,
                                orientationOverride = "INHERIT"
                            )
                            newZoneName = ""
                            newLocationTag = ""
                            newOperatorName = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_zone_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Playback Zone", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(zones, key = { it.id }) { zone ->
            val isActiveZone = liveState.activeZoneId == zone.id
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isActiveZone) ElectricCyan else BorderSlate,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = zone.zoneName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${zone.locationTag} • Operator: ${zone.operatorName} (${zone.operatorRole})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        FilterChip(
                            selected = isActiveZone,
                            onClick = { viewModel.selectActiveZone(zone.id) },
                            label = { Text(if (isActiveZone) "Active Zone" else "Select Zone") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilterChip(
                            selected = zone.canPushPlaylist,
                            onClick = {
                                viewModel.updateZoneRoleAndPermissions(
                                    zone = zone,
                                    operatorRole = zone.operatorRole,
                                    canPushPlaylist = !zone.canPushPlaylist,
                                    canRotateScreen = zone.canRotateScreen,
                                    canManageOta = zone.canManageOta,
                                    orientationOverride = zone.orientationOverride
                                )
                            },
                            label = { Text("Push Ads") }
                        )
                        FilterChip(
                            selected = zone.canRotateScreen,
                            onClick = {
                                viewModel.updateZoneRoleAndPermissions(
                                    zone = zone,
                                    operatorRole = zone.operatorRole,
                                    canPushPlaylist = zone.canPushPlaylist,
                                    canRotateScreen = !zone.canRotateScreen,
                                    canManageOta = zone.canManageOta,
                                    orientationOverride = zone.orientationOverride
                                )
                            },
                            label = { Text("Rotate") }
                        )
                        FilterChip(
                            selected = zone.canManageOta,
                            onClick = {
                                viewModel.updateZoneRoleAndPermissions(
                                    zone = zone,
                                    operatorRole = zone.operatorRole,
                                    canPushPlaylist = zone.canPushPlaylist,
                                    canRotateScreen = zone.canRotateScreen,
                                    canManageOta = !zone.canManageOta,
                                    orientationOverride = zone.orientationOverride
                                )
                            },
                            label = { Text("Manage OTA") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CmsOtaUpdatesAndRollbackTab(
    viewModel: SignageViewModel,
    otaReleases: List<OtaRelease>,
    devices: List<SignageDevice>,
    zones: List<PlaybackZone>,
    otaSlotState: OtaSlotState,
    otaProgressPercent: Int?
) {
    val nextSuggestedCode = (maxOf(
        otaSlotState.installedVersionCode,
        otaReleases.maxOfOrNull { it.versionCode } ?: 1L
    ) + 1L)

    var versionNameInput by remember(nextSuggestedCode) { mutableStateOf("1.$nextSuggestedCode.0") }
    var releaseNotesInput by remember {
        mutableStateOf("Enhanced Media3 gapless buffer + SHA-256 A/B boot watchdog")
    }
    var simulateCorruptUpdate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Current Client A/B Slot & Rollback Telemetry Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (otaSlotState.otaStatus == "ROLLED_BACK") AlertCrimson else ElectricCyan,
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdateAlt,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Text(
                                text = "A/B SLOT OTA ENGINE & WATCHDOG",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                        Surface(
                            color = when (otaSlotState.otaStatus) {
                                "ROLLED_BACK" -> AlertCrimson.copy(alpha = 0.2f)
                                "INSTALLED", "UP_TO_DATE" -> SignalGreen.copy(alpha = 0.2f)
                                else -> WatchdogAmber.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = otaSlotState.otaStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (otaSlotState.otaStatus) {
                                    "ROLLED_BACK" -> AlertCrimson
                                    "INSTALLED", "UP_TO_DATE" -> SignalGreen
                                    else -> WatchdogAmber
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Active Slot: ${otaSlotState.activeSlot} • Running v${otaSlotState.installedVersionName} (Build ${otaSlotState.installedVersionCode})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Rollback Backup Slot: v${otaSlotState.previousSlotVersionName} (Build ${otaSlotState.previousSlotVersionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = otaSlotState.lastOtaMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    if (otaProgressPercent != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (otaProgressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricCyan,
                            trackColor = BorderSlate
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.triggerManualOtaRollback("CMS Administrator triggered remote rollback")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("cms_manual_rollback_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            tint = WatchdogAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Force Rollback to Previous Slot (v${otaSlotState.previousSlotVersionName})",
                            color = WatchdogAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Publish New OTA Client Version Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "UPLOAD & PUBLISH NEW OTA CLIENT VERSION",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Clients automatically poll, download in 600KB chunks, verify SHA-256 integrity, and stage into the inactive A/B slot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = versionNameInput,
                        onValueChange = { versionNameInput = it },
                        label = { Text("Semantic Version Name (Build #$nextSuggestedCode)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ota_version_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = releaseNotesInput,
                        onValueChange = { releaseNotesInput = it },
                        label = { Text("Release Notes & Changelog") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ota_release_notes_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toggle to test automatic rollback on corrupt/failed update
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ControlDeckSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = WatchdogAmber
                            )
                            Column {
                                Text(
                                    text = "Simulate Corrupt OTA Package (Test Auto-Rollback)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Injects a SHA-256 mismatch so the client automatically rolls back to the known-good slot",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = simulateCorruptUpdate,
                            onCheckedChange = { simulateCorruptUpdate = it },
                            modifier = Modifier.testTag("ota_simulate_corrupt_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.publishNewOtaRelease(
                                versionCode = nextSuggestedCode,
                                versionName = versionNameInput.ifBlank { "1.$nextSuggestedCode.0" },
                                releaseNotes = releaseNotesInput,
                                targetZoneId = "ALL",
                                simulateCorruptPackage = simulateCorruptUpdate
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("publish_ota_release_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simulateCorruptUpdate) WatchdogAmber else ElectricCyan,
                            contentColor = ObsidianBackground
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (simulateCorruptUpdate) {
                                "Push Faulty OTA v$versionNameInput (Test Auto-Rollback)"
                            } else {
                                "Upload & Push OTA v$versionNameInput (Build #$nextSuggestedCode)"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Published OTA Release History
        item {
            Text(
                text = "OTA RELEASE MANIFEST HISTORY (${otaReleases.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(otaReleases, key = { it.id }) { release ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ControlDeckSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "v${release.versionName} (Build #${release.versionCode})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Surface(
                            color = if (release.releaseStatus == "ROLLED_BACK") {
                                AlertCrimson.copy(alpha = 0.2f)
                            } else {
                                SignalGreen.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = release.releaseStatus,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (release.releaseStatus == "ROLLED_BACK") AlertCrimson else SignalGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SHA-256: ${release.sha256Checksum.take(20)}... • ${release.chunkCount} x 600KB chunks",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
