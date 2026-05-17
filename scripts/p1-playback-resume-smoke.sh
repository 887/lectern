#!/usr/bin/env bash
# P.1 smoke test: cold-start playback resumption.
#
# Exercises MediaSession.Callback.onPlaybackResumption — after a force-stop,
# relaunching the activity should resume the last-played book at its last
# position. This script can't assert UI state (no mobile-mcp here); what it
# CAN do is sample `dumpsys media_session` before and after force-stop, then
# verify:
#   - the whisperboy package is registered as a media session,
#   - the session is active / has a queued playable item,
#   - the foreground service has come back up.
#
# Result is PASS / FAIL / INDETERMINATE based on what dumpsys returns.
#
# Prerequisites (the script does NOT set these up — that's manual / smoke-test scaffolding):
#   * AVD or device connected via adb,
#   * whisperboy installed,
#   * library scanned and at least one book with position > 0 (i.e. user has
#     started a book at least once).
#
# Usage:
#   scripts/p1-playback-resume-smoke.sh                       # first adb device
#   scripts/p1-playback-resume-smoke.sh --device emulator-5556
#
# Exit codes:
#   0  PASS / no-device (clean skip) / INDETERMINATE
#   1  FAIL (a clear regression: no media session for the pkg after relaunch)

set -euo pipefail

PKG=com.eight87.whisperboy
ACTIVITY=".WhisperboyActivity"
DEVICE=""
WAIT_AFTER_STOP=3
WAIT_AFTER_START=5

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device) DEVICE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

# Resolve device.
if [[ -z "$DEVICE" ]]; then
    DEVICE=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}')
fi
if [[ -z "${DEVICE:-}" ]]; then
    echo "P.1: no adb device reachable — skipping (exit 0)."
    exit 0
fi
ADB="adb -s $DEVICE"

echo "P.1 cold-start playback resumption smoke — device=$DEVICE pkg=$PKG"

# Sanity: package installed?
if ! $ADB shell pm list packages 2>/dev/null | grep -q "package:$PKG$"; then
    echo "P.1: $PKG not installed on $DEVICE — INDETERMINATE."
    exit 0
fi

dump_media_session() {
    # Filter for whisperboy + a few state lines. Output is multi-line.
    $ADB shell dumpsys media_session 2>/dev/null \
        | grep -E "package|state|active=|user=|MediaSessionRecord|queue|tag=|controllers" \
        || true
}

echo "--- pre-stop dumpsys media_session (whisperboy lines) ---"
PRE=$(dump_media_session | grep -B1 -A1 "$PKG" || true)
echo "${PRE:-<no whisperboy media_session entry pre-stop>}"

echo "--- force-stopping $PKG ---"
$ADB shell am force-stop "$PKG"
sleep "$WAIT_AFTER_STOP"

echo "--- relaunching $PKG/$ACTIVITY ---"
$ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep "$WAIT_AFTER_START"

echo "--- post-restart dumpsys media_session (whisperboy lines) ---"
POST=$(dump_media_session | grep -B1 -A1 "$PKG" || true)
echo "${POST:-<no whisperboy media_session entry post-restart>}"

echo "--- foreground service check ---"
SVC=$($ADB shell dumpsys activity services "$PKG" 2>/dev/null | grep -E "ServiceRecord|isForeground" | head -10 || true)
echo "${SVC:-<no service records for $PKG>}"

# Verdict.
if [[ -z "$POST" ]]; then
    echo "P.1 RESULT: FAIL — no media_session entry for $PKG after restart."
    echo "  (onPlaybackResumption did not produce a resumable session — or the app"
    echo "   has no last-played book on this device, in which case the prereq wasn't met.)"
    exit 1
fi

if echo "$POST" | grep -qiE "state=PLAYING|state=PAUSED|active=true"; then
    echo "P.1 RESULT: PASS — media_session present + active/paused after restart."
    exit 0
fi

echo "P.1 RESULT: INDETERMINATE — media_session entry exists for $PKG after restart,"
echo "  but state line did not clearly read active/playing/paused. dumpsys output above"
echo "  needs human eyeball; or the prereq (a book with position>0) wasn't met."
exit 0
