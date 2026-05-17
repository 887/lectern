package com.eight87.whisperboy.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.library.BookSource
import com.eight87.whisperboy.data.library.ChapterSource
import com.eight87.whisperboy.data.library.LibraryRescanCoordinator
import com.eight87.whisperboy.data.library.RescanState
import com.eight87.whisperboy.data.onboarding.OnboardingSettings
import kotlinx.coroutines.launch

/**
 * Phase L.4 — first-scan loading + completion screen.
 *
 * Observes [LibraryRescanCoordinator.state]. While `Running`, shows a centered
 * `CircularProgressIndicator` + a "Scanning your library…" label. Once `Idle`
 * (post-scan), counts the books and their chapters and shows "N books,
 * M chapters" + a Continue button. Continue flips
 * [OnboardingSettings.setCompleted] to `true` and calls [onFinish], which the
 * caller wires to a back-stack reset down to `HomeRoute`.
 *
 * Chapter count is derived by fanning out [ChapterSource.chaptersFor] over the
 * book list once the scan completes. A separate `BookSource.totalChapterCount()`
 * facet would be cleaner if anything else needed it, but no other surface does
 * — first-scan completion is the only consumer.
 *
 * If the rescan never moved past `Idle` (no roots / no audio files), the
 * count line renders "0 books, 0 chapters" and Continue still works. The
 * `Failed` state currently renders the same Continue affordance — letting
 * the user proceed to the (empty) library beats blocking them in onboarding.
 */
@Composable
fun OnboardingFirstScanScreen(
    libraryRescanCoordinator: LibraryRescanCoordinator,
    bookSource: BookSource,
    chapterSource: ChapterSource,
    onboardingSettings: OnboardingSettings,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val rescanState by libraryRescanCoordinator.state.collectAsStateWithLifecycle()
    val books by bookSource.observeBooks().collectAsStateWithLifecycle(initialValue = emptyList())

    var chapterCount by remember { mutableIntStateOf(0) }
    val bookCount = books.size
    val isRunning = rescanState is RescanState.Running

    LaunchedEffect(books, isRunning) {
        if (!isRunning) {
            chapterCount = books.sumOf { chapterSource.chaptersFor(it.bookId).size }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_first_scan_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (isRunning) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_first_scan_running),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.onboarding_first_scan_done,
                        bookCount,
                        chapterCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = {
                scope.launch {
                    onboardingSettings.setCompleted(true)
                    onFinish()
                }
            },
            enabled = !isRunning,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_first_scan_continue))
        }
    }
}
