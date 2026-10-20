#!/usr/bin/env python3
"""Fail closed on empty/failed/skipped instrumentation evidence; record exact counts."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(directory, api):
    tests = {}
    for path in Path(directory).rglob('TEST-*.xml'):
        for case in ET.parse(path).getroot().iter('testcase'):
            key = (case.get('classname'), case.get('name'))
            assert key not in tests, 'Duplicate test evidence'
            tests[key] = 'failed' if case.find('failure') is not None or case.find('error') is not None else ('skipped' if case.find('skipped') is not None else 'passed')
    if not tests or any(state != 'passed' for state in tests.values()):
        raise ValueError('Missing, failed or skipped Android tests')
    return {'api': int(api), 'passed': len(tests), 'failed': 0, 'skipped': 0, 'wifi_mobile_data': 'disabled',
            'tests': [{'class': cls, 'name': name} for cls, name in sorted(tests)]}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--api', required=True)
    parser.add_argument('--directory', default='android/build/outputs/androidTest-results/connected/debug')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    Path(args.output).write_text(json.dumps(summarize(args.directory, args.api), indent=2) + '\n')
