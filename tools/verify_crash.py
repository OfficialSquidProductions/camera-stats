#!/usr/bin/env python3
"""Prove committed SQLite data survives killing a separate native process. macOS test host only."""
from pathlib import Path
import json, os, signal, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='camerastats-crash-') as tmp:
    tmp=Path(tmp)
    executable=tmp/'CrashCommitProbe'
    command=['swiftc','-module-cache-path',str(tmp/'module-cache'),'-I',str(ROOT/'app/Sources/CSQLite')]
    command += [str(p) for p in sorted((ROOT/'app/Sources/CameraStatsCore').glob('*.swift'))]
    command += [str(ROOT/'tools/CrashCommitProbe.swift'),'-o',str(executable)]
    subprocess.run(command,check=True)
    killed=subprocess.run([str(executable),'commit-and-kill',str(tmp/'fixture')],capture_output=True)
    assert killed.returncode == -signal.SIGKILL, f'Unexpected crash fixture result: {killed.returncode}'
    result=json.loads(subprocess.check_output([str(executable),'read',str(tmp/'fixture')]))
    assert result == {'documents':1,'entries':3,'title':'Committed before SIGKILL'},result
    print(json.dumps({'result':'PASS','committed_report_survived_SIGKILL':True,'entries_recovered':3,'uses_app_control':False}))
