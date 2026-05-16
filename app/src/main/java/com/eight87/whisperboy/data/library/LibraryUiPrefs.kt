package com.eight87.whisperboy.data.library

/**
 * Library-screen UI preferences — render mode, sort key, filter chip.
 *
 * These three enums live in `data/library/` (not `ui/library/`) so the [LibraryUiSettings]
 * facet in this package can reference them without inverting the dependency direction
 * (data must not depend on UI). The pure-Kotlin sort/filter functions that consume them
 * still live alongside the composables in `ui/library/LibrarySorting.kt` — they're
 * pure-Kotlin and either layer works, but co-locating them with the screen keeps the
 * UI code path readable.
 */

/** Grid vs list rendering for the library screen. */
enum class GridMode { Grid, List }

/**
 * Sort keys for the library grid / list.
 *
 * - [Recent] — `lastPlayedAt` descending, never-played books last.
 * - [Title] — collation-aware title ascending.
 * - [Author] — collation-aware author ascending, unknown-author bucket last.
 */
enum class BookSortKey { Recent, Title, Author }

/**
 * Filter chips above the library grid.
 *
 * `Completed` is deliberately absent until E.5's "Mark completed" action lands — without
 * an explicit user-set completion flag, "completed" can only be inferred from position
 * math which is unreliable before the user has actually played to the end.
 */
enum class BookFilter { All, Current, NotStarted }
