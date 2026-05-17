#!/usr/bin/env bash
# translation-progress.sh — generate a markdown table of translation coverage
# per locale, comparing every `values-<locale>/strings*.xml` against the
# canonical English keys in `values/strings*.xml`.
#
# Usage:
#   scripts/translation-progress.sh             # print markdown table to stdout
#   scripts/translation-progress.sh --update    # rewrite README.md between markers
#   scripts/translation-progress.sh --test      # run golden-file tests
#
# The script counts <string> and <plurals> entries by `name=` attribute,
# skipping any row with `translatable="false"`. Canonical keys = union of
# all `app/src/main/res/values/strings*.xml`. A locale's coverage = number
# of canonical keys that also appear in any of its `values-<locale>/strings*.xml`.
#
# TODO: hook into build-release-apk.sh once it lands — invoke `--update`
# immediately before the `git tag` step so the release commit picks up
# the refreshed README block.

set -euo pipefail

ROOT="$(dirname "$(readlink -f "$0")")/.."
RES_DIR="${ROOT}/app/src/main/res"

# Display names for common locales. Fallback: bare locale tag.
locale_display() {
    case "$1" in
        de)    echo "German" ;;
        fr)    echo "French" ;;
        es)    echo "Spanish" ;;
        it)    echo "Italian" ;;
        pt)    echo "Portuguese" ;;
        pt-rBR|pt-BR) echo "Portuguese (Brazil)" ;;
        nl)    echo "Dutch" ;;
        pl)    echo "Polish" ;;
        ru)    echo "Russian" ;;
        ja)    echo "Japanese" ;;
        zh|zh-rCN|zh-CN) echo "Chinese (Simplified)" ;;
        zh-rTW|zh-TW)    echo "Chinese (Traditional)" ;;
        ko)    echo "Korean" ;;
        sv)    echo "Swedish" ;;
        nb|no) echo "Norwegian" ;;
        da)    echo "Danish" ;;
        fi)    echo "Finnish" ;;
        cs)    echo "Czech" ;;
        tr)    echo "Turkish" ;;
        uk)    echo "Ukrainian" ;;
        *)     echo "$1" ;;
    esac
}

# Extract translatable key names from a directory of strings*.xml files.
# Skips any element whose tag carries translatable="false".
extract_keys() {
    local dir="$1"
    [ -d "$dir" ] || { return 0; }
    local files
    files="$(find "$dir" -maxdepth 1 -name 'strings*.xml' -type f 2>/dev/null | sort)"
    [ -z "$files" ] && return 0
    # Match <string name="..."> and <plurals name="..."> on a single line.
    # Skip rows with translatable="false".
    # shellcheck disable=SC2086
    grep -hE '<(string|plurals)[[:space:]][^>]*name="[^"]+"' $files \
        | grep -v 'translatable="false"' \
        | sed -E 's/.*<(string|plurals)[[:space:]][^>]*name="([^"]+)".*/\2/' \
        | sort -u
}

# Generate markdown table to stdout.
generate_table() {
    local canonical_keys total
    canonical_keys="$(extract_keys "${RES_DIR}/values")"
    total="$(printf '%s\n' "${canonical_keys}" | grep -c . || true)"

    if [ "${total}" -eq 0 ]; then
        echo "_No translatable keys found in \`values/\`._"
        return
    fi

    echo "| Language | Coverage | Status |"
    echo "| --- | --- | --- |"

    local locale_dirs
    locale_dirs="$(find "${RES_DIR}" -maxdepth 1 -type d -name 'values-*' 2>/dev/null \
        | sed -E 's,.*/values-,,' \
        | awk '!/^(night|w[0-9]+dp|sw[0-9]+dp|land|port|v[0-9]+|h[0-9]+dp|hdpi|mdpi|xhdpi|xxhdpi|xxxhdpi|notnight|car|desk|television|watch)$/' \
        | sort || true)"

    if [ -z "${locale_dirs}" ]; then
        echo "| _none yet_ | — | — |"
        return
    fi

    while IFS= read -r locale; do
        [ -z "${locale}" ] && continue
        local locale_keys done_count percent name link bar
        locale_keys="$(extract_keys "${RES_DIR}/values-${locale}")"
        done_count="$(comm -12 <(printf '%s\n' "${canonical_keys}") <(printf '%s\n' "${locale_keys}") | grep -c . || true)"
        percent=$(( done_count * 100 / total ))
        name="$(locale_display "${locale}")"
        link="[${name}](app/src/main/res/values-${locale}/strings.xml)"
        bar="${done_count}/${total} (${percent}%)"
        echo "| ${link} | ${bar} | $(status_emoji "${percent}") |"
    done <<<"${locale_dirs}"
}

status_emoji() {
    local pct="$1"
    if   [ "${pct}" -ge 95 ]; then echo "complete"
    elif [ "${pct}" -ge 50 ]; then echo "in progress"
    elif [ "${pct}" -gt  0 ]; then echo "started"
    else                            echo "empty"
    fi
}

# Rewrite README.md between <!-- TRANSLATIONS-START --> / <!-- TRANSLATIONS-END --> markers.
update_readme() {
    local readme="${ROOT}/README.md"
    [ -f "${readme}" ] || { echo "README.md not found at ${readme}" >&2; exit 1; }

    if ! grep -q '<!-- TRANSLATIONS-START -->' "${readme}"; then
        echo "README.md missing <!-- TRANSLATIONS-START --> marker — add it first" >&2
        exit 1
    fi
    if ! grep -q '<!-- TRANSLATIONS-END -->' "${readme}"; then
        echo "README.md missing <!-- TRANSLATIONS-END --> marker — add it first" >&2
        exit 1
    fi

    local table tmp
    table="$(generate_table)"
    tmp="$(mktemp)"
    awk -v table="${table}" '
        /<!-- TRANSLATIONS-START -->/ { print; print ""; print table; print ""; in_block=1; next }
        /<!-- TRANSLATIONS-END -->/   { in_block=0 }
        !in_block { print }
    ' "${readme}" >"${tmp}"
    mv "${tmp}" "${readme}"
    echo "[translation-progress] README.md updated"
}

# Golden tests: scripts/tests/translation-progress/{empty,full-de,partial-de,no-locale}.
run_tests() {
    local test_root="${ROOT}/scripts/tests/translation-progress"
    [ -d "${test_root}" ] || { echo "no golden tests at ${test_root}" >&2; exit 1; }

    local fails=0 passes=0
    for case_dir in "${test_root}"/*/; do
        [ -d "${case_dir}" ] || continue
        local case_name
        case_name="$(basename "${case_dir}")"
        local expected="${case_dir}expected.md"
        [ -f "${expected}" ] || { echo "  ! ${case_name}: missing expected.md"; fails=$((fails+1)); continue; }

        local saved_res="${RES_DIR}"
        RES_DIR="${case_dir}res"
        local actual
        actual="$(generate_table)"
        RES_DIR="${saved_res}"

        if [ "$(printf '%s\n' "${actual}")" = "$(cat "${expected}")" ]; then
            echo "  ✓ ${case_name}"
            passes=$((passes+1))
        else
            echo "  ✗ ${case_name}"
            diff <(printf '%s\n' "${actual}") "${expected}" || true
            fails=$((fails+1))
        fi
    done
    echo "[translation-progress] ${passes} passed, ${fails} failed"
    [ "${fails}" -eq 0 ]
}

case "${1:-}" in
    --update)  update_readme ;;
    --test)    run_tests ;;
    --help|-h) sed -n '1,/^$/p' "$0" | sed 's/^# \?//'; exit 0 ;;
    "")        generate_table ;;
    *)         echo "unknown flag: $1" >&2; exit 1 ;;
esac
