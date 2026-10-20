#!/usr/bin/env python3
"""Compile the original adapter against an explicitly authorized local Mac SDK.
No downloads, SDK installation, signing, copying vendor runtime, or publication.
"""
import argparse
import json
from pathlib import Path
import platform
import plistlib
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--sdk',type=Path,required=True,help='Authorized extracted Blackmagic RAW SDK directory')
parser.add_argument('--output',type=Path,required=True,help='New private executable outside the repository')
parser.add_argument('--library-dir',type=Path,help='Already installed, authorized framework directory; defaults to SDK Mac/Libraries')
args=parser.parse_args()
if platform.system()!='Darwin':
    parser.error('This build script currently validates macOS only; obtain the Windows SDK/runtime for Windows builds.')
root=Path(__file__).resolve().parents[2]
sdk=args.sdk.resolve(strict=True)
output=args.output.absolute()
if not output.parent.is_dir() or output.exists() or output.is_symlink():
    parser.error('Output must be a new file in an existing private directory')
if output.resolve().is_relative_to(root):
    parser.error('Keep the local SDK-linked binary outside Git/repository storage')
include=sdk/'Mac'/'Include'
library=(args.library_dir or sdk/'Mac'/'Libraries').resolve(strict=True)
for file in [include/'BlackmagicRawAPI.h',include/'BlackmagicRawAPIDispatch.cpp',library/'BlackmagicRawAPI.framework'/'BlackmagicRawAPI']:
    if not file.exists():
        parser.error('The authorized SDK lacks required Mac headers, dispatch source, or API framework')
def version(directory):
    return plistlib.loads((directory/'BlackmagicRawAPI.framework/Resources/Info.plist').read_bytes()).get('CFBundleShortVersionString')
expected=version(sdk/'Mac/Libraries')
if not expected or version(library)!=expected:
    parser.error('The installed runtime version must match the supplied SDK; do not mix ABI versions')
subprocess.run(['xcrun','clang++','-std=c++17','-O2','-pthread','-fobjc-arc','-I',str(include),'-DSHOT_REPORTER_BRAW_LIBRARY_DEFAULT='+json.dumps(str(library)),'-DSHOT_REPORTER_BRAW_EXPECTED_VERSION='+json.dumps(expected),str(Path(__file__).with_name('shot_reporter_braw.mm')),str(include/'BlackmagicRawAPIDispatch.cpp'),'-framework','Foundation','-framework','CoreFoundation','-o',str(output)],check=True)
print(output)
