package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.remote.VercelCmsApiClient
import com.example.ui.player.TvDisplayClientScreen
import com.example.ui.theme.ControlDeckSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SignalGreen
import com.example.ui.viewmodel.SignageViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PulseCastTvClientApp()
            }
        }
    }
}

@Composable
fun PulseCastTvClientApp() {
    val context = LocalContext.current
    val localManager = remember(context) { ChunkedStorageAndOtaManager(context.applicationContext) }

    val viewModel: SignageViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SignageViewModel(
                    localManager = localManager,
                    vercelClient = VercelCmsApiClient()
                )
            }
        }
    )

    val cmsServerUrl by viewModel.cmsServerUrl.collectAsStateWithLifecycle()
    val isWebCmsConnected by viewModel.isWebCmsConnected.collectAsStateWithLifecycle()
    val isPairedWithCms by viewModel.isPairedWithCms.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val assignedVideoId by viewModel.assignedVideoId.collectAsStateWithLifecycle()
    val screenOrientation by viewModel.screenOrientation.collectAsStateWithLifecycle()
    val videoRotateDegrees by viewModel.videoRotateDegrees.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val allCloudVideos by viewModel.allCloudVideos.collectAsStateWithLifecycle()
    val forcedOfflineVideoId by viewModel.forcedOfflineVideoId.collectAsStateWithLifecycle()
    val localDeviceId by viewModel.localDeviceId.collectAsStateWithLifecycle()
    val localPairingCode by viewModel.localPairingCode.collectAsStateWithLifecycle()
    val otaSlotState by viewModel.otaSlotState.collectAsStateWithLifecycle()
    val otaProgressPercent by viewModel.otaProgressPercent.collectAsStateWithLifecycle()
    val chunkTelemetry by viewModel.chunkTelemetry.collectAsStateWithLifecycle()
    val syncToastMessage by viewModel.syncToastMessage.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
    val isUsingBackupStream by viewModel.isUsingBackupStream.collectAsStateWithLifecycle()
    val lastSyncCycleCount by viewModel.lastSyncCycleCount.collectAsStateWithLifecycle()
    val isPinDialogVisible by viewModel.isPinDialogVisible.collectAsStateWithLifecycle()
    val isAdminPanelOpen by viewModel.isAdminPanelOpen.collectAsStateWithLifecycle()
    val selectedSidebarTab by viewModel.selectedSidebarTab.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        TvDisplayClientScreen(
            viewModel = viewModel,
            localManager = localManager,
            cmsServerUrl = cmsServerUrl,
            isWebCmsConnected = isWebCmsConnected,
            isPairedWithCms = isPairedWithCms,
            deviceName = deviceName,
            assignedVideoId = assignedVideoId,
            screenOrientation = screenOrientation,
            videoRotateDegrees = videoRotateDegrees,
            liveState = liveState,
            videos = videos,
            allCloudVideos = allCloudVideos,
            forcedOfflineVideoId = forcedOfflineVideoId,
            pairingCode = localPairingCode,
            deviceId = localDeviceId,
            otaSlotState = otaSlotState,
            otaProgressPercent = otaProgressPercent,
            chunkTelemetry = chunkTelemetry,
            diagnosticLogs = diagnosticLogs,
            isUsingBackupStream = isUsingBackupStream,
            lastSyncCycleCount = lastSyncCycleCount,
            isPinDialogVisible = isPinDialogVisible,
            isAdminPanelOpen = isAdminPanelOpen,
            selectedSidebarTab = selectedSidebarTab,
            pinError = pinError
        )

        // Instant Sync Toast Overlay
        AnimatedVisibility(
            visible = !syncToastMessage.isNullOrBlank(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 14.dp)
        ) {
            Surface(
                color = ControlDeckSurfaceVariant,
                shape = RoundedCornerShape(50),
                shadowElevation = 10.dp,
                modifier = Modifier.border(1.dp, ElectricCyan, RoundedCornerShape(50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncToastMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
