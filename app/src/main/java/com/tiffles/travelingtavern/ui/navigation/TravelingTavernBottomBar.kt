package com.tiffles.travelingtavern.ui.navigation

import android.content.res.Configuration
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** The three-tab bottom navigation bar. Labels are always shown. */
@Composable
fun TravelingTavernBottomBar(
    selectedTab: TopLevelTab?,
    onTabSelected: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        TopLevelTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) tab.selectedIconRes else tab.iconRes),
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(tab.labelRes)) },
                alwaysShowLabel = true
            )
        }
    }
}

@Preview(name = "Chats · light", showBackground = true)
@Preview(name = "Chats · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomBarChatsPreview() {
    TravelingTavernTheme {
        TravelingTavernBottomBar(selectedTab = TopLevelTab.CHATS, onTabSelected = {})
    }
}

@Preview(name = "Characters · light", showBackground = true)
@Preview(name = "Characters · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomBarCharactersPreview() {
    TravelingTavernTheme {
        TravelingTavernBottomBar(selectedTab = TopLevelTab.CHARACTERS, onTabSelected = {})
    }
}

@Preview(name = "Me · light", showBackground = true)
@Preview(name = "Me · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomBarMePreview() {
    TravelingTavernTheme {
        TravelingTavernBottomBar(selectedTab = TopLevelTab.ME, onTabSelected = {})
    }
}