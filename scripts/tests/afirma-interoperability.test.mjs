// Runs the actual reviewed application shim against synthetic browser primitives.
// No certificate, private key, government account, or network connection is used.
import vm from 'node:vm';
import { webcrypto, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const root = dirname(fileURLToPath(import.meta.url));
// Every packaged fragment in generator order: the full assembly, as with all portal flags enabled.
const parts = [...readFileSync(join(root, '../../tools/generate_afirma_shim.py'), 'utf8')
  .matchAll(/\("(afirma_shim_[a-z_]+\.js)", \d+, \d+\)/g)].map(match => match[1]);
assert.ok(parts.length > 3 && parts[0] === 'afirma_shim_bootstrap.js' && parts.at(-1) === 'afirma_shim_installation.js');
const source = parts
  .map(part => readFileSync(join(root, `../../app/src/main/res/raw/${part}`), 'utf8')).join('');
import test from 'node:test';
function fixture(options = {}) {
  const messages = [], errors = [], events = new Map(), docEvents = new Map(), timers = new Map(), intervals = new Map();
  let id = 0, fallbacks = 0;
  const flags = { __JFM_FUNCTIONAL_SIGNING_ENABLED__: true, ...options.flags };
  const code = source.replace(/__JFM_[A-Z0-9_]+__/g, token => token === '__JFM_STA_BATCH_ORIGIN__'
    ? JSON.stringify('https://sede.melilla.es') : String(flags[token] ?? false));
  const listen = (map, name, fn, options) => map.set(name, [...(map.get(name) ?? []), { fn, once: Boolean(options?.once) }]);
  const url = new URL(options.url ?? 'https://www.juntadeandalucia.es/example');
  const window = { location: url, crypto: webcrypto, JuntaFirmaMobile: { postMessage: value => messages.push(JSON.parse(value)) },
    open() { fallbacks++; return {}; },
    WebSocket: function FakeWebSocket(url) { this.url = url; },
    addEventListener: (name, fn, options) => listen(events, name, fn, options),
    setTimeout: (fn, ms) => { timers.set(++id, { fn, ms }); return id; },
    clearTimeout: key => timers.delete(key),
    setInterval: (fn, ms) => { intervals.set(++id, { fn, ms }); return id; },
    clearInterval: key => intervals.delete(key)
  };
  window.window = window; window.self = window; window.top = window;
  const document = { readyState: 'loading', visibilityState: 'visible', getElementById() { return null; }, querySelector() { return null; },
    addEventListener: (name, fn, options) => listen(docEvents, name, fn, options) };
  Object.assign(window, { document, URL, DOMException, TextEncoder, TextDecoder, Uint8Array,
    btoa: value => Buffer.from(value, 'binary').toString('base64'),
    atob: value => Buffer.from(value, 'base64').toString('binary'),
    queueMicrotask: fn => fn(), console });
  const context = vm.createContext(window);
  new vm.Script(code, { filename: 'actual-afirma_shim.js' }).runInContext(context, { timeout: 1000 });
  function emit(name, target = events) {
    const listeners = target.get(name) ?? [];
    for (const { fn } of listeners) fn({});
    target.set(name, listeners.filter(item => !item.once));
  }
  function loaded() { emit('DOMContentLoaded'); document.readyState = 'complete'; emit('load'); }
  function original() { fallbacks++; }
  function sign(args = {}) {
    window.AutoScript.sign(args.data ?? 'QUJDRA==', args.algorithm ?? 'SHA256withRSA', args.format ?? 'CAdES',
      args.properties ?? 'mode=implicit', () => {}, (code, message) => errors.push({ code, message }));
  }
  return { window, context, messages, errors, timers, intervals, loaded, original, sign, emit, document,
    emitDocument: name => emit(name, docEvents),
    tick: () => { for (const timer of Array.from(intervals.values())) timer.fn(); },
    nativeCount: () => messages.filter(m => m.type === 'MINIAPPLET_SIGN').length,
    fallbackCount: () => fallbacks };
}

const badajoz = { url: 'https://sede.dip-badajoz.es/portal/entidades.do', flags: { __JFM_BADAJOZ_COMPATIBILITY_ENABLED__: true } };
const properties = 'policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true';

test('ordinary AutoScript assignment is intercepted once', () => {
 const f=fixture(); f.loaded(); f.window.AutoScript={sign:f.original}; f.sign();
 assert.equal(f.nativeCount(),1); assert.equal(f.fallbackCount(),0);
});

const aemet = {
  url: 'https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso',
  flags: { __JFM_AEMET_COMPATIBILITY_ENABLED__: true }
};
for (const nonce of ['0', '10000']) test('AEMET returns the local signature only to its login callback: '+nonce, () => {
  const f = fixture(aemet);
  let submits = 0, network = 0;
  const form = { signature: '', submit() { submits++; } };
  f.window.fetch = () => { network++; throw new Error('unexpected network'); };
  f.window.XMLHttpRequest = function () { network++; throw new Error('unexpected HTTP broker'); };
  f.window.MiniApplet = { sign: f.original };
  f.loaded();
  assert.equal(f.window.MiniApplet.sign.__jfmSignHook, true);
  f.window.MiniApplet.sign(Buffer.from(nonce).toString('base64'), 'SHA1withRSA', 'AUTO', null,
    signature => { form.signature = signature; form.submit(); },
    code => f.errors.push(code));
  assert.equal(f.nativeCount(), 1);
  assert.equal(submits, 0);
  const request = f.messages.find(m => m.type === 'MINIAPPLET_SIGN');
  assert.equal(request.format, 'AUTO');
  assert.equal(request.extraProperties, null);
  const result = { data: JSON.stringify({type: 'MINIAPPLET_RESULT', requestId: request.requestId,
    status: 'success', signature: 'AQ==', certificate: 'Ag=='}) };
  f.window.JuntaFirmaMobile.onmessage(result);
  f.window.JuntaFirmaMobile.onmessage(result);
  assert.equal(form.signature, 'AQ==');
  assert.equal(submits, 1);
  assert.equal(network, 0);
  assert.equal(f.fallbackCount(), 0);
  assert.deepEqual(f.errors, []);
});
for (const failure of ['CERTIFICATE_LOCKED', 'PROTOCOL_FAILED', 'REQUEST_EXPIRED']) {
  test('AEMET early error can submit a negative login through its original callback: ' + failure, () => {
    const f = fixture(aemet);
    let submits = 0, network = 0;
    const form = { errorMessage: '', submit() { submits++; } };
    f.window.fetch = () => { network++; throw new Error('unexpected network'); };
    f.window.XMLHttpRequest = function () { network++; throw new Error('unexpected HTTP broker'); };
    f.window.MiniApplet = { sign: f.original };
    f.loaded();
    const beforeTimers = new Set(f.timers.keys());
    f.window.MiniApplet.sign('MQ==', 'SHA1withRSA', 'AUTO', null,
      () => assert.fail('unexpected signature'),
      (code, message) => {
        // Models firma.js koCallback's login submit; the sink never performs a request.
        form.errorMessage = message;
        f.errors.push(code);
        form.submit();
      });
    assert.equal(f.nativeCount(), 1);
    assert.equal(submits, 0);
    const request = f.messages.find(m => m.type === 'MINIAPPLET_SIGN');
    if (failure === 'REQUEST_EXPIRED') {
      const pendingTimers = [...f.timers].filter(([id]) => !beforeTimers.has(id));
      assert.equal(pendingTimers.length, 1);
      pendingTimers[0][1].fn();
      pendingTimers[0][1].fn();
    } else {
      const result = { data: JSON.stringify({type: 'MINIAPPLET_RESULT', requestId: request.requestId,
        status: 'error', errorCode: failure}) };
      f.window.JuntaFirmaMobile.onmessage(result);
      f.window.JuntaFirmaMobile.onmessage(result);
    }
    assert.deepEqual(f.errors, [failure]);
    assert.ok(form.errorMessage.length > 0);
    assert.equal(submits, 1);
    assert.equal(network, 0);
    assert.equal(f.fallbackCount(), 0);
  });
}
for (const [label, data, format, extra] of [
  ['out of range', '10001', 'AUTO', null], ['leading zero', '00', 'AUTO', null],
  ['document', '%PDF-1.7', 'AUTO', null], ['other format', '1', 'CAdES', null],
  ['string properties', '1', 'AUTO', 'null'], ['empty properties', '1', 'AUTO', '']
]) test('AEMET rejects an unreviewed tuple without invoking the original transport: '+label, () => {
  const f = fixture(aemet); f.window.MiniApplet = { sign: f.original }; f.loaded();
  f.window.MiniApplet.sign(Buffer.from(data).toString('base64'), 'SHA1withRSA', format, extra,
    () => assert.fail('unexpected signature'), code => f.errors.push(code));
  assert.equal(f.nativeCount(), 0);
  assert.equal(f.fallbackCount(), 0);
  assert.deepEqual(f.errors, ['INVALID_REQUEST']);
});
test('late global defineProperty is repaired after page load', () => {
 const f=fixture(); f.loaded();
 Object.defineProperty(f.window,'AutoScript',{configurable:true,writable:true,value:{sign:f.original}});
 f.tick(); f.sign(); assert.equal(f.nativeCount(),1); assert.equal(f.fallbackCount(),0);
});
test('capture-phase click repairs a replacement before the page click handler', () => {
 const f=fixture(); f.loaded();
 Object.defineProperty(f.window,'AutoScript',{configurable:true,writable:true,value:{sign:f.original}});
 f.emitDocument('click'); f.sign(); assert.equal(f.nativeCount(),1); assert.equal(f.fallbackCount(),0);
});
test('capture-phase submit also repairs late MiniApplet replacement', () => {
 const f=fixture(); f.loaded();
 Object.defineProperty(f.window,'MiniApplet',{configurable:true,writable:true,value:{sign:f.original}});
 f.emitDocument('submit');
 f.window.MiniApplet.sign('QUJDRA==','SHA256withRSA','CAdES','mode=implicit',()=>{},()=>{});
 assert.equal(f.nativeCount(),1); assert.equal(f.fallbackCount(),0);
});
test('generic hook guard survives the old 120 second deadline', () => {
 const f=fixture(); f.loaded();
 for(const timer of Array.from(f.timers.values())) if(timer.ms===120000) timer.fn();
 Object.defineProperty(f.window,'AutoScript',{configurable:true,writable:true,value:{sign:f.original}});
 f.tick(); f.sign(); assert.equal(f.nativeCount(),1); assert.equal(f.fallbackCount(),0);
});
test('guard pauses in hidden documents and restarts on pageshow', () => {
 const f=fixture(); f.loaded(); assert.ok(f.intervals.size>0);
 f.document.visibilityState='hidden'; f.emitDocument('visibilitychange'); assert.equal(f.intervals.size,0);
 f.document.visibilityState='visible'; f.emitDocument('visibilitychange'); assert.ok(f.intervals.size>0);
 f.emit('pagehide'); assert.equal(f.intervals.size,0);
 f.emit('pageshow'); assert.ok(f.intervals.size>0);
});
test('repeated repair does not duplicate a native request', () => {
 const f=fixture(); f.window.AutoScript={sign:f.original}; f.loaded();
 for(let i=0;i<30;i++) f.tick(); f.sign(); assert.equal(f.nativeCount(),1);
});
for(const [label, extra, format] of [
 ['canonical',properties,'Cades'],
 ['reordered','headless=true\nfilters=nonexpired:true;authCert:true\npolicy=FirmaAGE','Cades'],
 ['CRLF',properties.replaceAll('\n','\r\n')+'\r\n','Cades'],
 ['format alias',properties,'CAdES'],
 ['format case',properties,'cades'],
]) test('Badajoz accepts equivalent properties: '+label,()=>{
 const f=fixture(badajoz); f.window.AutoScript={sign:f.original};f.loaded();f.sign({format,properties:extra});
 assert.equal(f.nativeCount(),1);assert.deepEqual(f.errors,[]);
});
for(const [label,extra] of [
 ['duplicate',properties+'\npolicy=FirmaAGE'],
 ['conflicting',properties+'\npolicy=Unknown'],
 ['unknown',properties+'\nunrecognised=true'],
 ['changed',properties.replace('FirmaAGE','Different')],
]) test('Badajoz rejects ambiguous or changed semantics: '+label,()=>{
 const f=fixture(badajoz);f.window.AutoScript={sign:f.original};f.loaded();f.sign({format:'Cades',properties:extra});
 assert.equal(f.nativeCount(),0);assert.equal(f.errors[0]?.code,'INVALID_REQUEST');
});
