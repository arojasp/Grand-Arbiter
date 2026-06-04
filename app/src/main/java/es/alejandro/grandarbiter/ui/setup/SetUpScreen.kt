package es.alejandro.grandarbiter.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun SetUpScreen(onSetUpComplete: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSetUpComplete) {
            Text(text = "Complete Set Up")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetUpScreenPreview() {
    GrandArbiterTheme {
        SetUpScreen(onSetUpComplete = {})
    }
}
