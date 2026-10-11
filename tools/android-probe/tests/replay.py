#!/usr/bin/env python3
"""Replay recorded emulator pages against the actual Java classifier."""
import base64
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
RECORDINGS = ROOT / 'artifacts/hongguo-retest'
JAVA = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
CASES = [(f'portrait-{i:02}.json', 'ad-waiting' if i in (2, 3, 4) else 'content') for i in range(1, 18)]
CASES += [('portrait-ad-ready.json', 'ad-ready'), ('landscape-ad-ready.json', 'ad-ready')]
CASES += [(f'feed-{i:02}.json', 'content') for i in range(1, 9)]
CASES += [(f'native-next-{i:02}.json', 'content') for i in range(1, 7)]
CASES += [('last-series-03.json', 'ad-ready'), ('after-auto-portrait.json', 'content')]
CASES += [('rotated-series-01.json', 'ad-ready'), ('after-auto-rotated.json', 'content')]
CASES += [('live-search-02.json', 'ad-waiting'), ('after-countdown-auto.json', 'content')]
for folder in ['ad01', 'ad02-fresh', 'ad03', 'ad05-entry', 'ad06-fresh']:
    for phase in ['ad-00s', 'ad-02s', 'ad-27s']:
        CASES.append(('../hongguo-landscape-a11y/' + folder + '/' + phase + '.json', 'content'))
CASES.append(('../hongguo-landscape-a11y/ad01/paused.json', 'content'))
for phase in ['before-next-01', 'after-next-01', 'before-next-02', 'after-next-02']:
    CASES.append(('../hongguo-landscape-a11y/ad06-fresh/' + phase + '.json', 'content'))
for name in ['manual-first/trial-01-before', 'manual-first/trial-01-after-1200ms',
             'fresh-three/trial-01-before', 'fresh-three/trial-01-after-1200ms',
             'fresh-three/trial-02-before', 'fresh-three/trial-02-after-3200ms',
             'countdown-later-ready']:
    CASES.append(('../hongguo-landscape-badge/' + name + '.json', 'content'))

def encode(text):
    return base64.b64encode(text.encode()).decode()

with tempfile.TemporaryDirectory(prefix='adpass-replay-') as temporary:
    out = Path(temporary)
    lines = []
    for name, expected in CASES:
        page = json.loads((RECORDINGS / name).read_text())
        window = page.get('window', [0, 0, 2340, 1080] if name.startswith('landscape') else [0, 0, 1080, 2340])
        lines.append(f'CASE\t{name}\t{expected}')
        for n in page['nodes']:
            l, t, r, b = n['bounds']
            if n.get('visible', True) and window[0] <= l < r <= window[2] and window[1] <= t < b <= window[3]:
                lines.append('CUE\t' + encode(n['text'] + n['description']) + '\t' + encode(n.get('id') or ''))
        lines.append('END')
    cases = out / 'cases.tsv'
    cases.write_text('\n'.join(lines))
    source = ROOT / 'tools/android-probe/src/io/adpass/probe/PageRules.java'
    harness = Path(__file__).with_name('RuleReplay.java')
    subprocess.run([str(JAVA / 'javac'), '--release', '11', '-d', str(out), str(source), str(harness)], check=True)
    subprocess.run([str(JAVA / 'java'), '-cp', str(out), 'io.adpass.probe.RuleReplay', str(cases)], check=True)
