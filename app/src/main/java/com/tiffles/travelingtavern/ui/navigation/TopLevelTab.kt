package com.tiffles.travelingtavern.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tiffles.travelingtavern.R

/** One entry in the bottom navigation bar. */
enum class TopLevelTab(
    val graphRoute: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int
) {
    CHATS(
        graphRoute = Routes.Tab.CHATS,
        labelRes = R.string.tab_chats,
        iconRes = R.drawable.ic_chat_outline,
        selectedIconRes = R.drawable.ic_chat_filled
    ),
    CHARACTERS(
        graphRoute = Routes.Tab.CHARACTERS,
        labelRes = R.string.tab_characters,
        iconRes = R.drawable.ic_people_outline,
        selectedIconRes = R.drawable.ic_people_filled
    ),
    ME(
        graphRoute = Routes.Tab.ME,
        labelRes = R.string.tab_me,
        iconRes = R.drawable.ic_person_outline,
        selectedIconRes = R.drawable.ic_person_filled
    )
}