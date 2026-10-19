#!/usr/bin/env bash
set +e
./gradlew connectedDebugAndroidTest --stacktrace
result=$?
adb pull /sdcard/Android/data/io.github.auroralucas365.pausecart/files/screenshots test-screenshots
adb logcat -d -s PauseCartTest:D AndroidRuntime:E > test-service-log.txt
exit "$result"
