#!/usr/bin/env python3
"""Exercise real Gradle/signing/archive failures with a disposable key. No adb, upload or production keys."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_package', ROOT / 'scripts/package-release.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def main():
    evidence = ROOT / 'app/build/verification/release-config'
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / 'verification.txt').unlink(missing_ok=True)
    (evidence / 'TEST-ONLY-release.zip').unlink(missing_ok=True)
    env = os.environ.copy()
    for name in (*release.SIGNING, 'RELEASE_VERSION_CODE', 'RELEASE_VERSION_NAME', 'ANDROID_RELEASE_CERT_SHA256'):
        env.pop(name, None)  # A developer's real signing inputs must never enter this fixture.
    signer, aapt2 = release.sdk_tools(env)
    gradle = [str(ROOT / 'gradlew'), '--no-daemon', '--no-configuration-cache', '--console=plain']
    results = []

    def run(label, command, inputs=env, success=True, contains=None):
        completed = subprocess.run(command, cwd=ROOT, env=inputs, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (evidence / f'{label}.txt').write_text(completed.stdout, encoding='utf-8')
        if (completed.returncode == 0) != success or (contains and contains not in completed.stdout):
            raise AssertionError(f'{label} failed; inspect {evidence / (label + ".txt")}')
        if inputs.get('ANDROID_RELEASE_STORE_PASSWORD') and inputs['ANDROID_RELEASE_STORE_PASSWORD'] in completed.stdout:
            raise AssertionError(f'{label} disclosed its signing password')
        results.append(label)
        return completed.stdout

    try:
        run('default-unsigned', gradle + [':app:assembleRelease'])
        metadata = json.loads((ROOT / 'app/build/outputs/apk/release/output-metadata.json').read_text())
        element = metadata['elements'][0]
        assert element['versionCode'] == 1 and element['versionName'] == '1.0'
        assert element['outputFile'] == 'app-release-unsigned.apk'
        run('unsigned-signature-rejected', [str(signer), 'verify', str(ROOT / 'app/build/outputs/apk/release' / element['outputFile'])], success=False)
        run('version-pair-required', gradle + [':app:help'], {**env, 'RELEASE_VERSION_CODE': '42'}, False, 'Set RELEASE_VERSION_CODE')
        run('version-range-enforced', gradle + [':app:help'], {**env, 'RELEASE_VERSION_CODE': '2100000001', 'RELEASE_VERSION_NAME': '1.0'}, False, 'Set RELEASE_VERSION_CODE')
        run('partial-signing-rejected', gradle + [':app:help'], {**env, 'ANDROID_RELEASE_KEY_ALIAS': 'fixture'}, False, 'No unsigned fallback')
        with tempfile.TemporaryDirectory(prefix='android-release-verification-') as temporary:
            key = Path(temporary) / 'fixture.p12'
            inputs = {**env, 'RELEASE_VERSION_CODE': '42', 'RELEASE_VERSION_NAME': '0.0.0-verification',
                      'ANDROID_RELEASE_KEYSTORE': str(key), 'ANDROID_RELEASE_STORE_PASSWORD': 'LOCAL_FIXTURE_PASSWORD_42',
                      'ANDROID_RELEASE_KEY_ALIAS': 'fixture', 'ANDROID_RELEASE_KEY_PASSWORD': 'LOCAL_FIXTURE_PASSWORD_42'}
            # keytool consumes the password through environment, not process arguments.
            run('generate-disposable-key', ['keytool', '-genkeypair', '-noprompt', '-keystore', str(key), '-storetype', 'PKCS12',
                '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=Local Release Verification',
                '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD', '-keypass:env', 'ANDROID_RELEASE_KEY_PASSWORD'], inputs)
            cert = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key), '-alias', 'fixture',
                       '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD'], env=inputs, stderr=subprocess.PIPE)
            inputs['ANDROID_RELEASE_CERT_SHA256'] = release.hashlib.sha256(cert).hexdigest()
            output = run('signed-version-and-archive', ['python3', str(ROOT / 'scripts/package-release.py')], inputs)
            archive = Path(output.strip().splitlines()[-1])
            assert archive.is_file()
            try:
                with zipfile.ZipFile(archive) as bundle:
                    manifest = json.loads(bundle.read('release-manifest.json'))
                    assert manifest['versionCode'] == 42 and manifest['versionName'] == '0.0.0-verification'
                    assert manifest['signedCertificateSha256'] == inputs['ANDROID_RELEASE_CERT_SHA256']
                    for line in bundle.read('SHA256SUMS').decode().splitlines():
                        digest, filename = line.split('  ', 1)
                        assert release.hashlib.sha256(bundle.read(filename)).hexdigest() == digest
                before = set(archive.parent.iterdir())
                try:
                    release.archive_release(ROOT, 42, '0.0.0-verification', '00' * 32, signer, aapt2)
                    raise AssertionError('Wrong expected certificate accepted')
                except ValueError as error:
                    assert 'APK signer' in str(error)
                assert before == set(archive.parent.iterdir())
                results.append('wrong-certificate-rejected')
                wrong_mapping = Path(temporary) / 'wrong-mapping.txt'
                wrong_mapping.write_text('# pg_map_id: stale-mapping\n# pg_map_hash: SHA-256 ' + release.hashlib.sha256(b'').hexdigest() + '\n')
                try:
                    release.mapping_identity(ROOT / 'app/build/outputs/apk/release/app-release.apk', wrong_mapping)
                    raise AssertionError('Mismatched mapping accepted')
                except ValueError as error:
                    assert 'DEX marker' in str(error)
                results.append('wrong-mapping-rejected')
                shutil.copyfile(archive, evidence / 'TEST-ONLY-release.zip')
            finally:
                archive.unlink()  # Do not mix the disposable certificate's archive with real releases.
            run('wrong-password-rejected', gradle + [':app:assembleRelease'],
                {**inputs, 'ANDROID_RELEASE_STORE_PASSWORD': 'WRONG_FIXTURE_PASSWORD'}, False)
            # No archive script is run after a signing failure, and Gradle must not fall back to unsigned.
    finally:
        run('restore-default-artifacts', gradle + [':app:assembleDebug', ':app:assembleRelease', ':app:assembleDebugAndroidTest'])
    (evidence / 'verification.txt').write_text('\n'.join(results) + '\nRELEASE_CONFIG_OK\n', encoding='utf-8')
    print(f'RELEASE_CONFIG_OK: {evidence}')


if __name__ == '__main__':
    main()
