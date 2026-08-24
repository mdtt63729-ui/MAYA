package com.aistudio.mj.wxyt.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusManager
import com.aistudio.mj.wxyt.domain.chat.MessageEntity
import com.aistudio.mj.wxyt.ui.getGreeting
import kotlinx.coroutines.delay

@Composable
fun ChatContent(
    viewModel: ChatViewModel,
    focusManager: FocusManager,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the latest assistant response visible. The previous implementation
    // could leave the newest message/thinking state below the viewport.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, uiState) {
        if (messages.isNotEmpty()) {
            delay(16)
            val target = if (uiState == ChatUIState.THINKING) messages.size else messages.lastIndex
            listState.animateScrollToItem(target.coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                EmptyChatState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(messages, key = { it.id }) { msg -> ChatMessageItem(msg) }
                    if (uiState == ChatUIState.THINKING) {
                        item(key = "maya-thinking") { PremiumThinkingIndicator() }
                    }
                }
            }
        }

        // IMPORTANT: IME padding belongs to the composer, not the whole Column.
        // Padding the parent was shrinking the chat viewport and visually moving
        // the composer hundreds of pixels above the keyboard.
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF1B1B1F), Color(0xFF15151A), Color(0xFF1B1B1F))
                        ),
                        shape = RoundedCornerShape(30.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Attachment pipeline can be connected here. */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Add attachment", tint = Color(0xFF9A9AA3))
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF9B4DFF)),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val value = inputText.trim()
                            if (value.isNotEmpty() && uiState != ChatUIState.THINKING && uiState != ChatUIState.SENDING) {
                                viewModel.sendMessage(value)
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text("Ask MAYA anything...", color = Color(0xFF77777F), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )

                val canSend = inputText.trim().isNotEmpty() &&
                    uiState != ChatUIState.SENDING && uiState != ChatUIState.THINKING
                IconButton(
                    onClick = {
                        if (canSend) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = canSend
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color(0xFF9B4DFF) else Color(0xFF45454D)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp)
    ) {
        Text(
            text = getGreeting(),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF9B4DFF), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(12.dp))
            Text("How can I help you today?", color = Color(0xFF85858D), fontSize = 16.sp)
        }
    }
}

@Composable
private fun PremiumThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "maya-thinking")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "thinking-shimmer"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinking-pulse"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF24242A),
            Color(0xFF3A244F),
            Color(0xFF24242A)
        ),
        start = androidx.compose.ui.geometry.Offset(shimmer * 500f, 0f),
        end = androidx.compose.ui.geometry.Offset(shimmer * 500f + 220f, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(shimmerBrush)
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8E2DE2).copy(alpha = pulse * 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFB875FF).copy(alpha = pulse), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            ThinkingDot(delayMs = 0)
            Spacer(Modifier.width(4.dp))
            ThinkingDot(delayMs = 130)
            Spacer(Modifier.width(4.dp))
            ThinkingDot(delayMs = 260)
            Spacer(Modifier.width(10.dp))
            Text("Thinking", color = Color(0xFFB8B1C1), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ThinkingDot(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "thinking-dot-$delayMs")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(520, delayMillis = delayMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot-alpha-$delayMs"
    )
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(Color(0xFFB875FF).copy(alpha = alpha))
    )
}

@Composable
fun ChatMessageItem(msg: MessageEntity) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF4A00E0).copy(alpha = 0.32f) else Color(0xFF1A1A1F),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(start = if (isUser) 48.dp else 0.dp, end = if (isUser) 0.dp else 48.dp)
        ) {
            Text(
                text = msg.content,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
