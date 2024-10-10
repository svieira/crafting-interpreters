#!/opt/homebrew/bin/bash
./gradlew jar
java -jar build/libs/jlox-1.0-SNAPSHOT.jar
