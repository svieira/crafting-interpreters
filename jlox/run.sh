#!/opt/homebrew/bin/bash
./gradlew jar
rlwrap -H jlox-repl-history java -jar build/libs/jlox-1.0-SNAPSHOT.jar
