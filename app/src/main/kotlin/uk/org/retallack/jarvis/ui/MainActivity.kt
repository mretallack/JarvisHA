package uk.org.retallack.jarvis.ui

import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import uk.org.retallack.jarvis.ui.theme.ThemeMode
import uk.org.retallack.jarvis.ui.voice.VoiceScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisApp()
        }
    }
}

@Composable
fun JarvisApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val isSetupComplete by viewModel.isSetupComplete.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val navController = rememberNavController()

    JarvisTheme(themeMode = themeMode) {
        if (isSetupComplete) {
            MainScreen(navController = navController)
        } else {
            SetupWizardNavHost(
                onSetupComplete = { viewModel.markSetupComplete() },
            )
        }
    }
}

@Composable
fun MainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
                SettingsScreen()
            }
            composable(Routes.ENTITY_DETAIL) { backStackEntry ->
                val entityId = backStackEntry.arguments?.getString("entityId") ?: ""
                EntityDetailScreen(entityId = entityId)
            }
        }
    }
}
