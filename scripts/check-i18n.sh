#!/usr/bin/env bash
# i18n discipline audit — see docs/plans/translations.md.
#
# Greps Compose UI sources under app/src/main/java/.../ui/** for hardcoded user-facing
# string patterns. Exits non-zero if any are found.
#
# What's flagged:
#   Text("literal")              — Compose Text composable with a string literal
#   text = "literal"             — keyword arg form
#   contentDescription = "..."   — accessibility content description with a literal
#
# What's NOT flagged (exempted by intent):
#   stringResource(R.string.*)   — already resource-backed (the goal)
#   @Preview-annotated functions — dev-only, never user-facing
#   testTag("...")               — test identifiers, not user copy
#   Log.* / android.util.Log     — log strings, not user copy
#   format-string constants ("%s", "%d", "%.1f")
#   Sentinel strings (single-token, surrounded by underscores like "_hidden_")
#
# Run standalone or as a pre-flight gate inside scripts/build-release-apk.sh.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI_ROOT="$ROOT/app/src/main/java/com/eight87/whisperboy/ui"

if [ ! -d "$UI_ROOT" ]; then
    echo "no $UI_ROOT yet — phase E hasn't shipped any UI; nothing to audit"
    exit 0
fi

# Build a temp file listing all .kt files in ui/** that aren't preview-annotated functions.
# We do a simple line-level grep first, then filter out lines that fall inside @Preview
# functions via a Python pass.

violations="$(mktemp)"
trap 'rm -f "$violations"' EXIT

# Pattern 1: Text("literal") — capture line, file, and the string
grep -rn --include='*.kt' \
    -E '(\bText\s*\(\s*"[^"]+"|text\s*=\s*"[^"]+"|contentDescription\s*=\s*"[^"]+")' \
    "$UI_ROOT" 2>/dev/null > "$violations" || true

# Filter out:
#  - format-string-only (line is just "%s" / "%d" / "%.1f" pattern strings)
#  - sentinel strings ("_foo_") used as internal keys
#  - lines inside Log.* calls
#  - Modifier.testTag("...")
filtered="$(grep -v -E 'Log\.|testTag\(|"_[a-zA-Z]+_"' "$violations" || true)"
filtered="$(echo "$filtered" | grep -v -E '"[%][0-9.]*[sdfn]"' || true)"

# Reject results that come from inside a @Preview-annotated function. We approximate this
# by walking each violating file backward from the offending line and looking for the
# nearest function declaration; if that function is preceded by @Preview within ~10 lines,
# it's a preview and we drop the violation.
real_violations=""
while IFS= read -r line; do
    [ -z "$line" ] && continue
    file="${line%%:*}"
    rest="${line#*:}"
    lineno="${rest%%:*}"
    # Find the nearest preceding 'fun ' declaration line.
    fun_lineno="$(awk -v target="$lineno" 'NR<=target && /^[[:space:]]*(internal[[:space:]]+|private[[:space:]]+)?fun[[:space:]]/ {found=NR} END {print found+0}' "$file")"
    if [ "$fun_lineno" -gt 0 ]; then
        # Check the 10 lines preceding the fun declaration for @Preview.
        start=$((fun_lineno - 10))
        [ "$start" -lt 1 ] && start=1
        if sed -n "${start},${fun_lineno}p" "$file" | grep -q '@Preview'; then
            continue
        fi
    fi
    real_violations="$real_violations
$line"
done <<< "$filtered"

real_violations="$(echo "$real_violations" | sed '/^$/d')"

if [ -z "$real_violations" ]; then
    echo "i18n audit: clean — no hardcoded user-facing strings in ui/**"
    exit 0
fi

echo "i18n audit FAILED — hardcoded user-facing strings found in ui/**:"
echo
echo "$real_violations"
echo
echo "Fix: move each literal to app/src/main/res/values/strings.xml and use stringResource(R.string.<key>)."
echo "See docs/plans/translations.md for the naming scheme (<surface>_<role> snake_case)."
exit 1
