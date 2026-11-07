#!/usr/bin/env sh
set -eu

live_only=false
if [ "${1:-}" = "--live-only" ]; then
  live_only=true
  shift
fi

if [ "$#" -ne 2 ]; then
  echo "usage: $0 [--live-only] <user-id> <avatar-file>" >&2
  exit 2
fi

if [ "$live_only" = false ]; then
  mkdir -p target/classes
  javac -d target/classes $(find src/main/java -name '*.java')
elif [ ! -f target/classes/cc/infrai/property/PropertyAvatarCommand.class ]; then
  echo "compiled classes not found; run sh scripts/run-example.sh without --live-only first" >&2
  exit 2
fi

java -cp target/classes cc.infrai.property.PropertyAvatarCommand "$1" "$2"
