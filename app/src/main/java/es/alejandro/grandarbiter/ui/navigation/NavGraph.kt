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
import es.alejandro.grandarbiter.ui.tools.banned.BannedListsScreen
import es.alejandro.grandarbiter.ui.tools.deckcounter.DeckCounterScreen
import es.alejandro.grandarbiter.ui.tools.DraftTimerScreen
import es.alejandro.grandarbiter.ui.tools.announcement.HeadJudgeAnnouncementScreen
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceDetailScreen
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceEditScreen
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferenceRoutes
import es.alejandro.grandarbiter.ui.tools.quickrefs.QuickReferencesScreen
import es.alejandro.grandarbiter.ui.tools.quickrefs.ARG_REFERENCE
import es.alejandro.grandarbiter.ui.tools.quickrefs.ARG_REFERENCE_DOCUMENT
import es.alejandro.grandarbiter.ui.tools.ToolsScreen
import es.alejandro.grandarbiter.ui.reader.ARG_TERM
import es.alejandro.grandarbiter.ui.reader.GlossaryRoutes
import es.alejandro.grandarbiter.ui.reader.GlossaryScreen
import es.alejandro.grandarbiter.ui.tools.releasenotes.ReleaseNotesScreen

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
    object DraftTimer : Screen("draft_timer")
    object HeadJudgeAnnouncement : Screen("head_judge_announcement")
    object BannedLists : Screen("banned_lists")
    object ReleaseNotes : Screen("release_notes")
    object QuickReferences : Screen(QuickReferenceRoutes.LIST)
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
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            ) 
        }
        composable(Screen.DocumentReader.route) {
            ReaderContentsScreen(
                onBack = { navController.popBackStack() },
                onOpenPage = { ref -> navController.navigate(ReaderRoutes.page(ref)) },
                onOpenGlossary = { term -> navController.navigate(GlossaryRoutes.glossary(term)) },
            )
        }
        composable(
            GlossaryRoutes.GLOSSARY,
            arguments = listOf(navArgument(ARG_TERM) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            GlossaryScreen(
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
            CardSearchScreen(
                onOpenCard = { oracleId -> navController.navigate(Screen.CardDetail.createRoute(oracleId)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.CardDetail.route) {
            CardDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Tools.route) {
            ToolsScreen(
                onOpen = { route -> navController.navigate(route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.DeckCounter.route) { DeckCounterScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.DraftTimer.route) { DraftTimerScreen() }
        composable(Screen.HeadJudgeAnnouncement.route) {
            HeadJudgeAnnouncementScreen(
                onBack = { navController.popBackStack() },
                onOpenRules = { ref -> navController.navigate(ReaderRoutes.page(ref)) },
            )
        }
        composable(Screen.ReleaseNotes.route) { ReleaseNotesScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.BannedLists.route) {
            BannedListsScreen(
                onBack = { navController.popBackStack() },
                onOpenCard = { oracleId -> navController.navigate(Screen.CardDetail.createRoute(oracleId)) },
            )
        }
        composable(Screen.QuickReferences.route) {
            QuickReferencesScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(QuickReferenceRoutes.detail(id)) },
                onCreate = { document -> navController.navigate(QuickReferenceRoutes.create(document)) },
            )
        }
        composable(
            QuickReferenceRoutes.DETAIL,
            arguments = listOf(navArgument(ARG_REFERENCE) { type = NavType.StringType }),
        ) {
            QuickReferenceDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(QuickReferenceRoutes.edit(id)) },
                onOpenPage = { ref -> navController.navigate(ReaderRoutes.page(ref)) },
            )
        }
        composable(
            QuickReferenceRoutes.EDIT,
            arguments = listOf(
                navArgument(ARG_REFERENCE) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(ARG_REFERENCE_DOCUMENT) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            QuickReferenceEditScreen(
                onClose = { navController.popBackStack() },
                onSaved = { id, created ->
                    if (created) {
                        // New reference: show it, and Back from there returns to the list.
                        navController.navigate(QuickReferenceRoutes.detail(id)) {
                            popUpTo(QuickReferenceRoutes.EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                // The detail behind the editor is gone too: back to the list.
                onDeleted = { navController.popBackStack(QuickReferenceRoutes.LIST, inclusive = false) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
