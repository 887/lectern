# whisperboy — open-source licenses plan

## Status: ✅ DONE

## Why

The app ships under MIT (`LICENSE` in repo root). Every dependency that ships in the APK is **Apache License 2.0** — every `androidx.*` module, the kotlinx ecosystem, Media3 (ExoPlayer + MediaSession + UI). Apache 2.0 §4 requires that downstream binary distributions preserve copyright + NOTICE entries from upstream artifacts. The Android-conventional way to satisfy this is an "Open-source licenses" sub-page: a list of every shipping dep with name, version, license SPDX, and license body.

`docs/plans/main.md` already schedules an About section in Phase K.6 ("version, build hash, license, link to GitHub repo"). This plan extends K.6 with an "Open-source licenses" entry — a sub-page that renders a build-time-generated inventory.

## Approach (locked)

- **Build-time inventory, zero runtime deps.** Use the [`app.cash.licensee`](https://github.com/cashapp/licensee) Gradle plugin. It walks the resolved `releaseRuntimeClasspath` at configuration time and writes a JSON inventory; nothing is added to the APK at runtime. Licensee is Apache 2.0 itself, build-time only, and is the same pattern adopted in [`tonearmboy/docs/plans/oss-licenses.md`](../../../tonearmboy/docs/plans/oss-licenses.md) and [`shutterboy/docs/plans/oss-licenses.md`](../../../shutterboy/docs/plans/oss-licenses.md) — keeping the three sibling apps consistent.
- **Compose-rendered sub-screen.** A new `LicensesScreen.kt` reads the generated `artifacts.json` from `assets/licenses/` and renders a `LazyColumn` of cards. Tapping a row reveals the license body. License bodies (Apache-2.0, MIT, EPL-1.0) ship as raw text assets — finite set, three at most.
- **Robolectric-driven catalog test.** Parses the generated JSON, asserts non-empty, asserts every entry has a known SPDX and a backing license-text asset, asserts a known sample of shipping deps is present.
- **Report-only allowlist in v1.** Declare the allowlist (`Apache-2.0`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`) but do not enforce in v1.
- **i18n discipline carries over.** Per CLAUDE.md, every user-facing string lands in `values/strings.xml` in the same commit that introduces the surface. New surface prefix: `licenses_`. Naming: `<surface>_<role>` lowercase snake.

## Inventory snapshot (informational — confirmed `2026-05-04` against `gradle/libs.versions.toml` + `app/build.gradle.kts`)

Whisperboy's dep tree is currently the smallest of the three sibling apps. Apache 2.0 across the board.

**Ships in APK (`implementation`):**
- `androidx.core:core-ktx`
- `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-{runtime-ktx,runtime-compose,viewmodel-compose,viewmodel-navigation3}`
- `androidx.compose.{ui,material3,ui-tooling-preview}` via the Compose BOM
- `androidx.navigation3:navigation3-{runtime,ui}`
- `androidx.media3:media3-{exoplayer,session,ui}`

The plan accommodates the deps the rest of main.md will add as it ships — Room (Phase B), DataStore (settings), kotlinx-serialization (chapter parsing), Coil 3 (cover art), `androidx.documentfile` (SAF tree walk), `androidx.work` if a debounced library scanner lands. All Apache 2.0 in the upstream catalog. The Licensee plugin re-runs every build, so newly-added deps appear automatically.

**Test-only (`testImplementation` / `androidTestImplementation`, excluded from the APK):**
- `junit:junit:4.13.2` — **EPL-1.0** (Eclipse Public License 1.0). Test-scope only; never shipped.
- `androidx.test.*`, `androidx.compose.ui.test.*`, `org.jetbrains.kotlinx:kotlinx-coroutines-test` — Apache 2.0
- (Robolectric will arrive when JVM-only data-layer tests land per main.md; MIT.)

**Build-only / KSP / Gradle plugins (never shipped):** AGP, Kotlin Compose / Serialization plugins, foojay-resolver — Apache 2.0.

Conclusion: **MIT app license is correct. No GPL anywhere. No dep prevents MIT.**

## Phase A — Licensee plugin + generated inventory — shipped in commit `a16612f`

**Why:** every later phase reads the JSON this phase generates. Independent of the About-screen UI; can land before main.md Phase K.

- [x] **A.1** Add Licensee version to `gradle/libs.versions.toml` and a `[plugins]` entry: `licensee = { id = "app.cash.licensee", version.ref = "licensee" }`. Pinned to `1.13.0` (the version tonearmboy + shutterboy ship).
- [x] **A.2** Apply `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [x] **A.3** Configure the plugin block: `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause") }`. Reporting only in v1. (No `allowDependency` for junit needed — `testImplementation(libs.junit)` doesn't enter the Android variant runtime classpath Licensee inspects.)
- [x] **A.4** Wire a Gradle task to copy `app/build/reports/licensee/android<Variant>/artifacts.json` into `app/src/main/assets/licenses/artifacts.json` and hook it as a dependency of `merge<Variant>Assets`. Done in `app/build.gradle.kts` via `androidComponents.onVariants`.
- [x] **A.5** Add `app/src/main/assets/licenses/Apache-2.0.txt`, `MIT.txt`, `BSD-3-Clause.txt`, `BSD-2-Clause.txt`. SPDX-canonical text shipped (mirrored from tonearmboy, whose copies came from `https://spdx.org/licenses/<spdx>.txt`).
- [x] **A.6** Run `:app:licenseeAndroidDebug` + `:app:assembleDebug`. Inventory has 231 entries; SPDX ids observed: `Apache-2.0`, `BSD-3-Clause`, `MIT` — all in the allowlist. Spot-checked `androidx.media3:media3-exoplayer`, `androidx.room:room-runtime`, `io.coil-kt.coil3:coil-compose`.
- [x] **A.7** Shipped.

## Phase B — `LicensesScreen` Compose UI — shipped in commit `a16612f`

**Why:** the user-facing surface that fulfils the Apache 2.0 NOTICE requirement and extends main.md K.6.

**Sequencing note:** depends on the SettingsScreen scaffold from main.md K.1 (Material 3 settings list with a "About" section). If K is not yet underway, this plan can land its own minimal About sub-page (build-version + GitHub link + license link + Licenses entry) and let main.md K expand it later — same shape, smaller initial surface.

- [x] **B.1** Add `app/src/main/java/com/eight87/whisperboy/ui/settings/LicensesScreen.kt`. M3 chrome — `Scaffold` + `TopAppBar` with back arrow, `LazyColumn` of `Card`s (`surfaceContainerHigh`) matching the existing AboutScreen style.
- [x] **B.2** ~ViewModel~ — folded the asset reader into a pure top-level function `loadLicensesFromAssets(context)` invoked from `remember(context) { … }`. Single cheap I/O + parse, no observable state, so the ViewModel layer is overkill. Same shape tonearmboy adopted.
- [x] **B.3** Define `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved at construction by reading `assets/licenses/<spdx>.txt`. Missing SPDX → badge falls back to "Unknown license" copy.
- [x] **B.4** UI: `LazyColumn` of dep cards. Each card: `<artifactId> <version>` (title) + `<groupId>` (subtitle) + SPDX badge (pill in `secondaryContainer`). Tap → `AlertDialog` shows the license body in monospaced text, vertically scrollable.
- [x] **B.5** Wire navigation. New `LicensesRoute` in `NavigationKeys.kt`; `entry<LicensesRoute>` in `WhisperboyApp.kt`. The existing About → "Open-source licenses" row's snackbar-stub `onClick` replaced with `onLicensesClick` callback threaded from `WhisperboyApp` (`backStack.add(LicensesRoute)`).
- [x] **B.6** Strings shipped in `values/strings.xml` under `licenses_` prefix: `licenses_title`, `licenses_back_cd`, `licenses_loading`, `licenses_load_error`, `licenses_spdx_badge_cd`, `licenses_unknown_spdx`, `licenses_dialog_close`.
- [ ] **B.7** AVD verification deferred — agent prompt explicitly excluded AVD-verify from this inch.
- [x] **B.8** Shipped.

## Phase C — Tests + audit discipline — shipped in commit `a16612f`

**Why:** keep the inventory honest as deps churn — and whisperboy's dep tree will grow as main.md ships, so the test catches accidental additions.

- [x] **C.1** `LicensesCatalogTest` — pure-JVM (no Robolectric dep in whisperboy yet). Reads `app/src/main/assets/licenses/artifacts.json` directly via `File("src/main/assets/...")` (Gradle's `:app:test` runs with `user.dir = app/`). Asserts non-empty, every SPDX in the JSON is in the allowlist and has a matching `<spdx>.txt`, and the catalog contains known shipping samples (`androidx.media3:media3-exoplayer`, `androidx.room:room-runtime`, `io.coil-kt.coil3:coil-compose`).
- [ ] **C.2** `LicensesScreenTest` deferred — Compose UI tests under Robolectric aren't wired in whisperboy yet (no Robolectric dep). Re-open once Robolectric arrives.
- [ ] **C.3** AVD smoke deferred — agent prompt explicitly excluded AVD-verify from this inch.
- [x] **C.4** "Open-source licenses" subhead already lives in the repo `CLAUDE.md` (it was already authored as part of the Plan files index). The catalog test + allowlist live alongside the plugin block in `app/build.gradle.kts`; that file is the canonical place to extend the allowlist.
- [ ] **C.5** Cross-link to main.md K.6 deferred — main.md K.6 (About sub-page) already shipped before this plan landed, and the "Open-source licenses" row is now wired to the new `LicensesScreen` instead of a snackbar stub. No tick required on main.md.
- [x] **C.6** Shipped.

## Out of scope (revisit if pain emerges)

- Failing the build on a disallowed SPDX (`failOnDisallowed = true`) — defer until the v1 inventory has been reviewed once.
- A separate "Acknowledgements" screen for non-binary attributions. Revisit if non-code assets (icons, sounds) land.
- Multi-locale license text. SPDX bodies are English-only by upstream convention.
- A DEPENDENCIES.md / NOTICE file in repo root duplicating the runtime list.
