"""Inventory exact cached inputs after Gradle resolves a locked runtime graph.

Evidence only: this is NOT an automatic license-compliance or release approval.
It does not execute dependencies, extract archive paths, or inspect credentials.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

MAX_NOTICE_BYTES = 2 * 1024 * 1024


def locked_modules(lock_text, configuration):
    modules = []
    for line in lock_text.splitlines():
        if not line or line.startswith('#') or '=' not in line:
            continue
        coordinate, configurations = line.split('=', 1)
        if coordinate == 'empty' or configuration not in configurations.split(','):
            continue
        if not re.fullmatch(r'[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[A-Za-z0-9_.+-]+', coordinate):
            raise ValueError(f'Invalid module coordinate: {coordinate}')
        modules.append(coordinate)
    if not modules:
        raise ValueError(f'No locked modules for {configuration}')
    return sorted(set(modules))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def archive_notices(data, output, prefix=''):
    results = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in sorted(archive.infolist(), key=lambda e: e.filename):
            name = entry.filename
            if entry.is_dir():
                continue
            if any(word in name.lower() for word in ('license', 'notice', 'copying', 'copyright')):
                if entry.file_size > MAX_NOTICE_BYTES:
                    raise ValueError(f'Notice too large: {name}')
                content = archive.read(entry)
                sha = digest(content)
                # Never use untrusted archive paths as output filenames.
                (output / f'{sha}.txt').write_bytes(content)
                results.append({'entry': prefix + name, 'sha256': sha, 'bytes': len(content)})
            if not prefix and name == 'classes.jar':
                results.extend(archive_notices(archive.read(entry), output, 'classes.jar!/'))
    return results


def inventory(lock_path, cache, configuration, output):
    output.mkdir(parents=True, exist_ok=True)
    notices_dir = output / 'notices'
    notices_dir.mkdir(exist_ok=True)
    modules = []
    for coordinate in locked_modules(lock_path.read_text(), configuration):
        module_dir = cache.joinpath(*coordinate.split(':'))
        row = {'coordinate': coordinate, 'artifacts': [], 'poms': []}
        for path in sorted(module_dir.glob('*/*')):
            if path.suffix == '.pom':
                raw = path.read_bytes()
                ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
                tree = ET.fromstring(raw)
                licenses = [{key: item.findtext('m:' + key, default='', namespaces=ns)
                             for key in ('name', 'url')}
                            for item in tree.findall('m:licenses/m:license', ns)]
                row['poms'].append({'file': path.name, 'sha256': digest(raw), 'licenses': licenses})
            elif path.suffix in ('.aar', '.jar') and not path.name.endswith(('-sources.jar', '-javadoc.jar')):
                raw = path.read_bytes()
                row['artifacts'].append({'file': path.name, 'sha256': digest(raw),
                                         'notices': archive_notices(raw, notices_dir)})
        row['cached_evidence_present'] = bool(row['poms'] or row['artifacts'])
        modules.append(row)
    report = {'schema_version': 1, 'configuration': configuration,
              'lock_sha256': digest(lock_path.read_bytes()),
              'release_approved': False,
              'limitations': ['Cache evidence is not a resolved-artifact selection proof.',
                             'POM licenses may be inherited, incomplete, or refer to additional terms.',
                             'Missing embedded notices require upstream review, not automatic approval.',
                             'Reconcile with the final APK/AAB and retain all required attributions.'],
              'modules': modules}
    (output / 'inventory.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, default=Path('app/gradle.lockfile'))
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--configuration', default='optimizedRuntimeClasspath')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = inventory(args.lock, args.cache, args.configuration, args.output)
    missing = [row['coordinate'] for row in report['modules'] if not row['cached_evidence_present']]
    print(json.dumps({'modules': len(report['modules']), 'missing_cache_evidence': missing,
                      'release_approved': False}))
    if missing:
        raise SystemExit('Incomplete runtime cache evidence; see inventory.json')


if __name__ == '__main__':
    main()
