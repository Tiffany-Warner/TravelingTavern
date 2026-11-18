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

    // ---- World Info (lorebooks) -------------------------------------------------------

    const W = S.worldInfo;
    let worldInfoModule = null;

    /**
     * SillyTavern's own world-info.js module (the instance its UI uses). The URL must be
     * absolute: a script injected by the app has no base URL of its own (about:blank).
     */
    function worldInfo() {
        if (!worldInfoModule) worldInfoModule = import(new URL(W.modulePath, location.href).href);
        return worldInfoModule;
    }

    function lorebookNames(wi) {
        return (wi[W.module.names] || []).slice();
    }

    function powerUser(ctx) {
        return ctx[S.powerUser.context] || {};
    }

    /** Avatar file name without its extension — how world_info.charLore names a character. */
    function charLoreKey(avatar) {
        return String(avatar).replace(/\.[^/.]+$/, '');
    }

    function primaryLorebook(character) {
        return W.characterPrimary.reduce(function (value, key) {
            return value && typeof value === 'object' ? value[key] : undefined;
        }, character);
    }

    /** name → { characterIds: [], personaNames: [] } for every character and persona link. */
    function lorebookLinks(ctx, wi) {
        const links = {};
        function linksOf(name) {
            if (!links[name]) links[name] = { characterIds: [], personaNames: [] };
            return links[name];
        }
        const byCharLoreKey = {};
        characterList(ctx).forEach(function (c) {
            const id = c && c[S.character.id];
            if (!id) return;
            byCharLoreKey[charLoreKey(id)] = id;
            const primary = primaryLorebook(c);
            if (primary) linksOf(primary).characterIds.push(id);
        });
        const settings = wi[W.module.settings] || {};
        (settings[W.charLore] || []).forEach(function (row) {
            const id = row && byCharLoreKey[row[W.charLoreName]];
            if (!id) return;
            (row[W.charLoreBooks] || []).forEach(function (name) {
                const ids = linksOf(name).characterIds;
                if (ids.indexOf(id) < 0) ids.push(id);
            });
        });
        const P = S.powerUser;
        const pu = powerUser(ctx);
        const personaNames = pu[P.personaNames] || {};
        Object.entries(pu[P.personaDescriptions] || {}).forEach(function (pair) {
            const name = pair[1] && pair[1][P.personaLorebook];
            if (name) linksOf(name).personaNames.push(personaNames[pair[0]] || pair[0]);
        });
        const current = pu[P.currentPersonaLorebook];
        // The current persona's link is mirrored here, possibly before it has a descriptor.
        if (current && linksOf(current).personaNames.length === 0) linksOf(current).personaNames.push('');
        return links;
    }

    function entryCount(data) {
        return data && data[W.entries] ? Object.keys(data[W.entries]).length : null;
    }

    /** Drops cached copies so the next load reads the server (picks up desktop edits). */
    function evict(wi, names) {
        const cache = wi[W.module.cache];
        names.forEach(function (name) { if (cache && cache.has(name)) cache.delete(name); });
    }

    /**
     * Posts `lorebooks`: every lorebook with its entry count, global flag and links. With
     * [fromServer], first re-reads the list and the lorebooks themselves from the server.
     */
    async function sendLorebooks(fromServer) {
        const ctx = context();
        if (!ctx) return;
        try {
            const wi = await worldInfo();
            if (fromServer) await wi[W.module.updateList]();
            const names = lorebookNames(wi);
            if (fromServer) evict(wi, names);
            const global = wi[W.module.globalSelected] || [];
            const links = lorebookLinks(ctx, wi);
            const counts = await Promise.all(names.map(function (name) {
                return wi[W.module.load](name).then(entryCount, function () { return null; });
            }));
            const items = names.map(function (name, i) {
                const link = links[name] || { characterIds: [], personaNames: [] };
                return {
                    name: name,
                    entryCount: counts[i],
                    global: global.indexOf(name) >= 0,
                    characterIds: link.characterIds,
                    personaNames: link.personaNames,
                };
            });
            post('lorebooks', { items: items });
        } catch (e) {
            postError('Lorebooks failed: ' + e);
        }
    }

    function toEntry(entry) {
        const F = W.entry;
        const keys = Array.isArray(entry[F.keys]) ? entry[F.keys] : [];
        return {
            uid: Number(entry[F.uid]),
            title: typeof entry[F.title] === 'string' ? entry[F.title] : '',
            keys: keys.map(String),
            content: typeof entry[F.content] === 'string' ? entry[F.content] : '',
            enabled: entry[F.disabled] !== true,
        };
    }

    /** Entries in the order SillyTavern's editor shows them by default. */
    function sortedEntries(data) {
        const F = W.entry;
        return Object.values(data[W.entries] || {}).sort(function (a, b) {
            const ai = Number(a[F.displayIndex] ?? a[F.uid]);
            const bi = Number(b[F.displayIndex] ?? b[F.uid]);
            return ai - bi || Number(a[F.uid]) - Number(b[F.uid]);
        });
    }

    function postResult(requestId, ok, extra) {
        post('lorebookResult', Object.assign({ requestId: requestId, ok: ok }, extra || {}));
    }

    /** Runs [action] and answers [requestId]; anything thrown becomes `{ ok: false }`. */
    function answer(requestId, action) {
        Promise.resolve()
            .then(action)
            .catch(function (e) {
                postResult(requestId, false, { error: 'failed' });
                postError('Lorebook action failed: ' + e);
            });
    }

    async function loadForEdit(wi, name) {
        if (lorebookNames(wi).indexOf(name) < 0) return null;
        return wi[W.module.load](name);
    }

    /** Removes [name] from every character's extra lorebooks, as SillyTavern's Delete button does. */
    function removeFromCharLore(ctx, wi, name) {
        const settings = wi[W.module.settings] || {};
        const rows = settings[W.charLore];
        if (!Array.isArray(rows)) return;
        let changed = false;
        for (let i = rows.length - 1; i >= 0; i--) {
            const books = rows[i][W.charLoreBooks];
            if (!Array.isArray(books) || books.indexOf(name) < 0) continue;
            const rest = books.filter(function (b) { return b !== name; });
            if (rest.length === 0) rows.splice(i, 1); else rows[i][W.charLoreBooks] = rest;
            changed = true;
        }
        if (changed) ctx[S.context.saveSettingsDebounced]();
    }

    const lorebookActions = {
        requestLorebooks: function (fromServer) {
            sendLorebooks(fromServer === true);
        },

        /** Posts `lorebookEntries` for [name], read fresh from the server. */
        requestLorebook: function (requestId, name) {
            answer(requestId, async function () {
                const wi = await worldInfo();
                evict(wi, [name]);
                const data = await loadForEdit(wi, name);
                if (!data) {
                    postResult(requestId, false, { error: 'notFound' });
                    return;
                }
                post('lorebookEntries', { requestId: requestId, name: name, entries: sortedEntries(data).map(toEntry) });
            });
        },

        /**
         * Creates an empty lorebook; answers with the exact name SillyTavern saved it under
         * (its sanitized file name). Never guesses from which names are new: lorebooks made
         * on the desktop meanwhile would look new too.
         */
        createLorebook: function (requestId, name) {
            answer(requestId, async function () {
                const wi = await worldInfo();
                const utils = await import(new URL(W.utilsPath, location.href).href);
                const saved = await utils[W.sanitizeName](name);
                const created = await wi[W.module.create](name, { interactive: false });
                if (!created) {
                    postResult(requestId, false, { error: 'exists' });
                    return;
                }
                const names = lorebookNames(wi);
                const found = [saved, name].find(function (n) { return names.indexOf(n) >= 0; });
                postResult(requestId, found !== undefined, found !== undefined ? { name: found } : { error: 'failed' });
                sendLorebooks(false);
            });
        },

        deleteLorebook: function (requestId, name) {
            answer(requestId, async function () {
                const ctx = context();
                const wi = await worldInfo();
                removeFromCharLore(ctx, wi, name);
                const deleted = await wi[W.module.remove](name);
                postResult(requestId, deleted === true, deleted === true ? {} : { error: 'failed' });
                sendLorebooks(false);
            });
        },

        /** Turns a lorebook on or off for every chat, through SillyTavern's own global list control. */
        setLorebookGlobal: function (requestId, name, enabled) {
            answer(requestId, async function () {
                const wi = await worldInfo();
                const index = lorebookNames(wi).indexOf(name);
                const select = document.querySelector(W.globalSelect);
                const option = select && index >= 0 ? select.querySelector('option[value="' + index + '"]') : null;
                if (!option) {
                    postResult(requestId, false, { error: 'notFound' });
                    return;
                }
                option.selected = enabled === true;
                select.dispatchEvent(new Event('change', { bubbles: true }));
                const isGlobal = (wi[W.module.globalSelected] || []).indexOf(name) >= 0;
                postResult(requestId, isGlobal === (enabled === true));
            });
        },

        /**
         * Saves title, keys, content and enabled into an entry ([entry].uid null = a new one),
         * leaving every other field as SillyTavern has it. Answers with the entry's uid.
         */
        saveLorebookEntry: function (requestId, name, entry) {
            answer(requestId, async function () {
                const wi = await worldInfo();
                const data = await loadForEdit(wi, name);
                const isNew = entry.uid === null || entry.uid === undefined;
                const target = !data ? null : isNew ? wi[W.module.createEntry](name, data) : data[W.entries][entry.uid];
                if (!target) {
                    postResult(requestId, false, { error: 'notFound' });
                    return;
                }
                const F = W.entry;
                target[F.title] = String(entry.title);
                target[F.keys] = entry.keys.map(String);
                target[F.content] = String(entry.content);
                target[F.disabled] = entry.enabled !== true;
                await wi[W.module.save](name, data, true);
                postResult(requestId, true, { uid: Number(target[F.uid]) });
            });
        },

        deleteLorebookEntry: function (requestId, name, uid) {
            answer(requestId, async function () {
                const wi = await worldInfo();
                const data = await loadForEdit(wi, name);
                const deleted = data ? await wi[W.module.removeEntry](data, uid, { silent: true }) : false;
                if (deleted === true) await wi[W.module.save](name, data, true);
                postResult(requestId, deleted === true, deleted === true ? {} : { error: 'notFound' });
            });
        },

        /** Posts `tokenCount` for [text] with SillyTavern's current tokenizer. */
        countTokens: function (requestId, text) {
            const ctx = context();
            if (!ctx) return;
            Promise.resolve(ctx[S.context.getTokenCountAsync](String(text)))
                .then(function (count) { post('tokenCount', { requestId: requestId, count: Number(count) || 0 }); })
                .catch(function (e) { postError('Token count failed: ' + e); });
        },
    };

    function sendLists() {
        sendCharacters();
        sendRecentChats();
        sendLorebooks(false);
    }

    let charactersTimer = null;
    function scheduleCharacters() {
        clearTimeout(charactersTimer);
        charactersTimer = setTimeout(sendLists, CHARACTERS_DEBOUNCE_MS);
    }

    let lorebooksTimer = null;
    function scheduleLorebooks() {
        clearTimeout(lorebooksTimer);
        lorebooksTimer = setTimeout(function () { sendLorebooks(false); }, CHARACTERS_DEBOUNCE_MS);
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
        W.events.forEach(function (key) {
            const name = eventName(ctx, key);
            if (name) events.on(name, scheduleLorebooks);
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

    window.__ttBridge = Object.freeze(Object.assign({}, lorebookActions, {
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
    }));

    if (!port) return;
    waitForSillyTavern(Date.now());
})();
