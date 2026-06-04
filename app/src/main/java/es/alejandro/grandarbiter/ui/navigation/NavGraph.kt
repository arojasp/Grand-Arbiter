package es.alejandro.grandarbiter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import es.alejandro.grandarbiter.ui.home.HomeScreen
import es.alejandro.grandarbiter.ui.documents.DocumentReaderScreen
import es.alejandro.grandarbiter.ui.cardsearch.CardSearchScreen
import es.alejandro.grandarbiter.ui.cardsearch.CardDetailScreen
import es.alejandro.grandarbiter.ui.tools.ToolsScreen
import es.alejandro.grandarbiter.ui.tools.DeckCounterScreen
import es.alejandro.grandarbiter.ui.tools.JudgeTimerScreen
import es.alejandro.grandarbiter.ui.tools.DraftTimerScreen
import es.alejandro.grandarbiter.ui.tools.HeadJudgeAnnouncementScreen
import es.alejandro.grandarbiter.ui.tools.BannedListsScreen
import es.alejandro.grandarbiter.ui.tools.QuickReferencesScreen
import es.alejandro.grandarbiter.ui.tools.QuickReferenceDetailScreen
import es.alejandro.grandarbiter.ui.settings.SettingsScreen
import es.alejandro.grandarbiter.ui.setup.SetUpScreen

sealed class Screen(val route: String) {
    object SetUp : Screen("setup")
    object Home : Screen("home")
    object DocumentReader : Screen("document_reader/{documentId}") {
        fun createRoute(documentId: String) = "document_reader/$documentId"
    }
    object CardSearch : Screen("card_search")
    object CardDetail : Screen("card_detail/{cardId}") {
        fun createRoute(cardId: String) = "card_detail/$cardId"
    }
    object Tools : Screen("tools")
    object DeckCounter : Screen("deck_counter")
    object JudgeTimer : Screen("judge_timer")
    object DraftTimer : Screen("draft_timer")
    object HeadJudgeAnnouncement : Screen("head_judge_announcement")
    object BannedLists : Screen("banned_lists")
    object QuickReferences : Screen("quick_references")
    object QuickReferenceDetail : Screen("quick_reference_detail/{referenceId}") {
        fun createRoute(referenceId: String) = "quick_reference_detail/$referenceId"
    }
    object Settings : Screen("settings")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onSetUpComplete: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.SetUp.route) {
            SetUpScreen(onSetUpComplete = onSetUpComplete)
        }
        composable(Screen.Home.route) { HomeScreen() }
        composable(Screen.DocumentReader.route) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            DocumentReaderScreen(documentId)
        }
        composable(Screen.CardSearch.route) { CardSearchScreen() }
        composable(Screen.CardDetail.route) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId") ?: ""
            CardDetailScreen(cardId)
        }
        composable(Screen.Tools.route) { ToolsScreen() }
        composable(Screen.DeckCounter.route) { DeckCounterScreen() }
        composable(Screen.JudgeTimer.route) { JudgeTimerScreen() }
        composable(Screen.DraftTimer.route) { DraftTimerScreen() }
        composable(Screen.HeadJudgeAnnouncement.route) { HeadJudgeAnnouncementScreen() }
        composable(Screen.BannedLists.route) { BannedListsScreen() }
        composable(Screen.QuickReferences.route) { QuickReferencesScreen() }
        composable(Screen.QuickReferenceDetail.route) { backStackEntry ->
            val referenceId = backStackEntry.arguments?.getString("referenceId") ?: ""
            QuickReferenceDetailScreen(referenceId)
        }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
