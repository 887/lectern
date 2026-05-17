package com.eight87.whisperboy.ui.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * R.E.3 — destination contract.
 *
 * Every `@Serializable data object|class XxxRoute : NavKey` in [com.eight87.whisperboy.NavigationKeys]
 * is the **persisted key** for one nav-stack destination. The rendering for each
 * key lives next to the key's logical grouping in this package:
 *
 * - [OnboardingDestinations] — Welcome / Permissions / Folder picker / First scan
 * - [HomeDestination] — library cover grid
 * - [LibraryDestinations] — Library settings hub + Sort / Grid / Scan-filter sub-pages
 * - [PlaybackDestinations] — Bookmark list + Cover-search (the player itself is an
 *   overlay sheet in `WhisperboyApp`, NOT a route)
 * - [SettingsDestinations] — Settings hub + Playback / Sleep / Theme / About / Licenses
 *
 * Each grouping file exposes an `EntryProviderScope.registerXxxEntries(scope: RouteScope)`
 * extension; `WhisperboyApp.kt`'s lone `entryProvider { … }` block calls them all,
 * keeping the composition root under the 150-LOC budget (R.E.4).
 *
 * The sealed type itself is a marker — the entry-provider block dispatches on the
 * `NavKey` subtypes (each already `@Serializable` for back-stack persistence) rather
 * than on `Destination`, so we keep `NavKey` as the source of truth.
 */
internal sealed interface Destination

/**
 * Registers every destination in the app against the [EntryProviderScope]. Called
 * from `WhisperboyApp.kt`'s `entryProvider { … }` block.
 */
@Suppress("unused", "NOTHING_TO_INLINE")
internal inline fun EntryProviderScope<NavKey>.registerAllDestinations(scope: RouteScope) {
    registerOnboardingEntries(scope)
    registerHomeEntries(scope)
    registerSettingsEntries(scope)
    registerLibraryEntries(scope)
    registerPlaybackEntries(scope)
}
