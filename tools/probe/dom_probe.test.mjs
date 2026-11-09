import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { sanitize, guardExactReady, clickReadyElement, dump, runOne } from './dom_probe.mjs';

const secret = 'PRIVATE_RUNTIME_VALUE';
const cleaned = sanitize({
  url: `https://sede.ordenacionjuego.gob.es/login;DGOJ_ID=${secret}?sedeCsrf=${secret}&SAMLRequest=${secret}#${secret}`,
  relative: `/login;DGOJ_ID=${secret}?token=${secret}`,
  cookie: secret,
  href: `#${secret}`,
  tokenNumber: 123456789,
  nested: [{ SAMLResponse: secret, csrfToken: secret, session: secret }],
  message: `Cookie: DGOJ_ID=${secret}; other=${secret}`,
  assignment: `sedeCsrf="${secret}" token='${secret}'`,
  forms: [{ id: 'plogin', hidden: [{ name: 'sedeCsrf', length: 21 }], sedeCsrf: { present: true, nonEmpty: true } }],
});
assert.ok(!JSON.stringify(cleaned).includes(secret));
assert.ok(!JSON.stringify(cleaned).includes('123456789'));
assert.deepEqual(sanitize(cleaned), cleaned);
assert.ok(cleaned.url.includes(';DGOJ_ID=<runtime>'));
assert.deepEqual(cleaned.forms[0].sedeCsrf, { present: true, nonEmpty: true });
assert.equal(cleaned.forms[0].hidden[0].name, 'sedeCsrf');
assert.equal(sanitize('https://pasarela.clave.gob.es/Proxy2/ServiceProvider'), 'https://pasarela.clave.gob.es/Proxy2/ServiceProvider');

assert.doesNotThrow(() => guardExactReady('complete', 1));
assert.throws(() => guardExactReady('loading', 1), /document-not-complete/);
assert.throws(() => guardExactReady('interactive', 1), /document-not-complete/);
assert.throws(() => guardExactReady('complete', 0), /exact-control-absent/);
assert.throws(() => guardExactReady('complete', 2), /exact-control-not-unique/);
let clicks = 0;
const document = { readyState: 'interactive' };
const click = vm.runInNewContext(`(${clickReadyElement.toString()})`, { document });
assert.throws(() => click({ click: () => clicks++ }), /document-not-complete/);
assert.equal(clicks, 0);
document.readyState = 'complete';
click({ click: () => clicks++ });
assert.equal(clicks, 1);

// Only numeric hidden lengths may leave the page; markup access must never occur.
const hidden = { tagName: 'INPUT', name: 'sedeCsrf', type: 'hidden', value: { length: 21 } };
const form = {
  id: 'plogin', method: 'post', action: `https://example.test/login;DGOJ_ID=${secret}`, target: '_self',
  elements: [hidden], getAttribute: () => null,
};
const root = { ...document, title: 'Login', forms: [form], querySelectorAll: () => [] };
Object.defineProperty(form, 'outerHTML', { get: () => assert.fail('raw form markup read') });
Object.defineProperty(hidden, 'outerHTML', { get: () => assert.fail('raw hidden markup read') });
const snapshot = vm.runInNewContext(`(${dump.toString()})`, { document: root, location: { href: 'https://example.test/login' } })();
assert.equal(snapshot.forms[0].method, 'post');
assert.equal(snapshot.forms[0].target, '_self');
assert.equal(snapshot.forms[0].hidden[0].length, 21);
assert.equal(snapshot.forms[0].sedeCsrf.nonEmpty, true);
assert.ok(!JSON.stringify(sanitize(snapshot)).includes(secret));

const control = (tagName, attrs) => ({
  tagName, id: tagName === 'IMG' ? 'imgClave' : null, innerText: '', offsetWidth: 1,
  getClientRects: () => [{}], getRootNode: () => ({}),
  getAttribute: name => attrs[name] ?? null, hasAttribute: name => Object.hasOwn(attrs, name),
});
const image = control('IMG', { src: 'clave.png' });
const parent = control('A', { class: 'text-center', target: '_self' });
for (const element of [image, parent]) {
  Object.defineProperty(element, 'outerHTML', { get: () => assert.fail('raw control markup read') });
}
const controlsRoot = { ...root, forms: [], querySelectorAll: selector =>
  selector === 'img#imgClave' ? [image] : selector === 'a.text-center:has(> img#imgClave)' ? [parent] : [] };
const controlSnapshot = vm.runInNewContext(`(${dump.toString()})`, {
  document: controlsRoot, location: { href: 'https://example.test/login' },
})(['img#imgClave', 'a.text-center:has(> img#imgClave)']);
assert.equal(controlSnapshot.html[0][1][0].src, 'clave.png');
assert.equal(controlSnapshot.html[1][1][0].target, '_self');
assert.equal(controlSnapshot.html[1][1][0].href, null);
assert.equal(controlSnapshot.html[0][1][0].onclick, null);

const configured = JSON.parse(fs.readFileSync(new URL('./probes.json', import.meta.url)));
assert.equal(configured.length, 3);
// Recon only: no probe may click anything.
configured.forEach(probe => assert.deepEqual(probe.clicks, []));
const probes = [{ name: 'wavef2-dgoj-login-clean-context',
  url: 'https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S',
  clicks: ['form#plogin input[type="submit"]'], net: true }];

// Exercise the runner without Chrome or network access, including the unchanged route aborts.
const contexts = [];
let current = {};
const browser = { newContext: async options => {
  const state = { options, clicks: 0, closed: false, handlers: {} };
  contexts.push(state);
  const frame = {};
  const emitResponse = (url, method, navigation, status) => {
    const request = { method: () => method, isNavigationRequest: () => navigation, frame: () => frame, resourceType: () => 'document' };
    for (const handler of state.handlers.response || []) handler({ url: () => url, request: () => request, status: () => status });
  };
  const page = {
    on: (event, handler) => (state.handlers[event] ??= []).push(handler), mainFrame: () => frame,
    goto: async () => {
      if (current.fail) throw new Error(secret);
      if (current.timeout) return new Promise((resolve, reject) => { state.cancel = reject; });
      for (const handler of state.handlers.console || []) handler({ type: () => 'error',
        text: () => assert.fail('console payload read'), location: () => ({ url: `https://example.test/?session=${secret}` }) });
      for (const handler of state.handlers.pageerror || []) handler(new Error(secret));
      return { status: () => current.status ?? 200 };
    },
    waitForTimeout: async () => {}, waitForNavigation: async () => ({ status: () => 202 }),
    evaluate: async () => ({ url: current.url ?? probes[0].url, readyState: current.readyState ?? 'complete', forms: [], html: [] }),
    locator: () => ({ count: async () => current.matches ?? 1, evaluate: async () => {
      state.clicks++;
      const endpoint = 'https://pasarela.clave.gob.es/Proxy2/ServiceProvider';
      for (let i = 0; i < 301; i++) emitResponse('https://example.test/unavailable', 'GET', false, 404);
      emitResponse(endpoint, 'POST', true, 202);
      emitResponse(endpoint, 'GET', true, 200);
      emitResponse(endpoint, 'POST', false, 200);
      emitResponse(`${endpoint}?token=${secret}`, 'POST', true, 200);
    } }),
  };
  return {
    route: async (pattern, handler) => {
      assert.ok(pattern.test('https://pasarela-ident.clave.gob.es/'));
      assert.ok(pattern.test('https://cert.valid.aoc.cat/'));
      assert.ok(!pattern.test('https://pasarela.clave.gob.es/Proxy2/ServiceProvider'));
      let aborted = false;
      await handler({ abort: async () => { aborted = true; } });
      assert.ok(aborted);
    },
    newPage: async () => page,
    close: async () => { state.closed = true; state.cancel?.(new Error(secret)); },
  };
} };
const probe = { ...probes[0], wait: 0, clicks: [probes[0].clicks[0]] };
const passed = await runOne(browser, probe);
assert.equal(contexts[0].clicks, 1);
assert.equal(passed.beforeClicks.length, 1);
assert.deepEqual(passed.net.filter(row => row[0] === 'clave-post'), [['clave-post', 202, 'https://pasarela.clave.gob.es/Proxy2/ServiceProvider', true]]);
for (const scenario of [
  { url: 'https://sede.ordenacionjuego.gob.es/500.html', status: 404, matches: 0, reason: 'dgoj-error-page' },
  { matches: 0, reason: 'exact-control-absent' },
  { matches: 2, reason: 'exact-control-not-unique' },
  { readyState: 'interactive', reason: 'document-not-complete' },
]) {
  current = scenario;
  const stopped = await runOne(browser, probe);
  assert.equal(stopped.stopped.reason, scenario.reason);
  assert.equal(stopped.stopped.status, scenario.status ?? 200);
  assert.equal(contexts.at(-1).clicks, 0);
}
current = { fail: true };
assert.equal((await runOne(browser, probe)).error, 'probe-error');
current = { timeout: true };
assert.equal((await runOne(browser, { ...probe, cap: 1 })).error, 'probe-timeout');
assert.ok(contexts.every(context => context.closed));
assert.equal(new Set(contexts).size, contexts.length);
assert.deepEqual(contexts[0].options.viewport, { width: 412, height: 915 });
assert.equal(contexts[0].options.isMobile, true);
assert.equal(contexts[0].options.hasTouch, true);
assert.equal(contexts[0].options.deviceScaleFactor, 1);
assert.equal(contexts[0].options.locale, 'es-ES');
assert.ok(!JSON.stringify(passed).includes(secret));
console.log('probe self-check: ok');
