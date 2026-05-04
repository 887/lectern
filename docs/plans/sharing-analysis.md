# tonearmboy + whisperboy — shared-library cost/benefit analysis

## TL;DR

**Don't share anything yet.** Build whisperboy as an independent repo. Revisit after both apps ship 1.0 if a load-bearing hotspot has emerged.

The user's rule was: *only share if the win is biiiiig — the cost of a subrepo, separate versioning, and change-coordination overhead is real, and "shared" must clear that bar comfortably.* The candidates I found don't.

## Decision criteria

A piece of code should be promoted to a shared library only if **all five** are true:

1. **Both apps need it.** Not "could use it" — actually need it, in production code paths.
2. **The shape is the same in both.** Not just "named the same" — the inputs, outputs, and contract align without per-app branching.
3. **It's atomic.** A single concern, not a "framework". The user explicitly ruled out a shared *framework*. Atomic = "you can describe what it does in one sentence without 'and'".
4. **It's substantial.** Roughly: more than ~600 LOC, OR demanding to write correctly (parsers, audio DSP, security-relevant code), OR likely to evolve and benefit from one source of truth.
5. **The cost of duplication exceeds the cost of a subrepo.** Subrepo cost = separate versioning, separate build, change-coordination overhead, reviewer surface, release pipeline, slower agent context loading. Real cost. Not zero.

When any of the five fails, the code stays in-app and gets copied if the second app needs it. **Three similar lines of code is better than a premature abstraction.**

## Candidates considered

I went through every plausible reuse candidate between tonearmboy (already shipped) and whisperboy (planned). Each gets a verdict against the five criteria.

### Media3 controller → StateFlow projection (`PlaybackController` shape)

**What it is.** Both apps wrap Media3's `MediaController` with a coroutines/Flow projection — `StateFlow<PlaybackUiState>` consumed by Compose. Tonearmboy has `PlaybackUiController` (882 LOC, currently being split in Phase R.C of the SOLID refactor); whisperboy's Phase F.2 plans the equivalent.

**Verdict: NO.** Fails (2). The *shape* differs:

- Tonearmboy tracks: queue, queue index, ReplayGain pre-amp, repeat/shuffle modes, position within current track.
- Whisperboy tracks: current book + chapter, position within current chapter (for the scrubber) AND position within whole book (for the cover progress overlay), per-book speed/skipSilence/gain (must apply on book change), sleep timer state.

The contract differs in the type signatures. They both happen to project from a `MediaController` to a `StateFlow`, but a "shared `PlaybackController`" would either be a type-parameter-heavy generic that does neither well, or a thin base class that each app extends, which is the worst of both worlds. The pattern is shared; the code is not.

### `OnlyAudioRenderersFactory`

**What it is.** A custom `RenderersFactory` that strips video renderers from ExoPlayer. Reduces APK size and keeps the audio pipeline simpler. Both apps want it.

**Verdict: NO.** Fails (4). It's literally ~30 lines. Copy it.

### Album / cover art bitmap loader (`TonearmboyBitmapLoader` shape)

**What it is.** A Media3 `BitmapLoader` that knows how to extract embedded album art from media files (ID3v2 / FLAC pictures / MP4 covr atom) or fall back to a content URI. Both apps need this for notification + lock-screen + Auto.

**Verdict: NO** (currently). Fails (2): tonearmboy pulls covers from MediaStore content URIs; whisperboy pulls them from SAF `DocumentFile` URIs and from `cover.jpg` / `folder.jpg` next to the audio file. Same intent, different inputs.

**Could become a yes** after both apps ship if the `extractEmbedded(InputStream): Bitmap?` core is factored out cleanly. Even then it's ~200 LOC; barely clears (4). File this under "post-1.0 maybe."

### Material Palette → theming module

**What it is.** Take the dominant color from a piece of cover art and tint the now-playing screen background. Tonearmboy has this in Phase D.8b; whisperboy wants it in Phase F.6.

**Verdict: NO.** Fails (4). It's ~50 LOC of `Palette.from(bitmap).generate()` + ColorScheme building. Copy. (Also fails (3) — it isn't really atomic; it's intertwined with each app's specific Material 3 ColorScheme.)

### SAF wrapper / `CachedDocumentFile`

**What it is.** Performance wrapper around `DocumentFile` (caches `name` / `length` / `isDirectory` / `listFiles` to avoid the SAF binder round-trip).

**Verdict: NO.** Fails (1) — *only whisperboy needs SAF*. Tonearmboy uses MediaStore. There is no second consumer.

### M4B chapter parser (MP4 box walker + Apple chap-track + Nero chpl)

**What it is.** Voice's flagship clever bit. Whisperboy's Phase I.

**Verdict: NO.** Fails (1) — *only whisperboy parses chapters*. Tonearmboy has no chapter concept; tracks are chapters. Tonearmboy doesn't need it now and doesn't need it in any plausible roadmap.

### Sleep timer

**What it is.** Voice's exemplary sleep-timer implementation (timed + end-of-chapter + fade-out + shake-to-resume + auto-bookmark). Whisperboy's Phase G.

**Verdict: NO.** Fails (1) — tonearmboy doesn't ship a sleep timer and isn't planned to. (A "stop after current track" feature for tonearmboy would be a different shape — playlist-based, not duration-based.)

### Queue persistence (DataStore-backed `MediaItem` queue + position snapshot)

**What it is.** Tonearmboy's `QueuePersistence` — restore queue on cold-start. Implemented in tonearmboy Phase E.5.

**Verdict: NO.** Fails (1). Whisperboy *does* persist position, but per-book on the `BookEntity` row, not as a queue snapshot. Different model.

### `MediaSessionService` / `MediaLibraryService` boilerplate

**What it is.** Both apps run a foreground service hosting Media3.

**Verdict: NO.** Fails (3). Tonearmboy uses `MediaSessionService` (no Auto). Whisperboy uses `MediaLibraryService` (Auto-required). They diverge at the entry point. The ~80 LOC of identical service-startup code that *would* fit a shared base is below threshold (4) and would couple two apps to one base class for ~2 days of dev savings.

### `AppGraph` composition root pattern

**What it is.** Hand-rolled DI — a single class that owns all long-lived singletons.

**Verdict: NO.** Fails (3). It's a *pattern*, not code. Each app has its own `AppGraph` listing its own concrete dependencies; there's nothing to share.

### Build pipeline (`scripts/build-release-apk.sh`, `.github/workflows/release.yml`)

**What it is.** Local-build-first, GitHub-Releases-as-CDN, Obtainium-friendly release pipeline.

**Verdict: NO** (as a code library). Fails (3) — it's bash scripts and a YAML workflow, not library code. **Copy these as templates** when scaffolding whisperboy (Phase O.1 / O.2). Diverge if the apps' release needs ever differ; they probably won't.

### Robolectric test conventions, KSP setup, `libs.versions.toml` shape

**What it is.** Project-wide build conventions.

**Verdict: NO.** Fails (3) and (4). Copy `libs.versions.toml` as a starting point; let it diverge naturally.

## Summary table

| Candidate                                  | Both need? | Same shape? | Atomic? | Substantial? | Cost > dup? | Decision |
| ------------------------------------------ | :--------: | :---------: | :-----: | :----------: | :---------: | :------: |
| Media3 controller → StateFlow              | ✓          | ✗           | ✓       | ✓            | ✓           | NO       |
| `OnlyAudioRenderersFactory`                | ✓          | ✓           | ✓       | ✗            | ✗           | NO       |
| Album-art bitmap loader                    | ✓          | ✗           | ✓       | ~            | ~           | NO       |
| Palette → ColorScheme                      | ✓          | ✓           | ✗       | ✗            | ✗           | NO       |
| `CachedDocumentFile` (SAF wrapper)         | ✗          | —           | —       | —            | —           | NO       |
| M4B chapter parser                         | ✗          | —           | —       | —            | —           | NO       |
| Sleep timer                                | ✗          | —           | —       | —            | —           | NO       |
| Queue persistence                          | ✗          | —           | —       | —            | —           | NO       |
| MediaService base class                    | ✓          | ✗           | ✓       | ✗            | ✗           | NO       |
| `AppGraph` pattern                         | ✓          | ✓           | ✗       | ✗            | —           | NO       |
| Release pipeline scripts                   | ✓          | ✓           | ✗       | ✗            | —           | COPY     |
| `libs.versions.toml` / build conventions   | ✓          | ✓           | ✗       | ✗            | —           | COPY     |

(✗ = fails the criterion; ~ = marginal; — = irrelevant once an earlier criterion fails.)

Across the entire surface, **zero candidates pass all five criteria.**

## What "copy" means

Where the table says COPY, that means: when scaffolding whisperboy, literally copy the file from tonearmboy, then mutate names/paths/IDs (`tonearmboy` → `whisperboy`, `com.eight87.tonearmboy` → `com.eight87.whisperboy`). The two copies are then independent. If one app's release pipeline grows a feature the other doesn't want, the copies diverge. **Diverging is fine** — that's the cost of avoiding the subrepo overhead and we're paying it deliberately.

If a piece of copied code starts evolving in lockstep — same change lands in both, repeatedly — *that's the signal* to revisit promoting it to a shared subrepo. Not before.

## When to revisit

Re-open this question when **any** of these become true:

- A bug fix lands in tonearmboy and someone says "we'd want that in whisperboy too" — and the change is non-trivial. Two such instances inside six months = signal.
- A third app enters the picture (audiobook+podcast hybrid, web client, etc.). Three consumers usually clears the (5) bar that two consumers don't.
- Tonearmboy's Phase R refactor produces a clean `PlaybackController` interface that genuinely matches whisperboy's needs (currently Phase R.C plans to split it; that work might converge the contract).
- A piece of the M4B chapter parser turns out to be useful for tonearmboy's CUE sheet / split-FLAC support (not currently planned, but a plausible future).

If/when this gets revisited, the format should be: open this file, change "Don't share anything yet" to a different recommendation, write the rationale below this section, and *don't delete the original analysis* — keep it as the prior decision. Future-self will want to see what the previous "no" was based on.

## Footnote: why the bar is high

Subrepos are deceptively expensive:

- **Versioning friction.** Every change requires bumping a version in the subrepo, then bumping the dependency in each consumer.
- **Change-coordination friction.** A breaking change blocks until both consumers can update. (Two consumers is enough to feel this.)
- **Build-graph friction.** Both consumers' builds slow by the cost of pulling and resolving the subrepo.
- **Reviewer-context friction.** Reviewing a PR means knowing which version of the subrepo the consumer is on, and whether the change is backwards-compatible.
- **Agent-context friction.** Subagents working on a phase have to load the subrepo's CLAUDE.md or equivalent, which is context they don't get for free.

A subrepo earns those costs back when the shared code is genuinely substantial *and* genuinely under both apps' weight. That's the bar. We're not at it.
