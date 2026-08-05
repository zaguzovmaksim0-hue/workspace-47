# Client TLS issuer-filter hardening implementation plan

1. Add a test-only two-certificate RSA fixture with a leaf subject distinct from
   its issuing CA, loaded through the same PKCS#12 path as other certificate
   tests.
2. Add `ClientAuthRequestHandlerTest` coverage asserting that an AEAT-style
   request whose sole principal is the leaf subject fails closed, while the
   existing issuer-positive test remains valid.
3. Run only the focused Debug/QA test targets and capture the expected RED
   failure before touching production source.
4. Change `ClientAuthRequestHandler.isValidFor()` to build candidates solely
   from every chain certificate's `issuerX500Principal.encoded`, retaining DER
   `MessageDigest.isEqual` matching and all other validation unchanged.
5. Run focused Debug/QA GREEN tests, then the relevant browser/security tests and
   the full Android/Python/Go/artifact/release policy gates required by the
   autonomous master plan.
6. Inspect the complete diff, run `git diff --check`, scan changed content for
   credentials/private keys and unsafe TLS/WebView weakening, and verify profile,
   release and threat-boundary invariants.
7. Update evidence documents only with observed results, create one atomic
   commit, push `agent/workspace-47-autonomous-20260803`, and verify the exact
   remote SHA and 0/0 divergence.
