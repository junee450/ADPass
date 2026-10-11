#!/usr/bin/env python3
"""Read-only bounded validation of our private debuggable app build."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ADB = ROOT/'scripts/adb.sh'
parser = argparse.ArgumentParser()
parser.add_argument('label')
parser.add_argument('--seconds', type=int, default=15)
options = parser.parse_args()
if not options.label.replace('-', '').isalnum() or not 1 <= options.seconds <= 45:
    raise SystemExit('Invalid bounded observation')
out = ROOT/'artifacts/hongguo-landscape-badge'/options.label
out.mkdir(parents=True, exist_ok=True)
until = time.monotonic()+options.seconds
previous = None
with (out/'states.ndjson').open('w') as stream:
    while time.monotonic() < until:
        data = subprocess.check_output([str(ADB),'shell','run-as','io.adpass.app','cat','shared_prefs/adpass.xml'], timeout=10)
        values = {e.attrib['name']: e.attrib.get('value', e.text or '') for e in ET.fromstring(data)}
        logs = json.loads(values.get('logs','[]'))[:12]
        state = {key: values.get(key) for key in ['enabled','landscape-ocr','landscape-tried','status','total','last-action']}
        state['logs'] = logs
        if state != previous:
            previous = state.copy(); state['time'] = int(time.time()*1000)
            stream.write(json.dumps(state, ensure_ascii=False)+'\n'); stream.flush()
            prefix = str(state['time'])+'-'+str(state['status'])
            (out/(prefix+'.xml')).write_bytes(data)
            (out/(prefix+'.png')).write_bytes(subprocess.check_output([str(ADB),'exec-out','screencap','-p'], timeout=10))
            print({key: state[key] for key in ['time','status','total','landscape-tried','landscape-ocr']}, flush=True)
        time.sleep(.12)
print('DONE', out, flush=True)
