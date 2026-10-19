#!/usr/bin/env python3
"""Pinned decoder tools in the temporary Windows CI runner only; never packaged/uploaded."""
from pathlib import Path
import hashlib,os,urllib.request,zipfile
url='https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-10-19-13-06/ffmpeg-n9.0.2-28-g669f5ab083-win64-lgpl-9.0.zip'
expected='24c4f2537f6804cd0223fd4da0212e65dabf7b84ae43c1095a0ca8fad9ec4606'
root=Path(os.environ['RUNNER_TEMP'])/'shot-reporter-media-tools';root.mkdir(exist_ok=False)
archive=root/'decoder.zip'
with urllib.request.urlopen(url,timeout=120) as response,archive.open('xb') as out:
    while chunk:=response.read(1024*1024):out.write(chunk)
if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise SystemExit('Pinned Windows decoder checksum mismatch; nothing executed.')
with zipfile.ZipFile(archive) as z:
    for name in ['ffmpeg.exe','ffprobe.exe']:
        entry=next(n for n in z.namelist() if n.endswith('/bin/'+name))
        (root/name).write_bytes(z.read(entry))
with Path(os.environ['GITHUB_ENV']).open('a') as env:
    env.write(f'SHOT_REPORTER_FFMPEG={root / "ffmpeg.exe"}\nSHOT_REPORTER_FFPROBE={root / "ffprobe.exe"}\n')
print(f'CI-only LGPL decoder asset verified: {expected}; no binary distribution or production codec certification.')
