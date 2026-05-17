package com.eight87.whisperboy.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Narrow facet (R.A pattern) for the persisted "onboarding completed" flag.
 *
 * Phase L state. Backed by its own `onboarding` Preferences DataStore so resetting
 * onboarding (debug menu, future) doesn't affect `library_roots` / `library_ui` /
 * `playback_settings`.
 *
 * Default is `false`: a fresh install routes through the Welcome → Permissions →
 * Folder picker → First-scan flow. Existing users with persisted roots from the
 * pre-Phase-L interim shell still see onboarding once on next upgrade (cheap
 * one-time cost; alternative is migrating from the presence of any roots, which
 * couples the two stores).
 */
interface OnboardingSettings {
    val completed: Flow<Boolean>
    suspend fun setCompleted(value: Boolean)
}

class AndroidOnboardingSettings(
    private val dataStore: DataStore<Preferences>,
) : OnboardingSettings {

    override val completed: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_COMPLETED] ?: false }

    override suspend fun setCompleted(value: Boolean) {
        dataStore.edit { it[KEY_COMPLETED] = value }
    }

    private companion object {
        val KEY_COMPLETED = booleanPreferencesKey("completed")
    }
}
