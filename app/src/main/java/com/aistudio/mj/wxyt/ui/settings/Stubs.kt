package com.aistudio.mj.wxyt.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService
import com.aistudio.mj.wxyt.domain.security.CascadeWakeWordEngine
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var pendingEnable by remember { mutableStateOf(false) }
    var showRetrainDialog by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingEnable) viewModel.setWakeWordEnabled(true)
        pendingEnable = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Wake Word") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                SettingsIntro("Voice activation", "Configure when MAYA listens for a wake phrase. Wake-word listening never runs at the same time as a Gemini Live microphone session.")
            }
            item {
                SettingCard {
                    SettingSwitch(
                        "Enable Wake Word",
                        "Listen for the configured phrase while the wake-word mode is active.",
                        settings.wakeWordEnabled
                    ) { enabled ->
                        if (enabled) {
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.setWakeWordEnabled(true)
                            else { pendingEnable = true; micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        } else viewModel.setWakeWordEnabled(false)
                    }
                    if (settings.wakeWordEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        OutlinedTextField(
                            value = settings.wakeWord,
                            onValueChange = viewModel::setWakeWord,
                            singleLine = true,
                            label = { Text("Wake phrase") },
                            supportingText = { Text("Example: Hey MAYA") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // PRD 2 §3.1: Sensitivity Slider for high-noise environments
                        SliderRow("Wake sensitivity", settings.wakeWordSensitivity, { viewModel.setFloatSetting("wakeWordSensitivity", it) })
                        // PRD 2 §3.1: Offline wake-word mode
                        SettingSwitch("Offline wake-word mode", "Requires an actual offline wake-word model. It is not enabled unless a model is installed.", settings.wakeWordOffline) { viewModel.setBooleanSetting("wakeWordOffline", it) }
                        // PRD 2 §2.1: Cascade detection info
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Text("Two-stage cascade detection", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Stage 1: Low-power wake-word detection scans audio buffers.\n" +
                            "Stage 2: Speaker verification (voiceprint) confirms owner identity.\n" +
                            "Only the registered owner's voice activates the assistant.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // PRD 2 §3.1: Voice Retraining Button
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        OutlinedButton(
                            onClick = { showRetrainDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retrain Voice Model")
                        }
                    }
                }
            }
            // PRD 2 §5: Battery & persistence
            item {
                SettingCard {
                    Text("Battery & persistence", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Battery drain target: < 3% per 24 hours in continuous listening mode.\n" +
                        "Service bound with START_STICKY for OS persistence.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    SystemButton("Battery optimization settings") {
                        try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }
            }
            item {
                SettingCard {
                    Text("Current behavior", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (settings.wakeWordEnabled) "MAYA is configured to listen for: ${settings.wakeWord.ifBlank { "Hey MAYA" }}\nOwner voice enrolled: ${if (settings.ownerVoiceEnrolled) "Yes" else "No"}"
                        else "Wake-word listening is OFF. Tapping the ORB can still start an explicit voice session.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (showRetrainDialog) {
        AlertDialog(
            onDismissRequest = { showRetrainDialog = false },
            title = { Text("Retrain Voice Model?") },
            text = { Text("This will delete your current voice profile and require you to re-enroll your voice from scratch.") },
            confirmButton = {
                TextButton(onClick = {
                    showRetrainDialog = false
                    viewModel.setBooleanSetting("ownerVoiceEnrolled", false)
                    viewModel.setBooleanSetting("ownerVoiceEnabled", false)
                }) { Text("Retrain", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showRetrainDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundAssistantSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var pendingEnable by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingEnable) viewModel.setBackgroundEnabled(true)
        pendingEnable = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Background Assistant") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            item { SettingsIntro("Background operation", "Background microphone capture is controlled by Android foreground-service rules. MAYA will never silently request microphone access.") }
            item {
                SettingCard {
                    SettingSwitch("Background processing", "Allow MAYA's background controller to operate when enabled.", settings.backgroundProcessing) { viewModel.setBooleanSetting("backgroundProcessing", it) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SettingSwitch("Run MAYA in background", "Keeps the assistant controller available after leaving the app.", settings.backgroundEnabled) { enabled ->
                        if (!enabled) viewModel.setBackgroundEnabled(false)
                        else {
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.setBackgroundEnabled(true)
                            else { pendingEnable = true; micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Text("Permissions & system access", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SystemButton("Microphone permission") { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    SystemButton("Battery optimization settings") {
                        try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                    // PRD 1 §2.1: Request battery optimization bypass
                    SystemButton("Request battery bypass") {
                        try {
                            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) { }
                    }
                    SystemButton("Floating ORB permission") {
                        try { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = android.net.Uri.parse("package:${context.packageName}") }) } catch (_: Exception) { }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Appearance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            item { SettingsIntro("Liquid-glass appearance", "These controls update the shared MAYA appearance state. The ORB renderer reads the same settings repository.") }
            item { ChoiceCard("Theme", settings.theme, listOf("System", "Light", "Dark")) { viewModel.setTheme(it) } }
            item { ChoiceCard("Blur", settings.blur, listOf("High", "Medium", "Off")) { viewModel.setBlur(it) } }
            item { ChoiceCard("Motion profile", settings.motion, listOf("60 FPS", "90 FPS", "120 FPS", "Battery Saver")) { viewModel.setMotion(it) } }
            item {
                SettingCard {
                    SettingSwitch("Idle breathing", "Subtle motion while MAYA is idle.", settings.idleBreathing) { viewModel.setBooleanSetting("idleBreathing", it) }
                    SettingSwitch("Emotion-reactive ORB", "Allow assistant state to influence the ORB material.", settings.emotionOrb) { viewModel.setBooleanSetting("emotionOrb", it) }
                    SettingSwitch("Music-reactive ORB", "React to local playback when a supported audio source is available.", settings.musicReactiveOrb) { viewModel.setBooleanSetting("musicReactiveOrb", it) }
                    SettingSwitch("Haptic feedback", "Use a short haptic response for supported assistant interactions.", settings.hapticFeedback) { viewModel.setBooleanSetting("hapticFeedback", it) }
                    SettingSwitch("Battery saver animation", "Reduce animation workload when the device is in a constrained state.", settings.batterySaverAnimation) { viewModel.setBooleanSetting("batterySaverAnimation", it) }
                }
            }
            item { SliderRow("ORB reactivity", settings.orbReactivity, { viewModel.setFloatSetting("orbReactivity", it) }) }
            item { SliderRow("Voice visualization", settings.voiceVisualization, { viewModel.setFloatSetting("voiceVisualization", it) }) }
            item { SliderRow("Glow intensity", (settings.glowIntensity / 1.5f).coerceIn(0f,1f), { viewModel.setFloatSetting("glowIntensity", it * 1.5f) }) }
            item { SliderRow("Particle density", settings.particleDensity, { viewModel.setFloatSetting("particleDensity", it) }) }
            item { SliderRow("ORB size", ((settings.orbSize - .72f) / .56f).coerceIn(0f,1f), { viewModel.setFloatSetting("orbSize", .72f + it * .56f) }) }
            item { SliderRow("Animation speed", ((settings.animationSpeed - .25f) / 1.75f).coerceIn(0f,1f), { viewModel.setFloatSetting("animationSpeed", .25f + it * 1.75f) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecuritySettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var notificationAccess by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) }
    val accessibilityEnabled = ORBAccessibilityService.instance != null
    val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(topBar = { TopAppBar(title = { Text("Privacy & Security") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            item { SettingsIntro("Privacy and permissions", "Settings are real controls where Android exposes a supported API. When a system permission is required, MAYA opens the corresponding Android screen instead of pretending it is enabled.") }
            item {
                SettingCard {
                    Text("Runtime status", fontWeight = FontWeight.SemiBold)
                    StatusLine("Microphone", micGranted)
                    StatusLine("Notification access", notificationAccess)
                    StatusLine("Accessibility", accessibilityEnabled)
                    StatusLine("Encrypted local memory", settings.encryptedMemory)
                }
            }
            item {
                SettingCard {
                    SettingSwitch("Save conversation history", "Persist conversations locally.", settings.saveHistory) { viewModel.setBooleanSetting("saveHistory", it) }
                    SettingSwitch("Remember conversations", "Allow conversation context to be reused in future sessions.", settings.rememberConversations) { viewModel.setBooleanSetting("rememberConversations", it) }
                    SettingSwitch("Long-term memory", "Allow MAYA's memory subsystem to store approved memories.", settings.longTermMemoryEnabled) { viewModel.setBooleanSetting("longTermMemoryEnabled", it) }
                    SettingSwitch("Memory auto-learn", "Learn useful preferences according to Memory Approval policy.", settings.memoryAutoLearn) { viewModel.setBooleanSetting("memoryAutoLearn", it) }
                    SettingSwitch("Memory approval", "Require explicit memory intent before saving inferred personal information.", settings.memoryApproval) { viewModel.setBooleanSetting("memoryApproval", it) }
                    SettingSwitch("Private Mode", "Disable persistent memory/history behavior for private interactions.", settings.privateMode) { viewModel.setBooleanSetting("privateMode", it) }
                    SettingSwitch("Encrypted memory", "Keep local memory in the encrypted storage layer.", settings.encryptedMemory) { viewModel.setBooleanSetting("encryptedMemory", it) }
                    SettingSwitch("Local-only memory", "Do not route memory storage to a cloud memory backend.", settings.localOnlyMemory) { viewModel.setBooleanSetting("localOnlyMemory", it) }
                }
            }
            item {
                SettingCard {
                    Text("Android permissions", fontWeight = FontWeight.SemiBold)
                    SystemButton("Microphone permission") { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${context.packageName}") }) } catch (_: Exception) { } }
                    SystemButton("Notification access") {
                        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { }
                    }
                    SystemButton("Accessibility") {
                        try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
                    }
                }
            }
            item {
                SettingCard {
                    SettingSwitch("Require confirmation", "Ask before executing device actions.", settings.requireConfirmation) { viewModel.setBooleanSetting("requireConfirmation", it) }
                    SettingSwitch("Risk-based confirmation", "Escalate confirmation for higher-risk actions.", settings.riskBasedConfirmation) { viewModel.setBooleanSetting("riskBasedConfirmation", it) }
                    SettingSwitch("Biometric action confirmation", "Use Android biometric authentication when the action engine requests it.", settings.biometricActionConfirmation) { viewModel.setBooleanSetting("biometricActionConfirmation", it) }
                    SettingSwitch("Strict notification privacy", "Apply the strictest notification redaction policy.", settings.notificationPrivacyStrict) { viewModel.setBooleanSetting("notificationPrivacyStrict", it) }
                    SettingSwitch("AI data sharing", "No telemetry backend is bundled; this remains off unless an explicit sharing backend exists.", settings.aiDataSharing) { viewModel.setBooleanSetting("aiDataSharing", it) }
                }
            }
            item {
                Button(onClick = { showClearDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Clear All MAYA Data") }
                Text("Deletes local conversations, memories, routines, audit logs, saved API keys and settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            if (showClearDialog) {
                item {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Delete all MAYA data?") },
                        text = { Text("This cannot be undone. Saved provider keys will also be removed.") },
                        confirmButton = { TextButton(onClick = { showClearDialog = false; viewModel.clearAllUserData() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

@Composable private fun SettingsIntro(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable private fun ChoiceCard(title: String, current: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SettingCard {
        Text(title, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) })
                }
            }
        }
    }
}

@Composable private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(String.format("%.2f", value.coerceIn(0f, 1f)), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable private fun SystemButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.weight(1f)) }
}

@Composable private fun StatusLine(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Security, contentDescription = null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(if (ok) "READY" else "NOT READY", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
