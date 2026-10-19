#!/usr/bin/env python3
"""Validate pinned original notice texts and assemble the client resource. No network."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
folder=root/'licenses/clients'
sources=json.loads((folder/'sources.json').read_text())
parts=['Shot Reporter — OPEN-SOURCE NOTICES\n\nApplication source: MIT, Copyright (c) 2026 Squid Productions.\nNames and marks remain their owners’ trademarks.\n\nThis software is based in part on the work of the Independent JPEG Group.\nThe bundled Windows JVM retains its original license files in runtime/legal.\nOriginal JAR notice resources are also retained. Some notices describe optional components in the upstream native package.\n\nAPPLICATION LICENSE\n'+(root/'LICENSE').read_text()]
for name,record in sorted(sources.items()):
    data=(folder/name).read_bytes()
    assert hashlib.sha256(data).hexdigest()==record['sha256'],name
    parts.append('\n'+'='*72+'\n'+name+'\nSource: '+record['source']+'\n\n'+data.decode('utf-8'))
(folder/'OPEN_SOURCE_NOTICES.txt').write_bytes('\n'.join(parts).encode('utf-8'))
print('Verified and assembled',len(sources),'original notices.')
