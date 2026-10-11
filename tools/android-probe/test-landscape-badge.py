#!/usr/bin/env python3
"""Bounded badge-only, one-swipe experiments. No OCR or auto-probe rules."""
import argparse
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT / 'scripts/adb.sh'
args = argparse.ArgumentParser()
args.add_argument('--trials', type=int, default=3)
args.add_argument('--label', default='manual')
options = args.parse_args()
if not 1 <= options.trials <= 5 or not options.label.replace('-', '').isalnum():
    raise SystemExit('Invalid bounded experiment')
OUT = ROOT / 'artifacts/hongguo-landscape-badge' / options.label
OUT.mkdir(parents=True, exist_ok=True)

def adb(*argv, data=None):
    return subprocess.check_output([str(ADB), *map(str, argv)], input=data, timeout=10)

def read():
    p = json.loads(adb('shell', 'run-as', 'io.adpass.probe', 'cat', 'files/latest.json'))
    if abs(time.time()*1000-p['time']) > 2000 or p['auto'] or p['window'] != [0, 0, 2340, 1080]:
        raise RuntimeError('Stale, automatic, or changed window: stop')
    return p

def nodes(p):
    return [n for n in p['nodes']+p.get('queries', []) if n['visible'] and 0 <= n['bounds'][0] < n['bounds'][2] <= 2340
            and 0 <= n['bounds'][1] < n['bounds'][3] <= 1080]

def badge(p):
    return any((n['text']+n['description']).strip() == '广告' and n['bounds'][0] >= 1521 and n['bounds'][3] <= 270 for n in nodes(p))

def next_buttons(p):
    return [n for n in nodes(p) if n.get('id') == 'com.phoenix.read:id/ar1' and n['clickable'] and n['bounds'][3]-n['bounds'][1] >= 50]

def await_controls():
    # Allow player entry animation to settle before assuming controls are hidden.
    until = time.monotonic()+1.6
    while True:
        p = read()
        if badge(p) or next_buttons(p) or time.monotonic() >= until: return p
        time.sleep(.25)

def save(prefix, p=None):
    p = p or read()
    (OUT/(prefix+'.json')).write_text(json.dumps(p, ensure_ascii=False, indent=2))
    (OUT/(prefix+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    return p

adb('shell', 'run-as', 'io.adpass.probe', 'tee', 'files/command.txt', data=b'research-on\n')
time.sleep(1.2)
results = []
for trial in range(1, options.trials+1):
    first_badge = None
    for step in range(13):
        p = read()
        if badge(p):
            first_badge = p['time']; break
        if p['kind'] != 'content':
            raise RuntimeError('Unknown/countdown/live page: stop manual navigation')
        if step == 12:
            raise RuntimeError('No advertisement within 12 manual next taps')
        buttons = next_buttons(p)
        if not buttons:
            p = await_controls()
            if badge(p): first_badge = p['time']; break
            buttons = next_buttons(p)
        if not buttons:
            adb('shell', 'input', 'tap', 1170, 540)
            time.sleep(1.4); p = read()
            if badge(p): first_badge = p['time']; break
            buttons = next_buttons(p)
        if not buttons:
            save('trial-%02d-unknown' % trial, p)
            raise RuntimeError('No confirmed next-episode control')
        save('trial-%02d-next-%02d' % (trial, step), p)
        p = read()
        if badge(p): first_badge = p['time']; break
        buttons = next_buttons(p)
        if not buttons: raise RuntimeError('Next control changed')
        l, t, r, b = buttons[-1]['bounds']
        adb('shell', 'input', 'tap', (l+r)//2, (t+b)//2)
        # Poll soon after next-episode input; do not wait for the ad to finish.
        until = time.monotonic()+1.8
        while time.monotonic() < until:
            time.sleep(.18); p = read()
            if badge(p): first_badge = p['time']; break
        if first_badge is not None: break
    p = save('trial-%02d-before' % trial)
    p = read()
    if not badge(p): raise RuntimeError('Advertisement changed before gesture')
    # This decision uses only the badge and landscape; no prompt text or pixels.
    attempt_ms = int(time.time()*1000)
    adb('shell', 'input', 'swipe', 1170, 810, 1170, 270, 350)
    observe_start = time.monotonic(); absent = []
    for delay in [.4, 1.2, 2.2, 3.2]:
        time.sleep(max(0, observe_start+delay-time.monotonic()))
        q = save('trial-%02d-after-%04dms' % (trial, int(delay*1000)))
        absent.append(not badge(q))
    success = all(absent[-2:])
    result = dict(trial=trial, first_badge_ms=first_badge, attempt_ms=attempt_ms,
                  badge_to_attempt_ms=attempt_ms-first_badge, ad_absent_observations=absent,
                  badge_exited=success, decision='exact upper-right ad badge, no OCR/no skip prompt')
    results.append(result)
    (OUT/'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))
    print(json.dumps(result, ensure_ascii=False), flush=True)
    if not success:
        print('Single trial failed; no repeated blind swipe. Leave ad for OCR investigation.', flush=True)
        break
print('DONE', OUT, flush=True)
