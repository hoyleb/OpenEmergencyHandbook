#!/usr/bin/env python3
"""Recover an encrypted household backup offline without an Android phone.

The passphrase is read using a hidden terminal prompt, never a command argument.
Requires tools/backup/requirements-test.txt. This does not modify the app or input.
"""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import struct
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC=b'OEHENC01'
ITERATIONS=600000
HEADER_BYTES=44
MAX_PLAIN=512*1024
MAX_BYTES=MAX_PLAIN+HEADER_BYTES+16


def validate_envelope(data):
    if not HEADER_BYTES+16 <= len(data) <= MAX_BYTES or data[:8]!=MAGIC:
        raise ValueError('Unsupported encrypted backup')
    iterations=struct.unpack('>I',data[8:12])[0]
    length=struct.unpack('>I',data[40:44])[0]
    if iterations!=ITERATIONS or not 16 <= length <= MAX_PLAIN+16 or len(data)!=HEADER_BYTES+length:
        raise ValueError('Unsupported encrypted backup parameters or length')


def decrypt(data, password):
    validate_envelope(data)
    if not 1 <= len(password.encode('utf-16-le'))//2 <= 128:
        raise ValueError('Invalid passphrase length')
    key=hashlib.pbkdf2_hmac('sha256',password.encode('utf-8'),data[12:28],ITERATIONS,dklen=32)
    plaintext=AESGCM(key).decrypt(data[28:40],data[HEADER_BYTES:],data[:HEADER_BYTES])
    # Preserve the authenticated bytes exactly. The Android restore path performs
    # its full field/type/range validation and still requires confirmation.
    decoded=json.loads(plaintext.decode('utf-8'))
    if not isinstance(decoded,dict) or decoded.get('format')!='open-emergency-household' or type(decoded.get('version')) is not int or decoded['version']!=1:
        raise ValueError('Unsupported household data')
    return plaintext


def save_new(path, plaintext):
    """Exclusive new file, owner-only mode on Unix; no overwrite/symlink following."""
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        with os.fdopen(fd,'wb') as output:
            output.write(plaintext); output.flush(); os.fsync(output.fileno())
    except BaseException:
        Path(path).unlink(missing_ok=True)
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('output',type=Path,help='New private JSON file; existing files are never overwritten')
    args=parser.parse_args()
    if args.output.exists(): parser.error('Output already exists; choose a new path')
    if not sys.stdin.isatty(): parser.error('Run in a terminal for the hidden passphrase prompt')
    try:
        with args.input.open('rb') as source: data=source.read(MAX_BYTES+1)
        validate_envelope(data)
        password=getpass.getpass('Backup passphrase (no reset available): ')
        try: plaintext=decrypt(data,password)
        finally: password=None  # Python immutable strings cannot be reliably zeroed.
        save_new(args.output,plaintext)
    except (KeyboardInterrupt,EOFError):
        print('Cancelled; keep the original encrypted backup.',file=sys.stderr); return 1
    except Exception:
        print('Could not decrypt/save: wrong passphrase, damaged/unsupported backup, existing output or storage error. Input is unchanged.',file=sys.stderr); return 1
    print('Private JSON backup written. Verify it by restoring in the app; keep the encrypted original.')
    return 0


if __name__=='__main__': sys.exit(main())
