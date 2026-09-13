#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="se.familjekalender.app"
ACTIVITY=".DesignPreviewActivity"

adb install -r "$APK"

# The production app legitimately asks for notification/location permissions.
# In the headless preview emulator those Android system dialogs would cover the
# activity and turn the visual regression screenshot into a false failure.
# Pre-grant only inside CI so the preview validates our UI, not PermissionController.
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION || true

adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82 || true
adb shell am force-stop "$PACKAGE"
adb logcat -c
adb shell am start -W -n "$PACKAGE/$ACTIVITY" --es screen calendar

stable=0
for _ in $(seq 1 30); do
  resumed="$(adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' || true)"
  echo "$resumed"
  if echo "$resumed" | grep -q "$PACKAGE/$ACTIVITY"; then
    stable=$((stable + 1))
  else
    stable=0
  fi
  if [ "$stable" -ge 5 ]; then
    break
  fi
  sleep 1
done

if [ "$stable" -lt 5 ]; then
  echo "DesignPreviewActivity did not remain resumed long enough" >&2
  adb shell dumpsys activity activities | tail -n 120 || true
  exit 1
fi

sleep 4
adb exec-out screencap -p > phase1-calendar.png
test -s phase1-calendar.png

python3 scripts/validate-preview-png.py phase1-calendar.png

adb logcat -d -v threadtime > phase1-logcat.txt || true
if grep -q "FATAL EXCEPTION.*$PACKAGE" phase1-logcat.txt; then
  echo "Fatal exception found in app logcat" >&2
  tail -n 200 phase1-logcat.txt >&2 || true
  exit 1
fi
