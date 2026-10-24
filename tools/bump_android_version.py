"""Preview an explicit, guarded Android version update (not a release action)."""
import argparse
from contextlib import contextmanager, nullcontext
import difflib
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'version.properties'
MAX_VERSION_CODE = 2100000000
NAME = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)')


def version_name(value):
    if not NAME.fullmatch(value):
        raise ValueError('Version name must be canonical MAJOR.MINOR.PATCH (no suffix)')
    return tuple(int(part) for part in value.split('.'))


def version_code(value):
    if not re.fullmatch(r'[1-9][0-9]*', value) or not 1 <= int(value) <= MAX_VERSION_CODE:
        raise ValueError('Version code must be an integer from 1 to 2100000000')
    return int(value)


def plan(source, expected_code, expected_name, next_name):
    text = source.decode('utf-8')
    match = re.fullmatch(
        r'versionCode=(?P<code>[1-9][0-9]*)\r?\n'
        r'versionName=(?P<name>(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))(?:\r?\n)?',
        text,
    )
    if match is None:
        raise ValueError('Expected canonical versionCode/versionName lines in version.properties')
    current_code = version_code(match['code'])
    current_name = match['name']
    if current_code < 13:
        raise ValueError('Version code must not be below the published floor 13')
    if (current_code, current_name) != (version_code(expected_code), expected_name):
        raise ValueError('Expected current code/name do not match the target')
    version_name(expected_name)
    if next_name is None:
        next_name = current_name
    elif version_name(next_name) <= version_name(current_name):
        raise ValueError('Next version name must be greater than the current version name')
    if current_code == MAX_VERSION_CODE:
        raise ValueError('Version code increment would exceed 2100000000')
    for field, value in [('name', next_name), ('code', str(current_code + 1))]:
        text = text[:match.start(field)] + value + text[match.end(field):]
    return text.encode('utf-8')


def git(*arguments):
    environment = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
    environment['GIT_OPTIONAL_LOCKS'] = '0'
    result = subprocess.run(['git', '-C', str(ROOT), '-c', 'core.fsmonitor=false', *arguments],
                            capture_output=True, env=environment, timeout=15)
    if result.returncode:
        raise ValueError('Git check failed; target must be tracked in a worktree with HEAD')
    return result.stdout


def identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def read_clean_target():
    target = ROOT / TARGET
    info = target.lstat()
    if target.resolve() != target or not stat.S_ISREG(info.st_mode):
        raise ValueError('Target must be a regular file without symlink path components')
    source = target.read_bytes()
    if identity(target.lstat()) != identity(info):
        raise ValueError('Target changed while reading')
    if Path(git('rev-parse', '--show-toplevel').decode().strip()).resolve() != ROOT:
        raise ValueError('Tool must be in its repository root/tools directory')
    status = git('status', '--porcelain=v1', '--untracked-files=all', '--', TARGET)
    if status or source != git('show', 'HEAD:' + TARGET):
        raise ValueError('Target must be tracked and clean in both the index and worktree')
    return source, identity(info)


@contextmanager
def writer_lock():
    # --git-path is per-worktree, including when .git is a worktree pointer file.
    path = ROOT / git('rev-parse', '--git-path', 'bump-android-version.lock').decode().strip()
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        raise ValueError('Version writer lock exists; inspect another writer or interrupted run') from None
    try:
        os.close(descriptor)
        yield
    finally:
        path.unlink()


def atomic_write(target, source, result, original):
    descriptor, name = tempfile.mkstemp(prefix='.android-version-', dir=target.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(result)
            output.flush()
            os.fchmod(output.fileno(), stat.S_IMODE(original[2]))
            os.fsync(output.fileno())
        checked, current = read_clean_target()
        if current != original or checked != source:
            raise ValueError('Target changed during version planning')
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description=__doc__, allow_abbrev=False,
        epilog='Local helper only; read docs/versioning.md before a release version PR.',
    )
    parser.add_argument('--expected-code', required=True)
    parser.add_argument('--expected-name', required=True)
    parser.add_argument('--next-name', help='Optional strictly greater MAJOR.MINOR.PATCH')
    parser.add_argument('--write', action='store_true', help='Explicitly replace the checked target')
    args = parser.parse_args(argv)
    try:
        with writer_lock() if args.write else nullcontext():
            source, original = read_clean_target()
            result = plan(source, args.expected_code, args.expected_name, args.next_name)
            if args.write:
                atomic_write(ROOT / TARGET, source, result, original)
    except (OSError, UnicodeError, ValueError, subprocess.SubprocessError) as error:
        parser.exit(2, str(error) + '\n')
    print('Updated ' + TARGET if args.write else
          'Dry-run only; no files changed. Use --write after reviewing this diff.')
    print(''.join(difflib.unified_diff(source.decode().splitlines(True), result.decode().splitlines(True),
                                     fromfile=TARGET, tofile=TARGET)), end='')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
