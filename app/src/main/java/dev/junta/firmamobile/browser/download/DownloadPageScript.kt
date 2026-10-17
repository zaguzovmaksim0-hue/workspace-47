package dev.junta.firmamobile.browser.download

/** Document-start helper for the top HTTPS frame. It keeps references to the
 * page's own recent Blob objects and a description of the last submitted POST
 * form, so that a download the page started can be saved. It never sends data
 * anywhere; the app reads it with evaluateJavascript after a download callback. */
internal object DownloadPageScript {
    const val HANDLE = "window.__firmaMobileDownloads"

    val SOURCE = """
(function () {
  'use strict';
  try {
    if (window.top !== window || location.protocol !== 'https:' || window.__firmaMobileDownloads) return;
    var MAX = ${DocumentDownloadPlan.MAX_BYTES}, MAX_FORM = ${FormSubmission.MAX_BODY}, AGE = 60000;
    var blobs = new Map(), held = 0, names = new Map(), reads = new Map(), lastForm = null;
    function forget(url) {
      var blob = blobs.get(url);
      if (blob) { held -= blob.size; blobs.delete(url); }
    }
    var create = URL.createObjectURL;
    URL.createObjectURL = function (object) {
      var url = create.apply(this, arguments);
      try {
        if (object instanceof Blob && object.size <= MAX) {
          blobs.set(url, object); held += object.size;
          while (blobs.size > 16 || held > 2 * MAX) forget(blobs.keys().next().value);
        }
      } catch (e) {}
      return url;
    };
    var revoke = URL.revokeObjectURL;
    URL.revokeObjectURL = function (url) {
      // The page may revoke right after starting its download; the Blob
      // object itself stays readable for a short time.
      try { if (blobs.has(url)) setTimeout(function () { forget(url); }, AGE); } catch (e) {}
      return revoke.apply(this, arguments);
    };
    function rememberName(anchor) {
      try {
        if (anchor && anchor.hasAttribute && anchor.hasAttribute('download')) {
          names.set(anchor.href.split('#')[0], { name: anchor.getAttribute('download') || '', at: Date.now() });
          if (names.size > 16) names.delete(names.keys().next().value);
        }
      } catch (e) {}
    }
    var anchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () { rememberName(this); return anchorClick.apply(this, arguments); };
    document.addEventListener('click', function (event) {
      var node = event.target;
      while (node && node.nodeName !== 'A') node = node.parentNode;
      rememberName(node);
    }, true);
    function remember(form, submitter) {
      try {
        if (!(form instanceof HTMLFormElement)) return;
        var method = ((submitter && submitter.getAttribute('formmethod')) || form.getAttribute('method') || 'get').toLowerCase();
        if (method !== 'post') return;
        var action = submitter && submitter.hasAttribute('formaction') ? submitter.formAction : form.action;
        var enctype = ((submitter && submitter.getAttribute('formenctype')) || form.getAttribute('enctype') || '').toLowerCase();
        var data;
        try { data = submitter ? new FormData(form, submitter) : new FormData(form); }
        catch (e) { data = new FormData(form); if (submitter && submitter.name) data.append(submitter.name, submitter.value || ''); }
        var entries = [], files = false, size = 0;
        data.forEach(function (value, name) {
          if (typeof value === 'string') { entries.push([name, value]); size += name.length + value.length; }
          else { if (value.size > 0 || value.name) files = true; entries.push([name, '', 'file']); }
        });
        var charset = (form.getAttribute('accept-charset') || '').trim().split(/[\s,]+/)[0] || document.characterSet || 'UTF-8';
        lastForm = size > MAX_FORM ? null : { action: action, enctype: enctype, charset: charset, entries: entries, files: files, at: Date.now() };
      } catch (e) { lastForm = null; }
    }
    window.addEventListener('submit', function (event) { remember(event.target, event.submitter); }, true);
    var submit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () { remember(this, null); return submit.apply(this, arguments); };
    Object.defineProperty(window, '__firmaMobileDownloads', { enumerable: false, configurable: false, writable: false, value: Object.freeze({
      form: function (url) {
        var form = lastForm;
        if (!form || Date.now() - form.at > AGE || String(form.action).split('#')[0] !== String(url).split('#')[0]) return null;
        lastForm = null;
        return JSON.stringify(form);
      },
      read: function (url, id) {
        if (reads.size >= 2) return false;
        var state = { state: 'pending' };
        reads.set(id, state);
        setTimeout(function () { reads.delete(id); }, 2 * AGE);
        var blob = blobs.get(url);
        var source = blob ? Promise.resolve(blob) : fetch(url).then(function (response) { return response.blob(); });
        source.then(function (value) {
          if (value.size > MAX) { state.state = 'too-large'; return; }
          var reader = new FileReader();
          reader.onload = function () {
            var text = String(reader.result), comma = text.indexOf(',');
            state.data = comma >= 0 ? text.substring(comma + 1) : '';
            state.size = value.size; state.type = value.type || '';
            state.state = 'ready';
          };
          reader.onerror = function () { state.state = 'error'; };
          reader.readAsDataURL(value);
        }).catch(function () { state.state = 'error'; });
        return true;
      },
      poll: function (id, url) {
        var state = reads.get(id);
        if (!state) return 'missing';
        if (state.state !== 'ready') return state.state;
        var name = names.get(String(url).split('#')[0]);
        var label = name && Date.now() - name.at < AGE ? name.name : '';
        return 'ready:' + state.size + ':' + state.data.length + ':' + encodeURIComponent(state.type) + ':' + encodeURIComponent(label);
      },
      chunk: function (id, offset, length) {
        var state = reads.get(id);
        return state && state.state === 'ready' ? state.data.substr(offset, length) : null;
      },
      done: function (id) { reads.delete(id); }
    }) });
  } catch (e) {}
})();
""".trimIndent()
}
