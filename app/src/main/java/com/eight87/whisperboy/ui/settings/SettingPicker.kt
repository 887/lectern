package com.eight87.whisperboy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.eight87.whisperboy.data.settings.Setting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * R.F.8 — Compose-side ergonomic helper for the common settings-sub-page picker pattern.
 *
 * **What this fixes.** Every settings sub-page today rolls its own
 * `var xPicker by remember { mutableStateOf(false) }` toggle plus a `Dialog`/`Sheet` plus
 * a `scope.launch { settings.setX(value) }` body. With ~6 sub-pages and 3-4 pickers each
 * that's ~100+ LOC of identical-shape glue, and the picker-visible flag and the
 * scope-launching-the-setter live in lexically distant places. This helper folds
 * "is the picker visible" + "user picked a value" + "user dismissed" into a single
 * `state` object whose surface is:
 *
 * - `state.visible: Boolean` — bind to `if (state.visible) RadioPickerDialog(...)`
 * - `state.show()` — open the picker (call from the row click)
 * - `state.select(value)` — write through to the [Setting] and close the picker
 * - `state.dismiss()` — close without writing
 *
 * **Migration path.** Land the helper here; rewire each existing sub-page
 * (`PlaybackSettingsScreen`, `SleepTimerSettingsScreen`, `LibrarySettingsScreen`,
 * `ThemeSettingsScreen`, the per-default sub-pages) as it's touched next. The helper is
 * additive — pages that haven't moved still work.
 *
 * The companion [RadioPickerDialog] is a generic radio-list dialog suitable for the most
 * common enum-picker case; sub-pages with custom layouts (e.g. the sleep-timer-duration
 * `TimePickerDialog`) can wire their own dialog against the same [SettingPickerState].
 */
@Stable
class SettingPickerState<T> internal constructor(
    private val setting: Setting<T>,
    private val scope: CoroutineScope,
) {
    var visible: Boolean by mutableStateOf(false)
        private set

    fun show() {
        visible = true
    }

    fun dismiss() {
        visible = false
    }

    /** Persist [value] to the bound [Setting] and close the picker. */
    fun select(value: T) {
        scope.launch { setting(value) }
        visible = false
    }
}

/**
 * Build a [SettingPickerState] bound to [setting]. The state is `remember`-stable across
 * recompositions so toggling `visible` only re-runs the dialog branch.
 *
 * @param setting the persisted value handle; `select(value)` writes through.
 * @param current the currently-collected value, passed in so the caller controls how
 *   `setting.flow` is collected (typically via `collectAsStateWithLifecycle`).
 *   Today it's reserved for callers that want to debounce / pre-select against current; the
 *   first integration just feeds it back into the dialog's `selected` parameter.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun <T> rememberSettingPicker(
    setting: Setting<T>,
    current: T,
): SettingPickerState<T> {
    val scope = rememberCoroutineScope()
    return remember(setting) { SettingPickerState(setting, scope) }
}

/**
 * Generic radio-list dialog. Suitable for any enum / small-set picker — the most common
 * shape of [SettingPickerState] consumer. Sub-pages with bespoke layouts (time pickers,
 * folder-type sheets) skip this and render their own dialog wired against the same state.
 */
@Composable
fun <T> RadioPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    label: (T) -> String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
