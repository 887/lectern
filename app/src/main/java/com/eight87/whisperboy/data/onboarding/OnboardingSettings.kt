package com.eight87.whisperboy.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.eight87.whisperboy.data.settings.Setting
import com.eight87.whisperboy.data.settings.setting
import kotlinx.coroutines.flow.Flow

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
 *
 * R.B.2 migration: backed by [Setting] via the [setting] factory.
 */
interface OnboardingSettings {
    val completed: Flow<Boolean>
    suspend fun setCompleted(value: Boolean)
}

class AndroidOnboardingSettings(
    dataStore: DataStore<Preferences>,
) : OnboardingSettings {

    private val completedSetting: Setting<Boolean> =
        dataStore.setting(booleanPreferencesKey("completed"), default = false)

    override val completed: Flow<Boolean> = completedSetting.flow

    override suspend fun setCompleted(value: Boolean) = completedSetting.set(value)
}
