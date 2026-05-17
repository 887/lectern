package com.eight87.whisperboy.ui.settings

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.whisperboy.R
import com.eight87.whisperboy.data.playback.SleepTimerSettings
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

/**
 * Phase K.3 — sleep-timer settings sub-page.
 *
 * Four cards mirror the [SleepTimerSettings] facet's four knobs:
 *
 *   1. **Default duration** — radio group of 5/10/15/30/45/60 minutes. Anchors
 *      the "default" timer button in the sleep-timer bottom sheet (G.2).
 *   2. **Fade-out duration** — Slider 0..60 seconds. Volume ramps down to silent
 *      over this period before the fire fires (G.3).
 *   3. **Shake-to-resume** — Switch. When on, post-fire shake detector picks up
 *      the phone-pickup gesture to resume playback (G.4).
 *   4. **Auto-arm window** — start + end OutlinedButton chips that open a
 *      [TimePicker] dialog; "Disable window" button clears both. Both-null in
 *      the facet means disabled (G.6). Chose OutlinedButton chips (vs a single
 *      "Disabled/Custom..." flow) so the current window is visible at a glance
 *      and editable in one tap; clearing is the explicit third action.
 *
 * Surface tier ladder matches [ThemeSettingsScreen]: page bg = `surface`,
 * cards = `surfaceContainerHigh`, `defaultElevation = 0.dp`.
 *
 * Writes go through [rememberCoroutineScope] — the facet setters are `suspend`
 * but cheap (single-key DataStore edits); no need for a VM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSettingsScreen(
    sleepTimerSettings: SleepTimerSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val defaultDuration by sleepTimerSettings.defaultDuration
        .collectAsStateWithLifecycle(initialValue = 30.minutes)
    val fadeOutDuration by sleepTimerSettings.fadeOutDuration
        .collectAsStateWithLifecycle(initialValue = 30.seconds)
    val shakeToResume by sleepTimerSettings.shakeToResume
        .collectAsStateWithLifecycle(initialValue = true)
    val autoArmStart by sleepTimerSettings.autoArmWindowStart
        .collectAsStateWithLifecycle(initialValue = null)
    val autoArmEnd by sleepTimerSettings.autoArmWindowEnd
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_sleep_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_sleep_back_cd),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SleepCard {
                SectionLabel(stringResource(R.string.settings_sleep_default_duration_title))
                DEFAULT_DURATION_CHOICES.forEach { choice ->
                    DurationRadioRow(
                        label = stringResource(R.string.settings_sleep_duration_format, choice.inWholeMinutes.toInt()),
                        selected = defaultDuration == choice,
                        onSelect = { scope.launch { sleepTimerSettings.setDefaultDuration(choice) } },
                    )
                }
            }

            SleepCard {
                FadeOutRow(
                    seconds = fadeOutDuration.inWholeSeconds.toInt().coerceIn(0, 60),
                    onChange = { newSeconds ->
                        scope.launch { sleepTimerSettings.setFadeOutDuration(newSeconds.seconds) }
                    },
                )
            }

            SleepCard {
                ShakeRow(
                    checked = shakeToResume,
                    onCheckedChange = { value ->
                        scope.launch { sleepTimerSettings.setShakeToResume(value) }
                    },
                )
            }

            SleepCard {
                AutoArmWindowSection(
                    start = autoArmStart,
                    end = autoArmEnd,
                    onSetStart = { newStart ->
                        scope.launch {
                            sleepTimerSettings.setAutoArmWindow(
                                start = newStart,
                                end = autoArmEnd,
                            )
                        }
                    },
                    onSetEnd = { newEnd ->
                        scope.launch {
                            sleepTimerSettings.setAutoArmWindow(
                                start = autoArmStart,
                                end = newEnd,
                            )
                        }
                    },
                    onClear = {
                        scope.launch {
                            sleepTimerSettings.setAutoArmWindow(start = null, end = null)
                        }
                    },
                )
            }
        }
    }
}

private val DEFAULT_DURATION_CHOICES: List<Duration> = listOf(
    5.minutes,
    10.minutes,
    15.minutes,
    30.minutes,
    45.minutes,
    60.minutes,
)

@Composable
private fun SleepCard(content: @Composable () -> Unit) {
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
private fun DurationRadioRow(
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
private fun FadeOutRow(
    seconds: Int,
    onChange: (Int) -> Unit,
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
                text = stringResource(R.string.settings_sleep_fade_out_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_sleep_fade_out_format, seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_sleep_fade_out_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 60)) },
            valueRange = 0f..60f,
            // 60 - 0 = 60 distinct integer stops; Slider's `steps` is the count
            // of stops BETWEEN endpoints, so we want 59.
            steps = 59,
        )
    }
}

@Composable
private fun ShakeRow(
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
                text = stringResource(R.string.settings_sleep_shake_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_sleep_shake_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AutoArmWindowSection(
    start: LocalTime?,
    end: LocalTime?,
    onSetStart: (LocalTime) -> Unit,
    onSetEnd: (LocalTime) -> Unit,
    onClear: () -> Unit,
) {
    // Which edge ("start" or "end") is currently being edited; null = closed.
    var editing by remember { mutableStateOf<WindowEdge?>(null) }
    val windowActive = start != null || end != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_sleep_window_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.settings_sleep_window_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WindowChip(
                label = stringResource(R.string.settings_sleep_window_start_label),
                time = start,
                onClick = { editing = WindowEdge.Start },
                modifier = Modifier.weight(1f),
            )
            WindowChip(
                label = stringResource(R.string.settings_sleep_window_end_label),
                time = end,
                onClick = { editing = WindowEdge.End },
                modifier = Modifier.weight(1f),
            )
        }
        if (windowActive) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.settings_sleep_window_clear))
            }
        }
    }

    editing?.let { edge ->
        val seed = when (edge) {
            WindowEdge.Start -> start ?: LocalTime.of(22, 0)
            WindowEdge.End -> end ?: LocalTime.of(2, 0)
        }
        TimePickerDialog(
            initial = seed,
            onDismiss = { editing = null },
            onConfirm = { picked ->
                when (edge) {
                    WindowEdge.Start -> onSetStart(picked)
                    WindowEdge.End -> onSetEnd(picked)
                }
                editing = null
            },
        )
    }
}

private enum class WindowEdge { Start, End }

@Composable
private fun WindowChip(
    label: String,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = time?.let { formatHm(it) }
                    ?: stringResource(R.string.settings_sleep_window_disabled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatHm(time: LocalTime): String =
    "%02d:%02d".format(time.hour, time.minute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                TimePicker(state = state)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                    ) {
                        Text(stringResource(R.string.dialog_ok))
                    }
                }
            }
        }
    }
}

