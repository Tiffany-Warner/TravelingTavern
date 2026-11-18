package com.tiffles.travelingtavern.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiffles.travelingtavern.ui.advanced.AdvancedScreen
import com.tiffles.travelingtavern.ui.characters.CharactersScreen
import com.tiffles.travelingtavern.ui.chat.ChatChromeHeight
import com.tiffles.travelingtavern.ui.chat.ChatScreen
import com.tiffles.travelingtavern.ui.chats.ChatsScreen
import com.tiffles.travelingtavern.ui.components.SillyTavernWebView
import com.tiffles.travelingtavern.ui.connect.ConnectScreen
import com.tiffles.travelingtavern.ui.me.MeScreen

/**
 * The app shell: a bottom navigation bar over three tab graphs, plus full-screen
 * destinations (Connect, Chat, Advanced) that hide the bar. The bar also hides while
 * the keyboard is open.
 *
 * The one SillyTavern WebView lives here, behind everything, attached for the whole session
 * so the JS bridge keeps feeding the tabs. Every destination except Chat covers it with an
 * opaque surface; Chat is transparent below its top bar, which reveals the page.
 *
 * The WebView always has Chat's geometry (below the status bar and Chat's top bar, above
 * the navigation bar, clear of a display cutout), covered or not: resizing it makes SillyTavern lay out the whole page
 * again, and the WebView paints nothing until it has — a visible blank on every tab ↔ Chat
 * move. Only the keyboard resizes it, and only while Chat shows it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TravelingTavernNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestinationViewModel: StartDestinationViewModel = hiltViewModel()
) {
    // Blank until the saved server config has been read (a few ms) so the graph
    // starts on the right screen instead of flashing Connect.
    val startDestination = startDestinationViewModel.startDestination
        .collectAsStateWithLifecycle().value ?: return

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedTab = currentDestination?.topLevelTab()
    val showBottomBar = selectedTab != null && !WindowInsets.isImeVisible
    val chatShowing = currentDestination?.route == Routes.FullScreen.Chat.ROUTE
    val openServerSettings = { navController.navigate(Routes.FullScreen.CONNECT) }
    val reselectEvents = remember { TabReselectEvents() }

    CompositionLocalProvider(LocalTabReselectEvents provides reselectEvents) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    TravelingTavernBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            if (tab == selectedTab) {
                                reselectEvents.reselect(tab)
                            } else {
                                navController.navigateToTab(tab)
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            // The WebView ignores innerPadding on purpose (see above); it sits inside the
            // Scaffold so the Scaffold's surface, which swallows touches, stays behind it.
            Box(modifier = Modifier.fillMaxSize()) {
                SillyTavernWebView(
                    revealed = chatShowing,
                    onOpenServerSettings = openServerSettings,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                        .padding(top = ChatChromeHeight)
                )
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    // Consumed so screens that add imePadding() don't count the system bars twice.
                    modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                ) {
                    tabGraphs(navController = navController, onOpenServerSettings = openServerSettings)
                    fullScreenDestinations(navController = navController)
                }
            }
        }
    }
}

/** Finds which tab (if any) the destination belongs to, by walking its parent graphs. */
private fun NavDestination.topLevelTab(): TopLevelTab? =
    TopLevelTab.entries.firstOrNull { tab -> hierarchy.any { it.route == tab.graphRoute } }

/**
 * Switches tabs, keeping each tab's own back stack (saveState / restoreState). Pops to the
 * Chats graph rather than the root start destination, which may be Connect on first launch.
 */
private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graphRoute) {
        popUpTo(Routes.Tab.CHATS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * After a successful save on Connect. Opened from inside the app (an error state): go back
 * to where the user was. Opened as the first screen: go to Chats and drop Connect.
 */
private fun NavHostController.leaveConnectAfterSave() {
    if (previousBackStackEntry != null) {
        popBackStack()
        return
    }
    navigate(Routes.Tab.CHATS) {
        popUpTo(Routes.FullScreen.CONNECT) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.tabGraphs(navController: NavHostController, onOpenServerSettings: () -> Unit) {
    val openChat = { characterId: String -> navController.navigate(Routes.FullScreen.Chat.create(characterId)) }
    navigation(startDestination = Routes.Tab.CHATS_ROOT, route = Routes.Tab.CHATS) {
        coveringComposable(Routes.Tab.CHATS_ROOT) {
            ChatsScreen(
                onOpenChat = openChat,
                onBrowseCharacters = { navController.navigateToTab(TopLevelTab.CHARACTERS) },
                onOpenServerSettings = onOpenServerSettings
            )
        }
    }
    navigation(startDestination = Routes.Tab.CHARACTERS_ROOT, route = Routes.Tab.CHARACTERS) {
        coveringComposable(Routes.Tab.CHARACTERS_ROOT) {
            CharactersScreen(
                onOpenChat = openChat,
                onOpenAdvanced = { navController.navigate(Routes.FullScreen.ADVANCED) },
                onOpenServerSettings = onOpenServerSettings
            )
        }
    }
    navigation(startDestination = Routes.Tab.ME_ROOT, route = Routes.Tab.ME) {
        coveringComposable(Routes.Tab.ME_ROOT) { MeScreen() }
    }
}

private fun NavGraphBuilder.fullScreenDestinations(navController: NavHostController) {
    coveringComposable(Routes.FullScreen.CONNECT) {
        ConnectScreen(onSaved = { navController.leaveConnectAfterSave() })
    }
    // Not covering: below its top bar, Chat shows the WebView drawn behind the NavHost.
    composable(
        route = Routes.FullScreen.Chat.ROUTE,
        arguments = listOf(
            navArgument(Routes.FullScreen.Chat.ARG_CHARACTER_ID) { type = NavType.StringType }
        )
    ) {
        ChatScreen(onBack = { navController.popBackStack() })
    }
    coveringComposable(Routes.FullScreen.ADVANCED) { AdvancedScreen() }
}

/**
 * A destination drawn over the WebView: an opaque surface that also consumes touches, so
 * nothing reaches the page underneath.
 */
private fun NavGraphBuilder.coveringComposable(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
