package es.alejandro.grandarbiter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import es.alejandro.grandarbiter.ui.cardsearch.CardDetailScreen
import es.alejandro.grandarbiter.ui.cardsearch.CardSearchScreen
import es.alejandro.grandarbiter.ui.home.HomeScreen
import es.alejandro.grandarbiter.ui.reader.ARG_ANCHOR
import es.alejandro.grandarbiter.ui.reader.ReaderContentsScreen
import es.alejandro.grandarbiter.ui.reader.ReaderPageScreen
import es.alejandro.grandarbiter.ui.reader.ReaderRoutes
import es.alejandro.grandarbiter.ui.settings.SettingsScreen
import es.alejandro.grandarbiter.ui.tools.BannedListsScreen
import es.alejandro.grandarbiter.ui.tools.DeckCounterScreen
import es.alejandro.grandarbiter.ui.tools.DraftTimerScreen
import es.alejandro.grandarbiter.ui.tools.HeadJudgeAnnouncementScreen
import es.alejandro.grandarbiter.ui.tools.JudgeTimerScreen
import es.alejandro.grandarbiter.ui.tools.QuickReferenceDetailScreen
import es.alejandro.grandarbiter.ui.tools.QuickReferencesScreen
import es.alejandro.grandarbiter.ui.tools.ToolsScreen

sealed class Screen(val route: String) {
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
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable(Screen.Home.route) { 
            HomeScreen(
                onNavigateToDocument = { documentId ->
                    navController.navigate(Screen.DocumentReader.createRoute(documentId))
                }
            ) 
        }
        composable(Screen.DocumentReader.route) {
            ReaderContentsScreen(
                onBack = { navController.popBackStack() },
                onOpenPage = { ref -> navController.navigate(ReaderRoutes.page(ref)) },
            )
        }
        composable(
            route = ReaderRoutes.PAGE,
            arguments = listOf(navArgument(ARG_ANCHOR) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            ReaderPageScreen(
                onBack = { navController.popBackStack() },
                onOpenPage = { ref -> navController.navigate(ReaderRoutes.page(ref)) },
            )
        }
        composable(Screen.CardSearch.route) {
            CardSearchScreen(onOpenCard = { oracleId -> navController.navigate(Screen.CardDetail.createRoute(oracleId)) })
        }
        composable(Screen.CardDetail.route) {
            CardDetailScreen(onBack = { navController.popBackStack() })
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
