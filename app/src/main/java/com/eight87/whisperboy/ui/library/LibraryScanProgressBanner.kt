package com.eight87.whisperboy.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.RescanState

/**
 * Issue-3 — in-library scan progress banner. Renders at the top of [LibraryScreen]
 * beneath the TopAppBar while [RescanState.Running] is the active state. Two rows:
 *
 * - Status: "Scanning library — N books, M chapters" (locale-aware).
 * - Indeterminate `LinearProgressIndicator` because the scanner doesn't know totals
 *   up front (the structural pass settles before per-file enrichment starts ticking
 *   chapter counts).
 *
 * Visibility is decided by the caller — the composable itself renders unconditionally
 * for the running state passed in. Caller hides it on [RescanState.Idle] /
 * [RescanState.Failed].
 *
 * M3E surface ladder — `surfaceContainerHigh`, the same tier the empty-state card
 * and list rows use. No elevation (M3E flat surfaces).
 */
@Composable
fun LibraryScanProgressBanner(
    running: RescanState.Running,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.library_scan_progress_cd)
    Card(
        modifier = modifier.semantics { contentDescription = cd },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.library_scan_progress_running,
                    running.booksFound,
                    running.chaptersFound,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
