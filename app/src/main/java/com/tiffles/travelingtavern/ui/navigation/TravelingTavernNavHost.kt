package com.tiffles.travelingtavern.ui.navigation

import android.content.Intent
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.activity.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
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
import androidx.navigation.navDeepLink
import com.tiffles.travelingtavern.ui.advanced.AdvancedScreen
import com.tiffles.travelingtavern.ui.characters.CharactersScreen
import com.tiffles.travelingtavern.ui.chat.ChatChromeHeight
import com.tiffles.travelingtavern.ui.chat.ChatScreen
import com.tiffles.travelingtavern.ui.chat.DefaultChatInputBarHeight
import com.tiffles.travelingtavern.ui.chats.ChatsScreen
import com.tiffles.travelingtavern.ui.components.SillyTavernWebView
import com.tiffles.travelingtavern.ui.connect.ConnectScreen
import com.tiffles.travelingtavern.ui.lease.LeaseGuard
import com.tiffles.travelingtavern.ui.lorebooks.LorebookDetailScreen
import com.tiffles.travelingtavern.ui.lorebooks.LorebookEntryScreen
import com.tiffles.travelingtavern.ui.lorebooks.LorebooksScreen
import com.tiffles.travelingtavern.ui.me.MeScreen
import com.tiffles.travelingtavern.ui.personas.PersonaEditorScreen
import com.tiffles.travelingtavern.ui.personas.PersonasScreen
import com.tiffles.travelingtavern.ui.protection.ExtensionPromptHost

/**
 * The app shell: a bottom navigation bar over four tab graphs, plus full-screen
 * destinations (Connect, Chat, Advanced, lorebook detail and entry, Personas and the persona
 * editor) that hide the bar. The bar also hides while
 * the keyboard is open.
 *
 * The one SillyTavern WebView lives here, behind everything, attached for the whole session
 * so the JS bridge keeps feeding the tabs. Every destination except Chat covers it with an
 * opaque surface; Chat is transparent below its top bar, which reveals the page.
 *
 * The WebView always has Chat's geometry (below the status bar and Chat's top bar, above
 * Chat's input bar and the navigation bar, clear of a display cutout), covered or not:
 * resizing it makes SillyTavern lay out the whole page again, and the WebView paints nothing
 * until it has — a visible blank on every tab ↔ Chat move. Only the keyboard and the input
 * bar growing with its text resize it, and only while Chat shows it.
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
    ChatDeepLinkHandling(navController = navController, serverSaved = startDestination != Routes.FullScreen.CONNECT)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedTab = currentDestination?.topLevelTab()
    val showBottomBar = selectedTab != null && !WindowInsets.isImeVisible
    val chatShowing = currentDestination?.route == Routes.FullScreen.Chat.ROUTE
    val openServerSettings = { navController.navigate(Routes.FullScreen.CONNECT) }
    val reselectEvents = remember { TabReselectEvents() }
    var inputBarHeight by remember { mutableStateOf(DefaultChatInputBarHeight) }

    // Over the whole Scaffold, tab bar included (A3).
    LeaseGuard(modifier = modifier) { leaseSnackbarHost ->
        CompositionLocalProvider(LocalTabReselectEvents provides reselectEvents) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = leaseSnackbarHost,
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
                            .padding(top = ChatChromeHeight, bottom = inputBarHeight)
                    )
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        // Consumed so screens that add imePadding() don't count the system bars twice.
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                    ) {
                        tabGraphs(navController = navController, onOpenServerSettings = openServerSettings)
                        fullScreenDestinations(
                            navController = navController,
                            onInputBarHeightChange = { inputBarHeight = it }
                        )
                    }
                }
            }
            // The one-time extension dialog (A8): drawn only while no scrim covers the app.
            ExtensionPromptHost()
        }
    }
}

/**
 * Deep links to a chat (`travelingtavern://chat/…`). With no server saved the link is dropped
 * and the app starts at Connect. While the app is running (`singleTop`), a new link is handed
 * to the NavController instead of starting a second Activity, without its task flags: with
 * `FLAG_ACTIVITY_NEW_TASK` (set by launchers, `am start` and notification taps)
 * `handleDeepLink` would restart the task and recreate the Activity instead of navigating.
 */
@Composable
private fun ChatDeepLinkHandling(navController: NavHostController, serverSaved: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    if (!serverSaved) activity.intent?.data = null
    DisposableEffect(activity, navController, serverSaved) {
        val listener = Consumer<Intent> { intent ->
            if (serverSaved && intent.data != null) navController.handleDeepLink(Intent(intent).apply { flags = 0 })
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
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

/**
 * Back from Chat. Opened from a list: back to it. Opened by a deep link (nothing under it but
 * the start destination, or a fresh task): the Chats tab.
 */
private fun NavHostController.leaveChat() {
    val previous = previousBackStackEntry?.destination
    if (previous?.topLevelTab() != null) {
        popBackStack()
        return
    }
    navigate(Routes.Tab.CHATS) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavGraphBuilder.tabGraphs(navController: NavHostController, onOpenServerSettings: () -> Unit) {
    val openChat = { characterId: String, chatFile: String? ->
        navController.navigate(Routes.FullScreen.Chat.create(characterId, chatFile))
    }
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
                onOpenChat = { characterId -> openChat(characterId, null) },
                onOpenAdvanced = { navController.navigate(Routes.FullScreen.ADVANCED) },
                onOpenServerSettings = onOpenServerSettings
            )
        }
    }
    navigation(startDestination = Routes.Tab.LOREBOOKS_ROOT, route = Routes.Tab.LOREBOOKS) {
        coveringComposable(Routes.Tab.LOREBOOKS_ROOT) {
            LorebooksScreen(
                onOpenLorebook = { name -> navController.navigate(Routes.FullScreen.LorebookDetail.create(name)) },
                onOpenServerSettings = onOpenServerSettings
            )
        }
    }
    navigation(startDestination = Routes.Tab.ME_ROOT, route = Routes.Tab.ME) {
        coveringComposable(Routes.Tab.ME_ROOT) { MeScreen() }
    }
}

private fun NavGraphBuilder.fullScreenDestinations(
    navController: NavHostController,
    onInputBarHeightChange: (Dp) -> Unit
) {
    coveringComposable(Routes.FullScreen.CONNECT) {
        ConnectScreen(onSaved = { navController.leaveConnectAfterSave() })
    }
    // Not covering: between its bars, Chat shows the WebView drawn behind the NavHost.
    composable(
        route = Routes.FullScreen.Chat.ROUTE,
        arguments = listOf(
            navArgument(Routes.FullScreen.Chat.ARG_CHARACTER_ID) { type = NavType.StringType },
            navArgument(Routes.FullScreen.Chat.ARG_CHAT_FILE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        deepLinks = listOf(navDeepLink { uriPattern = Routes.FullScreen.Chat.DEEP_LINK })
    ) {
        ChatScreen(
            onBack = { navController.leaveChat() },
            onInputBarHeightChange = onInputBarHeightChange,
            onOpenPersonas = { navController.navigate(Routes.FullScreen.Personas.create(inChat = true)) }
        )
    }
    coveringComposable(Routes.FullScreen.ADVANCED) { AdvancedScreen() }
    lorebookDestinations(navController)
    personaDestinations(navController)
}

private fun NavGraphBuilder.personaDestinations(navController: NavHostController) {
    coveringComposable(
        route = Routes.FullScreen.Personas.ROUTE,
        arguments = listOf(
            navArgument(Routes.FullScreen.Personas.ARG_IN_CHAT) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) {
        PersonasScreen(
            onClose = { navController.popBackStack() },
            onOpenEditor = { avatarId -> navController.navigate(Routes.FullScreen.PersonaEditor.create(avatarId)) },
            onOpenServerSettings = { navController.navigate(Routes.FullScreen.CONNECT) }
        )
    }
    coveringComposable(
        route = Routes.FullScreen.PersonaEditor.ROUTE,
        arguments = listOf(
            navArgument(Routes.FullScreen.PersonaEditor.ARG_AVATAR_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        PersonaEditorScreen(onBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.lorebookDestinations(navController: NavHostController) {
    coveringComposable(
        route = Routes.FullScreen.LorebookDetail.ROUTE,
        arguments = listOf(navArgument(Routes.FullScreen.LorebookDetail.ARG_NAME) { type = NavType.StringType })
    ) {
        LorebookDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenEntry = { lorebook, uid ->
                navController.navigate(
                    Routes.FullScreen.LorebookEntry.create(lorebook, uid ?: Routes.FullScreen.LorebookEntry.NEW_ENTRY)
                )
            },
            onOpenServerSettings = { navController.navigate(Routes.FullScreen.CONNECT) }
        )
    }
    coveringComposable(
        route = Routes.FullScreen.LorebookEntry.ROUTE,
        arguments = listOf(
            navArgument(Routes.FullScreen.LorebookEntry.ARG_NAME) { type = NavType.StringType },
            navArgument(Routes.FullScreen.LorebookEntry.ARG_UID) { type = NavType.IntType }
        )
    ) {
        LorebookEntryScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * A destination drawn over the WebView: an opaque surface that also consumes touches, so
 * nothing reaches the page underneath.
 */
private fun NavGraphBuilder.coveringComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable () -> Unit
) {
    composable(route, arguments) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
