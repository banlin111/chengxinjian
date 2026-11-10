#!/bin/sh
set -eu
REPO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# 本机可使用工作区内下载的 JDK；同事已有 JAVA_HOME 时直接使用。
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "$REPO_DIR"/../.tools/jdk-*/Contents/Home "$REPO_DIR"/.tools/jdk-*/Contents/Home; do
    if [ -x "$candidate/bin/java" ]; then JAVA_HOME=$candidate; export JAVA_HOME; break; fi
  done
fi
cd "$REPO_DIR/server"
case "${1:-test}" in
  test) if [ "$#" -gt 0 ]; then shift; fi; exec ./mvnw -B -ntp verify "$@" ;;
  run) shift; exec ./mvnw -B -ntp spring-boot:run "$@" ;;
  *) printf '%s\n' 'Usage: scripts/backend.sh [test|run] [Maven arguments]' >&2; exit 2 ;;
esac
