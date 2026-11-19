// Every SillyTavern / EchoText DOM selector and global name lives here, and
// nowhere else, so a SillyTavern update is a one-file fix.
// Verified against SillyTavern 1.19.0 (see context/current-feature.md, Phase 1.4 notes).
(function () {
    'use strict';
    if (window.__ttSelectors) return;

    window.__ttSelectors = Object.freeze({
        /** SillyTavern's public context, or undefined while the app is still loading. */
        getContext: function () {
            const st = window.SillyTavern;
            return st && typeof st.getContext === 'function' ? st.getContext() : undefined;
        },

        /** Keys into context.event_types (values are looked up at runtime). */
        events: Object.freeze({
            ready: 'APP_READY',
            chatChanged: 'CHAT_CHANGED',
            messageReceived: 'MESSAGE_RECEIVED',
            /** Events after which the character list (or a last-chat time) may have changed. */
            charactersChanged: Object.freeze([
                'CHARACTER_EDITED',
                'CHARACTER_DELETED',
                'CHARACTER_RENAMED',
                'CHARACTER_DUPLICATED',
                'CHARACTER_PAGE_LOADED',
                'CHAT_CREATED',
                'CHAT_CHANGED',
                'MESSAGE_RECEIVED',
            ]),
        }),

        /** context.* members and character fields. */
        context: Object.freeze({
            eventSource: 'eventSource',
            eventTypes: 'event_types',
            characters: 'characters',
            characterId: 'characterId',
            chat: 'chat',
            selectCharacterById: 'selectCharacterById',
            getThumbnailUrl: 'getThumbnailUrl',
            /** Re-reads the character list from the server (/api/characters/all). */
            getCharacters: 'getCharacters',
            getRequestHeaders: 'getRequestHeaders',
            getTokenCountAsync: 'getTokenCountAsync',
            saveSettingsDebounced: 'saveSettingsDebounced',
        }),
        character: Object.freeze({
            id: 'avatar',
            name: 'name',
            lastChatAt: 'date_last_chat',
            /** The card file's ctimeMs: changes whenever the card or its picture is written. */
            changedAt: 'date_added',
        }),
        message: Object.freeze({
            isUser: 'is_user',
            isSystem: 'is_system',
            /** Set on group chat messages: the avatar of the character who spoke. */
            originalAvatar: 'original_avatar',
        }),
        thumbnailType: 'avatar',

        /** Full-size avatar: this path + the URL-encoded avatar file name. */
        fullAvatarPath: '/characters/',

        /**
         * POST endpoint behind SillyTavern's welcome-screen "Recent Chats": one row per chat
         * file, newest first. Body `{ max }`. Needs getRequestHeaders() (CSRF token).
         */
        api: Object.freeze({
            recentChats: '/api/chats/recent',
            recentChatsMax: 100,
        }),
        /** Fields of one /api/chats/recent row. */
        recentChat: Object.freeze({
            characterId: 'avatar',
            lastMessage: 'mes',
            lastMessageAt: 'last_mes',
            messageCount: 'chat_items',
        }),

        /**
         * World Info (lorebooks). Most of it isn't on getContext(), so the bridge imports
         * SillyTavern's own module — the same instance its UI uses. Verified against 1.19.0
         * (see the 1.6b spec's Notes).
         */
        worldInfo: Object.freeze({
            modulePath: '/scripts/world-info.js',
            /** utils.js export that turns a name into the file name SillyTavern saves it under. */
            utilsPath: '/scripts/utils.js',
            sanitizeName: 'getSanitizedFilename',
            /** Module exports. `names` and `globalSelected` are reassigned: read them each time. */
            module: Object.freeze({
                names: 'world_names',
                globalSelected: 'selected_world_info',
                settings: 'world_info',
                cache: 'worldInfoCache',
                load: 'loadWorldInfo',
                save: 'saveWorldInfo',
                create: 'createNewWorldInfo',
                remove: 'deleteWorldInfo',
                createEntry: 'createWorldInfoEntry',
                removeEntry: 'deleteWorldInfoEntry',
                updateList: 'updateWorldInfoList',
            }),
            /** world_info.charLore: [{ name: avatar without extension, extraBooks: [names] }]. */
            charLore: 'charLore',
            charLoreName: 'name',
            charLoreBooks: 'extraBooks',
            /** A character's primary lorebook: character.data.extensions.world. */
            characterPrimary: Object.freeze(['data', 'extensions', 'world']),
            /** Global on/off: SillyTavern's own multi-select (option value = index into names). */
            globalSelect: '#world_info',
            entries: 'entries',
            entry: Object.freeze({
                uid: 'uid',
                title: 'comment',
                keys: 'key',
                content: 'content',
                disabled: 'disable',
                displayIndex: 'displayIndex',
            }),
            /** Event keys after which the lorebook list may have changed. */
            events: Object.freeze(['WORLDINFO_UPDATED', 'WORLDINFO_SETTINGS_UPDATED']),
        }),

        /** power_user (getContext().powerUserSettings) fields for persona lorebooks. */
        powerUser: Object.freeze({
            context: 'powerUserSettings',
            personaNames: 'personas',
            personaDescriptions: 'persona_descriptions',
            personaLorebook: 'lorebook',
            currentPersonaLorebook: 'persona_description_lorebook',
        }),

        /** selectCharacterById option: false opens the chat without the character editor panel. */
        selectOptions: Object.freeze({ switchMenu: false }),

        /** DOM nodes the bridge reads. */
        dom: Object.freeze({
            /** The message list; its scroll position settles once a chat is fully drawn. */
            chat: '#chat',
            /** Main chat input; send reads its value directly (`sendTextareaMessage`). */
            sendTextarea: '#send_textarea',
            /** Right-hand buttons of the input row; the in-app mic goes in here. */
            rightSendForm: '#rightSendForm',
            /** Send button; the in-app mic sits right before it. */
            sendButton: '#send_but',
        }),

        /**
         * Programmatic text counts as typing after this event (bubbling): SillyTavern's own
         * listener resizes the input and saves the draft. SillyTavern fills the input the same
         * way itself (impersonate, restoring a draft). Verified against 1.19.0.
         */
        inputEvent: 'input',

        /** Classes the in-app mic button borrows so it looks like the row's other buttons. */
        micButtonClasses: 'fa-solid fa-microphone interactable',

        /** Body class that turns on clean-theme.css. */
        cleanClass: 'tt-clean',

        // DOM ids used by clean-theme.css (CSS can't import names from here, so they are
        // listed for reference; keep both files in sync):
        //   #top-bar, #top-settings-holder        top toolbar
        //   #left-nav-panel                       drawer: AI response configuration
        //   #rm_api_block                         drawer: API connections
        //   #AdvancedFormatting                   drawer: advanced formatting
        //   #WorldInfo                            drawer: World Info
        //   #user-settings-block                  drawer: user settings
        //   #Backgrounds                          drawer: backgrounds
        //   #rm_extensions_block                  drawer: extensions
        //   #PersonaManagement                    drawer: persona management
        //   #right-nav-panel                      drawer: character management
        //   #extensionTopBar                      TopInfoBar extension's chat bar
        //   #chat .drawer-opener                  in-chat shortcut buttons that open a drawer
        //   #sheld, #chat, .mes_text              chat column, message list, message text
        //   #send_textarea, #leftSendForm, #rightSendForm   message input row
        //   #et-portal (sibling of <body>)        EchoText's window, floating button and pop-ups
        //   #microphone_button                    Speech Recognition extension's mic (hidden in the app)
        //   #tt-mic, .tt-mic-listening            the app's own mic button (added by bridge.js)
    });
})();
