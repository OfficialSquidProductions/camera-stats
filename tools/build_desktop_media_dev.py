#!/usr/bin/env python3
"""Local Mac development app only. No signing account, release, website or upload operation."""
from pathlib import Path
import argparse,subprocess,os,shutil,json,hashlib
p=argparse.ArgumentParser();p.add_argument('--java-home',required=True);p.add_argument('--ffmpeg-bin',required=True);p.add_argument('--ffmpeg-source',required=True);p.add_argument('--gradle');p.add_argument('--output',required=True);p.add_argument('--worker-only',action='store_true');a=p.parse_args()
root=Path(__file__).resolve().parents[1];java=Path(a.java_home).resolve();codecs=Path(a.ffmpeg_bin).resolve();source=Path(a.ffmpeg_source).resolve();output=Path(a.output).resolve()
if output.exists():raise SystemExit('Output exists. Choose a fresh development output; no installed app is replaced.')
version=subprocess.check_output([str(codecs/'ffmpeg'),'-version'],text=True)
if '--enable-gpl' in version or '--enable-nonfree' in version or any(flag not in version for flag in ['--disable-network','--disable-autodetect','--disable-everything']):raise SystemExit('This development bundle requires the reviewed network-disabled LGPL FFmpeg configuration.')
if not version.startswith('ffmpeg version 9.0.2 ') or (source/'RELEASE').read_text().strip()!='9.0.2':raise SystemExit('The current development profile requires FFmpeg 9.0.2 tools and matching source.')
archive=source.parent/'ffmpeg-9.0.2.tar.xz';archive_hash=hashlib.sha256(archive.read_bytes()).hexdigest() if archive.is_file() else None
expected='8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e'
if archive_hash is not None and archive_hash!=expected:raise SystemExit('Local FFmpeg source archive differs from the reviewed checksum.')
env=dict(os.environ);env['JAVA_HOME']=str(java)
subprocess.run([a.gradle or str(root/'clients/gradlew'),'-p',str(root/'clients'),'-PskipAndroid=true',':media-worker:installDist','--no-daemon','--console=plain'],check=True,env=env)
if a.worker_only:worker=output
else:
 derived=output.parent/'xcode-derived'
 subprocess.run(['xcodebuild','-project',str(root/'apple/CameraStats.xcodeproj'),'-scheme','CameraStats','-configuration','Debug','-destination','platform=macOS','-derivedDataPath',str(derived),'-clonedSourcePackagesDirPath',str(root/'app/.build'),'-disableAutomaticPackageResolution','-onlyUsePackageVersionsFromResolvedFile','CODE_SIGNING_ALLOWED=NO','build'],check=True,env=env)
 app=derived/'Build/Products/Debug/Shot Reporter.app';output.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(app,output);worker=output/'Contents/Resources/DesktopMedia'
worker.mkdir(parents=True,exist_ok=True);shutil.copytree(root/'clients/media-worker/build/install/media-worker/lib',worker/'lib')
subprocess.run([str(java/'bin/jlink'),'--add-modules','java.base,java.sql,java.logging,java.naming,jdk.unsupported,jdk.crypto.ec','--strip-debug','--no-header-files','--no-man-pages','--compress=2','--output',str(worker/'runtime')],check=True)
(worker/'bin').mkdir();(worker/'legal').mkdir()
for name in ['ffmpeg','ffprobe']:shutil.copy2(codecs/name,worker/'bin'/name)
shutil.copyfile(source/'COPYING.LGPLv2.1',worker/'legal/FFmpeg-LGPL-2.1.txt')
shutil.copyfile(root/'licenses/clients/OPEN_SOURCE_NOTICES.txt',worker/'legal/OPEN_SOURCE_NOTICES.txt')
if not a.worker_only:
 sparkle_license=root/'app/.build/checkouts/Sparkle/LICENSE'
 if not sparkle_license.is_file():raise SystemExit('Pinned Sparkle license missing from resolved source; development bundle is incomplete.')
 shutil.copyfile(sparkle_license,worker/'legal/Sparkle-LICENSE.txt')
metadata={'development_only':True,'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'source_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip()),'ffmpeg':version,'ffmpeg_source':'https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz','ffmpeg_source_sha256':expected,'local_source_archive_verified':archive_hash==expected,'release_source_audit':'Not performed; local extracted source may have modifications. Production corresponding-source review remains required.','runtime':subprocess.check_output([str(java/'bin/java'),'-version'],stderr=subprocess.STDOUT,text=True),'scope':'Local desktop development; RAW decoders require separately installed vendor tools and explicit configuration. No vendor SDK/runtime is redistributed. No OCR/NLE certification or production update feed.'}
(worker/'DEVELOPMENT.json').write_text(json.dumps(metadata,indent=2)+'\n');print('Local development worker:',worker)
