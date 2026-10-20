# Optional encrypted household backups

The 0.5 source adds **Plan → Backup / restore → Save encrypted backup**. Plain JSON backup and readable text export remain available. The APK retains the existing public signing pin; publication remains blocked until the signing identity is correctly provisioned. Do not assume a CI test APK can update a previously installed preview. See [SIGNING.md](SIGNING.md).

## Use and recovery

1. Choose a destination using Android's file picker. A local folder is suitable for offline use; a cloud provider may upload the selected file and expose its name/size under its own policies.
2. Enter and repeat a unique passphrase of 12–128 UTF-16 code units (BMP code points count once; non-BMP code points count twice, and composed emoji may use several units). Spaces, case and Unicode are preserved exactly: nothing is trimmed or normalized. A length limit does not establish password strength; use a long, unpredictable passphrase and keep it separately, for example in a password manager or secure paper record.
3. Choose **Encrypt** and wait for the saved confirmation. The file uses `.oeh`; only ciphertext is written to the selected destination. Cancelling before encryption can leave a file created by the provider empty. Check the result and retain an older verified backup.
4. Use **Restore backup** for either JSON or `.oeh`. An encrypted file first asks for its passphrase; authentication must succeed before parsing household data. The normal preview and explicit **Replace plan** confirmation still follow. Cancelling or a failed read/authentication does not replace the plan.

Wrong passphrases and authentication failures have the same retry message; this does not diagnose whether the passphrase or file is wrong. The app has no password reset, account recovery, escrow or uploaded key. Losing the passphrase can make the file unusable. Keep an accessible household plan on paper too; do not make one encrypted file your only emergency resource.

Passphrases are not put in preferences, bundles, logs, filenames or the ViewModel. The ViewModel retains only pending data/URI or bounded ciphertext across rotation; passphrase fields are cleared on backgrounding, dismissal and rotation, and are excluded from activity-state saving. The password dialog uses `FLAG_SECURE`, password masking, disabled autofill/suggestions and a deliberate Show control. The worker clears the caller's character array and temporary byte/key arrays after processing. Managed runtimes, the OS, keyboard, clipboard and libraries can retain copies: this is not a claim of complete memory erasure or protection on a compromised phone.

Encryption protects this exported file only. The live plan remains in app-private preferences protected by the device/OS; plain JSON/text exports and any decrypted copies are unencrypted. File providers, backups of exported copies and physical access to printed records remain separate concerns.

## Offline desktop recovery

The original Android phone/keystore/account is not required. A compatible computer with Python 3.10+ and the pinned dependency can decrypt the file using the passphrase:

```sh
python3 -m pip install -r tools/backup/requirements-test.txt
python3 tools/backup/decrypt_backup.py household.oeh recovered-household.json
```

Run in Terminal/PowerShell so `getpass` can hide input. The passphrase is never a CLI argument/environment variable, and decrypted notes are not printed to the terminal. Install/cache the dependency before an outage; package installation itself normally needs internet. The actual decrypt operation uses no network.

The helper validates the envelope before prompting, checks authentication and basic household format/version, and preserves the decrypted bytes exactly. Android performs the full field/type/range validation when importing the recovered JSON. The helper refuses to overwrite an existing output or follow a destination symlink; Unix output starts with owner-only mode, while Windows/filesystem ACLs still apply. An interrupted process can leave a partial output, so keep the encrypted original and verify a restore before relying on the new JSON. Python immutable strings/objects cannot be reliably zeroed.

## Version 1 format

All integers are unsigned big endian. The envelope overhead is 60 bytes. No compression, executable content, URL fetching, device key or external content is involved.

| Offset | Bytes | Field |
| --- | --- | --- |
| 0 | 8 | ASCII `OEHENC01`, including format version |
| 8 | 4 | PBKDF2 iteration count, exactly 600,000 |
| 12 | 16 | Random salt |
| 28 | 12 | Random AES-GCM nonce |
| 40 | 4 | Ciphertext length including the 16-byte tag |
| 44 | Variable | AES-256-GCM ciphertext and appended 128-bit tag |

PBKDF2-HMAC-SHA256 derives 32 key bytes from the exact UTF-8 passphrase. Each encryption generates a fresh salt and nonce with `SecureRandom`. AES-GCM authenticates **all 44 header bytes** as associated data before processing the ciphertext. `doFinal` must verify the tag before any plaintext is returned to the app. The payload is the existing versioned UTF-8 household JSON, capped at 512 KiB with its existing notes/bookmark/type/range checks.

The reader requires the fixed iteration count, version, exact length and bounded size before running the KDF. The file cannot request another cipher, weaker parameters, an arbitrary allocation or an excessively large iteration count. No trailing data is accepted. Future formats need an explicit compatible implementation/version; do not weaken validation to guess unknown formats.

Android's `PBKDF2withHmacSHA256` SecretKeyFactory is available from API 26. To keep the same format on APIs 21/23, the app pins Bouncy Castle's Java 1.5–1.8 distribution at **1.86** and uses its lightweight `PKCS5S2ParametersGenerator(SHA256Digest())`. It does not register/select a BC JCE provider; AES-GCM stays with Android's default provider. The dependency increases APK size and the dependency/security audit scope. Its original MIT licence ships in `assets/licenses/bouncycastle.html` and can be read offline from About.

This is password-based encryption, not a guarantee against guessing weak passphrases and not a memory-hard KDF. 600,000 PBKDF2-SHA256 iterations follow the referenced OWASP work-factor guidance, applied here as a design starting point rather than a completed backup-security audit. Actual latency/battery usability on older physical phones remains to be measured.

Primary references checked 20 October 2026:

- [Android Cipher and GCM requirements](https://developer.android.com/reference/javax/crypto/Cipher)
- [Android SecretKeyFactory availability](https://developer.android.com/reference/javax/crypto/SecretKeyFactory)
- [Bouncy Castle Java distribution](https://www.bouncycastle.org/download/bouncy-castle-java/)
- [Bouncy Castle PBKDF2 API](https://downloads.bouncycastle.org/java/docs/bcprov-jdk15to18-javadoc/org/bouncycastle/crypto/generators/PKCS5S2ParametersGenerator.html)
- [OWASP password KDF work factors](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

## Verification and outstanding review

Automated tests cover a public independently generated Python vector with Unicode/emoji, randomized salt/nonce, wrong passphrases, salt/nonce/ciphertext/tag tampering, malformed parameters/version/lengths, truncation/trailing data, bounded reads, weak export rejection and exclusive desktop output. Device tests exercise provider export, mismatched confirmation, wrong-passphrase retry, the replacement preview, rotation clearing, cancellation and 200% text/keyboard reachability. The host independently decrypts an Android-generated **public fixture**, never real household data. Exact results belong to the linked PR/workflow; test configuration alone is not evidence of a pass.

Two empty-form screenshots are rendered by test-only code from the native view hierarchy. `FLAG_SECURE` remains enabled on the actual password window, and these fixtures contain no entered passphrase or real household data. They are layout evidence, not a test that every OEM enforces screenshot protection.

Independent cryptography/security review, password-loss and recovery comprehension testing, physical older-phone performance, real low-storage/provider/process-death sessions and production signing remain open. No professional security certification or content approval is claimed.
