#!/usr/bin/env python3
"""Keep CI evidence in an owner-visible draft release, without metered Actions artifact storage.

Uses only the ephemeral job token supplied by GitHub. Never publishes the draft.
"""
import argparse,glob,hashlib,os,shutil,subprocess,tempfile,zipfile,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--label',required=True);p.add_argument('--evidence',action='append',default=[]);p.add_argument('--asset',action='append',default=[]);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
sha=os.environ['GITHUB_SHA'];repository=os.environ['GITHUB_REPOSITORY'];tag='field-clients-validation-'+sha[:12]
def gh(*args,check=True):
    for attempt in range(3):
        result=subprocess.run(['gh',*args,'--repo',repository],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        if result.returncode==0:break
        if not any(marker in result.stderr for marker in ['HTTP 502','HTTP 503','HTTP 504','connection reset','TLS handshake timeout','unexpected EOF']):break
        time.sleep(2*(attempt+1))
    if check and result.returncode:raise RuntimeError('GitHub draft operation failed: '+safe_error(result))
    return result
def safe_error(result):return result.stderr.replace(os.environ.get('GH_TOKEN','not-a-token'),'[redacted]')[-1800:]
existing=gh('release','view',tag,check=False)
if existing.returncode:
    result=gh('release','create',tag,'--draft','--target',sha,'--title','Shot Reporter development validation '+sha[:12],'--notes','Development build evidence only. Not a public platform release. Review tests and runtime validation before publication.',check=False)
    if result.returncode and gh('release','view',tag,check=False).returncode:raise RuntimeError('Could not create owner-visible draft validation release: '+safe_error(result))
with tempfile.TemporaryDirectory(prefix='camera-ci-evidence-') as temp:
    output=Path(temp);assets=[]
    archive=output/(a.label+'-evidence.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for pattern in a.evidence:
            for match in glob.glob(pattern):
                path=Path(match)
                for item in sorted(path.rglob('*')) if path.is_dir() else [path]:
                    if item.is_file() and not item.is_symlink():z.write(item,item.resolve().relative_to(root).as_posix())
    if archive.stat().st_size>22:assets.append(archive)
    for pattern in a.asset:
        for match in glob.glob(pattern):
            path=Path(match)
            if path.is_file():
                copied=output/(a.label+'-'+path.name);shutil.copyfile(path,copied);assets.append(copied)
    if not assets:print('No build evidence produced; job failure remains visible in its log.');raise SystemExit(0)
    checksums=output/(a.label+'-SHA256SUMS.txt');checksums.write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in assets));assets.append(checksums)
    result=gh('release','upload',tag,*map(str,assets),'--clobber')
    print('Saved development evidence to draft release '+tag+'; no public download was enabled.')
