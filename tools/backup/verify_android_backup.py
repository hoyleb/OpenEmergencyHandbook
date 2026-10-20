#!/usr/bin/env python3
"""CI only: independently decrypt the Android-generated PUBLIC test fixture."""
import json
from pathlib import Path
import sys
from decrypt_backup import decrypt

root=Path(__file__).resolve().parents[2]
vector=json.loads((root/'android/src/androidTest/assets/encrypted-backup-vector.json').read_text())
payload=decrypt(Path(sys.argv[1]).read_bytes(),vector['password'])
assert payload.decode('utf-8')==vector['plaintext_utf8'], 'Android/Python interoperability failed'
print('Android-generated public fixture independently decrypted with Python PBKDF2-SHA256/AES-GCM.')
