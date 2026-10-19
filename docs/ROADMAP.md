# Open items and evidence

Updated 19 October 2026. `content/coverage.json` maps 47 requirements to drafts and explicitly lists gaps. This is a development preview, not a complete or professionally reviewed survival reference.

## Implemented in the 0.2 preview

- 30 guides per language (up from 15), including dedicated adult/child choking, child/infant CPR, bleeding/wounds, burns, anaphylaxis, seizures, diabetes, poisoning/CO, stroke/heart attack, disability/medical equipment, pets, water-advisory distinctions, combined hazards and recovery.
- Help-now/first-aid/preparation/all categories, related offline guides, reader text controls and one-edit search fallback. Household access is in the persistent toolbar.
- Explicit household export through Android's file picker; confirmation describes unencrypted files and cloud-provider behaviour. Clearing the plan preserves language/text settings and does not claim to delete exported files.
- Per-article provenance/review dates, revision history, a topic matrix and a public-content gate. Review records require identity/scope/evidence and a hash of the exact bilingual content; no real review is claimed.
- HTML contents/related links, keyword search and print source expansion; Android 16/API 36 compile/target plus system-bar/cutout/keyboard insets.
- Content negative tests and Android findability/search/persistence/export-snapshot tests; CI targets APIs 23/36 offline. CI packages APK signature, manifest and runtime dependency tree with release checksums.

Validation results are attached to the actual workflow/release. A feature listed here is not a claim that every device, aid procedure or store requirement has been approved.

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
- [ ] Test declared minimum Android 5/API 21 and more OEMs. CI API 23/36 is useful but does not establish all supported versions.
- [ ] Test low storage, corrupt bundled data and failure paths; introduce an independently readable fallback if the main content file is damaged.
- [ ] Improve HTML/Android search parity (HTML lacks Android's fuzzy fallback); test the HTML interactively and without JavaScript on supported browsers.
- [ ] Produce and visually inspect a printable PDF from the same content; printed quick cards for loss of phone power.
- [ ] Optional encrypted/structured plan backup and restore. Plain-text export is implemented; restore is not. No cloud account should become necessary.

## P1 — Distribution and maintenance

- [ ] Complete transitive licence/security audit using the CI dependency inventory. The inventory is evidence of dependencies, not a completed audit.
- [ ] Protected production signing with backup/key-continuity plan. Cached development signing remains for previews; losing the cache can require reinstall and lose local notes.
- [ ] Generate production AAB, privacy/health/data-safety declarations, store screenshots/listing and applicable Play testing. Verify policy/target requirements again at submission. SDK 36 migration alone is not store readiness.
- [ ] Confirm ownership/access for store accounts and upload credentials outside Git. Submit only after review/testing/signing requirements are met.
- [ ] Assign correction triage, review cadence, withdrawal/replacement and source-link maintenance. Optional content updating requires authentication, schema validation, atomic install and a retained known-good edition; it must never lock out offline reading.
- [ ] Reproducibility check before claiming reproducible APKs. Source archive/checksum/signature do not by themselves prove reproducibility.

## Separate workstream

- [ ] iOS implementation and distribution/licence assessment. The Android APK cannot install on an iPhone.

No live alerts, verified open shelters/safe routes, pathogen identification, phone radiation measurement or guaranteed survival are claimed.
