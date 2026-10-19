#!/usr/bin/env python3
"""Private SDK/sample acceptance checks; never download or publish footage."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time

parser=argparse.ArgumentParser()
parser.add_argument('--helper',type=Path,required=True)
parser.add_argument('--sample',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True,help='New private validation directory outside Git')
args=parser.parse_args()
repo=Path(__file__).resolve().parents[2]
out=args.output.absolute()
if out.exists() or out.resolve().is_relative_to(repo):
    parser.error('Output must be a new private directory outside the repository')
out.mkdir(parents=True)
helper=args.helper.resolve(strict=True)
source=args.sample.resolve(strict=True)
checks=[]
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def run(*arguments,success=True):
    result=subprocess.run([str(helper),*map(str,arguments)],capture_output=True,text=True,timeout=55)
    assert (result.returncode==0)==success,(arguments,result.returncode,result.stderr)
    if success:
        data=json.loads(result.stdout)
        assert str(source.parent) not in result.stdout,'No absolute source paths in JSON'
        return data
    assert not result.stdout.strip(),'Failed operation must not publish a successful JSON frame'
    return result.stderr
original=digest(source)
sidecar=source.with_suffix('.sidecar')
original_sidecar=digest(sidecar) if sidecar.exists() else None
version=run('version')
probe=run('probe',source)
assert probe['codec']=='braw' and probe['container']=='braw' and probe['readable']
assert probe['frames']>=1 and probe['width']>0 and probe['height']>0
checks.append('real SDK probe and bounded structured metadata')
frame0=out/'first.ppm'
frame=run('frame',source,0,frame0)
assert frame['index']==0 and frame['pts']==0
header=b'P6\n%d %d\n255\n'%(frame['width'],frame['height'])
image=frame0.read_bytes()
assert image.startswith(header) and len(image)==len(header)+frame['width']*frame['height']*3
assert len(set(image[len(header):len(header)+100000]))>32,'Not an empty or placeholder image'
checks.append('actual full-resolution RGB frame output and exact SDK frame index')
repeat=out/'repeat.ppm'
run('frame',source,0,repeat)
assert digest(frame0)==digest(repeat)
checks.append('deterministic repeated frame decode')
last=out/'last.ppm'
last_frame=run('frame',source,probe['frames']-1,last)
assert last_frame['index']==probe['frames']-1
checks.append('last available frame index (sample may have only one frame)')
for index in ['-1','+0','0.5','18446744073709551616',str(probe['frames'])]:
    target=out/('invalid-'+index.replace('/','_')+'.ppm')
    run('frame',source,index,target,success=False)
    assert not target.exists()
checks.append('negative, malformed, overflow and out-of-range indices refused')
protected=out/'protected.ppm'
protected.write_bytes(b'foreign owner bytes')
run('frame',source,0,protected,success=False)
assert protected.read_bytes()==b'foreign owner bytes'
checks.append('existing output never overwritten')
link=out/'source-link.braw'
link.symlink_to(source)
run('probe',link,success=False)
parent_link=out/'output-link'
parent_link.symlink_to(out,target_is_directory=True)
run('frame',source,0,parent_link/'escaped.ppm',success=False)
assert not (out/'escaped.ppm').exists()
checks.append('source and output-parent symlinks refused')
no_sidecar=out/'no-sidecar'
no_sidecar.mkdir()
copy=no_sidecar/'sample.braw'
shutil.copyfile(source,copy)
without_probe=run('probe',copy)
assert without_probe['tags']['sidecarAttached']=='false'
without=out/'without-sidecar.ppm'
run('frame',copy,0,without)
checks.append('decode without sidecar is explicit in metadata')
malformed=out/'malformed-sidecar'
malformed.mkdir()
bad=malformed/'sample.braw'
shutil.copyfile(source,bad)
bad.with_suffix('.sidecar').write_text('{ invalid sidecar fixture')
run('probe',bad,success=False)
checks.append('malformed sidecar refuses ambiguous rendering')
sidecar_effect=None
if sidecar.exists():
    changed=out/'changed-sidecar'
    changed.mkdir()
    altered=changed/'sample.braw'
    shutil.copyfile(source,altered)
    text=sidecar.read_text()
    if '"post_3dlut_mode": "Embedded"' in text:
        altered.with_suffix('.sidecar').write_text(text.replace('"post_3dlut_mode": "Embedded"','"post_3dlut_mode": "Sidecar"',1))
        target=out/'sidecar-lut.ppm'
        run('frame',altered,0,target)
        sidecar_effect=digest(target)!=digest(frame0)
        assert sidecar_effect,'Changed active sidecar LUT must affect rendered pixels'
        checks.append('sidecar LUT selection changes actual rendered pixels')
# Process cancellation must not write source media or save a sidecar.
interrupted=out/'cancelled.ppm'
process=subprocess.Popen([str(helper),'frame',str(source),'0',str(interrupted)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
time.sleep(0.02)
if process.poll() is None:
    process.terminate()
process.communicate(timeout=5)
assert digest(source)==original
assert (digest(sidecar) if sidecar.exists() else None)==original_sidecar
checks.append('cancelled native process preserves original clip and sidecar hashes')
summary={'sdk':version,'probe':probe,'checks':checks,'checkCount':len(checks),'frame0Sha256':digest(frame0),'sourceSha256':original,'sourceSidecarSha256':original_sidecar,'sidecarChangedPixels':sidecar_effect,'temporalValidation':('single-frame sample: multi-frame ordering/late-frame seeking not validated' if probe['frames']==1 else 'first and last indices decoded; physical camera/card acceptance still required'),'distribution':'private local validation only; no SDK or footage redistribution'}
(out/'results.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({'checksPassed':len(checks),'frames':probe['frames'],'sourceUnchanged':True,'results':str(out/'results.json')}))
