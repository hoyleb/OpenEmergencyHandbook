# Household backup and offline recovery

## Save and restore a plan

Open **Plan → Backup / restore → Save JSON backup**. The confirmation explains that the file is unencrypted and a cloud file provider may upload it. Choose a local folder to stay offline. Keep a private second copy before uninstalling, clearing app storage or replacing a preview with a different signing key.

The versioned JSON contains the eight checklist states, people/days, notes and saved-guide IDs. It excludes language, reader settings and reading history. Invalid unfinished water-estimate inputs are backed up as blank; the notes remain. The plain-text export is still useful for printing but cannot be restored automatically.

Use **Restore JSON backup** to choose a file. Review the checklist count, water-estimate inputs, notes preview and saved-guide count, then explicitly replace the current plan and saved guides. Cancel leaves the current data alone. Restoring replaces those fields; it does not merge notes or change unrelated preferences. Retired guide IDs are ignored when a working catalog is available; if the catalog is unavailable, syntactically valid IDs are retained for later recovery.

The reader accepts UTF-8 files up to 512 KiB, notes up to 65,536 characters, the supported schema version, expected fields and strict field types. It rejects malformed, oversized or unsupported files before changing preferences. These are data files: no scripts, URL fetching or file paths are interpreted.

File-provider I/O runs off the UI thread. A ViewModel retains a pending operation/confirmation across rotation. If Android kills the process while a file picker is open, save again; the selected file may be empty and the app reports interruption. An in-progress restore preview is not a durable backup. Keep the original file and verify a restored plan before deleting a prior copy.

If a preference commit fails, the restore reports failure and restores the previous in-memory values. Keep the backup and free storage before retrying. A failed output may leave an incomplete file; do not treat it as a valid backup. Automated tests simulate a failed commit; actual full-disk, device-reboot and OEM/provider behaviour still need hands-on testing.

## Readable guidance after content damage

The main handbook is checked against its generated digest where the recovery index is available. If it cannot be loaded, the app reads independent per-guide copies and checks each against the index's SHA-256 digest. Damaged/missing copies are omitted, remaining internal links are repaired, and a recovery notice states the counts. No missing instructions are fabricated.

If both the main catalog and recovery copies/index are unavailable, the app shows a clear failure page while leaving the household plan, backups and dialler control reachable. Back up before reinstalling. The standalone HTML and printable PDFs are separate reading options; save them on another device or print them before an outage.

These copies detect accidental corruption and reduce dependence on one JSON file. They are not authenticated remote content updates, an independent clinical source, or protection against an adversary who can replace the entire APK and its signature.

## Evidence

The instrumentation suite exercises UTF-8 round trips, strict schema/type/range/size rejection, retired guides, simulated commit failure, file-picker contracts, confirmation/cancellation, rotation during confirmation, corrupt primary/individual/all content, and malformed preference types. The current run's exact test results are published in `TEST-SUMMARY-api-*.json`.

The 0.5 source adds optional [passphrase-encrypted portable backups and offline desktop recovery](ENCRYPTED_BACKUPS.md). Independent security review, interrupted-process/reboot stress testing and actual low-storage/provider fault sessions remain open. Android cloud/device-transfer backup is disabled in the manifest and extraction rules; explicit exports remain under the chosen file provider's policies.
