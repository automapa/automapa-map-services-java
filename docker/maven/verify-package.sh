#!/usr/bin/env bash
# Verifies the jars built by "make package": the binary, -sources.jar and -javadoc.jar must exist
# and must not contain test classes, test sources, test fixtures or local configuration (.env).
# Usage: docker/maven/verify-package.sh [directory with the jars, default: target]
set -euo pipefail

dir="${1:-target}"
failed=0

fail() {
    echo "PACKAGE CHECK: $*" >&2
    failed=1
}

single() {
    local pattern="$1" found=()
    shopt -s nullglob
    # shellcheck disable=SC2206
    found=($pattern)
    shopt -u nullglob
    if [ ${#found[@]} -ne 1 ]; then
        fail "expected exactly one $pattern, found ${#found[@]}; run 'make package' first"
        return 1
    fi
    echo "${found[0]}"
}

main_jar=""
for candidate in "$dir"/map-services-*.jar; do
    case "$candidate" in
        *-sources.jar | *-javadoc.jar) ;;
        *) [ -f "$candidate" ] && main_jar="$main_jar $candidate" ;;
    esac
done
main_jar="${main_jar# }"
if [ -z "$main_jar" ] || [ "$main_jar" != "${main_jar%% *}" ]; then
    fail "expected exactly one binary jar $dir/map-services-<version>.jar, found: '${main_jar}'; run 'make package' first"
    exit 1
fi
sources_jar="$(single "$dir/map-services-*-sources.jar")" || exit 1
javadoc_jar="$(single "$dir/map-services-*-javadoc.jar")" || exit 1

# Entries that belong to the test scope: classes and sources compiled from src/test/java.
test_classes="$(cd src/test/java && find . -name '*.java' | sed -e 's|^\./||' -e 's|\.java$||')"

forbidden_common() {
    local jar="$1" entries="$2"
    if grep -E '(^|/)\.env(\.local)?$' <<<"$entries" >/dev/null; then
        fail "$jar contains local configuration (.env)"
    fi
    if grep -E '(^|/)fixtures/' <<<"$entries" >/dev/null; then
        fail "$jar contains test fixtures (fixtures/)"
    fi
}

check_no_test_entries() {
    local jar="$1" entries="$2" suffix="$3" name
    while IFS= read -r name; do
        [ -z "$name" ] && continue
        # Nested classes compile to Name$Inner.class, so they are matched too.
        if grep -E "^${name}([\$][^/]*)?${suffix//./[.]}\$" <<<"$entries" >/dev/null; then
            fail "$jar contains test entry ${name}${suffix}"
        fi
    done <<<"$test_classes"
}

require_entry() {
    local jar="$1" entries="$2" entry="$3"
    if ! grep -Fx "$entry" <<<"$entries" >/dev/null; then
        fail "$jar is missing $entry"
    fi
}

main_entries="$(jar tf "$main_jar")"
forbidden_common "$main_jar" "$main_entries"
check_no_test_entries "$main_jar" "$main_entries" ".class"
require_entry "$main_jar" "$main_entries" "pl/automapa/mapservices/ApiClient.class"
require_entry "$main_jar" "$main_entries" "pl/automapa/mapservices/sdk.properties"
manifest_dir="$(mktemp -d)"
main_jar_path="$(cd "$(dirname "$main_jar")" && pwd)/$(basename "$main_jar")"
(cd "$manifest_dir" && jar xf "$main_jar_path" META-INF/MANIFEST.MF)
if ! grep -q '^Automatic-Module-Name: pl.automapa.mapservices' "$manifest_dir/META-INF/MANIFEST.MF"; then
    fail "$main_jar has no Automatic-Module-Name: pl.automapa.mapservices in its manifest"
fi
rm -rf "$manifest_dir"

sources_entries="$(jar tf "$sources_jar")"
forbidden_common "$sources_jar" "$sources_entries"
check_no_test_entries "$sources_jar" "$sources_entries" ".java"
require_entry "$sources_jar" "$sources_entries" "pl/automapa/mapservices/ApiClient.java"

javadoc_entries="$(jar tf "$javadoc_jar")"
forbidden_common "$javadoc_jar" "$javadoc_entries"
require_entry "$javadoc_jar" "$javadoc_entries" "index.html"
# With Automatic-Module-Name the pages live under a module directory (pl.automapa.mapservices/...).
if ! grep -E '(^|/)pl/automapa/mapservices/ApiClient[.]html$' <<<"$javadoc_entries" >/dev/null; then
    fail "$javadoc_jar is missing the ApiClient page (pl/automapa/mapservices/ApiClient.html)"
fi

if [ "$failed" -ne 0 ]; then
    echo "PACKAGE CHECK FAILED" >&2
    exit 1
fi
echo "PACKAGE CHECK PASSED: $main_jar, $sources_jar, $javadoc_jar"
