import base64
import json
from pathlib import Path
import struct
import tempfile
import os
import stat
import unittest
from cryptography.exceptions import InvalidTag
from decrypt_backup import decrypt,save_new,MAX_BYTES

ROOT=Path(__file__).resolve().parents[2]


class EncryptedFormatTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.vector=json.loads((ROOT/'android/src/androidTest/assets/encrypted-backup-vector.json').read_text())
        cls.data=base64.b64decode(cls.vector['encrypted_base64'])

    def test_unicode_vector_is_exact_and_json_remains_restorable(self):
        plaintext=decrypt(self.data,self.vector['password'])
        self.assertEqual(self.vector['plaintext_utf8'],plaintext.decode('utf-8'))
        self.assertEqual('open-emergency-household',json.loads(plaintext)['format'])

    def test_wrong_password_and_every_authenticated_component_are_rejected(self):
        with self.assertRaises(InvalidTag): decrypt(self.data,'wrong fixture password')
        for offset in [12,28,44,len(self.data)-1]:
            changed=bytearray(self.data);changed[offset]^=1
            with self.assertRaises(InvalidTag):decrypt(bytes(changed),self.vector['password'])

    def test_work_factor_size_version_truncation_and_trailing_data_are_rejected(self):
        cases=[self.data[:-1],self.data+b'\0',b'OEHENC02'+self.data[8:],b'\0'*(MAX_BYTES+1)]
        for value in [1,0xffffffff]: cases.append(self.data[:8]+struct.pack('>I',value)+self.data[12:])
        cases.append(self.data[:40]+struct.pack('>I',0xffffffff)+self.data[44:])
        for data in cases:
            with self.assertRaises(ValueError):decrypt(data,self.vector['password'])

    def test_new_output_never_overwrites_an_existing_backup_or_follows_a_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)/'private.json';save_new(output,b'original')
            if os.name=='posix':self.assertEqual(0o600,stat.S_IMODE(output.stat().st_mode))
            with self.assertRaises(FileExistsError):save_new(output,b'replace')
            self.assertEqual(b'original',output.read_bytes())
            link=Path(folder)/'link.json';link.symlink_to(output)
            with self.assertRaises(FileExistsError):save_new(link,b'replace')
            self.assertEqual(b'original',output.read_bytes())


if __name__=='__main__':unittest.main()
