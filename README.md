# Open Emergency Handbook

Free, open-source, downloadable Android emergency handbook. Fork of [ligi/SurvivalManual](https://github.com/ligi/SurvivalManual), preserving GPLv3 and upstream history.

**Development preview: all summaries/translations are unreviewed and coverage is incomplete.** See the in-app About guide and [build specification](docs/BUILD_SPEC.md).

## Install and test

Successful builds appear in [GitHub Releases](https://github.com/hoyleb/OpenEmergencyHandbook/releases). Download `OpenEmergencyHandbook-preview.apk` on an Android phone (Android 5.0+). Allow installation from the browser/file manager when Android asks. Open **Open Emergency Preview**. This is debug-signed for testing, not a Play Store production release.

Separate package: `com.hoyleb.openemergency.preview`. No account, subscription, advertising or network/location permission. All 30 English and German guides ship in the APK. External source links require internet through your browser.

Test airplane mode with Wi-Fi off and restart the app. Check search in both languages, language switching while reading, large text, and saving/reopening the household plan. Keep paper emergency contacts too.

The 0.4 series uses an explicit development keystore and pinned-certificate publication guard. Older previews used changing keys and may require uninstalling; export the old plan first. JSON backups from 0.4 can be restored; older text exports need manual copying. Production/protected signing remains outstanding. See [signing and key continuity](docs/SIGNING.md) and [backup/recovery](docs/BACKUP_AND_RECOVERY.md).

## Usability and resilience in the 0.4 preview

Urgent actions are on the home screen; Help, Search, Saved and Plan stay available. Back restores the previous guide or search position. Search ranks bilingual terms and labels typo suggestions. Reader sections support jumping; More holds bookmarks, text size and an explicit screen-on option. Bookmarks and recent reading stay local. Plan adds versioned JSON backup and confirmed restore; independent guide copies recover from a damaged main catalog. The HTML edition now has matching bilingual/typo search and works as an unfiltered handbook without JavaScript. See [usability goals and evidence](docs/USABILITY.md).

## Included

The 0.5 source also adds optional passphrase-encrypted household backups, confirmed restore and an offline desktop recovery helper. Plain JSON/text exports remain available. See [encrypted backup and password-loss limits](docs/ENCRYPTED_BACKUPS.md). Independent security review and physical-phone testing remain open; publication still requires the pinned signing identity to be provisioned correctly.

- All six disaster scenarios, first-aid overview, adult CPR/AED, safe water, food, sanitation, shelter, preparedness and communications.
- Native category navigation/reader with adjustable text and related guides, bilingual offline search with one-edit fallback, local checklist/notes, local text export, structured backup/restore and water estimate with assumptions.
- Source dates and explicit draft status; offline HTML, printable English/German PDFs, source archive, coverage report and checksums in successful releases.

No live alerts, radiation measurement, verified shelter directory or personalised diagnosis. Dedicated child/infant CPR, choking and other first-aid drafts are included, alongside accessibility, pets, combined-hazard and recovery pages. Reviewed diagrams, further detailed protocols and professional/translation review remain outstanding. The inherited wiki is excluded from preview assets pending its own review.

## Build

Requires JDK 17 and Android SDK. The app compiles/targets SDK 36, supports system-bar/display-cutout/keyboard insets, and retains Android 5.0 as its declared minimum. CI exercises Android 5, 6 and 16; physical-device/minimum-OS evidence and store requirements remain separate open items.

```sh
git clone https://github.com/hoyleb/OpenEmergencyHandbook.git
cd OpenEmergencyHandbook
python3 tools/build_handbook.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :android:assembleDebug :android:lintDebug
```

Canonical content: `content/handbook.json`. Generated assets: `android/src/main/offlineAssets/handbook/articles.json`. The build script uses Python's standard library. Android instrumentation tests: `EmergencyPreviewTest.kt`.

Coverage mapping: `content/coverage.json`. Run `python3 tools/build_handbook.py --public-release` to exercise the content-release gate; this intentionally fails while requirements remain missing, partial or unreviewed. Review records must identify a reviewer, scope and evidence and match the exact content hash. Passing this gate alone would not establish licensing, clinical accuracy, accessibility or store approval. See [review process](docs/CONTENT_REVIEW.md).

## Contribute and licence

Feedback: [repository pull requests](https://github.com/hoyleb/OpenEmergencyHandbook/pulls). See [roadmap](docs/ROADMAP.md). Do not post private medical details, contacts or credentials. Preserve sources and real review status.

Application and newly authored summaries/translations use GPLv3 under [COPYING](COPYING). Third-party libraries retain their licences. Linked publications retain their own rights. No agency endorsement, logos or copied illustrations are claimed. Preserve upstream attribution/history.

## Printable editions

`python3 -m pip install -r tools/requirements-pdf.txt` and install `fonts-dejavu-core`, then run `python3 tools/build_pdf.py`. This creates one English and one German PDF in `dist/` from the same canonical source. The current editions contain 33 pages each, clickable contents, bookmarks, related guides and source URLs. They carry the same unreviewed-preview status as the app.

Portable checks: `node tools/test_offline_search.cjs`. Browser checks: install Playwright 1.62.1 and Chromium/Firefox/WebKit, then run `node tools/test_offline_browser.cjs` (with its module path available). CI publishes exact API test summaries, HTML checks and screenshots, APK signature/permissions, and an unsigned AAB for packaging review. The AAB is not store-ready.

## Paper quick cards and reviewer handoff

Download the [English quick cards](docs/packs/OpenEmergencyHandbook-quick-cards-en.pdf) or [German quick cards](docs/packs/OpenEmergencyHandbook-quick-cards-de.pdf): eight A4 landscape sheets per language, cut into fourteen full-guide A5 cards and two blank household/support cards. Print single-sided at 100% / actual size. All guide text retains its unreviewed status; this selection is not the complete handbook.

The [complete ZIP download](docs/packs/OpenEmergencyHandbook-paper-review-pack.zip) and [paper/reviewer instructions](docs/PAPER_AND_REVIEW_PACKS.md) include a standalone bilingual HTML review copy, exact-block source-mapping worksheet, source-maintenance inventory and hash-bound blank sign-off templates. These tools support real review; no professional approval or verified claim mapping is inferred. Generated files and hashes are checked by a separate workflow without Android signing credentials.
