# Content review and corrections

The 0.2 preview contains 30 English and 30 German **drafts**. Source comparison by a coding assistant is not clinical, technical or translation approval. Source organisations do not endorse these adaptations. All text is authored summary/translation under the repository GPLv3; source pages retain their rights. No third-party diagrams, agency logos or copied source documents are included.

## Review before public reliance

An offline [reviewer handoff pack](PAPER_AND_REVIEW_PACKS.md) now provides full English/German text, exact-block CSV source-mapping fields, source/due-date inventory and blank review records bound to the existing content hashes. Candidate source lists and generated templates are not review evidence. Use them to carry out the process below; do not import blank templates as sign-off.

1. Assign a qualified reviewer appropriate to the topic: first-aid/medical; water/sanitation; electrical/CO; radiation; structural/flood; infection; cyber incident response. Assign a competent German/English translation reviewer separately.
2. Check each action, exception, age group, escalation trigger, quantity and unit against the cited edition. Add section/claim-level source mappings. Check source redirects and conflicting editions; links alone do not establish accuracy or redistribution rights.
3. Review combined hazards explicitly: fallout with a damaged/flooded shelter, infection with unsafe outside air, sewage with water loss, cold with earthquake damage, communications loss with cyberattack.
4. Record actual reviewer identity, qualifications/role, date, exact scope, evidence (such as a review PR) and `content_sha256`. Use `content_digest(article)` in `tools/build_handbook.py`; it hashes both translations and the source list. Put records in `professional_review` and `translation_review`. Both records require `reviewer`, `role`, `date`, `scope`, `evidence` and `content_sha256`. Only then change the article status to `reviewed`.
5. Clear each `remaining` item in `content/coverage.json` only after its scope is actually implemented and reviewed. A mapping only locates draft text; it does not certify adequate coverage. Add approved diagrams with provenance and accessible text, then obtain review of both diagram and instructions.
6. Run `python3 tools/build_handbook.py --public-release`. It must fail today: missing, partial and unreviewed requirements are real blockers. Hash checks invalidate stale sign-off after content/source edits. Automated checks verify record structure, not the truth of credentials or adequacy of a review. Licences, physical-device tests, accessibility, signing and store approval require separate evidence.

## Source differences requiring attention

- Infant CPR/choking: current RCUK 2025 guidance specifies the two-thumb encircling technique. Some NHS/SJA pages still describe two-finger techniques. Drafts use RCUK's current technique; reviewers must confirm suitability for the intended lay audience and German/EU teaching, with an appropriate diagram.
- Emergency calls: UK source numbers are adapted to Germany/EU **112**. Country-specific poison centres are deliberately not guessed. A verified offline directory is outstanding.
- Low blood glucose: products differ in glucose per tablet. The draft uses a person's care plan/product dose rather than an unqualified tablet count. A self-contained, age-appropriate fallback needs medical review.
- Heat-burn and chemical-burn cooling are different protocols. The draft does not apply a 20-minute thermal-burn limit to chemical exposure.
- General pandemic advice does not define a new pathogen or current isolation/treatment rules. Neither a generic water treatment nor a household filter addresses every contaminant.
- Source `checked_at` records retrieval/comparison, not clinical review. The scheduled `review_due` is a maintenance date, not a safe-use expiry or an automatic update.

## Correction process

Report an unsafe/confusing step through a private route where appropriate, or the repository issue form without personal data. Record article ID, edition, language, quoted step and a primary source. Triage urgent medical/water/electrical/radiation issues before ordinary enhancements. Have the correction reviewed, change the canonical content, regenerate assets and ship a new complete preview/release with notes and checksums. Replace withdrawn material in subsequent validated editions; never pretend an offline device received an immediate recall. Do not silently delete the user's only copy of essential guidance.

Do not publish patient details, credentials, private household exports or secret signing material in an issue/PR.
