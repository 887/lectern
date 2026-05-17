package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.OnboardingFolderPickerRoute
import com.eight87.whisperboy.OnboardingPermissionsRoute
import com.eight87.whisperboy.OnboardingWelcomeRoute
import com.eight87.whisperboy.ui.onboarding.OnboardingFolderPickerScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingPermissionsScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingWelcomeScreen

/**
 * Phase L — first-run onboarding flow destinations. Three steps:
 * Welcome → Permissions → Folder picker. The terminal folder-pick action sets
 * [com.eight87.whisperboy.data.onboarding.OnboardingSettings.setCompleted]`(true)`
 * and calls [RouteScope.finishOnboarding], which replaces the back stack with
 * `HomeRoute`.
 *
 * The retired `OnboardingFirstScanRoute` previously gated onboarding on the
 * initial scan settling. That blocked the user, and — worse — if the app was
 * closed before the scan finished, the completed flag never persisted, so the
 * user re-entered onboarding on every cold start. The library now renders its
 * skeleton/empty state while the background scan runs (Voice / tonearmboy
 * pattern); progress is surfaced via the in-library `LibraryScanProgressBanner`.
 */
@Suppress("NOTHING_TO_INLINE") internal inline fun EntryProviderScope<NavKey>.registerOnboardingEntries(scope: RouteScope) {
    val graph = scope.graph
    val backStack = scope.backStack

    entry<OnboardingWelcomeRoute> {
        OnboardingWelcomeScreen(
            onGetStarted = { backStack.add(OnboardingPermissionsRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<OnboardingPermissionsRoute> {
        OnboardingPermissionsScreen(
            onNext = { backStack.add(OnboardingFolderPickerRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<OnboardingFolderPickerRoute> {
        OnboardingFolderPickerScreen(
            persistedUriPermissionStore = graph.persistedUriPermissionStore,
            onboardingSettings = graph.onboardingSettings,
            onFinish = scope.finishOnboarding,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
