import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';

// Only fake adb/Gradle processes are invoked here. No physical or virtual
// Android device is connected by this contract test.
const script = join(dirname(fileURLToPath(import.meta.url)), '../run-android-instrumentation-ci.sh');
const fakeAdb = `#!${process.execPath}
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync(process.env.CALLS, JSON.stringify(args) + '\\n');
if (args.join(' ') === '-e shell getprop ro.kernel.qemu') console.log(process.env.MOCK_QEMU);
else if (args.join(' ') === '-e shell svc power stayon true' && process.env.MOCK_CANNOT_WAKE !== 'true') fs.writeFileSync(process.env.WAKE_MARKER, 'awake');
else if (args.join(' ') === '-e shell dumpsys power') console.log('mWakefulness=' + (fs.existsSync(process.env.WAKE_MARKER) ? 'Awake' : 'Asleep'));
else if (args.includes('dumpsys')) console.log('synthetic state');
else if (args.includes('exec-out')) process.stdout.write('synthetic screenshot');
else if (args.includes('get')) console.log('2147483647');
`;

function run(options, check) {
  const dir = mkdtempSync(join(tmpdir(), 'firma-harness-'));
  try {
    const bin = join(dir, 'bin'); mkdirSync(bin);
    writeFileSync(join(bin, 'adb'), fakeAdb, { mode: 0o755 });
    writeFileSync(join(bin, 'sleep'), `#!${process.execPath}\nprocess.exit(0);\n`, { mode: 0o755 });
    writeFileSync(join(dir, 'gradlew'), `#!${process.execPath}\nrequire('node:fs').writeFileSync(process.env.GRADLE_ARGS, JSON.stringify(process.argv.slice(2))); process.exit(Number(process.env.MOCK_GRADLE_EXIT));\n`, { mode: 0o755 });
    const env = { ...process.env, PATH: bin + ':' + process.env.PATH,
      GITHUB_ACTIONS: 'true', CI: 'true', VERIFY_REF: 'synthetic-test-head',
      MOCK_QEMU: '1', MOCK_GRADLE_EXIT: '0', CALLS: join(dir, 'calls.jsonl'),
      WAKE_MARKER: join(dir, 'awake'), GRADLE_ARGS: join(dir, 'gradle-args.json'), ...options };
    const result = spawnSync('bash', [script], { cwd: dir, env, encoding: 'utf8', timeout: 20_000 });
    assert.equal(result.error, undefined);
    const calls = existsSync(env.CALLS) ? readFileSync(env.CALLS, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : [];
    check({ result, calls, dir, env, state: join(dir, 'app/build/outputs/instrumentation-device-state') });
  } finally { rmSync(dir, { recursive: true, force: true }); }
}

test('refuses ordinary environments without invoking adb', () => run({ GITHUB_ACTIONS: 'false' }, ({ result, calls }) => {
  assert.equal(result.status, 64); assert.deepEqual(calls, []);
}));
test('refuses physical devices before any state-changing command', () => run({ MOCK_QEMU: '0' }, ({ result, calls }) => {
  assert.equal(result.status, 65); assert.deepEqual(calls, [['-e', 'shell', 'getprop', 'ro.kernel.qemu']]);
}));
test('prepares only the emulator then runs the entire unchanged test command', () => run({}, ({ result, calls, env, state }) => {
  assert.equal(result.status, 0, result.stderr);
  assert(calls.every(args => args[0] === '-e'));
  assert.deepEqual(JSON.parse(readFileSync(env.GRADLE_ARGS, 'utf8')), ['connectedQaAndroidTest', '--no-daemon', '--console=plain']);
  assert.match(readFileSync(join(state, 'before-power.txt'), 'utf8'), /Asleep/);
  assert.match(readFileSync(join(state, 'ready-power.txt'), 'utf8'), /Awake/);
  assert.match(readFileSync(join(state, 'after-power.txt'), 'utf8'), /Awake/);
  assert.match(readFileSync(join(state, 'source.txt'), 'utf8'), /test_exit_code=0/);
}));
test('preserves the failing Gradle exit code while capturing diagnostics', () => run({ MOCK_GRADLE_EXIT: '37' }, ({ result, state }) => {
  assert.equal(result.status, 37); assert(existsSync(join(state, 'after-window.txt')));
  assert.match(readFileSync(join(state, 'source.txt'), 'utf8'), /test_exit_code=37/);
}));
test('does not start tests when the emulator never becomes awake', () => run({ MOCK_CANNOT_WAKE: 'true' }, ({ result, env, state }) => {
  assert.equal(result.status, 66); assert.equal(existsSync(env.GRADLE_ARGS), false);
  assert.match(readFileSync(join(state, 'source.txt'), 'utf8'), /test_exit_code=66/);
}));
