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
    });
})();
