#!/usr/bin/env bash
# P.3 smoke test: process death during scan.
#
# Per-book Room transactions during scan mean killing the process mid-scan
# should lose only the in-flight book, not unwind everything committed so
# far. This script can't reliably trigger a SAF rescan from ADB (the
# "Rescan now" button is a UI element; deep links into Settings exist as a
# future ADB shortcut but aren't shipped). What this script CAN do:
#
#   1. Launch the app fresh.
#   2. Wait briefly into whatever boot-time / app-foreground scan happens.
#   3. force-stop mid-scan.
#   4. Restart the activity.
#   5. Verify Room contains at least the books committed before the kill.
#
# Counting books is best-effort via `run-as` + sqlite3. `run-as` only works
# on userdebug builds (debug-signed APKs on AVDs are fine; release on a
# real device usually is not). If `run-as` fails the script falls back to
# `dumpsys media_session` to check that a recognised library is mounted.
#
# Usage:
#   scripts/p3-scan-process-death-smoke.sh                       # first adb device
#   scripts/p3-scan-process-death-smoke.sh --device emulator-5556
#
# Exit codes:
#   0  PASS / INDETERMINATE / no-device (clean skip)
#   1  FAIL (clear regression: post-restart book count < pre-kill count)

set -euo pipefail

PKG=com.eight87.whisperboy
ACTIVITY=".WhisperboyActivity"
DB_PATH=/data/data/com.eight87.whisperboy/databases/whisperboy.db
DEVICE=""
MID_SCAN_DELAY=1
RESTART_SETTLE=4

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
    echo "P.3: no adb device reachable — skipping (exit 0)."
    exit 0
fi
ADB="adb -s $DEVICE"

echo "P.3 mid-scan process-death smoke — device=$DEVICE pkg=$PKG"

if ! $ADB shell pm list packages 2>/dev/null | grep -q "package:$PKG$"; then
    echo "P.3: $PKG not installed on $DEVICE — INDETERMINATE."
    exit 0
fi

book_count_via_runas() {
    # Returns book count or empty string on failure. Stderr is silenced.
    $ADB shell "run-as $PKG sh -c 'sqlite3 $DB_PATH \"SELECT count(*) FROM books;\" 2>/dev/null'" 2>/dev/null \
        | tr -d '\r' | head -1
}

media_session_has_library() {
    $ADB shell dumpsys media_session 2>/dev/null \
        | grep -E "package=$PKG|MediaLibrarySession|root" | head -5
}

echo "--- launching $PKG/$ACTIVITY ---"
$ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep "$MID_SCAN_DELAY"

PRE_COUNT=$(book_count_via_runas || true)
if [[ -n "${PRE_COUNT:-}" && "$PRE_COUNT" =~ ^[0-9]+$ ]]; then
    echo "pre-kill books in Room: $PRE_COUNT"
else
    echo "pre-kill book count: run-as/sqlite3 unavailable (build type may not be debuggable)"
    PRE_COUNT=""
fi

echo "--- force-stopping $PKG mid-scan ---"
$ADB shell am force-stop "$PKG"
sleep 1

echo "--- relaunching $PKG/$ACTIVITY ---"
$ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep "$RESTART_SETTLE"

POST_COUNT=$(book_count_via_runas || true)
if [[ -n "${POST_COUNT:-}" && "$POST_COUNT" =~ ^[0-9]+$ ]]; then
    echo "post-restart books in Room: $POST_COUNT"
else
    echo "post-restart book count: run-as/sqlite3 unavailable"
    POST_COUNT=""
fi

# Verdict.
if [[ -n "$PRE_COUNT" && -n "$POST_COUNT" ]]; then
    if (( POST_COUNT < PRE_COUNT )); then
        echo "P.3 RESULT: FAIL — post-restart count ($POST_COUNT) < pre-kill count ($PRE_COUNT)."
        echo "  Committed books were lost on process death; per-book Room transactions"
        echo "  are not landing per the design contract."
        exit 1
    fi
    echo "P.3 RESULT: PASS — book count preserved across mid-scan kill ($PRE_COUNT -> $POST_COUNT)."
    exit 0
fi

# Fallback: can't read Room directly. Check media_session for a recognised library.
echo "--- fallback: dumpsys media_session ---"
LIB=$(media_session_has_library || true)
echo "${LIB:-<no media_session library evidence>}"
if [[ -n "$LIB" ]]; then
    echo "P.3 RESULT: INDETERMINATE — couldn't read Room directly, but media_session"
    echo "  reports a recognised library after restart. Need real-device QA for a"
    echo "  definitive PASS/FAIL."
    exit 0
fi

echo "P.3 RESULT: INDETERMINATE — couldn't read Room and no media_session library"
echo "  evidence found. May indicate the app hasn't fully started, or no library"
echo "  is registered. Real-device or AVD-with-fixtures run needed."
exit 0
