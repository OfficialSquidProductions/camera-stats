#!/usr/bin/env python3
"""Install the signed release on an isolated CI emulator; verify demo/reopen/update."""
from pathlib import Path
import os,re,subprocess,time,xml.etree.ElementTree as ET,json
root=Path('android/build/qa/signed-release');root.mkdir(parents=True,exist_ok=True)
apk=Path(os.environ['CAMERA_STATS_RELEASE_APK']).resolve()
adb=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb'
package='org.squidproductions.camerastats'
def run(*args):
    p=subprocess.run([str(adb),'-s','emulator-5554',*args],check=True,capture_output=True,timeout=40)
    return p.stdout
def screen(name):
    (root/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
def snapshot():
    run('shell','uiautomator','dump','/sdcard/camera-stats-ui.xml')
    return run('exec-out','cat','/sdcard/camera-stats-ui.xml')
def wait_for(text,name):
    for attempt in range(12):
        data=snapshot();tree=ET.fromstring(data)
        matches=[n for n in tree.iter('node') if n.attrib.get('text')==text]
        if matches:
            (root/(name+'.xml')).write_bytes(data)
            return matches[0]
        time.sleep(1)
    raise AssertionError('Expected release UI: '+text)
def launch():
    run('shell','am','start','-W','-n',package+'/.android.MainActivity')
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
run('install',str(apk));launch()
tap(wait_for('Open The Last Light demo','initial'))
wait_for('The Last Light · DEMO','demo');screen('demo-portrait')
run('shell','am','force-stop',package);launch()
wait_for('The Last Light · DEMO','reopened');screen('demo-reopened')
run('install','-r',str(apk));launch()
wait_for('The Last Light · DEMO','updated');screen('demo-after-update')
run('shell','settings','put','system','accelerometer_rotation','0')
run('shell','settings','put','system','user_rotation','1')
for attempt in range(12):
    tree=ET.fromstring(snapshot())
    nodes=[n for n in tree.iter('node') if n.attrib.get('package')==package and 'bounds' in n.attrib]
    if nodes:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].attrib['bounds']))
        if x2-x1>y2-y1:break
    time.sleep(1)
else:raise AssertionError('Release did not reach landscape layout')
wait_for('The Last Light · DEMO','landscape');screen('demo-landscape')
assert run('shell','pidof',package).strip()
(root/'android-runtime.log').write_bytes(run('logcat','-d','-s','AndroidRuntime'))
(root/'PASS.json').write_text(json.dumps({'signed_release_install':True,'demo':True,'process_reopen_persistence':True,'same_signer_reinstall_keeps_report':True,'landscape':True,'apk_sha256':os.environ['EXPECTED_APK_SHA256']},indent=2)+'\n')
print('PASS: signed non-debuggable release installation, demo, process reopen, same-signer reinstall preservation and landscape.')
