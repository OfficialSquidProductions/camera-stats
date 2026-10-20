#!/usr/bin/env python3
"""Offline Windows update staging. No signing keys, downloads, installs or publication.

Production preparation/finalization require Windows' own Authenticode and MSI checks.
The input policy is reviewed public bootstrap configuration, never downloaded trust.
"""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

APP_ID = 'org.squidproductions.camerastats'
UPGRADE_CODE = 'c3774fda-7962-4264-a9bc-c428176d12b4'
FEED_URL = 'https://raw.githubusercontent.com/OfficialSquidProductions/shot-reporter/main/updates/windows/feed.json'
HOSTS = {'raw.githubusercontent.com', 'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com'}
SPKI_PREFIX = bytes.fromhex('302a300506032b6570032100')
REPO = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda:stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, f'Duplicate JSON field: {key}')
            result[key] = value
        return result
    data = Path(path).read_bytes()
    require(len(data) <= 1024 * 1024, 'JSON input exceeds 1 MiB')
    return json.loads(data.decode('utf-8'), object_pairs_hook=unique)


def b64(value, length):
    raw = base64.b64decode(value, validate=True)
    require(len(raw) == length, f'Expected {length} public/signature bytes')
    return raw


def version(value):
    match = re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?', value)
    require(match is not None, 'Invalid release SemVer')
    pre = match[4].split('.') if match[4] else []
    require(not any(p.isdigit() and len(p) > 1 and p[0] == '0' for p in pre), 'Invalid prerelease number')
    return tuple(int(match[i]) for i in (1, 2, 3)), pre


def newer(a, b):
    an, ap = version(a); bn, bp = version(b)
    if an != bn:
        return an > bn
    if not ap or not bp:
        return bool(bp) and not ap
    for x, y in zip(ap, bp):
        if x != y:
            if x.isdigit() and y.isdigit():
                return int(x) > int(y)
            if x.isdigit() != y.isdigit():
                return not x.isdigit()
            return x > y
    return len(ap) > len(bp)


def msi_version(value):
    numbers, pre = version(value)
    require(not pre and numbers[0] <= 255 and numbers[1] <= 255 and numbers[2] <= 65535, 'Invalid MSI ProductVersion')
    return numbers


def identity(release):
    require(isinstance(release, dict), 'Release metadata must be an object')
    version(release['version']); msi_version(release['msiVersion'])
    require(type(release['build']) is int and release['build'] > 0, 'Invalid monotonic build')
    require(re.fullmatch('[0-9a-f]{40}', release['sourceCommit']) is not None, 'Release lacks an exact source commit')
    require(release.get('sourceDirty', False) is False, 'Dirty source cannot stage an update')
    require(release['platform'] == 'windows-x64', 'Release is not Windows x64')
    return release


def policy_check(policy, current, certificate):
    expected = {'feedURL', 'publicKey', 'allowedHosts', 'signerSHA256', 'currentVersion', 'currentBuild', 'defaultChannel', 'currentMSIVersion'}
    require(set(policy) == expected, 'Unexpected or missing policy fields')
    require(policy['feedURL'] == FEED_URL, 'Unexpected Shot Reporter feed URL')
    hosts = policy['allowedHosts']
    require(isinstance(hosts, list) and len(hosts) == len(set(hosts)) and set(hosts) == HOSTS, 'Review the exact GitHub HTTPS host set')
    public = b64(policy['publicKey'], 44)
    require(public.startswith(SPKI_PREFIX) and any(public[len(SPKI_PREFIX):]), 'Expected an X.509 Ed25519 public key')
    require(policy['signerSHA256'] == hashlib.sha256(certificate).hexdigest(), 'Bundled publisher pin does not match supplied public DER certificate')
    identity(current)
    require((policy['currentVersion'], policy['currentBuild'], policy['currentMSIVersion']) == (current['version'], current['build'], current['msiVersion']), 'Policy must identify the exact bootstrap/installed release')
    require(policy['defaultChannel'] in ('alpha', 'stable'), 'Invalid channel')
    return public


# No caller-supplied signature report is accepted. This runs only the OS PowerShell
# and reads the actual MSI, signer DER, certificate EKUs and installer properties.
NATIVE_PROBE = r'''
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.Encoding]::UTF8
foreach ($module in @('Microsoft.PowerShell.Management','Microsoft.PowerShell.Utility','Microsoft.PowerShell.Security')) {
 Import-Module (Join-Path $PSHOME ('Modules/'+$module+'/'+$module+'.psd1')) -ErrorAction Stop
}
$p=$env:SHOT_REPORTER_STAGE_MSI
$lock=[IO.File]::Open($p,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
try {
$s=Microsoft.PowerShell.Security\Get-AuthenticodeSignature -LiteralPath $p
if ($s.Status -ne 'Valid' -or -not $s.SignerCertificate -or -not $s.TimeStamperCertificate) {throw 'MSI needs a valid trusted Authenticode signature and timestamp'}
$engine=New-Object -ComObject WindowsInstaller.Installer
$db=$engine.OpenDatabase($p,0)
function Property($name) {$v=$db.OpenView(('SELECT `Value` FROM `Property` WHERE `Property` = '+"'"+$name+"'"));$v.Execute();return $v.Fetch().StringData(1)}
$ekus=@($s.SignerCertificate.Extensions | Where-Object {$_.Oid.Value -eq '2.5.29.37'} | ForEach-Object {$_.EnhancedKeyUsages | ForEach-Object {$_.Value}})
@{status=$s.Status.ToString();sha256=(Microsoft.PowerShell.Utility\Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant();size=(Get-Item -LiteralPath $p).Length;certificate=[Convert]::ToBase64String($s.SignerCertificate.RawData);subject=$s.SignerCertificate.Subject;issuer=$s.SignerCertificate.Issuer;ekus=$ekus;timestamped=$true;productName=(Property 'ProductName');productVersion=(Property 'ProductVersion');upgradeCode=(Property 'UpgradeCode');template=$db.SummaryInformation(0).Property(7)} | ConvertTo-Json -Compress -Depth 5
} finally {$lock.Dispose()}
'''


def native_probe(msi):
    require(os.name == 'nt', 'Production staging requires native Windows Authenticode/MSI validation; supplied verification claims are not a substitute')
    system = Path(os.environ['SystemRoot']) / 'System32' / 'WindowsPowerShell' / 'v1.0'
    env = dict(os.environ, SHOT_REPORTER_STAGE_MSI=str(Path(msi).resolve()), PSModulePath=str(system / 'Modules'))
    result = subprocess.run([str(system / 'powershell.exe'), '-NoProfile', '-NonInteractive', '-Command', NATIVE_PROBE], env=env, capture_output=True, encoding='utf-8-sig', timeout=90)
    require(result.returncode == 0, 'Native signature/MSI verification failed: ' + result.stderr[:2000])
    return json.loads(result.stdout)


def checksum_entry(path, name):
    matches = []
    for line in Path(path).read_text(encoding='utf-8-sig').splitlines():
        match = re.fullmatch(r'([0-9a-fA-F]{64}) [ *](.+)', line)
        require(match is not None, 'Malformed checksum list')
        if match[2] == name:
            matches.append(match[1].lower())
    require(len(matches) == 1, 'Checksum list must name the MSI exactly once')
    return matches[0]


def candidate_check(msi, release, checksums, certificate, expected_source, probe):
    identity(release)
    require(release['sourceCommit'] == expected_source, 'Target source commit differs from reviewed source')
    require('unsigned' not in release['distribution'].lower(), 'Unsigned release metadata cannot stage a production feed')
    require(msi.is_file() and not msi.is_symlink() and msi.suffix.lower() == '.msi', 'Expected an actual non-symlink MSI')
    require(0 < msi.stat().st_size <= 4_294_967_296, 'Invalid MSI size')
    actual = digest(msi)
    require(release['files'].get(msi.name) == actual and checksum_entry(checksums, msi.name) == actual, 'MSI bytes differ from release metadata or checksums')
    signer = hashlib.sha256(certificate).hexdigest()
    claim = release['windowsSigning']
    require(claim.get('authenticodeStatus') == 'Valid' and claim.get('timestamped') is True and claim.get('signerSHA256') == signer, 'Missing or mismatched signed-release publisher claims')
    require(probe['status'] == 'Valid' and probe['timestamped'] is True, 'Native trust/timestamp verification did not pass')
    require(probe['sha256'] == actual and probe['size'] == msi.stat().st_size, 'Native signature verification does not identify these exact MSI bytes')
    require(b64(probe['certificate'], len(certificate)) == certificate, 'Actual MSI signer differs from pinned public certificate')
    require('1.3.6.1.5.5.7.3.3' in probe['ekus'], 'Signer lacks code-signing EKU')
    require(not any(e.startswith('1.3.6.1.4.1.311.97.') for e in probe['ekus']), 'Azure Artifact Signing requires a reviewed durable-profile EKU policy; fixed leaf pins are incompatible')
    require(claim.get('publisher') == probe['subject'], 'Claimed publisher differs from actual signer')
    require(probe['productName'] == 'Shot Reporter' and probe['productVersion'] == release['msiVersion'], 'Actual MSI product/version differs from release metadata')
    require(probe['upgradeCode'].strip('{}').lower() == UPGRADE_CODE and probe['template'].split(';')[0] == 'x64', 'Actual MSI upgrade identity or architecture differs')
    return actual


def utc(value):
    parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    require(parsed.tzinfo is not None, 'Feed dates need a timezone')
    return parsed.astimezone(timezone.utc)


def time_check(generated, expires, now):
    g, e = utc(generated), utc(expires)
    require(g <= now + timedelta(minutes=5) and e > now and g < e <= g + timedelta(days=31), 'Feed dates are expired, future or exceed 31 days')


def output_directory(path):
    resolved = path.resolve()
    require(not resolved.is_relative_to(REPO), 'Stage outside the repository; this tool never activates bundled policy or a public feed')
    resolved.mkdir(parents=True, exist_ok=False)
    return resolved


def write_new(path, data):
    with path.open('xb') as stream:
        stream.write(data); stream.flush(); os.fsync(stream.fileno())


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')


def release_entry(target, policy, msi, notes, channel):
    name = 'Shot-Reporter-Windows-' + target['version'] + '.msi'
    require(msi.name == name, 'Unexpected release MSI filename')
    asset = {'url':f'https://github.com/OfficialSquidProductions/shot-reporter/releases/download/v{target["version"]}/{name}', 'size':msi.stat().st_size, 'sha256':digest(msi), 'upgradeCode':UPGRADE_CODE, 'signerSHA256':policy['signerSHA256'], 'msiVersion':target['msiVersion']}
    require(channel in ('alpha','stable') and (channel == 'alpha' or not version(target['version'])[1]), 'Invalid release channel')
    require(len(notes) <= 100000, 'Release notes exceed app limit')
    return {'version':target['version'], 'build':target['build'], 'channel':channel, 'releaseNotes':notes, 'windowsX64':asset}


def progression(current, target):
    require(newer(target['version'], current['version']) and target['build'] > current['build'] and msi_version(target['msiVersion']) > msi_version(current['msiVersion']), 'Target must increase SemVer, build and MSI version')


def payload_check(parsed, target, policy, msi, notes, channel, now):
    require(set(parsed) == {'schema','appID','generatedAt','expiresAt','releases'} and type(parsed['schema']) is int and parsed['schema'] == 1 and parsed['appID'] == APP_ID, 'Unexpected signed payload identity/schema')
    require(parsed['releases'] == [release_entry(target, policy, msi, notes, channel)], 'Payload does not describe the verified target MSI and notes')
    time_check(parsed['generatedAt'], parsed['expiresAt'], now)


def prepare(args):
    policy, current, target = read_json(args.policy), read_json(args.current_release), read_json(args.release)
    certificate = args.certificate.read_bytes()
    require(0 < len(certificate) < 65536, 'Expected public DER certificate bytes')
    policy_check(policy, current, certificate)
    progression(current, target)
    require(args.channel == 'alpha' or not version(target['version'])[1], 'Prereleases cannot enter stable')
    before = digest(args.msi)
    probe = native_probe(args.msi)
    actual = candidate_check(args.msi, target, args.checksums, certificate, args.source_sha, probe)
    require(actual == before, 'MSI changed during verification')
    now = datetime.now(timezone.utc)
    generated = now.isoformat().replace('+00:00', 'Z')
    expires = (now + timedelta(days=args.expires_days)).isoformat().replace('+00:00', 'Z')
    time_check(generated, expires, now)
    notes = args.notes.read_text(encoding='utf-8')
    require(len(notes) <= 100000, 'Release notes exceed app limit')
    payload = {'schema':1, 'appID':APP_ID, 'generatedAt':generated, 'expiresAt':expires, 'releases':[release_entry(target, policy, args.msi, notes, args.channel)]}
    require(len(json_bytes(payload)) <= 512 * 1024, 'Feed payload exceeds app limit')
    out = output_directory(args.output)
    write_new(out/'payload.json', json_bytes(payload)); write_new(out/'bootstrap-policy.json', json_bytes(policy))
    paths = {name:str(getattr(args, name).resolve()) for name in ('policy','current_release','release','certificate','checksums','msi','notes')}
    manifest = {'schema':1, 'stageOnly':True, 'sourceSHA':args.source_sha, 'channel':args.channel, 'inputs':paths, 'inputSHA256':{name:digest(path) for name,path in paths.items()}, 'payloadSHA256':digest(out/'payload.json'), 'policySHA256':digest(out/'bootstrap-policy.json'), 'nativeVerification':probe, 'provenance':'Source identity/checksums supplied by operator from reviewed CI; not an independent build attestation.'}
    write_new(out/'STAGING.json', json_bytes(manifest))
    print(out)


def verify_signature(public, payload, signature, openssl):
    require(len(signature) == 64, 'Expected raw 64-byte external Ed25519 signature')
    with tempfile.TemporaryDirectory(prefix='shot-reporter-public-verification-') as tmp:
        home = Path(tmp)
        for name, data in [('public.der',public),('payload',payload),('signature',signature)]:
            (home/name).write_bytes(data)
        result = subprocess.run([openssl, 'pkeyutl', '-verify', '-pubin', '-inkey', str(home/'public.der'), '-keyform', 'DER', '-rawin', '-in', str(home/'payload'), '-sigfile', str(home/'signature')], capture_output=True, timeout=30)
        require(result.returncode == 0, 'External Ed25519 signature is invalid (OpenSSL 3+ required)')


def finalize(args):
    manifest = read_json(args.stage/'STAGING.json')
    require(manifest['schema'] == 1 and manifest['stageOnly'] is True, 'Invalid staging record')
    paths = {name:Path(path) for name,path in manifest['inputs'].items()}
    require(all(digest(path) == manifest['inputSHA256'][name] for name,path in paths.items()), 'Staged inputs changed; prepare again')
    require(digest(args.stage/'payload.json') == manifest['payloadSHA256'] and digest(args.stage/'bootstrap-policy.json') == manifest['policySHA256'], 'Staged payload or public policy changed')
    policy, current, target = read_json(paths['policy']), read_json(paths['current_release']), read_json(paths['release'])
    public = policy_check(policy, current, paths['certificate'].read_bytes())
    progression(current, target)
    candidate_check(paths['msi'], target, paths['checksums'], paths['certificate'].read_bytes(), manifest['sourceSHA'], native_probe(paths['msi']))
    payload = (args.stage/'payload.json').read_bytes(); parsed = read_json(args.stage/'payload.json')
    payload_check(parsed, target, policy, paths['msi'], paths['notes'].read_text(encoding='utf-8'), manifest['channel'], datetime.now(timezone.utc))
    signature = b64(args.signature.read_text(encoding='ascii').strip(), 64)
    verify_signature(public, payload, signature, args.openssl)
    out = output_directory(args.output)
    write_new(out/'feed.json', json_bytes({'payload':base64.b64encode(payload).decode('ascii'), 'signature':base64.b64encode(signature).decode('ascii')}))
    write_new(out/'VALIDATION.json', json_bytes({'schema':1, 'stageOnly':True, 'published':False, 'sourceCommit':target['sourceCommit'], 'msiSHA256':digest(paths['msi']), 'feedSHA256':digest(out/'feed.json'), 'note':'Externally signed exact payload and native trusted MSI verified; installed signed older-to-newer acceptance is still required before publication.'}))
    print(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('prepare')
    for name in ('policy','current-release','release','certificate','checksums','msi','notes','output'):
        p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--source-sha', required=True); p.add_argument('--channel', choices=['alpha','stable'], default='alpha')
    p.add_argument('--expires-days', type=int, choices=range(1,32), default=14)
    p.set_defaults(action=prepare)
    p = sub.add_parser('finalize')
    for name in ('stage','signature','output'):
        p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--openssl', default='openssl'); p.set_defaults(action=finalize)
    args = parser.parse_args()
    try:
        args.action(args)
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f'Staging refused: {error}\n')


if __name__ == '__main__':
    main()
