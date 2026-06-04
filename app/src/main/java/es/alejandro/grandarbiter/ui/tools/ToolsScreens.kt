package es.alejandro.grandarbiter.ui.tools

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun ToolsScreen() {
    Text(text = "Tools Screen")
}

@Composable
fun DeckCounterScreen() {
    Text(text = "Deck Counter")
}

@Composable
fun JudgeTimerScreen() {
    Text(text = "Judge Timer")
}

@Composable
fun DraftTimerScreen() {
    Text(text = "Draft Timer")
}

@Composable
fun HeadJudgeAnnouncementScreen() {
    Text(text = "Announcements")
}

@Composable
fun BannedListsScreen() {
    Text(text = "Banned Lists")
}

@Composable
fun QuickReferencesScreen() {
    Text(text = "Quick References")
}

@Composable
fun QuickReferenceDetailScreen(referenceId: String) {
    Text(text = "Quick Reference: $referenceId")
}

@Preview(showBackground = true)
@Composable
fun ToolsScreenPreview() {
    GrandArbiterTheme {
        ToolsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun DeckCounterScreenPreview() {
    GrandArbiterTheme {
        DeckCounterScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun JudgeTimerScreenPreview() {
    GrandArbiterTheme {
        JudgeTimerScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun DraftTimerScreenPreview() {
    GrandArbiterTheme {
        DraftTimerScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun HeadJudgeAnnouncementScreenPreview() {
    GrandArbiterTheme {
        HeadJudgeAnnouncementScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun BannedListsScreenPreview() {
    GrandArbiterTheme {
        BannedListsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun QuickReferencesScreenPreview() {
    GrandArbiterTheme {
        QuickReferencesScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun QuickReferenceDetailScreenPreview() {
    GrandArbiterTheme {
        QuickReferenceDetailScreen(referenceId = "456")
    }
}
