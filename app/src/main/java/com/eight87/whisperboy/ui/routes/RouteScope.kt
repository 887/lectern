package com.eight87.whisperboy.ui.routes

import androidx.compose.material3.SnackbarHostState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.eight87.whisperboy.AppGraph
import kotlinx.coroutines.CoroutineScope

/**
 * R.E.2 — handle bag passed to every per-destination `Register(scope)` extension.
 *
 * Modeled on tonearmboy's R.E pattern. Holds the dependencies that more than one
 * route needs (the [AppGraph], the back stack, the composition-scoped [CoroutineScope],
 * a snackbar host, and the cross-cutting callbacks that aren't in the graph):
 *
 * - [openSheet] is the "expand the now-playing sheet to 1f" callback owned by
 *   [com.eight87.whisperboy.WhisperboyApp] (the sheet itself is an overlay, not a route).
 *   The home route fires it on `onBookTap`; the bookmark route fires it after a
 *   bookmark seek to make the player surface visible.
 * - [finishOnboarding] replaces the entire back stack with `HomeRoute`, used by
 *   the onboarding first-scan "Continue" button so back-from-Home does not
 *   re-enter the onboarding flow.
 *
 * Each [Destination] takes a `RouteScope` and renders its screen.
 */
class RouteScope(
    val graph: AppGraph,
    val backStack: NavBackStack<NavKey>,
    val coroutineScope: CoroutineScope,
    val snackbar: SnackbarHostState,
    val openSheet: () -> Unit,
    val finishOnboarding: () -> Unit,
)
