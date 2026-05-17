package com.eight87.whisperboy.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.WhisperboyActivity
import com.eight87.whisperboy.WhisperboyApplication
import com.eight87.whisperboy.playback.PlaybackUiState
import java.io.File

/**
 * main.md Phase M — home-screen widget.
 *
 * Glance composes a RemoteViews tree at update time; we read the current
 * `NowPlayingState` from the [com.eight87.whisperboy.AppGraph] once per
 * `provideContent` call and re-render. Refreshes are driven from
 * [WidgetUpdater], which subscribes to the playback state flow and calls
 * `WhisperboyWidget().updateAll(context)` debounced via `sample(1000)` so we
 * don't churn from the 250 ms position ticker.
 *
 * Three adaptive size variants via [SizeMode.Responsive]:
 *
 *  - [SIZE_SMALL] (~1×1) — cover only, play/pause overlaid in the corner.
 *  - [SIZE_MEDIUM] (~2×2) — cover above title + transport row.
 *  - [SIZE_WIDE] (~4×1) — cover left, title + chapter + transport right.
 *
 * Tap on cover launches [WhisperboyActivity]; the now-playing sheet auto-opens
 * on resume whenever a `Loaded` state is present, so we don't need a deep-link
 * route here.
 */
class WhisperboyWidget : GlanceAppWidget() {

    // No per-widget persisted state — every render reads NowPlayingState
    // directly. Disabling the default `PreferencesGlanceStateDefinition` skips
    // a per-widget Preferences read on each `provideContent`.
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_WIDE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as WhisperboyApplication).graph
        val snapshot = graph.nowPlayingState.state.value
        val loaded = snapshot as? PlaybackUiState.Loaded
        val coverBitmap = loaded?.book?.coverPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        }

        provideContent {
            GlanceTheme {
                WidgetBody(loaded = loaded, coverBitmap = coverBitmap)
            }
        }
    }
}

@Composable
private fun WidgetBody(
    loaded: PlaybackUiState.Loaded?,
    coverBitmap: android.graphics.Bitmap?,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val title = loaded?.book?.title ?: context.getString(R.string.widget_empty_title)
    val chapter = loaded?.currentChapter?.title
    val isPlaying = loaded?.isPlaying == true
    val hasBook = loaded != null

    val openAppAction = actionStartActivity(
        Intent(context.applicationContext, WhisperboyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

    val widthDp = size.width.value
    val heightDp = size.height.value
    val isWide = widthDp >= 260f && widthDp > heightDp * 1.3f
    val isSmall = widthDp <= 140f && heightDp <= 140f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(8.dp),
    ) {
        when {
            isSmall -> SmallLayout(coverBitmap, isPlaying, openAppAction, hasBook)
            isWide -> WideLayout(coverBitmap, title, chapter, isPlaying, openAppAction, hasBook)
            else -> MediumLayout(coverBitmap, title, chapter, isPlaying, openAppAction, hasBook)
        }
    }
}

@Composable
private fun SmallLayout(
    cover: android.graphics.Bitmap?,
    isPlaying: Boolean,
    openAppAction: Action,
    hasBook: Boolean,
) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Cover(cover, modifier = GlanceModifier.fillMaxSize().clickable(openAppAction))
        if (hasBook) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                PlayPauseButton(isPlaying)
            }
        }
    }
}

@Composable
private fun MediumLayout(
    cover: android.graphics.Bitmap?,
    title: String,
    chapter: String?,
    isPlaying: Boolean,
    openAppAction: Action,
    hasBook: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(
            cover,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
                .clickable(openAppAction),
        )
        TitleAndChapter(title, chapter)
        if (hasBook) {
            TransportRow(isPlaying)
        }
    }
}

@Composable
private fun WideLayout(
    cover: android.graphics.Bitmap?,
    title: String,
    chapter: String?,
    isPlaying: Boolean,
    openAppAction: Action,
    hasBook: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Cover(
            cover,
            modifier = GlanceModifier
                .size(72.dp)
                .clickable(openAppAction),
        )
        Column(modifier = GlanceModifier.defaultWeight().padding(start = 10.dp)) {
            TitleAndChapter(title, chapter)
            if (hasBook) {
                TransportRow(isPlaying)
            }
        }
    }
}

@Composable
private fun Cover(
    bitmap: android.graphics.Bitmap?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val provider = bitmap?.let { ImageProvider(it) }
        ?: ImageProvider(R.drawable.widget_book_glyph)
    Image(
        provider = provider,
        contentDescription = context.getString(R.string.widget_cover_cd),
        contentScale = if (bitmap != null) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier.cornerRadius(8.dp),
    )
}

@Composable
private fun TitleAndChapter(title: String, chapter: String?) {
    Column(modifier = GlanceModifier.padding(top = 4.dp)) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = sp(13),
                fontWeight = FontWeight.Medium,
            ),
        )
        if (!chapter.isNullOrBlank()) {
            Text(
                text = chapter,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = sp(11),
                ),
            )
        }
    }
}

@Composable
private fun TransportRow(isPlaying: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            glyph = "‹‹",
            cd = context.getString(R.string.widget_prev_cd),
            onClick = actionRunCallback<PrevChapterAction>(),
        )
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
            PlayPauseButton(isPlaying)
        }
        TransportButton(
            glyph = "››",
            cd = context.getString(R.string.widget_next_cd),
            onClick = actionRunCallback<NextChapterAction>(),
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean) {
    val glyph = if (isPlaying) "❚❚" else "▶"
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .background(GlanceTheme.colors.primary)
            .cornerRadius(20.dp)
            .clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = sp(14),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun TransportButton(glyph: String, cd: String, onClick: Action) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = sp(16),
                fontWeight = FontWeight.Bold,
            ),
        )
        @Suppress("UNUSED_VARIABLE") val unusedCd = cd
    }
}

@Suppress("UNUSED_PARAMETER")
private fun stringRef(@StringRes id: Int): Int = id

private fun sp(value: Int): TextUnit = TextUnit(value.toFloat(), TextUnitType.Sp)

// Three responsive breakpoints; Glance picks the closest match per host cell.
private val SIZE_SMALL = DpSize(120.dp, 120.dp)
private val SIZE_MEDIUM = DpSize(180.dp, 180.dp)
private val SIZE_WIDE = DpSize(300.dp, 110.dp)
