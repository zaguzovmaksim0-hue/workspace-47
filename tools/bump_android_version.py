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
TARGET = 'app/build.gradle.kts'
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


def visible_kotlin(text):
    # A narrow lexical mask, not a Kotlin evaluator: preserve all source offsets.
    non_code = r'//[^\r\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''

    def hide(match):
        value = match[0]
        if value.startswith('/*') and '/*' in value[2:-2]:
            raise ValueError('Nested Kotlin comments require manual review')
        return ''.join(c if c in '\r\n' else ' ' for c in value)

    visible = re.sub(non_code, hide, text, flags=re.S)
    if re.search(r'/\*|\*/|["\']', visible):
        raise ValueError('Unsupported or unterminated Kotlin comment/string')
    depth = 0
    for brace in re.finditer(r'[{}]', visible):
        depth += 1 if brace[0] == '{' else -1
        if depth < 0:
            raise ValueError('Unbalanced Gradle braces')
    if depth:
        raise ValueError('Unbalanced Gradle braces')
    return visible


def block(visible, header):
    found = list(re.finditer(header, visible, re.M))
    if len(found) != 1:
        raise ValueError('Expected one explicit android/defaultConfig block')
    opening = visible.index('{', found[0].start(), found[0].end())
    depth = 1
    for brace in re.finditer(r'[{}]', visible[opening + 1:]):
        depth += 1 if brace[0] == '{' else -1
        if depth == 0:
            return opening, opening + 1 + brace.start()
    raise ValueError('Unbalanced Gradle block')


def same_depth(visible, opening, position):
    prefix = visible[opening + 1:position]
    return prefix.count('{') == prefix.count('}')


def plan(source, expected_code, expected_name, next_name):
    text = source.decode('utf-8')
    visible = visible_kotlin(text)
    if re.search(r'\bsetVersion(?:Code|Name)\b', visible):
        raise ValueError('Alternate version setters require manual review')
    android_open, android_close = block(visible, r'^android[ \t]*\{[ \t]*\r?$')
    default_open, default_close = block(visible, r'^    defaultConfig[ \t]*\{[ \t]*\r?$')
    if not same_depth(visible, -1, android_open):
        raise ValueError('android must be top-level')
    if not (android_open < default_open < default_close < android_close) or not same_depth(
            visible, android_open, default_open):
        raise ValueError('defaultConfig must be a direct child of android')
    matches = []
    for field, literal in [('versionCode', r'[0-9]+'), ('versionName', r'"[^"\r\n]+"')]:
        positions = list(re.finditer(r'\b' + field + r'\b', visible))
        if len(positions) != 1:
            raise ValueError('Expected one unambiguous ' + field + ' declaration')
        declaration = (r'(?m)^[ \t]*(?P<field>' + field
                       + r')[ \t]*=[ \t]*(?P<value>' + literal + r')[ \t]*\r?$')
        found = list(re.finditer(declaration, text))
        found = [match for match in found if match.start('field') == positions[0].start()]
        if len(found) != 1:
            raise ValueError('Expected a literal ' + field + ' declaration')
        position = positions[0].start()
        if not default_open < position < default_close or not same_depth(
                visible, default_open, position):
            raise ValueError(field + ' must be directly inside defaultConfig')
        matches.append(found[0])
    current_code = version_code(matches[0]['value'])
    current_name = matches[1]['value'][1:-1]
    if (current_code, current_name) != (version_code(expected_code), expected_name):
        raise ValueError('Expected current code/name do not match the target')
    version_name(expected_name)
    if version_name(next_name) <= version_name(current_name):
        raise ValueError('Next version name must be greater than the current version name')
    if current_code == MAX_VERSION_CODE:
        raise ValueError('Version code increment would exceed 2100000000')
    replacements = [(matches[0], str(current_code + 1)), (matches[1], '"' + next_name + '"')]
    for match, value in sorted(replacements, key=lambda row: row[0].start(), reverse=True):
        text = text[:match.start('value')] + value + text[match.end('value'):]
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
    parser.add_argument('--next-name', required=True)
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
