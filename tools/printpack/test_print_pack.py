import copy
import csv
from datetime import date
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
import zipfile

from pypdf import PdfReader
import pdfplumber

from build_print_pack import (ROOT, CARD_IDS, CARD_COUNT, SHEET_COUNT, LABELS,
                              card_pdf, content_digest, csv_cell, outputs,
                              review_blocks, review_outputs, source_inventory)


def normalized(text):
    return ''.join(c for c in text if c.isalnum())


class PrintPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads((ROOT/'content/handbook.json').read_text())
        cls.matrix = json.loads((ROOT/'content/coverage.json').read_text())
        cls.as_of = date(2026, 10, 20)
        cls.generated = outputs(cls.data, cls.matrix, cls.as_of)

    def test_every_selected_guide_body_and_summary_are_present_in_full(self):
        by_id = {a['id']:a for a in self.data['articles']}
        for lang in ('en','de'):
            reader = PdfReader(io.BytesIO(self.generated[f'OpenEmergencyHandbook-quick-cards-{lang}.pdf']))
            self.assertEqual(SHEET_COUNT, len(reader.pages))
            for index, aid in enumerate(CARD_IDS):
                page = normalized(reader.pages[index//2].extract_text())
                self.assertIn(normalized(by_id[aid][lang]['body']), page, f'{lang}/{aid}')
                self.assertIn(normalized(by_id[aid][lang]['summary']), page, f'{lang}/{aid}/summary')
                self.assertIn(normalized(content_digest(by_id[aid])[:12]),page)
            self.assertIn(normalized(LABELS[lang]['plan']), normalized(reader.pages[-1].extract_text()))
            self.assertIn(normalized(LABELS[lang]['care']), normalized(reader.pages[-1].extract_text()))

    def test_all_cards_keep_visible_draft_labels_and_stay_inside_their_cut_edges(self):
        for lang in ('en','de'):
            payload = self.generated[f'OpenEmergencyHandbook-quick-cards-{lang}.pdf']
            with pdfplumber.open(io.BytesIO(payload)) as pdf:
                for page in pdf.pages:
                    self.assertAlmostEqual(841.8898, page.width, places=2)
                    self.assertAlmostEqual(595.2756, page.height, places=2)
                    for char in page.chars:
                        self.assertGreaterEqual(char['x0'], 20)
                        self.assertLessEqual(char['x1'], page.width-20)
                        self.assertGreaterEqual(char['top'], 12)
                        self.assertLessEqual(char['bottom'], page.height-12)
                        self.assertTrue(char['x1'] <= page.width/2-20 or char['x0'] >= page.width/2+20)
                    for left in (0, page.width/2):
                        text = page.crop((left,0,left+page.width/2,page.height)).extract_text()
                        self.assertIn(LABELS[lang]['draft'], text)
                        self.assertIn(self.data['edition'], text)

    def test_overflow_is_rejected_instead_of_truncated_or_silently_shrunk(self):
        changed = copy.deepcopy(self.data)
        changed['articles'][0]['en']['body'] += '\n\n' + ('A deliberately oversized paragraph. '*150)
        with self.assertRaisesRegex(ValueError, 'overflow'):
            card_pdf(changed,'en')

    def test_required_card_cannot_silently_disappear(self):
        changed=copy.deepcopy(self.data)
        changed['articles']=[a for a in changed['articles'] if a['id']!='adult-cpr']
        with self.assertRaisesRegex(ValueError, 'Missing quick-card guides'):
            card_pdf(changed,'en')

    def test_pdf_and_pack_are_byte_reproducible_with_fixed_input_and_date(self):
        self.assertEqual(self.generated, outputs(self.data,self.matrix,self.as_of))

    def test_manifest_and_every_checksum_match_actual_payloads(self):
        manifest=json.loads(self.generated['PACK-MANIFEST.json'])
        self.assertEqual(CARD_COUNT,manifest['paper_layout']['cards_per_language'])
        for name,meta in manifest['files'].items():
            self.assertEqual(len(self.generated[name]),meta['bytes'])
            self.assertEqual(hashlib.sha256(self.generated[name]).hexdigest(),meta['sha256'])
        for line in self.generated['SHA256SUMS.txt'].decode().splitlines():
            expected,name=line.split('  ')
            self.assertEqual(expected,hashlib.sha256(self.generated[name]).hexdigest())

    def test_single_download_contains_every_inner_file_without_unsafe_paths(self):
        name='OpenEmergencyHandbook-paper-review-pack.zip'
        with zipfile.ZipFile(io.BytesIO(self.generated[name])) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(self.generated)-{name},set(archive.namelist()))
            for entry in archive.namelist():
                self.assertNotIn('/',entry)
                self.assertEqual(self.generated[entry],archive.read(entry))

    def test_source_worksheet_has_all_exact_bilingual_blocks_and_no_fake_findings(self):
        rows=list(csv.DictReader(io.StringIO(self.generated['source-mapping-worksheet.csv'].decode('utf-8-sig'))))
        expected={(a['id'],lang,b['block_id']):b['text'] for a in self.data['articles'] for lang in ('en','de') for b in review_blocks(a,lang)}
        self.assertEqual(len(expected),len(rows))
        for row in rows:
            self.assertEqual(expected[(row['article_id'],row['language'],row['block_id'])],row['exact_draft_text'])
            for field in ('verified_source_url','source_edition_date','page_section_or_claim_locator','finding_or_correction','reviewer','evidence'):
                self.assertEqual('',row[field])

    def test_csv_cells_cannot_turn_into_spreadsheet_formulas(self):
        for value in ('=HYPERLINK("https://example.org")',' +1','-1','@SUM(1)','\t=1','\r=1','\n=1'):
            self.assertTrue(csv_cell(value).startswith("'"))
        self.assertEqual('Normal text',csv_cell('Normal text'))

    def test_generated_html_escapes_content_and_does_not_load_external_assets(self):
        changed=copy.deepcopy(self.data)
        changed['articles'][0]['en']['title']='</h3><script>alert(1)</script>'
        changed['articles'][0]['en']['body']='<img src=x onerror=alert(1)>'
        rendered=review_outputs(changed,self.matrix,self.as_of)['OpenEmergencyHandbook-review.html'].decode()
        self.assertNotIn('<script',rendered); self.assertNotIn('<img',rendered)
        self.assertIn('&lt;script&gt;',rendered); self.assertIn('&lt;img',rendered)
        for target in re.findall(r'href="#([^"]+)"',rendered):
            self.assertIn('id="'+target+'"',rendered)

    def test_templates_bind_both_reviews_to_the_exact_content_without_approvals(self):
        records=json.loads(self.generated['review-record-templates.json'])['articles']
        for a,record in zip(self.data['articles'],records):
            self.assertEqual(a['id'],record['article_id'])
            for kind in ('professional_review','translation_review'):
                review=record[kind]
                self.assertEqual(content_digest(a),review['content_sha256'])
                self.assertTrue(all(review[k]=='' for k in ('reviewer','role','date','scope','evidence')))
        snapshot=json.loads(self.generated['handbook-review-snapshot.json'])
        self.assertEqual(self.data,snapshot)
        self.assertFalse(json.loads(self.generated['coverage-report.json'])['public_release_ready'])

    def test_inventory_deduplicates_urls_and_due_dates_are_explicit(self):
        changed=copy.deepcopy(self.data)
        a=changed['articles'][0]; a['review_due']='2026-10-20'
        inventory=source_inventory(changed,self.as_of)
        self.assertTrue(inventory['reviews'][0]['due_on_or_before_as_of'])
        earlier=source_inventory(changed,date(2026,10,19))
        self.assertFalse(earlier['reviews'][0]['due_on_or_before_as_of'])
        urls=[s['url'] for s in inventory['sources']]
        self.assertEqual(len(set(urls)),len(urls))
        self.assertEqual({u for a in changed['articles'] for u in a['sources']},set(urls))

    def test_check_mode_detects_tampered_or_missing_outputs(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)
            for name,payload in self.generated.items(): (path/name).write_bytes(payload)
            command=[sys.executable,str(ROOT/'tools/printpack/build_print_pack.py'),'--as-of',self.as_of.isoformat(),'--output',folder,'--check']
            self.assertEqual(0,subprocess.run(command,capture_output=True).returncode)
            (path/'OpenEmergencyHandbook-quick-cards-en.pdf').write_bytes(b'not a PDF')
            result=subprocess.run(command,capture_output=True,text=True)
            self.assertNotEqual(0,result.returncode)
            self.assertIn('Missing/stale',result.stderr)


if __name__=='__main__': unittest.main()
