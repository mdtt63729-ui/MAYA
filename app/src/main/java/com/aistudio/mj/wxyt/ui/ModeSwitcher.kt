package com.aistudio.mj.wxyt.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AssistantMode {
    VOICE, CHAT
}

@Composable
fun AnimatedSegmentedControl(
    currentMode: AssistantMode,
    onModeSelected: (AssistantMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var voiceWidth by remember { mutableStateOf(0.dp) }
    var chatWidth by remember { mutableStateOf(0.dp) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .padding(4.dp)
    ) {
        // Animated Indicator
        val targetOffsetX = if (currentMode == AssistantMode.VOICE) 0.dp else voiceWidth
        val targetWidth = if (currentMode == AssistantMode.VOICE) voiceWidth else chatWidth
        
        // "buttery smooth" premium easing
        val offsetX by animateDpAsState(
            targetValue = targetOffsetX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 300f
            ),
            label = "indicatorOffset"
        )
        
        val indicatorWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 300f
            ),
            label = "indicatorWidth"
        )

        // The background indicator pill
        if (voiceWidth > 0.dp && chatWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = offsetX)
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFF4A00E0).copy(alpha = 0.3f))
                )
            }
        }

        // The Buttons
        Row {
            // VOICE BUTTON
            Row(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        voiceWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onModeSelected(AssistantMode.VOICE) }
                    )
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Mic, 
                    contentDescription = "Voice Mode", 
                    tint = if (currentMode == AssistantMode.VOICE) Color.White else Color.Gray, 
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Voice", 
                    color = if (currentMode == AssistantMode.VOICE) Color.White else Color.Gray, 
                    fontWeight = FontWeight.Medium
                )
            }
            
            // CHAT BUTTON
            Row(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        chatWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onModeSelected(AssistantMode.CHAT) }
                    )
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat, 
                    contentDescription = "Chat Mode", 
                    tint = if (currentMode == AssistantMode.CHAT) Color.White else Color.Gray, 
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Chat", 
                    color = if (currentMode == AssistantMode.CHAT) Color.White else Color.Gray, 
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
