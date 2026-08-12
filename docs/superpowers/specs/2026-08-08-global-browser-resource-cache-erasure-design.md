# G31-01 — global browser resource-cache erasure design

## Finding

The separately confirmed `Borrar todos los datos web` action currently removes
`WebStorage`, all WebView cookies, the active WebView back/forward list and the
active form autocomplete popup. It does **not** call `WebView.clearCache(true)`.

Android's current WebView API documents `clearCache(includeDiskFiles)` as the API
that clears the resource cache. With `includeDiskFiles = true`, the global action
can explicitly remove disk-backed resource-cache state. Android also documents
that `WebView.clearFormData()` only removes the currently displayed autocomplete
popup and does not delete saved WebView form data; this milestone does not broaden
into deprecated `WebViewDatabase` storage because the app does not read/write that
database or implement HTTP-auth credential persistence.

The current UI title/success state says all application web data was deleted, and
the confirmation copy promises cookies and web storage. Leaving the resource
cache intact is therefore a privacy/completeness defect: cacheable responses or
resources from previously opened portals can remain in the app-owned WebView cache
after the user chooses the global erase command.

## Boundary

This milestone is intentionally narrow:

- global confirmed browser-data deletion requires an exact active WebView owner,
  calls `clearCache(true)` on that owner before process-wide cookie/WebStorage
  deletion, and fails closed without starting partial deletion when no active owner
  exists;
- current-site deletion must **not** call `clearCache(true)`, because WebView
  resource-cache clearing is not exact-origin scoped and would silently widen a
  site-only command;
- existing pre-clear navigation-epoch invalidation, Client TLS abandonment,
  signing cancellation, completion lease, stale-callback ownership and reload
  rules remain unchanged;
- no dependency upgrade is required. The project already pins AndroidX WebKit
  1.16.0, but adopting `WebStorageCompat.deleteBrowsingData` is a separate larger
  compatibility/result-semantics change and is not necessary for this defect;
- no claim is made that `clearCache(true)` deletes every WebView-owned persistence
  class. The UI confirmation continues to state the concrete cookies + web-storage
  scope; this change additionally closes the resource-cache residue.

## TDD seam

`BrowserSecurityRegressionTest` already pins source-level order and separation for
current-site/global clear handlers. Add one regression that extracts both handlers
and requires:

1. global handler contains `clearCache(true)` after `stopLoading()` and before
   `siteDataCleaner.clearAllConfirmed`;
2. current-site handler does not contain `clearCache(true)`;
3. the completion lease is typed to a non-null `WebView`, and a missing active
   WebView invalidates any older lease, publishes the existing failure state, and
   returns before either cache or cookie/WebStorage deletion starts.

The first test must fail against the original source before the cache-call mutation.
The owner-availability regression must then fail against the first GREEN candidate
before its follow-up production mutation. The runtime fix stays narrow: one
`clearCache(true)` call plus fail-closed non-null owner admission for the global path.
