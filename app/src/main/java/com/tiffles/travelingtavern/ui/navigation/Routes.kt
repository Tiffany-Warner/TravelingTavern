package com.tiffles.travelingtavern.ui.navigation

import android.net.Uri

/** All navigation routes. Screens never build route strings themselves. */
object Routes {

    /** Top-level tabs shown in the bottom navigation bar. Each is a nested graph. */
    object Tab {
        const val CHATS = "tab/chats"
        const val CHARACTERS = "tab/characters"
        const val LOREBOOKS = "tab/lorebooks"
        const val ME = "tab/me"

        /** Start destination inside each tab graph. */
        const val CHATS_ROOT = "chats"
        const val CHARACTERS_ROOT = "characters"
        const val LOREBOOKS_ROOT = "lorebooks"
        const val ME_ROOT = "me"
    }

    /** Destinations that cover the whole screen and hide the bottom bar. */
    object FullScreen {
        const val CONNECT = "connect"
        const val ADVANCED = "advanced"

        /**
         * One character's chat; [ARG_CHAT_FILE] (SillyTavern's chat file name without `.jsonl`)
         * is optional — without it SillyTavern's current chat for the character opens.
         */
        object Chat {
            const val ARG_CHARACTER_ID = "characterId"
            const val ARG_CHAT_FILE = "chatFile"
            const val ROUTE = "chat/{$ARG_CHARACTER_ID}?$ARG_CHAT_FILE={$ARG_CHAT_FILE}"

            /** For Phase 8's notification tap: `travelingtavern://chat/<id>?chatFile=<file>`. */
            const val DEEP_LINK = "travelingtavern://chat/{$ARG_CHARACTER_ID}?$ARG_CHAT_FILE={$ARG_CHAT_FILE}"

            /**
             * Builds the route. SillyTavern character IDs are avatar file names (e.g.
             * `Seraphina.png`) and chat files contain spaces, so both are URL-encoded.
             */
            fun create(characterId: String, chatFile: String? = null): String {
                val base = "chat/${Uri.encode(characterId)}"
                return if (chatFile == null) base else "$base?$ARG_CHAT_FILE=${Uri.encode(chatFile)}"
            }
        }

        /** One lorebook's entries. Lorebook names are their IDs (and file names), so URL-encoded. */
        object LorebookDetail {
            const val ARG_NAME = "name"
            const val ROUTE = "lorebook/{$ARG_NAME}"

            fun create(name: String): String = "lorebook/${Uri.encode(name)}"
        }

        /** The entry editor; [NEW_ENTRY] as the uid opens an empty entry. */
        object LorebookEntry {
            const val ARG_NAME = "name"
            const val ARG_UID = "uid"
            const val NEW_ENTRY = -1
            const val ROUTE = "lorebook/{$ARG_NAME}/entry/{$ARG_UID}"

            fun create(name: String, uid: Int = NEW_ENTRY): String = "lorebook/${Uri.encode(name)}/entry/$uid"
        }

        /** The Personas list. [ARG_IN_CHAT]: opened from a chat, so picking also locks it to that chat. */
        object Personas {
            const val ARG_IN_CHAT = "inChat"
            const val ROUTE = "personas?$ARG_IN_CHAT={$ARG_IN_CHAT}"

            fun create(inChat: Boolean): String = "personas?$ARG_IN_CHAT=$inChat"
        }

        /** The persona editor; without [ARG_AVATAR_ID] it creates a new persona. */
        object PersonaEditor {
            const val ARG_AVATAR_ID = "avatarId"
            const val ROUTE = "persona?$ARG_AVATAR_ID={$ARG_AVATAR_ID}"

            fun create(avatarId: String? = null): String =
                if (avatarId == null) "persona" else "persona?$ARG_AVATAR_ID=${Uri.encode(avatarId)}"
        }
    }
}