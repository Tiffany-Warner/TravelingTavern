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
            getRequestHeaders: 'getRequestHeaders',
        }),
        character: Object.freeze({
            id: 'avatar',
            name: 'name',
            lastChatAt: 'date_last_chat',
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

        /** selectCharacterById option: false opens the chat without the character editor panel. */
        selectOptions: Object.freeze({ switchMenu: false }),

        /** DOM nodes the bridge reads. */
        dom: Object.freeze({
            /** The message list; its scroll position settles once a chat is fully drawn. */
            chat: '#chat',
        }),

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
    });
})();
