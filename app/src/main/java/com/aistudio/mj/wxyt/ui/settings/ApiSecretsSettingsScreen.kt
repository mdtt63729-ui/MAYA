package com.aistudio.mj.wxyt.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.aistudio.mj.wxyt.domain.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSecretsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val openCodeApiKey by viewModel.openCodeApiKey.collectAsState()
    val nvidiaApiKey by viewModel.nvidiaApiKey.collectAsState()
    val customProviderApiKey by viewModel.customProviderApiKey.collectAsState()
    val customBaseUrl by viewModel.customBaseUrl.collectAsState()
    val customModelId by viewModel.customModelId.collectAsState()

    val geminiConnectionState by viewModel.geminiConnectionState.collectAsState()
    val geminiErrorMessage by viewModel.geminiErrorMessage.collectAsState()
    val openRouterConnectionState by viewModel.openRouterConnectionState.collectAsState()
    val openRouterErrorMessage by viewModel.openRouterErrorMessage.collectAsState()
    val openCodeConnectionState by viewModel.openCodeConnectionState.collectAsState()
    val openCodeErrorMessage by viewModel.openCodeErrorMessage.collectAsState()
    val nvidiaConnectionState by viewModel.nvidiaConnectionState.collectAsState()
    val nvidiaErrorMessage by viewModel.nvidiaErrorMessage.collectAsState()
    val customProviderConnectionState by viewModel.customProviderConnectionState.collectAsState()
    val customProviderErrorMessage by viewModel.customProviderErrorMessage.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("API & Secrets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ApiProviderCard(
                title = "Gemini API",
                apiKey = geminiApiKey,
                connectionState = geminiConnectionState,
                errorMessage = geminiErrorMessage,
                onSave = { viewModel.setGeminiApiKey(it) },
                onTestConnection = { viewModel.testGeminiConnection() },
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(16.dp))

            ApiProviderCard(
                title = "OpenRouter",
                apiKey = openRouterApiKey,
                connectionState = openRouterConnectionState,
                errorMessage = openRouterErrorMessage,
                onSave = { viewModel.setOpenRouterApiKey(it) },
                onTestConnection = { viewModel.testOpenRouterConnection() },
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(16.dp))

            ApiProviderCard(
                title = "OpenCode",
                apiKey = openCodeApiKey,
                connectionState = openCodeConnectionState,
                errorMessage = openCodeErrorMessage,
                onSave = { viewModel.setOpenCodeApiKey(it) },
                onTestConnection = { viewModel.testOpenCodeConnection() },
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(16.dp))

            ApiProviderCard(
                title = "NVIDIA NIM",
                apiKey = nvidiaApiKey,
                connectionState = nvidiaConnectionState,
                errorMessage = nvidiaErrorMessage,
                onSave = { viewModel.setNvidiaApiKey(it) },
                onTestConnection = { viewModel.testNvidiaConnection() },
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(16.dp))

            ApiProviderCard(
                title = "Custom OpenAI-Compatible",
                apiKey = customProviderApiKey,
                connectionState = customProviderConnectionState,
                errorMessage = customProviderErrorMessage,
                onSave = { viewModel.setCustomProviderApiKey(it) },
                onTestConnection = { viewModel.testCustomProviderConnection() },
                snackbarHostState = snackbarHostState
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customBaseUrl,
                onValueChange = viewModel::setCustomBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom API base URL") },
                placeholder = { Text("https://example.com/v1") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customModelId,
                onValueChange = viewModel::setCustomModelId,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom model ID") },
                placeholder = { Text("provider/model-name") },
                singleLine = true
            )
            Text(
                "Custom endpoint and model are used by the Custom OpenAI-Compatible chat provider.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ApiProviderCard(
    title: String,
    apiKey: String,
    connectionState: com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState,
    errorMessage: String?,
    onSave: (String) -> Boolean,
    onTestConnection: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var keyVisible by remember { mutableStateOf(false) }
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }
    var showDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(connectionState) {
        if (connectionState == com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.ERROR) {
            showDialog = true
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            val (statusText, statusColor, statusIcon) = when (connectionState) {
                com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.SUCCESS -> Triple("Connected", MaterialTheme.colorScheme.primary, Icons.Default.Check)
                com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.ERROR -> Triple("Disconnected", MaterialTheme.colorScheme.error, Icons.Default.Close)
                com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.TESTING -> Triple("Testing...", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), null)
                com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.NOT_TESTED -> {
                    if (apiKey.isNotEmpty()) Triple("Configured", MaterialTheme.colorScheme.primary, null)
                    else Triple("Not Configured", MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), null)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusIcon != null) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Status: $statusText", color = statusColor)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tempKey,
                onValueChange = { tempKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Visibility")
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (apiKey.isNotEmpty() && tempKey == apiKey) {
                    Button(
                        onClick = onTestConnection,
                        enabled = connectionState != com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.TESTING,
                    ) {
                        if (connectionState == com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.TESTING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else if (connectionState == com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.SUCCESS) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connected")
                        } else if (connectionState == com.aistudio.mj.wxyt.domain.settings.SettingsViewModel.ConnectionState.ERROR) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnected")
                        } else {
                            Text("Test Connection")
                        }
                    }
                } else {
                    Button(onClick = { 
                        val success = onSave(tempKey.trim())
                        if (success) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("✓ API key saved") }
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Unable to save API key. Please try again.") }
                        }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
    
    if (showDialog && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Connection Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString("MAYA API Connection Error\nProvider: $title\nError: $errorMessage"))
                        coroutineScope.launch { snackbarHostState.showSnackbar("✓ Error copied") }
                        showDialog = false
                    }
                ) {
                    Text("Copy Error")
                }
            }
        )
    }
}
