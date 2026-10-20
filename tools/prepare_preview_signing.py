#!/usr/bin/env python3
"""One cache-backed development key per workflow; protected secrets are never cached.

Only public certificate fingerprints are printed. Matrix workers cannot generate
their own replacement keys. A missing/mismatched public pin blocks publishing.
"""
import base64
import hashlib
import os
from pathlib import Path
import subprocess
import sys


def main():
    key = Path(os.environ['OPEN_EMERGENCY_PREVIEW_KEYSTORE'])
    encoded = os.environ.get('OPEN_EMERGENCY_PREVIEW_KEYSTORE_BASE64', '')
    protected = bool(encoded)
    env = os.environ.copy()
    names = ['OPEN_EMERGENCY_PREVIEW_STORE_PASSWORD', 'OPEN_EMERGENCY_PREVIEW_KEY_PASSWORD', 'OPEN_EMERGENCY_PREVIEW_KEY_ALIAS']
    if protected:
        if not all(env.get(name) for name in names):
            raise SystemExit('Configure the preview store password, key password and key alias secrets.')
        key.parent.mkdir(parents=True, exist_ok=True)
        key.write_bytes(base64.b64decode(encoded, validate=True))
        key.chmod(0o600)
    else:
        env.update(dict(zip(names, ['android', 'android', 'androiddebugkey'])))
    if not key.is_file():
        if sys.argv[1] != 'prepare':
            raise SystemExit('Prepared preview key is missing. Refusing a new matrix signing identity.')
        key.parent.mkdir(parents=True, exist_ok=True)
        args = ['keytool', '-genkeypair', '-noprompt', '-keystore', str(key), '-storetype', 'JKS',
                '-storepass:env', names[0], '-keypass:env', names[1], '-alias', env[names[2]],
                '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Open Emergency Preview,O=Development,C=DE']
        subprocess.run(args, env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        key.chmod(0o600)
    der = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key), '-storepass:env', names[0], '-alias', env[names[2]]], env=env, stderr=subprocess.DEVNULL)
    certificate = hashlib.sha256(der).hexdigest()
    pin_file = Path('config/preview-certificate.sha256')
    pinned = pin_file.is_file() and pin_file.read_text().strip() == certificate
    print(f'Preview certificate SHA256: {certificate}\nMatches pinned certificate: {pinned}')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
            out.write(f'certificate={certificate}\npinned={str(pinned).lower()}\n')
    if os.environ.get('GITHUB_ENV') and not protected:
        with open(os.environ['GITHUB_ENV'], 'a') as out:
            for name in names: out.write(f'{name}={env[name]}\n')


if __name__ == '__main__':
    main()
