# cold-start-perf — keep whisperboy fast

> Reference: [Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
>
> Sibling plan: [`tonearmboy/docs/plans/cold-start-perf.md`](https://github.com/887/tonearmboy/blob/main/docs/plans/cold-start-perf.md)

This plan is a **maintenance contract**, not a punch list of work
to do. Whisperboy's `WhisperboyActivity` is currently 22 lines and
cold-starts inside the realistic Compose floor (~300–400 ms on real
hardware, ~1 s on the AVD). The plan exists so we don't slowly
regress it as features land. Tonearmboy spent a session clawing back
~400 ms after letting `MainActivity` grow to 220 lines; this plan
exists so that doesn't happen here.

## What kept whisperboy fast (lessons from tonearmboy's session)

The single biggest variable is **what happens synchronously between
`super.onCreate()` and `setContent { ... }` returning** — and what
the body of `setContent { }` allocates / subscribes / measures on
the first composition.

Whisperboy's activity (22 LOC) does effectively nothing on the
critical path:
```kotlin
super.onCreate(savedInstanceState)
enableEdgeToEdge()
setContent {
  WhisperboyTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      WhisperboyApp()
    }
  }
}
```

Tonearmboy at the start of the perf session was 220 LOC with:
- A custom splash hold (`splash.setKeepOnScreenCondition { ... }`) gating dismissal on a Media3 controller handshake — kept the splash up *longer*, not shorter
- Three `applicationScope.launch { ... }` chains in onCreate (watcher service re-arm, first-launch initialise, controller connect kick) all running synchronously on the critical path
- Six `collectAsState` (not `collectAsStateWithLifecycle`) calls inline in `setContent` — each starts collecting during composition, each first emission triggers a root recomposition cascade
- Nine `animateColorAsState(tween(300))` calls in the theme to crossfade chrome between album palettes — allocates 9 `State<Color>` + 9 `LaunchedEffect`s on every cold start, even when no album is playing
- A `BoxWithConstraints` (which is a `SubcomposeLayout`) at the top of the app composable, just to read screen height for sheet drag math
- The full NowPlaying surface mounted unconditionally inside the sheet stack even when sheet was collapsed

After stripping all of these, tonearmboy matches shutterboy to
within ~20 ms on the same AVD. The lessons are below as guards.

## Phase A — keep WhisperboyActivity ≤30 lines — standing rule

- [ ] **A.1** **Don't add a splash-hold mechanism.** The default `installSplashScreen()` flow is fine — splash dismisses on first activity frame. A `setKeepOnScreenCondition { ... }` that gates dismissal on async work *extends* the splash visibility, which feels like a hang to the user.
- [ ] **A.2** **Don't run application work in `onCreate`.** Things like `applicationScope.launch { firstLaunchInitialise() }` belong in a `LaunchedEffect(Unit) { }` inside `setContent` — they execute post-first-frame, off the critical path.
- [ ] **A.3** **Don't open the Room database in `onCreate`.** Lazy from the first repository call instead.
- [ ] **A.4** When tempted to put intent / deeplink handling here, keep `handleIntent(...)` minimal — read the extra, set a Compose state, **clear the extra back into the intent via `setIntent(intent)` so a later activity recreation doesn't re-fire the same deeplink**. (Tonearmboy validated in `3436ae2`.)
- [ ] **A.5** **Lazy-mount heavy surfaces behind sheets.** When a `ModalBottomSheet` / `BottomSheetScaffold` houses a complex composable (e.g. the full now-playing surface — cover, scrubber, transport, chapter list), do NOT mount the surface inside the sheet stack at all times. Mount it only when the sheet is expanded; render only the slim collapsed-state bar when collapsed. Tonearmboy `3436ae2` reclaimed real cold-start time by deferring the full NowPlaying tree behind the sheet's `targetValue`.

## Phase B — keep `setContent { }` shallow — standing rule

- [ ] **B.1** **Use `collectAsStateWithLifecycle`, not `collectAsState`.** The `WithLifecycle` variant defers Flow collection to `Lifecycle.STARTED`, which means DataStore reads (and the recomposition cascades their first emissions trigger) don't happen during the first composition pass.
- [ ] **B.2** **Don't allocate per-color `animateColorAsState` chains in the theme.** Pure function calls recompose the theme on input change without per-color State allocation. The animation is only worth it if the user asks for it; default to direct values.
- [ ] **B.3** **Don't use `BoxWithConstraints` to read screen size.** It's a `SubcomposeLayout`, forcing an extra composition pass for the children after parent measure. Use `LocalConfiguration.current.screenHeightDp.dp` if you need screen height ahead of layout.

## Phase C — phase-aware modifiers — standing rule

From the [best-practices page](https://developer.android.com/develop/ui/compose/performance/bestpractices):
*"By switching to the lambda version of the modifier, you can make
sure the function reads the scroll state in the layout phase. As a
result, when the scroll state changes, Compose can skip the
composition phase entirely."*

- [ ] **C.1** Prefer `Modifier.offset { IntOffset(x, y) }` over `Modifier.offset(x.dp, y.dp)` when `x` / `y` are derived from animated state — the lambda form runs in the layout phase, the value form runs in composition.
- [ ] **C.2** Prefer `Modifier.graphicsLayer { alpha = ... }` over `Modifier.alpha(...)` when alpha is derived from animated state. Same reason — defers the State read to draw, skips the composition entirely.
- [ ] **C.3** Same for `Modifier.padding { ... }` (where supported) and `Modifier.drawBehind { ... }`.

## Phase D — minimise composable bodies — standing rule

*"Composable functions can run very frequently... you should do as
little calculation in the body of your composable as you can. ... If
possible, it's best to move calculations outside of the composable
altogether."*

- [ ] **D.1** Cache derivations in `remember(keys) { ... }` blocks. Not in the body of the composable.
- [ ] **D.2** Pure derivations that don't depend on Compose state belong outside composables entirely (top-level functions).

## Phase E — lazy layout keys — standing rule

When the transcripts list / search-results list grows, **every
`items(...)` call needs a stable `key = { ... }`** so Compose's
slot-table reuse works correctly across reorderings.

- [ ] **E.1** Every `items(...)` / `items(count = ...)` call passes a `key = { ... }` argument that returns a stable identity (typically the entity id, NOT the index). **Duplicate-key crash gotcha** (tonearmboy `d75b542`): when a single `LazyVerticalGrid` mixes heterogeneous item types (e.g. section headers + book tiles), the section header's key MUST include a type discriminator (`"section:$letter"` not just `letter`) — otherwise it can collide with a tile id that happens to share the same value, and `LazyVerticalGrid` throws `IllegalArgumentException: Key X was already used`. Same rule applies when sort-aware section keys are introduced (per main.md E.3): keys must round-trip across sort changes without collision.
- [ ] **E.2** When using `LazyColumn` / `LazyVerticalGrid` for a heterogeneous content type, consider `contentType = { ... }` too — Compose's slot table reuse benefits.

## Phase F — Baseline Profile — shipped in commit `161e0e0`

Baseline Profile typically shaves 25–35% off cold start on its own.
Worth more than every phase above combined.

- [x] **F.1** Add the `androidx.baselineprofile` Gradle plugin + a sibling `baselineprofile` benchmark module.
- [x] **F.2** Record the cold-boot path (`launch → app's first usable screen visible`) via `MacrobenchmarkRule` and generate `app/src/main/baseline-prof.txt`. Placeholder committed; regenerated by `scripts/build-release-apk.sh` on next release run against an attached device/AVD.
- [x] **F.3** Add `androidx.profileinstaller:profileinstaller` to the app module.
- [x] **F.4** Wire profile generation into `scripts/build-release-apk.sh` (with `--skip-baseline` escape hatch for device-less builds).

## Phase G — measure, don't guess

- [ ] **G.1** Cold-start regression check before tagging a release: `adb shell am force-stop com.eight87.whisperboy && adb shell am start -W -n com.eight87.whisperboy/.WhisperboyActivity` × 5 runs, median should stay under 1300 ms on the AVD.
- [ ] **G.2** When `WhisperboyActivity` LOC creeps above 50 lines, **revisit this plan before merging**. That's almost always a sign that work has migrated to the critical path.

## Status

This is a **standing-rule plan** — no terminal "DONE". Phase F is the only delta-of-work piece; everything else is preventive. The cost of *not* maintaining it is paying tonearmboy's perf-clawback session over again.
