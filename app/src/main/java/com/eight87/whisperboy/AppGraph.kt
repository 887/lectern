package com.eight87.whisperboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.eight87.whisperboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.playback.PlayerHolder

/**
 * Composition root. The only place in the app that knows concrete types for long-lived singletons.
 *
 * Created once in [WhisperboyApplication.onCreate]; ViewModels / composables / services receive
 * narrow interfaces from here, never the AppGraph itself.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val playerHolder: PlayerHolder = PlayerHolder(appContext)

    private val libraryRootsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("library_roots") }
    )

    val persistedUriPermissionStore: PersistedUriPermissionStore =
        AndroidPersistedUriPermissionStore(appContext, libraryRootsDataStore)

    fun release() {
        playerHolder.release()
    }
}
