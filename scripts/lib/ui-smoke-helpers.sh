#!/usr/bin/env bash
# Shared helpers for AVD UI smoke scripts. Source this file from a smoke script.
#
# Establishes:
#   PKG, ACTIVITY (whisperboy defaults — override before sourcing if needed)
#   ADB (set after resolve_device)
#   TMP_DIR (auto-cleaned on EXIT trap installed here)
#
# Provides:
#   log <msg>
#   resolve_device [optional preferred device]   -> sets DEVICE, ADB
#   ensure_pkg_installed
#   force_stop_launch
#   clear_app_data
#   dump_ui <out_xml_path>
#   find_tap_target <xml> <regex>                -> echoes "x y" or empty
#   tap_text <label> <regex>                     -> 0=tapped, 1=not found, 2=dump fail
#   tap_coords <x> <y>
#   ui_contains <xml> <regex>                    -> 0=present, 1=missing
#
# Conventions:
#   * Patterns target text= or content-desc= attributes from `uiautomator dump`.
#   * Coordinates returned are the centre of the matched node's `bounds=`.
#   * Sparse-tree on headless AVD is the documented INDETERMINATE case.

: "${PKG:=com.eight87.whisperboy}"
: "${ACTIVITY:=.WhisperboyActivity}"
: "${WAIT_AFTER_LAUNCH:=4}"
: "${WAIT_AFTER_TAP:=2}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

log() {
    local tag="${SMOKE_TAG:-ui-smoke}"
    printf '[%s] %s\n' "$tag" "$*"
}

resolve_device() {
    local preferred="${1:-}"
    if [[ -n "$preferred" ]]; then
        DEVICE="$preferred"
    else
        DEVICE=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}')
    fi
    if [[ -z "${DEVICE:-}" ]]; then
        return 1
    fi
    ADB="adb -s $DEVICE"
    log "using device: $DEVICE"
    return 0
}

ensure_pkg_installed() {
    if $ADB shell pm list packages 2>/dev/null | grep -q "package:$PKG$"; then
        log "package $PKG installed"
        return 0
    fi
    local apk="app/build/outputs/apk/debug/whisperboy-debug.apk"
    if [[ -f "$apk" ]]; then
        log "installing $apk"
        $ADB install -r "$apk" >/dev/null || { log "install failed"; return 1; }
        return 0
    fi
    log "package $PKG not installed and no debug APK at $apk"
    return 1
}

clear_app_data() {
    log "force-stop + pm clear $PKG"
    $ADB shell am force-stop "$PKG" >/dev/null 2>&1 || true
    $ADB shell pm clear "$PKG" >/dev/null 2>&1 || true
}

force_stop_launch() {
    log "force-stop + launch $PKG/$ACTIVITY"
    $ADB shell am force-stop "$PKG" >/dev/null 2>&1 || true
    $ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
    sleep "$WAIT_AFTER_LAUNCH"
}

dump_ui() {
    local out="$1"
    $ADB shell uiautomator dump /sdcard/whisperboy_ui.xml >/dev/null 2>&1 || return 1
    $ADB pull /sdcard/whisperboy_ui.xml "$out" >/dev/null 2>&1 || return 1
    [[ -s "$out" ]]
}

# Find centre of first node whose text= or content-desc= matches the regex.
find_tap_target() {
    local xml="$1"; local pattern="$2"
    # Split each XML node onto its own line. `tr` is 1:1 so we use sed for
    # multi-char replacement.
    local line
    line=$(sed 's/>/>\n/g' "$xml" | grep -E "(text|content-desc)=\"[^\"]*${pattern}[^\"]*\"" | head -1 || true)
    [[ -z "$line" ]] && return 1
    # Match bounds on THIS node's line only.
    local bounds
    bounds=$(echo "$line" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
    [[ -z "$bounds" ]] && return 1
    local nums
    nums=$(echo "$bounds" | grep -oE '[0-9]+')
    local x1 y1 x2 y2
    x1=$(echo "$nums" | sed -n '1p')
    y1=$(echo "$nums" | sed -n '2p')
    x2=$(echo "$nums" | sed -n '3p')
    y2=$(echo "$nums" | sed -n '4p')
    echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}

tap_text() {
    local label="$1"; local pattern="$2"
    local xml="$TMP_DIR/ui-$(date +%s%N).xml"
    if ! dump_ui "$xml"; then
        log "[$label] uiautomator dump failed"
        return 2
    fi
    local coords
    coords=$(find_tap_target "$xml" "$pattern" || true)
    if [[ -z "$coords" ]]; then
        log "[$label] no node matched /$pattern/"
        return 1
    fi
    log "[$label] tapping at $coords (matched /$pattern/)"
    # shellcheck disable=SC2086
    $ADB shell input tap $coords >/dev/null
    sleep "$WAIT_AFTER_TAP"
    return 0
}

tap_coords() {
    local x="$1" y="$2"
    log "tap ($x,$y)"
    $ADB shell input tap "$x" "$y" >/dev/null
    sleep "$WAIT_AFTER_TAP"
}

ui_contains() {
    local xml="$1"; local pattern="$2"
    grep -qE "(text|content-desc)=\"[^\"]*${pattern}[^\"]*\"" "$xml"
}
