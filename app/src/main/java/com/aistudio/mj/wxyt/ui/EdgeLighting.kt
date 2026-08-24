package com.aistudio.mj.wxyt.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.aistudio.mj.wxyt.domain.assistant.EdgeLightingState
import com.aistudio.mj.wxyt.domain.assistant.MJState
import com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

// PRD §4 — Color system: Electric Blue, Deep Violet, Cyan, Soft Magenta
// Made more vibrant/saturated for a premium live feel
private val ColorElectricBlue = Color(0xFF2979FF)
private val ColorDeepViolet = Color(0xFF7C4DFF)
private val ColorCyan = Color(0xFF00E5FF)
private val ColorSoftMagenta = Color(0xFFFF4081)

/**
 * Premium 3D-style edge lighting — fully native GPU-rendered.
 *
 * Upgraded: thicker strokes, more vibrant colors, smooth flowing gradient,
 * strong voice reactivity with fast-attack / slow-release envelope.
 */
@Composable
fun MayaEdgeLighting(
    assistantState: MJState,
    reactiveState: VoiceReactiveState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by remember(context) {
        SettingsRepository.get(context).settings
    }.collectAsState()

    if (!settings.edgeLightingEnabled) return

    val edgeState = when (assistantState) {
        MJState.ACTIVATING, MJState.CONNECTING -> EdgeLightingState.ACTIVATING
        MJState.LISTENING -> EdgeLightingState.LISTENING
        MJState.THINKING -> EdgeLightingState.THINKING
        MJState.SPEAKING -> EdgeLightingState.SPEAKING
        else -> EdgeLightingState.IDLE
    }

    val isVisible = edgeState != EdgeLightingState.IDLE || settings.edgeLightingIdle
    if (!isVisible) return

    // Continuous infinite transition for flow animation
    val infiniteTransition = rememberInfiniteTransition(label = "edgeLighting")
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (5000f / settings.edgeLightingSpeed.coerceIn(0.35f, 2f))
                    .toInt().coerceIn(2500, 12000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )

    // Breathing pulse
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2500f / settings.edgeLightingSpeed.coerceIn(0.35f, 2f))
                    .toInt().coerceIn(1500, 5000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathPhase"
    )

    // Fast wave for voice-reactive ripple — travels faster when speaking
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (edgeState == EdgeLightingState.SPEAKING) 1200 else 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Energy from reactive state — this drives the "alive" feel
    val energy = if (settings.edgeLightingReactive) reactiveState.energy.coerceIn(0f, 1f) else 0.15f
    val isVoiceActive = reactiveState.isVoiceActive

    // State-specific intensity boost — much more dynamic now
    val stateBoost = when (edgeState) {
        EdgeLightingState.ACTIVATING -> 1.3f
        EdgeLightingState.LISTENING -> 1.0f + energy * 0.5f
        EdgeLightingState.THINKING -> 0.7f + breathPhase * 0.3f
        EdgeLightingState.SPEAKING -> 1.2f + energy * 0.4f
        EdgeLightingState.IDLE -> 0.0f
    }

    val baseIntensity = settings.edgeLightingIntensity.coerceIn(0.3f, 1.5f)
    val intensity = (baseIntensity * stateBoost).coerceIn(0f, 2.0f)

    val breath = 0.8f + 0.2f * sin(breathPhase * PI.toFloat())

    Canvas(modifier = modifier.fillMaxSize()) {
        drawPremiumEdgeGlow(
            width = size.width,
            height = size.height,
            edgeState = edgeState,
            intensity = intensity,
            energy = energy,
            flowPhase = flowPhase,
            breathPhase = breathPhase,
            wavePhase = wavePhase,
            breath = breath,
            isVoiceActive = isVoiceActive,
            glowIntensity = settings.glowIntensity
        )
    }
}

/**
 * Draws the premium multi-layer edge glow with seamless corners.
 * Upgraded with thicker strokes, more layers, and stronger colors.
 */
private fun DrawScope.drawPremiumEdgeGlow(
    width: Float,
    height: Float,
    edgeState: EdgeLightingState,
    intensity: Float,
    energy: Float,
    flowPhase: Float,
    breathPhase: Float,
    wavePhase: Float,
    breath: Float,
    isVoiceActive: Boolean,
    glowIntensity: Float
) {
    if (intensity <= 0.01f) return

    val inset = max(1f, min(width, height) * 0.004f)
    val radius = max(28f, min(width, height) * 0.055f)

    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset

    // Build the continuous rounded rectangle path — seamless corners
    val edgePath = Path().apply {
        moveTo(left, top + radius)
        arcTo(
            rect = Rect(left, top, left + 2 * radius, top + 2 * radius),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(right - radius, top)
        arcTo(
            rect = Rect(right - 2 * radius, top, right, top + 2 * radius),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(right, bottom - radius)
        arcTo(
            rect = Rect(right - 2 * radius, bottom - 2 * radius, right, bottom),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(left + radius, bottom)
        arcTo(
            rect = Rect(left, bottom - 2 * radius, left + 2 * radius, bottom),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(left, top + radius)
        close()
    }

    // Flowing gradient colors — 16 stops for ultra-smooth color transitions
    val phaseOffset = flowPhase * 4f
    val colors = mutableListOf<Color>()
    for (i in 0..16) {
        val t = i / 16f
        val shifted = (t + phaseOffset * 0.12f) % 1f
        colors.add(flowingGradientColor(shifted))
    }

    // Energy-reactive intensity
    val reactiveBoost = when (edgeState) {
        EdgeLightingState.LISTENING -> 0.8f + energy * 0.6f
        EdgeLightingState.SPEAKING -> 0.9f + energy * 0.5f
        EdgeLightingState.THINKING -> breath
        EdgeLightingState.ACTIVATING -> 1.0f
        EdgeLightingState.IDLE -> 0f
    }

    val finalIntensity = (intensity * reactiveBoost).coerceIn(0f, 2.0f)

    // --- Layer 1: Ultra-wide soft bloom (outermost, atmospheric glow) ---
    drawPath(
        path = edgePath,
        color = ColorCyan,
        style = Stroke(width = 40f),
        alpha = (0.02f * finalIntensity * glowIntensity).coerceIn(0f, 0.15f)
    )
    drawPath(
        path = edgePath,
        color = ColorDeepViolet,
        style = Stroke(width = 32f),
        alpha = (0.035f * finalIntensity * glowIntensity).coerceIn(0f, 0.2f)
    )

    // --- Layer 2: Medium bloom — wider, more colorful ---
    drawPath(
        path = edgePath,
        color = ColorElectricBlue,
        style = Stroke(width = 22f),
        alpha = (0.05f * finalIntensity).coerceIn(0f, 0.25f)
    )
    drawPath(
        path = edgePath,
        color = ColorSoftMagenta,
        style = Stroke(width = 16f),
        alpha = (0.08f * finalIntensity).coerceIn(0f, 0.3f)
    )

    // --- Layer 3: Core glow with flowing gradient — thicker ---
    val gradientBrush = Brush.sweepGradient(
        colors = colors,
        center = Offset(width / 2f, height / 2f)
    )
    drawPath(
        path = edgePath,
        brush = gradientBrush,
        style = Stroke(width = 8f, cap = StrokeCap.Round),
        alpha = (0.18f * finalIntensity).coerceIn(0f, 0.6f)
    )

    // --- Layer 4: Bright inner rim — the "neon" core ---
    drawPath(
        path = edgePath,
        brush = gradientBrush,
        style = Stroke(width = 4f, cap = StrokeCap.Round),
        alpha = (0.45f * finalIntensity).coerceIn(0f, 0.9f)
    )

    // --- Layer 5: Sharp luminous core line ---
    drawPath(
        path = edgePath,
        brush = gradientBrush,
        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
        alpha = (0.7f * finalIntensity).coerceIn(0f, 1.0f)
    )

    // --- Voice-reactive wave ripples — travel around the edge when speaking/listening ---
    if (edgeState == EdgeLightingState.LISTENING || edgeState == EdgeLightingState.SPEAKING) {
        drawVoiceWaveRipples(
            left = left, top = top, right = right, bottom = bottom, radius = radius,
            wavePhase = wavePhase,
            energy = energy,
            intensity = finalIntensity,
            edgeState = edgeState
        )
    }

    // --- Traveling light comets for dynamic feel ---
    if (edgeState == EdgeLightingState.LISTENING || edgeState == EdgeLightingState.SPEAKING) {
        drawTravelingComets(
            edgePath = edgePath,
            left = left, top = top, right = right, bottom = bottom, radius = radius,
            flowPhase = flowPhase,
            energy = energy,
            intensity = finalIntensity,
            edgeState = edgeState
        )
    }

    // --- Corner accent glows — brighter, more visible ---
    drawCornerAccents(
        left, top, right, bottom, radius,
        flowPhase, finalIntensity, glowIntensity
    )
}

/**
 * Returns a color from the 4-color flowing gradient at position t (0..1).
 */
private fun flowingGradientColor(t: Float): Color {
    val phase = t * 4f
    val i = phase.toInt() % 4
    val f = phase - phase.toInt()
    val (c1, c2) = when (i) {
        0 -> ColorElectricBlue to ColorDeepViolet
        1 -> ColorDeepViolet to ColorCyan
        2 -> ColorCyan to ColorSoftMagenta
        else -> ColorSoftMagenta to ColorElectricBlue
    }
    return lerpColor(c1, c2, f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

/**
 * Voice-reactive wave ripples — energy pulses that travel around the edge.
 * These make the lighting "move" with the user's voice.
 */
private fun DrawScope.drawVoiceWaveRipples(
    left: Float, top: Float, right: Float, bottom: Float, radius: Float,
    wavePhase: Float,
    energy: Float,
    intensity: Float,
    edgeState: EdgeLightingState
) {
    val rippleCount = if (edgeState == EdgeLightingState.SPEAKING) 4 else 3
    for (i in 0 until rippleCount) {
        val ripplePhase = (wavePhase + i.toFloat() / rippleCount) % 1f
        val point = perimeterPointAt(ripplePhase, left, top, right, bottom, radius)
        val rippleColor = flowingGradientColor(ripplePhase)

        // Ripple expands outward from the point — simulates voice energy radiating
        val baseRadius = 6f + energy * 20f
        val expandPhase = (wavePhase * 2f + i * 0.3f) % 1f
        val expandRadius = baseRadius * (0.5f + expandPhase * 1.5f)
        val rippleAlpha = (0.2f * energy * intensity * (1f - expandPhase)).coerceIn(0f, 0.4f)

        // Outer ripple
        drawCircle(
            color = rippleColor,
            radius = expandRadius,
            center = point,
            alpha = rippleAlpha * 0.3f
        )
        // Mid ripple
        drawCircle(
            color = rippleColor,
            radius = expandRadius * 0.6f,
            center = point,
            alpha = rippleAlpha * 0.6f
        )
        // Bright core
        drawCircle(
            color = rippleColor,
            radius = 2f + energy * 4f,
            center = point,
            alpha = rippleAlpha.coerceIn(0f, 0.9f)
        )
    }
}

/**
 * Draws traveling light comets along the perimeter.
 */
private fun DrawScope.drawTravelingComets(
    edgePath: Path,
    left: Float, top: Float, right: Float, bottom: Float, radius: Float,
    flowPhase: Float,
    energy: Float,
    intensity: Float,
    edgeState: EdgeLightingState
) {
    val cometCount = if (edgeState == EdgeLightingState.SPEAKING) 4 else 2
    for (i in 0 until cometCount) {
        val cometPhase = (flowPhase + i.toFloat() / cometCount) % 1f
        val point = perimeterPointAt(cometPhase, left, top, right, bottom, radius)
        val cometColor = flowingGradientColor(cometPhase)
        val cometAlpha = (0.2f + energy * 0.4f) * intensity

        // Comet tail
        drawCircle(
            color = cometColor,
            radius = (10f + energy * 18f) * glowIntensityScale(intensity),
            center = point,
            alpha = (cometAlpha * 0.1f).coerceIn(0f, 0.2f)
        )
        // Comet mid
        drawCircle(
            color = cometColor,
            radius = (5f + energy * 10f) * glowIntensityScale(intensity),
            center = point,
            alpha = (cometAlpha * 0.25f).coerceIn(0f, 0.35f)
        )
        // Comet core — bright
        drawCircle(
            color = cometColor,
            radius = (2f + energy * 4f),
            center = point,
            alpha = cometAlpha.coerceIn(0f, 0.9f)
        )
    }
}

private fun glowIntensityScale(intensity: Float): Float = (0.5f + intensity * 0.5f).coerceIn(0.3f, 1.5f)

/**
 * Corner accent glows — brighter and more visible now.
 */
private fun DrawScope.drawCornerAccents(
    left: Float, top: Float, right: Float, bottom: Float, radius: Float,
    flowPhase: Float, intensity: Float, glowIntensity: Float
) {
    val corners = listOf(
        Offset(left + radius, top + radius),
        Offset(right - radius, top + radius),
        Offset(right - radius, bottom - radius),
        Offset(left + radius, bottom - radius)
    )
    corners.forEachIndexed { i, point ->
        val cornerPhase = (flowPhase * 4f + i.toFloat()) % 4f
        val glow = (0.5f + 0.5f * sin(cornerPhase * PI.toFloat() / 2f)) * intensity
        if (glow > 0.02f) {
            val color = flowingGradientColor((flowPhase + i * 0.25f) % 1f)
            drawCircle(
                color = color,
                radius = radius * 0.7f,
                center = point,
                alpha = (0.04f * glow * glowIntensity).coerceIn(0f, 0.15f)
            )
        }
    }
}

/**
 * Maps a phase value (0..1) to a point on the rounded rectangle perimeter.
 */
private fun perimeterPointAt(
    phase: Float,
    left: Float, top: Float, right: Float, bottom: Float, radius: Float
): Offset {
    val straightH = right - left - 2f * radius
    val straightV = bottom - top - 2f * radius
    val arcLen = (PI.toFloat() * radius * 0.5f)
    val perimeter = 2f * straightH + 2f * straightV + 4f * arcLen
    var d = (phase * perimeter) % perimeter

    if (d <= straightH) return Offset(left + radius + d, top)
    d -= straightH
    if (d <= arcLen) {
        val a = -PI.toFloat() / 2f + d / radius
        return Offset(right - radius + cos(a) * radius, top + radius + sin(a) * radius)
    }
    d -= arcLen
    if (d <= straightV) return Offset(right, top + radius + d)
    d -= straightV
    if (d <= arcLen) {
        val a = d / radius
        return Offset(right - radius + cos(a) * radius, bottom - radius + sin(a) * radius)
    }
    d -= arcLen
    if (d <= straightH) return Offset(right - radius - d, bottom)
    d -= straightH
    if (d <= arcLen) {
        val a = PI.toFloat() / 2f + d / radius
        return Offset(left + radius + cos(a) * radius, bottom - radius + sin(a) * radius)
    }
    d -= arcLen
    if (d <= straightV) return Offset(left, bottom - radius - d)
    d -= straightV
    val a = PI.toFloat() + d / radius
    return Offset(left + radius + cos(a) * radius, top + radius + sin(a) * radius)
}
