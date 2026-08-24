package com.aistudio.mj.wxyt.ui

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager
import com.aistudio.mj.wxyt.ui.chat.ChatContent
import com.aistudio.mj.wxyt.ui.chat.ChatViewModel
import com.aistudio.mj.wxyt.ui.settings.SettingsNavigation
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    navController: NavController,
    chatViewModel: ChatViewModel,
    initialMode: AssistantMode,
    autoActivateRequestId: Long = 0L
) {
    var currentMode by remember { mutableStateOf(initialMode) }
    
    val context = LocalContext.current
    val voiceManager = remember { MJVoiceManager.getInstance(context) }
    val mjState by voiceManager.state.collectAsState()
    val rmsValue by voiceManager.rmsValue.collectAsState()
    val voiceReactiveState by voiceManager.voiceReactiveState.collectAsState()
    val voiceErrorMessage by voiceManager.errorMessage.collectAsState()
    val spokenText by voiceManager.currentSpokenText.collectAsState()
    
    val focusManager = LocalFocusManager.current
    
    var showSettings by remember { mutableStateOf(false) }
    
    // Only microphone permission is required to activate a voice session.
    // Notification permission must never block the core assistant interaction.
    val microphonePermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var permissionRequestIssuedFor by remember { mutableLongStateOf(-1L) }

    // A tap on the floating JARVIS orb launches MAYA and, once microphone
    // permission is available, immediately starts the assistant session.
    // The request id makes repeated orb taps work even when MainActivity is
    // already in the foreground and receives the intent through onNewIntent().
    LaunchedEffect(autoActivateRequestId, microphonePermissionState.status.isGranted, currentMode) {
        if (autoActivateRequestId <= 0L || currentMode != AssistantMode.VOICE) return@LaunchedEffect

        if (!microphonePermissionState.status.isGranted) {
            if (permissionRequestIssuedFor != autoActivateRequestId) {
                permissionRequestIssuedFor = autoActivateRequestId
                microphonePermissionState.launchPermissionRequest()
            }
            return@LaunchedEffect
        }

        if (mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.DISCONNECTED ||
            mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.IDLE ||
            mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.WAKE_WORD_LISTENING ||
            mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.ERROR) {
            voiceManager.startSession()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF4A00E0).copy(alpha = 0.3f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("history") },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF4A00E0).copy(alpha = 0.3f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp)) // Balance the settings button
                
                // Segmented Control
                AnimatedSegmentedControl(
                    currentMode = currentMode,
                    onModeSelected = { 
                        currentMode = it 
                        if (it == AssistantMode.VOICE) {
                            focusManager.clearFocus()
                        }
                    }
                )
                
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
            
            // Content Transition
            AnimatedContent(
                targetState = currentMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(280))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "modeTransition"
            ) { mode ->
                if (mode == AssistantMode.VOICE) {
                    VoiceContent(
                        mjState = mjState,
                        rmsValue = rmsValue,
                        onActivate = {
                            if (microphonePermissionState.status.isGranted) {
                                if (mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.DISCONNECTED || mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.IDLE || mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.WAKE_WORD_LISTENING || mjState == com.aistudio.mj.wxyt.domain.assistant.MJState.ERROR) {
                                    voiceManager.startSession()
                                } else {
                                    voiceManager.stopSession()
                                }
                            } else {
                                microphonePermissionState.launchPermissionRequest()
                            }
                        },
                        voiceReactiveState = voiceReactiveState,
                        errorMessage = voiceErrorMessage,
                        spokenText = spokenText
                    )
                } else {
                    ChatContent(
                        viewModel = chatViewModel,
                        focusManager = focusManager
                    )
                }
            }
        }
        
        if (showSettings) {
            Dialog(
                onDismissRequest = { showSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                SettingsNavigation(onClose = { showSettings = false })
            }
        }
        }

        // Native Max-style full-screen edge lighting. It is deliberately drawn
        // above the UI and below system touch handling so the whole edge can
        // glow, including the edge-to-edge system-bar regions.
        if (currentMode == AssistantMode.VOICE && !showSettings) {
            MayaEdgeLighting(
                assistantState = mjState,
                reactiveState = voiceReactiveState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
