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

    /**
     * [path] as an absolute URL that changes when the card file does. The app's image cache
     * keys on the URL, and a new picture keeps the same file name. The server ignores it.
     */
    function versionedUrl(path, changedAt) {
        const url = new URL(path, location.href);
        if (changedAt > 0) url.searchParams.set('tt_v', String(Math.floor(changedAt)));
        return url.href;
    }

    function toSummary(ctx, character) {
        const id = character[S.character.id];
        const thumbnail = ctx[S.context.getThumbnailUrl](S.thumbnailType, id);
        const lastChat = Number(character[S.character.lastChatAt]);
        const changedAt = Number(character[S.character.changedAt]);
        return {
            id: id,
            name: character[S.character.name] || '',
            avatarUrl: versionedUrl(thumbnail, changedAt),
            fullAvatarUrl: versionedUrl(S.fullAvatarPath + encodeURIComponent(id), changedAt),
            lastChatAt: lastChat > 0 ? Math.floor(lastChat) : null,
        };
    }

    /**
     * Posts `characters`. With [fromServer] (a pull to refresh), first has SillyTavern re-read
     * its list from the server, so changes made elsewhere (e.g. a new avatar on the desktop)
     * show up. Reselecting the open character there doesn't reload its chat.
     */
    async function sendCharacters(fromServer) {
        const ctx = context();
        if (!ctx) return;
        if (fromServer === true) {
            try {
                await ctx[S.context.getCharacters]();
            } catch (e) {
                postError('getCharacters failed: ' + e);
            }
        }
        const items = characterList(ctx)
            .filter(function (c) { return c && c[S.character.id]; })
            .map(function (c) { return toSummary(ctx, c); });
        post('characters', { items: items });
    }

    /** One /api/chats/recent row → { characterId, chatFile, lastMessage, lastMessageAt, messageCount }. */
    function toRecentChat(row) {
        const F = S.recentChat;
        const count = Number(row[F.messageCount]) || 0;
        const text = typeof row[F.lastMessage] === 'string' ? row[F.lastMessage] : '';
        const at = Date.parse(row[F.lastMessageAt]);
        return {
            characterId: row[F.characterId],
            chatFile: typeof row[F.chatFile] === 'string' ? row[F.chatFile] : null,
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
            const current = context();
            rememberOpenChat(current);
            post('chatChanged', { characterId: appState.characterId, chatFile: appState.chatFile });
            refreshChatDecorations();
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
            setUpChatScreen(ctx);
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

    // ---- Story chat (1.7): app state, generation state, message taps and actions, the input
    // bar's commands, swipes that never generate, the "…" card and the scroll button.

    const C = S.chatScreen;
    const SWIPE_END_CLASS = 'tt-swipe-end';
    const HAS_BACKGROUND_CLASS = 'tt-has-bg';
    const BACKGROUND_SIZE_VAR = '--tt-bg-size';
    const BACKGROUND_Y_VAR = '--tt-bg-y';
    const DARK_CLASS = 'tt-dark';
    const TYPING_ID = 'tt-typing';
    const SCROLL_BUTTON_ID = 'tt-scroll-newest';
    /** Body class while the app's actions sheet covers the bottom of the chat. */
    const REVEAL_CLASS = 'tt-revealing';
    const REVEAL_GAP_PX = 8;
    const SCROLL_BUTTON_VISIBLE_CLASS = 'tt-visible';
    const SCROLL_AWAY_PX = 240;
    const THEME_COLOR = /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/;
    const THEME_KEY = /^[a-z][a-zA-Z]*$/;
    /** Generation types the "…" card waits for (a reply that hasn't appeared yet). */
    const REPLY_TYPES = ['normal', 'regenerate'];

    /** What the app knows about this page; Phase 8 sends it to the Bridge as the heartbeat. */
    const appState = { foreground: true, characterId: null, chatFile: null };
    let generating = false;
    let generationType = null;
    let afterGeneration = [];
    let selectionAtPointerDown = false;
    let scrollScheduled = false;

    function jq() {
        return window[S.jQuery];
    }

    function chatOf(ctx) {
        return ctx[S.context.chat] || [];
    }

    /** The open chat's file name (without `.jsonl`), or null. */
    function currentChatFile(ctx) {
        const file = ctx[S.context.getCurrentChatId]();
        return typeof file === 'string' && file ? file : null;
    }

    function rememberOpenChat(ctx) {
        appState.characterId = currentCharacterId(ctx);
        appState.chatFile = currentChatFile(ctx);
    }

    function setInputText(input, text) {
        input.value = text;
        input.dispatchEvent(new Event(S.inputEvent, { bubbles: true }));
    }

    function clickElement(selector) {
        jq()(selector).trigger('click');
    }

    function messageElement(id) {
        return document.querySelector(S.dom.chat + ' ' + C.message + '[' + C.messageIdAttr + '="' + id + '"]');
    }

    function clickMessageButton(id, selector) {
        const element = messageElement(id);
        const button = element && element.querySelector(selector);
        if (!button) throw new Error('Message button not found');
        jq()(button).trigger('click');
    }

    /** The greeting of an untouched chat: SillyTavern's swipes loop there instead of generating. */
    function isPristineGreeting(ctx, id) {
        const metadata = ctx[S.context.chatMetadata] || {};
        return id === 0 && !metadata[S.chatMetadataTainted];
    }

    // -- Generation state: SillyTavern shows #mes_stop exactly while it generates.

    function isStopShown() {
        const stop = document.querySelector(C.stopButton);
        const display = stop ? stop.style.display : '';
        return display !== '' && display !== 'none';
    }

    function onStopButtonChanged() {
        const running = isStopShown();
        if (running === generating) return;
        generating = running;
        if (!running) {
            generationType = null;
            runAfterGeneration();
        }
        post('generationState', { running: running });
        refreshChatDecorations();
    }

    function whenNotGenerating(fn) {
        if (generating) afterGeneration.push(fn);
        else fn();
    }

    function runAfterGeneration() {
        const queued = afterGeneration;
        afterGeneration = [];
        queued.forEach(function (fn) {
            try {
                fn();
            } catch (e) {
                postError('Deferred chat action failed: ' + e);
            }
        });
    }

    function onGenerationStarted(type, options, dryRun) {
        if (dryRun || type === 'quiet') return;
        generationType = type;
        refreshChatDecorations();
    }

    // -- Decorations: the swipe-end mark, the "…" card, the background flag, the scroll button.

    /** Marks the latest message when it shows its last reply, so the CSS hides its right arrow. */
    function markSwipeEnd(ctx) {
        document.querySelectorAll('.' + SWIPE_END_CLASS).forEach(function (el) {
            el.classList.remove(SWIPE_END_CLASS);
        });
        const chat = chatOf(ctx);
        const id = chat.length - 1;
        const message = chat[id];
        if (!message || message[S.message.isUser] || message[S.message.isSystem]) return;
        if (isPristineGreeting(ctx, id)) return;
        const swipes = Array.isArray(message[S.message.swipes]) ? message[S.message.swipes].length : 1;
        if (Number(message[S.message.swipeId] || 0) < swipes - 1) return;
        const element = messageElement(id);
        if (element) element.classList.add(SWIPE_END_CLASS);
    }

    function lastMessageElement(chatElement) {
        const messages = chatElement.querySelectorAll(C.message);
        return messages.length ? messages[messages.length - 1] : null;
    }

    /** "…" on the character's side until SillyTavern adds the reply's own message. */
    function updateTypingCard() {
        const chatElement = document.querySelector(S.dom.chat);
        if (!chatElement) return;
        const last = lastMessageElement(chatElement);
        const waiting = generating && REPLY_TYPES.indexOf(generationType) >= 0 &&
            (!last || last.getAttribute(C.isUserAttr) === 'true');
        let card = document.getElementById(TYPING_ID);
        if (!waiting) {
            if (card) card.remove();
            return;
        }
        if (!card) {
            card = document.createElement('div');
            card.id = TYPING_ID;
            card.setAttribute('role', 'status');
            card.setAttribute('aria-label', 'Writing a reply');
            card.innerHTML = '<span></span><span></span><span></span>';
        }
        if (chatElement.lastElementChild !== card) {
            chatElement.appendChild(card);
            chatElement.scrollTop = chatElement.scrollHeight;
        }
    }

    function updateBackgroundFlag() {
        const background = document.querySelector(S.dom.background);
        const image = background ? getComputedStyle(background).backgroundImage : 'none';
        document.body.classList.toggle(HAS_BACKGROUND_CLASS, !!image && image !== 'none');
        updateBackgroundGeometry();
    }

    /**
     * Keeps SillyTavern's background picture still while the page's height changes (the app's input
     * bar growing, the keyboard). #bg1 itself follows the page, but the picture inside it gets a fixed
     * size and offset: what `cover` gives at the tallest height seen at this width, centered as there.
     * A shorter page then only hides the picture's bottom instead of rescaling it. Passed to
     * clean-theme.css as --tt-bg-size / --tt-bg-y; a new width (rotation) starts over.
     */
    let backgroundWidth = 0;
    let backgroundHeight = 0;
    let backgroundImage = null; // { url, width, height } once loaded

    function updateBackgroundGeometry() {
        if (window.innerWidth !== backgroundWidth) {
            backgroundWidth = window.innerWidth;
            backgroundHeight = 0;
        }
        backgroundHeight = Math.max(backgroundHeight, window.innerHeight);
        const url = backgroundImageUrl();
        if (!url) return;
        if (!backgroundImage || backgroundImage.url !== url) {
            loadBackgroundImage(url);
            return;
        }
        const scale = Math.max(backgroundWidth / backgroundImage.width, backgroundHeight / backgroundImage.height);
        const width = backgroundImage.width * scale;
        const height = backgroundImage.height * scale;
        const root = document.documentElement.style;
        root.setProperty(BACKGROUND_SIZE_VAR, width + 'px ' + height + 'px');
        root.setProperty(BACKGROUND_Y_VAR, (backgroundHeight - height) / 2 + 'px');
    }

    function backgroundImageUrl() {
        const background = document.querySelector(S.dom.background);
        const image = background ? getComputedStyle(background).backgroundImage : 'none';
        const match = /url\(["']?(.*?)["']?\)/.exec(image || '');
        return match ? match[1] : null;
    }

    function loadBackgroundImage(url) {
        const image = new Image();
        image.onload = function () {
            if (backgroundImageUrl() !== url || !image.naturalWidth || !image.naturalHeight) return;
            backgroundImage = { url: url, width: image.naturalWidth, height: image.naturalHeight };
            updateBackgroundGeometry();
        };
        image.src = url;
    }

    function scrollButton() {
        let button = document.getElementById(SCROLL_BUTTON_ID);
        const sheld = document.querySelector(S.dom.sheld);
        if (button || !sheld) return button;
        button = document.createElement('div');
        button.id = SCROLL_BUTTON_ID;
        button.setAttribute('role', 'button');
        button.setAttribute('aria-label', 'Scroll to the newest message');
        button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' +
            '<path d="M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z"/></svg>';
        button.addEventListener('click', function () {
            const chatElement = document.querySelector(S.dom.chat);
            if (chatElement) chatElement.scrollTo({ top: chatElement.scrollHeight, behavior: 'smooth' });
        });
        sheld.appendChild(button);
        return button;
    }

    function updateScrollButton() {
        scrollScheduled = false;
        const chatElement = document.querySelector(S.dom.chat);
        const button = scrollButton();
        if (!chatElement || !button) return;
        const away = chatElement.scrollHeight - chatElement.scrollTop - chatElement.clientHeight > SCROLL_AWAY_PX;
        button.classList.toggle(SCROLL_BUTTON_VISIBLE_CLASS, away);
    }

    function scheduleScrollButton() {
        if (scrollScheduled) return;
        scrollScheduled = true;
        requestAnimationFrame(updateScrollButton);
    }

    function refreshChatDecorations() {
        const ctx = context();
        if (!ctx) return;
        markSwipeEnd(ctx);
        updateTypingCard();
        scheduleScrollButton();
    }

    // -- Message taps (clean mode only).

    function onChatPointerDown() {
        const selection = window.getSelection();
        selectionAtPointerDown = !!selection && !selection.isCollapsed;
    }

    function onChatClick(event) {
        if (!document.body.classList.contains(S.cleanClass)) return;
        if (selectionAtPointerDown) {
            // A tap while text is selected only clears the selection.
            selectionAtPointerDown = false;
            const selection = window.getSelection();
            if (selection) selection.removeAllRanges();
            return;
        }
        const target = event.target;
        if (!(target instanceof Element) || target.closest(C.tapIgnore)) return;
        const element = target.closest(C.message);
        if (!element || element.querySelector(C.editTextarea)) return;
        const ctx = context();
        const id = Number(element.getAttribute(C.messageIdAttr));
        const message = ctx ? chatOf(ctx)[id] : null;
        if (message) post('messageTapped', describeTappedMessage(ctx, id, message));
    }

    function describeTappedMessage(ctx, id, message) {
        const isLatest = id === chatOf(ctx).length - 1;
        const isUser = !!message[S.message.isUser];
        const isHidden = !!message[S.message.isSystem];
        return {
            messageId: id,
            sendDate: String(message[S.message.sendDate] == null ? '' : message[S.message.sendDate]),
            name: String(message[S.message.name] || ''),
            isUser: isUser,
            isLatest: isLatest,
            isHidden: isHidden,
            canRegenerate: isLatest && !isHidden && !isPristineGreeting(ctx, id),
            canContinue: isLatest && !isUser && !isHidden,
        };
    }

    // -- Message actions, each through SillyTavern's own handler or function.

    function regenerate(ctx, id) {
        const message = chatOf(ctx)[id];
        if (message[S.message.isUser]) {
            // SillyTavern: regenerating on a user message asks the character to reply.
            clickElement(C.options.regenerate);
            return;
        }
        // A new swipe past the last one: SillyTavern generates it and, if that fails, shows
        // the last existing reply again. Not awaited: the answer means "started".
        const swipes = Array.isArray(message[S.message.swipes]) ? message[S.message.swipes].length : 1;
        Promise.resolve(ctx[S.context.swipe].to(null, S.swipeRight, { forceMesId: id, forceSwipeId: swipes }))
            .catch(function (e) { postError('Regenerate failed: ' + e); });
    }

    const MESSAGE_ACTIONS = {
        copy: function (ctx, id) {
            return { text: String(chatOf(ctx)[id][S.message.text] || '') };
        },
        edit: function (ctx, id) { clickMessageButton(id, C.buttons.edit); },
        branch: function (ctx, id) { clickMessageButton(id, C.buttons.branch); },
        hide: function (ctx, id) { clickMessageButton(id, C.buttons.hide); },
        show: function (ctx, id) { clickMessageButton(id, C.buttons.unhide); },
        delete: function (ctx, id) { return ctx[S.context.deleteMessage](id); },
        continue: function () { clickElement(C.options.continue); },
        regenerate: regenerate,
    };

    /** SillyTavern's script.js module (exports that aren't on getContext()), by absolute URL. */
    function scriptModule() {
        return import(new URL(S.scriptModulePath, location.href).href);
    }

    /**
     * Deletes chat [chatFile] of character [index] with SillyTavern's own function. It reports
     * nothing, so this resolves true only if CHAT_DELETED fired for that file (server said OK).
     */
    function deleteChatFile(ctx, index, chatFile) {
        const events = ctx[S.context.eventSource];
        const name = eventName(ctx, S.events.chatDeleted);
        let deleted = false;
        function onDeleted(file) {
            if (file === chatFile) deleted = true;
        }
        events.on(name, onDeleted);
        return scriptModule()
            .then(function (script) { return script[S.deleteCharacterChatByName](String(index), chatFile); })
            .then(function () { return deleted; })
            .finally(function () { events.removeListener(name, onDeleted); });
    }

    function postChatResult(requestId, ok, error, extra) {
        const payload = { requestId: requestId, ok: ok };
        if (error) payload.error = error;
        post('chatResult', Object.assign(payload, extra || {}));
    }

    // -- Opening a chat (1.6 + 1.7: a specific chat file, reload when stale).

    function selectById(ctx, id) {
        return new Promise(function (resolve, reject) {
            const index = characterList(ctx).findIndex(function (c) { return c && c[S.character.id] === id; });
            if (index < 0) {
                reject(new Error('Unknown character'));
                return;
            }
            const events = ctx[S.context.eventSource];
            const changed = eventName(ctx, S.events.chatChanged);
            function onChatChanged() {
                if (currentCharacterId(context()) !== id) return;
                events.removeListener(changed, onChatChanged);
                resolve();
            }
            events.on(changed, onChatChanged);
            Promise.resolve(ctx[S.context.selectCharacterById](index, S.selectOptions)).catch(function (e) {
                events.removeListener(changed, onChatChanged);
                reject(e);
            });
        });
    }

    async function chatFileExists(ctx, id, chatFile) {
        const response = await fetch(S.characterChatsApi, {
            method: 'POST',
            headers: ctx[S.context.getRequestHeaders](),
            body: JSON.stringify({ avatar_url: id, simple: true }),
        });
        if (!response.ok) return false;
        const files = await response.json();
        return Array.isArray(files) && files.some(function (f) { return f && f.file_id === chatFile; });
    }

    function waitUntilNotGenerating() {
        return new Promise(function (resolve) { whenNotGenerating(resolve); });
    }

    /** Opens [id]'s chat ([chatFile] if it exists), re-reading it if [reload] and nothing loaded it. */
    async function openChat(id, chatFile, reload) {
        let loaded = false;
        if (currentCharacterId(context()) !== id) {
            await selectById(context(), id);
            loaded = true;
        }
        const ctx = context();
        if (chatFile && currentChatFile(ctx) !== chatFile && await chatFileExists(ctx, id, chatFile)) {
            await ctx[S.context.openCharacterChat](chatFile);
            loaded = true;
        }
        if (reload && !loaded) {
            await waitUntilNotGenerating();
            await context()[S.context.reloadCurrentChat]();
        }
    }

    function postChatOpened(requestId, id) {
        whenChatSettled(function () {
            post('chatOpened', { requestId: requestId, characterId: id, chatFile: currentChatFile(context()) });
        });
    }

    function onImpersonateReady(text) {
        post('impersonateReady', { text: String(text || '') });
        // The text now lives in the app's field; don't leave it behind as SillyTavern's draft.
        const input = document.querySelector(S.dom.sendTextarea);
        if (input) setInputText(input, '');
    }

    function setUpChatScreen(ctx) {
        const events = ctx[S.context.eventSource];
        events.on(eventName(ctx, S.events.generationStarted), onGenerationStarted);
        events.on(eventName(ctx, S.events.impersonateReady), onImpersonateReady);
        S.events.messagesChanged.forEach(function (key) {
            const name = eventName(ctx, key);
            if (name) events.on(name, refreshChatDecorations);
        });
        const stop = document.querySelector(C.stopButton);
        if (stop) new MutationObserver(onStopButtonChanged).observe(stop, { attributes: true, attributeFilter: ['style'] });
        const chatElement = document.querySelector(S.dom.chat);
        if (chatElement) {
            new MutationObserver(refreshChatDecorations).observe(chatElement, { childList: true });
            chatElement.addEventListener('scroll', scheduleScrollButton, { passive: true });
            chatElement.addEventListener('pointerdown', onChatPointerDown, true);
            chatElement.addEventListener('click', onChatClick);
        }
        const background = document.querySelector(S.dom.background);
        if (background) new MutationObserver(updateBackgroundFlag).observe(background, { attributes: true, attributeFilter: ['style'] });
        updateBackgroundFlag();
        window.addEventListener('resize', updateBackgroundGeometry, { passive: true });
        updateBackgroundGeometry();
        rememberOpenChat(ctx);
        generating = isStopShown();
        refreshChatDecorations();
    }

    const chatActions = {
        /** The page's view of the app (foreground, open chat). Read by Phase 8; nothing reads it yet. */
        appState: appState,

        setAppState: function (foreground) {
            appState.foreground = foreground === true;
        },

        /** Re-reads the open chat from the server, after a running generation ends. */
        reloadChat: function () {
            whenNotGenerating(function () {
                const ctx = context();
                if (!ctx) return;
                Promise.resolve(ctx[S.context.reloadCurrentChat]()).catch(function (e) {
                    postError('reloadChat failed: ' + e);
                });
            });
        },

        /**
         * Runs [action] on message [messageId] if it still has [sendDate] (else `stale`), and
         * answers with a `chatResult` tagged [requestId]. Only copy runs during a generation.
         */
        messageAction: function (requestId, messageId, sendDate, action) {
            const ctx = context();
            const run = Object.prototype.hasOwnProperty.call(MESSAGE_ACTIONS, action) ? MESSAGE_ACTIONS[action] : null;
            const id = Number(messageId);
            const message = ctx ? chatOf(ctx)[id] : null;
            if (!run) {
                postChatResult(requestId, false, 'failed');
            } else if (!message || String(message[S.message.sendDate] == null ? '' : message[S.message.sendDate]) !== String(sendDate)) {
                postChatResult(requestId, false, 'stale');
            } else if (generating && action !== 'copy') {
                postChatResult(requestId, false, 'busy');
            } else {
                Promise.resolve()
                    .then(function () { return run(ctx, id); })
                    .then(function (extra) {
                        postChatResult(requestId, true, null, extra && extra.text !== undefined ? extra : null);
                    }, function (e) {
                        postError('Message action failed: ' + e);
                        postChatResult(requestId, false, 'failed');
                    });
            }
        },

        /** Sends [text] through SillyTavern's own send (a `/command` runs as if typed there). */
        sendMessage: function (text) {
            const input = document.querySelector(S.dom.sendTextarea);
            if (!input) return;
            setInputText(input, String(text));
            clickElement(S.dom.sendButton);
        },

        /** SillyTavern's impersonate; the text arrives as `impersonateReady`. */
        impersonate: function () {
            clickElement(C.options.impersonate);
        },

        /**
         * Scrolls message [id] into the part of the chat the app's actions sheet leaves visible:
         * the bottom [coveredPx] CSS pixels are under the sheet. Adds that much room below the
         * messages first, so even the latest one can move up. [endReveal] takes the room away.
         */
        revealMessage: function (id, coveredPx) {
            const chat = document.querySelector(S.dom.chat);
            const element = messageElement(Number(id));
            if (!chat || !element) return;
            const covered = Math.max(0, Number(coveredPx) || 0);
            chat.style.setProperty('--tt-reveal-room', covered + 'px');
            document.body.classList.add(REVEAL_CLASS);
            const chatBox = chat.getBoundingClientRect();
            const box = element.getBoundingClientRect();
            const visibleBottom = chat.clientHeight - covered - REVEAL_GAP_PX;
            const bottom = box.bottom - chatBox.top;
            const top = box.top - chatBox.top;
            if (bottom > visibleBottom) chat.scrollTop += Math.min(bottom - visibleBottom, top - REVEAL_GAP_PX);
        },

        endReveal: function () {
            document.body.classList.remove(REVEAL_CLASS);
        },

        stopGeneration: function () {
            const ctx = context();
            if (ctx) ctx[S.context.stopGeneration]();
        },

        /** Start new chat ([deleteCurrent] false) or Restart chat (true), answered with `chatResult`. */
        newChat: function (requestId, deleteCurrent) {
            if (generating) {
                postChatResult(requestId, false, 'busy');
                return;
            }
            scriptModule()
                .then(function (script) { return script[S.doNewChat]({ deleteCurrentChat: deleteCurrent === true }); })
                .then(function () { postChatResult(requestId, true); }, function (e) {
                    postError('New chat failed: ' + e);
                    postChatResult(requestId, false, 'failed');
                });
        },

        /**
         * Deletes character [id]'s chat [chatFile] (no `.jsonl`) through SillyTavern's own code.
         * If it's the chat open in the page, the page then moves to the newest chat left (or a
         * new one), as deleting in SillyTavern's chat list does. Answered with `chatResult`.
         */
        deleteChat: function (requestId, id, chatFile) {
            const ctx = context();
            const index = ctx ? characterList(ctx).findIndex(function (c) { return c && c[S.character.id] === id; }) : -1;
            if (index < 0 || typeof chatFile !== 'string' || !chatFile) {
                postChatResult(requestId, false, 'failed');
                return;
            }
            const isOpen = currentCharacterId(ctx) === id && currentChatFile(ctx) === chatFile;
            if (isOpen && generating) {
                postChatResult(requestId, false, 'busy');
                return;
            }
            deleteChatFile(ctx, index, chatFile)
                .then(function (deleted) {
                    if (!deleted) {
                        postChatResult(requestId, false, 'failed');
                        return;
                    }
                    const moveOn = isOpen
                        ? scriptModule().then(function (script) { return script[S.replaceCurrentChat](); })
                        : Promise.resolve();
                    return moveOn.then(function () { postChatResult(requestId, true); });
                })
                .catch(function (e) {
                    postError('Delete chat failed: ' + e);
                    postChatResult(requestId, false, 'failed');
                });
        },

        /** The app's theme for the clean layout: `{ dark, colors: { name: '#rrggbb' } }` → `--tt-*`. */
        setTheme: function (theme) {
            if (!theme || typeof theme !== 'object') return;
            const colors = theme.colors || {};
            const style = document.documentElement.style;
            Object.keys(colors).forEach(function (key) {
                const value = String(colors[key]);
                if (!THEME_KEY.test(key) || !THEME_COLOR.test(value)) return;
                style.setProperty('--tt-' + key.replace(/[A-Z]/g, function (c) { return '-' + c.toLowerCase(); }), value);
            });
            document.body.classList.toggle(DARK_CLASS, theme.dark === true);
        },
    };

    window.__ttBridge = Object.freeze(Object.assign({}, lorebookActions, chatActions, {
        requestCharacters: function (fromServer) {
            sendCharacters(fromServer === true);
        },
        requestRecentChats: sendRecentChats,

        /**
         * Opens [id]'s chat (by avatar file name) through SillyTavern's own code: [chatFile]
         * (name without `.jsonl`) if given and it exists, else the character's current chat;
         * re-read first if [reload] and it was already open. Posts `chatOpened` tagged
         * [requestId] once the chat is drawn and has stopped moving.
         */
        selectCharacter: function (requestId, id, chatFile, reload) {
            if (!context()) return;
            const file = typeof chatFile === 'string' && chatFile ? chatFile : null;
            openChat(id, file, reload === true).then(function () {
                postChatOpened(requestId, id);
            }, function (e) {
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
    }));

    // Runs in any SillyTavern page: without the app's port, post() simply does nothing.
    waitForSillyTavern(Date.now());
})();
