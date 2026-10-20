#!/usr/bin/env bash
set -euo pipefail

adb shell mkdir -p /sdcard/Download
adb shell svc wifi disable
adb shell svc data disable
check_result=0
./gradlew :android:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.ligi.survivalmanual.EmergencyPreviewTest,org.ligi.survivalmanual.HouseholdBackupTest,org.ligi.survivalmanual.HouseholdTransferTest,org.ligi.survivalmanual.RecoveryTest \
  --console=plain || check_result=$?

# Preserve diagnostic screenshots even if a later test fails. These are emulator
# fixtures, not user household data. The shell owns Download; no app storage
# permission is added just to collect test evidence.
mkdir -p android/build/reports/usability
for name in home reader-section large-text-home large-text-reader large-text-home-de large-text-reader-de dark-home landscape backup-preview backup-large-text recovery-unavailable; do
  adb pull "/sdcard/Download/open-emergency-$name.png" "android/build/reports/usability/$name.png" >/dev/null || true
done
adb shell settings put system font_scale 1.0
if [ "$check_result" -ne 0 ]; then
  exit "$check_result"
fi
for name in home reader-section large-text-home large-text-reader large-text-home-de large-text-reader-de dark-home landscape backup-preview backup-large-text recovery-unavailable; do
  cp "android/build/reports/usability/$name.png" "dist/Usability-$name.png"
done
adb install -r android/build/outputs/apk/debug/*.apk
adb shell am force-stop com.hoyleb.openemergency.preview
adb shell am start -W -n com.hoyleb.openemergency.preview/org.ligi.survivalmanual.ui.EmergencyActivity
adb shell uiautomator dump /sdcard/emergency-window.xml
adb pull /sdcard/emergency-window.xml dist/emergency-window.xml
grep -q 'OPEN EMERGENCY' dist/emergency-window.xml
adb exec-out screencap -p > dist/Android-preview.png
