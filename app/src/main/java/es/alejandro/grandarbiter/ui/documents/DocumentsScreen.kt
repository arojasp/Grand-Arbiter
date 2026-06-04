package es.alejandro.grandarbiter.ui.documents

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun DocumentsScreen() {
    Text(text = "Documents Screen")
}

@Composable
fun DocumentReaderScreen(documentId: String) {
    Text(text = "Document Reader: $documentId")
}

@Preview(showBackground = true)
@Composable
fun DocumentsScreenPreview() {
    GrandArbiterTheme {
        DocumentsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun DocumentReaderScreenPreview() {
    GrandArbiterTheme {
        DocumentReaderScreen(documentId = "cr")
    }
}
