# Preview signing and publication guard

The early previews had a real continuity defect: the CI cache path did not contain the key used by Gradle. Cache logs reported a path-validation error, and API 23/36 produced different certificates in the same master build. Do not assume those previews can update one another without uninstalling.

The 0.4 workflow explicitly supplies Gradle's keystore path. A single preparation job restores/creates the development key before the matrix starts; workers only restore it and refuse to generate a replacement. Each APK's verified certificate must match the preparation job's public fingerprint. Publication also requires the fingerprint pinned in `config/preview-certificate.sha256`.

A missing cache, a branch change or an unexpected key can therefore stop publication instead of silently shipping a new identity. Test builds can still run with an unpinned development key. The source fingerprint is public; no private key/password is committed or uploaded as a release artifact. Cache-backed debug signing is still not a production key-custody solution.

## Protected owner configuration

For reliable distribution, keep a keystore and recoverable private backup outside Git and configure these GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `OPEN_EMERGENCY_PREVIEW_KEYSTORE_BASE64` | Single-line base64 encoding of the private keystore |
| `OPEN_EMERGENCY_PREVIEW_STORE_PASSWORD` | Keystore password |
| `OPEN_EMERGENCY_PREVIEW_KEY_ALIAS` | Exact signing-key alias |
| `OPEN_EMERGENCY_PREVIEW_KEY_PASSWORD` | Key password |

When these secrets are present on a trusted push/manual run, the workflow loads that key without caching it. Pull-request runs do not receive these signing secrets. The configured key must match the public pin before publication. Validate the workflow on a trusted branch and keep access to signing secrets restricted.

Actions caches are scoped to refs and may expire. After merging a cache-backed branch, a default-branch run may not have that branch's key; the guard will block a different identity. Provision the matching key through protected secrets, or plan a deliberate identity migration with backup/restore and an explicit public pin change. A certificate fingerprint or APK cannot recover its private key. Do not publish a private key in an artifact, log, issue or source commit to work around cache scope.

Observed after merging PR #3: [master run 39399691702](https://github.com/hoyleb/OpenEmergencyHandbook/actions/runs/39399691702) passed the three Android matrix jobs and portable checks but stopped at the pinned-certificate publication guard. The existing `preview-0.4.51` APK remains the verified published identity. Do not interpret that run's failure as a failed Android test or bypass the pin to publish an incompatible update. The separate paper/reviewer workflow needs no signing key.

The 0.4 preview starts a new development identity. Export an old household plan before uninstalling; older text exports require manual copying, while 0.4 JSON backups can be restored. Uninstall deletes local app data. Retain and verify the backup before removing a working installation.

## Production distribution remains separate

The APK is a development build. The downloadable AAB is unsigned packaging evidence, with a separate application ID; it is not a store submission or an approved production release. Production/upload keys, key custody/backup, applicable Play declarations/testing, source/licence/security review and content approval remain required. Check current store requirements at submission rather than treating SDK 36 or an AAB as sufficient.
