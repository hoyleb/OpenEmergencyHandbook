#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import re
import sys

report = Path(sys.argv[1]).read_text()
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})', report).group(1)
if certificate != sys.argv[2]:
    raise SystemExit('APK certificate differs from the workflow-prepared identity')
pin = Path('config/preview-certificate.sha256')
record = {'certificate_sha256': certificate, 'matches_prepared_key': True,
          'matches_published_pin': pin.is_file() and pin.read_text().strip() == certificate,
          'production_signing': False}
Path(sys.argv[3]).write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
