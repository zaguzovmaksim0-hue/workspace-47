# 0002 — Local PAdES consent preview

**Date:** 2026-10-24
**Status:** Draft

## Context

Consent already has actual PDF bytes in MiniApplet PAdES envelopes, ACCEDA and
native AutoFirma PDF sign/co-sign, including inline JSON batch items. Native
tri-phase/batch inputs can also contain full PDF bytes: admit only a bounded
local PDF header, after strict Base64 decoding for a batch item. Identifiers,
PRE data, hashes and remote references are unavailable. Never fetch them.

## Decision

Offer an explicit preview action inside the existing secure consent dialogs.
Keep the existing confirmation callbacks, 600 ms arming and request/hash checks.
Pass a revocable local preview capability, never an endpoint. MiniApplet/ACCEDA
evidence owns a clearable snapshot; native operations copy their owned input only
on request. Native tri-phase/batch capabilities first probe and clear a bounded
local copy, rejecting references/hashes; batch items use the existing strict
Base64 decoder and limits. Non-native PAdES batch consent holds an opaque CAIB
batch XML envelope and labels, not original PDF bytes, and stays unavailable.

Use the platform PdfRenderer on an IO dispatcher, one render at a time, first page
only. Apply existing 500,000/524,288-byte document admission limits and a bitmap
budget of 1080 × 1920 and 1,500,000 pixels. A randomly named private cache file is
unlinked immediately after opening its seekable descriptor, before PDF parsing;
finally also attempts deletion. Source copies and discarded bitmaps are cleared.

The coroutine deadline is 3 seconds, with cancellation checkpoints before and
after native calls. It cannot interrupt synchronous native PDF parsing/rendering.
No additional dependency, network access, signing or consent action is introduced.

## Consequences

Preview is explicitly unavailable when no proven local PDF capability exists.
Rendering failures show a localized generic message. Closing/replacing the dialog
cancels publication and releases its image; native cleanup waits for a native call
to return. JVM tests cover copy revocation, cleanup and pixel bounds; platform
rendering and Compose lifecycle still require broad CI/device verification.

## Alternatives

1. An isolated rendering service with a terminable process provides stronger
   hostile-PDF isolation and a hard deadline, but adds IPC and lifecycle scope.
2. A memory/pipe descriptor avoids a cache file. PdfRenderer requires a seekable
   descriptor; a pipe is unsuitable, and the public SharedMemory API starts at
   API 27 while this app supports API 26. A cache descriptor works across the
   supported range without hidden APIs.

Platform contract: [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer).

## Payoff trigger

A hard preview deadline or hostile-PDF process isolation becomes required.
Track the native-call cancellation limitation in `docs/debt/`.
