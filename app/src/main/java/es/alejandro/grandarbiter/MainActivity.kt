package es.alejandro.grandarbiter

import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.alejandro.grandarbiter.domain.model.ThemeMode
import es.alejandro.grandarbiter.domain.model.rules.RulesDocumentInfo
import es.alejandro.grandarbiter.ui.main.MainScreen
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.main.appupdate.AppUpdateController
import es.alejandro.grandarbiter.ui.main.appupdate.AppUpdateUi
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()
    private lateinit var appUpdates: AppUpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appUpdates = AppUpdateController(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.appUpdateRequired.collect(appUpdates::setRequired) }
        }
        // Android 12+: tell the system the in-app theme. It keeps it for the app, so the next
        // launch's splash is drawn dark or light to match (the splash shows before any app code).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModes = getSystemService(UiModeManager::class.java)
            lifecycleScope.launch {
                viewModel.themeMode.collect { mode ->
                    val night = when (mode) {
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    }
                    // No "already set?" check: getNightMode() may report the system mode, not the app's.
                    uiModes.setApplicationNightMode(night)
                }
            }
        }
        setContent {
            val settings by viewModel.displaySettings.collectAsState()
            // Only while the app is in the foreground: the flag goes with the window.
            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            GrandArbiterTheme(
                themeMode = settings.themeMode,
                textScale = settings.textScale,
                highContrast = settings.highContrast,
            ) {
                val snackbar = remember { SnackbarHostState() }
                UpdateMessages(snackbar)
                MainScreen(snackbar)
            }
        }
    }

    /** "CR updated" after the launch check, and the app-update snackbar / dialog. */
    @Composable
    private fun UpdateMessages(snackbar: SnackbarHostState) {
        val updateUi by appUpdates.ui.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.documentsUpdated.collect { docs -> snackbar.showSnackbar(documentsMessage(docs), withDismissAction = true, duration = SnackbarDuration.Long) }
        }
        LaunchedEffect(updateUi) {
            if (updateUi == AppUpdateUi.RestartReady) {
                val result = snackbar.showSnackbar(
                    getString(R.string.app_update_ready), actionLabel = getString(R.string.app_update_restart),
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) appUpdates.restart()
            }
        }
        if (updateUi is AppUpdateUi.Required) {
            AlertDialog(
                onDismissRequest = appUpdates::postpone,
                title = { Text(stringResource(R.string.app_update_required_title)) },
                text = { Text(stringResource(R.string.app_update_required_message)) },
                confirmButton = { TextButton(onClick = appUpdates::updateNow) { Text(stringResource(R.string.app_update_now)) } },
                dismissButton = { TextButton(onClick = appUpdates::postpone) { Text(stringResource(R.string.app_update_later)) } },
            )
        }
    }

    private fun documentsMessage(docs: List<RulesDocumentInfo>): String {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        return if (docs.size == 1) getString(R.string.rules_updated_one, docs[0].title, docs[0].effectiveDate.format(date))
        else getString(R.string.rules_updated_many, docs.joinToString(", ") { it.id.key.uppercase() })
    }
}

data class NavigationItem(
    @StringRes val titleRes: Int,
    val route: String,
    val icon: ImageVector
)
