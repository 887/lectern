# whisperboy — Material 3 Expressive (M3E) starter plan

## Status: PLANNED

## Why this exists

The user's sister project `tonearmboy` did the leg-work: a side-by-side
of the Android 16 system Settings UI vs the app's own settings showed
the system-side has been redesigned around **Material 3 Expressive
(M3E)** — vibrant per-row coloured circular icon avatars, a clearly
elevated card surface tier vs the page background, divider-less stacks,
the new motion / shape / typography defaults. tonearmboy was on
baseline M3 and reading flat / monochrome by comparison.

**Same architectural shape applies here.** Whisperboy was scaffolded
from the same toolchain and design conventions as tonearmboy; the
moment the Settings / Library / Player / Sleep-timer chrome lands on
a screen-shaped list of cards, you'll get the same flatness from the
same root cause unless we adopt M3E up front.

This is a **starter plan**: it captures the M3E patterns to follow.
It does not prescribe specific whisperboy UI changes — the actual
sweep should follow whatever screens whisperboy ends up shipping,
including any Android Auto / `MediaLibraryService` browse-tree
chrome that sits on top of the Compose surfaces.

## The four M3E patterns to follow

(All four together produce the "happy" Settings look. Each is cheap
in isolation; the combination matters.)

1. **Surface tier ladder.** Page background reads from
   `MaterialTheme.colorScheme.surface`; grouped cards / row clusters
   read from `MaterialTheme.colorScheme.surfaceContainer` (or
   `surfaceContainerHigh` if AMOLED dark needs more separation). Do
   NOT use `surface` for both. Don't lean on shadow elevation —
   `defaultElevation = 0.dp` with the surface-tier token does the
   lift.
2. **`MaterialExpressiveTheme`** in place of plain `MaterialTheme` at
   the app theme entry. Pulls in expressive motion / typography /
   shapes. Requires
   `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` until
   `material3:1.5.0` ships stable.
3. **Coloured circular row-icon avatars.** A `Box` with
   `Modifier.size(40.dp).clip(CircleShape).background(accent.container)`
   wrapping a `24.dp` filled `Icon(tint = accent.onContainer)`.
   `accent` is per-category and comes from a small `data class
   CategoryAccent(val container: Color, val onContainer: Color)`
   defined alongside the theme — M3 only ships three container
   pairs (`primary` / `secondary` / `tertiary`) and the system
   Settings spreads ~6 hues. Hand-pick.
4. **Filled glyph icons** at the avatar layer (`Icons.Filled.*`,
   not `Icons.Outlined.*`). Outlined glyphs read weak inside a
   coloured circle — they're meant for transparent-background row
   leads, not avatars.

Two supporting niceties:

- Card shape: `RoundedCornerShape(28.dp)` or
  `MaterialTheme.shapes.extraLarge` — matches the M3E "extra-large"
  default the system Settings uses for groups.
- Divider-less list rows inside a card: `Column(verticalArrangement
  = Arrangement.spacedBy(2.dp))`. The surface-tier gap between
  cards does the visual separation; in-card rows just stack.

## Phase A — dependency + theme entry

- [ ] **A.1** Bump `androidx.compose.material3:material3` to `1.4.0`
  (or later). Verify Licensee allowlist still passes — see
  `CLAUDE.md` license workflow.
- [ ] **A.2** Add explicit
  `androidx.compose.material:material-icons-extended` dep — no
  longer transitive in `material3:1.4.0+`.
- [ ] **A.3** At the app theme entry, wrap the root in
  `MaterialExpressiveTheme(...)` instead of `MaterialTheme(...)`.
  Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at the
  file level once.
- [ ] **A.4** Switch `darkColorScheme()` /  `lightColorScheme()`
  calls to `expressiveDarkColorScheme()` / `expressiveLightColorScheme()`.

## Phase B — surface-tier discipline

- [ ] **B.1** Audit every `colorScheme.surface` /
  `colorScheme.background` call site. Page-level callers stay on
  `surface`; card-level callers move to `surfaceContainer`.
- [ ] **B.2** Audit every `Card` / `Surface` / equivalent grouped
  list-row container. Each must use `containerColor =
  surfaceContainer`, `defaultElevation = 0.dp`,
  `RoundedCornerShape(28.dp)`.
- [ ] **B.3** Add a unit test asserting
  `darkColorScheme.surface != darkColorScheme.surfaceContainer` —
  cheap regression guard.

## Phase C — `CategoryAccent` + per-row avatars

- [ ] **C.1** Add `data class CategoryAccent(val container: Color,
  val onContainer: Color)` next to the theme.
- [ ] **C.2** Define ~5–6 hand-picked accent pairs for each surface
  that needs them. whisperboy's category split is its own — pick
  naturals from whatever the settings catalog ends up shaped like
  (e.g. Appearance / Library sources / Playback / Sleep timer /
  Bookmarks / About). Light + dark variants.
- [ ] **C.3** New composable
  `SettingsCategoryIcon(icon: ImageVector, accent: CategoryAccent,
  contentDescription: String?)` per the pattern in §3 above.
- [ ] **C.4** Wire it into the settings catalog row renderer.

## Phase D — the rest of the chrome

- [ ] **D.1** Library grid (book covers) chrome — top app bar, FAB,
  empty state. Pull through `surfaceContainer`-on-`surface`.
- [ ] **D.2** Player screen — chapter list cards, sleep-timer sheet,
  speed/skip controls. Verify they respect the new container
  surface.
- [ ] **D.3** Bookmark / chapter sheets — `ModalBottomSheet` defaults
  pick up M3 tokens automatically; confirm the new expressive
  containers flow through.
- [ ] **D.4** **`MediaLibraryService` browse-tree branding (Android
  Auto / Automotive):** while M3E doesn't directly affect the AAOS
  rendering of the browse tree (the car system theme dominates),
  any custom in-car UI we add — e.g. a settings activity reachable
  from the car launcher — should use the same theme entry. Note
  for the Phase N (AAOS) branch.

## References

- `tonearmboy/docs/plans/m3-expressive.md` — the full plan that
  triggered this one; cites all the upstream docs and the
  `material3` artifact version-roll story.
- developer.android.com/develop/ui/compose/designsystems/material3
- developer.android.com/jetpack/androidx/releases/compose-material3
- m3.material.io/develop/android/jetpack-compose
- github.com/android/androidify — Google's M3E reference app.

## Risk / unknowns

- **Wallpaper-driven Material You vs hand-picked accents:** the
  per-category `CategoryAccent` plan is intentionally NOT driven
  off `dynamicDarkColorScheme(LocalContext.current)` — letting the
  user's wallpaper palette override the per-category intent defeats
  the colour-coding. Document the decision in the theme file.
- **Light mode pass:** verify after Phase C that
  `expressiveLightColorScheme()` + the same accent class reads OK
  against the lighter background.
- **Scope creep:** keep `Icons.Outlined.*` → `Icons.Filled.*` swap
  at the settings-row leading-icon layer only. Don't churn icons
  inside the player / chapter list / sleep-timer dial.
- **Sister-app sharing:** the `CategoryAccent` data class + the
  `SettingsCategoryIcon` composable are obvious candidates for the
  shared-code module discussed in `docs/plans/sharing-analysis.md`
  if/when that lands. For now each app keeps its own copy.
