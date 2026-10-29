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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.ChunkedStorageAndOtaManager
import com.example.data.model.LiveState
import com.example.data.repository.SignageRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutFromGoogle
import com.example.ui.cms.CmsDashboardScreen
import com.example.ui.player.TvDisplayClientScreen
import com.example.ui.theme.ControlDeckSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SignalGreen
import com.example.ui.viewmodel.AppOperatingMode
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.SignageViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigationRoot()
            }
        }
    }
}

@Composable
fun AppNavigationRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser */ })
    } else {
        AuthenticatedSignageApp(currentUserId = user.uid)
    }
}

@Composable
fun AuthenticatedSignageApp(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val localManager = remember(context) { ChunkedStorageAndOtaManager(context.applicationContext) }

    val viewModel: SignageViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SignageViewModel(
                    repository = SignageRepository(db),
                    localManager = localManager,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val operatingMode by viewModel.operatingMode.collectAsStateWithLifecycle()
    val selectedCmsTab by viewModel.selectedCmsTab.collectAsStateWithLifecycle()
    val liveStateUi by viewModel.liveStateUi.collectAsStateWithLifecycle()
    val videosUi by viewModel.videosUi.collectAsStateWithLifecycle()
    val devicesUi by viewModel.devicesUi.collectAsStateWithLifecycle()
    val zonesUi by viewModel.zonesUi.collectAsStateWithLifecycle()
    val otaReleasesUi by viewModel.otaReleasesUi.collectAsStateWithLifecycle()

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
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()

    val liveState = (liveStateUi as? DataUiState.Success)?.data ?: LiveState(userId = currentUserId)
    val videos = (videosUi as? DataUiState.Success)?.data ?: emptyList()
    val devices = (devicesUi as? DataUiState.Success)?.data ?: emptyList()
    val zones = (zonesUi as? DataUiState.Success)?.data ?: emptyList()
    val otaReleases = (otaReleasesUi as? DataUiState.Success)?.data ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        when (operatingMode) {
            AppOperatingMode.CMS_DASHBOARD -> {
                CmsDashboardScreen(
                    viewModel = viewModel,
                    localManager = localManager,
                    liveState = liveState,
                    videos = videos,
                    devices = devices,
                    zones = zones,
                    otaReleases = otaReleases,
                    selectedTab = selectedCmsTab,
                    chunkTelemetry = chunkTelemetry,
                    otaSlotState = otaSlotState,
                    otaProgressPercent = otaProgressPercent,
                    localPairingCode = localPairingCode,
                    onSignOut = {
                        signOutFromGoogle(
                            credentialManager = credentialManager,
                            onSignOutComplete = {},
                            scope = scope
                        )
                    }
                )
            }

            AppOperatingMode.TV_DISPLAY_CLIENT -> {
                TvDisplayClientScreen(
                    viewModel = viewModel,
                    localManager = localManager,
                    liveState = liveState,
                    videos = videos,
                    pairingCode = localPairingCode,
                    deviceId = localDeviceId,
                    otaSlotState = otaSlotState,
                    otaProgressPercent = otaProgressPercent,
                    diagnosticLogs = diagnosticLogs,
                    isUsingBackupStream = isUsingBackupStream,
                    lastSyncCycleCount = lastSyncCycleCount,
                    isPinDialogVisible = isPinDialogVisible,
                    isAdminPanelOpen = isAdminPanelOpen,
                    pinError = pinError
                )
            }
        }

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
