package uk.org.retallack.jarvis.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.org.retallack.jarvis.ui.entities.EntitiesScreen
import uk.org.retallack.jarvis.ui.entities.EntityDetailScreen
import uk.org.retallack.jarvis.ui.navigation.Routes
import uk.org.retallack.jarvis.ui.settings.SettingsScreen
import uk.org.retallack.jarvis.ui.setup.SetupWizardNavHost
import uk.org.retallack.jarvis.ui.theme.JarvisTheme
import uk.org.retallack.jarvis.ui.voice.VoiceScreen
import uk.org.retallack.jarvis.voice.wakeword.WakeWordService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startedByWakeWord = intent?.action == WakeWordService.ACTION_WAKE_WORD
        if (startedByWakeWord) {
            Log.i(TAG, "Started by wake word detection")
        }

        setContent {
            JarvisApp(startedByWakeWord = startedByWakeWord)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == WakeWordService.ACTION_WAKE_WORD) {
            Log.i(TAG, "Wake word intent received (onNewIntent)")
            // The composable will re-read intent via activity
            setIntent(intent)
        }
    }
}

@Composable
fun JarvisApp(
    startedByWakeWord: Boolean = false,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val isSetupComplete by viewModel.isSetupComplete.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val navController = rememberNavController()
    var showWizard by remember { mutableStateOf(false) }

    JarvisTheme(themeMode = themeMode) {
        if (!isSetupComplete || showWizard) {
            SetupWizardNavHost(
                onSetupComplete = {
                    viewModel.markSetupComplete()
                    showWizard = false
                },
            )
        } else {
            MainScreen(
                navController = navController,
                onRerunWizard = { showWizard = true },
                autoStartListening = startedByWakeWord,
            )
        }
    }
}

@Composable
fun MainScreen(
    navController: NavHostController,
    onRerunWizard: () -> Unit = {},
    autoStartListening: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // When launched by wake word, navigate to Voice tab
    LaunchedEffect(autoStartListening) {
        if (autoStartListening && currentRoute != Routes.VOICE_TAB) {
            navController.navigate(Routes.VOICE_TAB) {
                popUpTo(Routes.VOICE_TAB) { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in listOf(Routes.VOICE_TAB, Routes.ENTITIES_TAB, Routes.SETTINGS_TAB)) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.VOICE_TAB,
                        onClick = {
                            navController.navigate(Routes.VOICE_TAB) {
                                popUpTo(Routes.VOICE_TAB) { inclusive = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.Mic, contentDescription = "Voice") },
                        label = { Text("Voice") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTITIES_TAB,
                        onClick = {
                            navController.navigate(Routes.ENTITIES_TAB) {
                                popUpTo(Routes.VOICE_TAB)
                            }
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Entities") },
                        label = { Text("Entities") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS_TAB,
                        onClick = {
                            navController.navigate(Routes.SETTINGS_TAB) {
                                popUpTo(Routes.VOICE_TAB)
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.VOICE_TAB,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.VOICE_TAB) {
                VoiceScreen(
                    autoStartListening = autoStartListening,
                    onEntityClick = { entityId ->
                        navController.navigate(Routes.entityDetail(entityId))
                    },
                )
            }
            composable(Routes.ENTITIES_TAB) {
                EntitiesScreen(
                    onEntityClick = { entityId ->
                        navController.navigate(Routes.entityDetail(entityId))
                    },
                )
            }
            composable(Routes.SETTINGS_TAB) {
                SettingsScreen(onRerunWizard = onRerunWizard)
            }
            composable(Routes.ENTITY_DETAIL) { backStackEntry ->
                val entityId = backStackEntry.arguments?.getString("entityId") ?: ""
                EntityDetailScreen(entityId = entityId)
            }
        }
    }
}
