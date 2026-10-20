import copy
import json
from pathlib import Path
import unittest
from build_handbook import ROOT, validate, make_html, coverage_report, content_digest, recovery_files


class ContentContractTest(unittest.TestCase):
    def setUp(self):
        self.content = json.loads((ROOT / 'content/handbook.json').read_text())

    def test_shipped_guides_have_all_required_modules_and_both_languages(self):
        validate(self.content)
        self.assertGreaterEqual(len(self.content['articles']), 30)

    def test_missing_disaster_is_rejected(self):
        self.content['articles'] = [a for a in self.content['articles'] if a['id'] != 'fallout']
        with self.assertRaisesRegex(ValueError, 'Missing required modules'):
            validate(self.content)

    def test_missing_translation_is_rejected(self):
        del self.content['articles'][0]['de']
        with self.assertRaisesRegex(ValueError, 'Missing de'):
            validate(self.content)

    def test_review_cannot_be_claimed_without_a_record(self):
        self.content['articles'][0]['status'] = 'reviewed'
        with self.assertRaisesRegex(ValueError, 'Unsubstantiated review'):
            validate(self.content)

    def test_portable_edition_does_not_load_remote_scripts_or_assets(self):
        rendered = make_html(self.content)
        self.assertNotIn('<script src=', rendered)
        self.assertNotIn('<link ', rendered)
        self.assertNotIn('<img ', rendered)
        self.assertIn('en-fallout', rendered)
        self.assertIn('de-fallout', rendered)

    def test_app_requests_no_network_or_location_permissions(self):
        manifest = (ROOT / 'android/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('uses-permission', manifest)
        self.assertIn('android:allowBackup="false"', manifest)

    def test_related_guides_must_exist(self):
        self.content['articles'][0]['related'] = ['made-up-protocol']
        with self.assertRaisesRegex(ValueError, 'Invalid related guide'):
            validate(self.content)

    def test_recovery_files_include_every_canonical_guide_with_matching_hash(self):
        import hashlib
        files = recovery_files(self.content)
        manifest = json.loads(files['index.json'])
        self.assertEqual(len(self.content['articles']), len(manifest['articles']))
        for part in manifest['articles']:
            payload = files[part['id'] + '.json']
            self.assertEqual(part['sha256'], hashlib.sha256(payload).hexdigest())
            self.assertEqual(next(a for a in self.content['articles'] if a['id'] == part['id']), json.loads(payload))

    def test_duplicate_article_id_is_rejected(self):
        self.content['articles'].append(self.content['articles'][0])
        with self.assertRaisesRegex(ValueError, 'duplicate article'):
            validate(self.content)

    def test_review_is_invalidated_by_an_edit(self):
        article = self.content['articles'][0]
        article['status'] = 'reviewed'
        record = dict(reviewer='Test fixture only', role='Test fixture', date='2026-10-19', scope='Both languages', evidence='fixture', content_sha256=content_digest(article))
        article['professional_review'] = record.copy()
        article['translation_review'] = record.copy()
        validate(self.content)
        article['de']['body'] += '\nChanged procedure'
        with self.assertRaisesRegex(ValueError, 'Review does not match content'):
            validate(self.content)

    def test_content_matrix_reports_gaps_and_blocks_public_release(self):
        matrix = json.loads((ROOT / 'content/coverage.json').read_text())
        report = coverage_report(self.content, matrix)
        self.assertFalse(report['public_release_ready'])
        self.assertTrue(any(r['status'] == 'missing' for r in report['requirements']))
        self.assertTrue(any(r['status'] == 'partial' for r in report['requirements']))
        self.assertTrue(any(r['status'] == 'draft' for r in report['requirements']))

    def test_matrix_cannot_reference_nonexistent_guidance(self):
        matrix = {'schema_version': 1, 'requirements': [{'requirement': 'Unknown', 'article_ids': ['absent'], 'remaining': ''}]}
        with self.assertRaisesRegex(ValueError, 'missing article'):
            coverage_report(self.content, matrix)

    def test_invalid_review_dates_are_rejected(self):
        self.content['articles'][0]['review_due'] = '2020-01-01'
        with self.assertRaisesRegex(ValueError, 'date range'):
            validate(self.content)

    def test_every_portable_related_anchor_exists(self):
        import re
        rendered = make_html(self.content)
        targets = set(re.findall(r' id="([^"]+)"', rendered))
        for target in re.findall(r'href="#([^"]+)"', rendered):
            self.assertIn(target, targets)
        self.assertIn('data-keywords=', rendered)


if __name__ == '__main__':
    unittest.main()
