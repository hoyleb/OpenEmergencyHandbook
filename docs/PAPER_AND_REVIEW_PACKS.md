# Paper and reviewer packs

The generated files in [packs](packs/) use the same canonical bilingual content as the app. They are development drafts with no new clinical instructions or claimed professional approval. Print them for evaluation alongside the full handbook and current official information.

## Print for a flat battery

Download [English A5 quick cards](packs/OpenEmergencyHandbook-quick-cards-en.pdf) or [German A5 quick cards](packs/OpenEmergencyHandbook-quick-cards-de.pdf).

- Print **A4, landscape, 100% / actual size, single-sided**, then cut each sheet at the dashed centre line. Do not use multiple-pages-per-sheet mode. Each language has eight sheets, making sixteen A5 cards.
- Fourteen cards contain the full existing guide and summary: first-aid overview, adult CPR, child/infant CPR, adult choking, child/infant choking, safe water, power outage, flood, earthquake, fallout, cyber/digital outage, pandemic, communication and water-advisory distinctions. None is shortened to fit. This selection is not the complete handbook.
- Two cards are blank household contact/support worksheets. Fill them in by hand; no personal household information was used to create these files. Completed sheets may contain private details: choose where to store/share them deliberately and do not upload them to a public issue or PR.
- Draft labels, content edition, guide ID, source-comparison date and short content hash remain on the guide cards. The full [handbook PDFs and source list](https://github.com/hoyleb/OpenEmergencyHandbook/releases/tag/preview-0.4.51) remain companions, including additional conditions, related guides and source URLs. These cards do not establish medical accuracy or replace training/emergency services.
- Check an actual printed copy for readability, printer margins, cut positions and whether your household can find it. Digital rendering is not a physical print/user test. There are no reviewed action diagrams yet.

Guide body type is fixed at 10.4 pt. If revised content no longer fits, the generator fails instead of deleting text or shrinking it silently. PDF metadata timestamps are fixed for deterministic output; the visible content edition is the meaningful source date. These PDFs are not tagged PDF/UA documents; the HTML reviewer pack provides a semantic-text alternative.

## Review the material

Open [the bilingual reviewer HTML](packs/OpenEmergencyHandbook-review.html) locally in a browser. It needs no JavaScript or external assets. All thirty guides appear in English/German columns on wide screens and stack on narrow screens. Sources are explicit external links; the file does not fetch them automatically. Browser opening/rendering from GitHub's code viewer may require downloading the HTML first.

The accompanying files are:

| File | Purpose and limits |
| --- | --- |
| `source-mapping-worksheet.csv` | 430 rows for titles, summaries, search keywords and exact paragraph/section blocks in both languages. Candidate source URLs are supplied; verified URL, source edition, locator, finding, reviewer and evidence are blank. A candidate is not a verified claim-to-source mapping. Spreadsheet formula prefixes are escaped. |
| `review-record-templates.json` | Separate blank professional and translation review records bound to each guide's current bilingual content/source hash. Templates are work aids; they are not automatically imported or treated as approvals. |
| `source-maintenance-inventory.json` | 55 distinct URLs with article mappings, current recorded review status and due-date comparisons against an explicit inventory date. URLs were not fetched: no availability, source-edition, rights or content-validity check is claimed. |
| `handbook-review-snapshot.json` | Exact canonical data as deterministically formatted JSON. This enables an offline handoff with the review hashes. |
| `coverage-report.json` | The existing 47-requirement gap matrix. The public-release gate remains blocked. |
| `PACK-MANIFEST.json`, `SHA256SUMS.txt` | File sizes/hashes, selected guide IDs and paper dimensions. Hashes detect changes; they do not authenticate a reviewer or establish accuracy. |

Follow [CONTENT_REVIEW.md](CONTENT_REVIEW.md) to conduct and record actual reviews. Recompute the hashes after a content/source edit and review the changed scope again. Never copy a blank template into the canonical source as a completed review. The generated pack does not overwrite canonical content or review records.

## Rebuild and check

Requires Python 3.12, `fonts-dejavu-core` and the pinned PDF tools:

```sh
python3 -m pip install -r tools/printpack/requirements-test.txt
python3 tools/printpack/build_print_pack.py --output docs/packs --as-of 2026-10-20
python3 tools/printpack/build_print_pack.py --output docs/packs --as-of 2026-10-20 --check
python3 -m unittest discover -s tools/printpack -p 'test_*.py' -v
```

Use the deliberate inventory date recorded in `PACK-MANIFEST.json`; it is not a renewed professional/source check date. After changing content, regenerate, inspect every rendered sheet and commit all generated files. `--check` rejects missing or stale generated output. With the pinned tools/fonts and identical inputs, generation produces identical bytes; no claim about reproducible Android APKs follows from that.

The **Paper and review pack** workflow checks freshness, complete text retention, cut-edge bounds, draft labels, overflow rejection, hashes, unsafe CSV formula prefixes, HTML escaping and blank review fields. It renders every sheet and runs reviewer-layout checks in Chromium/Firefox/WebKit without JavaScript, including 200% CSS text. Downloads and evidence are uploaded as the `Paper-and-review-pack` workflow artifact. The workflow does not require or modify an Android signing key.

For a visual check locally, install Poppler and run:

```sh
pdftoppm -scale-to 1400 -png docs/packs/OpenEmergencyHandbook-quick-cards-en.pdf /tmp/quick-cards-en
pdftoppm -scale-to 1400 -png docs/packs/OpenEmergencyHandbook-quick-cards-de.pdf /tmp/quick-cards-de
```
