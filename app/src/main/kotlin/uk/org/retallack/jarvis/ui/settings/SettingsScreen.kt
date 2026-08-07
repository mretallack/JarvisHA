package uk.org.retallack.jarvis.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                Text(if (state.wakeWordEnabled) "Enabled" else "Disabled")
            },
            leadingContent = {
                Icon(Icons.Filled.Code, contentDescription = "Wake word settings")
            },
            modifier = Modifier.clickable { },
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
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()

        // Security
        SettingsSection("Security")
        ListItem(
            headlineContent = { Text("Biometric Auth") },
            supportingContent = {
                Text(if (state.biometricEnabled) "Required for sensitive devices" else "Disabled")
            },
            leadingContent = {
                Icon(Icons.Filled.Security, contentDescription = "Biometric security settings")
            },
            modifier = Modifier.clickable { },
        )
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
