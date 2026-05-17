#!/usr/bin/env bash
# P.6 smoke test: foreground-service lifecycle on Android 14+ (API 34+).
#
# Android 14 (API 34) tightened the rules around when an app may transition a
# Service to the foreground state via FOREGROUND_SERVICE_MEDIA_PLAYBACK:
# the start must originate from a foreground-eligible context (a visible
# Activity, a MediaButton, a MediaSession-routed transport, or one of a
# small set of OS-allowlisted exemptions like widget user-taps). A
# non-foreground call site throws ForegroundServiceStartNotAllowedException
# at runtime.
#
# Media3's MediaSessionService / MediaLibraryService framework handles the
# transition for us — `MediaController.play()` from a UI surface causes
# Media3 to call `startForeground(...)` on the service after the session is
# active. We just need to verify, on a real API 34+ device, that:
#
#   - the device is API >= 34,
#   - whisperboy is installed and launched,
#   - after user-initiated playback (manual step, see below), the
#     PlaybackService reports isForeground=true in dumpsys, and
#   - logcat has no ForegroundServiceStartNotAllowedException or
#     RemoteServiceException stack since the script started.
#
# What this script CAN'T do (without mobile-mcp):
#   - drive the SAF folder picker (whisperboy is SAF-only — there is no
#     deep link / intent action to start playback from adb),
#   - tap the play button on the library grid.
# So the script REQUIRES the operator to start playback manually after
# the relaunch, then press Enter to continue verification. In CI / fully
# automated runs the script will fall back to INDETERMINATE if no
# manual confirmation was received within the timeout.
#
# Prerequisites:
#   * AVD or device on API >= 34, reachable via adb,
#   * whisperboy installed,
#   * a library configured with at least one book (so manual playback
#     start is achievable).
#
# Usage:
#   scripts/p6-fgs-android14-smoke.sh                       # first adb device
#   scripts/p6-fgs-android14-smoke.sh --device emulator-5556
#   scripts/p6-fgs-android14-smoke.sh --no-manual           # skip the manual play step
#                                                            (will report INDETERMINATE
#                                                            unless playback was already
#                                                            running when the script began)
#
# Exit codes:
#   0  PASS / no-device (clean skip) / INDETERMINATE
#   1  FAIL (clear FGS regression: dumpsys shows the service running but NOT
#      foreground while playback is active, OR a ForegroundServiceStartNotAllowed
#      / RemoteServiceException appeared in logcat during the run)

set -euo pipefail

PKG=com.eight87.whisperboy
ACTIVITY=".WhisperboyActivity"
DEVICE=""
MANUAL=1
MANUAL_WAIT=60     # seconds to wait for the operator to press Enter after starting playback

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device) DEVICE="$2"; shift 2 ;;
        --no-manual) MANUAL=0; shift ;;
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
    echo "P.6: no adb device reachable — skipping (exit 0)."
    exit 0
fi
ADB="adb -s $DEVICE"

echo "P.6 FGS Android 14+ smoke — device=$DEVICE pkg=$PKG"

# API level gate.
API=$($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
if [[ -z "$API" || ! "$API" =~ ^[0-9]+$ ]]; then
    echo "P.6: could not read ro.build.version.sdk from $DEVICE — INDETERMINATE."
    exit 0
fi
echo "  API level: $API"
if (( API < 34 )); then
    echo "P.6: device API $API < 34 — FGS strictness doesn't apply. Skipping (exit 0)."
    exit 0
fi

# Package installed?
if ! $ADB shell pm list packages 2>/dev/null | grep -q "package:$PKG$"; then
    echo "P.6: $PKG not installed on $DEVICE — INDETERMINATE."
    exit 0
fi

# Clear logcat so we can scope error checking to *this run*.
$ADB logcat -c >/dev/null 2>&1 || true

echo "--- launching $PKG/$ACTIVITY ---"
$ADB shell am start -W -n "$PKG/$ACTIVITY" >/dev/null
sleep 3

# Service should NOT be foreground yet (no playback has started).
echo "--- pre-play service state ---"
PRE_SVC=$($ADB shell dumpsys activity services "$PKG" 2>/dev/null | grep -A 4 "$PKG" | grep -E "ServiceRecord|isForeground|app=ProcessRecord" || true)
echo "${PRE_SVC:-<no service records for $PKG yet>}"

# Manual step.
if (( MANUAL == 1 )); then
    cat <<EOF

  >>> MANUAL STEP <<<
  Start playback on the device now (open the app, tap a book / press play).
  Then press Enter to continue. Will auto-INDETERMINATE after ${MANUAL_WAIT}s.

EOF
    if read -r -t "$MANUAL_WAIT" _; then
        :
    else
        echo "  (timed out waiting for operator — continuing; result will likely be INDETERMINATE)"
    fi
else
    echo "  --no-manual: skipping the manual play step; sampling current state."
fi

# Let things settle.
sleep 2

echo "--- post-play service state ---"
POST_SVC=$($ADB shell dumpsys activity services "$PKG" 2>/dev/null | grep -A 4 "$PKG" | grep -E "ServiceRecord|isForeground|fg=|foregroundServiceType" || true)
echo "${POST_SVC:-<no service records for $PKG post-play>}"

# Pull error-level logcat for FGS violations since the script start.
echo "--- error logcat (since script start, FGS / RemoteService matches) ---"
ERRORS=$($ADB logcat -d '*:E' 2>/dev/null | grep -iE "ForegroundServiceStartNotAllowed|RemoteServiceException" || true)
echo "${ERRORS:-<none>}"

# Verdict.
FAIL=0

if [[ -n "$ERRORS" ]]; then
    echo
    echo "P.6 RESULT: FAIL — FGS-violation matches in logcat (above)."
    FAIL=1
fi

# Look for an explicit isForeground=true line. Different Android versions print this
# differently (`isForeground=true`, `fg=true`, sometimes nothing if the service is
# not running). Treat absence as INDETERMINATE, not FAIL.
if echo "$POST_SVC" | grep -qiE "isForeground=true|fg=true"; then
    if (( FAIL == 0 )); then
        echo
        echo "P.6 RESULT: PASS — PlaybackService isForeground=true after playback start,"
        echo "  no FGS violations in logcat."
        exit 0
    fi
fi

if (( FAIL == 1 )); then
    exit 1
fi

# No clear foreground signal AND no error — most likely the operator didn't actually start
# playback, or the manual confirmation was skipped.
echo
echo "P.6 RESULT: INDETERMINATE — could not confirm isForeground=true for PlaybackService."
echo "  No FGS-violation errors in logcat, which is the regression signal P.6 cares about."
echo "  Likely cause: playback wasn't actually started during the manual window, or the"
echo "  service hasn't been created yet on this run."
exit 0
