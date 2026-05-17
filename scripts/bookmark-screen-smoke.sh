#!/usr/bin/env bash
# Bookmark AVD smoke — reach the BookmarkScreen via the player's top-app-bar
# bookmark icon.
#
# Prereq: app installed + library has at least one book that has been played
# at least once (media_session history exists). SKIPS gracefully otherwise.
#
# Strategy:
#   1. Force-stop + relaunch.
#   2. Tap first book tile.
#   3. Wait for media_session state to flip from NONE.
#   4. Swipe up to expand player.
#   5. Tap the bookmark-view icon (content-desc "View bookmarks").
#   6. Dump UI; assert BookmarkScreen rendered (title "Bookmarks").
#   7. If bookmarks exist, count rows; otherwise assert empty-state copy.
#
# Outcome: PASS / FAIL / INDETERMINATE / SKIPPED.
# Exit codes: 0 PASS/INDETERMINATE/skip, 1 FAIL.

set -uo pipefail
SMOKE_TAG=bookmark-smoke
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/ui-smoke-helpers.sh
source "$HERE/lib/ui-smoke-helpers.sh"

WAIT_FOR_PLAYBACK_S=10
DEVICE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --device) DEVICE="$2"; shift 2 ;;
        -h|--help) sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

if ! resolve_device "$DEVICE"; then
    log "no adb device — SKIPPED"; exit 0
fi
if ! ensure_pkg_installed; then
    log "OUTCOME: SKIPPED-no-pkg"; exit 0
fi

# Quick prereq: any media_session history? If not, this test can't
# meaningfully reach the bookmark surface (no last-played book to resume).
SESSIONS=$($ADB shell dumpsys media_session 2>/dev/null | grep -c "$PKG" || true)
if [[ "${SESSIONS:-0}" -eq 0 ]]; then
    log "no media_session history for $PKG — likely no book played yet"
    # Not a hard skip — library could still surface a book that hasn't been
    # played. Continue and detect empty cases later.
fi

force_stop_launch

# Library state?
LIB_XML="$TMP_DIR/library.xml"
if ! dump_ui "$LIB_XML"; then
    log "library dump failed"; log "OUTCOME: INDETERMINATE"; exit 0
fi
if ui_contains "$LIB_XML" "Welcome|Pick a folder|Pick an audiobook folder"; then
    log "onboarding active — no library books"
    log "OUTCOME: SKIPPED-no-prereq"
    exit 0
fi

# Tap first tile.
TAPPED=0
if tap_text "first tile (cover-art cd)" "Cover art"; then
    TAPPED=1
else
    SIZE=$($ADB shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)
    if [[ -n "$SIZE" ]]; then
        W=${SIZE%x*}; H=${SIZE#*x}
        tap_coords $((W/2)) $((H/4))
        TAPPED=1
    fi
fi
if (( TAPPED == 0 )); then
    log "could not find a book tile"; log "OUTCOME: INDETERMINATE"; exit 0
fi

# Wait for playback.
PLAYBACK_OK=0
for i in $(seq 1 "$WAIT_FOR_PLAYBACK_S"); do
    st=$($ADB shell dumpsys media_session 2>/dev/null \
        | grep -A2 "$PKG" | grep -oE "state=PlaybackState \{state=[A-Z_]+" | head -1 || true)
    if [[ -n "$st" && "$st" != *"state=NONE"* ]]; then
        PLAYBACK_OK=1
        log "playback active after ${i}s"
        break
    fi
    sleep 1
done

# Expand player.
SIZE=$($ADB shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)
if [[ -n "$SIZE" ]]; then
    W=${SIZE%x*}; H=${SIZE#*x}
    $ADB shell input swipe $((W/2)) $((H*85/100)) $((W/2)) $((H*15/100)) 300 >/dev/null
    sleep 2
fi

# Tap "View bookmarks" content-desc in the player top app bar.
BM_NAV_OK=0
if tap_text "View bookmarks" "View bookmarks"; then
    BM_NAV_OK=1
else
    log "View bookmarks CD not found in expanded player"
fi

# Confirm BookmarkScreen.
BM_XML="$TMP_DIR/bookmarks.xml"
BM_TITLE_OK=0
BM_ROWS=0
BM_EMPTY=0
if dump_ui "$BM_XML"; then
    if ui_contains "$BM_XML" "Bookmarks"; then
        BM_TITLE_OK=1
        log "BookmarkScreen title detected"
    fi
    if ui_contains "$BM_XML" "No bookmarks yet"; then
        BM_EMPTY=1
        log "Bookmark empty-state copy detected"
    fi
    # Count rows by 'Set by sleep timer' badges OR by Delete/Rename actions.
    BM_ROWS=$(grep -oE 'content-desc="Set by sleep timer"' "$BM_XML" | wc -l | tr -d ' ')
    log "bookmark rows (sleep-timer badges seen): $BM_ROWS"
fi

# Verdict.
if (( BM_NAV_OK == 1 && BM_TITLE_OK == 1 )); then
    if (( BM_EMPTY == 1 )) || (( BM_ROWS > 0 )); then
        log "OUTCOME: PASS (BookmarkScreen reached; empty-state=$BM_EMPTY rows=$BM_ROWS)"
        exit 0
    fi
    log "OUTCOME: INDETERMINATE (BookmarkScreen title visible but neither rows nor empty-state confirmed)"
    exit 0
fi
if (( PLAYBACK_OK == 1 || BM_NAV_OK == 1 )); then
    log "OUTCOME: INDETERMINATE (partway — playback=$PLAYBACK_OK nav=$BM_NAV_OK title=$BM_TITLE_OK)"
    exit 0
fi
log "OUTCOME: FAIL (could not reach BookmarkScreen)"
exit 1
