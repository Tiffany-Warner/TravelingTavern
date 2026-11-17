package com.tiffles.travelingtavern.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiffles.travelingtavern.ui.advanced.AdvancedScreen
import com.tiffles.travelingtavern.ui.chat.ChatScreen
import com.tiffles.travelingtavern.ui.connect.ConnectScreen
import com.tiffles.travelingtavern.ui.home.HomeScreen
import com.tiffles.travelingtavern.ui.settings.SettingsScreen

/**
 * The app's navigation graph. Home is the root; Chat, Settings, and Advanced are
 * pushed on top of it. There is no bottom navigation.
 */
@Composable
fun TravelingTavernNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.CONNECT) {
            ConnectScreen()
        }
        composable(Routes.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(
            route = Routes.Chat.ROUTE,
            arguments = listOf(navArgument(Routes.Chat.ARG_CHARACTER_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString(Routes.Chat.ARG_CHARACTER_ID).orEmpty()
            ChatScreen(characterId = characterId)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
        composable(Routes.ADVANCED) {
            AdvancedScreen()
        }
    }
}