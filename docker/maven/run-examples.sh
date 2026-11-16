#!/usr/bin/env bash
# Runs examples/*.java (single-file source launcher) against target/classes and prints a summary.
#
# AUTOMAPA_API_REQUIRED_ACCESS (comma-separated, e.g. "roadpermit"): when non-empty, examples of services
# outside the list are skipped. Empty or unset: every example runs. Examples without a service prefix always run.
# EXAMPLES_DRY_RUN=1: print the RUN/SKIP plan without running anything.
set -uo pipefail

# Prints the service (access name) of an example file, or nothing when the file is not mapped.
# RoadPermit* is checked before Road*.
suite_of() {
    case "$(basename "$1")" in
        RoadPermit*) echo "roadpermit" ;;
        Road*) echo "road" ;;
        Routes*) echo "routes" ;;
        Geocoder* | GoogleGeocodeAdapter*) echo "geocoder" ;;
        Autocomplete*) echo "autocomplete" ;;
        PingPong*) echo "pingpong" ;;
        *) echo "" ;;
    esac
}

required=()
IFS=',' read -r -a parts <<< "${AUTOMAPA_API_REQUIRED_ACCESS:-}"
for part in "${parts[@]}"; do
    part="${part//[[:space:]]/}"
    if [ -n "$part" ]; then
        required+=("$part")
    fi
done

# Prints the suite that excludes the example, or nothing when the example should run.
excluded_suite() {
    local suite
    suite="$(suite_of "$1")"
    if [ ${#required[@]} -eq 0 ] || [ -z "$suite" ]; then
        return
    fi
    local item
    for item in "${required[@]}"; do
        if [ "$item" = "$suite" ]; then
            return
        fi
    done
    echo "$suite"
}

dry_run="${EXAMPLES_DRY_RUN:-0}"

shopt -s nullglob
passed=0
failed=0
skipped=0
planned=0
for example in examples/*.java; do
    suite="$(excluded_suite "$example")"
    if [ "$dry_run" = "1" ]; then
        if [ -n "$suite" ]; then
            echo "SKIP $example ($suite not in AUTOMAPA_API_REQUIRED_ACCESS)"
            skipped=$((skipped + 1))
        else
            echo "RUN $example"
            planned=$((planned + 1))
        fi
        continue
    fi

    echo ">>> $example"
    if [ -n "$suite" ]; then
        echo "SKIPPED ($suite not in AUTOMAPA_API_REQUIRED_ACCESS)"
        skipped=$((skipped + 1))
    elif java -cp target/classes "$example"; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
        echo "FAILED: $example"
    fi
    echo ""
done

if [ "$dry_run" = "1" ]; then
    echo "=== Dry run: $planned to run, $skipped skipped ==="
    exit 0
fi

echo "=== Summary: $passed passed, $failed failed, $skipped skipped ==="
[ "$failed" -eq 0 ]
