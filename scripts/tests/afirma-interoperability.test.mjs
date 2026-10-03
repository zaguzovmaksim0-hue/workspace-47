// Runs the actual reviewed application shim against synthetic browser primitives.
// No certificate, private key, government account, or network connection is used.
import vm from 'node:vm';
import { webcrypto, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const root = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(root, '../../app/src/main/res/raw/afirma_shim.js'), 'utf8');
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
