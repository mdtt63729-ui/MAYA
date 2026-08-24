package com.aistudio.mj.wxyt.ui.history


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.aistudio.mj.wxyt.domain.chat.ConversationEntity
import com.aistudio.mj.wxyt.ui.settings.SettingsNavigation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("main_home?mode=voice&conversationId=") { popUpTo(0) } },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF4A00E0).copy(alpha = 0.3f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF4A00E0).copy(alpha = 0.3f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))
                // Mode Switcher - Both just navigate
                Row(
                    modifier = Modifier
                        .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(32.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .clickable { navController.navigate("main_home?mode=voice&conversationId=") }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voice", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .clickable { navController.navigate("main_home?mode=chat&conversationId=") }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat", color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }
                
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
            
            // Header
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "History",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Your recent conversations and interactions with MAYA.",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Search Bar
            Surface(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF8E2DE2)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search history...", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Conversation List
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (searchQuery.isNotEmpty()) {
                        Text("No matching conversations", color = Color.Gray, fontSize = 16.sp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No conversations yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Your conversations with MAYA will appear here.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    val grouped = groupConversationsByDate(conversations)
                    grouped.forEach { (header, items) ->
                        item {
                            Text(
                                text = header,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        items(items, key = { it.id }) { conv ->
                            ConversationItem(
                                conversation = conv,
                                onClick = {
                                    // Open conversation
                                    navController.navigate("main_home?mode=chat&conversationId=${conv.id}")
                                },
                                onDelete = {
                                    viewModel.deleteConversation(conv.id)
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        if (showSettings) {
            Dialog(
                onDismissRequest = { showSettings = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                SettingsNavigation(onClose = { showSettings = false })
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Surface(
        color = Color(0xFF141414),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF8E2DE2).copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFF8E2DE2))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title.ifBlank { "Conversation" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(conversation.updatedAt),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Gray)
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    DropdownMenuItem(
                        text = { Text("Open", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }
}

fun formatTimestamp(timeMs: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timeMs))
}

fun groupConversationsByDate(conversations: List<ConversationEntity>): Map<String, List<ConversationEntity>> {
    val grouped = mutableMapOf<String, MutableList<ConversationEntity>>()
    val now = Calendar.getInstance()
    
    for (conv in conversations) {
        val convDate = Calendar.getInstance().apply { timeInMillis = conv.updatedAt }
        
        val isToday = now.get(Calendar.YEAR) == convDate.get(Calendar.YEAR) && 
                      now.get(Calendar.DAY_OF_YEAR) == convDate.get(Calendar.DAY_OF_YEAR)
                      
        val isYesterday = now.get(Calendar.YEAR) == convDate.get(Calendar.YEAR) && 
                          now.get(Calendar.DAY_OF_YEAR) - convDate.get(Calendar.DAY_OF_YEAR) == 1
                          
        now.add(Calendar.DAY_OF_YEAR, -7)
        val isThisWeek = convDate.after(now) && !isToday && !isYesterday
        now.add(Calendar.DAY_OF_YEAR, 7) // reset
        
        val group = when {
            isToday -> "Today"
            isYesterday -> "Yesterday"
            isThisWeek -> "This Week"
            else -> "Older"
        }
        
        if (grouped[group] == null) {
            grouped[group] = mutableListOf()
        }
        grouped[group]?.add(conv)
    }
    
    // Sort groups
    val result = linkedMapOf<String, List<ConversationEntity>>()
    listOf("Today", "Yesterday", "This Week", "Older").forEach { key ->
        grouped[key]?.let { result[key] = it }
    }
    
    return result
}
