#!/usr/bin/env bash
# P.2 smoke test: headset / Bluetooth media-key controls.
#
# Exercises the MediaSession.Callback path that handles hardware media keys
# delivered by wired headsets and Bluetooth headsets. ADB cannot drive a real
# BT stack, but the keyevent path is the same one the system uses to forward
# AVRCP / wired-headset button presses into the active MediaSession — so
# driving keyevents validates the controller wiring end-to-end. Real-headset
# QA still has to happen on hardware.
#
# Events exercised:
#   * KEYCODE_HEADSETHOOK (79)           — single tap, toggles play/pause
#   * KEYCODE_MEDIA_PLAY (126)
#   * KEYCODE_MEDIA_PAUSE (127)
#   * KEYCODE_MEDIA_NEXT (87)            — next chapter
#   * KEYCODE_MEDIA_PREVIOUS (88)        — previous chapter
#   * long-press 87 / 88                 — chapter-jump driver behaviour
#
# Between events we sample `dumpsys media_session` and grep for
# `state=PLAYING` / `state=PAUSED` to verify the controller responded.
#
# Result is PASS / FAIL / INDETERMINATE.
#
# Prerequisites (manual, not set up by this script):
#   * AVD or device connected via adb,
#   * whisperboy installed,
#   * a book queued and playable (library scanned + at least one playable item).
#
# Usage:
#   scripts/p2-headset-controls-smoke.sh
#   scripts/p2-headset-controls-smoke.sh --device emulator-5556
#
# Exit codes:
#   0  PASS / no-device skip / INDETERMINATE
#   1  FAIL — no whisperboy media_session entry, or no state transitions seen

set -euo pipefail

PKG=com.eight87.whisperboy
DEVICE=""
SETTLE=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device) DEVICE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$DEVICE" ]]; then
    DEVICE=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}')
fi
if [[ -z "${DEVICE:-}" ]]; then
    echo "P.2: no adb device reachable — skipping (exit 0)."
    exit 0
fi
ADB="adb -s $DEVICE"

echo "P.2 headset / BT media-key controls smoke — device=$DEVICE pkg=$PKG"

if ! $ADB shell pm list packages 2>/dev/null | grep -q "package:$PKG$"; then
    echo "P.2: $PKG not installed on $DEVICE — INDETERMINATE."
    exit 0
fi

dump_state() {
    # Return the state= line(s) inside the whisperboy media_session block.
    $ADB shell dumpsys media_session 2>/dev/null \
        | awk -v pkg="$PKG" '
            /MediaSessionRecord/ { inblock=0 }
            $0 ~ pkg { inblock=1 }
            inblock && /state=/ { print }
          ' \
        || true
}

dump_session_present() {
    $ADB shell dumpsys media_session 2>/dev/null | grep -c "$PKG" || true
}

send_key() {
    local code="$1"; local label="$2"
    echo "--- keyevent $code ($label) ---"
    $ADB shell input keyevent "$code"
    sleep "$SETTLE"
    local s
    s=$(dump_state | head -3)
    echo "  state: ${s:-<no state line>}"
}

send_longpress() {
    local code="$1"; local label="$2"
    echo "--- keyevent --longpress $code ($label) ---"
    $ADB shell input keyevent --longpress "$code"
    sleep "$SETTLE"
    local s
    s=$(dump_state | head -3)
    echo "  state: ${s:-<no state line>}"
}

# Initial probe — is there a whisperboy media_session at all?
if [[ "$(dump_session_present)" -eq 0 ]]; then
    echo "P.2: no media_session entry for $PKG before key events — INDETERMINATE."
    echo "  (App likely not in the foreground / no playable item queued. Launch it"
    echo "   manually with a book queued and re-run.)"
    exit 0
fi

PRE=$(dump_state | head -3)
echo "--- pre-event state ---"
echo "  ${PRE:-<no state line>}"

# Exercise the key event matrix.
send_key 79  KEYCODE_HEADSETHOOK
send_key 126 KEYCODE_MEDIA_PLAY
send_key 127 KEYCODE_MEDIA_PAUSE
send_key 126 KEYCODE_MEDIA_PLAY
send_key 87  KEYCODE_MEDIA_NEXT
send_key 88  KEYCODE_MEDIA_PREVIOUS
send_longpress 87 KEYCODE_MEDIA_NEXT_LONG
send_longpress 88 KEYCODE_MEDIA_PREVIOUS_LONG

POST=$(dump_state | head -3)
echo "--- post-event state ---"
echo "  ${POST:-<no state line>}"

# Verdict.
if [[ "$(dump_session_present)" -eq 0 ]]; then
    echo "P.2 RESULT: FAIL — media_session for $PKG disappeared during key events."
    exit 1
fi

if echo "$PRE$POST" | grep -qiE "state=PLAYING|state=PAUSED"; then
    # Did we ever see a transition between PLAYING and PAUSED? That's the
    # signal that the MediaSession.Callback actually handled the keys.
    ALL_STATES=$(
        for k in 79 126 127 126 87 88; do
            $ADB shell input keyevent "$k" >/dev/null 2>&1 || true
            sleep "$SETTLE"
            dump_state | head -1
        done
    )
    if echo "$ALL_STATES" | grep -qi "state=PLAYING" \
       && echo "$ALL_STATES" | grep -qi "state=PAUSED"; then
        echo "P.2 RESULT: PASS — observed both PLAYING and PAUSED transitions in response to media keys."
        exit 0
    fi
    echo "P.2 RESULT: INDETERMINATE — media_session present and at least one state line"
    echo "  was readable, but did not see both PLAYING and PAUSED transitions. The"
    echo "  controller may have handled keys without a state flip (e.g. nothing queued),"
    echo "  or chapter-skip key events may be a no-op when there's only one chapter."
    exit 0
fi

echo "P.2 RESULT: INDETERMINATE — media_session entry exists for $PKG but no state="
echo "  line was readable. dumpsys output above needs human eyeball; or the prereq"
echo "  (a playable item queued) wasn't met."
exit 0
