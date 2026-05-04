package com.eight87.whisperboy.playback

import androidx.media3.session.MediaLibraryService.MediaLibrarySession

/**
 * Stub library-session callback. Phase B leaves the browseable media tree empty — direct
 * `MediaController` connections from the activity still work, but Auto / Wear-OS browse
 * surfaces will see nothing.
 *
 * The real tree (Currently listening / Not started / All books / Authors) lands in
 * Phase N alongside the rest of the Auto integration.
 */
class WhisperboyLibrarySessionCallback : MediaLibrarySession.Callback
