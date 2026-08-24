package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.mj.wxyt.domain.chat.LongTermMemoryRepository
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainMemorySettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val memory = remember { LongTermMemoryRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("MAYA Brain & Memory") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Persistent intelligence", style = MaterialTheme.typography.titleLarge)
                Text("MAYA can remember explicit facts and relevant preferences locally on this device.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                SettingSwitch("Long-term memory", "Remember user facts and preferences", settings.longTermMemoryEnabled) {
                    viewModel.updateSettings(settings.copy(longTermMemoryEnabled = it))
                }
            }
            item {
                SettingSwitch("Accessibility automation", "Allow MAYA to interact with supported app UI when you explicitly enable it", settings.allowAccessibilityAutomation) {
                    viewModel.setBooleanSetting("allowAccessibilityAutomation", it)
                }
            }
            item {
                SettingSwitch("Device awareness", "Give MAYA local battery, time, screen and device-state context", settings.useDeviceContext) {
                    viewModel.updateSettings(settings.copy(useDeviceContext = it))
                }
            }
            item {
                SettingSwitch("Action verification", "Verify important UI actions when possible", settings.actionVerification) {
                    viewModel.updateSettings(settings.copy(actionVerification = it))
                }
            }
            item {
                SettingSwitch("Safe auto-actions", "Execute low-risk deterministic actions without an extra confirmation", settings.autoExecuteSafeActions) {
                    viewModel.updateSettings(settings.copy(autoExecuteSafeActions = it))
                }
            }
            item {
                SettingSwitch("Proactive intelligence", "Allow MAYA to surface useful context without being asked", settings.proactiveIntelligence) {
                    viewModel.updateSettings(settings.copy(proactiveIntelligence = it))
                }
            }
            item {
                Text("Memory depth: ${settings.memoryDepth}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.memoryDepth.toFloat(),
                    onValueChange = { viewModel.updateSettings(settings.copy(memoryDepth = it.toInt().coerceIn(1, 30))) },
                    valueRange = 1f..30f,
                    steps = 28
                )
            }
            item {
                OutlinedButton(onClick = { scope.launch { memory.clear() } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear MAYA memory")
                }
            }
            item {
                Text("Note: Android and third-party apps still enforce their own permissions and confirmation flows. MAYA cannot bypass OS security.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Switch(checked, onCheckedChange) })
}
