"""Verify preserved runtime legal evidence and its byte-identical APK packaging."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LEGAL = ROOT / 'app/src/main/assets/legal'


def verify_inventory(current_path):
    frozen = json.loads((LEGAL / 'dependencies/inventory.json').read_text())
    current = json.loads(current_path.read_text())
    if frozen['lock_sha256'] != hashlib.sha256((ROOT / 'app/gradle.lockfile').read_bytes()).hexdigest():
        raise ValueError('Legal evidence no longer matches the runtime lock')
    if frozen['modules'] != current['modules']:
        raise ValueError('Resolved dependency evidence changed; review and refresh the legal bundle')
    for module in frozen['modules']:
        for artifact in module['artifacts']:
            for notice in artifact['notices']:
                actual = (LEGAL / 'dependencies' / (notice['sha256'] + '.txt')).read_bytes()
                if hashlib.sha256(actual).hexdigest() != notice['sha256']:
                    raise ValueError('Dependency notice bytes changed: ' + module['coordinate'])


def verify_apk(apk):
    with zipfile.ZipFile(apk) as archive:
        for path in LEGAL.rglob('*'):
            if path.is_file():
                name = 'assets/legal/' + path.relative_to(LEGAL).as_posix()
                if archive.read(name) != path.read_bytes():
                    raise ValueError('APK legal document mismatch: ' + name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', type=Path)
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    if not args.inventory and not args.apk:
        parser.error('Provide --inventory or --apk')
    if args.inventory:
        verify_inventory(args.inventory)
    if args.apk:
        verify_apk(args.apk)
    print('Legal evidence / packaging verification passed (not a certification)')
