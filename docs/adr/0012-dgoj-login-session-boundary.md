# 0012 — Bound DGOJ session comparison to its two login forms

**Date:** 2026-11-08
**Status:** Proposed

## Context

Public no-certificate probe 42471980144 observed two top-level DGOJ login submits with one nonempty sedeCsrf per form. The first form action and second document URL contain a terminal `;DGOJ_ID` on the exact plogin.jjsp path. Sanitization did not retain the session alphabet or length. Existing exact-button action comparison cannot represent this pair without losing the observed session, and the homepage popup intentionally has no certificate profile.

## Decision

Use the reviewed stable inicio.jjsp login as the existing inventory entry's exact launchUrl and QA-only Cl@ve v2 start. Invoke only the two pinned native submit controls, then the unchanged AFIRMA helper. Require exact form id/name, ownership, POST/action, empty target, no submit overrides or inherited base target, complete top-level document and one enabled nonempty hidden sedeCsrf. Compare native and document sources independently; send only static recipe codes to Kotlin.

Only this DGOJ helper accepts the exact raw plogin.jjsp URL followed by one final `;DGOJ_ID=` and 1–256 ASCII unreserved characters. This is a defensive limit, not a measured server grammar. Require the absolute form.action to agree with its raw absolute or root-relative spelling so browser canonicalization cannot hide an explicit port or dot segment. Other spellings fail closed. Never decode or rewrite the URL, session, form or CSRF. The generic URL matcher, TLS authorizer, profile model, grant lifetimes, popup isolation and signing allowlist are unchanged.

## Consequences

The original catalog identity and entry URL remain; the universal button opens the published login without a profile. Only an explicit QA profile launch can use the reviewed ServiceRedirect → POST AuthenticateCitizen contract. Unknown session formats or DOM changes stop the recipe. The probe's top-level ServiceProvider POST 200 does not establish a TLS issuer list, Android callback/grant, certificate presentation or successful login. Keep E2E_PENDING / IMPLEMENTED_NOT_E2E and no signing capability.

## Alternatives

1. Keep DGOJ navigation-only until certificate acceptance. This preserves the earlier skip but cannot exercise the now-reviewed top-level login route.
2. Extend the shared exact-button or TLS URL matcher with session normalization. This would affect unrelated portals and grant boundaries without reviewed evidence; a local two-state helper needs no such exception.

## Payoff trigger

Authorized synthetic Android fixtures and protected main-only REAL E2E validate the form guards, session comparison and certificate callback on the integrated candidate. If a real session falls outside the defensive limit or a different raw action spelling appears, obtain sanitized structural evidence and review that exact case; never broaden the matcher to make acceptance pass. Track the unmeasured boundary in the dgoj-session-acceptance debt entry.
