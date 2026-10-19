# Open Emergency Handbook

Free, open-source, downloadable Android emergency handbook. Fork of [ligi/SurvivalManual](https://github.com/ligi/SurvivalManual), preserving GPLv3 and upstream history.

**Development preview: all summaries/translations are unreviewed and coverage is incomplete.** See the in-app About guide and [build specification](docs/BUILD_SPEC.md).

## Install and test

Successful builds appear in [GitHub Releases](https://github.com/hoyleb/OpenEmergencyHandbook/releases). Download `OpenEmergencyHandbook-preview.apk` on an Android phone (Android 5.0+). Allow installation from the browser/file manager when Android asks. Open **Open Emergency Preview**. This is debug-signed for testing, not a Play Store production release.

Separate package: `com.hoyleb.openemergency.preview`. No account, subscription, advertising or network/location permission. All 15 English and German guides ship in the APK. External source links require internet through your browser.

Test airplane mode with Wi-Fi off and restart the app. Check search in both languages, language switching while reading, large text, and saving/reopening the household plan. Keep paper emergency contacts too.

Preview builds normally reuse a cached development signing key. If that cache is lost, uninstall/reinstall may be needed, removing local notes. Production signing is still outstanding.

## Included

- All six disaster scenarios, first-aid overview, adult CPR/AED, safe water, food, sanitation, shelter, preparedness and communications.
- Native dashboard/reader, bilingual offline search, local checklist/notes and water estimate with assumptions.
- Source dates and explicit draft status; offline HTML, source archive, coverage report and checksums in successful releases.

No live alerts, radiation measurement, verified shelter directory or personalised diagnosis. Dedicated child/infant medical protocols, diagrams, extended content and professional/translation review remain outstanding. The inherited wiki is excluded from preview assets pending its own review.

## Build

Requires JDK 17 and Android SDK. Initial preview targets SDK 34; update tooling/target SDK to current requirements before Google Play submission.

```sh
git clone https://github.com/hoyleb/OpenEmergencyHandbook.git
cd OpenEmergencyHandbook
git switch feature/offline-emergency-preview
python3 tools/build_handbook.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :android:assembleDebug :android:lintDebug
```

Canonical content: `content/handbook.json`. Generated assets: `android/src/main/offlineAssets/handbook/articles.json`. The build script uses Python's standard library. Android smoke tests: `EmergencyPreviewTest.kt`.

## Contribute and licence

Feedback: [pull request #1](https://github.com/hoyleb/OpenEmergencyHandbook/pull/1). See [roadmap](docs/ROADMAP.md). Do not post private medical details, contacts or credentials. Preserve sources and real review status.

Application and newly authored summaries/translations use GPLv3 under [COPYING](COPYING). Third-party libraries retain their licences. Linked publications retain their own rights. No agency endorsement, logos or copied illustrations are claimed. Preserve upstream attribution/history.
