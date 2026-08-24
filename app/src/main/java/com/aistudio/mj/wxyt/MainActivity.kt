package com.aistudio.mj.wxyt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.aistudio.mj.wxyt.ui.theme.MyApplicationTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.mj.wxyt.ui.AssistantMode
import com.aistudio.mj.wxyt.ui.MainHomeScreen
import com.aistudio.mj.wxyt.ui.chat.ChatViewModel
import com.aistudio.mj.wxyt.ui.history.HistoryScreen
import com.aistudio.mj.wxyt.ui.history.HistoryViewModel
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository

class MainActivity : ComponentActivity() {
    private var assistantEntryRequestId by mutableLongStateOf(0L)

    private val essentialPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Result is checked at usage time; nothing to do here */ }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("orb_click", false) || intent.getBooleanExtra("assistant_entry", false)) {
            assistantEntryRequestId += 1L
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("orb_click", false) || intent.getBooleanExtra("assistant_entry", false)) {
            assistantEntryRequestId = 1L
        }
        enableEdgeToEdge()

        // Request all essential permissions at startup so that contacts, calls,
        // microphone, and notifications work without manual settings navigation.
        val missing = essentialPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        // Trigger a background app index scan so that voice commands like
        // "open YouTube" resolve instantly without a PackageManager query on
        // every command. The scan is fire-and-forget.
        Thread {
            try {
                val pm = packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) { }
        }.start()

        setContent {
            val appSettings by remember { SettingsRepository.get(this@MainActivity).settings }.collectAsState()
            val darkTheme = when (appSettings.theme) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController = navController, startDestination = "main_home?mode=voice&conversationId=") {
                        composable(
                            "main_home?mode={mode}&conversationId={conversationId}",
                            arguments = listOf(
                                navArgument("mode") { type = NavType.StringType; defaultValue = "voice" },
                                navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            val viewModel: ChatViewModel = viewModel()
                            val modeArg = backStackEntry.arguments?.getString("mode") ?: "voice"
                            val conversationId = backStackEntry.arguments?.getString("conversationId")
                            
                            val initialMode = if (modeArg == "chat" || conversationId?.isNotEmpty() == true) AssistantMode.CHAT else AssistantMode.VOICE
                            
                            LaunchedEffect(conversationId) {
                                if (conversationId != null && conversationId.isNotEmpty()) {
                                    viewModel.loadConversation(conversationId)
                                } else if (initialMode == AssistantMode.CHAT) {
                                    viewModel.newConversation()
                                }
                            }
                            
                            MainHomeScreen(
                                navController = navController, 
                                chatViewModel = viewModel,
                                initialMode = initialMode,
                                autoActivateRequestId = assistantEntryRequestId
                            )
                        }
                        composable("history") {
                            val viewModel: HistoryViewModel = viewModel()
                            HistoryScreen(navController = navController, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
