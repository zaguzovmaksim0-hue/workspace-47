// Throwaway probe branch: load public portal pages in mobile Chrome and dump their clickable controls.
import { chromium } from 'playwright-core';
import fs from 'node:fs';

const probes = JSON.parse(fs.readFileSync(new URL('./probes.json', import.meta.url)));
const browser = await chromium.launch({ channel: 'chrome', args: ['--no-sandbox'] });
const context = await browser.newContext({
  viewport: { width: 412, height: 915 },
  isMobile: true,
  hasTouch: true,
  deviceScaleFactor: 1,
  locale: 'es-ES',
  userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36',
});
// Never reach the certificate step itself: abort every request to the Cl@ve identity endpoint.
await context.route(/pasarela-ident\.clave\.gob\.es/, route => route.abort());
const dump = () => {
  const vis = (el) => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
  const text = (el) => (el.innerText || el.value || el.getAttribute('aria-label') || el.title || '').trim().replace(/\s+/g, ' ').slice(0, 90);
  const controls = Array.from(document.querySelectorAll('a,button,input[type=submit],input[type=button],input[type=image],[onclick],[role=button]'))
    .map(el => ({
      tag: el.tagName, id: el.id || null, name: el.getAttribute('name'), type: el.getAttribute('type'), cls: (el.getAttribute('class') || '').slice(0, 60),
      text: text(el), href: el.getAttribute('href'), onclick: (el.getAttribute('onclick') || '').slice(0, 160) || null,
      form: el.form ? { id: el.form.id || null, action: el.form.getAttribute('action'), method: el.form.getAttribute('method') } : null,
      visible: vis(el),
    }))
    .filter(c => c.text || c.href || c.onclick || c.id);
  const forms = Array.from(document.forms).map(f => ({ id: f.id || null, name: f.getAttribute('name'), action: f.getAttribute('action'), method: f.getAttribute('method'),
    inputs: Array.from(f.elements).map(i => [i.tagName, i.name || null, i.type || null, (i.value || '').length]).slice(0, 40) }));
  const frames = Array.from(document.querySelectorAll('iframe')).map(f => f.getAttribute('src'));
  return { url: location.href, title: document.title, readyState: document.readyState, controls: controls.slice(0, 250), forms, frames };
};
fs.mkdirSync('probe-out', { recursive: true });
const results = new Array(probes.length);
const runOne = async (probe) => {
  const page = await context.newPage();
  const entry = { name: probe.name, steps: [] };
  try {
    const response = await page.goto(probe.url, { waitUntil: 'load', timeout: 45000 });
    entry.status = response && response.status();
    await page.waitForTimeout(probe.wait ?? 4000);
    entry.steps.push(await page.evaluate(dump));
    for (const selector of probe.clicks ?? []) {
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'load', timeout: 45000 }).catch(() => null),
        page.locator(selector).first().evaluate(el => el.click()),
      ]);
      await page.waitForTimeout(probe.wait ?? 4000);
      entry.steps.push({ clicked: selector, ...(await page.evaluate(dump)) });
    }
  } catch (e) {
    entry.error = String(e).slice(0, 400);
    try { entry.steps.push(await page.evaluate(dump)); } catch (_) {}
  }
  await page.close();
  return entry;
};
let next = 0;
await Promise.all(Array.from({ length: 6 }, async () => {
  while (next < probes.length) {
    const index = next++;
    const probe = probes[index];
    // A hung page must not cost the whole dump: cap each probe and flush results after every one.
    results[index] = await Promise.race([
      runOne(probe),
      new Promise(resolve => setTimeout(() => resolve({ name: probe.name, error: 'probe-timeout' }), 90000)),
    ]);
    fs.writeFileSync('probe-out/dom.json', JSON.stringify(results, null, 1));
    console.log(index, probe.name, results[index].error || 'ok');
  }
}));
fs.writeFileSync('probe-out/dom.json', JSON.stringify(results, null, 1));
await browser.close().catch(() => null);
process.exit(0);
