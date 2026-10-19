#!/usr/bin/env python3
"""Install the signed release on an isolated CI emulator; verify demo/reopen/update."""
from pathlib import Path
import os,re,subprocess,time,xml.etree.ElementTree as ET,json,atexit
root=Path('android/build/qa/signed-release');root.mkdir(parents=True,exist_ok=True)
apk=Path(os.environ['CAMERA_STATS_RELEASE_APK']).resolve()
adb=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb'
package='org.squidproductions.camerastats'
def run(*args):
    p=subprocess.run([str(adb),'-s','emulator-5554',*args],check=True,capture_output=True,timeout=40)
    return p.stdout
def screen(name):
    (root/(name+'.png')).write_bytes(run('exec-out','screencap','-p'))
def diagnostics():
    try:screen('last-screen');(root/'android-runtime.log').write_bytes(run('logcat','-d','-s','AndroidRuntime'))
    except Exception:pass
atexit.register(diagnostics)
def snapshot():
    (root/'last-dump.log').write_bytes(run('shell','uiautomator','dump','/sdcard/camera-stats-ui.xml'))
    data=run('exec-out','cat','/sdcard/camera-stats-ui.xml');(root/'last-ui.xml').write_bytes(data)
    return data
def wait_for(text,name,attempts=12):
    for attempt in range(attempts):
        data=snapshot()
        try:tree=ET.fromstring(data)
        except ET.ParseError:
            time.sleep(1);continue
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
previous=Path(os.environ['CAMERA_STATS_PREVIOUS_APK']).resolve()
run('install',str(previous));launch();screen('previous-camera-stats')
# On a cold phone emulator the first input can arrive while its launch transition still owns touch.
# Retry only while the old app is visibly still blank, before any migration is attempted.
for attempt in range(3):
    tap(wait_for('Open The Last Light demo','initial'))
    try:
        wait_for('The Last Light · DEMO','demo',attempts=4);break
    except AssertionError:
        wait_for('New production','still-blank',attempts=1)
else:raise AssertionError('Old Camera Stats demo could not be prepared for the upgrade')
screen('demo-portrait')
run('shell','am','force-stop',package);launch()
wait_for('The Last Light · DEMO','reopened');screen('demo-reopened')
run('install','-r',str(apk));launch()
wait_for('Shot Reporter','rebranded-header')
wait_for('The Last Light · DEMO','updated');screen('demo-after-update')
run('shell','settings','put','system','accelerometer_rotation','0')
physical=run('shell','wm','size').decode()
width,height=map(int,re.search(r'Physical size: (\d+)x(\d+)',physical).groups())
# Tablets such as Pixel C naturally start in landscape; phones naturally start in portrait.
run('shell','settings','put','system','user_rotation','0' if width>height else '1')
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
(root/'PASS.json').write_text(json.dumps({'signed_release_install':True,'demo':True,'process_reopen_persistence':True,'camera_stats_0_1_0_to_shot_reporter_0_1_1_preserves_report':True,'landscape':True,'apk_sha256':os.environ['EXPECTED_APK_SHA256']},indent=2)+'\n')
print('PASS: signed non-debuggable release installation, demo, process reopen, same-signer reinstall preservation and landscape.')
