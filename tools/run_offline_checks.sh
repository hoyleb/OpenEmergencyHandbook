#!/usr/bin/env bash
set -euo pipefail

adb shell mkdir -p /sdcard/Download
adb shell svc wifi disable
adb shell svc data disable
# Collect atomic app-private captures while tests are running; AGP removes the
# fixture APK/data on completion. This works on API 21 without adding storage
# permissions to the distributed app or depending on UiAutomation shell UIDs.
mkdir -p android/build/reports/usability
names="home reader-section large-text-home large-text-reader large-text-home-de large-text-reader-de dark-home landscape backup-preview backup-large-text recovery-unavailable encrypted-backup-large-text encrypted-backup-prompt"
collect_evidence() {
  while true; do
    for name in $names; do
      target="android/build/reports/usability/$name.png"
      if [ ! -s "$target" ]; then
        if adb exec-out run-as com.hoyleb.openemergency.preview cat "cache/usability/$name.png" > "$target.tmp" 2>/dev/null && [ "$(od -An -tx1 -N8 "$target.tmp" | tr -d ' \n')" = 89504e470d0a1a0a ]; then
          mv "$target.tmp" "$target"
        else
          rm -f "$target.tmp"
        fi
      fi
    done
    if [ ! -s android/build/reports/usability/encrypted-interop.oeh ]; then
      candidate=android/build/reports/usability/encrypted-interop.oeh
      if adb exec-out run-as com.hoyleb.openemergency.preview cat cache/encrypted-interop.oeh > "$candidate.tmp" 2>/dev/null && [ "$(od -An -tx1 -N8 "$candidate.tmp" | tr -d ' \n')" = 4f4548454e433031 ]; then
        mv "$candidate.tmp" "$candidate"
      else
        rm -f "$candidate.tmp"
      fi
    fi
    sleep 0.2
  done
}
collect_evidence &
evidence_collector_pid=$!
trap 'kill "$evidence_collector_pid" 2>/dev/null || true' EXIT
check_result=0
./gradlew :android:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.ligi.survivalmanual.EmergencyPreviewTest,org.ligi.survivalmanual.HouseholdBackupTest,org.ligi.survivalmanual.HouseholdTransferTest,org.ligi.survivalmanual.RecoveryTest,org.ligi.survivalmanual.EncryptedBackupTest \
  --console=plain || check_result=$?
kill "$evidence_collector_pid" 2>/dev/null || true
wait "$evidence_collector_pid" 2>/dev/null || true
trap - EXIT
adb shell settings put system font_scale 1.0
if [ "$check_result" -ne 0 ]; then
  exit "$check_result"
fi
python3 tools/backup/verify_android_backup.py android/build/reports/usability/encrypted-interop.oeh
for name in $names; do
  cp "android/build/reports/usability/$name.png" "dist/Usability-$name.png"
done
adb install -r android/build/outputs/apk/debug/*.apk
adb shell am force-stop com.hoyleb.openemergency.preview
adb shell am start -W -n com.hoyleb.openemergency.preview/org.ligi.survivalmanual.ui.EmergencyActivity
adb shell uiautomator dump /data/local/tmp/emergency-window.xml
adb pull /data/local/tmp/emergency-window.xml dist/emergency-window.xml
grep -q 'OPEN EMERGENCY' dist/emergency-window.xml
adb exec-out screencap -p > dist/Android-preview.png
