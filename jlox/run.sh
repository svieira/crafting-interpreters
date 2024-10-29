#!/opt/homebrew/bin/bash

# Preserve STDOUT via HACK
./gradlew jar &
wait

if [[ -t 0 && $# == 0 ]]; then
  rlwrap -H jlox-repl-history java -jar build/libs/jlox-1.0-SNAPSHOT.jar
else
  java -jar build/libs/jlox-1.0-SNAPSHOT.jar ${1:--}
fi
