package com.eight87.whisperboy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.eight87.whisperboy.data.library.AndroidPersistedUriPermissionStore
import com.eight87.whisperboy.data.library.LibraryDatabase
import com.eight87.whisperboy.data.library.LibraryScanner
import com.eight87.whisperboy.data.library.PersistedUriPermissionStore
import com.eight87.whisperboy.data.library.SafLibraryScanner
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

    /**
     * Library cache database. Eagerly constructed so a misconfigured schema fails fast at app
     * start rather than on first scan. Phase D.4's `LibraryRepository` wraps the DAOs behind
     * the narrow `BookSource` / `ChapterSource` / `BookmarkSource` interfaces.
     */
    val libraryDatabase: LibraryDatabase = Room.databaseBuilder(
        appContext,
        LibraryDatabase::class.java,
        "library.db",
    ).build()

    /**
     * Phase D.2's SAF tree walker. Composables / future settings / Phase D.5 rescan triggers
     * call this via the [LibraryScanner] interface, never the concrete impl. Phase D.4's
     * `LibraryRepository` will own the scan→write pipeline.
     */
    val libraryScanner: LibraryScanner = SafLibraryScanner(appContext)

    fun release() {
        playerHolder.release()
    }
}
