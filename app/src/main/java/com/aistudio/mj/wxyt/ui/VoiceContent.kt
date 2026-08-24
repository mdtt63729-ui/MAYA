package com.aistudio.mj.wxyt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.mj.wxyt.domain.assistant.MJState
import com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState
import java.util.Calendar

@Composable
fun VoiceContent(
    mjState: MJState,
    rmsValue: Float,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    voiceReactiveState: VoiceReactiveState? = null,
    errorMessage: String? = null,
    spokenText: String = ""
) {
    val context = LocalContext.current
    val settingsFlow = remember(context) { SettingsRepository.get(context).settings }
    val settings by settingsFlow.collectAsState()
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (settings.greetingBehavior) {
            Text(
                text = getGreeting(settings.greetingBehavior, settings.timeAwareGreetings, settings.responseLanguage),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 64.dp)
            )
        }

        LiquidAIOrb(
            state = mjState,
            rmsValue = rmsValue,
            onClick = onActivate,
            voiceReactiveState = voiceReactiveState
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Live subtitle / typewriter text overlay — shown when MAYA is speaking
        SubtitleTextOverlay(
            spokenText = spokenText,
            isVisible = mjState == MJState.SPEAKING && spokenText.isNotBlank()
        )

        val statusText = when (mjState) {
            MJState.DISCONNECTED, MJState.IDLE, MJState.WAKE_WORD_LISTENING -> "● Ready"
            MJState.ACTIVATING -> "Activating..."
            MJState.CONNECTING -> "Connecting..."
            MJState.LISTENING -> "Listening..."
            MJState.THINKING -> "Thinking..."
            MJState.SPEAKING -> ""
            MJState.ERROR -> "Error encountered"
        }

        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (mjState == MJState.ERROR) {
            Text(
                text = errorMessage ?: "MAYA could not connect.",
                color = Color(0xFFFF9AA2),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to retry",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp
            )
        } else if (mjState == MJState.DISCONNECTED || mjState == MJState.IDLE || mjState == MJState.WAKE_WORD_LISTENING) {
            Text(
                text = "Tap to activate",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Typewriter / real-time subtitle text overlay below the orb.
 * Animates text character-by-character as it streams in.
 */
@Composable
private fun SubtitleTextOverlay(
    spokenText: String,
    isVisible: Boolean
) {
    // Typewriter effect: reveal characters progressively
    var displayedLength by remember { mutableIntStateOf(0) }

    LaunchedEffect(spokenText) {
        if (spokenText.isNotEmpty()) {
            // If new text is shorter or different, reset
            if (displayedLength > spokenText.length) {
                displayedLength = 0
            }
            // Reveal characters progressively
            while (displayedLength < spokenText.length) {
                kotlinx.coroutines.delay(18L)
                displayedLength++
            }
        } else {
            displayedLength = 0
        }
    }

    AnimatedVisibility(
        visible = isVisible && displayedLength > 0,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        val displayText = if (spokenText.isNotEmpty()) spokenText.take(displayedLength) else ""
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp)
        ) {
            Text(
                text = displayText,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

fun getGreeting(greetingEnabled: Boolean = true, timeAware: Boolean = true, language: String = "English"): String {
    if (!greetingEnabled) return ""
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (!timeAware) return when (language.lowercase()) {
        "bengali" -> "হ্যালো, User"
        "hindi" -> "नमस्ते, User"
        else -> "Hello, User"
    }
    return when (language.lowercase()) {
        "bengali" -> when (hour) { in 0..11 -> "শুভ সকাল, User"; in 12..16 -> "শুভ অপরাহ্ন, User"; else -> "শুভ সন্ধ্যা, User" }
        "hindi" -> when (hour) { in 0..11 -> "सुप्रभात, User"; in 12..16 -> "नमस्कार, User"; else -> "शुभ संध्या, User" }
        else -> when (hour) { in 0..11 -> "Good morning, User"; in 12..16 -> "Good afternoon, User"; else -> "Good evening, User" }
    }
}
