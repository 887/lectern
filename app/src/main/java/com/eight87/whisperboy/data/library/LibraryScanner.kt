package com.eight87.whisperboy.data.library

/**
 * Narrow data interface (Phase R.A pattern) for the SAF tree walker.
 *
 * Phase D.2 ships [SafLibraryScanner] as the only implementation; tests can substitute fakes.
 * Phase D.5's rescan triggers (manual / on foreground / on root add-or-remove) call this
 * suspend method on `Dispatchers.IO`.
 */
interface LibraryScanner {

    /**
     * Walk [roots] and return the structural [ScanSnapshot]. Implementations should invoke
     * [onProgress] as books are discovered so the in-library banner can tick continuously
     * during the (potentially slow) SAF traversal — without this, counts stay frozen at 0
     * for the entire structural pass while the user waits.
     *
     * The callback's arguments are the *cumulative* discovery counts so far and the
     * relative path / name of the folder currently being walked (when known). Errors raised
     * by [onProgress] must not abort the scan; the callback is for UX surfaces, not
     * control flow. The default no-op keeps existing test paths working.
     */
    suspend fun scan(
        roots: List<LibraryRoot>,
        onProgress: suspend (booksFound: Int, chaptersFound: Int, currentFolder: String?) -> Unit = { _, _, _ -> },
    ): ScanSnapshot
}
