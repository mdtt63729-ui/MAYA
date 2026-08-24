package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

private val chatProviders = listOf(
    "auto" to "MAYA Auto Router (Non-Gemini)",
    "openrouter" to "OpenRouter",
    "opencode" to "OpenCode",
    "nvidia" to "NVIDIA NIM",
    "custom" to "Custom OpenAI-Compatible"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI & Intelligence") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "MAYA now has separate AI pipelines: text chat uses a non-Gemini provider, while realtime voice stays on Google Gemini.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    fontSize = 15.sp
                )
            }

            item {
                Text("CHAT AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        chatProviders.forEachIndexed { index, (providerId, providerName) ->
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val defaultModel = if (providerId == "openrouter" || providerId == "auto") "openrouter/auto" else ""
                                    viewModel.updateSettings(settings.copy(chatProvider = providerId, chatModel = defaultModel))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text(providerName, modifier = Modifier.weight(1f), fontWeight = if (settings.chatProvider == providerId) FontWeight.SemiBold else FontWeight.Normal)
                                if (settings.chatProvider == providerId) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (index < chatProviders.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = settings.chatModel,
                    onValueChange = { viewModel.updateSettings(settings.copy(chatModel = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chat model ID") },
                    placeholder = { Text(if (settings.chatProvider == "openrouter" || settings.chatProvider == "auto") "openrouter/auto" else "Enter provider model ID") },
                    supportingText = {
                        Text("Auto Router selects a non-Gemini provider by task type. Gemini is never used for text chat. Other providers can use a model ID from their /models endpoint.")
                    },
                    singleLine = true
                )
            }

            item {
                Text("VOICE AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.padding(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    androidx.compose.material3.ListItem(
                        leadingContent = {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("Google Gemini Live") },
                        supportingContent = { Text("Locked to Gemini for realtime voice, VAD, interruption and audio streaming.") }
                    )
                }
            }

            item {
                Text("GENERATION PREFERENCES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Response Creativity: ${String.format("%.1f", settings.temperature)}")
                        androidx.compose.material3.Slider(
                            value = settings.temperature,
                            onValueChange = { viewModel.updateSettings(settings.copy(temperature = it)) },
                            valueRange = 0f..1f,
                            steps = 9
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Context Length: ${settings.contextLength} tokens")
                        androidx.compose.material3.Slider(
                            value = settings.contextLength.toFloat(),
                            onValueChange = { viewModel.updateSettings(settings.copy(contextLength = it.toInt().coerceIn(256, 32768))) },
                            valueRange = 256f..32768f
                        )
                    }
                }
            }
        }
    }
}
