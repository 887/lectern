package com.eight87.whisperboy.data.theme

/**
 * User-selectable theme mode (Phase K.5).
 *
 * - [Light] / [Dark] explicitly override the system setting.
 * - [FollowSystem] defers to `isSystemInDarkTheme()`.
 *
 * Persisted by [ThemeSettings] as the enum constant name.
 */
enum class ThemeMode {
    Light,
    Dark,
    FollowSystem,
}
