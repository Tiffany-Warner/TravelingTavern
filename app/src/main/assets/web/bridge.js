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

    let charactersTimer = null;
    function scheduleCharacters() {
        clearTimeout(charactersTimer);
        charactersTimer = setTimeout(sendCharacters, CHARACTERS_DEBOUNCE_MS);
    }

    /** Avatar file name of the open character, or null (no character, or a group chat). */
    function currentCharacterId(ctx) {
        const index = ctx[S.context.characterId];
        const character = index === undefined ? undefined : characterList(ctx)[index];
        return character ? character[S.character.id] : null;
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
            sendCharacters();
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

        /** Opens the character whose avatar file name is [id], through SillyTavern's own code. */
        selectCharacter: function (id) {
            const ctx = context();
            if (!ctx) return;
            const index = characterList(ctx).findIndex(function (c) { return c && c[S.character.id] === id; });
            if (index < 0) {
                postError('Unknown character');
                return;
            }
            Promise.resolve(ctx[S.context.selectCharacterById](index)).catch(function (e) {
                postError('selectCharacter failed: ' + e);
            });
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
