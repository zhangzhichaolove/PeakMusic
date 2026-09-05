#!/usr/bin/env python3
"""Build and verify a signed, minified APK plus its exact R8 mapping. Never uploads anything."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APPLICATION_ID = 'com.chao.peakmusic'
SIGNING = ('ANDROID_RELEASE_KEYSTORE', 'ANDROID_RELEASE_STORE_PASSWORD',
           'ANDROID_RELEASE_KEY_ALIAS', 'ANDROID_RELEASE_KEY_PASSWORD')


def required_inputs(env):
    for key in (*SIGNING, 'RELEASE_VERSION_CODE', 'RELEASE_VERSION_NAME', 'ANDROID_RELEASE_CERT_SHA256'):
        if not env.get(key):
            raise ValueError(f'Missing environment input: {key}')
    code, name = env['RELEASE_VERSION_CODE'], env['RELEASE_VERSION_NAME']
    if not re.fullmatch(r'[0-9]{1,10}', code) or not 1 <= int(code) <= 2100000000:
        raise ValueError('RELEASE_VERSION_CODE must be 1..2100000000')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+\-]{0,63}', name):
        raise ValueError('RELEASE_VERSION_NAME must be a 1..64 character ASCII label')
    cert = env['ANDROID_RELEASE_CERT_SHA256'].replace(':', '').lower()
    if not re.fullmatch(r'[0-9a-f]{64}', cert):
        raise ValueError('ANDROID_RELEASE_CERT_SHA256 must be the expected SHA-256 certificate fingerprint')
    key = Path(env['ANDROID_RELEASE_KEYSTORE'])
    if not key.is_absolute() or not key.is_file():
        raise ValueError('ANDROID_RELEASE_KEYSTORE must be an absolute file path')
    return int(code), name, cert


def sdk_tools(env):
    sdk = env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise ValueError('Set ANDROID_HOME to the Android SDK')
    versions = [p for p in (Path(sdk) / 'build-tools').glob('*')
                if re.fullmatch(r'\d+\.\d+\.\d+', p.name)]
    for version in sorted(versions, key=lambda p: tuple(map(int, p.name.split('.'))), reverse=True):
        if all((version / tool).is_file() for tool in ('apksigner', 'aapt2')):
            return version / 'apksigner', version / 'aapt2'
    raise ValueError('Install Android SDK build-tools (apksigner and aapt2)')


def output(args, cwd=ROOT):
    return subprocess.check_output([str(arg) for arg in args], cwd=cwd, text=True, stderr=subprocess.STDOUT)


def check_signature(report, expected):
    # Build-tools 37 labels the verified identity "V3.0 Signer:"; older versions use "Signer #1".
    prefix = r'(?:Signer #\d+|V\d+(?:\.\d+)? Signer):?'
    certs = re.findall(r'^' + prefix + r' certificate SHA-256 digest: ([0-9a-fA-F]+)$', report, re.M)
    if not re.search(r'^Number of signers: 1$', report, re.M) or not certs or {c.lower() for c in certs} != {expected}:
        raise ValueError('APK signer does not match ANDROID_RELEASE_CERT_SHA256 (single identity required)')
    if re.search(r'^' + prefix + r' certificate DN: .*CN=Android Debug(?:,|$)', report, re.M):
        raise ValueError('The public Android Debug certificate is not a release signing identity')


def check_badging(report, application_id, code, name):
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", report, re.M)
    if not match or match.groups() != (application_id, str(code), name):
        raise ValueError('APK manifest identity/version differs from requested release and AGP metadata')
    if re.search(r'^application-debuggable(?:\s|$)', report, re.M):
        raise ValueError('A debuggable APK is not a release artifact')


def mapping_identity(apk, mapping):
    # The R8 marker embedded in DEX binds this APK to its mapping, not just a filename/timestamp.
    with mapping.open('rb') as source:
        lines = []
        for _ in range(20):
            line = source.readline()
            lines.append(line)
            if line.startswith(b'# pg_map_hash: '):
                break
        header = b''.join(lines).decode('utf-8')
        expected_hash = re.search(r'^# pg_map_hash: SHA-256 ([0-9a-f]{64})$', header, re.M)
        if not expected_hash:
            raise ValueError('R8 mapping has no supported content hash')
        digest = hashlib.sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
        if digest.hexdigest() != expected_hash[1]:
            raise ValueError('R8 mapping content hash does not match (modified/truncated mapping)')
    match = re.search(r'^# pg_map_id: (\S+)$', header, re.M)
    if not match:
        raise ValueError('R8 mapping has no pg_map_id')
    map_id = match[1]
    ids = set()
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', entry):
                for raw in re.findall(rb'~~R8\{[^}]+\}', archive.read(entry)):
                    marker = json.loads(raw[4:])
                    if marker.get('compilation-mode') == 'release' and marker.get('pg-map-id'):
                        ids.add(marker['pg-map-id'])
    if ids != {map_id}:
        raise ValueError('R8 mapping does not match the release DEX marker')
    return map_id


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def archive_release(root, code, name, cert, apksigner, aapt2):
    apk_dir = root / 'app/build/outputs/apk/release'
    metadata_path = apk_dir / 'output-metadata.json'
    metadata = json.loads(metadata_path.read_text(encoding='utf-8'))
    elements = metadata.get('elements', [])
    if metadata.get('variantName') != 'release' or len(elements) != 1 or elements[0].get('filters'):
        raise ValueError('Expected one unsplit release APK in current AGP output metadata')
    element = elements[0]
    if element.get('versionCode') != code or element.get('versionName') != name:
        raise ValueError('AGP output has a stale or unexpected release version')
    filename = element.get('outputFile', '')
    if not filename or Path(filename).name != filename:
        raise ValueError('Invalid APK output filename')
    apk = apk_dir / filename
    mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
    application_id = metadata.get('applicationId', '')
    if application_id != APPLICATION_ID:
        raise ValueError('AGP output application ID does not belong to this project')
    # Successful apksigner exit is essential: printed certificate data alone is not verification.
    signature = output([apksigner, 'verify', '--verbose', '--print-certs', apk], root)
    check_signature(signature, cert)
    check_badging(output([aapt2, 'dump', 'badging', apk], root), application_id, code, name)
    map_id = mapping_identity(apk, mapping)
    files = {'app.apk': apk, 'mapping.txt': mapping}
    hashes = {entry: sha256(path) for entry, path in files.items()}
    manifest = {
        'schema': 1, 'applicationId': application_id, 'versionCode': code, 'versionName': name,
        'variant': 'release', 'signedCertificateSha256': cert, 'r8MapId': map_id,
        'gitRevision': output(['git', 'rev-parse', 'HEAD'], root).strip(),
        'workingTreeDirty': bool(output(['git', 'status', '--porcelain'], root).strip()),
        'packagedAtUtc': datetime.now(timezone.utc).isoformat(), 'sha256': hashes,
        'publication': 'not_published',
    }
    destination = root / 'app/build/release-archives'
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / f'{application_id}-{code}-{hashes["app.apk"][:12]}.zip'
    # Exclusive publication: a retry cannot overwrite a previously archived APK/mapping pair.
    with tempfile.NamedTemporaryFile(dir=destination, suffix='.tmp', delete=False) as temporary:
        pending = Path(temporary.name)
    try:
        with zipfile.ZipFile(pending, 'w', zipfile.ZIP_DEFLATED) as archive:
            for entry, source in files.items():
                archive.write(source, entry)
            manifest_bytes = (json.dumps(manifest, indent=2, ensure_ascii=False) + '\n').encode('utf-8')
            archive.writestr('release-manifest.json', manifest_bytes)
            hashes_with_manifest = {**hashes, 'release-manifest.json': hashlib.sha256(manifest_bytes).hexdigest()}
            archive.writestr('SHA256SUMS', ''.join(f'{digest}  {entry}\n' for entry, digest in sorted(hashes_with_manifest.items())))
        os.link(pending, path)
    finally:
        pending.unlink(missing_ok=True)
    return path


def main():
    code, name, cert = required_inputs(os.environ)
    apksigner, aapt2 = sdk_tools(os.environ)
    # Avoid persisting signing inputs in a configuration-cache snapshot or a reused daemon.
    subprocess.run([str(ROOT / 'gradlew'), '--no-daemon', '--no-configuration-cache', '--console=plain',
                    ':app:testDebugUnitTest', ':app:lintDebug', ':app:lintRelease', ':app:assembleRelease'], cwd=ROOT, check=True)
    print(archive_release(ROOT, code, name, cert, apksigner, aapt2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile, KeyError) as error:
        # Do not echo subprocess output/environment: signing failures can include local paths.
        print(f'Release packaging failed: {error if not isinstance(error, subprocess.CalledProcessError) else "build or artifact verification failed"}', file=sys.stderr)
        sys.exit(1)
