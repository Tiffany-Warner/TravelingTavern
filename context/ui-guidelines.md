# UI Guidelines

The look is **quiet and simple**: neutral surfaces, one accent color, and the characters' avatars supply the color. No glows, particles, or multi-colored cards.

## Principles

- **Characters are the color.** The UI is neutral so avatars and portraits stand out
- **One accent color**, used sparingly: the selected tab, primary buttons, unread dots, toggles. Never for decoration
    - Exception: the user's own message cards in Chat use the accent container (`primaryContainer`) so your messages stand apart (1.7, user decision 2026-11-19)
- **Few things per screen.** If a screen needs explaining, it has too much on it
- **Phone-first.** Large touch targets (min 48dp), thumb-reachable actions at the bottom, no hover-only affordances
- **Tuning lives elsewhere.** SillyTavern settings stay in SillyTavern (Advanced) — the app only shows what a phone user needs

## Color

- Dark theme (default): near-black background, slightly lighter surfaces for cards and sheets, high-contrast white text, muted gray secondary text
- Light theme: off-white background, white surfaces, near-black text
- Accent: warm amber (one tone, plus its container variant for selected states)
- Status colors (error, warning) only for actual status — never decoration
- No gradients, glows, particles, or colored card borders
- Dynamic color (Material You) is **off**. A "Use wallpaper colors" toggle comes with Appearance in Me, a later nice-to-have (`project-schema.md` → Later)

## Typography

- One sans-serif family (Inter, bundled), Material 3 type scale
- Screen titles: `titleLarge`; list item names: `titleMedium`; previews and timestamps: `bodyMedium` / `labelMedium` in secondary color
- No decorative logo type in the app bar — plain text title or nothing

## Shape and Spacing

- Corner radius: 16dp cards and sheets, 12dp buttons and inputs, fully round avatars and chips
- 16dp screen padding, 8dp spacing grid
- Elevation: tonal surface color, not shadows

## Navigation

- **Bottom navigation bar** with four tabs: **Chats**, **Characters**, **Lorebooks**, **Me** (Me stays last)
- Never ship a placeholder tab
- Icons with short labels, labels always shown
- Bottom bar is hidden on full-screen destinations: Chat, Advanced, Connect, Personas, the persona editor, Create a character (and later creator flows)
- Tab state is preserved when switching tabs (scroll position, search text)

## Common Patterns

- **Lists:** avatar (48dp, round) + name + one line of secondary text + trailing timestamp or dot. No dividers; spacing separates rows
- **Grids:** portrait cards (2 columns, 3:4 ratio), name over a subtle bottom scrim
- **Item grids** (things without a portrait, e.g. lorebooks): 2 columns, surface card with 16dp corners and no border, a 4:3 neutral cover tile with an icon, then name and one or two lines of secondary text below. One tap target per card
- **Filter chips:** single-select row under the app bar; the selected chip uses the accent container. Filters and search text survive tab switches on the image
- **Primary action per screen:** one FAB or one filled button, never both
- **Empty states:** one short sentence + one action button, no illustrations for now
- **Errors:** inline banner at the top of the content ("Can't reach your server — Retry"), not dialogs. Connection status is not shown at all when things are working
- **Sheets over dialogs** for choices (e.g. a chat's Hide / Delete)
    - Exception: deleting a whole chat (Delete in Chats, Restart chat) confirms in an alert dialog (`ConfirmDeleteChatDialog`: red delete icon, a bold red first line "This also deletes the chat on your SillyTavern.", red button) so it can't be mistaken for the Start new chat sheet (1.6c, user decision 2026-11-19). Deleting a persona uses the same dialog (`ConfirmDeleteDialog`, 1.9, user decision 2026-11-20)
- **Overflow menus:** a ⋮ (or a persona card's horizontal ⋯) opens a dropdown anchored under it, not a sheet
- **Motion:** Material defaults only; no custom animations until a later polish pass

## Screen Map

Where each core feature lives in the app:

| Feature | Where it lives |
|---|---|
| Recent conversations | **Chats** tab (default) |
| Character library | **Characters** tab — one library, since characters live in SillyTavern |
| Lorebooks (World Info) | **Lorebooks** tab — browse, filter, create and delete lorebooks; edit entries |
| Create a character | + on Characters → Create a character screen (1.10). Import is later |
| Me (settings and profile) | **Me** tab — personas, server, Advanced, About. Notifications return with Phase 8; Appearance is later |
| Connection status | Nothing when connected; a banner only when disconnected |