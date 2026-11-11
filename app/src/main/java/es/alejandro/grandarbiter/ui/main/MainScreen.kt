package es.alejandro.grandarbiter.ui.main

import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import es.alejandro.grandarbiter.ui.main.review.ReviewPrompter
import es.alejandro.grandarbiter.ui.navigation.LOOKUP_ROUTES
import androidx.navigation.NavHostController
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import es.alejandro.grandarbiter.NavigationItem
import es.alejandro.grandarbiter.R
import es.alejandro.grandarbiter.domain.model.ThemeMode
import es.alejandro.grandarbiter.ui.navigation.AppNavGraph
import es.alejandro.grandarbiter.ui.navigation.Screen
import es.alejandro.grandarbiter.ui.theme.GrandArbiterTheme

@Composable
fun MainScreen(snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
    MainScreenContent(startDestination = Screen.Home.route, snackbarHostState = snackbarHostState, reviews = koinInject())
}

@Composable
fun MainScreenContent(
    startDestination: String,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    reviews: ReviewPrompter? = null,
) {
    val navController = rememberNavController()
    if (reviews != null) ReviewOnLookups(navController, reviews)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem(R.string.nav_home, Screen.Home.route, Icons.Default.Home),
        NavigationItem(R.string.nav_card_search, Screen.CardSearch.route, Icons.Default.Search),
        NavigationItem(R.string.nav_tools, Screen.Tools.route, Icons.Default.Build)
    )

    val showNavigation = items.any { item -> currentDestination?.hierarchy?.any { it.route == item.route } == true }
    // Phone turned sideways: the bar moves to the side (a rail), the content keeps its height.
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height
    val isSelected = { item: NavigationItem -> currentDestination?.hierarchy?.any { it.route == item.route } == true }
    val open = { item: NavigationItem ->
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },   // app-wide: rules updated, app update ready
        // No top inset here: each screen's TopAppBar pads for the status bar itself (and draws behind it).
        // Taking it here too put the status bar height twice above every title.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (showNavigation && !landscape) {
                NavigationBar {
                    items.forEach { item ->
                        val title = stringResource(item.titleRes)
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = null) },   // the label names it
                            label = { Text(title) },
                            selected = isSelected(item),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            onClick = { open(item) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // consumeWindowInsets: the screens' own Scaffolds don't add the bottom/side insets a second time.
        Row(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize()) {
            if (showNavigation && landscape) {
                NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {   // the Scaffold already applied the insets
                    Spacer(Modifier.weight(1f))
                    items.forEach { item ->
                        val title = stringResource(item.titleRes)
                        NavigationRailItem(
                            icon = { Icon(item.icon, contentDescription = null) },   // the label names it
                            label = { Text(title) },
                            selected = isSelected(item),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            onClick = { open(item) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            // Wide screens: content centered at a readable width (no 900 dp lines of rules text).
            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                AppNavGraph(
                    navController = navController,
                    modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
                    startDestination = startDestination,
                )
            }
        }
    }
}

/** Widest the screens get; phones in portrait are narrower, so it only applies sideways. */
private val MAX_CONTENT_WIDTH = 640.dp


@Preview(showBackground = true)
@Composable
fun MainScreenDarkPreview() {
    GrandArbiterTheme(themeMode = ThemeMode.DARK) {
        MainScreenContent(startDestination = Screen.Home.route)
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenLightPreview() {
    GrandArbiterTheme(themeMode = ThemeMode.LIGHT) {
        MainScreenContent(startDestination = Screen.Home.route)
    }
}

/**
 * Counts each lookup screen opened (once per visit, not when coming back to it) and, when the
 * judge goes back from one, lets [reviews] decide whether to ask for a review. A screen opened
 * from a lookup (a rule linked from a card) doesn't count as leaving it.
 */
@Composable
private fun ReviewOnLookups(navController: NavHostController, reviews: ReviewPrompter) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(navController) {
        val seen = mutableSetOf<String>()
        var previous: String? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val isNew = seen.add(entry.id)
            val route = entry.destination.route
            when {
                route in LOOKUP_ROUTES -> if (isNew) scope.launch { reviews.lookupOpened() }
                previous in LOOKUP_ROUTES && !isNew && activity != null -> scope.launch { reviews.lookupClosed(activity) }
            }
            previous = route
        }
    }
}