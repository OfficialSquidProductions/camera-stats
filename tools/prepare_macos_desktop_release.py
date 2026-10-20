#!/usr/bin/env python3
"""Prepare an unsigned Mac arm64 candidate and explicit signing/source manifest.

Offline only: no account, Keychain, signing, notarization, upload or feed write.
FFmpeg is rebuilt from its pinned pristine source archive for macOS 14.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import json
import os
import plistlib
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

FFMPEG_SHA = '8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e'
JDK_SOURCE_SHA = '573057d03584ae793fb7ec9a14c76d826d9187a53efeefd99da47403a5308234'
FEED = 'https://raw.githubusercontent.com/OfficialSquidProductions/shot-reporter/main/updates/macos/appcast.xml'
MODULES = 'java.base,java.sql,java.logging,java.naming,jdk.unsupported,jdk.crypto.ec'
DOWNLOADER_BUNDLE = 'Contents/Frameworks/Sparkle.framework/Versions/B/XPCServices/Downloader.xpc'
DOWNLOADER_EXECUTABLE = DOWNLOADER_BUNDLE + '/Contents/MacOS/Downloader'

def macho_entitlement_profile(relative):
    if relative == 'Contents/Resources/DesktopMedia/runtime/bin/java': return 'java.plist'
    if relative == DOWNLOADER_EXECUTABLE: return 'downloader.plist'
    return None

CODEC_OPTIONS = [
    '--disable-everything', '--disable-autodetect', '--disable-network', '--disable-doc', '--disable-debug',
    '--enable-ffmpeg', '--enable-ffprobe', '--enable-protocol=file,pipe',
    '--enable-demuxer=mov,image2,matroska,rawvideo',
    '--enable-decoder=h264,mpeg4,mjpeg,png,tiff,ppm,rawvideo,ffv1,prores,aac,pcm_s16le',
    '--enable-parser=h264,mpeg4video', '--enable-encoder=png,rawvideo,mpeg4,ffv1',
    '--enable-muxer=image2,image2pipe,matroska,mp4,mov,rawvideo',
    '--enable-filter=select,showinfo,scale,format,crop,transpose', '--enable-swscale', '--enable-zlib',
    '--extra-cflags=-mmacosx-version-min=14.0', '--extra-ldflags=-mmacosx-version-min=14.0',
]


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            result.update(chunk)
    return result.hexdigest()


def run(args, *, cwd=None, env=None, log=None):
    if log:
        with Path(log).open('w') as stream:
            subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True, stdout=stream, stderr=subprocess.STDOUT)
    else:
        subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)


def capture(args, cwd=None):
    return subprocess.check_output(list(map(str, args)), cwd=cwd, text=True, stderr=subprocess.STDOUT).strip()


def extract_source(archive, destination):
    with tarfile.open(archive) as source:
        for entry in source.getmembers():
            path = Path(entry.name)
            if path.is_absolute() or '..' in path.parts or path.parts[0] != 'ffmpeg-9.0.2':
                raise RuntimeError('Unsafe pinned source archive path')
            if entry.issym() or entry.islnk():
                target = Path(entry.linkname)
                if target.is_absolute() or '..' in target.parts:
                    raise RuntimeError('Unsafe pinned source archive link')
            elif not entry.isfile() and not entry.isdir():
                raise RuntimeError('Unexpected pinned source archive entry')
        source.extractall(destination)


def macho_info(path):
    description = capture(['file', '-b', path])
    if 'Mach-O' not in description:
        return None
    architecture = capture(['lipo', '-archs', path])
    if 'arm64' not in architecture.split():
        raise RuntimeError('Packaged Mach-O lacks arm64: ' + path.name)
    build = capture(['vtool', '-show-build', path])
    minima = re.findall(r'\bminos\s+([0-9.]+)', build)
    if not minima:
        load = capture(['otool', '-l', path])
        minima = re.findall(r'cmd LC_VERSION_MIN_MACOSX\s+cmdsize \d+\s+version ([0-9.]+)', load)
    if not minima or any(tuple(map(int, value.split('.'))) > (14, 0, 0) for value in minima):
        raise RuntimeError('Missing or incompatible macOS minimum for ' + path.name + ': ' + str(minima))
    dependencies = capture(['otool', '-L', path])
    for dependency in dependencies.splitlines()[1:]:
        if not dependency.startswith('\t'): continue
        name = dependency.strip().split(' (', 1)[0]
        if name.startswith('/') and not name.startswith(('/System/Library/', '/usr/lib/')):
            raise RuntimeError('Unbundled absolute native dependency: ' + name)
    return {'architectures': architecture.split(), 'minimumOS': minima}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--ffmpeg-archive', type=Path, required=True)
    parser.add_argument('--jdk-source-archive', type=Path, required=True)
    parser.add_argument('--gradle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--app-input', type=Path, help='Existing unsigned Release app; omitted builds current Xcode project')
    parser.add_argument('--version', default='0.2.0-alpha.3')
    parser.add_argument('--build', type=int, default=5)
    parser.add_argument('--public-update-key', help='Public Ed25519 key only; never pass a private key')
    parser.add_argument('--unsandboxed-host', action='store_true', help='Explicit direct-distribution model; requires data-root compatibility acceptance')
    parser.add_argument('--allow-dirty', action='store_true', help='Review candidate only; manifest forbids public release until rebuilt clean')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.absolute()
    if output.exists() or output.is_symlink() or output.resolve().is_relative_to(root):
        parser.error('Output must be a fresh directory outside the source checkout')
    if not re.fullmatch(r'\d+\.\d+\.\d+-alpha\.\d+', args.version) or args.build < 3:
        parser.error('This alpha profile requires a 3-part alpha version and monotonic numeric build >=3')
    dirty = bool(capture(['git', 'status', '--porcelain'], cwd=root))
    if dirty and not args.allow_dirty:
        parser.error('Source is dirty; commit reviewed source or request a review-only --allow-dirty candidate')
    if args.public_update_key:
        try:
            key = base64.b64decode(args.public_update_key, validate=True)
        except ValueError:
            parser.error('Public update key must be canonical base64')
        if len(key) != 32 or not any(key):
            parser.error('Public update key must contain 32 nonzero Ed25519 public-key bytes')
    if digest(args.ffmpeg_archive) != FFMPEG_SHA or digest(args.jdk_source_archive) != JDK_SOURCE_SHA:
        parser.error('Corresponding source archive checksum mismatch')
    java = args.java_home.resolve(strict=True)
    release = (java / 'release').read_text()
    if 'OS_ARCH="aarch64"' not in release or 'JAVA_RUNTIME_VERSION="21.0.12.1+1-LTS"' not in release:
        parser.error('This profile requires the reviewed Temurin 21.0.12.1+1 Mac aarch64 JDK')
    output.mkdir(parents=True)
    logs = output / 'logs'; logs.mkdir()
    assets = output / 'source-assets'; assets.mkdir()
    env = dict(os.environ, JAVA_HOME=str(java), MACOSX_DEPLOYMENT_TARGET='14.0')
    run([args.gradle, '-p', root / 'clients', '-PskipAndroid=true', ':media-worker:installDist', '--offline', '--no-daemon', '--console=plain'], env=env, log=logs/'worker-build.log')
    if args.app_input:
        app_input = args.app_input.resolve(strict=True)
    else:
        derived = output / 'xcode-derived'
        run(['xcodebuild', '-project', root/'apple/CameraStats.xcodeproj', '-scheme', 'CameraStats', '-configuration', 'Release', '-destination', 'platform=macOS', '-derivedDataPath', derived, '-clonedSourcePackagesDirPath', root/'app/.build', '-disableAutomaticPackageResolution', '-onlyUsePackageVersionsFromResolvedFile', 'ARCHS=arm64', 'ONLY_ACTIVE_ARCH=YES', 'CODE_SIGNING_ALLOWED=NO', 'build'], env=env, log=logs/'app-build.log')
        app_input = derived/'Build/Products/Release/Shot Reporter.app'
    app = output/'Shot Reporter.app'
    shutil.copytree(app_input, app, symlinks=True)
    info_path = app/'Contents/Info.plist'; info = plistlib.loads(info_path.read_bytes())
    if info.get('CFBundleIdentifier') != 'org.squidproductions.camerastats':
        raise RuntimeError('Unexpected app identity')
    if str(info.get('CFBundleVersion')) != str(args.build) or info.get('CFBundleShortVersionString') not in {args.version, args.version.split('-',1)[0]}:
        raise RuntimeError('Build source must already have reviewed release version/build')
    info.update({'ShotReporterDistribution':'direct', 'ShotReporterReleaseVersion':args.version, 'ShotReporterUpdateChannel':'alpha', 'SURequireSignedFeed':True, 'SUSignedFeedFailureExpirationInterval':0, 'SUVerifyUpdateBeforeExtraction':True, 'SUAllowsAutomaticUpdates':False, 'SUEnableAutomaticChecks':True, 'SUAutomaticallyUpdate':False, 'SUEnableSystemProfiling':False, 'SUShowReleaseNotes':True, 'SUScheduledCheckInterval':86400})
    if args.public_update_key:
        info.update({'SUFeedURL':FEED, 'SUPublicEDKey':args.public_update_key})
    else:
        info.pop('SUFeedURL', None); info.pop('SUPublicEDKey', None)
    info_path.write_bytes(plistlib.dumps(info))
    worker = app/'Contents/Resources/DesktopMedia'
    if worker.exists():
        raise RuntimeError('App input already contains a media worker; use a fresh Xcode app')
    worker.mkdir(parents=True)
    shutil.copytree(root/'clients/media-worker/build/install/media-worker/lib', worker/'lib')
    run([java/'bin/jlink', '--add-modules', MODULES, '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=2', '--output', worker/'runtime'], log=logs/'jlink.log')
    native = worker/'native'; native.mkdir()
    sqlite_jars = list((worker/'lib').glob('sqlite-jdbc-*.jar'))
    if len(sqlite_jars) != 1:
        raise RuntimeError('Expected one reviewed SQLite JDBC dependency')
    with zipfile.ZipFile(sqlite_jars[0]) as archive:
        native_file = 'org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib'
        (native/'libsqlitejdbc.dylib').write_bytes(archive.read(native_file))
        repacked = sqlite_jars[0].with_suffix('.repacked')
        with zipfile.ZipFile(repacked,'w') as target:
            for entry in archive.infolist():
                if not entry.filename.startswith('org/sqlite/native/Mac/'):
                    target.writestr(entry,archive.read(entry.filename))
    repacked.replace(sqlite_jars[0])
    legal = worker/'legal'; legal.mkdir()
    (legal/'SQLITE_NATIVE_LAYOUT.txt').write_text('The SQLite JDBC JAR retains its Java classes, notices and license resources. Embedded Mac native copies were omitted during packaging. The unmodified Mac arm64 SQLite JNI library is staged separately at DesktopMedia/native/libsqlitejdbc.dylib and signed with this application. The Java worker selects that native directory explicitly, avoiding extraction of ad-hoc native libraries into a temporary directory. SQLite/xerial source and licensing references remain in OPEN_SOURCE_NOTICES.txt.\n')
    for source, name in [(root/'LICENSE','Shot-Reporter-MIT.txt'),(root/'THIRD_PARTY_NOTICES.md','THIRD_PARTY_NOTICES.md'),(root/'licenses/clients/OPEN_SOURCE_NOTICES.txt','OPEN_SOURCE_NOTICES.txt'),(root/'app/.build/checkouts/Sparkle/LICENSE','Sparkle-LICENSE.txt')]:
        shutil.copyfile(source, legal/name)
    with tempfile.TemporaryDirectory(prefix='shot-reporter-release-codecs-',dir='/tmp') as build:
        build = Path(build)
        extract_source(args.ffmpeg_archive, build)
        source = build/'ffmpeg-9.0.2'; prefix = build/'installed'
        clang = capture(['xcrun','--find','clang'])
        sdk = capture(['xcrun','--sdk','macosx','--show-sdk-path'])
        configure = ['./configure', '--prefix='+str(prefix), '--cc='+clang, '--sysroot='+sdk, '--host-cc=/usr/bin/clang', '--host-cflags=-isysroot '+sdk+' -mmacosx-version-min=14.0', '--host-ldflags=-isysroot '+sdk+' -mmacosx-version-min=14.0'] + CODEC_OPTIONS
        codec_env = {'PATH':'/usr/bin:/bin:/usr/sbin:/sbin', 'HOME':os.environ['HOME'], 'TMPDIR':os.environ.get('TMPDIR','/tmp'), 'LANG':'C', 'LC_ALL':'C', 'SDKROOT':sdk, 'MACOSX_DEPLOYMENT_TARGET':'14.0'}
        try:
            run(configure,cwd=source,env=codec_env,log=logs/'ffmpeg-configure.log')
        except subprocess.CalledProcessError:
            if (source/'ffbuild/config.log').is_file(): shutil.copyfile(source/'ffbuild/config.log',logs/'ffmpeg-config-debug.log')
            raise
        run(['make','-j',str(min(os.cpu_count() or 2,8))],cwd=source,env=codec_env,log=logs/'ffmpeg-build.log')
        run(['make','install'],cwd=source,env=codec_env,log=logs/'ffmpeg-install.log')
        shutil.copytree(prefix/'bin',worker/'bin')
        shutil.copyfile(source/'COPYING.LGPLv2.1',legal/'FFmpeg-LGPL-2.1.txt')
        codec_version = capture([worker/'bin/ffmpeg','-version'])
        if 'LGPL version 2.1 or later' not in (logs/'ffmpeg-configure.log').read_text() or '--enable-gpl' in codec_version or '--enable-nonfree' in codec_version:
            raise RuntimeError('FFmpeg license/profile mismatch')
    for asset in [args.ffmpeg_archive,args.jdk_source_archive]:
        shutil.copyfile(asset, assets/asset.name)
    metadata = args.jdk_source_archive.with_name(args.jdk_source_archive.name+'.json')
    if metadata.is_file():
        shutil.copyfile(metadata,assets/metadata.name)
    recipe = {'ffmpegVersion':'9.0.2','sourceURL':'https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz','sourceSHA256':FFMPEG_SHA,'sourceChanges':False,'configure':['./configure','--prefix=<new-private-output>','--cc=<xcrun clang>','--sysroot=<xcrun macOS SDK>','--host-cc=/usr/bin/clang','--host-cflags=-isysroot <xcrun macOS SDK> -mmacosx-version-min=14.0','--host-ldflags=-isysroot <xcrun macOS SDK> -mmacosx-version-min=14.0']+CODEC_OPTIONS,'compiler':capture(['xcrun','clang','--version']),'architecture':'arm64','minimumMacOS':'14.0','license':'LGPL-2.1-or-later','use':'separate local command-line programs; no vendor codec SDKs'}
    (assets/'FFmpeg-9.0.2-Mac-arm64-build.json').write_text(json.dumps(recipe,indent=2)+'\n')
    (legal/'CORRESPONDING_SOURCE.txt').write_text('This Mac arm64 release includes separate FFmpeg 9.0.2 LGPL-2.1-or-later command-line programs and a Temurin 21.0.12.1+1 OpenJDK runtime (GPL-2.0 with Classpath Exception). Original notices remain in runtime/legal and JAR resources. Complete corresponding FFmpeg and JDK source archives and the FFmpeg build recipe accompany this binary in the same GitHub release:\nhttps://github.com/OfficialSquidProductions/shot-reporter/releases/tag/v'+args.version+'\nSource assets must be published with the binary; a remote upstream link alone is not this release\'s source delivery. No vendor RAW runtime/helper or sample media is redistributed. Local modifications/rebuilds of the separate FFmpeg programs are supported from this source and recipe; production update signature checks remain enabled.\n')
    signing = output/'signing'; signing.mkdir()
    host_entitlements = {'com.apple.security.app-sandbox':True,'com.apple.security.files.user-selected.read-write':True,'com.apple.security.temporary-exception.mach-lookup.global-name':['org.squidproductions.camerastats-spks','org.squidproductions.camerastats-spki']} if not args.unsandboxed_host else {}
    java_entitlements = {'com.apple.security.cs.allow-jit':True}
    if not args.unsandboxed_host:
        java_entitlements.update({'com.apple.security.app-sandbox':True,'com.apple.security.inherit':True})
    (signing/'host.plist').write_bytes(plistlib.dumps(host_entitlements))
    (signing/'java.plist').write_bytes(plistlib.dumps(java_entitlements))
    # Sparkle supports this optional least-privilege Downloader configuration.
    # Installer remains unsandboxed so it can replace and relaunch the app.
    (signing/'downloader.plist').write_bytes(plistlib.dumps({'com.apple.security.app-sandbox':True,'com.apple.security.network.client':True}))
    objects = []
    for file in sorted(app.rglob('*')):
        if file.is_symlink():
            if not file.resolve().is_relative_to(app.resolve()):
                raise RuntimeError('Bundle symlink escapes distribution')
            continue
        if not file.is_file(): continue
        if file.suffix.lower() in ['.r3d','.braw','.sidecar'] or file.name.lower() in ['redline','redline.exe','blackmagicrawapi']:
            raise RuntimeError('Vendor media/runtime entered distribution')
        detail = macho_info(file)
        if detail:
            objects.append({'path':str(file.relative_to(app)), 'entitlements':macho_entitlement_profile(str(file.relative_to(app))), **detail})
    bundles = sorted([p for p in app.rglob('*') if p.is_dir() and not p.is_symlink() and p.suffix in ['.framework','.xpc','.app']],key=lambda p:len(p.parts),reverse=True)
    plan = {'identityTeam':'FZ298DXLS3','identifier':'org.squidproductions.camerastats','architecture':'arm64','hostSandboxed':not args.unsandboxed_host,'hardenedRuntime':True,'timestampRequired':True,'signingPerformed':False,'macho':sorted(objects,key=lambda x:len(Path(x['path']).parts),reverse=True),'nestedBundles':[str(p.relative_to(app)) for p in bundles],'nestedBundleEntitlements':{DOWNLOADER_BUNDLE:'downloader.plist'},'outerBundle':'Shot Reporter.app','outerEntitlements':'host.plist','instructions':'Coordinator must sign nested Mach-O and bundles from inside out, then outer host; no --deep signing. Retain original Sparkle helper entitlements where applicable. Verify each signature and final bundle; notarize, staple, then rearchive. Do not install candidate over owner app for QA.'}
    (output/'MAC_SIGNING_PLAN.json').write_text(json.dumps(plan,indent=2)+'\n')
    files = [{'name':p.name,'sha256':digest(p),'bytes':p.stat().st_size} for p in assets.iterdir() if p.is_file()]
    manifest = {'version':args.version,'build':args.build,'platform':'macOS 14+ Apple Silicon arm64','sourceCommit':capture(['git','rev-parse','HEAD'],cwd=root),'sourceDirty':dirty,'updaterConfigured':bool(args.public_update_key),'feedURL':FEED if args.public_update_key else None,'runtimeModules':MODULES,'jdkVersion':'Temurin21.0.12.1+1','ffmpeg':recipe,'sourceAssets':files,'unsigned':True,'notarized':False,'rawVendorRuntimeBundled':False,'downloaderSandboxed':True,'releaseReady':False,'releaseGates':['Reviewed clean source rebuild' if dirty else 'Final exact-source regression evidence','Coordinator signature/notarization/stapling','Actual signed app worker and external RAW tool acceptance','Existing report roots/preferences/media journal preservation','Published corresponding source assets','Real signed Sparkle older-to-newer upgrade/relaunch'+('' if args.public_update_key else ' and dedicated public-key/feed configuration')]}
    (worker/'DISTRIBUTION.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (output/'MAC_RELEASE_CANDIDATE.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({'app':str(app),'signingPlan':str(output/'MAC_SIGNING_PLAN.json'),'sourceAssets':str(assets),'releaseReady':False}))

if __name__ == '__main__':
    main()
