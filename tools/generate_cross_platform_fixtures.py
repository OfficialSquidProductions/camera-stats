#!/usr/bin/env python3
"""Compile the released Apple core unchanged and generate cross-platform contract fixtures."""
from pathlib import Path
import subprocess,shutil
root=Path(__file__).resolve().parents[1]
out=Path('/tmp/camerastats-fixture-tool')
sources=[root/'app/Sources/CameraStatsCore'/f for f in ['Model.swift','Demo.swift','Exchange.swift','Reports.swift']]
subprocess.run(['xcrun','swiftc','-module-cache-path','/tmp/camerastats-swift-module-cache',*[str(p) for p in sources],str(root/'tools/fixtures/CameraStatsFixtureTool.swift'),'-o',str(out)],check=True)
subprocess.run([str(out),'generate',str(root/'fixtures/camreport')],check=True)
# The untouched demo remains precisely the shared fixture; golden transport cases add flags/Unicode separately.
