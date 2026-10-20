#!/usr/bin/env python3
"""Sign an explicitly prepared local candidate with an existing Developer ID.
No key creation/export, account changes, notarization, upload or installation.
"""
import argparse
import json
from pathlib import Path
import plistlib
import re
import subprocess


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate',type=Path,required=True)
    parser.add_argument('--identity',required=True,help='Existing Developer ID certificate SHA-1 metadata')
    args=parser.parse_args()
    if not re.fullmatch('[A-Fa-f0-9]{40}',args.identity): parser.error('Use the confirmed existing certificate fingerprint')
    candidate=args.candidate.resolve(strict=True)
    plan=json.loads((candidate/'MAC_SIGNING_PLAN.json').read_text())
    app=candidate/'Shot Reporter.app'
    result=candidate/'MAC_SIGNING_RESULT.json'
    if result.exists(): parser.error('This candidate already has a signing result; prepare a fresh candidate')
    if plan['identityTeam']!='FZ298DXLS3' or plan['identifier']!='org.squidproductions.camerastats': parser.error('Unexpected signing plan identity')
    profiles=candidate/'signing'
    log=candidate/'logs/codesign.log'
    def path_for(relative):
        path=app/relative
        if not path.resolve(strict=True).is_relative_to(app.resolve()) or path.is_symlink(): raise RuntimeError('Signing path escaped candidate')
        return path
    def sign(path,profile=None):
        args=['codesign','--force','--sign',args_identity,'--options','runtime','--timestamp','--preserve-metadata=identifier']
        if profile is None:
            inspected=subprocess.run(['codesign','-d','--entitlements',':-',str(path)],capture_output=True)
            if inspected.stdout.startswith(b'<?xml'):
                rights=plistlib.loads(inspected.stdout)
                rights.pop('com.apple.security.get-task-allow',None)
                if rights:
                    profile=profiles/('preserved-'+str(len(signed))+'.plist')
                    profile.write_bytes(plistlib.dumps(rights))
        if profile is not None: args+=['--entitlements',str(profile)]
        args.append(str(path))
        with log.open('ab') as stream: subprocess.run(args,check=True,stdout=stream,stderr=subprocess.STDOUT)
        signed.append(str(path.relative_to(app)) if path!=app else 'Shot Reporter.app')
        print('Signed',signed[-1],flush=True)
    args_identity=args.identity
    signed=[]
    for item in plan['macho']:
        sign(path_for(item['path']),profiles/item['entitlements'] if item['entitlements'] else None)
    for relative in plan['nestedBundles']:
        sign(path_for(relative))
    sign(app,profiles/plan['outerEntitlements'])
    with log.open('ab') as stream:
        subprocess.run(['codesign','--verify','--deep','--strict','--verbose=2',str(app)],check=True,stdout=stream,stderr=subprocess.STDOUT)
        subprocess.run(['codesign','--verify','--strict','-R=anchor apple generic and identifier "org.squidproductions.camerastats" and certificate leaf[subject.OU] = "FZ298DXLS3"',str(app)],check=True,stdout=stream,stderr=subprocess.STDOUT)
    result.write_text(json.dumps({'signed':True,'identity':args_identity,'team':'FZ298DXLS3','objects':signed,'notarized':False,'stapled':False,'installed':False,'note':'No publication; coordinator must notarize/staple/rearchive and validate actual signed runtime/upgrade before activating feed.'},indent=2)+'\n')
    print(result)

if __name__=='__main__': main()
