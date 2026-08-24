package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel
import com.aistudio.mj.wxyt.domain.settings.IndianLanguages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSpeechSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val activeVoice by viewModel.activeVoice.collectAsState()
    val speakingSpeed by viewModel.speakingSpeed.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val responseLanguage by viewModel.responseLanguage.collectAsState()
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice & Speech") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Control how MAYA listens, responds and speaks.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                )
            }

            item {
                Text("VOICE STUDIO", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Current Voice", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(activeVoice, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.previewVoice(activeVoice) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview")
                        }
                    }
                }
            }

            item {
                Text(
                    "AI LANGUAGE",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MAYA's Language", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${IndianLanguages.findByName(responseLanguage).nativeName} • ${IndianLanguages.findByName(responseLanguage).name}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { languageMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${IndianLanguages.findByName(responseLanguage).nativeName}  •  ${IndianLanguages.findByName(responseLanguage).name}",
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ExpandMore, contentDescription = "Choose language")
                            }

                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.92f)
                            ) {
                                IndianLanguages.all.forEach { language ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(language.nativeName, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    language.name,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (language.name == responseLanguage) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setResponseLanguage(language.name)
                                            languageMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Saved instantly. MAYA will use this language for future voice replies.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("SPEECH PREFERENCES", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voice Feedback", fontSize = 16.sp)
                                Text(
                                    "Controls spoken replies only; it never starts or stops the microphone.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = voiceEnabled, onCheckedChange = { viewModel.setVoiceEnabled(it) })
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Speaking Speed: ${String.format("%.1f", speakingSpeed)}x", fontSize = 16.sp)
                        Slider(
                            value = speakingSpeed,
                            onValueChange = { viewModel.setSpeakingSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 14
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
