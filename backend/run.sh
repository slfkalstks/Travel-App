#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if [[ ! -f build/libs/yeohaeng-demo-api-0.1.0.jar ]]; then
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${TMPDIR:-/tmp}/yeohaeng-gradle-cache}"
  ./gradlew --no-daemon bootJar
fi
exec "$java_bin" -jar build/libs/yeohaeng-demo-api-0.1.0.jar "$@"
