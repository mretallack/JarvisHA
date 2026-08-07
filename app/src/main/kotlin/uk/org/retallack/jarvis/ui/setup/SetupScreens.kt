package uk.org.retallack.jarvis.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to JarvisHA",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Voice control for Home Assistant.\nPrivate. Offline. No Google required.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Get Started")
        }
    }
}

@Composable
fun ConnectionScreen(
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Connect to Home Assistant",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Enter your Home Assistant URL and a long-lived access token.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.haUrl,
            onValueChange = { viewModel.updateUrl(it) },
            label = { Text("Home Assistant URL") },
            placeholder = { Text("https://homeassistant.local:8123") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = state.haToken,
            onValueChange = { viewModel.updateToken(it) },
            label = { Text("Access Token") },
            placeholder = { Text("Long-lived access token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.connectionError != null) {
            Text(
                text = state.connectionError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.connectionSuccess) {
            Text(
                text = "✓ Connected (HA ${state.haVersion})",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isTestingConnection) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test Connection")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.connectionSuccess,
        ) {
            Text("Next")
        }
    }
}

@Composable
fun ModelDownloadScreen(
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Voice Models",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "JarvisHA uses on-device speech recognition. " +
                "The STT model (~30MB) will be downloaded for offline use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "• Speech-to-Text: Sherpa-ONNX streaming model (20M params)\n" +
                "• All processing stays on your device\n" +
                "• Works fully offline after download",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.sttModelDownloaded) {
            Text(
                text = "✓ STT model ready",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (state.isDownloadingSttModel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloading... ${(state.sttDownloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            if (state.sttDownloadError != null) {
                Text(
                    text = state.sttDownloadError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { viewModel.downloadSttModel() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download STT Model (~30MB)")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.sttModelDownloaded,
        ) {
            Text("Continue")
        }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for Now")
        }
    }
}

@Composable
fun WakeWordScreen(
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setWakeWordEnabled(true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Wake Word",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Enable \"Hey Jarvis\" to activate voice control hands-free.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable Wake Word")
            Switch(
                checked = state.wakeWordEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // Request microphone permission before enabling
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.setWakeWordEnabled(false)
                    }
                },
            )
        }

        if (state.wakeWordEnabled) {
            Text(
                text = "Sensitivity: ${String.format("%.0f%%", state.sensitivity * 100)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.sensitivity,
                onValueChange = { viewModel.setSensitivity(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "⚠️ Wake word uses a background service with microphone access. " +
                    "Battery usage is minimal (~2% per day).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun QuietHoursScreen(
    viewModel: SetupViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Quiet Hours",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Disable wake word listening during specific hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable Quiet Hours")
            Switch(
                checked = state.quietHoursEnabled,
                onCheckedChange = { viewModel.setQuietHoursEnabled(it) },
            )
        }

        if (state.quietHoursEnabled) {
            Text(
                text = "From ${state.quietHoursStart} to ${state.quietHoursEnd}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "The mic button will still work during quiet hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun SetupDoneScreen(
    viewModel: SetupViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "All Set!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (state.wakeWordEnabled) {
                "Say \"Hey Jarvis\" or tap the mic button to control your smart home."
            } else {
                "Tap the mic button to control your smart home."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                viewModel.completeSetup()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Using JarvisHA")
        }
    }
}
