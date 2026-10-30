import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const relayScript = fs.readFileSync(
  new URL('../../app/src/main/res/raw/afirma_relay.js', import.meta.url),
  'utf8'
);
const servlet = 'https://servlet.example.test/afirma/StorageService';

function createPage({ withBridge = true, inFrame = false, protocol = 'https:' } = {}) {
  class FakeEvent {
    constructor(type) {
      this.type = type;
      this.stopped = false;
    }

    stopImmediatePropagation() {
      this.stopped = true;
    }
  }

  class FakeXHR {
    constructor() {
      this.nativeReadyState = 0;
      this.nativeStatus = 0;
      this.nativeText = '';
      this.onreadystatechange = null;
      this.listeners = [];
      this.sentBody = null;
      this.sendCount = 0;
    }

    get readyState() { return this.nativeReadyState; }
    get status() { return this.nativeStatus; }
    get responseText() { return this.nativeText; }

    open(method, url, async = true) {
      this.method = method;
      this.url = url;
      this.async = async;
      this.nativeReadyState = 1;
      this.nativeStatus = 0;
      this.nativeText = '';
    }

    send(body) {
      this.sentBody = body;
      this.sendCount++;
    }

    addEventListener(type, callback, capture = false) {
      this.listeners.push({ type, callback, capture });
    }

    dispatchEvent(event) {
      for (const listener of this.listeners) {
        if (listener.type === event.type && listener.capture) {
          listener.callback.call(this, event);
          if (event.stopped) return false;
        }
      }
      const handler = this['on' + event.type];
      if (typeof handler === 'function') {
        handler.call(this, event);
        if (event.stopped) return false;
      }
      for (const listener of this.listeners) {
        if (listener.type === event.type && !listener.capture) {
          listener.callback.call(this, event);
          if (event.stopped) return false;
        }
      }
      return true;
    }

    abort() {
      this.nativeReadyState = 0;
      this.nativeStatus = 0;
      this.nativeText = '';
      this.dispatchEvent(new FakeEvent('abort'));
      this.dispatchEvent(new FakeEvent('loadend'));
    }

    respond(status, text = '') {
      this.nativeReadyState = 4;
      this.nativeStatus = status;
      this.nativeText = text;
      this.dispatchEvent(new FakeEvent('readystatechange'));
      if (status === 0) {
        this.dispatchEvent(new FakeEvent('error'));
        this.dispatchEvent(new FakeEvent('loadend'));
      } else {
        this.dispatchEvent(new FakeEvent('load'));
        this.dispatchEvent(new FakeEvent('loadend'));
      }
    }
  }

  const sent = [];
  const bridge = {
    postMessage(value) { sent.push(JSON.parse(value)); },
    onmessage: null
  };
  const window = {};
  window.top = inFrame ? {} : window;
  if (withBridge) window.FirmaMobileAfirmaRelay = bridge;
  const location = { href: protocol + '//site.example.test/page', protocol };

  let clock = 0;
  let timerId = 0;
  const timers = new Map();
  function setTimeout(fn, delay) {
    const id = ++timerId;
    timers.set(id, { at: clock + delay, fn });
    return id;
  }
  function clearTimeout(id) {
    timers.delete(id);
  }
  function advance(ms) {
    const end = clock + ms;
    while (true) {
      const next = [...timers].filter(([, timer]) => timer.at <= end)
        .sort((a, b) => a[1].at - b[1].at)[0];
      if (!next) break;
      const [id, timer] = next;
      clock = timer.at;
      timers.delete(id);
      timer.fn();
    }
    clock = end;
  }

  const originalOpen = FakeXHR.prototype.open;
  const originalSend = FakeXHR.prototype.send;
  vm.runInNewContext(relayScript, {
    window, location, XMLHttpRequest: FakeXHR, Event: FakeEvent,
    setTimeout, clearTimeout, URL
  }, { filename: 'afirma_relay.js' });
  function answer(rid, status, text) {
    bridge.onmessage({ data: JSON.stringify({ v: 1, rid, status, text }) });
  }
  return {
    XHR: FakeXHR, sent, bridge, answer, advance, timers,
    originalOpen, originalSend
  };
}

function blocked(page, method, body, url = servlet) {
  const xhr = new page.XHR();
  const seen = [];
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) seen.push([xhr.status, xhr.responseText]);
  };
  xhr.open(method, url, true);
  xhr.send(body);
  xhr.respond(0);
  return { xhr, seen };
}

function eventRecorder(xhr) {
  const events = [];
  const record = (type) => () => events.push(type);
  for (const type of ['readystatechange', 'load', 'error', 'loadend', 'abort']) {
    xhr.addEventListener(type, record(type));
    xhr['on' + type] = record(type + ':on');
  }
  return events;
}

test('blocked GET check sends origin and returns 200 OK exactly once', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const calls = [];
  let loads = 0;
  xhr.open('GET', servlet + '?op=check', true);
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) calls.push(['handler', xhr.status, xhr.responseText]);
  };
  xhr.addEventListener('readystatechange', () => {
    if (xhr.readyState === 4) calls.push(['listener', xhr.status, xhr.responseText]);
  });
  xhr.addEventListener('load', () => loads++);
  xhr.send('');
  xhr.respond(0);
  assert.deepEqual(calls, [], 'page must not see the native status 0 event');
  assert.deepEqual(page.sent[0], {
    v: 1, rid: 1, op: 'check', servlet: 'https://servlet.example.test', id: null
  });
  page.answer(1, 'ok');
  assert.deepEqual(calls, [['handler', 200, 'OK'], ['listener', 200, 'OK']]);
  assert.equal(loads, 1);
  assert.equal(xhr.sendCount, 1, 'native request must be attempted');
});

test('one XHR can poll get through wait, wait, ready with reassigned handler', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const seen = [];
  const texts = ['#wait', '#wait', 'raw+encrypted=/result'];
  for (let cycle = 0; cycle < 3; cycle++) {
    xhr.onreadystatechange = () => {
      if (xhr.readyState === 4) seen.push([cycle, xhr.status, xhr.responseText]);
    };
    xhr.open('POST', servlet, true);
    assert.equal(xhr.readyState, 1, 'open clears the preceding 4 override');
    assert.equal(xhr.status, 0, 'open clears the preceding 200 override');
    xhr.send('op=get&v=1_0&id=Ab_12-xy&it=' + cycle);
    xhr.respond(0);
    assert.equal(seen.length, cycle, 'native status 0 must stay hidden');
    assert.equal(page.sent[cycle].op, 'get');
    assert.equal(page.sent[cycle].id, 'Ab_12-xy');
    page.answer(page.sent[cycle].rid, cycle === 2 ? 'ready' : 'wait', texts[cycle]);
  }
  assert.deepEqual(seen, [
    [0, 200, '#wait'], [1, 200, '#wait'], [2, 200, texts[2]]
  ]);
  assert.equal(xhr.sendCount, 3);
});

test('working servlet status 200 goes unchanged to native handlers', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const seen = [];
  const events = eventRecorder(xhr);
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) seen.push([xhr.status, xhr.responseText]);
  };
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(200, 'SERVER RESPONSE');
  assert.deepEqual(seen, [[200, 'SERVER RESPONSE']]);
  assert.deepEqual(events, [
    'readystatechange',
    'load:on', 'load', 'loadend:on', 'loadend'
  ]);
  assert.equal(page.sent.length, 0, 'no relay for a successful server request');
  assert.equal(page.timers.size, 0);
});

test('put success returns OK and forwards data unchanged', () => {
  const page = createPage();
  const dat = 'abc_def-0123==';
  const { seen } = blocked(page, 'POST', 'op=put&v=1_0&id=sign-01&dat=' + dat);
  assert.equal(seen.length, 0);
  assert.deepEqual(page.sent[0], {
    v: 1, rid: 1, op: 'put', servlet: 'https://servlet.example.test',
    id: 'sign-01', dat
  });
  page.answer(1, 'ok');
  assert.deepEqual(seen, [[200, 'OK']]);
});

test('put over 8 MiB bypasses the bridge, including original status 0', () => {
  const page = createPage();
  const dat = 'A'.repeat(8 * 1024 * 1024 + 1);
  const { seen } = blocked(page, 'POST', 'op=put&v=1_0&id=s1&dat=' + dat);
  assert.deepEqual(seen, [[0, '']]);
  assert.equal(page.sent.length, 0);
});

test('pass returns original 0 and releases timer', () => {
  const page = createPage();
  const { seen } = blocked(page, 'POST', 'op=get&v=1_0&id=s1&it=0');
  page.answer(1, 'pass');
  assert.deepEqual(seen, [[0, '']]);
  assert.equal(page.timers.size, 0);
});

test('15-second timeout delivers original status 0', () => {
  const page = createPage();
  const { seen } = blocked(page, 'GET', null, servlet + '?op=check');
  page.advance(14999);
  assert.deepEqual(seen, []);
  page.advance(1);
  assert.deepEqual(seen, [[0, '']]);
  page.answer(1, 'ok');
  assert.deepEqual(seen, [[0, '']], 'late answers must be ignored');
});

test('no bridge, iframe and http never patch the XHR prototype', () => {
  for (const options of [
    { withBridge: false }, { inFrame: true }, { protocol: 'http:' }
  ]) {
    const page = createPage(options);
    assert.equal(page.XHR.prototype.open, page.originalOpen);
    assert.equal(page.XHR.prototype.send, page.originalSend);
  }
});

test('other methods, invalid fields and non-HTTPS targets bypass bridge', () => {
  const page = createPage();
  const cases = [
    ['GET', '', servlet + '?op=foo'],
    ['GET', '', servlet + '?op=check&extra=1'],
    ['PUT', 'op=get&v=1_0&id=a&it=0', servlet],
    ['POST', 'op=foo&v=1_0&id=a', servlet],
    ['POST', 'op=get&v=1_0&id=wrong!&it=0', servlet],
    ['POST', 'op=get&v=1_0&id=a&it=x', servlet],
    ['POST', 'op=put&v=1_0&id=a&dat=x', 'http://servlet.example.test/afirma']
  ];
  for (const [method, body, url] of cases) {
    const { seen } = blocked(page, method, body, url);
    assert.deepEqual(seen, [[0, '']], method + ' ' + body);
  }
  assert.equal(page.sent.length, 0);
});

test('unknown rid, bad JSON and wrong version do not settle pending request', () => {
  const page = createPage();
  const { seen } = blocked(page, 'GET', '', servlet + '?op=check');
  page.bridge.onmessage({ data: '{' });
  page.bridge.onmessage({ data: JSON.stringify({ v: 1, rid: 999, status: 'ok' }) });
  page.bridge.onmessage({ data: JSON.stringify({ v: 2, rid: 1, status: 'ok' }) });
  assert.deepEqual(seen, []);
  page.answer(1, 'ok');
  assert.deepEqual(seen, [[200, 'OK']]);
});

test('unknown status and missing ready text fall back to native status 0', () => {
  const page = createPage();
  const first = blocked(page, 'POST', 'op=get&v=1_0&id=a&it=1');
  page.answer(1, 'ready');
  assert.deepEqual(first.seen, [[0, '']]);
  const second = blocked(page, 'POST', 'op=put&v=1_0&id=a&dat=data');
  page.answer(2, 'ready', 'unexpected');
  assert.deepEqual(second.seen, [[0, '']]);
});

test('at most eight blocked requests may wait for a bridge reply', () => {
  const page = createPage();
  const items = Array.from({ length: 9 }, () =>
    blocked(page, 'GET', '', servlet + '?op=check'));
  assert.equal(page.sent.length, 8);
  assert.equal(page.timers.size, 8);
  assert.deepEqual(items[8].seen, [[0, '']]);
  assert.ok(items.slice(0, 8).every(({ seen }) => seen.length === 0));
  page.answer(1, 'ok');
  assert.deepEqual(items[0].seen, [[200, 'OK']]);
  const last = blocked(page, 'GET', '', servlet + '?op=check');
  assert.equal(page.sent.length, 9);
  page.answer(page.sent[8].rid, 'ok');
  assert.deepEqual(last.seen, [[200, 'OK']]);
});

test('reopen cancels old pending rid, restoring the original XHR properties', () => {
  const page = createPage();
  const { xhr, seen } = blocked(page, 'GET', '', servlet + '?op=check');
  xhr.open('POST', servlet, true);
  assert.equal(page.timers.size, 0);
  page.answer(1, 'ok');
  assert.deepEqual(seen, []);
  xhr.send('op=get&v=1_0&id=a&it=0');
  xhr.respond(0);
  page.answer(2, 'wait');
  assert.deepEqual(seen, [[200, '#wait']]);
  xhr.open('GET', servlet + '?op=check', true);
  assert.equal(Object.hasOwn(xhr, 'responseText'), false);
  assert.equal(Object.hasOwn(xhr, 'status'), false);
  assert.equal(Object.hasOwn(xhr, 'readyState'), false);
});

test('bridge postMessage error leaves the page with original status 0', () => {
  const page = createPage();
  page.bridge.postMessage = () => { throw new Error('bridge unavailable'); };
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  const seen = [];
  xhr.addEventListener('readystatechange', () => {
    if (xhr.readyState === 4) seen.push([xhr.status, xhr.responseText]);
  });
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(0);
  assert.deepEqual(seen, [[0, '']]);
  assert.deepEqual(events, [
    'readystatechange:on', 'readystatechange',
    'error:on', 'error', 'loadend:on', 'loadend'
  ]);
  assert.equal(page.timers.size, 0);
  page.answer(1, 'ok');
  page.advance(15000);
  assert.equal(events.length, 6, 'late replies and timers must not emit more events');
  assert.deepEqual(seen, [[0, '']]);
  assert.equal(page.timers.size, 0);
});

test('blocked put success suppresses native error and emits load then loadend once', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  let status = null;
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) status = xhr.status;
  };
  xhr.open('POST', servlet, true);
  xhr.send('op=put&v=1_0&id=upload-01&dat=data');
  xhr.respond(0);
  assert.deepEqual(events, []);
  page.answer(1, 'ok');
  assert.equal(status, 200);
  assert.deepEqual(events, [
    'readystatechange',
    'load:on', 'load', 'loadend:on', 'loadend'
  ]);
});

test('blocked check pass restores native error and loadend without load', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  const states = [];
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) states.push(xhr.status);
  };
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(0);
  assert.deepEqual(events, []);
  page.answer(1, 'pass');
  assert.deepEqual(states, [0]);
  assert.deepEqual(events, [
    'readystatechange',
    'error:on', 'error', 'loadend:on', 'loadend'
  ]);
});

test('timeout restores native error and loadend without load', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  const states = [];
  xhr.onreadystatechange = () => {
    if (xhr.readyState === 4) states.push(xhr.status);
  };
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(0);
  page.advance(15000);
  assert.deepEqual(states, [0]);
  assert.deepEqual(events, [
    'readystatechange',
    'error:on', 'error', 'loadend:on', 'loadend'
  ]);
});

test('abort during a pending check prevents a late bridge reply from reviving it', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  const seen = [];
  xhr.addEventListener('readystatechange', () => {
    if (xhr.readyState === 4) seen.push([xhr.status, xhr.responseText]);
  });
  xhr.addEventListener('load', () => events.push('page-load'));
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(0);
  assert.equal(page.timers.size, 1);
  xhr.abort();
  assert.equal(page.timers.size, 0);
  page.answer(1, 'ok');
  assert.deepEqual(seen, []);
  assert.equal(events.filter((event) => event === 'page-load').length, 0);
  assert.equal(page.timers.size, 0);

  xhr.open('POST', servlet, true);
  xhr.send('op=get&v=1_0&id=a&it=0');
  xhr.respond(0);
  page.answer(page.sent[1].rid, 'wait');
  assert.deepEqual(seen, [[200, '#wait']]);
});

test('abort during a pending check prevents the 15-second timeout from reviving it', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  const seen = [];
  xhr.addEventListener('readystatechange', () => {
    if (xhr.readyState === 4) seen.push([xhr.status, xhr.responseText]);
  });
  xhr.addEventListener('load', () => events.push('page-load'));
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.respond(0);
  xhr.abort();
  page.advance(15000);
  assert.deepEqual(seen, []);
  assert.equal(events.filter((event) => event === 'page-load').length, 0);
  assert.equal(page.timers.size, 0);
});

test('aborting a request that never waits for the bridge keeps native behavior', () => {
  const page = createPage();
  const xhr = new page.XHR();
  const events = eventRecorder(xhr);
  xhr.open('GET', servlet + '?op=check', true);
  xhr.send('');
  xhr.abort();
  assert.deepEqual(events, [
    'abort:on', 'abort', 'loadend:on', 'loadend'
  ]);
  assert.equal(page.sent.length, 0);
  assert.equal(page.timers.size, 0);
});
