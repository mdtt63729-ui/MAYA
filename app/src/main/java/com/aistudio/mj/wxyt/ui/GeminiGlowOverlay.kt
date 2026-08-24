package com.aistudio.mj.wxyt.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aistudio.mj.wxyt.domain.assistant.OrbVisualMode
import com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState
import kotlin.math.sin

/**
 * Gemini-Style Glowing Overlay — PRD 2 §4.2 & §4.3.
 *
 * Renders a dynamic 4-color gradient edge-glow using custom Canvas drawing.
 * Transitions from idle opacity (0%) to full glow (100%) via spring-based
 * interpolation upon Stage 2 speaker verification.
 *
 * Real-time audio reactivity: glow amplitude, frequency height, and color
 * shift modulate based on active speech input from VoiceReactiveState.
 */

// 4-color gradient palette — Gemini-inspired
private val GlowColors = listOf(
    Color(0xFF4285F4),  // Blue
    Color(0xFF9B72CB),  // Purple
    Color(0xFFD96570),  // Pink/Red
    Color(0xFF4EC9A8),  // Teal
)

@Composable
fun GeminiGlowOverlay(
    modifier: Modifier = Modifier,
    voiceReactiveState: VoiceReactiveState,
    isVerified: Boolean = false,
    glowIntensity: Float = 0.92f
) {
    val mode = voiceReactiveState.mode
    val energy = voiceReactiveState.energy

    // Spring-based opacity transition: idle 0% → full glow 100% on verification
    val targetAlpha = if (isVerified || mode != OrbVisualMode.IDLE) glowIntensity else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "glow_alpha"
    )

    // Infinite animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = (1500 / (1f + energy * 2f)).toInt().coerceAtLeast(500)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Audio-reactive phase shift — modulates color shift based on speech
    val phaseShift = energy * 6.28f

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cornerRadius = 40.dp.toPx()

        // Outer glow — 4-color gradient edge
        val glowAlpha = alpha * (0.6f + 0.4f * pulse) * (0.5f + 0.5f * energy)

        // Animated gradient brush — colors shift based on audio energy
        val colorStops = GlowColors.mapIndexed { i, color ->
            val offset = ((i.toFloat() / GlowColors.size) + pulse * 0.1f + energy * 0.05f) % 1f
            offset to color.copy(alpha = glowAlpha)
        }.sortedBy { it.first }

        val brush = Brush.sweepGradient(
            colors = colorStops.map { it.second },
            center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)
        )

        // Draw edge glow stroke
        drawRoundRect(
            brush = brush,
            style = Stroke(width = (3f + energy * 4f) * (1f + pulse * 0.3f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            topLeft = androidx.compose.ui.geometry.Offset(2f, 2f),
            size = androidx.compose.ui.geometry.Size(width - 4f, height - 4f)
        )

        // Bottom light-rail effect — PRD 2 §4.2
        if (isVerified || mode == OrbVisualMode.LISTENING || mode == OrbVisualMode.USER_SPEAKING) {
            val railHeight = (20f + energy * 40f) * alpha
            val railBrush = Brush.horizontalGradient(
                colors = GlowColors.map { it.copy(alpha = glowAlpha * 0.7f) }
            )
            drawRect(
                brush = railBrush,
                topLeft = androidx.compose.ui.geometry.Offset(0f, height - railHeight),
                size = androidx.compose.ui.geometry.Size(width, railHeight)
            )
        }

        // Audio-reactive frequency bars — PRD 2 §4.3
        if (mode == OrbVisualMode.USER_SPEAKING || mode == OrbVisualMode.SPEAKING) {
            val barCount = 12
            val barWidth = width / (barCount * 2)
            val barSpacing = barWidth
            val startX = (width - barCount * (barWidth + barSpacing)) / 2
            for (i in 0 until barCount) {
                val phase = i * 0.5f + phaseShift
                val barHeight = (sin(phase) * 0.5f + 0.5f) * energy * 60f * alpha
                val color = GlowColors[i % GlowColors.size].copy(alpha = glowAlpha * 0.8f)
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        startX + i * (barWidth + barSpacing),
                        height - barHeight - 4f
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
        }
    }
}
