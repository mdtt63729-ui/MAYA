package com.aistudio.mj.wxyt.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.security.OwnerVoiceEnrollmentController
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerVoiceEnrollmentScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { OwnerVoiceEnrollmentController(context) }
    val phase by controller.phase.collectAsState()
    val sampleIndex by controller.sampleIndex.collectAsState()
    val progress by controller.progress.collectAsState()
    val message by controller.message.collectAsState()
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = false
        if (!granted) { /* controller reports the same state when tapped again */ }
    }

    val isRecording = phase == OwnerVoiceEnrollmentController.Phase.RECORDING
    val isComplete = phase == OwnerVoiceEnrollmentController.Phase.COMPLETE
    val pulse by rememberInfiniteTransition(label = "ownerVoicePulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Up Owner Voice") },
                navigationIcon = {
                    IconButton(onClick = { controller.cancel(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text("Make MAYA recognize your voice", fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "This is the owner-voice setup used by Parent Mode. Speak naturally in a quiet place.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = .65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(28.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Box(
                    Modifier.size(if (isRecording) 190.dp else 172.dp).scale(if (isRecording) pulse else 1f)
                        .background(Brush.radialGradient(listOf(Color(0xFF6E35FF), Color(0xFF1A56FF), Color.Transparent)), CircleShape)
                )
                Box(
                    Modifier.size(112.dp).background(Brush.radialGradient(listOf(Color(0xFF9DEBFF), Color(0xFF4C35FF), Color(0xFF0A0A14))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }

            Text(
                when {
                    isComplete -> "Voice profile ready"
                    isRecording -> "Listening…"
                    phase == OwnerVoiceEnrollmentController.Phase.PROCESSING -> "Analyzing your voice…"
                    else -> "Tap the microphone and speak the phrase"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(18.dp))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .07f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("SAY THIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        controller.phrases[sampleIndex],
                        fontSize = 21.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (isRecording) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 2.2f).coerceAtMost(2.2f).let { String.format("%.1f", it) }} / 2.2 sec", fontSize = 12.sp, color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            if (!isComplete) {
                Button(
                    onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionRequested = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            controller.startSample(sampleIndex)
                        }
                    },
                    enabled = !isRecording && phase != OwnerVoiceEnrollmentController.Phase.PROCESSING,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Mic, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (sampleIndex == 0) "Record phrase 1 of 3" else "Record phrase ${sampleIndex + 1} of 3")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.setOwnerVoiceEnrolled(true)
                        viewModel.setParentMode(true)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Enable Parent Mode")
                }
            }

            if (phase == OwnerVoiceEnrollmentController.Phase.ERROR) {
                Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            }
            if (isComplete) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Stored locally on this device", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
                }
            }
        }
    }
}
