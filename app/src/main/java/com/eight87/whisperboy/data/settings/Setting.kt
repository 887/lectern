package com.eight87.whisperboy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * R.B.1 value type — pairs a `Flow<T>` source with a `suspend (T) -> Unit` setter so a single
 * named handle expresses "the persisted X value".
 *
 * **Why this exists.** Today every settings facet hand-rolls the quartet
 * `*PreferencesKey + Flow + setter + (sometimes) default-on-decode` (see e.g.
 * `data/playback/PlaybackSettings.kt`, `data/theme/ThemeSettings.kt`,
 * `data/library/LibraryUiSettings.kt`). For one or two knobs the boilerplate is invisible;
 * past a dozen it stops being. [Setting] collapses the quartet into one value type that
 * sub-pages can either observe (`.flow.collectAsState(...)`) or write
 * (`scope.launch { setting(newValue) }` / `setting.set(newValue)`).
 *
 * **Migration path.** Facets stay interface-shaped; the **impl** uses [setting] / [enumSetting]
 * factory helpers to build [Setting] handles, then re-exposes them as `Flow<T>` / `suspend setX(...)`
 * pairs for backward compatibility. As consumers are touched they can switch to taking the
 * [Setting] directly (or [EnumSetting] for enum-valued knobs) and the facet interface can shed
 * the Flow+setter pair. The first facet that migrated end-to-end is
 * `data/library/LibraryUiSettings.kt` (R.B.2 reference impl).
 *
 * **What is NOT in here.** Picker dialog state, "did the user just change this?" side-channels,
 * Compose anything. [Setting] is data-layer only; the Compose-side ergonomic helper that
 * builds a picker dialog around it lives at `ui/settings/SettingPicker.kt` (R.F.8).
 */
class Setting<T>(
    val flow: Flow<T>,
    private val setter: suspend (T) -> Unit,
) {
    /** Allow `setting.set(value)` as a more readable alternative to `setting(value)`. */
    suspend fun set(value: T) {
        setter(value)
    }

    /** Allow `setting(value)` invocation syntax — symmetric with reading via `setting.flow`. */
    suspend operator fun invoke(value: T) {
        setter(value)
    }
}

/**
 * R.B.1 enum specialisation — round-trips through `enum.name` so corrupt / future-version
 * stored values fall back to [default] on read (same coercion every existing facet does
 * by hand via `runCatching { enumValueOf<E>(it) }`).
 *
 * Exposes the same [flow] + [set] / `invoke` surface as [Setting]. Wraps rather than extends
 * so call sites can pass it where a `Setting<E>` is expected without variance gymnastics.
 */
class EnumSetting<E : Enum<E>>(
    val flow: Flow<E>,
    private val setter: suspend (E) -> Unit,
) {
    suspend fun set(value: E) {
        setter(value)
    }

    suspend operator fun invoke(value: E) {
        setter(value)
    }

    /** View as a generic [Setting] for code paths that want the non-enum-specialised type. */
    fun asSetting(): Setting<E> = Setting(flow, setter)
}

/**
 * Build a [Setting] from a typed Preferences key. The flow falls back to [default] when the
 * key is absent; the setter writes through `dataStore.edit { ... }`. Mirrors what every facet
 * was doing by hand.
 */
fun <T> DataStore<Preferences>.setting(
    key: Preferences.Key<T>,
    default: T,
): Setting<T> = Setting(
    flow = data.map { prefs -> prefs[key] ?: default },
    setter = { value -> edit { it[key] = value } },
)

/**
 * Build an [EnumSetting] backed by a string preference storing `enum.name`. Unknown / null
 * values coerce to [default] on read (same `runCatching { enumValueOf<E>(it) }` discipline
 * the hand-rolled facets used).
 */
inline fun <reified E : Enum<E>> DataStore<Preferences>.enumSetting(
    name: String,
    default: E,
): EnumSetting<E> {
    val key = stringPreferencesKey(name)
    return EnumSetting(
        flow = data.map { prefs ->
            prefs[key]?.let { raw -> runCatching { enumValueOf<E>(raw) }.getOrNull() } ?: default
        },
        setter = { value -> edit { it[key] = value.name } },
    )
}

/**
 * R.B.2 — build a [Setting] from a typed Preferences key with a custom [decode] (called on the
 * raw stored value, may be null) and [encode] (called on the user's setter input). Use this when
 * a knob has a non-trivial range / validation step on top of a plain Preferences type — e.g.
 * `PlaybackSettings.defaultSpeed` clamps to `0.5..3.5`, `rewindSeconds` coerces to a fixed set.
 *
 * Decode is called with the raw `prefs[key]` (i.e. `T?`); return the in-domain value (commonly
 * `if (raw != null && raw in allowed) raw else default`). Encode is called with the setter input;
 * return the value to persist (commonly `raw.coerceIn(min, max)`).
 */
fun <T> DataStore<Preferences>.setting(
    key: Preferences.Key<T>,
    decode: (T?) -> T,
    encode: (T) -> T,
): Setting<T> = Setting(
    flow = data.map { prefs -> decode(prefs[key]) },
    setter = { value -> edit { it[key] = encode(value) } },
)

/**
 * R.B.2 — build a [Setting] backed by a string-set preference. The decode normalises stored
 * values via [normalise] (commonly `String::lowercase`); the setter applies the same normalisation
 * before writing so a "DiSaBlEd.MP3" set round-trips as `{"disabled.mp3"}`. Used by
 * `LibraryScanFilterSettings.disabledExtensions`.
 */
fun DataStore<Preferences>.stringSetSetting(
    name: String,
    default: Set<String> = emptySet(),
    normalise: (String) -> String = { it },
): Setting<Set<String>> {
    val key = stringSetPreferencesKey(name)
    return Setting(
        flow = data.map { prefs ->
            (prefs[key] ?: default).mapTo(mutableSetOf(), normalise)
        },
        setter = { value ->
            val normalised = value.mapTo(mutableSetOf(), normalise)
            edit { it[key] = normalised }
        },
    )
}
