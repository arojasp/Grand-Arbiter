package es.alejandro.grandarbiter.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun HomeScreen() {
    Text(text = "Home Screen")
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GrandArbiterTheme {
        HomeScreen()
    }
}
