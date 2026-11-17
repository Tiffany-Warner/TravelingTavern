package com.tiffles.travelingtavern.ui.navigation

import android.net.Uri

/** All navigation routes. Screens never build route strings themselves. */
object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADVANCED = "advanced"

    object Chat {
        const val ARG_CHARACTER_ID = "characterId"
        const val ROUTE = "chat/{$ARG_CHARACTER_ID}"

        /**
         * Builds the route for one character. SillyTavern character IDs are avatar
         * file names (e.g. `Seraphina.png`), so the ID is URL-encoded.
         */
        fun create(characterId: String): String = "chat/${Uri.encode(characterId)}"
    }
}