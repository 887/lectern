#!/usr/bin/env bash
# G.1 cold-start regression check (cold-start-perf.md Phase G).
#
# Force-stops whisperboy and cold-launches WhisperboyActivity 5 times, parsing
# `TotalTime` from `am start -W` output (the conventional cold-start metric:
# system_server-stamped end-to-end from launch intent to first-frame display).
# Reports min / median / max / mean and exits non-zero if the median exceeds
# the threshold (default 1300 ms — the AVD budget set in cold-start-perf.md).
#
# Usage:
#   scripts/cold-start-check.sh                              # first adb device
#   scripts/cold-start-check.sh --device emulator-5556
#   scripts/cold-start-check.sh --threshold 1500             # ms
#   scripts/cold-start-check.sh --runs 7
#
# Exit codes:
#   0  median <= threshold (PASS)
#   1  median >  threshold (REGRESSION)
#   2  setup error / no device / parse failure
#
# The app must already be installed on the target device.

set -euo pipefail

PKG=com.eight87.whisperboy
ACTIVITY=".WhisperboyActivity"
THRESHOLD=1300
RUNS=5
SETTLE_SECS=2
DEVICE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)    DEVICE="$2"; shift 2 ;;
        --threshold) THRESHOLD="$2"; shift 2 ;;
        --runs)      RUNS="$2"; shift 2 ;;
        --settle)    SETTLE_SECS="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not on PATH" >&2
    exit 2
fi

if [[ -z "$DEVICE" ]]; then
    # First serial from `adb devices` (skip the "List of devices attached" header).
    DEVICE="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi

if [[ -z "$DEVICE" ]]; then
    echo "no device — skipping cold-start regression check (G.1)."
    echo "  Start an emulator (or wifi-adb a phone) and re-run."
    exit 0
fi

ADB="adb -s $DEVICE"

# Verify the package is installed; nothing to launch otherwise.
if ! $ADB shell pm path "$PKG" >/dev/null 2>&1; then
    echo "package $PKG not installed on $DEVICE — install the debug APK first" >&2
    exit 2
fi

echo "=== cold-start regression check (G.1) ==="
echo "device:    $DEVICE"
echo "package:   $PKG/$ACTIVITY"
echo "runs:      $RUNS"
echo "threshold: ${THRESHOLD} ms (median)"
echo

samples=()
for i in $(seq 1 "$RUNS"); do
    $ADB shell am force-stop "$PKG"
    sleep "$SETTLE_SECS"
    # `am start -W` blocks until the activity is "fully drawn" and prints
    # ThisTime / TotalTime / WaitTime. TotalTime is the cold-start figure.
    out="$($ADB shell am start -W -n "$PKG/$ACTIVITY" 2>&1)"
    total="$(printf '%s\n' "$out" | awk -F': *' '/^TotalTime:/ {print $2; exit}' | tr -d '\r')"
    if ! [[ "$total" =~ ^[0-9]+$ ]]; then
        echo "run $i: could not parse TotalTime from am start output:" >&2
        printf '%s\n' "$out" >&2
        exit 2
    fi
    printf 'run %d/%d: %s ms\n' "$i" "$RUNS" "$total"
    samples+=("$total")
done

# Sort samples ascending for min / median / max.
IFS=$'\n' sorted=($(printf '%s\n' "${samples[@]}" | sort -n))
unset IFS

count=${#sorted[@]}
min=${sorted[0]}
max=${sorted[$((count-1))]}

# Median: middle element for odd count, average of two middles for even.
if (( count % 2 == 1 )); then
    median=${sorted[$((count/2))]}
else
    lo=${sorted[$((count/2 - 1))]}
    hi=${sorted[$((count/2))]}
    median=$(( (lo + hi) / 2 ))
fi

# Mean: integer arithmetic, good enough for this report.
sum=0
for s in "${sorted[@]}"; do sum=$((sum + s)); done
mean=$((sum / count))

echo
echo "=== results ==="
echo "min:    ${min} ms"
echo "median: ${median} ms"
echo "max:    ${max} ms"
echo "mean:   ${mean} ms"
echo "threshold: ${THRESHOLD} ms"

if (( median > THRESHOLD )); then
    echo
    echo "FAIL: median ${median} ms exceeds threshold ${THRESHOLD} ms"
    exit 1
fi

echo
echo "PASS: median ${median} ms within threshold ${THRESHOLD} ms"
exit 0
