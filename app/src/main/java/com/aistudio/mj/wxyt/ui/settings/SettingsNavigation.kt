package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@Composable
fun SettingsNavigation(onClose: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: SettingsViewModel = viewModel()
    
    NavHost(navController = navController, startDestination = "settings_home") {
        composable("settings_home") {
            SettingsHomeScreen(
                viewModel = viewModel,
                onClose = onClose,
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable("command_center") {
            MayaControlCenterScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onOwnerVoiceSetup = { navController.navigate("owner_voice_setup") })
        }
        composable("owner_voice_setup") {
            OwnerVoiceEnrollmentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("voice_speech") {
            VoiceSpeechSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("brain_memory") {
            BrainMemorySettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("api_secrets") {
            ApiSecretsSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("ai_intelligence") {
            AISettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("wake_word") {
            WakeWordSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("background_assistant") {
            BackgroundAssistantSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("appearance") {
            AppearanceSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("privacy_security") {
            PrivacySecuritySettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
