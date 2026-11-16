#!/usr/bin/env bash
# Compiles every examples/*.java against target/classes with the same strict flags as the SDK.
set -euo pipefail

shopt -s nullglob
examples=(examples/*.java)
if [ ${#examples[@]} -eq 0 ]; then
    echo "No examples/*.java found; skipping examples compilation."
    exit 0
fi

out=target/examples-classes
rm -rf "$out"
mkdir -p "$out"
javac --release 17 -Xlint:all -Werror -cp target/classes -d "$out" "${examples[@]}"
echo "Compiled ${#examples[@]} example(s) with -Xlint:all -Werror."
