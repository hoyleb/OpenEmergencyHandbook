import unittest
from verify_preview_signature import verify


class PackageContractTest(unittest.TestCase):
    def test_certificate_package_and_unexpected_permissions_block_distribution(self):
        cert = 'a' * 64
        report = 'Signer #1 certificate SHA-256 digest: ' + cert
        manifest = "package: name='com.hoyleb.openemergency.preview'\nuses-permission: name='com.hoyleb.openemergency.preview.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'"
        self.assertEqual(cert, verify(report, cert, manifest)['certificate_sha256'])
        for permission in ('android.permission.INTERNET', 'android.permission.ACCESS_FINE_LOCATION', 'android.permission.READ_CONTACTS'):
            with self.assertRaises(ValueError): verify(report, cert, manifest + "\nuses-permission: name='" + permission + "'")
        with self.assertRaises(ValueError): verify(report, 'b' * 64, manifest)
        with self.assertRaises(ValueError): verify(report, cert, manifest.replace('com.hoyleb.openemergency.preview', 'other.application'))
