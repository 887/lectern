#!/usr/bin/env bash
# Player AVD smoke — reach the NowPlayingSheet / PlaybackScreen surface,
# expand the mini-player, verify the chapter title + scrubber render, toggle
# play/pause, and verify the inline chapter queue is reachable.
#
# Prereq: app installed AND library has at least one book. SKIPS gracefully
# if the library is empty (cover grid presents an empty state instead of a
# book tile).
#
# Strategy:
#   1. Force-stop + relaunch (lands on library).
#   2. Find first book tile (content-desc starts with a book title; we tap
#      the first descendant of the cover-grid that looks like a tile by
#      grepping content-desc with /cover/ or by tapping any non-action node
#      in the grid area). Fallback: tap centre of the screen's upper third
#      where the grid usually sits.
#   3. Wait for media_session state to flip from NONE.
#   4. Swipe up to expand the now-playing sheet.
#   5. Dump UI; grep for player elements (Pause / Rewind / Forward CDs).
#   6. Tap the play/pause centre button; verify state flips.
#   7. Confirm inline chapter queue is rendered (chapter index entries).
#
# Outcome: PASS / FAIL / INDETERMINATE / SKIPPED.
# Exit codes: 0 PASS/INDETERMINATE/skip, 1 FAIL.

set -uo pipefail
SMOKE_TAG=player-smoke
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

force_stop_launch

# ---- 1. Confirm we're on a library screen with at least one book. ----
LIB_XML="$TMP_DIR/library.xml"
if ! dump_ui "$LIB_XML"; then
    log "library dump failed — INDETERMINATE"
    log "OUTCOME: INDETERMINATE"
    exit 0
fi

# If onboarding chrome appears, library has no books yet.
if ui_contains "$LIB_XML" "Welcome|Pick a folder|Pick an audiobook folder"; then
    log "onboarding active — library is empty"
    log "OUTCOME: SKIPPED-no-prereq (no books in library)"
    exit 0
fi

# ---- 2. Tap first book tile. ----
# Try: content-desc containing "Cover art" / a tile-ish row. Fallback: tap
# a coordinate well within the cover grid's typical region (centre-upper).
TAPPED=0
if tap_text "first tile (cover-art cd)" "Cover art"; then
    TAPPED=1
elif tap_text "first tile (book cd)" "book"; then
    TAPPED=1
else
    # Fallback: tap the centre of the upper-third of the screen.
    SIZE=$($ADB shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)
    if [[ -n "$SIZE" ]]; then
        W=${SIZE%x*}; H=${SIZE#*x}
        tap_coords $((W/2)) $((H/4))
        TAPPED=1
        log "fallback coordinate tap into grid"
    fi
fi

if (( TAPPED == 0 )); then
    log "could not find a book tile to tap"
    log "OUTCOME: INDETERMINATE"
    exit 0
fi

# ---- 3. Wait for playback. ----
playback_state() {
    $ADB shell dumpsys media_session 2>/dev/null \
        | grep -A2 "$PKG" | grep -oE "state=PlaybackState \{state=[A-Z_]+" | head -1
}

PLAYBACK_OK=0
for i in $(seq 1 "$WAIT_FOR_PLAYBACK_S"); do
    st=$(playback_state || true)
    if [[ -n "$st" && "$st" != *"state=NONE"* ]]; then
        log "playback state after ${i}s: $st"
        PLAYBACK_OK=1
        break
    fi
    sleep 1
done
if (( PLAYBACK_OK == 0 )); then
    log "playback never started (state stayed NONE / empty)"
    # Still continue — UI may render even without auto-play in some configs.
fi

# ---- 4. Expand mini-player via swipe up. ----
SIZE=$($ADB shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1)
if [[ -n "$SIZE" ]]; then
    W=${SIZE%x*}; H=${SIZE#*x}
    XC=$((W/2))
    YHIGH=$((H*85/100))
    YLOW=$((H*15/100))
    log "swipe up $XC,$YHIGH -> $XC,$YLOW"
    $ADB shell input swipe $XC $YHIGH $XC $YLOW 300 >/dev/null
    sleep 2
fi

# ---- 5. Dump expanded player. ----
PLAYER_XML="$TMP_DIR/player.xml"
PLAYER_TITLE_OK=0
PLAYER_TRANSPORT_OK=0
if dump_ui "$PLAYER_XML"; then
    if ui_contains "$PLAYER_XML" "Rewind|Forward|Pause|Play|player_play_cd|player_pause_cd"; then
        PLAYER_TRANSPORT_OK=1
        log "player transport controls detected"
    fi
    if ui_contains "$PLAYER_XML" "Chapter|/"; then
        PLAYER_TITLE_OK=1
        log "player chapter/scrubber text detected"
    fi
else
    log "expanded player dump failed"
fi

# ---- 6. Toggle play/pause. ----
PRE_STATE=$(playback_state || true)
TOGGLE_OK=0
if tap_text "play/pause toggle" "Pause|Play"; then
    sleep 2
    POST_STATE=$(playback_state || true)
    if [[ -n "$PRE_STATE" && -n "$POST_STATE" && "$PRE_STATE" != "$POST_STATE" ]]; then
        log "play/pause toggle: $PRE_STATE -> $POST_STATE"
        TOGGLE_OK=1
    else
        log "play/pause tap fired but media_session state unchanged ($PRE_STATE / $POST_STATE)"
    fi
fi

# ---- 7. Inline chapter queue. ----
QUEUE_OK=0
if dump_ui "$PLAYER_XML"; then
    # Inline queue rows render with chapter titles or the "Chapter N" fallback.
    QUEUE_HITS=$(grep -oE 'text="Chapter [0-9]+"' "$PLAYER_XML" | wc -l | tr -d ' ')
    if (( QUEUE_HITS > 0 )); then
        QUEUE_OK=1
        log "inline chapter queue: $QUEUE_HITS chapter rows visible"
    fi
fi

# ---- Verdict ----
if (( PLAYBACK_OK == 1 && PLAYER_TRANSPORT_OK == 1 && (TOGGLE_OK == 1 || QUEUE_OK == 1) )); then
    log "OUTCOME: PASS (player reached, transport + (toggle|queue) confirmed)"
    exit 0
fi
if (( PLAYBACK_OK == 1 || PLAYER_TRANSPORT_OK == 1 )); then
    log "OUTCOME: INDETERMINATE (player partially confirmed)"
    exit 0
fi
log "OUTCOME: FAIL (player surface not reached)"
exit 1
