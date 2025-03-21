#!/opt/homebrew/bin/bash

# Preserve STDOUT via HACK
./gradlew jar &
wait

if [[ -t 0 && $# == 0 ]]; then
  rlwrap -pyellow -rH jlox-repl-history java -jar build/libs/jlox-1.0-SNAPSHOT.jar
else
  fileName=${1:--}; shift;
  java -jar build/libs/jlox-1.0-SNAPSHOT.jar "$fileName" "$@"
fi
