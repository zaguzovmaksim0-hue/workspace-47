(() => {
  'use strict';

  const bridge = window.FirmaMobileAfirmaRelay;
  if (!bridge || typeof bridge.postMessage !== 'function' ||
      window !== window.top || location.protocol !== 'https:') {
    return;
  }

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;
  const originalAbort = XMLHttpRequest.prototype.abort;
  const requests = new WeakMap();
  const listeners = new WeakSet();
  const pending = new Map();
  const MAX_DATA_LENGTH = 8 * 1024 * 1024;
  const MAX_PENDING = 8;
  const REPLY_TIMEOUT = 15000;
  let nextRid = 0;

  function matchRequest(method, url, body) {
    let target;
    try {
      target = new URL(url, location.href);
    } catch (_) {
      return null;
    }
    if (target.protocol !== 'https:') {
      return null;
    }

    if (method === 'GET' && target.search === '?op=check' &&
        (body === undefined || body === null || body === '')) {
      return { op: 'check', servlet: target.origin, id: null };
    }
    if (method !== 'POST' || typeof body !== 'string') {
      return null;
    }

    const get = /^op=get&v=1_0&id=([A-Za-z0-9_-]{1,64})&it=[0-9]+$/.exec(body);
    if (get) {
      return { op: 'get', servlet: target.origin, id: get[1] };
    }
    const put = /^op=put&v=1_0&id=([A-Za-z0-9_-]{1,64})&dat=([\s\S]*)$/.exec(body);
    if (put && put[2].length <= MAX_DATA_LENGTH) {
      return { op: 'put', servlet: target.origin, id: put[1], dat: put[2] };
    }
    return null;
  }

  function cancel(state) {
    if (state && state.rid !== null) {
      const entry = pending.get(state.rid);
      if (entry) {
        clearTimeout(entry.timer);
        pending.delete(state.rid);
      }
      state.rid = null;
    }
  }

  function dispatchCompletion(xhr, state, type) {
    if (requests.get(xhr) !== state) {
      return;
    }
    xhr.dispatchEvent(new Event(type));
  }

  function notify(xhr, state, succeeded) {
    if (requests.get(xhr) !== state) {
      return;
    }
    state.synthetic = true;
    const handler = xhr.onreadystatechange;
    try {
      if (typeof handler === 'function') {
        try {
          handler.call(xhr, new Event('readystatechange'));
        } catch (_) {
          // Other listeners must still receive the completion events.
        }
      }
      if (requests.get(xhr) !== state) {
        return;
      }
      // dispatchEvent also invokes the onreadystatechange IDL handler.
      // Temporarily detach it so the page observes exactly one callback.
      const detach = typeof handler === 'function' && xhr.onreadystatechange === handler;
      if (detach) {
        xhr.onreadystatechange = null;
      }
      try {
        xhr.dispatchEvent(new Event('readystatechange'));
      } finally {
        if (detach && xhr.onreadystatechange === null && requests.get(xhr) === state) {
          xhr.onreadystatechange = handler;
        }
      }
      if (succeeded) {
        dispatchCompletion(xhr, state, 'load');
        dispatchCompletion(xhr, state, 'loadend');
      } else {
        dispatchCompletion(xhr, state, 'error');
        dispatchCompletion(xhr, state, 'loadend');
      }
    } finally {
      state.synthetic = false;
    }
  }

  function settle(entry, reply) {
    if (pending.get(entry.rid) !== entry) {
      return;
    }
    pending.delete(entry.rid);
    clearTimeout(entry.timer);
    const { xhr, state } = entry;
    state.rid = null;
    if (requests.get(xhr) !== state) {
      return;
    }

    let text;
    if (reply && reply.status === 'ok' &&
        (state.request.op === 'check' || state.request.op === 'put')) {
      text = 'OK';
    } else if (reply && state.request.op === 'get' && reply.status === 'wait') {
      text = '#wait';
    } else if (reply && state.request.op === 'get' &&
               reply.status === 'ready' && typeof reply.text === 'string') {
      text = reply.text;
    }

    if (text !== undefined) {
      try {
        Object.defineProperty(xhr, 'readyState', { configurable: true, value: 4 });
        Object.defineProperty(xhr, 'status', { configurable: true, value: 200 });
        Object.defineProperty(xhr, 'responseText', { configurable: true, value: text });
        state.overridden = true;
      } catch (_) {
        delete xhr.readyState;
        delete xhr.status;
        delete xhr.responseText;
      }
    }
    notify(xhr, state, text !== undefined);
  }

  function onStateChange(event) {
    const state = requests.get(this);
    if (!state || !state.request || state.synthetic ||
        this.readyState !== 4 || this.status !== 0) {
      return;
    }
    if (state.rid !== null) {
      event.stopImmediatePropagation();
      return;
    }
    if (pending.size >= MAX_PENDING) {
      return;
    }

    const rid = ++nextRid;
    const entry = { rid, xhr: this, state, timer: null };
    state.rid = rid;
    pending.set(rid, entry);
    entry.timer = setTimeout(() => settle(entry, null), REPLY_TIMEOUT);
    try {
      bridge.postMessage(JSON.stringify({
        v: 1, rid, ...state.request
      }));
    } catch (_) {
      cancel(state);
      return;
    }
    event.stopImmediatePropagation();
  }

  function onNativeCompletionEvent(event) {
    const state = requests.get(this);
    if (!state || !state.request || state.synthetic) {
      return;
    }
    if (state.rid !== null) {
      event.stopImmediatePropagation();
    }
  }

  bridge.onmessage = function (event) {
    if (!event || typeof event.data !== 'string') {
      return;
    }
    let reply;
    try {
      reply = JSON.parse(event.data);
    } catch (_) {
      return;
    }
    if (!reply || reply.v !== 1 || !Number.isSafeInteger(reply.rid)) {
      return;
    }
    const entry = pending.get(reply.rid);
    if (entry) {
      settle(entry, reply);
    }
  };

  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    const previous = requests.get(this);
    cancel(previous);
    if (previous && previous.overridden) {
      delete this.readyState;
      delete this.status;
      delete this.responseText;
    }
    const state = {
      method: String(method).toUpperCase(),
      url: String(url),
      async: rest[0] !== false,
      request: null,
      rid: null,
      synthetic: false,
      overridden: false
    };
    requests.set(this, state);
    if (!listeners.has(this)) {
      this.addEventListener('readystatechange', onStateChange, true);
      this.addEventListener('error', onNativeCompletionEvent, true);
      this.addEventListener('loadend', onNativeCompletionEvent, true);
      listeners.add(this);
    }
    return originalOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function (body) {
    const state = requests.get(this);
    if (state && state.async) {
      state.request = matchRequest(state.method, state.url, body);
    }
    return originalSend.apply(this, arguments);
  };

  XMLHttpRequest.prototype.abort = function () {
    const state = requests.get(this);
    cancel(state);
    if (state && state.overridden) {
      delete this.readyState;
      delete this.status;
      delete this.responseText;
      state.overridden = false;
    }
    return originalAbort.apply(this, arguments);
  };
})();
