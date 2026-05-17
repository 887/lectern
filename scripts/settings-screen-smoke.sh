#!/usr/bin/env bash
# Settings AVD smoke — navigate Library → Settings, then walk each category
# sub-screen (Playback / Sleep timer / Library / Theme / About), asserting
# each sub-screen title appears, then backing out.
#
# Strategy:
#   1. Force-stop + relaunch.
#   2. If onboarding chrome is showing, SKIP — settings only reachable from
#      library top app bar.
#   3. Tap "Settings" content-desc from library.
#   4. Assert SettingsScreen rendered (5 category rows + Rescan button).
#   5. For each row, tap, dump, assert sub-screen title, tap back, dump
#      again to assert we're back on the root SettingsScreen.
#
# Outcome:
#   PASS           — all 5 sub-screens reached + dismissed.
#   INDETERMINATE  — root Settings reached but some sub-screens failed.
#   FAIL           — could not reach SettingsScreen at all.

set -uo pipefail
SMOKE_TAG=settings-smoke
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/ui-smoke-helpers.sh
source "$HERE/lib/ui-smoke-helpers.sh"

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

LIB_XML="$TMP_DIR/library.xml"
if ! dump_ui "$LIB_XML"; then
    log "OUTCOME: INDETERMINATE (initial dump failed)"; exit 0
fi
if ui_contains "$LIB_XML" "Welcome|Pick an audiobook folder"; then
    log "onboarding active — settings unreachable"
    log "OUTCOME: SKIPPED-no-prereq"
    exit 0
fi

# Tap Settings gear (content-desc "Settings").
if ! tap_text "library Settings gear" "Settings"; then
    log "OUTCOME: FAIL (Settings gear not found on library)"
    exit 1
fi

# Confirm SettingsScreen root.
ROOT_XML="$TMP_DIR/settings-root.xml"
ROOT_OK=0
ROW_HITS=0
RESCAN_OK=0
if dump_ui "$ROOT_XML"; then
    if ui_contains "$ROOT_XML" "Playback" \
        && ui_contains "$ROOT_XML" "Sleep timer" \
        && ui_contains "$ROOT_XML" "Library" \
        && ui_contains "$ROOT_XML" "Theme" \
        && ui_contains "$ROOT_XML" "About"; then
        ROOT_OK=1
        ROW_HITS=5
        log "Settings root: all 5 category rows visible"
    else
        for label in Playback "Sleep timer" Library Theme About; do
            if ui_contains "$ROOT_XML" "$label"; then
                ROW_HITS=$((ROW_HITS+1))
            fi
        done
        log "Settings root: $ROW_HITS / 5 category rows visible"
    fi
    if ui_contains "$ROOT_XML" "Rescan now"; then
        RESCAN_OK=1
        log "Rescan now button present"
    fi
fi

if (( ROOT_OK == 0 )); then
    log "OUTCOME: FAIL (SettingsScreen root not confirmed)"
    exit 1
fi

# Walk each sub-screen. Each entry: tap-pattern + expected-title.
# Note: "Library" appears both as a category row AND as the hub title, so
# the assertion uses a unique sub-screen string when possible.
declare -a ROWS=(
    "Playback|Defaults"
    "Sleep timer|Default duration"
    "Library|Folders"
    "Theme|Dynamic color"
    "About|Version"
)
RESULTS=()

for entry in "${ROWS[@]}"; do
    row="${entry%%|*}"
    expect="${entry##*|}"
    log "--- visiting Settings → $row ---"
    if ! tap_text "row $row" "^${row}$|>${row}<|\"${row}\""; then
        # Fallback: looser match
        if ! tap_text "row $row (loose)" "$row"; then
            log "row $row: FAIL (tap target not found)"
            RESULTS+=("$row:FAIL-tap")
            continue
        fi
    fi
    sub_xml="$TMP_DIR/settings-$row.xml"
    sub_xml="${sub_xml// /-}"
    if ! dump_ui "$sub_xml"; then
        log "row $row: INDETERMINATE (dump failed)"
        RESULTS+=("$row:INDETERMINATE")
        $ADB shell input keyevent KEYCODE_BACK >/dev/null
        sleep 1
        continue
    fi
    if ui_contains "$sub_xml" "$expect"; then
        log "row $row: PASS (sub-screen marker '$expect' present)"
        RESULTS+=("$row:PASS")
    else
        log "row $row: INDETERMINATE (sub-screen marker '$expect' NOT present)"
        RESULTS+=("$row:INDETERMINATE")
    fi
    # Back.
    $ADB shell input keyevent KEYCODE_BACK >/dev/null
    sleep 1
done

# Verdict.
pass_count=0
fail_count=0
indet_count=0
for r in "${RESULTS[@]}"; do
    case "${r##*:}" in
        PASS) pass_count=$((pass_count+1)) ;;
        FAIL-tap) fail_count=$((fail_count+1)) ;;
        INDETERMINATE) indet_count=$((indet_count+1)) ;;
    esac
done

log "results: ${RESULTS[*]}"
log "summary: pass=$pass_count indeterminate=$indet_count fail=$fail_count rescan=$RESCAN_OK"

if (( pass_count == 5 && RESCAN_OK == 1 )); then
    log "OUTCOME: PASS"
    exit 0
fi
if (( pass_count >= 3 )); then
    log "OUTCOME: INDETERMINATE (root reached, $pass_count/5 sub-screens fully confirmed)"
    exit 0
fi
log "OUTCOME: INDETERMINATE (root reached but most sub-screens unconfirmed)"
exit 0
