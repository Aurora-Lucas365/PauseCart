#!/usr/bin/env bash
set -eu
mkdir -p preview
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent 224
adb shell wm dismiss-keyguard
adb shell am start -W -n io.github.auroralucas365.pausecart/.MainActivity
sleep 3
adb shell screencap -p /sdcard/Download/pausecart-home.png
adb pull /sdcard/Download/pausecart-home.png preview/home.png
adb shell am start -W -a android.intent.action.SEND -t text/plain -n io.github.auroralucas365.pausecart/.MainActivity --es android.intent.extra.TEXT Demo_item_https://example.com/item
sleep 2
adb shell screencap -p /sdcard/Download/pausecart-share.png
adb pull /sdcard/Download/pausecart-share.png preview/share.png
