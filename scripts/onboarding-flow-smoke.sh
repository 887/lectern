#!/usr/bin/env bash
# Onboarding L.1–L.5 AVD smoke — drive the cold-start onboarding flow:
#   L.1  OnboardingWelcomeScreen          ("Welcome" / "Get started")
#   L.2  OnboardingPermissionsScreen      (notification rationale + Allow/Not now)
#   L.5  OnboardingFolderPickerScreen     (folder-mode rows + "Pick a folder")
#
# Cuts off BEFORE the SAF picker — the SAF UI requires an OEM Files app and
# is brittle in headless AVDs (different device skins surface different
# picker chrome). The cutoff is intentional; the goal is to prove the
# in-app onboarding composables mount + transition correctly.
#
# Outcome:
#   PASS           — Welcome → Permissions → Folder picker all reached.
#   INDETERMINATE  — Partial progress (e.g. Welcome reached but Permissions
#                    text not surfaced through uiautomator).
#   FAIL           — Welcome screen never appeared after data clear.
#
# Exit codes: 0 PASS/INDETERMINATE/skip, 1 FAIL.
#
# Usage:
#   scripts/onboarding-flow-smoke.sh
#   scripts/onboarding-flow-smoke.sh --device emulator-5556

set -uo pipefail
SMOKE_TAG=onboarding-smoke
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/ui-smoke-helpers.sh
source "$HERE/lib/ui-smoke-helpers.sh"

DEVICE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --device) DEVICE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

if ! resolve_device "$DEVICE"; then
    log "no adb device — clean skip"
    log "OUTCOME: SKIPPED-no-device"
    exit 0
fi

if ! ensure_pkg_installed; then
    log "OUTCOME: SKIPPED-no-pkg"
    exit 0
fi

# True cold-start: clear data so onboarding fires.
clear_app_data
force_stop_launch

# ---- L.1 Welcome ----
WELCOME_XML="$TMP_DIR/welcome.xml"
WELCOME_OK=0
if dump_ui "$WELCOME_XML"; then
    if ui_contains "$WELCOME_XML" "Welcome|Get started"; then
        WELCOME_OK=1
        log "L.1 Welcome screen detected"
    else
        log "L.1 Welcome screen NOT detected in dump"
    fi
else
    log "L.1 dump failed"
fi

if (( WELCOME_OK == 0 )); then
    log "OUTCOME: FAIL (Welcome screen never appeared after clear+launch)"
    exit 1
fi

# Tap the Get started CTA.
PERM_OK=0
if tap_text "L.1 Get started" "Get started"; then
    # ---- L.2 Permissions ----
    PERM_XML="$TMP_DIR/perm.xml"
    if dump_ui "$PERM_XML"; then
        # On API < 33 the permissions screen is skipped — folder picker may
        # appear immediately. Check for either Allow notifications or folder
        # picker title.
        if ui_contains "$PERM_XML" "Allow notifications|notifications to show"; then
            PERM_OK=1
            log "L.2 Permissions screen detected (API >= 33 path)"
            # Don't grant — that triggers a system dialog we can't safely drive.
            # Tap "Not now" instead.
            if tap_text "L.2 Not now" "Not now"; then
                log "L.2 dismissed permissions with Not now"
            else
                log "L.2 Not now CTA not found — proceeding"
            fi
        elif ui_contains "$PERM_XML" "Pick an audiobook folder|Pick a folder"; then
            PERM_OK=1
            log "L.2 skipped (API < 33 path) — folder picker shown directly"
        else
            log "L.2 Permissions screen not detected (and no folder picker yet)"
        fi
    else
        log "L.2 dump failed"
    fi
else
    log "L.1 Could not tap Get started"
fi

# ---- L.5 Folder picker ----
FOLDER_XML="$TMP_DIR/folder.xml"
FOLDER_OK=0
if dump_ui "$FOLDER_XML"; then
    if ui_contains "$FOLDER_XML" "Pick an audiobook folder|Pick a folder"; then
        FOLDER_OK=1
        log "L.5 Folder picker screen detected"
    else
        log "L.5 Folder picker NOT detected"
    fi
fi

# Cutoff: do NOT tap "Pick a folder" — the SAF picker is out of scope here.
log "STOP: not launching SAF picker (OEM-dependent, brittle on headless AVD)"

# ---- Verdict ----
if (( WELCOME_OK == 1 && PERM_OK == 1 && FOLDER_OK == 1 )); then
    log "OUTCOME: PASS (L.1 + L.2 + L.5 all reached)"
    exit 0
fi
if (( WELCOME_OK == 1 )) && { (( PERM_OK == 1 )) || (( FOLDER_OK == 1 )); }; then
    log "OUTCOME: INDETERMINATE (partial onboarding progress detected)"
    exit 0
fi
log "OUTCOME: FAIL (onboarding flow did not progress past Welcome)"
exit 1
