# AI Interaction Guidelines

## Communication

- Be concise and direct
- Explain non-obvious decisions briefly
- Ask before large refactors or architectural changes
- Don't add features not in the project spec
- Never delete files without clarification

## Workflow

This is the common workflow that we will use for every single feature/fix:

1. **Document** - Document the feature in @context/current-feature.md.
2. **Branch** - Create new branch for feature, fix, etc. from `dev`
3. **Implement** - Implement the feature/fix that I create in @context/current-feature.md
4. **Test** - Verify it works on device/emulator against the real SillyTavern server. Run `./gradlew build` (includes the unit tests) and fix any errors — required before every commit and merge. Android tests (`connectedDebugAndroidTest`) are not part of this step; they run only before a release (see Releasing to main)
5. **Iterate** - Iterate and change things if needed
6. **Commit** - Only after build passes and everything works
7. **Merge** - Merge into `dev` and push `dev`. Never merge or push into `main` (see Branching)
8. **Delete Branch** - Delete branch after merge
9. **Review** - Review AI-generated code periodically and on demand
10. Mark as completed in @context/current-feature.md and add to history

Do NOT commit without permission and until the build passes. If build fails, fix the issues first.

## Branching

We will create a new branch for every feature/fix. Name branch **feature/[feature]** or **fix/[fix]**, etc. Ask to delete the branch once merged.

Two long-lived branches:

- **`dev`** — the working branch. Every feature/fix branch starts from `dev` and merges back into it. Merging locally and pushing is fine; CI runs on every push to `dev`
- **`main`** — protected (no direct pushes, no force pushes, no deletion). Changes reach `main` only through a pull request from `dev`, after the **Build, test and lint** check passes. I merge those PRs on GitHub

Never delete `dev`. If you can't merge locally (e.g. a cloud session), push the feature branch and open a pull request into `dev` instead.

### Releasing to main

When I ask to release (usually after a sub-feature is verified on device and its version bump is on `dev`):

1. Make sure `dev` is pushed and its latest CI run is green
2. Run the Compose UI tests (`./gradlew connectedDebugAndroidTest`) on an emulator or device and make sure they pass. Note the result in the PR description
    - Use the emulator (or a spare device), not the phone I use every day: the test run **uninstalls the app** afterwards, which deletes its saved data (server address, login, permissions). With several devices connected, pick one with `ANDROID_SERIAL=<serial>` — otherwise Gradle runs on every connected device
3. Open a pull request from `dev` into `main`, titled with the version (e.g. `Release 0.1.5`) and listing the history entries it includes
4. I merge it on GitHub with **Create a merge commit** — not squash or rebase, which would make `main` and `dev` drift apart

## Private Data

This repo is public. Nothing that identifies my network, devices or accounts goes into any committed file — code, tests, specs, `current-feature.md` history entries, commit messages or PR descriptions:

- No real IP addresses, ports tied to my machines, Tailscale machine or tailnet names, hostnames, usernames, emails, passwords, tokens or API keys
- In history entries, describe the setup instead: "against SillyTavern 1.19.0 over Tailscale", "on the Pixel 8"
- In code, tests and previews, use placeholders only: `100.64.0.2:8000`, `desktop.tail1234.ts.net`, `user` / `pass`
- Before every commit, check the diff for anything like the above. If something private was already committed, stop and tell me — don't just overwrite it, since it stays in Git history

## Commits

- Ask before committing (don't auto-commit)
- Use conventional commit messages (feat:, fix:, chore:, etc.)
- Keep commits focused (one feature/fix per commit)
- Never put "Generated With Claude" in the commit messages

## When Stuck

- If something isn't working after 2-3 attempts, stop and explain the issue
- Don't keep trying random fixes
- Ask for clarification if requirements are unclear

## Investigating SillyTavern and Extensions

Several features depend on SillyTavern's (and extensions') live DOM and JavaScript APIs, which can change between versions.

- Never guess a DOM selector, global, or `getContext()` member. Confirm it first — inspect the live page via `chrome://inspect` (WebView debugging is on in debug builds) or read the SillyTavern / extension source
- When a spec marks something **(verify)**, confirm it before building on it, and write what you found into the spec's Notes section
- Keep every SillyTavern and extension selector and global name in one place (see coding standards) so a SillyTavern update is a one-file fix

## Code Changes

- Make minimal changes to accomplish the task
- Don't refactor unrelated code unless asked
- Don't add "nice to have" features
- Preserve existing patterns in the codebase

## Code Review

Review AI-generated code periodically, especially for:

- Security (credential storage, JS bridge origin checks, nothing sensitive in logs)
- Performance (unnecessary recompositions, WebView reloads)
- Logic errors (edge cases — server unreachable, Tailscale off, auth failures)
- Patterns (matches existing codebase?)
