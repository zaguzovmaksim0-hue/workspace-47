# UI performance work — 2026-10-04

The operator reports slow scrolling and screen opening. No FPS claim is made: no E2E or interaction benchmark is authorized in this pass.

- An optimized, non-debuggable R8 build preserves the installed profile availability, excludes debug source sets and persistent QA diagnostics. Cryptographic/PDF reflection namespaces and JavaScript bridge methods remain kept conservatively.
- Diagnostic emission uses a bounded background queue; clear is an erasure barrier that invalidates pending entries. The bounded file appends normally and rotates chunks rather than rewriting on each callback.
- Bundled catalog/profile parsing and search-index preparation run off the UI thread behind a process-local single-load cache. Startup has a visible loading state and retry after a failed load. Activity teardown before initialization is guarded.
- Browser loading UI tracks visibility instead of every integer progress change.

Unit tests cover queue bounds/ordering/clear/failure, cached concurrent loads/retry, and progress-state transitions. GitHub Actions runs unit/lint/build/security gates; synthetic emulator and real-service E2E remain unrun. Signing correctness after shrinking needs conservative keep rules; optimized build success alone is not a measured frame-rate improvement or real-provider acceptance.

## R8 optional dependency boundary

R8 exposed absent classes already optional/unavailable in the unminified Android dependency graph. Narrow rules cover PDFBox's optional JP2 image codec (the app signs PDF streams without rendering page images), SLF4J 1.7's documented NOP binding fallback, build-time OSGi provider annotations, and the unused Santuario StAX/JSR105 surfaces. Application XML paths use DOM parsing plus Santuario canonicalization, not StAX. No global `-ignorewarnings` or blanket `-dontwarn **` is used. Missing classes outside this documented set remain build failures. This does not add support for optional codecs or StAX.

References: https://github.com/TomRoush/PdfBox-Android#optional-dependencies and https://www.slf4j.org/codes.html#StaticLoggerBinder .
