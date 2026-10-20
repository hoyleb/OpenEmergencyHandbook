import tempfile
from pathlib import Path
import unittest
from summarize_android_tests import summarize


class EvidenceTest(unittest.TestCase):
    def test_empty_failed_or_skipped_results_cannot_claim_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError): summarize(directory, 21)
            path = Path(directory) / 'TEST-device.xml'
            for outcome in ('<failure>bad</failure>', '<error>bad</error>', '<skipped/>'):
                path.write_text(f'<testsuite><testcase classname="fixture" name="case">{outcome}</testcase></testsuite>')
                with self.assertRaises(ValueError): summarize(directory, 21)
            path.write_text('<testsuite><testcase classname="fixture" name="case"/></testsuite>')
            self.assertEqual(1, summarize(directory, 21)['passed'])
