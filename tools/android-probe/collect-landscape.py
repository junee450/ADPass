#!/usr/bin/env python3
"""Bounded manual next-episode sampling, followed by read-only ad observation.

Auto gestures and OCR are never enabled. Stops if the layout or foreground changes.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT / 'scripts/adb.sh'
OUT = ROOT / 'artifacts/hongguo-landscape-a11y'
parser = argparse.ArgumentParser()
parser.add_argument('label')
parser.add_argument('--observe-only', action='store_true')
parser.add_argument('--skip-current', action='store_true', help='One manual swipe after operator verifies the visible continue prompt')
args = parser.parse_args()
if not args.label.replace('-', '').isalnum():
    raise SystemExit('Invalid label')
folder = OUT / args.label
folder.mkdir(parents=True, exist_ok=True)

def adb(*argv, data=None):
    return subprocess.check_output([str(ADB), *map(str, argv)], input=data, timeout=10)

def read():
    page = json.loads(adb('shell', 'run-as', 'io.adpass.probe', 'cat', 'files/latest.json'))
    if abs(time.time()*1000 - page['time']) > 2500 or page['auto']:
        raise RuntimeError('Stale observation or automatic gestures enabled')
    return page

def visible(page):
    w = page['window']
    return [n for n in page['nodes']+page.get('queries', []) if n['visible'] and w[0] <= n['bounds'][0] < n['bounds'][2] <= w[2]
            and w[1] <= n['bounds'][1] < n['bounds'][3] <= w[3]]

def is_ad(page):
    return page['kind'] != 'content' or any((n['text']+n['description']).strip() == '广告' for n in visible(page))

def save(name):
    page = read()
    (folder / (name+'.json')).write_text(json.dumps(page, ensure_ascii=False, indent=2))
    (folder / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    texts = [n['text']+n['description'] for n in visible(page) if n['text'] or n['description']]
    print(name, page['kind'], 'readMs=', round(page['readMs'], 2), texts[:12], flush=True)
    return page

adb('shell', 'run-as', 'io.adpass.probe', 'tee', 'files/command.txt', data=b'research-on\n')
time.sleep(1.1)
start_ms = int(time.time()*1000)
if args.skip_current:
    page = read()
    if page['window'] != [0, 0, 2340, 1080] or not is_ad(page):
        raise SystemExit('STOP: The verified advertisement is no longer foreground')
    save('before-manual-skip')
    adb('shell', 'input', 'swipe', 1170, 810, 1170, 270, 350)
    deadline = time.monotonic()+5
    while time.monotonic() < deadline:
        time.sleep(.3)
        if not is_ad(read()): break
    else: raise SystemExit('STOP: Advertisement did not exit after manual swipe')
if not args.observe_only:
    for step in range(1, 13):
        page = read()
        if page['window'] != [0, 0, 2340, 1080] or is_ad(page):
            break
        buttons = [n for n in visible(page) if n['clickable'] and n.get('id') == 'com.phoenix.read:id/ar1']
        if not buttons:
            adb('shell', 'input', 'tap', 1170, 540)
            time.sleep(1.1)
            page = read()
            if is_ad(page): break
            buttons = [n for n in visible(page) if n['clickable'] and n.get('id') == 'com.phoenix.read:id/ar1']
        if not buttons:
            save('unknown-player')
            raise SystemExit('STOP: Next episode control unavailable')
        save('before-next-%02d' % step)
        page = read()
        if is_ad(page): break
        buttons = [n for n in visible(page) if n['clickable'] and n.get('id') == 'com.phoenix.read:id/ar1']
        if not buttons: raise SystemExit('STOP: Next control disappeared before action')
        l, t, r, b = buttons[-1]['bounds']
        adb('shell', 'input', 'tap', (l+r)//2, (t+b)//2)
        time.sleep(.35)
        page = save('after-next-%02d' % step)
        if is_ad(page): break
        time.sleep(.5)
page = read()
if page['window'] != [0, 0, 2340, 1080] or not is_ad(page):
    raise SystemExit('STOP: No native landscape ad reached within bounded manual navigation')
print('AD REACHED; observing only, no skip or playback tap', flush=True)
start = time.monotonic()
for seconds in [0, 1, 2, 3, 5, 7, 9, 12, 16, 21, 27, 34, 42]:
    time.sleep(max(0, start+seconds-time.monotonic()))
    page = save('ad-%02ds' % seconds)
    if page['window'] != [0, 0, 2340, 1080]: break
    if seconds > 1 and not is_ad(page):
        print('AD EXIT OBSERVED; stopping observation', flush=True)
        break
for name in ['research-samples.ndjson', 'research-events.ndjson']:
    try:
        data = adb('shell', 'run-as', 'io.adpass.probe', 'cat', 'files/'+name)
    except subprocess.CalledProcessError:
        data = b''
    lines = [line for line in data.decode().splitlines() if json.loads(line)['time'] >= start_ms]
    (folder / name).write_text('\n'.join(lines)+'\n')
print('COLLECTION COMPLETE', folder, flush=True)
