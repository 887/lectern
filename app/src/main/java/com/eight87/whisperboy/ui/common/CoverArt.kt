package com.eight87.whisperboy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.whisperboy.R

/**
 * Placeholder cover surface. Cover-art Phase A (CoverScanner) will replace the inner content
 * with a Coil-loaded `AsyncImage` of `coverPath` when present; for now every tile shows the
 * M3E book glyph in a `surfaceContainer` square.
 *
 * Tile decode discipline (tonearmboy `8d8c1a4`): once Coil lands, the model size MUST be
 * `Size(coverSizePx, coverSizePx)` for grid tiles — never `Size.ORIGINAL`. Defer that to
 * cover-art.md Phase A.5 where the Coil dep enters.
 */
@Composable
fun CoverArt(
    coverPath: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        // coverPath is unused until Coil lands (cover-art Phase A); the parameter exists now so
        // call sites don't change shape when the AsyncImage swap happens.
        @Suppress("UNUSED_EXPRESSION") coverPath
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringResource(R.string.library_cover_placeholder_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.45f),
        )
    }
}
