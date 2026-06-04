package es.alejandro.grandarbiter.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun SettingsScreen() {
    Text(text = "Settings Screen")
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    GrandArbiterTheme {
        SettingsScreen()
    }
}
