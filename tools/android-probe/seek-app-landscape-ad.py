#!/usr/bin/env python3
"""Manual next taps with a read-only probe; the product alone handles ad gestures."""
import argparse
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT/'scripts/adb.sh'
parser = argparse.ArgumentParser()
parser.add_argument('label')
options = parser.parse_args()
if not options.label.replace('-', '').isalnum(): raise SystemExit('Invalid label')
out = ROOT/'artifacts/hongguo-landscape-badge'/options.label
out.mkdir(parents=True, exist_ok=True)
def adb(*args, data=None):
    return subprocess.check_output([str(ADB),*map(str,args)], input=data, timeout=10)
def read():
    p=json.loads(adb('shell','run-as','io.adpass.probe','cat','files/latest.json'))
    if abs(time.time()*1000-p['time']) > 2000 or p['auto'] or p['window'] != [0,0,2340,1080]:
        raise RuntimeError('Stale, automatic probe, or changed window')
    return p
def nodes(p):
    return [n for n in p['nodes']+p.get('queries',[]) if n['visible'] and 0 <= n['bounds'][0]<n['bounds'][2]<=2340
            and 0 <= n['bounds'][1]<n['bounds'][3]<=1080]
def ad(p):return any((n['text']+n['description']).strip()=='广告' for n in nodes(p))
def buttons(p):return [n for n in nodes(p) if n.get('id')=='com.phoenix.read:id/ar1' and n['clickable'] and n['bounds'][3]-n['bounds'][1]>=50]
def save(label,p):
    (out/(label+'.json')).write_text(json.dumps(p,ensure_ascii=False,indent=2))
    (out/(label+'.png')).write_bytes(adb('exec-out','screencap','-p'))
adb('shell','run-as','io.adpass.probe','tee','files/command.txt',data=b'research-on\n')
time.sleep(1.2)
found=False
for step in range(13):
    p=read()
    if ad(p):found=True;break
    if p['kind']!='content':raise RuntimeError('Unknown/countdown/live navigation page')
    if step==12:break
    until=time.monotonic()+1.6
    while not buttons(p) and not ad(p) and time.monotonic()<until:
        time.sleep(.25);p=read()
    if ad(p):found=True;break
    if not buttons(p):
        adb('shell','input','tap',1170,540);time.sleep(1.4);p=read()
    if ad(p):found=True;break
    if not buttons(p):
        save('unknown-player',p);raise RuntimeError('No confirmed next control')
    save('next-%02d'%step,p)
    p=read()
    if ad(p):found=True;break
    if not buttons(p):raise RuntimeError('Next control changed')
    l,t,r,b=buttons(p)[-1]['bounds']
    adb('shell','input','tap',(l+r)//2,(t+b)//2)
    until=time.monotonic()+1.8
    while time.monotonic()<until:
        time.sleep(.18);p=read()
        if ad(p):found=True;break
    if found:break
if not found:raise SystemExit('No new ad within 12 next taps')
save('ad-entry',p)
print('AD FOUND; no manual ad gesture, product handles it',p['time'],flush=True)
subprocess.run(['python3',str(ROOT/'tools/android-probe/watch-app-state.py'),options.label,'--seconds','20'],check=True)
