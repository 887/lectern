package com.eight87.whisperboy

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eight87.whisperboy.ui.home.HomeScreen

@Composable
fun WhisperboyApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    val graph = (LocalContext.current.applicationContext as WhisperboyApplication).graph

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    persistedUriPermissionStore = graph.persistedUriPermissionStore,
                    libraryRescanCoordinator = graph.libraryRescanCoordinator,
                    bookSource = graph.bookSource,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
