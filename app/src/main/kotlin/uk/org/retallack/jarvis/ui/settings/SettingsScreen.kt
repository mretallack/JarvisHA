package uk.org.retallack.jarvis.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

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
                    if (state.isConnected) "Connected (${state.haVersion})" else "Not connected",
                )
            },
            leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null) },
            modifier = Modifier.clickable { /* navigate to connection settings */ },
        )
        HorizontalDivider()

        // Voice
        SettingsSection("Voice")
        ListItem(
            headlineContent = { Text("Speech Recognition") },
            supportingContent = {
                Text(if (state.sttModelAvailable) "Model loaded" else "Model not available")
            },
            leadingContent = { Icon(Icons.Filled.Mic, contentDescription = null) },
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Text to Speech") },
            supportingContent = {
                Text(if (state.ttsModelAvailable) "Model loaded" else "Model not available")
            },
            leadingContent = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) },
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()

        // Wake word
        SettingsSection("Wake Word")
        ListItem(
            headlineContent = { Text("Hey Jarvis") },
            supportingContent = {
                Text(if (state.wakeWordEnabled) "Enabled" else "Disabled")
            },
            leadingContent = { Icon(Icons.Filled.Code, contentDescription = null) },
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
            leadingContent = { Icon(Icons.Filled.Nightlight, contentDescription = null) },
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
            leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
            modifier = Modifier.clickable { },
        )
        HorizontalDivider()

        // About
        SettingsSection("About")
        ListItem(
            headlineContent = { Text("JarvisHA") },
            supportingContent = { Text("Version ${state.appVersion}") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
        )
    }
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
