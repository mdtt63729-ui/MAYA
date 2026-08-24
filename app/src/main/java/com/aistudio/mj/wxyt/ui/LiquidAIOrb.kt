package com.aistudio.mj.wxyt.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aistudio.mj.wxyt.domain.assistant.MJState
import com.aistudio.mj.wxyt.domain.assistant.OrbVisualMode
import com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * MAYA Dynamic Liquid Glass 3D Orb — PRD v2.0 Redesign.
 *
 * High-gloss liquid glass surface with iridescent side reflections
 * (Cyan, Magenta, Neon Blue, Purple gradient). Organic fluid swirl
 * motion inside and outside the sphere. Soft backlit glow on dark
 * backgrounds.
 *
 * Visual states:
 *  - IDLE: slow, smooth liquid swirl with deep purple/cyan gradient
 *  - LISTENING: real-time lighting response to voice amplitude
 *  - THINKING: faster liquid rotation and glow frequency
 *  - SPEAKING: dynamic light pulsation with live text overlay
 *  - ERROR: restrained red-violet tint
 *
 * All previous orb logic (audio reactivity, state handlers, voice
 * triggers, touch/click interactions) is preserved and integrated.
 */
@Composable
fun LiquidAIOrb(
    state: MJState,
    rmsValue: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    voiceReactiveState: VoiceReactiveState? = null
) {
    val appContext = LocalContext.current
    val settingsRepo = remember { SettingsRepository.get(appContext) }
    val settings by settingsRepo.settings.collectAsState()
    val energy = (voiceReactiveState?.energy ?: (rmsValue / 10f)).coerceIn(0f, 1f)

    val mode = voiceReactiveState?.mode ?: when (state) {
        MJState.LISTENING, MJState.WAKE_WORD_LISTENING -> OrbVisualMode.LISTENING
        MJState.SPEAKING -> OrbVisualMode.SPEAKING
        MJState.THINKING, MJState.ACTIVATING, MJState.CONNECTING -> OrbVisualMode.THINKING
        MJState.ERROR -> OrbVisualMode.ERROR
        else -> OrbVisualMode.IDLE
    }

    val active = mode == OrbVisualMode.SPEAKING || mode == OrbVisualMode.USER_SPEAKING
    val processing = mode == OrbVisualMode.THINKING || mode == OrbVisualMode.EXECUTING
    val listening = mode == OrbVisualMode.LISTENING

    val reactiveEnergy = if (settings.musicReactiveOrb) {
        (energy * settings.orbReactivity.coerceIn(0f, 1f) * settings.voiceVisualization.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val targetEnergy = if (active) {
        0.22f + reactiveEnergy * 0.78f
    } else if (listening) {
        0.14f + reactiveEnergy * 0.56f
    } else if (processing) {
        0.32f * settings.orbReactivity.coerceIn(0.5f, 1f)
    } else {
        0.08f + reactiveEnergy * 0.18f
    }

    val smoothedEnergy by animateFloatAsState(
        targetValue = targetEnergy,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = if (active) Spring.StiffnessHigh else Spring.StiffnessLow
        ),
        label = "orbEnergy"
    )

    // One frame clock drives every layer so the liquid surface remains phase-locked.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mode) {
        var previous = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (previous != 0L) {
                val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                val motionMultiplier = when (settings.motion) {
                    "120 FPS" -> 1.0f
                    "90 FPS" -> 0.88f
                    "Battery Saver" -> 0.48f
                    else -> 0.72f
                } * settings.animationSpeed.coerceIn(0.25f, 2f) *
                    if (settings.batterySaverAnimation && settings.performanceMode == "Battery Saver") 0.55f else 1f
                val speed = when {
                    active -> (0.90f + smoothedEnergy * 1.20f) * motionMultiplier
                    listening -> (0.55f + smoothedEnergy * 0.65f) * motionMultiplier
                    processing -> 0.52f * motionMultiplier
                    else -> (if (settings.idleBreathing) 0.16f else 0.05f) * motionMultiplier
                }
                time = (time + dt * speed) % 10000f
            }
            previous = now
        }
    }

    val breathing = sin(time * 0.78f) * 0.55f + sin(time * 1.17f + 1.4f) * 0.25f
    val pulse = (breathing * 0.5f + 0.5f).coerceIn(0f, 1f)
    val scaleTarget = when {
        active -> 1f + smoothedEnergy * 0.085f + pulse * 0.008f
        listening -> 1f + smoothedEnergy * 0.045f + pulse * 0.004f
        processing -> 1.008f + pulse * 0.008f
        else -> 0.992f + pulse * 0.010f
    }
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "orbScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "orbPressScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(270.dp)
            .semantics { contentDescription = "MAYA AI assistant orb. Tap to activate or stop the assistant." }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (settings.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val sphereRadius = size.minDimension * 0.325f * scale * settings.orbSize.coerceIn(0.72f, 1.28f)

            drawIridescentGlow(center, sphereRadius, smoothedEnergy, active, listening, settings.glowIntensity * when (settings.blur) { "Off" -> 0.55f; "Medium" -> 0.8f; else -> 1f })
            drawLiquidGlassSphere(
                center = center,
                radius = sphereRadius,
                time = time,
                energy = smoothedEnergy,
                active = active,
                listening = listening,
                processing = processing,
                error = mode == OrbVisualMode.ERROR,
                emotionEnabled = settings.emotionOrb,
                particleDensity = settings.particleDensity
            )
        }
    }
}

/**
 * Multi-layer iridescent backlit glow — Cyan, Magenta, Blue, Purple radiating outward.
 */
private fun DrawScope.drawIridescentGlow(
    center: Offset,
    radius: Float,
    energy: Float,
    active: Boolean,
    listening: Boolean,
    glowIntensity: Float
) {
    val glow = glowIntensity.coerceIn(0f, 1.5f)
    val baseAlpha = (if (active) 0.30f + energy * 0.30f else if (listening) 0.18f + energy * 0.16f else 0.12f + energy * 0.08f) * glow
    val glowRadius = radius * if (active) 2.25f else if (listening) 1.95f else 1.72f

    // Outer purple-violet glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF7C4DFF).copy(alpha = baseAlpha),
                Color(0xFF243DFF).copy(alpha = baseAlpha * 0.42f),
                Color.Transparent
            ),
            center = center,
            radius = glowRadius
        ),
        radius = glowRadius,
        center = center
    )

    // Iridescent cyan side bloom (upper-left)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = if (active) 0.22f else if (listening) 0.14f else 0.09f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.38f, center.y - radius * 0.48f),
            radius = radius * 1.2f
        ),
        radius = radius * 1.2f,
        center = Offset(center.x - radius * 0.38f, center.y - radius * 0.48f)
    )

    // Magenta side bloom (lower-right) — adds the iridescent feel
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF4081).copy(alpha = if (active) 0.16f else 0.08f),
                Color.Transparent
            ),
            center = Offset(center.x + radius * 0.42f, center.y + radius * 0.40f),
            radius = radius * 1.0f
        ),
        radius = radius * 1.0f,
        center = Offset(center.x + radius * 0.42f, center.y + radius * 0.40f)
    )
}

/**
 * Draws the full 3D liquid glass sphere with all internal layers.
 *
 * Layers (back to front):
 * 1. Deep body gradient — gives 3D volume
 * 2. Internal liquid lobes — 4 swirling color masses (iridescent)
 * 3. Fluid caustic streaks — moving light patterns inside the glass
 * 4. Iridescent Fresnel rim — colorful edge reflection
 * 5. Specular highlight — bright glass reflection
 * 6. Glass reflection arc — secondary highlight
 * 7. Internal particles — floating sparkles
 * 8. Processing orbit ring (when thinking)
 */
private fun DrawScope.drawLiquidGlassSphere(
    center: Offset, radius: Float, time: Float, energy: Float,
    active: Boolean, listening: Boolean, processing: Boolean, error: Boolean,
    emotionEnabled: Boolean, particleDensity: Float
) {
    val bodyPath = createLiquidSpherePath(center, radius, time, energy, active, listening)

    // --- Layer 1: Deep body — 3D volume ---
    drawPath(
        bodyPath,
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF0A1230),
                0.28f to Color(0xFF0E2670),
                0.55f to Color(0xFF1421A5),
                0.78f to Color(0xFF3510C7),
                1.0f to Color(0xFF070010)
            ),
            center = Offset(center.x - radius * 0.24f, center.y - radius * 0.30f),
            radius = radius * 1.30f
        )
    )

    // --- Layer 2: Internal liquid lobes — iridescent swirling colors ---
    clipPath(bodyPath) {
        val drift = time * if (active) 1.1f else if (listening) 0.68f else 0.42f

        // Cyan lobe — upper left
        val liquid1 = createLiquidLobePath(center, radius, drift, 0.0f, 0.78f, energy)
        drawPath(
            liquid1,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = if (active) 0.82f else if (listening) 0.48f else 0.28f),
                    Color(0xFF35B7FF).copy(alpha = 0.50f),
                    Color.Transparent
                ),
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.52f),
                radius = radius * 1.12f
            ),
            blendMode = BlendMode.Screen
        )

        // Blue lobe — right side
        val liquid2 = createLiquidLobePath(center, radius, -drift * 0.72f, 1.9f, 0.66f, energy)
        drawPath(
            liquid2,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2979FF).copy(alpha = if (active) 0.70f else if (listening) 0.48f else 0.40f),
                    Color(0xFF2432F2).copy(alpha = 0.60f),
                    Color.Transparent
                ),
                center = Offset(center.x + radius * 0.36f, center.y + radius * 0.10f),
                radius = radius * 1.28f
            ),
            blendMode = BlendMode.Screen
        )

        // Violet/Purple lobe — bottom
        val liquid3 = createLiquidLobePath(center, radius, drift * 0.54f, 4.1f, 0.56f, energy)
        drawPath(
            liquid3,
            brush = Brush.radialGradient(
                colors = listOf(
                    if (error) Color(0xFFFF527A) else Color(0xFF7C4DFF).copy(alpha = if (emotionEnabled) 1f else 0.65f),
                    Color(0xFF6200FF).copy(alpha = 0.78f * if (emotionEnabled) 1f else 0.75f),
                    Color.Transparent
                ),
                center = Offset(center.x + radius * 0.18f, center.y + radius * 0.55f),
                radius = radius * 1.08f
            ),
            blendMode = BlendMode.Screen
        )

        // Magenta lobe — adds iridescent side reflection
        val liquid4 = createLiquidLobePath(center, radius, -drift * 0.38f, 2.7f, 0.48f, energy)
        drawPath(
            liquid4,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF4081).copy(alpha = if (active) 0.55f else if (listening) 0.30f else 0.15f),
                    Color(0xFFC2185B).copy(alpha = 0.30f),
                    Color.Transparent
                ),
                center = Offset(center.x - radius * 0.22f, center.y + radius * 0.30f),
                radius = radius * 0.95f
            ),
            blendMode = BlendMode.Screen
        )

        // Moving caustic streaks — fluid glass light patterns
        drawLiquidCaustics(center, radius, time, energy, active, listening)
    }

    // --- Layer 3: Iridescent Fresnel rim — colorful edge ---
    drawPath(
        bodyPath,
        brush = Brush.sweepGradient(
            0f to Color(0xFF00E5FF).copy(alpha = 0.82f),
            0.15f to Color(0xFF5FB9FF).copy(alpha = 0.55f),
            0.30f to Color(0xFF7C4DFF).copy(alpha = 0.72f),
            0.45f to Color(0xFFFF4081).copy(alpha = 0.65f),
            0.60f to Color(0xFF7C4DFF).copy(alpha = 0.55f),
            0.80f to Color(0xFF2979FF).copy(alpha = 0.45f),
            1f to Color(0xFF00E5FF).copy(alpha = 0.82f),
            center = center
        ),
        style = Stroke(width = radius * 0.022f)
    )

    // --- Layer 4: Specular highlight — bright glass reflection ---
    val highlightCenter = Offset(center.x - radius * 0.36f, center.y - radius * 0.42f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = if (active) 0.80f else if (listening) 0.55f else 0.42f),
                Color(0xFFDDFBFF).copy(alpha = if (active) 0.32f else 0.12f),
                Color.Transparent
            ),
            center = highlightCenter,
            radius = radius * 0.62f
        ),
        radius = radius * 0.62f,
        center = highlightCenter,
        blendMode = BlendMode.Screen
    )

    // --- Layer 5: Glass reflection arc ---
    drawArc(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.68f), Color.Transparent)
        ),
        startAngle = 202f,
        sweepAngle = 74f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
        size = androidx.compose.ui.geometry.Size(radius * 1.76f, radius * 1.76f),
        style = Stroke(width = radius * 0.024f, cap = StrokeCap.Round)
    )

    // --- Layer 6: Internal particles ---
    drawOrbParticles(center, radius, time, particleDensity, active, listening)

    // --- Layer 7: Processing orbit ring ---
    if (processing) {
        rotate(time * 30f, pivot = center) {
            drawArc(
                color = Color(0xFF00E5FF).copy(alpha = 0.62f),
                startAngle = -35f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.10f, center.y - radius * 1.10f),
                size = androidx.compose.ui.geometry.Size(radius * 2.20f, radius * 2.20f),
                style = Stroke(width = radius * 0.014f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Floating particles inside the sphere — denser when active.
 */
private fun DrawScope.drawOrbParticles(center: Offset, radius: Float, time: Float, density: Float, active: Boolean, listening: Boolean) {
    val count = (density.coerceIn(0f, 1f) * 22f).toInt()
    if (count <= 0) return
    repeat(count) { i ->
        val seed = i + 1
        val angle = time * (0.08f + seed * 0.003f) + seed * 2.399f
        val radial = radius * (0.28f + ((seed * 37) % 61) / 100f * 0.55f)
        val x = center.x + cos(angle.toDouble()).toFloat() * radial
        val y = center.y + sin((angle * 1.31f + seed).toDouble()).toFloat() * radial * 0.72f
        val alpha = (if (active) 0.32f else if (listening) 0.20f else 0.12f) * (1f - radial / (radius * 1.1f)).coerceIn(0.2f, 1f)
        val color = if (seed % 3 == 0) Color(0xFF00E5FF) else if (seed % 3 == 1) Color(0xFFFF4081) else Color(0xFFBFEFFF)
        drawCircle(color.copy(alpha = alpha), radius * 0.009f, Offset(x, y), blendMode = BlendMode.Screen)
    }
}

/**
 * Creates the liquid sphere silhouette with organic deformation.
 * More deformation when active/listening — fluid turbulence.
 */
private fun createLiquidSpherePath(
    center: Offset, radius: Float, time: Float, energy: Float,
    active: Boolean, listening: Boolean
): Path {
    val points = 96
    val deformation = when {
        active -> 0.032f + energy * 0.078f
        listening -> 0.018f + energy * 0.045f
        else -> 0.008f + energy * 0.020f
    }
    val speed = when {
        active -> 0.92f
        listening -> 0.58f
        else -> 0.28f
    }
    val samples = Array(points) { i ->
        val a = (i.toDouble() / points.toDouble()) * Math.PI * 2.0
        val wave =
            sin(a * 2.0 + time * 1.55 * speed) * 0.42 +
                sin(a * 3.0 - time * 1.02 * speed + 0.8) * 0.28 +
                cos(a * 5.0 + time * 0.64 * speed - 1.1) * 0.18 +
                sin(a * 7.0 - time * 0.38 * speed) * 0.12
        val r = radius * (1f + wave.toFloat() * deformation)
        Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r)
    }

    val path = Path()
    path.moveTo(samples[0].x, samples[0].y)
    for (i in samples.indices) {
        val p0 = samples[(i - 1 + points) % points]
        val p1 = samples[i]
        val p2 = samples[(i + 1) % points]
        val p3 = samples[(i + 2) % points]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.close()
    return path
}

/**
 * Creates an internal liquid lobe — a swirling color mass inside the sphere.
 */
private fun createLiquidLobePath(
    center: Offset, radius: Float, time: Float, phase: Float,
    sizeFactor: Float, energy: Float
): Path {
    val path = Path()
    val points = 88
    val localRadius = radius * sizeFactor

    for (i in 0..points) {
        val a = (i.toDouble() / points.toDouble()) * Math.PI * 2.0
        val wave =
            sin(a * 2.0 + time * 1.35 + phase) * 0.32 +
                cos(a * 4.0 - time * 0.82 + phase * 0.7) * 0.22 +
                sin(a * 6.0 + time * 0.55 - phase) * 0.14
        val r = localRadius * (0.72f + wave.toFloat() * (0.09f + energy * 0.06f))
        val x = center.x + cos(a).toFloat() * r
        val y = center.y + sin(a).toFloat() * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Fluid caustic streaks — light patterns that move inside the glass.
 */
private fun DrawScope.drawLiquidCaustics(
    center: Offset, radius: Float, time: Float, energy: Float,
    active: Boolean, listening: Boolean
) {
    val lineAlpha = when {
        active -> 0.34f + energy * 0.26f
        listening -> 0.22f + energy * 0.16f
        else -> 0.10f
    }
    val shift = time * when {
        active -> 0.82f
        listening -> 0.52f
        else -> 0.24f
    }

    repeat(4) { index ->
        val path = Path()
        val yBase = center.y - radius * (0.22f - index * 0.22f)
        for (i in 0..64) {
            val x = center.x - radius * 1.05f + (i / 64f) * radius * 2.1f
            val normalized = (x - center.x) / radius
            val y = yBase +
                sin(normalized * 3.2f + shift + index * 1.8f) * radius * (0.06f + energy * 0.03f) +
                cos(normalized * 6.0f - shift * 0.7f) * radius * 0.025f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val causticColors = when (index % 4) {
            0 -> listOf(Color.Transparent, Color(0xFF00E5FF).copy(alpha = lineAlpha), Color.Transparent)
            1 -> listOf(Color.Transparent, Color(0xFF7C4DFF).copy(alpha = lineAlpha * 0.85f), Color.Transparent)
            2 -> listOf(Color.Transparent, Color(0xFFFF4081).copy(alpha = lineAlpha * 0.70f), Color.Transparent)
            else -> listOf(Color.Transparent, Color(0xFFBDEFFF).copy(alpha = lineAlpha * 0.78f), Color.Transparent)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(colors = causticColors),
            style = Stroke(width = radius * 0.028f, cap = StrokeCap.Round),
            blendMode = BlendMode.Screen
        )
    }
}
