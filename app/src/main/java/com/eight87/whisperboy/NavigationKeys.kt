package com.eight87.whisperboy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey

/**
 * Full-screen player surface (Phase F.1) for a specific book.
 *
 * Retired from the user-driven nav path: the book-tap in [com.eight87.whisperboy.ui.library.LibraryScreen]
 * now fires `bookCommands.playBook(bookId)` and animates the in-place
 * [com.eight87.whisperboy.ui.playback.NowPlayingSheet] from peek (mini-player) to fully
 * expanded, NOT a back-stack push. Retained for future deeplink entry — e.g.
 * notification tap, Android Auto launch — which will hand off to the sheet's
 * `animateTo(1f)` rather than register a nav entry. See tonearmboy's
 * `pendingDeeplink` pattern in `TonearmboyApp.kt` for the shape that lands when
 * deeplinks ship.
 */
@Serializable data class PlaybackRoute(val bookId: String) : NavKey

/**
 * Per-book DuckDuckGo image-search surface for cover art (cover-art.md Phase B). Pushed
 * onto the back stack by the library long-press action sheet's "Search online" item; the
 * library-side integration ships in a coexisting inch.
 */
@Serializable data class CoverSearchRoute(val bookId: String) : NavKey

/**
 * Phase K.1 — settings root surface. Pushed from the library top-app-bar
 * overflow menu's "Settings" item. Hosts the category list that branches
 * into Playback (K.2) / Sleep timer (K.3) / Library (K.4) / Theme (K.5) /
 * About (K.6) sub-pages.
 */
@Serializable data object SettingsRoute : NavKey

/**
 * Phase K.6 — About sub-page. Pushed from `SettingsRoute`. Shows app
 * icon, version, license, clean-room "spiritual sibling" credit for
 * Voice, GitHub link, and (eventually) the open-source-licenses
 * sub-page tracked in `docs/plans/oss-licenses.md`.
 */
@Serializable data object AboutRoute : NavKey

/**
 * Phase K.4 (partial) — Library folders sub-page. Pushed from Settings.
 * Lists configured roots with their `FolderType` + Remove action; a FAB
 * adds a new root via the SAF tree picker.
 */
@Serializable data object LibraryFoldersRoute : NavKey

/**
 * Phase K.5 — Theme sub-page. Pushed from Settings. Hosts the theme-mode
 * radio group (Light / Dark / Follow system) plus the dynamic-color
 * (Material You) toggle. Selection persists via [com.eight87.whisperboy.data.theme.ThemeSettings].
 */
@Serializable data object ThemeSettingsRoute : NavKey

/**
 * Phase K.2 — Playback sub-page. Pushed from Settings. Hosts the
 * [com.eight87.whisperboy.data.playback.PlaybackSettings] knobs: per-book
 * defaults (speed / skip silence / gain), seek seconds (rewind / forward /
 * auto-rewind radio groups), and a system equalizer launcher row that
 * fires `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`.
 */
@Serializable data object PlaybackSettingsRoute : NavKey

/**
 * Phase K.3 — Sleep timer sub-page. Pushed from Settings. Hosts the four
 * [com.eight87.whisperboy.data.playback.SleepTimerSettings] knobs: default
 * duration (5/10/15/30/45/60 min radio group), fade-out duration (slider),
 * shake-to-resume (toggle), and the auto-arm window (two `LocalTime`
 * chips + clear button).
 */
@Serializable data object SleepTimerSettingsRoute : NavKey

/**
 * Phase L — first-run onboarding flow. Four steps, in order:
 *
 * 1. [OnboardingWelcomeRoute] — one short sentence + Get-started CTA.
 * 2. [OnboardingPermissionsRoute] — `POST_NOTIFICATIONS` rationale (API 33+; auto-skip otherwise).
 * 3. [OnboardingFolderPickerRoute] — explains the four `FolderType` modes, launches
 *    the SAF `OPEN_DOCUMENT_TREE` picker, surfaces the FolderType bottom sheet.
 * 4. [OnboardingFirstScanRoute] — listens to `LibraryRescanCoordinator.state`, shows
 *    "Scanning your library…" and, once Idle, "Found N books, M chapters" + Continue.
 */
@Serializable data object OnboardingWelcomeRoute : NavKey

@Serializable data object OnboardingPermissionsRoute : NavKey

@Serializable data object OnboardingFolderPickerRoute : NavKey

@Serializable data object OnboardingFirstScanRoute : NavKey

/**
 * Phase H.1 — bookmark list for a single book. Pushed from the player's top-app-bar
 * "view bookmarks" icon. Tap on a row inside the screen seeks the active player to the
 * bookmark's `positionInBookMs`, auto-resumes playback, and pops the back stack back to the
 * player (the entry-block in `WhisperboyApp` owns the seek + play + pop wiring).
 *
 * Carries `bookId` so the screen can scope its `observeBookmarksForBook` flow without reaching
 * into the playback session — keeps the screen usable even when nothing is currently playing
 * (a future deeplink "bookmarks for book X" pattern, parallel to `PlaybackRoute`).
 */
@Serializable data class BookmarkRoute(val bookId: String) : NavKey
