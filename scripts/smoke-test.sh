#!/usr/bin/env bash
# B.5 smoke test: verify ExoPlayer decodes the five formats whisperboy needs.
#
#   MP3, M4B (AAC-in-MP4), OGG Vorbis, FLAC, WebM/Matroska
#
# Generates 1-second sine fixtures with ffmpeg, pushes to /data/local/tmp/ on the device,
# broadcasts com.eight87.whisperboy.action.SMOKE_PLAY with each path, asserts STATE_READY
# in logcat under the "whisperboy.smoke" tag.
#
# The app must be installed (`./gradlew :app:assembleDebug && adb install ...`); this script
# also launches the activity to ensure WhisperboyApplication.onCreate has run before the
# first broadcast lands.
#
# Override the device with DEVICE=emulator-5556 ./scripts/smoke-test.sh

set -euo pipefail

PKG=com.eight87.whisperboy
ACTION=com.eight87.whisperboy.action.SMOKE_PLAY
TAG=whisperboy.smoke
DEVICE="${DEVICE:-emulator-5554}"
ADB="adb -s $DEVICE"

LOCAL_DIR=/tmp/whisperboy-smoke
DEV_DIR=/data/local/tmp/whisperboy-smoke

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 2; }
}
require_cmd ffmpeg
require_cmd adb

echo "=== generating fixtures ==="
mkdir -p "$LOCAL_DIR"
ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -c:a libmp3lame  "$LOCAL_DIR/foo.mp3"
ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -c:a aac         "$LOCAL_DIR/foo.m4a"
cp "$LOCAL_DIR/foo.m4a" "$LOCAL_DIR/foo.m4b"   # same audio, .m4b extension exercises the MP4 sniffer
ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -c:a libvorbis   "$LOCAL_DIR/foo.ogg"
ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -c:a flac        "$LOCAL_DIR/foo.flac"
ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=440:duration=1" -c:a libvorbis -f webm "$LOCAL_DIR/foo.webm"

echo "=== pushing to $DEVICE:$DEV_DIR ==="
$ADB shell mkdir -p "$DEV_DIR"
for f in "$LOCAL_DIR"/foo.*; do
    $ADB push "$f" "$DEV_DIR/$(basename "$f")" >/dev/null
done
# Make readable by the app uid.
$ADB shell chmod 0644 "$DEV_DIR"/foo.* >/dev/null || true

echo "=== launching app to bring WhisperboyApplication up ==="
$ADB shell am start -n "$PKG/.WhisperboyActivity" >/dev/null
sleep 2

echo "=== running format checks ==="
ok=0
fail=0
for ext in mp3 m4b ogg flac webm; do
    path_on_dev="$DEV_DIR/foo.$ext"
    $ADB logcat -c
    $ADB shell am broadcast -a "$ACTION" -p "$PKG" --es path "$path_on_dev" >/dev/null

    found=0
    for _ in $(seq 1 50); do
        if $ADB logcat -d -s "$TAG":I 2>/dev/null | grep -q "foo.$ext.*STATE_READY"; then
            found=1; break
        fi
        sleep 0.1
    done

    if [ "$found" -eq 1 ]; then
        printf "  %-5s  ok\n" "$ext"
        ok=$((ok+1))
    else
        printf "  %-5s  FAIL\n" "$ext"
        $ADB logcat -d -s "$TAG":E 2>/dev/null | grep "foo.$ext" | head -3 | sed 's/^/    /'
        fail=$((fail+1))
    fi
done

echo
echo "results: $ok ok, $fail fail"
[ "$fail" -eq 0 ]
