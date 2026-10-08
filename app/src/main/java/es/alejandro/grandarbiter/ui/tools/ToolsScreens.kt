package es.alejandro.grandarbiter.ui.tools

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun DraftTimerScreen() {
    Text(text = "Draft Timer")
}

@Preview(showBackground = true)
@Composable
fun DraftTimerScreenPreview() {
    GrandArbiterTheme {
        DraftTimerScreen()
    }
}

