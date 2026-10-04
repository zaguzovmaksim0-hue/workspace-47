# Inline address entry above the catalog — 0.2.3

## User outcome

Immediately after the certificate screen, the catalog screen must begin with a usable custom-address field; the catalog stays below it. The former extra “Abrir otra web” modal action below catalog search is no longer the primary entry path.

PortalCatalogScreen now hoists a transient CatalogAddressInput outside the LazyColumn items and renders CatalogAddressCard immediately after the compact header, before the shared-engine notice, region selector, search and portal sections. It uses the existing MainActivity.onOpenPublicWeb callback, which revalidates the accepted URI and selects profileId=null. No catalog profile, signing engine, certificate consent or browser transport is changed.

The field accepts a full public HTTPS URL or a bare public domain/path, in which case only the https:// prefix is added. Existing explicit http:// and unsafe schemes, credentials, numeric/local hosts and invalid ports remain rejected by PublicBrowserAddress. Query/fragment spelling and encoded bytes are not decoded or normalized. Inputs above the budget are rejected, not truncated into another navigable URL.

## Interaction and useful additions

- Inline Paste, Clear and Open actions; the URI keyboard Go action uses the same one-shot transition as Open.
- A host-only destination hint and field-local error messages; no new confirmation dialog for ordinary navigation.
- Paste reads only a single plain text clipboard item after the explicit tap. Content URIs and Intents are not coerced, opened or fetched. Missing or excessive clipboard text does not accidentally submit an old draft.
- Successful submission disables the field/actions until the screen leaves; a second click/IME action cannot launch the same request again.
- A back-to-address shortcut after scrolling down and a clear-search control for catalog search. Clearing search or scrolling does not erase the separate address draft.
- Narrow cards or larger font scales put Open on a separate full-width row. The list accounts for the keyboard inset; actions retain minimum touch heights.

The draft is ordinary in-memory Compose state, not rememberSaveable, preferences, history or a favorite. It disappears when the catalog leaves composition. No background clipboard observation, URL persistence, analytics or logging is added. Diagnostic state descriptions exclude address/query contents. Existing screenshot policy is not changed.

## Verification scope

No subagents or E2E are used. The existing PR338 no-E2E label and skipped instrumentation job remain. Five other exact-head gates must pass, including compilation of the unchanged/adjusted instrumentation sources without executing them.

Twelve pure state/input tests cover HTTPS convenience, exact path/query preservation, rejected unsafe addresses, empty/invalid/oversized clipboard inputs, exact length boundaries, one-shot submission, reset and non-leaking diagnostic strings. Eight isolated JVM Compose tests exercise the real catalog component with a recording URI callback, not MainActivity/WebView: inline order, keyboard/button behavior, error correction, explicit paste, clipboard rejection, scrolling/search and draft retention, disposal and narrow/large-text layout.

Existing E2E source call sites now locate the visible address field rather than clicking the removed modal-opening button. Their original browser/consent assertions are retained, but those E2E tests are not run in this operator scope. The legacy standalone dialog helper remains for its own compatibility test; it is not invoked by the catalog.

The local pure tests shorten the feedback loop; GitHub Actions Debug/QA unit, lint, APK, Python, Go and security results on the exact head remain the broad acceptance record. These tests do not establish real government authentication or signing success.

## Release

Version0.2.3, versionCode5, QA suffix retained. Install over verified0.2.2 only after current source/check/artifact/signer/payload validation and a non-foreground check. Keep a backup and preserve UID/first-install date/user data. Do not merge main. Do not launch the app, Shower, emulator or a real procedure for testing.

Primary UI references: Android Compose text input and keyboard actions, and state hoisting/lifecycle documentation:
https://developer.android.com/develop/ui/compose/text/user-input
https://developer.android.com/develop/ui/compose/state

No external implementation is copied; no dependencies, signing permissions or new network endpoints are introduced.

The first isolated component run exposed two test setup mistakes: TextField supporting text belongs to the unmerged semantics tree, and clearing catalog search restores collapsed sections. The tests now select the actual supporting nodes and expand the real section before scrolling. Their display, input, error and retention assertions remain; production UI is unchanged by this correction. The first failed XML report is retained.

A focused negative regression also caught bare-host URLs whose query or fragment contains another https:// link. Prefix recognition is now anchored to the start of the input, so an embedded redirect URL is not mistaken for the outer scheme. The original query/fragment bytes remain unchanged and explicit unsupported schemes remain rejected. The same19 input/validator tests are rerun after this correction.

Input admission also checks the unchanged MainActivity ASCII-form size contract before consuming the action. An oversized Unicode-to-ASCII URL cannot leave the form stuck in its consumed state after downstream rejection; short Unicode paths still preserve their URI data.
