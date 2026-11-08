// Throwaway probe branch: inspect public login controls without retaining runtime credentials.
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const runtime = '<runtime>';
const sensitiveKey = /cookie|token|saml|csrf|session|password|secret|credential|authorization|DGOJ_ID/i;
const urlMetadata = new Set(['url', 'href', 'action', 'routerlink']);
const sanitizeUrl = text => text.replace(/(https?:\/\/)[^/@\s]+@/gi, '$1<runtime>@')
  .replace(/#(?:<runtime>|[^\s"'<>]*)/g, '#<runtime>');

export function sanitize(value) {
  if (Array.isArray(value)) return value.map(sanitize);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
    [key, sensitiveKey.test(key) && ['string', 'number'].includes(typeof item) ? runtime :
      sanitize(urlMetadata.has(key) && typeof item === 'string' ? sanitizeUrl(item) : item)]));
  if (typeof value !== 'string') return value;
  return (/^[/?]/.test(value) ? sanitizeUrl(value) : value.replace(/(?:https?:\/\/|afirma:\/\/)[^\s"'<>]+/gi, sanitizeUrl))
    .replace(/(;[\w.-]+=)(?:<runtime>|[^/;?#\s"'<>]*)/g, '$1<runtime>')
    .replace(/([?&][^=&#\s"'<>]+=)(?:<runtime>|[^&#\s"'<>]*)/g, '$1<runtime>')
    .replace(/\b((?:set-cookie|cookie|authorization)\s*[:=])[^\r\n]*/gi, '$1<runtime>')
    .replace(/\b([\w-]*(?:token|saml|csrf|session|password|secret|credential)[\w-]*["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;&<>]+)/gi, '$1<runtime>');
}

export function guardExactReady(readyState, count) {
  if (readyState !== 'complete') throw new Error('document-not-complete');
  if (count === 0) throw new Error('exact-control-absent');
  if (count !== 1) throw new Error('exact-control-not-unique');
}

export function clickReadyElement(el) {
  // Recheck inside the strict locator evaluation, in the same task as the native click.
  if (document.readyState !== 'complete') throw new Error('document-not-complete');
  el.click();
}

export const dump = (selectors = []) => {
  const vis = el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const text = el => el.type === 'hidden' ? '' : (el.innerText || el.value || el.getAttribute('aria-label') || el.title || '').trim().replace(/\s+/g, ' ').slice(0, 90);
  const formInfo = f => ({ id: f.id || null, name: f.getAttribute('name'), action: f.action,
    method: f.method, target: f.target || null });
  const describe = el => ({
    tag: el.tagName, id: el.id || null, shadowHost: el.getRootNode().host ? el.getRootNode().host.tagName : null,
    routerlink: el.getAttribute('routerlink'), name: el.getAttribute('name'), type: el.getAttribute('type'), cls: (el.getAttribute('class') || '').slice(0, 60),
    text: text(el), href: el.getAttribute('href'), src: el.getAttribute('src'), target: el.getAttribute('target'),
    onclick: el.hasAttribute('onclick') ? '<runtime>' : null,
    form: el.form ? formInfo(el.form) : null, visible: vis(el),
  });
  // Pierce open shadow roots: web-component sedes keep their login links there.
  const roots = [document];
  for (let i = 0; i < roots.length; i++) roots[i].querySelectorAll('*').forEach(el => el.shadowRoot && roots.push(el.shadowRoot));
  const sel = 'a,button,input[type=submit],input[type=button],input[type=image],[onclick],[role=button],[routerlink]';
  const controls = roots.flatMap(r => Array.from(r.querySelectorAll(sel)))
    .filter(el => el.type !== 'hidden').map(describe).filter(c => c.text || c.href || c.onclick || c.id);
  const forms = Array.from(document.forms).map(f => {
    // Compute lengths in the page; never return hidden strings or serialize form markup.
    const inputs = Array.from(f.elements).map(i => [i.tagName, i.name || null, i.type || null, (i.value || '').length]);
    const hidden = inputs.filter(i => i[2] === 'hidden').map(i => ({ name: i[1], length: i[3] }));
    const csrf = hidden.filter(i => i.name === 'sedeCsrf');
    return { ...formInfo(f), inputs, hidden, sedeCsrf: { present: csrf.length > 0, nonEmpty: csrf.some(i => i.length > 0) } };
  });
  const frames = Array.from(document.querySelectorAll('iframe')).map(f => f.getAttribute('src'));
  // Keep selector evidence structural: outerHTML can expose hidden fields and inline credentials.
  const html = selectors.map(selector => [selector, roots.flatMap(r => Array.from(r.querySelectorAll(selector))).slice(0, 3).map(describe)]);
  return { url: location.href, title: document.title, readyState: document.readyState, controls: controls.slice(0, 250), forms, frames, html };
};

export const runOne = async (browser, probe) => {
  const context = await browser.newContext({
    viewport: { width: 412, height: 915 },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: 1,
    locale: 'es-ES',
    userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36',
  });
  const entry = { name: probe.name, steps: [], beforeClicks: [] };
  const net = [];
  let timer;
  try {
    // Never reach the certificate step itself; preserve the existing identity-route aborts.
    await context.route(/pasarela-ident\.clave\.gob\.es|cert\.valid\.aoc\.cat/, route => route.abort());
    const page = await context.newPage();
    page.on('response', response => {
      const request = response.request();
      if (request.isNavigationRequest() && request.method() === 'POST' &&
          response.url() === 'https://pasarela.clave.gob.es/Proxy2/ServiceProvider') {
        net.push(['clave-post', response.status(), response.url(), request.frame() === page.mainFrame()]);
      }
    });
    // Matching script diagnostics retain metadata, never response bodies with possible credentials.
    if (probe.save) {
      const re = new RegExp(probe.save);
      page.on('response', r => {
        try {
          if (!re.test(r.url())) return;
          const url = sanitize(r.url());
          const safe = sanitize(probe.name + '__' + url).replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 200);
          fs.mkdirSync('probe-out/saved', { recursive: true });
          fs.writeFileSync('probe-out/saved/' + safe, JSON.stringify(sanitize({ url, status: r.status(), resourceType: r.request().resourceType() })));
        } catch (_) {}
      });
    }
    if (probe.net) {
      page.on('requestfailed', r => net.push(['failed', r.resourceType(), sanitize(r.url()),
        /^net::ERR_[A-Z0-9_]+$/.test(r.failure()?.errorText || '') ? r.failure().errorText : 'request-failed', r.method()]));
      page.on('response', r => { if (r.status() >= 300) net.push([r.status(), r.request().resourceType(), sanitize(r.url()), r.request().method()]); });
      page.on('framenavigated', f => { if (f === page.mainFrame()) net.push(['nav', sanitize(f.url())]); });
      // Console/error payloads can contain arbitrary values; retain only their event metadata.
      page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') net.push(['console', m.type(), sanitize(m.location().url || '')]); });
      page.on('pageerror', () => net.push(['pageerror']));
    }
    const collect = async () => {
      try {
        const response = await page.goto(probe.url, { waitUntil: 'load', timeout: 45000 });
        entry.status = response && response.status();
        let status = entry.status;
        await page.waitForTimeout(probe.wait ?? 4000);
        const first = await page.evaluate(dump, probe.html ?? []);
        if (probe.html) entry.html = first.html;
        delete first.html;
        entry.steps.push(first);
        for (const selector of probe.clicks ?? []) {
          const before = await page.evaluate(dump);
          const locator = page.locator(selector);
          const matches = await locator.count();
          entry.beforeClicks.push(sanitize({ selector, status, matches, ...before }));
          if (probe.name === 'wavef2-dgoj-login-clean-context' && new URL(before.url).pathname === '/500.html') {
            entry.stopped = { reason: 'dgoj-error-page', status, matches, selector, url: before.url };
            break;
          }
          try { guardExactReady(before.readyState, matches); } catch (e) {
            entry.stopped = { reason: e.message, status, matches, selector, url: before.url };
            throw e;
          }
          const [navigation] = await Promise.all([
            page.waitForNavigation({ waitUntil: 'load', timeout: 45000 }).catch(() => null),
            locator.evaluate(clickReadyElement),
          ]);
          status = navigation ? navigation.status() : null;
          await page.waitForTimeout(probe.wait ?? 4000);
          entry.steps.push({ clicked: selector, status, ...(await page.evaluate(dump)) });
        }
      } catch (e) {
        const guards = ['document-not-complete', 'exact-control-absent', 'exact-control-not-unique'];
        entry.error ??= guards.includes(e.message) ? e.message : (e.name === 'TimeoutError' ? 'navigation-timeout' : 'probe-error');
        try { entry.steps.push(await page.evaluate(dump)); } catch (_) {}
      }
    };
    const work = collect();
    await Promise.race([
      work,
      new Promise(resolve => { timer = setTimeout(() => { entry.error = 'probe-timeout'; resolve(); }, probe.cap ?? 90000); }),
    ]);
  } finally {
    clearTimeout(timer);
    await context.close();
  }
  if (probe.net) entry.net = net.filter((row, index) => index < 300 || row[0] === 'clave-post');
  return sanitize(entry);
};

async function main() {
  const { chromium } = await import('playwright-core');
  const probes = JSON.parse(fs.readFileSync(new URL('./probes.json', import.meta.url)));
  const browser = await chromium.launch({ channel: 'chrome', args: ['--no-sandbox'] });
  fs.mkdirSync('probe-out', { recursive: true });
  const results = new Array(probes.length);
  let next = 0;
  try {
    await Promise.all(Array.from({ length: 6 }, async () => {
      while (next < probes.length) {
        const index = next++;
        const probe = probes[index];
        results[index] = await runOne(browser, probe);
        fs.writeFileSync('probe-out/dom.json', JSON.stringify(sanitize(results), null, 1));
        console.log(index, sanitize(probe.name), results[index].error || results[index].stopped?.reason || 'ok');
      }
    }));
    fs.writeFileSync('probe-out/dom.json', JSON.stringify(sanitize(results), null, 1));
  } finally {
    await browser.close().catch(() => null);
  }
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch(() => { console.error('probe-runner-error'); process.exitCode = 1; });
}
