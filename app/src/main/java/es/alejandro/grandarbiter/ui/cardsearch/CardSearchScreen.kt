package es.alejandro.grandarbiter.ui.cardsearch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun CardSearchScreen() {
    Text(text = "Card Search Screen")
}

@Composable
fun CardDetailScreen(cardId: String) {
    Text(text = "Card Detail: $cardId")
}

@Preview(showBackground = true)
@Composable
fun CardSearchScreenPreview() {
    GrandArbiterTheme {
        CardSearchScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun CardDetailScreenPreview() {
    GrandArbiterTheme {
        CardDetailScreen(cardId = "123")
    }
}
