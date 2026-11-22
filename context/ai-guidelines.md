# AI Guidelines

## Style
- Concise. Briefly explain non-obvious decisions.
- Ask before large refactors/architecture changes.
- Minimal changes. No unspecced or nice-to-have features, no unrelated refactors. Follow existing patterns.
- Never delete files without asking.

## Workflow (every feature/fix)
1. Document in @context/current-feature.md
2. Branch from `dev`: `feature/x`, `fix/x`, etc.
3. Implement
4. Test on device/emulator vs real SillyTavern server. `./gradlew build` (incl. unit tests) must pass before every commit/merge. `connectedDebugAndroidTest` only before release.
5. Iterate
6. Commit: ask first, build must pass
7. Merge into `dev`, push `dev`. Can't merge locally (e.g. cloud): push branch, open PR into `dev`.
8. Ask, then delete branch
9. Mark complete in current-feature.md, add to history
10. History entries: 3–5 lines max — what changed, cause/key decisions, gotchas, what's unverified, version. No verification logs or test counts.

## Branches
- `dev`: working branch, CI on push. Never delete.
- `main`: protected. Never push/merge. Only via PR from `dev` after **Build, test and lint** passes. I merge.

## Release (when asked)
1. `dev` pushed, latest CI green
2. `./gradlew connectedDebugAndroidTest` passes. Use emulator/spare device, not my daily phone (uninstalls app, wipes data). Multiple devices: `ANDROID_SERIAL=<serial>`. Note result in PR.
3. PR `dev`→`main`, titled `Release x.y.z`, listing included history entries
4. I merge with merge commit (no squash/rebase)
5. `1.0.0` (Phase 1 complete): a signed release APK attached to a GitHub Release for the merge commit's tag. Keystore and its passwords stay outside the repo, never committed (`*.jks`, `*.keystore`, `keystore.properties` are gitignored)

## Private data (public repo)
Nothing identifying my network/devices/accounts in code, tests, specs, history, commits or PRs: no real IPs, machine-tied ports, Tailscale machine/tailnet names, hostnames, usernames, emails, passwords, tokens, keys.
- History: describe setup ("SillyTavern 1.19.0 over Tailscale", "Pixel 8")
- Placeholders: `192.0.2.1:8000` (documentation-only range `192.0.2.x`), `desktop.tail1234.ts.net`, `user`/`pass`
- Only exception: user-facing Tailscale examples (`100.64.x.x`) in `README.md`, `docs/tailscale-setup.md` and the `connect_url_placeholder` string. Everywhere else (tests, fakes, androidTest, comments, previews, specs) use `192.0.2.x`
- Check diff before every commit. Already committed? Stop and tell me (it's in Git history).

## Commits
Conventional (`feat:`, `fix:`, `chore:`). One feature/fix each. Never "Generated with Claude".

## Stuck
After 2–3 failed attempts, stop and explain. No random fixes. Ask if unclear.

## SillyTavern/extensions
- Never guess DOM selectors, globals or `getContext()` members. Verify via `chrome://inspect` (debug WebView) or source.
- Spec says **(verify)**: confirm first, record findings in spec Notes.
- All ST/extension selectors/globals in one file (see coding standards).

## Code review (periodic + on demand)
- Security: credential storage, JS bridge origin checks, no secrets in logs
- Performance: recompositions, WebView reloads
- Logic: server unreachable, Tailscale off, auth failures
- Matches existing patterns