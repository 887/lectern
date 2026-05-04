# whisperboy — open-source licenses plan

## Status: 🟡 PLANNED

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

## Phase A — Licensee plugin + generated inventory

**Why:** every later phase reads the JSON this phase generates. Independent of the About-screen UI; can land before main.md Phase K.

- [ ] **A.1** Add Licensee version to `gradle/libs.versions.toml` and a `[plugins]` entry: `licensee = { id = "app.cash.licensee", version.ref = "licensee" }`. Pin to the latest stable.
- [ ] **A.2** Apply `alias(libs.plugins.licensee)` in `app/build.gradle.kts`.
- [ ] **A.3** Configure the plugin block: `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allowDependency("junit", "junit", "4.13.2") { because("EPL-1.0; test-scope only, not shipped") } }`. Reporting only in v1.
- [ ] **A.4** Wire a Gradle task to copy `app/build/reports/licensee/release/artifacts.json` to `app/src/main/assets/licenses/artifacts.json`. Hook as a dependency of `mergeReleaseAssets` and `mergeDebugAssets`.
- [ ] **A.5** Add `app/src/main/assets/licenses/Apache-2.0.txt`, `MIT.txt`, `EPL-1.0.txt`. Source from SPDX official text. Top-of-file comment notes SPDX id and source URL.
- [ ] **A.6** Run `:app:assembleDebug` once. Verify the generated JSON exists, parses, and contains a non-empty array. Spot-check three entries against the inventory snapshot.
- [ ] **A.7** Ship + tick.

## Phase B — `LicensesScreen` Compose UI

**Why:** the user-facing surface that fulfils the Apache 2.0 NOTICE requirement and extends main.md K.6.

**Sequencing note:** depends on the SettingsScreen scaffold from main.md K.1 (Material 3 settings list with a "About" section). If K is not yet underway, this plan can land its own minimal About sub-page (build-version + GitHub link + license link + Licenses entry) and let main.md K expand it later — same shape, smaller initial surface.

- [ ] **B.1** Add `app/src/main/java/com/eight87/whisperboy/ui/settings/LicensesScreen.kt`. Use the same chrome the rest of settings adopts in main.md K (M3 list rows, scaffolded top app bar with back).
- [ ] **B.2** Add `LicensesViewModel`. Reads `assets/licenses/artifacts.json` once at init via `AssetManager.open(...)` + `kotlinx.serialization.json`. Exposes `StateFlow<List<LicenseEntry>>` (entries pre-sorted by `groupId:artifactId`).
- [ ] **B.3** Define `LicenseEntry { groupId, artifactId, version, spdxId, licenseText: String? }` — `licenseText` resolved at construction by reading `assets/licenses/<spdx>.txt`. Unknown SPDX → `licenseText = null` and the row renders an "Unknown SPDX" warning.
- [ ] **B.4** UI: `LazyColumn` of list rows. Row title: `<artifactId> <version>`. Supporting text: `<groupId> • <spdxId>`. Tap → expand inline (or open a Material3 `Dialog` showing the license body in monospaced text, scrollable).
- [ ] **B.5** Wire navigation. Add an "Open-source licenses" entry to the About section in `SettingsScreen` (icon: `Icons.Outlined.Article`), positioned alongside / below the existing license-link row.
- [ ] **B.6** Strings: every label resource-backed in `values/strings.xml`. Keys: `licenses_screen_title`, `licenses_row_label`, `licenses_row_supporting`, `cd_licenses_back`, `licenses_unknown_spdx`.
- [ ] **B.7** Verify on AVD: open Settings → tap About / "Open-source licenses" → list renders → tap a row → license body appears. Per CLAUDE.md, UI changes are not done on the strength of unit tests + build alone.
- [ ] **B.8** Ship + tick.

## Phase C — Tests + audit discipline

**Why:** keep the inventory honest as deps churn — and whisperboy's dep tree will grow as main.md ships, so the test catches accidental additions.

- [ ] **C.1** `LicensesCatalogTest` (Robolectric, JVM-only — once Robolectric is wired by main.md): parses `assets/licenses/artifacts.json`; asserts non-empty; asserts every entry has a recognized SPDX from the allowlist and a backing license-text asset; asserts the catalog contains a known shipping sample (`androidx.media3:media3-exoplayer`, `androidx.compose.material3:material3` via the BOM, `androidx.navigation3:navigation3-runtime`).
- [ ] **C.2** `LicensesScreenTest` (Compose UI test under Robolectric, `ui-test-junit4`): renders, scrolls, expanding a row reveals license text.
- [ ] **C.3** AVD smoke per CLAUDE.md.
- [ ] **C.4** Add a one-paragraph "Licenses" subhead to `CLAUDE.md`: when adding a new `implementation` dep, run `:app:licenseeReport` and confirm the SPDX is in the allowlist; if not, either add it to `licensee.allow(...)` (preferred) or document the exemption with a `because("...")`. Include a pointer to this plan.
- [ ] **C.5** Cross-link from main.md K.6 to this plan when K.6 ships, and tick the relevant K.6 sub-step.
- [ ] **C.6** Ship + tick.

## Out of scope (revisit if pain emerges)

- Failing the build on a disallowed SPDX (`failOnDisallowed = true`) — defer until the v1 inventory has been reviewed once.
- A separate "Acknowledgements" screen for non-binary attributions. Revisit if non-code assets (icons, sounds) land.
- Multi-locale license text. SPDX bodies are English-only by upstream convention.
- A DEPENDENCIES.md / NOTICE file in repo root duplicating the runtime list.
