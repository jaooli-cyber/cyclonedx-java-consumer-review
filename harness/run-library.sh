#!/bin/sh
set -eu
version=${1:?usage: run-library.sh VERSION DEPENDENCY_DIRECTORY OUTPUT_DIRECTORY}
deps=${2:?dependency directory required}
output=${3:?output directory required}
case "$version" in 13.2.0|13.3.0) ;; *) exit 2 ;; esac
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$output/classes"
javac -cp "$deps/*" -d "$output/classes" "$root/harness/EnumRoundTripCheck.java"
java -Xmx1536m -cp "$output/classes:$deps/*" EnumRoundTripCheck "$version" "$root" "$output/$version"
