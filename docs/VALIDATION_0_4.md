# Verified 0.4 resilience preview

Verified 20 October 2026. This records engineering evidence for the development preview. It does not establish clinical accuracy, professional content approval or high usability with actual users.

- Tested code commit: `2a7e942fc5f89e3abeeb0409ead351012d2e4036`.
- Passing workflow: [39387989175](https://github.com/hoyleb/OpenEmergencyHandbook/actions/runs/39387989175).
- Published download set: [preview-0.4.51](https://github.com/hoyleb/OpenEmergencyHandbook/releases/tag/preview-0.4.51).
- Documentation-only follow-up commits do not change these artifacts. The workflow ignores documentation-only changes; evidence refers to the tested code commit above.

## Automated results

| Check | Result |
| --- | --- |
| Content/tool Python tests | 17 passed |
| Shared bilingual/typo search vectors | 12 cases passed in JavaScript and Android; category checks also passed |
| Android 5 / API 21 | 35 passed; 0 failures, errors or skips |
| Android 6 / API 23 | 35 passed; 0 failures, errors or skips |
| Android 16 / API 36 | 35 passed; 0 failures, errors or skips |
| Chromium, Firefox, WebKit | All passed with and without JavaScript; 200% CSS text; 0 external requests or page errors |
| APK signature/package/permissions gate | Passed; pinned certificate and expected preview application ID |
| Publication guard | Passed; only publishes after the complete matrix and portable checks |

Native tests ran with Wi-Fi/mobile data disabled. The downloaded JUnit XML and released `TEST-SUMMARY-api-21.json`, `TEST-SUMMARY-api-23.json` and `TEST-SUMMARY-api-36.json` were checked against the counts above. Browser evidence is in `HTML-TESTS.json` and nine browser screenshots.

API 36 uses the system font setting for 200% text. APIs 21/23 use an in-process resource fixture with effective font-scale and scaled-density assertions; these do not establish legacy OEM settings integration. Native assertions cover backup validation, rollback, confirmation/rotation, retained plan access after content failure, ranking, navigation, roles, landscape, keyboard dismissal and reading preferences.

## Download and source integrity

All 38 published asset names, sizes and GitHub SHA-256 digests matched the downloaded CI artifact union. The complete `SHA256SUMS.txt` contains 37 matching entries, including the unsigned AAB. Artifact ZIP hashes were also checked before extraction.

APK: `OpenEmergencyHandbook-preview.apk`, 6,655,162 bytes, package `com.hoyleb.openemergency.preview`, version code 6, version `0.4.0-preview-debug`, minimum API 21, target/compile API 36.

```
APK SHA-256:
3ad21ed885705ba41dd3647591e1509d768833b901f223d3d74ac67035f94a4c

Preview certificate SHA-256:
52a38196f1d45547cd0ad223bfdfa3346d0d8a48b2774d6120ea78632061956f
```

The APK certificate was independently read from its signing certificate and matched the public pin and CI signature evidence. Its manifest requests only its own AndroidX signature-level receiver permission, with no internet/location permissions.

The bundled catalog matched `content/handbook.json` byte for byte. All 30 independent recovery copies matched their index hashes and canonical article objects; the index primary hash matched the catalog. All 182 files in the released source archive matched the workspace at the tested code commit. No private keystore was present in that archive.

The unsigned AAB opened as a valid ZIP with its bundle configuration, base DEX and matching canonical handbook; no JAR signing certificate was present. It is packaging evidence, not a production/store-ready build. APK reproducibility has not been established.

## Visual evidence

All 54 downloaded PNGs decoded successfully: native run screenshots for the three APIs, API 36 copies in the release and nine portable screenshots. Visual inspection included restore confirmation at normal/200% text on APIs 21 and 36, the total-content-failure page, landscape and large-text portable urgent actions. The restore actions remained visible outside the scrolling text, and the previous failure feedback no longer obscured the new confirmation.

Native evidence includes home, section navigation, large-text English/German home and reader, dark mode, landscape, normal/large-text backup confirmation and content-unavailable recovery. This is inspection of specific captured states, not a completed accessibility audit or human usability study.

## Remaining release blockers and next work

- Qualified medical/first-aid and other specialist reviews; bilingual comprehension review, missing protocols, source/rights mapping and reviewed diagrams. There are still 30 draft guides per language and no professional reviews.
- Physical Xiaomi and additional OEM/device testing, TalkBack/switch access and timed unfamiliar-user sessions using [USABILITY_TEST_PLAN.md](USABILITY_TEST_PLAN.md). Real low-storage, interrupted-process and file-provider fault sessions remain open.
- Protected production signing and key backup, dependency/licence/security review, store declarations/listing/testing and ownership of upload credentials. See [SIGNING.md](SIGNING.md) before merging or distributing further builds: branch-scoped development-key caches are not durable key custody.
- Optional encrypted portable backups, authenticated/atomic content updating, compact printed quick cards and an iOS workstream remain engineering roadmap items.

The 0.4 preview starts a new development signing identity. Before uninstalling an older preview, export and verify the household plan: uninstall removes local data. Earlier text exports need manual copying; 0.4 JSON backups support restore. The AAB is unsigned, and no store submission or public content approval is claimed.
