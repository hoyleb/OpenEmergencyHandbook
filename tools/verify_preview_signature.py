#!/usr/bin/env python3
"""Verify the built APK's identity and merged permission contract before packaging."""
import json
from pathlib import Path
import re
import sys


def verify(report, expected, manifest):
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})', report)
    if not match or match.group(1) != expected:
        raise ValueError('APK certificate differs from the workflow-prepared identity')
    package = re.search(r"package: name='([^']+)'", manifest)
    if not package or package.group(1) != 'com.hoyleb.openemergency.preview':
        raise ValueError('Unexpected preview application identity')
    permissions = re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", manifest)
    allowed = package.group(1) + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
    if any(permission != allowed for permission in permissions):
        raise ValueError('Unexpected APK permission; review before distributing')
    return {'certificate_sha256': expected, 'matches_prepared_key': True,
            'package': package.group(1), 'requested_permissions': permissions, 'production_signing': False}


if __name__ == '__main__':
    record = verify(Path(sys.argv[1]).read_text(), sys.argv[2], Path(sys.argv[4]).read_text())
    pin = Path('config/preview-certificate.sha256')
    record['matches_published_pin'] = pin.is_file() and pin.read_text().strip() == record['certificate_sha256']
    Path(sys.argv[3]).write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record))
