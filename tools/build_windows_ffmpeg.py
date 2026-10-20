#!/usr/bin/env python3
"""Build minimal standalone LGPL FFmpeg tools from pinned sources on a disposable Windows CI runner."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,tarfile,urllib.request
SOURCES={
 'ffmpeg-9.0.2.tar.xz':('https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz','8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e'),
 'zlib-1.3.2.tar.gz':('https://zlib.net/fossils/zlib-1.3.2.tar.gz','bb329a0a2cd0274d05519d61c667c062e06990d72e125ee2dfa8de64f0119d16')}
FLAGS=['--disable-everything','--disable-autodetect','--disable-network','--disable-doc','--disable-debug','--disable-x86asm','--enable-ffmpeg','--enable-ffprobe','--enable-protocol=file,pipe','--enable-demuxer=mov,image2,matroska','--enable-decoder=h264,mpeg4,mjpeg,png,tiff,ppm,rawvideo,ffv1,prores,aac,pcm_s16le','--enable-parser=h264,mpeg4video','--enable-encoder=png,rawvideo,mpeg4,ffv1','--enable-muxer=image2,image2pipe,matroska,mp4,mov,rawvideo','--enable-filter=select,showinfo,scale,format,crop,transpose','--enable-swscale','--enable-zlib','--enable-w32threads','--disable-pthreads']
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def posix(p):
 value=str(p.resolve()).replace('\\','/')
 return '/'+value[0].lower()+value[2:] if re.match(r'^[A-Za-z]:/',value) else value
def main():
 p=argparse.ArgumentParser();p.add_argument('--output',required=True);a=p.parse_args()
 if os.name!='nt':raise SystemExit('Build runs only on the disposable Windows runner.')
 out=Path(a.output).resolve();out.mkdir(exist_ok=False);source=out/'corresponding-source';source.mkdir()
 for name,(url,expected) in SOURCES.items():
  target=source/name
  with urllib.request.urlopen(url,timeout=90) as response,target.open('xb') as dest:shutil.copyfileobj(response,dest)
  if sha(target)!=expected:raise SystemExit(f'Official source hash mismatch: {name}; not extracted or executed.')
  with tarfile.open(target) as archive:archive.extractall(out/'source',filter='data')
 bash=Path('C:/msys64/usr/bin/bash.exe')
 if not bash.is_file():raise SystemExit('Expected preinstalled MSYS2 is missing; no alternative compiler is trusted automatically.')
 env=dict(os.environ,MSYSTEM='MINGW64',CHERE_INVOKING='yes',MSYS2_PATH_TYPE='inherit')
 def run(script):return subprocess.run([str(bash),'--login','-c',script],check=True,env=env,cwd=out,capture_output=False)
 # Toolchain only; its MSYS DLL must never appear in the shipped tools' dependencies.
 run('pacman -Sy --noconfirm --needed mingw-w64-x86_64-gcc make diffutils pkgconf')
 build=out/'build-codecs.sh';prefix=out/'prefix';resources=out/'resources/windows-x64/DesktopCodecs';(resources/'bin').mkdir(parents=True);(resources/'legal').mkdir()
 import shlex
 q=lambda path:shlex.quote(posix(path))
 configure=FLAGS+[f'--prefix={posix(prefix)}',f'--extra-cflags=-I{posix(prefix)}/include',f'--extra-ldflags=-L{posix(prefix)}/lib -static']
 script='set -euo pipefail\ncd '+q(out/'source/zlib-1.3.2')+'\nmake -f win32/Makefile.gcc -j4 libz.a\nmkdir -p '+q(prefix/'include')+' '+q(prefix/'lib')+'\ncp zlib.h zconf.h '+q(prefix/'include')+'/\ncp libz.a '+q(prefix/'lib')+'/\ncd '+q(out/'source/ffmpeg-9.0.2')+'\n./configure '+shlex.join(configure)+'\nmake -j4\nmake install\npacman -Q > '+q(out/'toolchain-packages.txt')+'\ngcc --version > '+q(out/'compiler-version.txt')+'\n'
 build.write_text(script,encoding='utf-8',newline='\n');run('bash '+q(build))
 allowed={'kernel32.dll','msvcrt.dll','ucrtbase.dll','user32.dll','advapi32.dll','shell32.dll','bcrypt.dll','ws2_32.dll','ole32.dll','ntdll.dll','secur32.dll','crypt32.dll','rpcrt4.dll','gdi32.dll','winmm.dll','version.dll'}
 dependencies={}
 for name in ['ffmpeg.exe','ffprobe.exe']:
  binary=prefix/'bin'/name;shutil.copy2(binary,resources/'bin'/name)
  result=subprocess.check_output([str(bash),'--login','-c','objdump -p '+q(binary)],text=True,env=env)
  dlls=re.findall(r'DLL Name:\s*(\S+)',result);dependencies[name]=dlls
  if not dlls or any(d.lower() not in allowed and not d.lower().startswith(('api-ms-win-','ext-ms-win-')) for d in dlls):raise SystemExit(f'Unreviewed redistributed DLL dependency: {dlls}')
 version=subprocess.check_output([str(resources/'bin/ffmpeg.exe'),'-version'],text=True)
 if '--enable-gpl' in version or '--enable-nonfree' in version or '--disable-network' not in version or '--disable-autodetect' not in version:raise SystemExit('FFmpeg license/profile differs; refusing distribution.')
 shutil.copy2(out/'source/ffmpeg-9.0.2/COPYING.LGPLv2.1',resources/'legal/FFmpeg-LGPL-2.1.txt')
 shutil.copy2(out/'source/zlib-1.3.2/LICENSE',resources/'legal/zlib-LICENSE.txt')
 notices=Path('C:/msys64/mingw64/share/licenses')
 if not notices.is_dir():raise SystemExit('MinGW toolchain license notices missing.')
 shutil.copytree(notices,resources/'legal/MinGW-toolchain-notices')
 shutil.copy2(build,source/'build-codecs.sh');shutil.copy2(Path(__file__),source/'build_windows_ffmpeg.py')
 for name in ['toolchain-packages.txt','compiler-version.txt']:shutil.copy2(out/name,source/name)
 metadata={'schema':1,'source':{name:{'url':url,'sha256':digest} for name,(url,digest) in SOURCES.items()},'configuration':configure,'versionOutput':version,'binaries':{name:sha(resources/'bin'/name) for name in ['ffmpeg.exe','ffprobe.exe']},'windowsDLLDependencies':dependencies,'license':'FFmpeg LGPL-2.1-or-later; zlib license; MinGW runtime notices retained. Separate CLI executables; application does not link FFmpeg libraries.','sourcePolicy':'Unmodified hash-verified official source archives accompany this release. No vendor SDK, runtime or media is included.'}
 (resources/'BUILD.json').write_text(json.dumps(metadata,indent=2)+'\n');(source/'BUILD.json').write_text(json.dumps(metadata,indent=2)+'\n')
 shutil.make_archive(str(out/'Shot-Reporter-Windows-Codec-Sources'),'zip',source)
 with Path(os.environ['GITHUB_ENV']).open('a') as f:f.write(f'SHOT_REPORTER_CODEC_RESOURCES={out / "resources"}\nSHOT_REPORTER_FFMPEG={resources / "bin/ffmpeg.exe"}\nSHOT_REPORTER_FFPROBE={resources / "bin/ffprobe.exe"}\n')
 print('Built source-audited minimal Windows LGPL profile; OS-only DLL dependencies verified. Native media/runtime tests must still pass.')
if __name__=='__main__':main()
