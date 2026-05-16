package com.eight87.whisperboy.ui.library

import com.eight87.whisperboy.data.library.BookEntity
import com.eight87.whisperboy.data.library.BookFilter
import com.eight87.whisperboy.data.library.BookSortKey
import java.text.Collator
import java.util.Locale

/**
 * Phase E.2 / E.3 — pure-function predicates and comparators for the library grid / list.
 *
 * The enums ([BookSortKey], [BookFilter], [com.eight87.whisperboy.data.library.GridMode])
 * live in `data/library/` so the persisted [com.eight87.whisperboy.data.library.LibraryUiSettings]
 * facet can reference them without inverting the dependency direction. These functions stay
 * here — they're consumed by the composable and have no business in the data layer.
 *
 * No Compose imports, so unit-testable without `createComposeRule()` (R.D.3 discipline).
 */

fun filterBooks(books: List<BookEntity>, filter: BookFilter): List<BookEntity> = when (filter) {
    BookFilter.All -> books
    BookFilter.Current -> books.filter { it.lastPlayedAt != null }
    BookFilter.NotStarted -> books.filter { it.lastPlayedAt == null }
}

/**
 * Pure-function sort. The comparator instances are constructed per-call so they pick up
 * the current default locale (R.A.Q for collation across languages — Voice does the same).
 */
fun sortedBooks(books: List<BookEntity>, key: BookSortKey): List<BookEntity> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    return when (key) {
        // Recent: `lastPlayedAt` desc, nulls last; ties break on title for stable order.
        BookSortKey.Recent -> books.sortedWith(
            compareByDescending<BookEntity> { it.lastPlayedAt != null }
                .thenByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
                .thenBy(collator) { it.title }
        )
        BookSortKey.Title -> books.sortedWith(compareBy(collator) { it.title })
        // Author: nulls last (unknown-author group sorts to the end), then title within author.
        BookSortKey.Author -> books.sortedWith(
            compareBy<BookEntity> { it.author.isNullOrBlank() }
                .thenBy(collator) { it.author.orEmpty() }
                .thenBy(collator) { it.title }
        )
    }
}

/**
 * Phase E.3 — `(itemIndex, label)` pairs for the [com.eight87.whisperboy.ui.common.FastScrollbar]
 * section-letter chips, sort-aware.
 *
 * - [BookSortKey.Title] → first letter of the title (uppercase; non-letters bucket into "#")
 * - [BookSortKey.Author] → first letter of the author (unknown-author bucket is "?")
 * - [BookSortKey.Recent] → coarse-grained recency bucket label ("Today" / "Week" / "Month" /
 *   "Older" / "Never"). The chip labels are short so they fit the track; the bucket math
 *   leans on `nowMs` so it's deterministic per call.
 *
 * Duplicate-key crash gotcha (tonearmboy `d75b542`): these labels never become item keys in
 * a LazyGrid — they live only on the FastScrollbar track. If a future header-item type lands
 * in the same grid, header keys must be `"section:$label"` not just `label` to avoid colliding
 * with book ids.
 */
fun sectionStartsFor(
    books: List<BookEntity>,
    key: BookSortKey,
    nowMs: Long = System.currentTimeMillis(),
): List<Pair<Int, String>> {
    if (books.isEmpty()) return emptyList()
    val labelOf: (BookEntity) -> String = when (key) {
        BookSortKey.Title -> { book -> firstLetterLabel(book.title) }
        BookSortKey.Author -> { book ->
            if (book.author.isNullOrBlank()) "?" else firstLetterLabel(book.author)
        }
        BookSortKey.Recent -> { book -> recencyBucketLabel(book.lastPlayedAt, nowMs) }
    }
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Pair<Int, String>>()
    books.forEachIndexed { index, book ->
        val label = labelOf(book)
        if (seen.add(label)) out += index to label
    }
    return out
}

private fun firstLetterLabel(text: String): String {
    val first = text.trimStart().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

private fun recencyBucketLabel(lastPlayedAt: Long?, nowMs: Long): String {
    if (lastPlayedAt == null) return "Never"
    val ageMs = nowMs - lastPlayedAt
    val oneDay = 24L * 60 * 60 * 1000
    return when {
        ageMs < oneDay -> "Today"
        ageMs < 7 * oneDay -> "Week"
        ageMs < 30 * oneDay -> "Month"
        else -> "Older"
    }
}
