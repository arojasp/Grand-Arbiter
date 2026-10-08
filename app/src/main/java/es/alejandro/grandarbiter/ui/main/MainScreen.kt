package es.alejandro.grandarbiter.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
fun MainScreen() {
    MainScreenContent(startDestination = Screen.Home.route)
}

@Composable
fun MainScreenContent(
    startDestination: String,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavigationItem(R.string.nav_home, Screen.Home.route, Icons.Default.Home),
        NavigationItem(R.string.nav_card_search, Screen.CardSearch.route, Icons.Default.Search),
        NavigationItem(R.string.nav_tools, Screen.Tools.route, Icons.Default.Build)
    )

    Scaffold(
        bottomBar = {
            val showBottomBar = items.any { item ->
                currentDestination?.hierarchy?.any { it.route == item.route } == true
            }

            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        val title = stringResource(item.titleRes)
                        NavigationBarItem(
                            icon = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(item.icon, contentDescription = title)
                                    Text(title)
                                }
                            },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            startDestination = startDestination,
        )
    }
}


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
