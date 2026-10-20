# Open items and evidence

Updated 20 October 2026. `content/coverage.json` maps 47 requirements to drafts and explicitly lists gaps. This is a development preview, not a complete or professionally reviewed survival reference.

## Implemented in the 0.2 preview

- 30 guides per language (up from 15), including dedicated adult/child choking, child/infant CPR, bleeding/wounds, burns, anaphylaxis, seizures, diabetes, poisoning/CO, stroke/heart attack, disability/medical equipment, pets, water-advisory distinctions, combined hazards and recovery.
- Help-now/first-aid/preparation/all categories, related offline guides, reader text controls and one-edit search fallback. Household access is in the persistent toolbar.
- Explicit household export through Android's file picker; confirmation describes unencrypted files and cloud-provider behaviour. Clearing the plan preserves language/text settings and does not claim to delete exported files.
- Per-article provenance/review dates, revision history, a topic matrix and a public-content gate. Review records require identity/scope/evidence and a hash of the exact bilingual content; no real review is claimed.
- HTML contents/related links, keyword search and print source expansion; Android 16/API 36 compile/target plus system-bar/cutout/keyboard insets.
- Content negative tests and Android findability/search/persistence/export-snapshot tests; CI targets APIs 23/36 offline. CI packages APK signature, manifest and runtime dependency tree with release checksums.

Validation results are attached to the actual workflow/release. A feature listed here is not a claim that every device, aid procedure or store requirement has been approved.

## Implemented in the 0.3 usability preview

- Direct urgent home actions and persistent Help/Search/Saved/Plan navigation, without a horizontally hidden category control.
- Back stack with query/filter/position restoration, including related guides and activity recreation.
- Ranked bilingual search with explicitly labelled approximate matches and category reset for empty results.
- Bookmarks, six recently opened guides, reader section jumps and More menu for text size and opt-in screen-on.
- Accessible headings/card roles and responsive layouts for larger system fonts. CI includes 200% font/dark-mode scenarios and screenshots.
- See [USABILITY.md](USABILITY.md) for exact flows, automated coverage and the user/device/TalkBack evidence still needed.

## Implemented in the 0.4 resilience preview

- Versioned JSON backup/confirmed restore of household fields and bookmarks, bounded validation, cancellation and failed-commit rollback. File I/O runs off the UI thread; confirmation survives rotation. Plain-text export remains separate. See [BACKUP_AND_RECOVERY.md](BACKUP_AND_RECOVERY.md).
- Main-catalog recovery through independently stored/hash-checked guide files, partial recovery with repaired links, and a total-failure page that retains access to the plan/backup. Malformed preference types/history no longer block reading.
- HTML/Android ranked bilingual search parity, including foreign-language title priority and labelled one-edit suggestions; shared acceptance vectors. Portable browser checks cover filters, anchors, 200% CSS text, no external requests and reading all 60 guides with JavaScript disabled.
- CI matrix expanded to APIs 21/23/36; keyboard dismissal, landscape, card/heading roles, source expanded/collapsed state, backup and corruption tests. Exact run results are in `TEST-SUMMARY-api-*.json` and `HTML-TESTS.json`; configuration is not by itself a passing result.
- Explicit preview keystore path, one preparation job, matrix certificate checks and a pinned-certificate publication guard. A missing/different identity blocks release; protected owner key provisioning remains open. See [SIGNING.md](SIGNING.md).
- Unsigned AAB packaging for review; Android cloud/device-transfer extraction exclusions. Neither establishes store/content approval.
- Repeatable [hands-on test plan](USABILITY_TEST_PLAN.md), including proposed pilot targets and evidence fields. No user sessions are claimed.

## P0 — Content needed before public reliance

- [ ] Qualified medical/first-aid review of every procedure, age distinction and escalation trigger; qualified water, electrical, radiation, structural and infection review. Process: [CONTENT_REVIEW.md](CONTENT_REVIEW.md).
- [ ] German/English translation and lay-reader comprehension review; reconcile competing source editions and jurisdictional differences.
- [ ] Claim/section-level source mapping, stable source-edition details and reuse-rights audit. App licence does not confer rights to every linked publication. Legacy wiki remains excluded.
- [ ] Reviewed diagrams for CPR, choking, bleeding, shelter selection and water workflow, with accessible equivalent instructions. Do not generate medical illustrations without checking anatomy/actions.
- [ ] Dedicated fractures, spinal/head injury, hypothermia/frostbite/heatstroke, drowning, asthma and dehydration/oral-rehydration protocols; infant feeding and immunocompromised water needs.
- [ ] More detailed radiation-incident distinctions, extended shelter logistics, product-specific disinfection, flood cleanup/mould and cyber restore/recovery workflows.
- [ ] Verified regional poison-contact directory and scenario/user tests with actual caregivers and accessibility needs.

## P1 — Resilience and device evidence

- [ ] Test Ben's physical Xiaomi: install/update while retaining notes, flight mode and force-stop/restart, search/navigation, Android large-font/dark mode, export to local storage, export cancellation, saved plan deletion and keyboard behaviour.
- [ ] TalkBack, low vision, landscape, one-handed and timed findability sessions; measure rather than infer accessibility from labels.
- [ ] Minimum Android 5/API 21 is now in the emulator matrix alongside 23/36; attach the current passing run before closing emulator evidence. More OEMs/physical-device evidence remains separate.
- [x] Add independently readable/hash-checked per-guide fallback, partial/total catalog failure handling and corruption fixtures.
- [ ] Complete actual low-storage, interrupted-process/reboot and file-provider fault sessions; current tests simulate selected commit/read failures.
- [x] Add HTML/Android bilingual ranking and typo-fallback parity, category reset, shared vectors and Chromium/Firefox/WebKit JavaScript/no-JavaScript tests.
- [ ] Repeat portable checks on actual Safari and physical Android browsers; Linux WebKit automation is not actual Safari/device evidence.
- [x] Produce and visually inspect printable English/German PDFs from the same content (33 pages each).
- [ ] Produce compact printed quick cards for loss of phone power.
- [x] Structured JSON plan/bookmark backup and explicit preview/confirmed restore, preserving unrelated settings.
- [ ] Optional encrypted portable backups and a security review of their key/password recovery design. Current text/JSON exports are unencrypted; no account is needed.

## P1 — Distribution and maintenance

- [ ] Complete transitive licence/security audit using the CI dependency inventory. The inventory is evidence of dependencies, not a completed audit.
- [ ] Protected production signing with backup/key-continuity plan. Cached development signing remains for previews; losing the cache can require reinstall and lose local notes.
- [x] Generate an unsigned AAB as packaging evidence.
- [ ] Complete production signing, privacy/health/data-safety declarations, store screenshots/listing and applicable Play testing. Verify policy/target requirements again at submission. SDK 36 migration alone is not store readiness.
- [ ] Confirm ownership/access for store accounts and upload credentials outside Git. Submit only after review/testing/signing requirements are met.
- [ ] Assign correction triage, review cadence, withdrawal/replacement and source-link maintenance. Optional content updating requires authentication, schema validation, atomic install and a retained known-good edition; it must never lock out offline reading.
- [ ] Reproducibility check before claiming reproducible APKs. Source archive/checksum/signature do not by themselves prove reproducibility.

## Separate workstream

- [ ] iOS implementation and distribution/licence assessment. The Android APK cannot install on an iPhone.

No live alerts, verified open shelters/safe routes, pathogen identification, phone radiation measurement or guaranteed survival are claimed.
