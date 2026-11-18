// TravelingTavern JS bridge — injected into SillyTavern by the app after every page load.
// Posts typed JSON messages to Kotlin through the `TravelingTavern` web message listener
// and exposes window.__ttBridge for Kotlin to call. SillyTavern names come only from
// window.__ttSelectors (st-selectors.js).
(function () {
    'use strict';
    if (window.__ttBridge) return;

    const VERSION = 1;
    const READY_TIMEOUT_MS = 60000;
    const READY_POLL_MS = 250;
    const CHARACTERS_DEBOUNCE_MS = 300;
    const PREVIEW_MAX_CHARS = 200;
    const SETTLE_POLL_MS = 100;
    const SETTLE_MAX_MS = 2000;
    const S = window.__ttSelectors;
    const port = window.TravelingTavern;

    function post(type, payload) {
        if (!port) return;
        const message = Object.assign({ v: VERSION, type: type }, payload || {});
        try {
            port.postMessage(JSON.stringify(message));
        } catch (e) {
            // Nothing else to report to: the channel itself failed.
        }
    }

    function postError(message) {
        post('error', { message: String(message) });
    }

    function context() {
        return S.getContext();
    }

    function eventName(ctx, key) {
        return ctx[S.context.eventTypes][key];
    }

    function characterList(ctx) {
        return ctx[S.context.characters] || [];
    }

    function toSummary(ctx, character) {
        const id = character[S.character.id];
        const thumbnail = ctx[S.context.getThumbnailUrl](S.thumbnailType, id);
        const lastChat = Number(character[S.character.lastChatAt]);
        return {
            id: id,
            name: character[S.character.name] || '',
            avatarUrl: new URL(thumbnail, location.href).href,
            fullAvatarUrl: new URL(S.fullAvatarPath + encodeURIComponent(id), location.href).href,
            lastChatAt: lastChat > 0 ? Math.floor(lastChat) : null,
        };
    }

    function sendCharacters() {
        const ctx = context();
        if (!ctx) return;
        const items = characterList(ctx)
            .filter(function (c) { return c && c[S.character.id]; })
            .map(function (c) { return toSummary(ctx, c); });
        post('characters', { items: items });
    }

    /** One /api/chats/recent row → { characterId, lastMessage, lastMessageAt, messageCount }. */
    function toRecentChat(row) {
        const F = S.recentChat;
        const count = Number(row[F.messageCount]) || 0;
        const text = typeof row[F.lastMessage] === 'string' ? row[F.lastMessage] : '';
        const at = Date.parse(row[F.lastMessageAt]);
        return {
            characterId: row[F.characterId],
            // An empty chat reports a placeholder text, not a message.
            lastMessage: count > 0 && text ? text.slice(0, PREVIEW_MAX_CHARS) : null,
            lastMessageAt: isNaN(at) ? null : at,
            messageCount: count,
        };
    }

    /** Posts `recentChats`: the chats SillyTavern's welcome screen lists, with last messages. */
    function sendRecentChats() {
        const ctx = context();
        if (!ctx) return;
        fetch(S.api.recentChats, {
            method: 'POST',
            headers: ctx[S.context.getRequestHeaders](),
            body: JSON.stringify({ max: S.api.recentChatsMax }),
        })
            .then(function (response) {
                if (!response.ok) throw new Error('HTTP ' + response.status);
                return response.json();
            })
            .then(function (rows) {
                const items = (Array.isArray(rows) ? rows : [])
                    // Group chats have no single character avatar.
                    .filter(function (row) { return row && typeof row[S.recentChat.characterId] === 'string'; })
                    .map(toRecentChat);
                post('recentChats', { items: items });
            })
            .catch(function (e) {
                postError('Recent chats failed: ' + e);
            });
    }

    function sendLists() {
        sendCharacters();
        sendRecentChats();
    }

    let charactersTimer = null;
    function scheduleCharacters() {
        clearTimeout(charactersTimer);
        charactersTimer = setTimeout(sendLists, CHARACTERS_DEBOUNCE_MS);
    }

    /** Avatar file name of the open character, or null (no character, or a group chat). */
    function currentCharacterId(ctx) {
        const index = ctx[S.context.characterId];
        const character = index === undefined ? undefined : characterList(ctx)[index];
        return character ? character[S.character.id] : null;
    }

    /**
     * Calls [done] once the message list has stopped moving: SillyTavern keeps growing and
     * scrolling a long chat (images, long messages) for a while after drawing it, and the
     * WebView shows blank tiles until it catches up. Gives up waiting after SETTLE_MAX_MS.
     */
    function whenChatSettled(done) {
        const chat = document.querySelector(S.dom.chat);
        const started = Date.now();
        let previous = null;
        function check() {
            requestAnimationFrame(function () {
                const current = chat ? chat.scrollTop + ':' + chat.scrollHeight : '';
                if (current === previous || Date.now() - started > SETTLE_MAX_MS) {
                    done();
                    return;
                }
                previous = current;
                setTimeout(check, SETTLE_POLL_MS);
            });
        }
        check();
    }

    function postChatOpened(id) {
        whenChatSettled(function () { post('chatOpened', { characterId: id }); });
    }

    function onMessageReceived(messageId) {
        const ctx = context();
        const message = (ctx[S.context.chat] || [])[messageId];
        if (!message || message[S.message.isUser] || message[S.message.isSystem]) return;
        post('messageReceived', {
            characterId: message[S.message.originalAvatar] || currentCharacterId(ctx),
            messageId: Number(messageId),
        });
    }

    function listen(ctx) {
        const events = ctx[S.context.eventSource];
        events.on(eventName(ctx, S.events.chatChanged), function () {
            post('chatChanged', { characterId: currentCharacterId(context()) });
        });
        events.on(eventName(ctx, S.events.messageReceived), onMessageReceived);
        S.events.charactersChanged.forEach(function (key) {
            const name = eventName(ctx, key);
            if (name) events.on(name, scheduleCharacters);
        });
        // APP_READY re-fires for late listeners, so this also covers an injection after load.
        events.on(eventName(ctx, S.events.ready), function () {
            post('ready');
            sendLists();
        });
    }

    function waitForSillyTavern(startedAt) {
        const ctx = context();
        if (ctx) {
            try {
                listen(ctx);
            } catch (e) {
                postError('Bridge setup failed: ' + e);
            }
            return;
        }
        if (Date.now() - startedAt > READY_TIMEOUT_MS) {
            postError('SillyTavern did not finish loading');
            return;
        }
        setTimeout(function () { waitForSillyTavern(startedAt); }, READY_POLL_MS);
    }

    window.__ttBridge = Object.freeze({
        requestCharacters: sendCharacters,
        requestRecentChats: sendRecentChats,

        /**
         * Opens the character whose avatar file name is [id], through SillyTavern's own code,
         * and posts `chatOpened` once its chat is drawn and has stopped moving (right away,
         * if it is already open).
         */
        selectCharacter: function (id) {
            const ctx = context();
            if (!ctx) return;
            if (currentCharacterId(ctx) === id) {
                postChatOpened(id);
                return;
            }
            const index = characterList(ctx).findIndex(function (c) { return c && c[S.character.id] === id; });
            if (index < 0) {
                postError('Unknown character');
                return;
            }
            const events = ctx[S.context.eventSource];
            const changed = eventName(ctx, S.events.chatChanged);
            function onChatChanged() {
                if (currentCharacterId(context()) !== id) return;
                events.removeListener(changed, onChatChanged);
                postChatOpened(id);
            }
            events.on(changed, onChatChanged);
            Promise.resolve(ctx[S.context.selectCharacterById](index, S.selectOptions)).catch(function (e) {
                events.removeListener(changed, onChatChanged);
                postError('selectCharacter failed: ' + e);
            });
        },

        /** Turns the clean theme (clean-theme.css) on or off. */
        setClean: function (enabled) {
            document.body.classList.toggle(S.cleanClass, enabled === true);
        },

        /**
         * Adds or replaces a <style id=[id]>. [base64] is the UTF-8 stylesheet, Base64-encoded
         * so quotes, backslashes and newlines can't break the calling script.
         */
        installStyle: function (id, base64) {
            const bytes = Uint8Array.from(atob(base64), function (c) { return c.charCodeAt(0); });
            const css = new TextDecoder().decode(bytes);
            let style = document.getElementById(id);
            if (!style) {
                style = document.createElement('style');
                style.id = id;
                document.head.appendChild(style);
            }
            style.textContent = css;
        },

        /** Replaces the body's tt-mode-* class with tt-mode-[mode]; an empty mode clears it. */
        setMode: function (mode) {
            const body = document.body;
            Array.from(body.classList)
                .filter(function (c) { return c.indexOf('tt-mode-') === 0; })
                .forEach(function (c) { body.classList.remove(c); });
            if (mode && /^[a-z][a-z-]*$/.test(mode)) body.classList.add('tt-mode-' + mode);
        },
    });

    if (!port) return;
    waitForSillyTavern(Date.now());
})();
