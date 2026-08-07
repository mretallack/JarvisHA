package uk.org.retallack.jarvis.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uk.org.retallack.jarvis.ui.navigation.Routes

@Composable
fun SetupWizardNavHost(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.SETUP_WELCOME,
        modifier = modifier,
    ) {
        composable(Routes.SETUP_WELCOME) {
            WelcomeScreen(
                onNext = { navController.navigate(Routes.SETUP_CONNECTION) },
            )
        }
        composable(Routes.SETUP_CONNECTION) {
            ConnectionScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(Routes.SETUP_MODEL_DOWNLOAD) },
            )
        }
        composable(Routes.SETUP_MODEL_DOWNLOAD) {
            ModelDownloadScreen(
                onNext = { navController.navigate(Routes.SETUP_WAKE_WORD) },
                onSkip = { navController.navigate(Routes.SETUP_WAKE_WORD) },
            )
        }
        composable(Routes.SETUP_WAKE_WORD) {
            WakeWordScreen(
                viewModel = viewModel,
                onNext = {
                    if (state.wakeWordEnabled) {
                        navController.navigate(Routes.SETUP_QUIET_HOURS)
                    } else {
                        navController.navigate(Routes.SETUP_DONE)
                    }
                },
            )
        }
        composable(Routes.SETUP_QUIET_HOURS) {
            QuietHoursScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(Routes.SETUP_DONE) },
            )
        }
        composable(Routes.SETUP_DONE) {
            SetupDoneScreen(
                viewModel = viewModel,
                onDone = onSetupComplete,
            )
        }
    }
}
