import copy
import json
from pathlib import Path
import unittest
from build_handbook import ROOT, validate, make_html


class ContentContractTest(unittest.TestCase):
    def setUp(self):
        self.content = json.loads((ROOT / 'content/handbook.json').read_text())

    def test_shipped_guides_have_all_required_modules_and_both_languages(self):
        validate(self.content)
        self.assertEqual(len(self.content['articles']), 15)

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


if __name__ == '__main__':
    unittest.main()
