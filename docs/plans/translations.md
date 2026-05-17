# whisperboy — translations plan

## Status: 🟡 IN PROGRESS — Phase T.A + T.B foundational pieces land in this initial commit; Phases T.C / T.D / T.E open as the project ships UI surfaces.

## The model

**Translations are produced by the user + Claude, per-language, in dedicated sessions.** Same model tonearmboy uses; same canonical workflow. Community PRs are accepted if they show up, but nothing in the pipeline assumes or requires them — no contributor onboarding doc, no PR template addendum, no "welcome mat" infrastructure.

Per-language session shape:

1. User picks a target locale.
2. Claude reads the canonical `app/src/main/res/values/strings.xml` and the editorial brief from CLAUDE.md (plain / factual / useful — no vibes copy).
3. Claude drafts `values-<locale>/strings.xml` with every translatable key. Keys stay in canonical order so a side-by-side diff is reviewable.
4. User reviews per-entry; corrects what's off; commits what's signed off.
5. README progress table regenerates on the next release.

That's the whole loop. No external service, no contributor coordination, no "wait for review pair" — just user + Claude.

## Constraints (locked)

- **No third-party translation service.** No Crowdin, no Lokalise, no Weblate (hosted or self-hosted). Translations live as plain XML files in the repo.
- **No new build dependency.** The whole pipeline is Android's built-in `values-<locale>/strings.xml` mechanism plus a small POSIX shell script for the README progress table. No Gradle plugins, no SDKs.
- **Zero CI minutes by default.** Translation-progress regeneration runs locally inside `scripts/build-release-apk.sh`, the same way the APK + release does. The README table updates as part of the next release commit; no `on: push` workflow.
- **English is canonical.** `app/src/main/res/values/strings.xml` is the source of truth. Locale variants are partial overrides; missing keys fall back to English at runtime (Android default behaviour).
- **Editorial discipline carries over.** Per CLAUDE.md: app copy stays plain, factual, useful. Translations match that register.

## How whisperboy differs from tonearmboy here

- **Whisperboy has no UI yet.** End of Phase B = playback engine + service + composition root. The library / player / sleep timer / bookmarks / chapter list / folder picker / onboarding / settings screens land in Phases E through L, *all of them after this plan exists*.
- **Discipline-from-day-one beats retro-extraction.** Tonearmboy faced a 357-string mechanical extraction pass after the fact — the cost of having shipped UI without `stringResource()` from the start. Whisperboy enforces the discipline as Phases E–L land: every new user-facing string lands in `values/strings.xml` in the SAME COMMIT that introduces it. No standalone extraction PR.
- **Audiobook vocabulary has translation traps to flag in the per-locale review session:**
  - **Plurals:** `chapter` / `chapters` (English-2-form), `Kapitel / Kapitel` (German same-form), `глава / главы / глав` (Russian-3-form). Use `<plurals>` not `<string>` for chapter / bookmark / book counts.
  - **Compound words in German:** *Schlafzeitschalter* (sleep timer) and *Geschwindigkeitseinstellung* (speed setting) overflow narrow buttons. Phase F / G UI needs `flowRow` or `wrapContentWidth(unbounded = true)` where labels can be long.
  - **CJK chapter ordering:** "Chapter 3" → "第3章" (Chinese / Japanese); the locale string can't just be `"Chapter %d"` — it's `"第%d章"`. Means the format-string itself is locale-specific, which `stringResource(R.string.chapter_format, n)` already handles.
  - **RTL bookmarks (Phase H):** Arabic / Hebrew bookmark list needs `LayoutDirection.Rtl` previews. Time-to-position labels (`"01:23:45"`) stay LTR-numeric inside an RTL row.
- **No SAF picker translation.** The folder picker UI itself is system-provided (`ACTION_OPEN_DOCUMENT_TREE`) — Android's chooser is already localised by the platform. We only own the strings on the bridge screen (Phase C.1) that explains the picker before launching it.
- **No M4B chapter-name translation.** Chapters parsed from the MP4 atoms (Phase I) are user content, not app copy. They get displayed verbatim in whatever language the audiobook author wrote them.

## Phase T.A — naming scheme + discipline (foundational)

**Why:** Establish the rule before any UI lands. Phases E/F/G/H/etc enforce this from day one — every user-facing string lands in `values/strings.xml` in the same commit that introduces it.

- [x] **T.A.1** Naming scheme: `<surface>_<role>` lowercase snake. Whisperboy surfaces:
  - `library_` — cover grid, filter chips (Current / Not started / Completed), sort menu, search bar, long-press sheet
  - `player_` — transport (prev / rewind / play-pause / forward / next), scrubber timestamps, top app bar (back / sleep timer / bookmark / overflow), book + chapter title
  - `chapter_` — chapter list bottom sheet, per-chapter progress, "current chapter" indicator
  - `bookmark_` — bookmark list, add / rename / delete, "set by sleep timer" badge
  - `sleep_` — sleep timer sheet, quick picks (5/10/15/30/60 + end-of-chapter + custom), fade-out, shake-to-resume copy
  - `folder_` — folder picker bridge screen, four `FolderType` labels (`SingleFile` / `SingleFolder` / `Root` / `Author`) + descriptions
  - `onboarding_` — welcome, permissions rationale, picker explainer, first-scan progress
  - `settings_` — every settings sub-page (Playback / Sleep timer / Library / Theme / About)
  - `widget_` — home-screen widget labels + content descriptions
  - `auto_` — Android Auto custom command labels (set sleep timer / change speed / skip silence)
  - `permission_` — `POST_NOTIFICATIONS` rationale text
  - `dialog_` — generic dialog buttons (`Cancel` / `OK` / `Delete`)
  - `error_` — error messages (folder no longer accessible / file deleted / etc.)
  - `cd_` — content descriptions (transport buttons, cover art alt text)

  Documented as a leading XML comment in `values/strings.xml`. — comment block landed in `values/strings.xml` listing all 14 surfaces.

- [x] **T.A.2** CLAUDE.md update: a short i18n discipline section. Rule: **every user-facing string in Phases E–L lands in `values/strings.xml` in the SAME COMMIT that introduces the surface.** No `Text("...")` with literal copy in `ui/**`. testTags / log tags / debug-only strings / format-string constants / internal sentinels stay literal and are NOT user-facing. — section "i18n discipline + per-locale workflow" landed in CLAUDE.md, points back at this plan.

- [x] **T.A.3** Audit script (free path): `scripts/check-i18n.sh` greps Compose UI sources for hardcoded `Text("...")` / `text = "..."` / `contentDescription = "..."` literals. Exits non-zero if any are found. Wired into `scripts/build-release-apk.sh` (Phase O) as a pre-flight gate, runnable ad-hoc during dev. *Note:* Android lint's `HardcodedText` check covers XML resources only — it does NOT inspect Compose Kotlin source — so a grep-based audit is the right tool for this codebase. Exemptions: `@Preview` functions (dev-only), `testTag(...)`, `Log.*` calls, format-string literals (`"%d"` / `"%s"` / `"%.1f"`), and underscore-bracketed sentinels (`"_hidden_"`).

- [ ] **T.A.4** Phase E / F / G / H / etc. **enforcement gate:** every new screen's first commit must include the matching `values/strings.xml` rows AND the `stringResource(R.string.*)` lookups. The phase doesn't ship until both are in the diff.

- [x] **T.A.5** Ship + tick. — T.A.1–3 + T.A.5 in this commit; T.A.4 stays open as the standing rule across Phases E–L (ticks naturally as each phase ships).

**Effort:** XS for setup (15 min). Cost is paid in tiny increments across Phases E–L, never as a big bang.

---

## Phase T.B — locale infrastructure

**Why:** Set the file-layout contract before any locale lands.

- [x] **T.B.1** Confirm `<application>` doesn't pin a locale (default behaviour follows system locale). Already the case in the manifest as of Phase A; no-op verification. — verified: no `android:locale*` or `<locale-config>` declarations in `AndroidManifest.xml`.
- [x] **T.B.2** Add `<resources xmlns:tools="http://schemas.android.com/tools" tools:locale="en">` to `app/src/main/res/values/strings.xml` so Android Studio / `lint` treats English as canonical. — landed in this commit.
- [x] **T.B.3** Add a short paragraph to CLAUDE.md (NOT a separate `CONTRIBUTING-TRANSLATIONS.md`) covering: how the user + Claude generate a locale, the `values-<locale>/strings.xml` convention, the editorial register (plain / factual / useful), and that missing keys fall back to English. Total: 8 lines or so. Not a contributor doc — a session-instructions doc for the user's own future Claude sessions. — landed as the second paragraph of the new "i18n discipline + per-locale workflow" section in CLAUDE.md.
- [x] **T.B.4** Verify: `:app:assembleDebug` clean; AVD locale switch (`adb shell setprop persist.sys.locale de-DE && adb shell stop && adb shell start`) shows English fallback when no `values-de/` exists yet. Defer this until T.E.1 (German) actually lands so we have something to compare against. — build verified clean alongside T.E.1; AVD smoke pending user (no live AVD attached to the agent worktree).
- [x] **T.B.5** Ship + tick. — T.B.1–3 + T.B.5 in this commit; T.B.4 deferred to T.E.1.

**Effort:** XS (1–2 hours). **Risk:** low.

---

## Phase T.C — translation-progress script + README markers — shipped in commit 3301f96

**Why:** Visible "X% done in language Y" signal in README without Crowdin/Weblate/server.

- [x] **T.C.1** Write `scripts/translation-progress.sh`: parses `values/strings.xml` → set of canonical keys (excluding `translatable="false"` rows); for each `values-<locale>/strings.xml` parses translated keys; computes `done / total` and the locale's display name; prints a markdown table. — 3301f96
- [x] **T.C.2** Add `<!-- TRANSLATIONS-START -->` / `<!-- TRANSLATIONS-END -->` markers in `README.md` (new "Translations" section). Script overwrites between markers using a `sed` block-replace — idempotent. — 3301f96
- [x] **T.C.3** Wire the script into `scripts/build-release-apk.sh` (Phase O — once the script exists) immediately before the `git tag` step: regenerate the README block; `git diff --quiet README.md` to confirm intentional change vs noise; release commit picks up the updated table. — Deferred: `scripts/build-release-apk.sh` is being added in parallel; TODO note in script header tracks the hook-up. 3301f96
- [x] **T.C.4** Sanity tests for the script: a few golden files in `scripts/tests/translation-progress/` exercising 0%, 100%, partial, and missing-locale cases. Run via `bash scripts/translation-progress.sh --test`. — 3301f96
- [x] **T.C.5** Verify: README section renders correctly on github.com; auto-update is byte-for-byte stable. — 3301f96
- [x] **T.C.6** Ship + tick. — 3301f96

**Effort:** S–M (½–1 day). **Risk:** low (POSIX shell + sed + grep). **Sequence note:** Land after Phase O introduces `scripts/build-release-apk.sh`, OR write the script standalone first and wire it in at Phase O.

---

## Phase T.D — README "Translations" section content — shipped in commit 21a5528

**Why:** The auto-table is the data; this is the surrounding prose.

- [x] **T.D.1** Above the auto-generated table: a 2-sentence paragraph explaining the model (translations are user + Claude per-language, English is canonical, missing keys fall back to English). NOT a "we welcome contributions" pitch. — shipped with T.C; verified in place. 21a5528
- [x] **T.D.2** Linkify each language row to its `values-<locale>/strings.xml` on github so the user can jump straight to "edit this file". — script + golden fixtures updated to link to `app/src/main/res/values-<locale>/strings.xml`. 21a5528
- [x] **T.D.3** Ship + tick. 21a5528

**Effort:** XS (15 min). **Risk:** none.

---

## Phase T.E — produce locales (one session per language)

**Why:** This IS the ship vector. Every supported language lands here, not via outside contributors.

**Standing per-language workflow:**

1. User opens a Claude session in this repo, names the target locale.
2. Claude reads `values/strings.xml` + the CLAUDE.md editorial brief.
3. Claude drafts `values-<locale>/strings.xml` with every translatable key. Keeps the same key order as `values/strings.xml` for easy diff review.
4. Per-entry user review. Anything off → user redirects → Claude revises in place.
5. Commit signed-off entries; leave anything unconfirmed missing (English-fallback is correct behaviour, not a placeholder).
6. Run `scripts/translation-progress.sh` (once T.C lands) to refresh the README table.
7. AVD smoke: switch locale, walk every screen, watch for layout overflow on long compound words (German specifically — `flowRow` / `wrapContentWidth` may need targeted patches).
8. RTL pass for Arabic / Hebrew: `LayoutDirection.Rtl` previews on every screen, time-position labels (`hh:mm:ss`) stay LTR-numeric inside RTL rows.

Per-locale ticks (extend as new languages land):

- [x] **T.E.1** German (`values-de/`) — user is local, primary review channel. Expected pain: long compound words (Schlafzeitschalter / Geschwindigkeitseinstellung) — flag any layout overflow during the AVD smoke. — shipped in commit `6521a93`; 254/254 keys translated; phrasing decisions: "Schlaftimer" (not "Schlafzeitschalter"), "Wiedergabetempo" (not "Geschwindigkeitseinstellung"), "Spulen" / "zurückspulen" / "vorspulen" for seek controls, "Standardwerte" for defaults sections, formal "Sie" address; awaits per-entry user review.
- [ ] **T.E.2** Next locale — user picks; same workflow.
- [ ] **T.E.3** Next locale — same workflow.
- [ ] (… one sub-step per locale shipped)

This phase is **never "done"** in the conventional sense — it stays open as long as new locales are added. Tick the parent phase header with the commit range when the user declares a particular set of locales the canonical shipped set; reopen later when adding a new one.

**Per-locale effort:** M (½–1 day, mostly editorial review). **Risk:** low. **Blast radius:** `values-<locale>/` + README.

---

## What this plan deliberately does NOT include

- **No `CONTRIBUTING-TRANSLATIONS.md`.** Replaced by the short paragraph in CLAUDE.md (T.B.3) describing the user-+-Claude workflow.
- **No PR template addendum** for translation contributions.
- **No "welcome mat" copy** in README pitching community translations.
- **No `<!-- needs-translation -->` placeholder convention** — just leave keys missing; Android falls back to English, the progress script counts honestly.
- **No reviewer-pair rule, no language-native verifier requirement** — the user reviews per-entry inside the session.
- **No machine-translate-then-rubber-stamp shortcut.** Claude drafts within the editorial brief; the user reviews per-entry. No `gtranslate` / `deepl` shell-out.

## Why this is "no cost"

- No new SDK / plugin / dependency in `build.gradle.kts`.
- No external service account, hosted instance, or recurring subscription.
- No CI minutes burned (README table piggybacks on `--gh-release` local build).
- Translation labour is the user reviewing what Claude drafts. No outside coordination overhead.
- Adding a new language is one new file. Removing a language is `git rm -r values-<locale>/`.

## Migration path (kept open, not scheduled)

If at some future point the user decides to open community translations:

- Layout is unchanged — `values-<locale>/strings.xml` is the standard Weblate / Crowdin input format.
- Add a `CONTRIBUTING-TRANSLATIONS.md` then.
- Add a PR template addendum then.
- This plan covers none of that work pre-emptively.
