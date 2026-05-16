package com.eight87.whisperboy.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.BookFilter

/**
 * Phase E.2 upgrade — vertical tab rail used as the library's filter chrome.
 * Replaces the horizontal `FilterChip` row. Mirrors tonearmboy's hand-rolled
 * rail shape: a fixed 52 dp column on the left edge with rotated text labels,
 * an active highlight (bold + 2 dp accent stripe on the right edge), and a
 * vertical scroll if labels overflow.
 *
 * Tonearmboy's rail carries a bottom settings gear; whisperboy already has
 * folder management via the top-app-bar overflow menu, so the gear is omitted.
 */
@Composable
fun LibraryRail(
    filter: BookFilter,
    onFilterChange: (BookFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth = 52.dp
    val railCd = stringResource(R.string.library_rail_cd)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .requiredWidth(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                testTag = "library_rail"
                contentDescription = railCd
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookFilter.entries.forEach { option ->
                RailTabItem(
                    label = stringResource(filterLabel(option)),
                    name = option.name.lowercase(),
                    selected = option == filter,
                    onClick = { onFilterChange(option) },
                )
            }
        }
    }
}

@Composable
private fun RailTabItem(
    label: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor =
        if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 108.dp)
            .clickable(onClick = onClick)
            .semantics { testTag = "rail_filter_$name" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .rotate(-90f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(accent)
                    .semantics { testTag = "rail_accent" },
            )
        }
    }
}

internal fun filterLabel(filter: BookFilter): Int = when (filter) {
    BookFilter.All -> R.string.library_filter_all
    BookFilter.Current -> R.string.library_filter_current
    BookFilter.NotStarted -> R.string.library_filter_not_started
    BookFilter.Completed -> R.string.library_filter_completed
}
