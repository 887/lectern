package com.eight87.whisperboy.ui.routes

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.OnboardingFirstScanRoute
import com.eight87.whisperboy.OnboardingFolderPickerRoute
import com.eight87.whisperboy.OnboardingPermissionsRoute
import com.eight87.whisperboy.OnboardingWelcomeRoute
import com.eight87.whisperboy.ui.onboarding.OnboardingFirstScanScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingFolderPickerScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingPermissionsScreen
import com.eight87.whisperboy.ui.onboarding.OnboardingWelcomeScreen

/**
 * Phase L — first-run onboarding flow destinations. Four steps in order:
 * Welcome → Permissions → Folder picker → First scan. The terminal "Continue"
 * button on First scan calls [RouteScope.finishOnboarding] which replaces the
 * back stack with `HomeRoute`.
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
            onNext = { backStack.add(OnboardingFirstScanRoute) },
            modifier = Modifier.safeDrawingPadding(),
        )
    }
    entry<OnboardingFirstScanRoute> {
        OnboardingFirstScanScreen(
            libraryRescanCoordinator = graph.libraryRescanCoordinator,
            bookSource = graph.bookSource,
            chapterSource = graph.chapterSource,
            onboardingSettings = graph.onboardingSettings,
            onFinish = scope.finishOnboarding,
            modifier = Modifier.safeDrawingPadding(),
        )
    }
}
