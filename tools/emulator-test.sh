#!/usr/bin/env bash
set +e
./gradlew connectedDebugAndroidTest --stacktrace
result=$?
adb pull /data/local/tmp/pausecart-screenshots test-screenshots
adb logcat -d -s PauseCartTest:D AndroidRuntime:E > test-service-log.txt
exit "$result"
