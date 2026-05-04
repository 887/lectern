package com.eight87.whisperboy.data.library

/**
 * Narrow data interface (Phase R.A pattern) for the SAF tree walker.
 *
 * Phase D.2 ships [SafLibraryScanner] as the only implementation; tests can substitute fakes.
 * Phase D.5's rescan triggers (manual / on foreground / on root add-or-remove) call this
 * suspend method on `Dispatchers.IO`.
 */
interface LibraryScanner {

    suspend fun scan(roots: List<LibraryRoot>): ScanSnapshot
}
