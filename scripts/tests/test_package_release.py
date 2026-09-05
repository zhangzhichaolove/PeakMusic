import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import zipfile

MODULE = Path(__file__).resolve().parents[1] / 'package-release.py'
spec = importlib.util.spec_from_file_location('release_package', MODULE)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleasePackageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.key = self.root / 'fixture.keystore'
        self.key.touch()
        self.cert = 'ab' * 32
        self.env = dict(zip(release.SIGNING, [str(self.key), 'PRIVATE_STORE_CANARY', 'fixture', 'PRIVATE_KEY_CANARY']))
        self.env.update(RELEASE_VERSION_CODE='42', RELEASE_VERSION_NAME='2.0.0-rc1', ANDROID_RELEASE_CERT_SHA256=self.cert)
        self.apk_dir = self.root / 'app/build/outputs/apk/release'
        self.apk_dir.mkdir(parents=True)
        self.mapping = self.root / 'app/build/outputs/mapping/release/mapping.txt'
        self.mapping.parent.mkdir(parents=True)
        body = 'com.example.Type -> a:\n'
        self.mapping.write_text('# compiler: R8\n# pg_map_id: fixture-map\n# pg_map_hash: SHA-256 ' + hashlib.sha256(body.encode()).hexdigest() + '\n' + body)
        with zipfile.ZipFile(self.apk_dir / 'app-release.apk', 'w') as apk:
            apk.writestr('classes.dex', b'\x00~~R8{"compilation-mode":"release","pg-map-id":"fixture-map"}\x00')
        self.metadata = {'applicationId': release.APPLICATION_ID, 'variantName': 'release',
                         'elements': [{'versionCode': 42, 'versionName': '2.0.0-rc1', 'outputFile': 'app-release.apk', 'filters': []}]}
        self.write_metadata()
        self.signature = f'Verifies\nNumber of signers: 1\nSigner #1 certificate DN: CN=Release Fixture\nSigner #1 certificate SHA-256 digest: {self.cert}\n'
        self.badging = f"package: name='{release.APPLICATION_ID}' versionCode='42' versionName='2.0.0-rc1'\n"

    def write_metadata(self):
        (self.apk_dir / 'output-metadata.json').write_text(json.dumps(self.metadata))

    def tool_output(self, args, cwd):
        if args[0] == 'apksigner':
            return self.signature
        if args[0] == 'aapt2':
            return self.badging
        if args[:2] == ['git', 'rev-parse']:
            return 'f' * 40 + '\n'
        if args[:2] == ['git', 'status']:
            return ' M app/build.gradle\n'
        raise AssertionError(args)

    def package(self):
        with mock.patch.object(release, 'output', side_effect=self.tool_output):
            return release.archive_release(self.root, 42, '2.0.0-rc1', self.cert, 'apksigner', 'aapt2')

    def test_required_inputs_and_colon_separated_certificate(self):
        self.env['ANDROID_RELEASE_CERT_SHA256'] = ':'.join(['AB'] * 32)
        self.assertEqual((42, '2.0.0-rc1', self.cert), release.required_inputs(self.env))
        for key in self.env:
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.required_inputs({k: v for k, v in self.env.items() if k != key})

    def test_invalid_versions_and_key_paths_are_rejected(self):
        for code in ['0', '-1', '2100000001', '1.5', 'x', '1' * 100]:
            with self.subTest(code=code), self.assertRaises(ValueError):
                release.required_inputs({**self.env, 'RELEASE_VERSION_CODE': code})
        for name in ['', '../release', 'one\ntwo', 'v' * 65]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                release.required_inputs({**self.env, 'RELEASE_VERSION_NAME': name})
        with self.assertRaises(ValueError):
            release.required_inputs({**self.env, 'ANDROID_RELEASE_KEYSTORE': 'relative.keystore'})

    def test_signature_requires_one_matching_non_debug_identity(self):
        release.check_signature(self.signature, self.cert)
        release.check_signature(self.signature.replace('Signer #1', 'V3.0 Signer:'), self.cert)
        for report in [self.signature.replace('Number of signers: 1', 'Number of signers: 2'), self.signature.replace(self.cert, 'cd' * 32), self.signature + f'Signer #2 certificate SHA-256 digest: {"cd" * 32}\n',
                       self.signature.replace('CN=Release Fixture', 'CN=Android Debug, O=Android, C=US')]:
            with self.subTest(report=report), self.assertRaises(ValueError):
                release.check_signature(report, self.cert)

    def test_badging_must_match_actual_version_and_not_be_debuggable(self):
        release.check_badging(self.badging, release.APPLICATION_ID, 42, '2.0.0-rc1')
        for report in [self.badging.replace("versionCode='42'", "versionCode='1'"), self.badging + 'application-debuggable\n']:
            with self.subTest(report=report), self.assertRaises(ValueError):
                release.check_badging(report, release.APPLICATION_ID, 42, '2.0.0-rc1')

    def test_mapping_requires_exact_release_dex_identity(self):
        self.assertEqual('fixture-map', release.mapping_identity(self.apk_dir / 'app-release.apk', self.mapping))
        original = self.mapping.read_text()
        for text in [original.replace('fixture-map', 'stale-map'), original + 'tampered\n', '# compiler: R8\n']:
            self.mapping.write_text(text)
            with self.assertRaises(ValueError):
                self.package()
        self.assertFalse((self.root / 'app/build/release-archives').exists())

    def test_stale_metadata_wrong_package_splits_and_path_traversal_fail(self):
        for change in [('versionCode', 1), ('outputFile', '../old.apk'), ('filters', ['split'])]:
            original = self.metadata['elements'][0].copy()
            self.metadata['elements'][0][change[0]] = change[1]
            self.write_metadata()
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.package()
            self.metadata['elements'][0] = original
        self.metadata['applicationId'] = 'com.other.app'
        self.write_metadata()
        with self.assertRaises(ValueError):
            self.package()

    def test_failed_signature_command_never_archives_printed_certificate(self):
        def failed(args, cwd):
            raise subprocess.CalledProcessError(1, args, output=self.signature)
        with mock.patch.object(release, 'output', side_effect=failed), self.assertRaises(subprocess.CalledProcessError):
            release.archive_release(self.root, 42, '2.0.0-rc1', self.cert, 'apksigner', 'aapt2')
        self.assertFalse((self.root / 'app/build/release-archives').exists())

    def test_archive_contains_exact_hashes_mapping_provenance_and_no_signing_secrets(self):
        path = self.package()
        with zipfile.ZipFile(path) as archive:
            self.assertEqual({'app.apk', 'mapping.txt', 'release-manifest.json', 'SHA256SUMS'}, set(archive.namelist()))
            manifest = json.loads(archive.read('release-manifest.json'))
            self.assertTrue(manifest['workingTreeDirty'])
            self.assertEqual('not_published', manifest['publication'])
            self.assertEqual('fixture-map', manifest['r8MapId'])
            self.assertEqual(self.cert, manifest['signedCertificateSha256'])
            for line in archive.read('SHA256SUMS').decode().splitlines():
                digest, filename = line.split('  ', 1)
                self.assertEqual(digest, hashlib.sha256(archive.read(filename)).hexdigest())
            for field in ['release-manifest.json', 'SHA256SUMS']:
                self.assertNotIn(b'PRIVATE_', archive.read(field))
                self.assertNotIn(str(self.key).encode(), archive.read(field))
        before = release.sha256(path)
        with self.assertRaises(FileExistsError):
            self.package()
        self.assertEqual(before, release.sha256(path))
        self.assertFalse(list(path.parent.glob('*.tmp')))


if __name__ == '__main__':
    unittest.main()
