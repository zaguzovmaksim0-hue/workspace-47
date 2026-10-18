"""Exercise the version CLI against disposable Git repositories, never the app."""
from contextlib import redirect_stderr
import importlib.util
import io
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = '''// Keep unrelated configuration and UTF-8: versión.
android {
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.junta.firmamobile"
        versionCode = 13
        versionName = "0.2.11"
        minSdk = 26
    }
    buildTypes.create("qa") {
        versionNameSuffix = "-qa"
    }
}
'''.encode('utf-8')


class AndroidVersionBumpTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.target = self.root / 'app/build.gradle.kts'
        self.target.parent.mkdir()
        self.target.write_bytes(FIXTURE)
        self.tool = self.root / 'tools/bump_android_version.py'
        self.tool.parent.mkdir()
        shutil.copyfile(ROOT / 'tools/bump_android_version.py', self.tool)
        self.git('init', '-q', '--template=')
        self.git('config', 'user.name', 'Version fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.commit_target()

    def git(self, *arguments):
        return subprocess.run(
            ['git', '-C', str(self.root), '-c', 'core.hooksPath=/dev/null',
             '-c', 'commit.gpgsign=false', *arguments],
            check=True, capture_output=True, text=True,
        )

    def commit_target(self):
        self.git('add', '--', 'app/build.gradle.kts')
        self.git('commit', '-qm', 'Synthetic version fixture')

    def run_cli(self, *extra, code='13', name='0.2.11', next_name='0.2.12'):
        return subprocess.run(
            [sys.executable, str(self.tool), '--expected-code', code,
             '--expected-name', name, '--next-name', next_name, *extra],
            cwd=self.root, capture_output=True, text=True,
        )

    def target_state(self):
        info = self.target.stat()
        return self.target.read_bytes(), stat.S_IMODE(info.st_mode), info.st_mtime_ns, info.st_ino

    def test_default_preview_does_not_change_bytes_mode_or_mtime(self):
        before = self.target_state()
        result = self.run_cli()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('+        versionCode = 14\n', result.stdout)
        self.assertIn('+        versionName = "0.2.12"\n', result.stdout)
        self.assertIn('dry-run', result.stdout.lower())
        self.assertEqual(before, self.target_state())

    def test_explicit_write_replaces_only_the_literals_atomically_and_preserves_mode(self):
        self.target.chmod(0o640)
        old_inode = self.target.stat().st_ino
        result = self.run_cli('--write')
        self.assertEqual(0, result.returncode, result.stderr)
        expected = FIXTURE.replace(b'versionCode = 13', b'versionCode = 14').replace(
            b'versionName = "0.2.11"', b'versionName = "0.2.12"')
        self.assertEqual(expected, self.target.read_bytes())
        self.assertEqual(0o640, stat.S_IMODE(self.target.stat().st_mode))
        self.assertNotEqual(old_inode, self.target.stat().st_ino)
        self.assertEqual(['build.gradle.kts'], [p.name for p in self.target.parent.iterdir()])

    def test_dirty_target_is_rejected_even_in_preview(self):
        self.target.write_bytes(FIXTURE + b'// An unrelated local target edit.\n')
        before = self.target_state()
        for arguments in [(), ('--write',)]:
            result = self.run_cli(*arguments)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('clean', result.stderr.lower())
            self.assertEqual(before, self.target_state())

    def test_literals_outside_the_single_default_config_are_rejected(self):
        self.target.write_bytes(FIXTURE.replace(b'defaultConfig {', b'buildFeatures {'))
        self.commit_target()
        before = self.target_state()
        result = self.run_cli('--write')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, self.target_state())

    def test_existing_writer_lock_fails_closed_without_removing_another_lock(self):
        name = self.git('rev-parse', '--git-path', 'bump-android-version.lock').stdout.strip()
        lock = self.root / name
        lock.write_bytes(b'Another writer or interrupted run.\n')
        before = self.target_state()
        result = self.run_cli('--write')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('lock', result.stderr.lower())
        self.assertEqual(before, self.target_state())
        self.assertEqual(b'Another writer or interrupted run.\n', lock.read_bytes())
        self.assertEqual(0, self.run_cli().returncode)

    def assert_rejected_unchanged(self, *arguments, **options):
        before = self.target_state()
        result = self.run_cli(*arguments, **options)
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertEqual(before, self.target_state())
        self.assertEqual(['build.gradle.kts'], [p.name for p in self.target.parent.iterdir()])
        return result

    def test_android_inside_an_uninvoked_function_is_not_the_target(self):
        self.target.write_bytes(b'fun configureLater() {\n' + FIXTURE + b'}\n')
        self.commit_target()
        self.assert_rejected_unchanged('--write')

    def test_stale_expectations_fail_without_modification(self):
        for options in [dict(code='12'), dict(code='14'), dict(name='0.2.10'), dict(name='0.2.12')]:
            for arguments in [(), ('--write',)]:
                with self.subTest(options=options, arguments=arguments):
                    self.assert_rejected_unchanged(*arguments, **options)

    def test_names_are_explicit_canonical_stable_and_strictly_increasing(self):
        for name in ['0.2.11', '0.2.9', '0.1.99', '0.2.011', '01.2.12', '0.2',
                     '0.2.12-rc01', '0.2.12+build', 'v0.2.12', '', '0.2.12\n', '0.٢.12']:
            with self.subTest(name=name):
                self.assert_rejected_unchanged('--write', next_name=name)

    def test_codes_are_canonical_positive_android_integers(self):
        for code in ['0', '-1', '+13', '013', '13.0', '2_100_000_000', '2100000001', '١٣', '13\n']:
            with self.subTest(code=code):
                self.assert_rejected_unchanged('--write', code=code)

    def test_maximum_code_overflow_is_rejected_and_boundary_increment_is_allowed(self):
        self.target.write_bytes(FIXTURE.replace(b'versionCode = 13', b'versionCode = 2100000000'))
        self.commit_target()
        self.assert_rejected_unchanged('--write', code='2100000000')
        self.target.write_bytes(FIXTURE.replace(b'versionCode = 13', b'versionCode = 2099999999'))
        self.commit_target()
        result = self.run_cli('--write', code='2099999999')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(b'versionCode = 2100000000\n', self.target.read_bytes())

    def test_name_order_is_numeric_not_lexical_and_is_not_derived_from_code(self):
        self.target.write_bytes(FIXTURE.replace(b'0.2.11', b'0.9.99'))
        self.commit_target()
        result = self.run_cli('--write', name='0.9.99', next_name='0.10.0')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(b'versionCode = 14\n', self.target.read_bytes())
        self.assertIn(b'versionName = "0.10.0"\n', self.target.read_bytes())

    def test_duplicate_missing_dynamic_and_malformed_declarations_fail_closed(self):
        examples = [
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 13\n        versionCode = 13'),
            FIXTURE.replace(b'versionName = "0.2.11"', b'versionName = "0.2.11"\n        versionName = "0.2.11"'),
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 12 + 1'),
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 13L'),
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 013'),
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 0'),
            FIXTURE.replace(b'versionCode = 13', b'versionCode = 2100000001'),
            FIXTURE.replace(b'versionCode = 13', b'setVersionCode(13)'),
            FIXTURE.replace(b'        versionName = "0.2.11"\n', b''),
            FIXTURE.replace(b'versionName = "0.2.11"', b'versionName = "0.2.11" + suffix'),
            FIXTURE.replace(b'versionName = "0.2.11"', b'versionName = "$name"'),
            FIXTURE.replace(b'versionName = "0.2.11"', b'versionName = "0.2.011"'),
            FIXTURE.replace(b'        versionCode', b'/*\n        versionCode').replace(b'        minSdk', b'*/\n        minSdk'),
            FIXTURE + b'androidComponents.onVariants { it.outputs.first().versionCode.set(14) }\n',
            FIXTURE.replace(b'    defaultConfig {', b'    defaultConfig {\n        if (true) {').replace(b'        minSdk = 26', b'        }\n        minSdk = 26'),
            FIXTURE + b'android {\n    defaultConfig {\n    }\n}\n',
            FIXTURE + b'/* unterminated comment',
            FIXTURE.replace(b'    defaultConfig {', b'    defaultConfig { /* nested /* unsupported */ */'),
            FIXTURE + b'\xff',
        ]
        for number, source in enumerate(examples):
            with self.subTest(example=number):
                self.target.write_bytes(source)
                self.commit_target()
                self.assert_rejected_unchanged('--write')

    def test_comments_and_raw_strings_do_not_become_declarations_or_change(self):
        prefix = b'/* versionCode = 99; versionName = "9.9.9"; { } */\n'
        prefix += b'val documentation = """\nandroid {\n    defaultConfig {\n        versionCode = 88\n        versionName = "8.8.8"\n    }\n}\n"""\n'
        source = prefix + FIXTURE
        self.target.write_bytes(source)
        self.commit_target()
        result = self.run_cli('--write')
        self.assertEqual(0, result.returncode, result.stderr)
        expected = prefix + FIXTURE.replace(b'versionCode = 13', b'versionCode = 14').replace(
            b'versionName = "0.2.11"', b'versionName = "0.2.12"')
        self.assertEqual(expected, self.target.read_bytes())

    def test_crlf_and_unrelated_bytes_are_preserved(self):
        source = FIXTURE.replace(b'\n', b'\r\n')
        self.target.write_bytes(source)
        self.commit_target()
        result = self.run_cli('--write')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(source.replace(b'versionCode = 13', b'versionCode = 14').replace(
            b'versionName = "0.2.11"', b'versionName = "0.2.12"'), self.target.read_bytes())

    def test_staged_and_index_hidden_changes_are_rejected(self):
        for situation in ['staged', 'staged-but-worktree-restored', 'assume-unchanged', 'skip-worktree']:
            with self.subTest(situation=situation):
                self.git('update-index', '--no-assume-unchanged', '--no-skip-worktree', '--', 'app/build.gradle.kts')
                self.git('reset', '-q', 'HEAD', '--', 'app/build.gradle.kts')
                self.target.write_bytes(FIXTURE)
                if situation in ['assume-unchanged', 'skip-worktree']:
                    self.git('update-index', '--' + situation, '--', 'app/build.gradle.kts')
                self.target.write_bytes(FIXTURE + b'// Changed target.\n')
                if situation.startswith('staged'):
                    self.git('add', '--', 'app/build.gradle.kts')
                if situation == 'staged-but-worktree-restored':
                    self.target.write_bytes(FIXTURE)
                self.assert_rejected_unchanged('--write')

    def test_untracked_target_and_missing_git_repository_are_rejected(self):
        self.git('rm', '--cached', '--', 'app/build.gradle.kts')
        self.assert_rejected_unchanged('--write')
        (self.root / '.git').rename(self.root / 'not-a-git-repository')
        self.assert_rejected_unchanged('--write')

    def test_final_and_parent_symlinks_are_rejected_without_touching_the_referent(self):
        referent = self.root / 'elsewhere.gradle.kts'
        referent.write_bytes(FIXTURE)
        self.target.unlink()
        self.target.symlink_to(referent)
        self.assert_rejected_unchanged('--write')
        self.assertTrue(self.target.is_symlink())
        self.assertEqual(FIXTURE, referent.read_bytes())
        self.target.unlink()
        self.target.write_bytes(FIXTURE)
        actual = self.root / 'real-app'
        self.target.parent.rename(actual)
        (self.root / 'app').symlink_to(actual, target_is_directory=True)
        self.assert_rejected_unchanged('--write')
        self.assertTrue((self.root / 'app').is_symlink())

    def test_identical_retry_cannot_consume_another_code_before_or_after_commit(self):
        self.assertEqual(0, self.run_cli('--write').returncode)
        self.assert_rejected_unchanged('--write')
        self.commit_target()
        result = self.assert_rejected_unchanged('--write')
        self.assertIn('expected', result.stderr.lower())
        self.assertIn(b'versionCode = 14\n', self.target.read_bytes())

    def test_required_arguments_cannot_be_omitted(self):
        before = self.target_state()
        for arguments in [[], ['--write'], ['--expected-code', '13', '--expected-name', '0.2.11']]:
            result = subprocess.run([sys.executable, str(self.tool), *arguments],
                                    capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(before, self.target_state())

    def load_cli(self):
        spec = importlib.util.spec_from_file_location('fixture_version_cli', self.tool)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def assert_write_error(self, module):
        errors = io.StringIO()
        with redirect_stderr(errors), self.assertRaises(SystemExit) as failure:
            module.main(['--expected-code', '13', '--expected-name', '0.2.11',
                         '--next-name', '0.2.12', '--write'])
        self.assertEqual(2, failure.exception.code)
        self.assertEqual(['build.gradle.kts'], [p.name for p in self.target.parent.iterdir()])
        name = self.git('rev-parse', '--git-path', 'bump-android-version.lock').stdout.strip()
        self.assertFalse((self.root / name).exists())
        return errors.getvalue()

    def test_failed_atomic_replacement_leaves_the_original_and_cleans_up(self):
        module = self.load_cli()
        before = self.target_state()
        with mock.patch.object(module.os, 'replace', side_effect=OSError('Synthetic replace failure')):
            errors = self.assert_write_error(module)
        self.assertIn('Synthetic replace failure', errors)
        self.assertEqual(before, self.target_state())

    def test_failed_temporary_file_sync_leaves_the_original_and_cleans_up(self):
        module = self.load_cli()
        before = self.target_state()
        with mock.patch.object(module.os, 'fsync', side_effect=OSError('Synthetic sync failure')):
            errors = self.assert_write_error(module)
        self.assertIn('Synthetic sync failure', errors)
        self.assertEqual(before, self.target_state())

    def test_edit_during_preparation_is_not_overwritten(self):
        module = self.load_cli()
        real_fsync = module.os.fsync
        external = FIXTURE + b'// An external editor changed the target.\n'

        def edit_then_sync(descriptor):
            self.target.write_bytes(external)
            real_fsync(descriptor)

        with mock.patch.object(module.os, 'fsync', side_effect=edit_then_sync):
            self.assert_write_error(module)
        self.assertEqual(external, self.target.read_bytes())

    def test_mode_only_change_during_preparation_is_not_overwritten(self):
        module = self.load_cli()
        real_fsync = module.os.fsync
        self.target.chmod(0o644)

        def chmod_then_sync(descriptor):
            self.target.chmod(0o640)
            real_fsync(descriptor)

        with mock.patch.object(module.os, 'fsync', side_effect=chmod_then_sync):
            errors = self.assert_write_error(module)
        self.assertIn('changed', errors)
        self.assertEqual(FIXTURE, self.target.read_bytes())
        self.assertEqual(0o640, stat.S_IMODE(self.target.stat().st_mode))

    def test_missing_target_is_not_created(self):
        self.target.unlink()
        for arguments in [(), ('--write',)]:
            result = self.run_cli(*arguments)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(self.target.exists())
            self.assertEqual([], list(self.target.parent.iterdir()))

    def test_linked_worktree_uses_its_own_target_and_lock_not_a_global_allocator(self):
        worktree = self.root / 'linked-worktree'
        self.git('worktree', 'add', '--detach', str(worktree), 'HEAD')
        linked_tool = worktree / 'tools/bump_android_version.py'
        linked_tool.parent.mkdir()
        shutil.copyfile(self.tool, linked_tool)
        name = self.git('rev-parse', '--git-path', 'bump-android-version.lock').stdout.strip()
        lock = self.root / name
        lock.write_bytes(b'Only protects the original worktree.\n')
        result = subprocess.run(
            [sys.executable, str(linked_tool), '--expected-code', '13',
             '--expected-name', '0.2.11', '--next-name', '0.2.12', '--write'],
            cwd=worktree, capture_output=True, text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(b'versionCode = 14\n', (worktree / 'app/build.gradle.kts').read_bytes())
        self.assertEqual(FIXTURE, self.target.read_bytes())
        self.assertEqual(b'Only protects the original worktree.\n', lock.read_bytes())

    def test_write_flag_must_be_explicit_not_an_abbreviation(self):
        for abbreviation in ['--w', '--wri']:
            with self.subTest(abbreviation=abbreviation):
                self.assert_rejected_unchanged(abbreviation)

    def test_alternate_version_setters_are_ambiguous_even_with_one_literal(self):
        for setter in [b'setVersionCode(99)', b'setVersionName("9.9.9")']:
            with self.subTest(setter=setter):
                self.target.write_bytes(FIXTURE.replace(b'        minSdk = 26',
                                                       b'        ' + setter + b'\n        minSdk = 26'))
                self.commit_target()
                self.assert_rejected_unchanged('--write')

    def test_unbalanced_outer_gradle_braces_are_rejected(self):
        for source in [FIXTURE + b'}\n', b'}\n{\n' + FIXTURE, FIXTURE + b'{\n']:
            with self.subTest(source=source):
                self.target.write_bytes(source)
                self.commit_target()
                self.assert_rejected_unchanged('--write')
