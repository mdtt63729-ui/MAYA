package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val activeVoice by viewModel.activeVoice.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val openCodeApiKey by viewModel.openCodeApiKey.collectAsState()
    val nvidiaApiKey by viewModel.nvidiaApiKey.collectAsState()
    val customApiKey by viewModel.customProviderApiKey.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Customize how MAYA looks, sounds and behaves.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                )
                
                // Profile Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("MAYA", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Personal AI Assistant", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active / Ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                SettingsCategoryCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "MAYA Command Center",
                    subtitle = "JARVIS-style intelligence, owner voice, automation & performance",
                    onClick = { onNavigate("command_center") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voice & Speech",
                    subtitle = "$activeVoice • ${if (voiceEnabled) "Enabled" else "Disabled"}",
                    onClick = { onNavigate("voice_speech") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "AI & Intelligence",
                    subtitle = "Chat providers, models, routing & generation",
                    onClick = { onNavigate("ai_intelligence") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.Psychology,
                    title = "MAYA Brain & Memory",
                    subtitle = "Long-term memory, personalization & automation",
                    onClick = { onNavigate("brain_memory") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.Key,
                    title = "API & Secrets",
                    subtitle = if (geminiApiKey.isEmpty() && openRouterApiKey.isEmpty() && openCodeApiKey.isEmpty() && nvidiaApiKey.isEmpty() && customApiKey.isEmpty()) "Not Configured" else "Voice + chat providers configured",
                    onClick = { onNavigate("api_secrets") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.Mic,
                    title = "Wake Word",
                    subtitle = "Hey MAYA",
                    onClick = { onNavigate("wake_word") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.Apps,
                    title = "Background Assistant",
                    subtitle = "Manage background services",
                    onClick = { onNavigate("background_assistant") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.ColorLens,
                    title = "Appearance",
                    subtitle = "Theme, Blur, and Motion",
                    onClick = { onNavigate("appearance") }
                )
            }
            item {
                SettingsCategoryCard(
                    icon = Icons.Default.Security,
                    title = "Privacy & Security",
                    subtitle = "History and Permissions",
                    onClick = { onNavigate("privacy_security") }
                )
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Go",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
