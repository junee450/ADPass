#!/usr/bin/env python3
"""Bounded manual next-episode test. Stops at an ad or unknown player layout."""
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT / 'scripts/adb.sh'
OUT = ROOT / 'artifacts/hongguo-landscape'

def adb(*args):
    return subprocess.check_output([str(ADB), *map(str, args)], timeout=10)

def read():
    page = json.loads(adb('shell', 'run-as', 'io.adpass.probe', 'cat', 'files/latest.json'))
    if abs(time.time()*1000 - page['time']) > 4000:
        raise RuntimeError('stale probe observation')
    return page

def ad(page):
    return page['kind'] != 'content' or any(n.get('visible') and ('跳过广告' in n['text'] or n['text']=='广告') for n in page['nodes'])

for step in range(1, 7):
    page = read()
    if page['window'] != [0, 0, 2340, 1080] or ad(page):
        print('STOP: ad or changed window', page['kind'], flush=True)
        break
    controls = [n for n in page['nodes'] if n.get('visible') and n.get('clickable') and n.get('id')=='com.phoenix.read:id/ar1']
    if not controls:
        adb('shell', 'input', 'tap', 1170, 540)
        time.sleep(1.15)
        page = read()
        controls = [n for n in page['nodes'] if n.get('visible') and n.get('clickable') and n.get('id')=='com.phoenix.read:id/ar1']
    if ad(page) or not controls:
        print('STOP: ad or next control unavailable', page['kind'], flush=True)
        break
    title = next((n['text'] for n in page['nodes'] if n.get('visible') and n.get('id')=='com.phoenix.read:id/k8'), '')
    left, top, right, bottom = controls[0]['bounds']
    adb('shell', 'input', 'tap', (left+right)//2, (top+bottom)//2)
    time.sleep(1.4)
    page = read()
    prefix = OUT / ('manual-next-%02d' % step)
    prefix.with_suffix('.json').write_text(json.dumps(page, ensure_ascii=False, indent=2))
    prefix.with_suffix('.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    print(step, title, '->', page['kind'], flush=True)
    if ad(page):
        print('STOP: inserted advertisement; no skip attempted', flush=True)
        break
    time.sleep(2)
