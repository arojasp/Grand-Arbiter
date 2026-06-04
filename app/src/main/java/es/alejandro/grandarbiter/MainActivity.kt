package es.alejandro.grandarbiter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import es.alejandro.grandarbiter.ui.main.MainScreen
import es.alejandro.grandarbiter.ui.main.MainViewModel
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            GrandArbiterTheme(themeMode = themeMode) {
                MainScreen()
            }
        }
    }
}

data class NavigationItem(
    @StringRes val titleRes: Int,
    val route: String,
    val icon: ImageVector
)
