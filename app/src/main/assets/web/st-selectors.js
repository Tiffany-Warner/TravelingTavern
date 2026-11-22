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
            /** (type, options, dryRun); also fires for quiet generations and dry runs. */
            generationStarted: 'GENERATION_STARTED',
            /** (text) once impersonate has written its result into #send_textarea. */
            impersonateReady: 'IMPERSONATE_READY',
            /** (fileName without `.jsonl`) after the server deleted a character's chat file. */
            chatDeleted: 'CHAT_DELETED',
            /** Events after which the latest message or its swipes may have changed. */
            messagesChanged: Object.freeze([
                'MESSAGE_SWIPED',
                'MESSAGE_DELETED',
                'MESSAGE_RECEIVED',
                'CHARACTER_MESSAGE_RENDERED',
                'USER_MESSAGE_RENDERED',
            ]),
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
            /** The open chat's file name, without `.jsonl` (`characters[this_chid].chat`). */
            getCurrentChatId: 'getCurrentChatId',
            /** Opens a chat file (name without `.jsonl`) of the selected character. */
            openCharacterChat: 'openCharacterChat',
            /** Clears and re-reads the open chat from the server; emits CHAT_CHANGED. */
            reloadCurrentChat: 'reloadCurrentChat',
            /** (id) deletes the whole message, swipes included; no popup without askConfirmation. */
            deleteMessage: 'deleteMessage',
            stopGeneration: 'stopGeneration',
            /** { to(event, direction, { forceMesId, forceSwipeId }) }: past the last swipe it generates. */
            swipe: 'swipe',
            chatMetadata: 'chatMetadata',
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
            name: 'name',
            text: 'mes',
            /** The shown swipe's date; the bridge's stale check compares it. */
            sendDate: 'send_date',
            swipes: 'swipes',
            swipeId: 'swipe_id',
        }),
        /** chat_metadata.tainted: false until the chat gets past its greeting. */
        chatMetadataTainted: 'tainted',
        /** Swipe direction value for context.swipe.to. */
        swipeRight: 'right',

        /** script.js export for Start new chat / Restart chat (not on getContext()). */
        scriptModulePath: '/script.js',
        doNewChat: 'doNewChat',
        /**
         * (characterId = index in characters, as a string; fileName without `.jsonl`). Deletes
         * the file and repoints the character's current chat, but leaves the open page alone;
         * returns nothing either way (success = CHAT_DELETED). See the 1.6c spec's Notes.
         */
        deleteCharacterChatByName: 'deleteCharacterChatByName',
        /** Clears the page and opens the character's newest chat left, or a new one (CHAT_CHANGED). */
        replaceCurrentChat: 'replaceCurrentChat',

        /** POST { avatar_url, simple: true } → [{ file_name, file_id }] for one character. */
        characterChatsApi: '/api/characters/chats',

        /** jQuery global: SillyTavern's handlers are jQuery ones, reached with trigger('click'). */
        jQuery: 'jQuery',

        /** The story chat (1.7). Verified against 1.19.0 (see the 1.7 spec's Notes). */
        chatScreen: Object.freeze({
            message: '.mes',
            messageIdAttr: 'mesid',
            isUserAttr: 'is_user',
            /** Present on a message while SillyTavern's inline editor is open in it. */
            editTextarea: '#curEditTextarea',
            /** Shown (inline display: flex) exactly while SillyTavern is generating. */
            stopButton: '#mes_stop',
            /** Buttons inside a message, clicked to run SillyTavern's own handlers. */
            buttons: Object.freeze({
                edit: '.mes_edit',
                branch: '.mes_create_branch',
                hide: '.mes_hide',
                unhide: '.mes_unhide',
            }),
            /** Options-menu items, clicked to run SillyTavern's own handlers. */
            options: Object.freeze({
                regenerate: '#option_regenerate',
                continue: '#option_continue',
                impersonate: '#option_impersonate',
            }),
            /** Taps on these keep their own behavior and don't open the message actions. */
            tapIgnore: 'a, img, video, audio, button, input, textarea, select, summary, details, ' +
                '.swipe_left, .swipe_right, .swipes-counter, .mes_buttons, .mes_edit_buttons, ' +
                '.mes_reasoning_header, .mes_img_controls',
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
            /**
             * POST, body `{}`: the saved settings. `settings` is settings.json as a string;
             * its `power_user` holds the personas. Needs getRequestHeaders().
             */
            settings: '/api/settings/get',
            settingsJson: 'settings',
            settingsPowerUser: 'power_user',
            /** SillyTavern saves settings 1 s after a change (saveSettingsDebounced, debounce_timeout.relaxed). */
            settingsSaveDelayMs: 1000,
            /**
             * Paths of SillyTavern's chat saves (lower case, no trailing slash): a character's chat
             * (`script.js` `saveChat`) and a group chat (`group-chats.js`). Verified in 1.19.1.
             */
            chatSaves: Object.freeze(['/api/chats/save', '/api/chats/group/save']),
            /**
             * Extension install and update (`src/endpoints/extensions.js`, verified in 1.19.1).
             * POST with getRequestHeaders(). Install body `{ url }` (no `branch`: the default
             * branch; no `global`: needs admin), `409` when the folder exists. Update body
             * `{ extensionName }`, answer field `isUpToDate` (the state before its pull), `404`
             * when not in the user's folder.
             */
            extensionInstall: '/api/extensions/install',
            extensionUpdate: '/api/extensions/update',
            extensionUpToDate: 'isUpToDate',
        }),
        /** Fields of one /api/chats/recent row. */
        recentChat: Object.freeze({
            characterId: 'avatar',
            /** The chat file name without `.jsonl` (`file_name` has it). */
            chatFile: 'file_id',
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

        /**
         * Personas (1.9). Verified against 1.19.0 source (see the 1.9 spec's Notes). Data is
         * in powerUser.context; `user_avatar` isn't on getContext(), so the bridge imports
         * SillyTavern's own personas module (a live binding: read it each time).
         */
        personas: Object.freeze({
            modulePath: '/scripts/personas.js',
            module: Object.freeze({
                /** The active persona's avatar id (reassigned: read it each time). */
                active: 'user_avatar',
                /** (avatarId): switches like a click in SillyTavern's list; emits PERSONA_CHANGED. */
                select: 'setUserAvatar',
                /** (state, 'chat'): chat_metadata.persona = the active persona. */
                setLock: 'setPersonaLockState',
            }),
            /** powerUserSettings fields. */
            descriptions: 'persona_descriptions',
            defaultPersona: 'default_persona',
            /** true: SillyTavern locks every picked persona to the open chat (see Notes). */
            autoLock: 'persona_auto_lock',
            descriptor: Object.freeze({ description: 'description', title: 'title' }),
            /**
             * Slash commands whose callbacks are called directly (SlashCommandParser.commands[name]
             * .callback(args, '')): SillyTavern's own create / update / delete, without the
             * parser, so `{{user}}` and quotes in a description stay as typed.
             */
            commands: Object.freeze({
                parser: 'SlashCommandParser',
                create: 'persona-create',
                update: 'persona-update',
                remove: 'persona-delete',
            }),
            thumbnailType: 'persona',
            events: Object.freeze([
                'PERSONA_CHANGED',
                'PERSONA_CREATED',
                'PERSONA_UPDATED',
                'PERSONA_RENAMED',
                'PERSONA_DELETED',
                'CHAT_CHANGED',
            ]),
        }),

        /**
         * The model connection (A7 Connect). The same buttons SillyTavern's auto-connect presses
         * (`RossAscends-mods.js` `RA_autoconnect`), by `main_api`; other APIs (e.g. `koboldhorde`)
         * have none. Each carries the `disabled` class while an attempt runs (`startStatusLoading()`
         * to `stopStatusLoading()`). Verified in 1.19.1's source (extension spec E15) and in the live
         * page (session lease app spec, Notes slice 8).
         */
        model: Object.freeze({
            /** getContext() members. */
            onlineStatus: 'onlineStatus',
            mainApi: 'mainApi',
            /** onlineStatus while not connected. */
            noConnection: 'no_connection',
            /** Key into context.event_types; emitted only when the status changes, with the new one. */
            statusChanged: 'ONLINE_STATUS_CHANGED',
            connectButtons: Object.freeze({
                kobold: '#api_button',
                novel: '#api_button_novel',
                textgenerationwebui: '#api_button_textgenerationwebui',
                openai: '#api_button_openai',
            }),
            connectingClass: 'disabled',
        }),

        /** selectCharacterById option: false opens the chat without the character editor panel. */
        selectOptions: Object.freeze({ switchMenu: false }),

        /** DOM nodes the bridge reads. */
        dom: Object.freeze({
            /** The message list; its scroll position settles once a chat is fully drawn. */
            chat: '#chat',
            /** Main chat input; send reads its value directly (`sendTextareaMessage`). */
            sendTextarea: '#send_textarea',
            /** Send button; its click handler runs SillyTavern's sendTextareaMessage(). */
            sendButton: '#send_but',
            /** Main column; the app's scroll-to-newest button is added in here. */
            sheld: '#sheld',
            /** SillyTavern's chat background; it sets an inline background-image when one is chosen. */
            background: '#bg1',
        }),

        /**
         * Programmatic text counts as typing after this event (bubbling): SillyTavern's own
         * listener resizes the input and saves the draft. SillyTavern fills the input the same
         * way itself (impersonate, restoring a draft). Verified against 1.19.0.
         */
        inputEvent: 'input',

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
        //   #form_sheld                           SillyTavern's input area (hidden: the app's bar replaces it)
        //   #et-portal (sibling of <body>)        EchoText's window, floating button and pop-ups
        //   .mes, .last_mes, .mes_block, .ch_name, .name_text, .timestamp, .mesIDDisplay,
        //   .mes_timer, .tokenCounterDisplay, .mes_buttons, .mesAvatarWrapper, .mes_text
        //                                         message markup restyled as cards
        //   .mes_reasoning_summary, .mes_reasoning_header, .mes_reasoning_arrow, .mes_reasoning_actions,
        //   .mes_reasoning                        reasoning bar (kept, can't open), its arrow, actions, text
        //   .swipe_right, .swipe_left, .swipes-counter   swipe arrows and counter
        //   .mes_edit_buttons, .mes_edit_done, .mes_edit_cancel   inline editor buttons
        //   .mes[is_user="true"], .mes[is_system="true"]   user / hidden messages
        //   #bg1                                  SillyTavern's chat background (inline background-image)
    });
})();
