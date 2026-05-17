package com.eight87.whisperboy.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.playback.PlaybackSettings
import kotlinx.coroutines.launch

/**
 * Phase K.2 — playback settings sub-page.
 *
 * Three cards mirror the [PlaybackSettings] facet plus the system equalizer launcher:
 *
 *   1. **Defaults** — global per-book seeds copied to [BookEntity] on a book's FIRST scan:
 *      - Default speed: Slider over 0.5..3.5 in 0.05 steps + numeric readout + Reset to 1.00x.
 *      - Default skip silence: Switch.
 *      - Default volume gain: Slider over -3..+12 dB in 0.5-dB steps + numeric readout.
 *      Edits do NOT retro-write existing books — these are first-scan seeds; per-book overrides
 *      live on `BookEntity`.
 *
 *   2. **Seek controls** — transport-button defaults:
 *      - Rewind seconds: radio group 5 / 10 / 30 / 60.
 *      - Forward seconds: radio group 5 / 10 / 30 / 60.
 *      - Auto-rewind after pause: radio group 0 (off) / 3 / 5 / 10.
 *
 *   3. **System** — equalizer launcher:
 *      - `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` system intent. Wraps in a
 *        try/catch on [ActivityNotFoundException] because not all OEMs ship an equalizer;
 *        falls back to a snackbar. **Session ID = 0 (system-wide effects)**: whisperboy
 *        currently has no plumbing to surface the running ExoPlayer's `audioSessionId` up to
 *        this screen — wiring it through would mean a new optional callback on this composable
 *        + a service-bound query path. Passing 0 asks the system equalizer to apply effects
 *        at the global mix level instead of per-stream, which is what the user actually wants
 *        for an audiobook player (the effect persists across playback restarts). Voice does
 *        the same trick.
 *
 * Surface tier ladder matches [SleepTimerSettingsScreen]: page bg = `surface`, cards =
 * `surfaceContainerHigh`, `defaultElevation = 0.dp`. Writes go through
 * [rememberCoroutineScope] — facet setters are `suspend` but cheap single-key DataStore edits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    playbackSettings: PlaybackSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val equalizerUnavailable = stringResource(R.string.settings_playback_equalizer_unavailable)

    val defaultSpeed by playbackSettings.defaultSpeed
        .collectAsStateWithLifecycle(initialValue = 1.0f)
    val defaultSkipSilence by playbackSettings.defaultSkipSilence
        .collectAsStateWithLifecycle(initialValue = false)
    val defaultGainDb by playbackSettings.defaultGainDb
        .collectAsStateWithLifecycle(initialValue = 0.0f)
    val rewindSeconds by playbackSettings.rewindSeconds
        .collectAsStateWithLifecycle(initialValue = 30)
    val forwardSeconds by playbackSettings.forwardSeconds
        .collectAsStateWithLifecycle(initialValue = 30)
    val autoRewindSeconds by playbackSettings.autoRewindSeconds
        .collectAsStateWithLifecycle(initialValue = 5)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_playback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_playback_back_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlaybackCard {
                SectionLabel(stringResource(R.string.settings_playback_defaults_section))
                DefaultSpeedRow(
                    speed = defaultSpeed,
                    onChange = { value ->
                        scope.launch { playbackSettings.setDefaultSpeed(snapSpeed(value)) }
                    },
                    onReset = { scope.launch { playbackSettings.setDefaultSpeed(1.0f) } },
                )
                DefaultSkipSilenceRow(
                    checked = defaultSkipSilence,
                    onCheckedChange = { value ->
                        scope.launch { playbackSettings.setDefaultSkipSilence(value) }
                    },
                )
                DefaultGainRow(
                    gainDb = defaultGainDb,
                    onChange = { value ->
                        scope.launch { playbackSettings.setDefaultGainDb(snapGain(value)) }
                    },
                )
            }

            PlaybackCard {
                SectionLabel(stringResource(R.string.settings_playback_seek_section))
                SecondsSection(
                    title = stringResource(R.string.settings_playback_rewind_title),
                    selected = rewindSeconds,
                    choices = SEEK_CHOICES,
                    onSelect = { value ->
                        scope.launch { playbackSettings.setRewindSeconds(value) }
                    },
                )
                SecondsSection(
                    title = stringResource(R.string.settings_playback_forward_title),
                    selected = forwardSeconds,
                    choices = SEEK_CHOICES,
                    onSelect = { value ->
                        scope.launch { playbackSettings.setForwardSeconds(value) }
                    },
                )
                SecondsSection(
                    title = stringResource(R.string.settings_playback_auto_rewind_title),
                    subtitle = stringResource(R.string.settings_playback_auto_rewind_subtitle),
                    selected = autoRewindSeconds,
                    choices = AUTO_REWIND_CHOICES,
                    onSelect = { value ->
                        scope.launch { playbackSettings.setAutoRewindSeconds(value) }
                    },
                )
            }

            PlaybackCard {
                EqualizerRow(
                    onClick = {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            // Session ID 0 → system-wide effects; see kdoc above.
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbarHostState.showSnackbar(equalizerUnavailable) }
                        }
                    },
                )
            }
        }
    }
}

private val SEEK_CHOICES: List<Int> = listOf(5, 10, 30, 60)
private val AUTO_REWIND_CHOICES: List<Int> = listOf(0, 3, 5, 10)

/** Snap to nearest 0.05 — the Slider's `steps = 59` already discretises but converting through
 * Float drift can leave 1.4999999 — round for clean readouts. */
private fun snapSpeed(raw: Float): Float = (Math.round(raw / 0.05f) * 0.05f)

/** Snap gain to nearest 0.5 dB. */
private fun snapGain(raw: Float): Float = (Math.round(raw / 0.5f) * 0.5f)

@Composable
private fun PlaybackCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = { content() },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DefaultSpeedRow(
    speed: Float,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_playback_default_speed_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_playback_default_speed_format, speed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = speed,
            onValueChange = onChange,
            valueRange = 0.5f..3.5f,
            // (3.5 - 0.5) / 0.05 = 60 distinct stops; Slider's `steps` is stops BETWEEN
            // endpoints, so 59.
            steps = 59,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.settings_playback_default_speed_reset))
        }
    }
}

@Composable
private fun DefaultSkipSilenceRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_playback_default_silence_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_playback_default_silence_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DefaultGainRow(
    gainDb: Float,
    onChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_playback_default_gain_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_playback_default_gain_format, gainDb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = gainDb,
            onValueChange = onChange,
            valueRange = -3f..12f,
            // (12 - -3) / 0.5 = 30 distinct stops; 29 between.
            steps = 29,
        )
    }
}

@Composable
private fun SecondsSection(
    title: String,
    subtitle: String? = null,
    selected: Int,
    choices: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        choices.forEach { value ->
            SecondsRadioRow(
                label = stringResource(R.string.settings_playback_seconds_format, value),
                selected = selected == value,
                onSelect = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun SecondsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EqualizerRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_playback_equalizer_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_playback_equalizer_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
