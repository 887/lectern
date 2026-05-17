package com.eight87.whisperboy.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.RescanState
import com.eight87.whisperboy.data.library.ScanPhase

/**
 * Issue-3 — in-library scan progress banner. Renders at the top of [LibraryScreen]
 * beneath the TopAppBar while [RescanState.Running] is the active state.
 *
 * Bug 1 fix: the status string is now phase-aware. Discovering shows the running
 * books/chapters tally (ticks during the SAF tree walk); Analyzing shows
 * `analyzed / total chapters` (ticks during per-file metadata reads); Writing shows
 * a brief "Updating library…" string while the Room transaction commits.
 *
 * Bug 2 fix: the indeterminate progress indicator was visually frozen because the
 * default M3 colors on a `surfaceContainerHigh` card made the moving segment blend
 * into the track. We explicitly set `color = primary` / `trackColor = surfaceVariant`
 * and bump the height to 6dp so the wave is unmistakably alive. When totals are known
 * during the Analyzing phase the indicator switches to determinate progress, which is
 * the most informative signal during the slow per-file metadata read.
 *
 * Visibility is decided by the caller — the composable itself renders unconditionally
 * for the running state passed in. Caller hides it on [RescanState.Idle] /
 * [RescanState.Failed].
 *
 * Visual shape ported from tonearmboy's `ScanProgressBar` — a **flat full-width
 * `Surface` flush with the bottom of the TopAppBar**, no rounded card, no horizontal
 * margin, tight 8dp vertical padding, `labelMedium` typography, optional percent on
 * the right. Reads as an extension of the toolbar chrome, not a content card.
 */
@Composable
fun LibraryScanProgressBanner(
    running: RescanState.Running,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.library_scan_progress_cd)
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cd },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val text = when (running.phase) {
                ScanPhase.Discovering -> stringResource(
                    R.string.library_scan_progress_discovering,
                    running.booksFound,
                    running.chaptersFound,
                )
                ScanPhase.Analyzing -> stringResource(
                    R.string.library_scan_progress_analyzing,
                    running.analyzedChapters,
                    running.totalChapters,
                )
                ScanPhase.Writing -> stringResource(R.string.library_scan_progress_writing)
            }
            val determinate = running.phase == ScanPhase.Analyzing && running.totalChapters > 0
            val ratio = if (determinate) {
                (running.analyzedChapters.toFloat() / running.totalChapters.toFloat())
                    .coerceIn(0f, 1f)
            } else 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (determinate) {
                    Text(
                        text = stringResource(
                            R.string.library_scan_progress_percent,
                            (ratio * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            val color = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val barModifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
            if (determinate) {
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = barModifier,
                    color = color,
                    trackColor = trackColor,
                )
            } else {
                LinearProgressIndicator(
                    modifier = barModifier,
                    color = color,
                    trackColor = trackColor,
                )
            }
        }
    }
}
