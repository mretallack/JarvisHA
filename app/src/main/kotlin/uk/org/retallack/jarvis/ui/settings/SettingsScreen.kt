package uk.org.retallack.jarvis.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import uk.org.retallack.jarvis.data.repository.SttSettings
import uk.org.retallack.jarvis.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onRerunWizard: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showAgentDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Permission launcher for RECORD_AUDIO (needed for wake word)
    val context = androidx.compose.ui.platform.LocalContext.current

    var showQuietHoursDialog by remember { mutableStateOf(false) }
    var qhEnabled by remember { mutableStateOf(state.quietHoursEnabled) }
    var qhStart by remember { mutableStateOf(state.quietHoursStart) }
    var qhEnd by remember { mutableStateOf(state.quietHoursEnd) }

    androidx.compose.runtime.LaunchedEffect(state.quietHoursEnabled, state.quietHoursStart, state.quietHoursEnd) {
        qhEnabled = state.quietHoursEnabled
        qhStart = state.quietHoursStart
        qhEnd = state.quietHoursEnd
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            viewModel.toggleWakeWord(true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Connection
        SettingsSection("Connection")
        ListItem(
            headlineContent = { Text("Home Assistant") },
            supportingContent = {
                Text(
                    if (state.isConnected) "Connected (${state.haVersion ?: state.haUrl})" else "Not connected",
                )
            },
            leadingContent = {
                Icon(Icons.Filled.Wifi, contentDescription = "Connection settings")
            },
            modifier = Modifier.clickable { /* navigate to connection settings */ },
        )
        HorizontalDivider()

        // Voice
        SettingsSection("Voice")
        ListItem(
            headlineContent = { Text("Speech Recognition") },
            supportingContent = {
                Text(if (state.sttModelAvailable) "Speech recognition ready" else "Speech recognition not initialised")
            },
            leadingContent = {
                Icon(Icons.Filled.Mic, contentDescription = "Speech recognition settings")
            },
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Text to Speech") },
            supportingContent = {
                Text(if (state.ttsModelAvailable) "Text-to-speech ready" else "Text-to-speech not initialised")
            },
            leadingContent = {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Text to speech settings")
            },
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()

        // STT End-of-Command Detection Settings
        SttSettingsSliders(
            sttSettings = state.sttSettings,
            onSettingsChanged = { viewModel.updateSttSettings(it) },
        )
        HorizontalDivider()

        // Conversation Agent
        ListItem(
            headlineContent = { Text("Conversation Agent") },
            supportingContent = {
                val agentName = state.availableAgents
                    .find { it.id == state.selectedAgentId }?.name
                    ?: state.selectedAgentId
                    ?: "Default (Home Assistant)"
                Text(agentName)
            },
            leadingContent = {
                Icon(Icons.Filled.SmartToy, contentDescription = "Conversation agent settings")
            },
            modifier = Modifier.clickable { showAgentDialog = true },
        )
        HorizontalDivider()

        // Wake word
        SettingsSection("Wake Word")
        ListItem(
            headlineContent = { Text("Hey Jarvis") },
            supportingContent = {
                Text(
                    if (state.wakeWordEnabled) {
                        if (state.wakeWordRunning) "Listening in background" else "Enabled (starting…)"
                    } else {
                        "Disabled"
                    },
                )
            },
            leadingContent = {
                Icon(Icons.Filled.Code, contentDescription = "Wake word settings")
            },
            trailingContent = {
                Switch(
                    checked = state.wakeWordEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // Request permissions before enabling wake word
                            val permissions = buildList {
                                add(android.Manifest.permission.RECORD_AUDIO)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.RECORD_AUDIO,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasAudio) {
                                viewModel.toggleWakeWord(true)
                            } else {
                                permissionLauncher.launch(permissions.toTypedArray())
                            }
                        } else {
                            viewModel.toggleWakeWord(false)
                        }
                    },
                )
            },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Quiet Hours") },
            supportingContent = {
                Text(
                    if (state.quietHoursEnabled) {
                        "${state.quietHoursStart} – ${state.quietHoursEnd}"
                    } else {
                        "Disabled"
                    },
                )
            },
            leadingContent = {
                Icon(Icons.Filled.Nightlight, contentDescription = "Quiet hours settings")
            },
            modifier = Modifier.clickable { showQuietHoursDialog = true },
        )

        if (showQuietHoursDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showQuietHoursDialog = false },
                title = { Text("Configure Quiet Hours") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text("Enable Quiet Hours")
                            Switch(
                                checked = qhEnabled,
                                onCheckedChange = { qhEnabled = it },
                            )
                        }

                        if (qhEnabled) {
                            androidx.compose.material3.OutlinedTextField(
                                value = qhStart,
                                onValueChange = { qhStart = it },
                                label = { Text("Start Time (HH:MM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = qhEnd,
                                onValueChange = { qhEnd = it },
                                label = { Text("End Time (HH:MM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.updateQuietHours(qhEnabled, qhStart, qhEnd)
                            showQuietHoursDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showQuietHoursDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        HorizontalDivider()



        // Appearance
        SettingsSection("Appearance")
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = {
                Text(
                    when (state.themeMode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                    },
                )
            },
            leadingContent = {
                Icon(Icons.Filled.Brightness6, contentDescription = "Theme settings")
            },
            modifier = Modifier.clickable { showThemeDialog = true },
        )
        HorizontalDivider()

        // Setup
        SettingsSection("Setup")
        ListItem(
            headlineContent = { Text("Re-run Setup Wizard") },
            supportingContent = { Text("Reconfigure connection, voice, and wake word settings") },
            leadingContent = {
                Icon(Icons.Filled.Refresh, contentDescription = "Re-run setup wizard")
            },
            modifier = Modifier.clickable { onRerunWizard?.invoke() },
        )

        // About
        SettingsSection("About")
        ListItem(
            headlineContent = { Text("JarvisHA") },
            supportingContent = { Text("Version ${state.appVersion}") },
            leadingContent = {
                Icon(Icons.Filled.Info, contentDescription = "About JarvisHA")
            },
        )
    }

    // Agent selection dialog
    if (showAgentDialog) {
        AgentSelectionDialog(
            agents = state.availableAgents,
            selectedAgentId = state.selectedAgentId,
            isLoading = state.isLoadingAgents,
            onAgentSelected = { agentId ->
                viewModel.selectAgent(agentId)
                showAgentDialog = false
            },
            onDismiss = { showAgentDialog = false },
        )
    }

    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = state.themeMode,
            onModeSelected = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun AgentSelectionDialog(
    agents: List<ConversationAgent>,
    selectedAgentId: String?,
    isLoading: Boolean,
    onAgentSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation Agent") },
        text = {
            Column {
                if (isLoading) {
                    Text("Loading agents...")
                } else if (agents.isEmpty()) {
                    Text("No conversation agents found. Make sure Home Assistant is connected.")
                } else {
                    // Default option
                    ListItem(
                        headlineContent = { Text("Default (Home Assistant)") },
                        leadingContent = {
                            RadioButton(
                                selected = selectedAgentId == null,
                                onClick = { onAgentSelected(null) },
                            )
                        },
                        modifier = Modifier.clickable { onAgentSelected(null) },
                    )
                    agents.forEach { agent ->
                        ListItem(
                            headlineContent = { Text(agent.name) },
                            supportingContent = { Text(agent.id) },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedAgentId == agent.id,
                                    onClick = { onAgentSelected(agent.id) },
                                )
                            },
                            modifier = Modifier.clickable { onAgentSelected(agent.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                    }
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(
                                selected = currentMode == mode,
                                onClick = { onModeSelected(mode) },
                            )
                        },
                        modifier = Modifier.clickable { onModeSelected(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SttSettingsSliders(
    sttSettings: SttSettings,
    onSettingsChanged: (SttSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "End-of-Command Detection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Silence Duration slider
        val silenceDurationSeconds = sttSettings.silenceDurationMs / 1000f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Silence Duration: ${"%.1f".format(silenceDurationSeconds)}s",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Slider(
            value = silenceDurationSeconds,
            onValueChange = { value ->
                val ms = (value * 1000).toInt()
                onSettingsChanged(sttSettings.copy(silenceDurationMs = ms))
            },
            valueRange = 1f..5f,
            steps = 7, // 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0 → 8 intervals = 7 steps
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("1s", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text("5s", style = MaterialTheme.typography.labelSmall)
        }

        // Silence Threshold slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(
                text = "Silence Threshold: ${sttSettings.silenceThreshold}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Slider(
            value = sttSettings.silenceThreshold.toFloat(),
            onValueChange = { value ->
                onSettingsChanged(sttSettings.copy(silenceThreshold = value.toInt()))
            },
            valueRange = 200f..1500f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("200", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text("1500", style = MaterialTheme.typography.labelSmall)
        }

        // Max Recording slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(
                text = "Max Recording: ${sttSettings.maxRecordingSeconds}s",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Slider(
            value = sttSettings.maxRecordingSeconds.toFloat(),
            onValueChange = { value ->
                onSettingsChanged(sttSettings.copy(maxRecordingSeconds = value.toInt()))
            },
            valueRange = 10f..60f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("10s", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text("60s", style = MaterialTheme.typography.labelSmall)
        }
    }
}
