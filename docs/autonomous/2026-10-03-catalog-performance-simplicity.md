# Faster, simpler catalog — 0.2.4

## Request and scope

The user reports that the catalog feels slow despite a modest design, and asks for fewer buttons, preferably one action to open a site. Preserve the newly added address entry above the catalog. Work independently without subagents/E2E and install the verified update without clearing data. Main stays unmerged.

## Findings and measurable boundary

The list was already a keyed LazyColumn, not an eager rendering of all183 cards. The avoidable work was elsewhere: PortalCatalogRepository normalized the query for every candidate, normalized searchable fields again, created a Collator and sorted on each query; PortalCatalogViewModel rebuilt that content even for location progress and transient messages on its UI scope. Cards also carried two or three visible action controls and a second background layer for a decorative offset shadow.

A focused JVM probe reproduces the old search/sort hot loop and compares it with the actual new index over the183 public catalog entries. For360 queries, the old loop performs360 sorts and hundreds of thousands of normalization calls. The new index normalizes915 public fields once, then360 queries once each with zero additional name collations. Result membership/order are compared; the repository tests separately cover actual resolved profile names, aliases, regions and all filter types.

Probe timings are supporting measurements of that local hot loop, not a frame-rate benchmark or proof of a complete diagnosis of every perceived delay. No app launch, frame trace or device E2E was performed. Device load, build mode and rendering/provider work can still affect real scrolling. Avoid claiming a general speedup multiplier or that all phone jank has been eliminated.

## Search and state changes

CatalogSearchIndex snapshots normalized public fields and name ordering once. Region ordering reuses a bounded cache of public region IDs. User search strings and query results are not retained as a history/cache. Filtering still uses current favorites/recents and live query constraints. Security-sensitive launch resolution remains outside the index and is revalidated through the existing repository methods.

The view model computes content from search/preferences on Dispatchers.Default; lightweight location and snackbar state combine with the last computed content without rebuilding sections. Its constructor no longer performs catalog resolution/sorting synchronously. A loading marker avoids showing a false empty result during that initial deferred computation. Tests use an injected deterministic dispatcher and verify section-object reuse for transient state, changed preferences and latest search results.

The LazyColumn retains stable keys and now supplies explicit content types for headers, fields, notices and portal rows. No large platform profiles are marked falsely immutable. Existing account/cryptographic/browser state is unchanged.

## One visible action per portal

A portal card now contains its title, organization/territory and one full-width “Abrir sitio” button. The ordinary open path is unchanged. Cards use one simple background and a1dp outline, not a nested offset-shadow background plus multiple action rows.

Favorite management and reviewed profile compatibility are retained as rare explicit actions in a long-press menu. An equivalent accessibility custom action exposes the menu to assistive technology. Nothing automatically switches a portal into the special mode, starts a signature, grants a certificate or retries navigation. A short hint explains long press once, rather than repeating more buttons in every card.

The region selector is a compact name/change row. “Usar mi ubicación” moves into the region picker, where it still requires an explicit tap. No extra location request is made. Existing location-denial/settings behavior is preserved.

The own-address entry remains above the catalog. It also has one full-width Open-site action; paste is a small field action while empty, and clear is a field action when needed. Its additional BoxWithConstraints subcomposition is removed. Immutable address drafts reuse their parsed URI for preview and submission instead of reparsing it on each read. Existing paste/clear/keyboard/privacy behavior is retained. Browser/profile callbacks, signature algorithms, permissions, endpoints and user data are not modified.

## Verification

Eight pure index tests check precomputation counters, ordering, accents/aliases, filters, snapshots and concurrent queries. Four real-repository tests compare all183 entries, region priorities, filters and aliases with the prior behavior and retain launch validation. Four view-model tests cover deferred computation and retained sections during transient state. Seven isolated JVM Compose tests cover the one-button card, explicit long press, accessible options/favorite action, moved geolocation, loading, bounded lazy composition and narrow/large-text layout.

Existing profile-selection instrumentation sources are adjusted to select the newly explicit menu; they compile but are not executed. Existing component assertions are retained with the new open-button wording and the location control's new placement. The broad gate is five successful exact-head groups plus explicitly SKIPPED Android instrumentation under PR338's no-E2E label. Do not report that skipped group as passed.

## Release and references

Version0.2.4, versionCode6. Verify exact CI source/checksum, previous installed bytes, Android signer/payload, UID/first-install date and non-foreground state before a normal in-place update. Preserve the previous APK; never uninstall or clear data to force acceptance. No app/Shower/emulator/private account or real administrative procedure is launched for verification.

Primary references:
https://developer.android.com/develop/ui/compose/performance/bestpractices
https://developer.android.com/develop/ui/compose/lists
https://developer.android.com/develop/ui/compose/performance/stability/strongskipping

These references support precomputation, stable reuse and minimizing recomposition work; they do not establish measured frame performance for this installed application.
