from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/real-e2e.yml"
RUNNER = ROOT / "scripts/ci/run-real-e2e.sh"
REPORT_HELPER = ROOT / "scripts/ci/real_e2e_report.py"
INSTRUMENTATION = ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt"
CATALOG = ROOT / "app/src/main/res/raw/public_portal_catalog_v1.json"


class RealE2ePolicyTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def helper(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(REPORT_HELPER), *args],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
        )

    def test_real_e2e_is_manual_main_only_and_read_only(self) -> None:
        source = self.read(WORKFLOW)
        self.assertIn("workflow_dispatch:", source)
        self.assertNotIn("pull_request:", source)
        self.assertNotIn("pull_request_target:", source)
        self.assertNotIn("\n  push:\n", source)
        self.assertIn("permissions:\n  contents: read", source)
        self.assertIn("if: github.ref == 'refs/heads/main'", source)
        self.assertIn("environment: real-e2e", source)
        self.assertIn("group: real-e2e-${{ inputs.portal_id || 'catalog' }}", source)
        self.assertIn("cancel-in-progress: false", source)
        self.assertIn("ref: ${{ github.sha }}", source)
        self.assertIn("persist-credentials: false", source)
        self.assertIn('test "$GITHUB_REF" = refs/heads/main', source)
        self.assertIn('test "$(git rev-parse HEAD)" = "$GITHUB_SHA"', source)

    def test_real_e2e_actions_are_sha_pinned(self) -> None:
        source = self.read(WORKFLOW)
        for line in source.splitlines():
            stripped = line.strip()
            if not stripped.startswith("uses:"):
                continue
            reference = stripped.split("uses:", 1)[1].strip().split()[0]
            self.assertRegex(reference, r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
        self.assertIn(
            "ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d",
            source,
        )
        self.assertIn(
            "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
            source,
        )

    def test_real_e2e_secrets_are_scoped_to_the_emulator_step(self) -> None:
        source = self.read(WORKFLOW)
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_P12_B64"))
        self.assertEqual(1, source.count("secrets.REAL_E2E_CERT_PASSWORD"))
        build = source.index("Build QA test artifacts before credentials are exposed")
        credential = source.index("REAL_E2E_CERT_P12_B64:")
        self.assertLess(build, credential)
        self.assertIn("retention-days: 1", source)
        self.assertIn("build/reports/real-e2e/summary.json", source)
        self.assertIn("build/reports/real-e2e/summary.md", source)
        self.assertIn("build/reports/real-e2e/navigation/", source)
        self.assertNotIn("identity.p12", source)
        self.assertNotIn("password\n", source.lower())

    def test_real_e2e_webview_reset_starts_callback_apis_on_main_thread(self) -> None:
        source = self.read(ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt")
        start = source.index("private fun resetBrowserState()")
        end = source.index("private fun shell(", start)
        body = source[start:end]
        self.assertGreaterEqual(body.count("instrumentation.runOnMainSync"), 2)
        self.assertIn("CookieManager.getInstance().removeAllCookies", body)
        self.assertIn("WebView.clearClientCertPreferences", body)
        self.assertIn("cookies.await(5, TimeUnit.SECONDS)", body)
        self.assertIn("clientCert.await(5, TimeUnit.SECONDS)", body)

    def test_instrumented_probe_skips_without_opt_in_but_persists_enabled_failures(self) -> None:
        source = self.read(ROOT / "app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt")
        opt_in_skip = source.index('assumeTrue("REAL_E2E requires explicit opt-in", explicitlyEnabled)')
        portal_check = source.index('require(PORTAL_ID_PATTERN.matches(portalId)) { "REAL_E2E_INVALID_PORTAL_ID" }')
        result_init = source.index("val result = ProbeResult(")
        fixture_check = source.index('require(certificateFile.isFile) { "REAL_E2E_CERTIFICATE_MISSING" }')
        final_write = source.index("writeResult(result)")
        self.assertLess(opt_in_skip, portal_check)
        self.assertLess(portal_check, result_init)
        self.assertLess(result_init, fixture_check)
        self.assertLess(fixture_check, final_write)
        self.assertEqual(1, source.count("assumeTrue("))
        self.assertIn('"CERTIFICATE_MISSING"', source)
        self.assertIn('"PASSWORD_MISSING"', source)
        self.assertIn("safeInfrastructureCode(throwable, probeStage)", source)

    def test_runner_streams_credentials_without_remote_shell_redirection(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn('adb_bounded shell run-as "$PACKAGE_NAME" mkdir -p "$FIXTURE_DIR"', runner)
        self.assertIn('adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$CERTIFICATE_PATH" bs=4096', runner)
        self.assertIn('adb_bounded shell -T run-as "$PACKAGE_NAME" dd of="$PASSWORD_PATH" bs=4096', runner)
        self.assertIn('chmod 600 "$CERTIFICATE_PATH" "$PASSWORD_PATH"', runner)
        self.assertIn('progress STAGE_CERT_WRITE_START', runner)
        self.assertIn('progress STAGE_PASSWORD_WRITE_START', runner)
        self.assertIn('progress STAGE_STAT_DONE', runner)
        self.assertNotIn('run-as "$PACKAGE_NAME" sh -c', runner)
        self.assertNotIn("cat > '$CERTIFICATE_PATH'", runner)
        self.assertNotIn("cat > '$PASSWORD_PATH'", runner)

    def test_runner_bounds_adb_operations_outside_instrumentation(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn("readonly ADB_TIMEOUT_SECONDS=30", runner)
        self.assertIn("readonly ADB_INSTALL_TIMEOUT_SECONDS=120", runner)
        self.assertIn('timeout --signal=TERM --kill-after=5s "${ADB_TIMEOUT_SECONDS}s" adb "$@"', runner)
        self.assertIn('timeout --signal=TERM --kill-after=5s "${ADB_INSTALL_TIMEOUT_SECONDS}s" adb install --no-streaming -r "$1"', runner)
        self.assertIn('adb_bounded exec-out run-as "$PACKAGE_NAME" cat "$RESULT_PATH"', runner)
        self.assertIn('adb_bounded exec-out run-as "$PACKAGE_NAME" cat files/qa-navigation.log', runner)
        self.assertIn('adb_bounded shell am force-stop "$PACKAGE_NAME"', runner)
        self.assertIn('timeout --signal=TERM --kill-after=10s "${PORTAL_TIMEOUT_SECONDS}s" \\', runner)
        self.assertIn('      adb shell am instrument -w -r \\', runner)
        self.assertNotIn('\n  adb install -r ', runner)
        self.assertNotIn('\n    adb exec-out ', runner)
        self.assertNotIn('\n    adb shell run-as ', runner)

    def test_progress_and_partial_results_are_validated_before_upload(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        helper = self.read(REPORT_HELPER)
        self.assertIn('PROGRESS_PATH="$REPORT_DIR/progress.tsv"', runner)
        self.assertIn('progress INSTALL_QA_START', runner)
        self.assertIn('progress INSTALL_QA_DONE', runner)
        self.assertIn('progress INSTALL_TEST_START', runner)
        self.assertIn('progress STAGE_FIXTURE_START', runner)
        self.assertIn('progress STAGE_MKDIR_DONE', runner)
        self.assertIn('progress STAGE_CERT_WRITE_START', runner)
        self.assertIn('progress STAGE_CERT_WRITE_DONE', runner)
        self.assertIn('progress STAGE_PASSWORD_WRITE_DONE', runner)
        self.assertIn('progress STAGE_STAT_DONE', runner)
        self.assertIn('::notice title=REAL_E2E progress::', runner)
        self.assertIn('progress PORTAL_START', runner)
        self.assertIn('progress INSTRUMENT_START', runner)
        self.assertIn('progress RESULT_READ_START', runner)
        self.assertIn('describe-result --result "$result_file" --portal "$portal_id"', runner)
        self.assertIn('progress NAV_READ_START', runner)
        self.assertIn('progress PORTAL_DONE', runner)
        self.assertIn('validate-progress', workflow)
        self.assertIn('validate-partial-results', workflow)
        self.assertIn('build/reports/real-e2e/progress.tsv', workflow)
        self.assertIn('build/reports/real-e2e/results/', workflow)
        self.assertIn("PROGRESS_STAGES", helper)
        self.assertIn("validate_progress", helper)
        self.assertIn("validate_partial_results", helper)

    def test_runner_uses_private_fixture_and_never_clears_app_data(self) -> None:
        source = self.read(RUNNER)
        self.assertTrue(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"))
        self.assertIn('FIXTURE_DIR="files/real-e2e"', source)
        self.assertIn('run-as "$PACKAGE_NAME"', source)
        self.assertIn("base64 --decode", source)
        self.assertIn("unset REAL_E2E_CERT_P12_B64 REAL_E2E_CERT_PASSWORD", source)
        self.assertIn('rm -rf "$FIXTURE_DIR"', source)
        self.assertNotIn("pm clear", source)
        self.assertNotIn("set -x", source)
        self.assertNotIn("echo $REAL_E2E", source)
        self.assertNotIn("printenv", source)

    def test_runner_selects_catalog_and_supports_one_portal_retry(self) -> None:
        workflow = self.read(WORKFLOW)
        runner = self.read(RUNNER)
        self.assertIn("portal_id:", workflow)
        self.assertIn("deep_auth_signing:", workflow)
        self.assertIn('PORTAL_ID_FILTER: ${{ inputs.portal_id }}', workflow)
        self.assertIn('REAL_E2E_DEEP_AUTH_SIGNING: ${{ inputs.deep_auth_signing }}', workflow)
        self.assertIn('"$REPORT_HELPER" select', runner)
        self.assertIn('--portal "${PORTAL_ID_FILTER:-}"', runner)
        self.assertIn('--shard-index "$shard_index"', runner)
        self.assertIn('--shard-total "$shard_total"', runner)
        self.assertIn('for portal_id in "${portal_ids[@]}"', runner)
        self.assertIn("-e portalId", runner)
        self.assertIn("-e realE2e true", runner)
        self.assertIn("-e realE2eDeep", runner)

    def test_targeted_portal_uses_exactly_one_real_shard(self) -> None:
        workflow = self.read(WORKFLOW)
        self.assertIn("include: ${{ fromJSON(inputs.portal_id != ''", workflow)
        self.assertIn('{"shard_number":1,"shard_index":0,"shard_total":1}', workflow)
        self.assertIn('REAL_E2E_SHARD_TOTAL: ${{ matrix.shard_total }}', workflow)
        self.assertIn('shard ${{ matrix.shard_number }}/${{ matrix.shard_total }}', workflow)
        targeted = self.helper(
            "select",
            "--catalog",
            str(CATALOG),
            "--portal",
            "junta-andalucia-carne-joven",
            "--shard-index",
            "0",
            "--shard-total",
            "1",
        )
        self.assertEqual(0, targeted.returncode, targeted.stderr)
        self.assertEqual(["junta-andalucia-carne-joven"], targeted.stdout.splitlines())

    def test_workflow_runs_six_parallel_non_overlapping_shards_for_full_catalog(self) -> None:
        workflow = self.read(WORKFLOW)
        self.assertIn("strategy:\n      fail-fast: false\n      matrix:\n", workflow)
        self.assertEqual(6, workflow.count('"shard_total":6'))
        self.assertIn('REAL_E2E_SHARD_INDEX: ${{ matrix.shard_index }}', workflow)
        self.assertIn('REAL_E2E_SHARD_TOTAL: ${{ matrix.shard_total }}', workflow)
        self.assertIn('shard-${{ matrix.shard_number }}', workflow)

        shards = []
        for index in range(6):
            completed = self.helper(
                "select",
                "--catalog",
                str(CATALOG),
                "--shard-index",
                str(index),
                "--shard-total",
                "6",
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            shard = completed.stdout.splitlines()
            self.assertIn(len(shard), {30, 31})
            shards.extend(shard)
        self.assertEqual(183, len(shards))
        self.assertEqual(183, len(set(shards)))

    def test_carne_joven_recipe_is_exact_and_same_origin_only(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('CARNE_JOVEN_PORTAL_ID = "junta-andalucia-carne-joven"', source)
        self.assertIn(
            '"https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"',
            source,
        )
        self.assertIn(
            '"/carneJoven/servlet/CallAuthenticationServlet"',
            source,
        )
        self.assertIn('when (portalId)', source)
        self.assertIn('currentUrl != null && currentUrl != expectedCurrentUrl', source)
        self.assertIn('CARNE_JOVEN_AUTH_LINK_ID = "bot-obtener"', source)
        self.assertIn('const element = document.getElementById($quotedId)', source)
        self.assertIn("element.getAttribute('href') !== $quotedExpectedHref", source)
        self.assertIn('element.click()', source)
        self.assertIn('"1" -> clicked = true', source)
        self.assertIn('"2" -> failure = "REAL_E2E_RECIPE_TARGET_MISMATCH"', source)
        self.assertIn('val quotedExpectedHref = JSONObject.quote(expectedHref)', source)
        self.assertIn('REAL_E2E_RECIPE_TARGET_MISMATCH', source)
        self.assertIn('SystemClock.sleep(RECIPE_POLL_MILLIS)', source)
        self.assertIn('REAL_E2E_RECIPE_TARGET_TIMEOUT', source)
        self.assertIn('RECIPE_TARGET_TIMEOUT', source)
        self.assertNotIn('window.location.assign(', source)
        self.assertNotIn('targetUrl = portalId', source)

    def test_ovorion_auth_recipe_clicks_only_the_exact_login_button(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('OVORION_PORTAL_ID = "junta-andalucia-ovorion"', source)
        self.assertIn(
            '"https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs"',
            source,
        )
        self.assertIn('OVORION_AUTH_BUTTON_ID = "btnacceso"', source)
        self.assertIn('OVORION_AUTH_BUTTON_VALUE = "Acceder"', source)
        self.assertIn('OVORION_AUTH_BUTTON_ONCLICK = "autenticar();"', source)
        self.assertIn("element.getAttribute('type') !== 'button'", source)
        self.assertIn('element.value !== $quotedExpectedValue', source)
        self.assertIn("element.getAttribute('onclick') !== $quotedExpectedOnClick", source)
        self.assertIn('OVORION_PORTAL_ID -> clickExactAuthButton(', source)
        self.assertIn('element.click()', source)

    def test_ofvirtual_auth_recipe_clicks_only_the_exact_login_button(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('OFVIRTUAL_PORTAL_ID = "junta-andalucia-ofvirtual"', source)
        self.assertIn(
            '"https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs"',
            source,
        )
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_ID = "btnacceso"', source)
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_VALUE = "Acceder"', source)
        self.assertIn('OFVIRTUAL_AUTH_BUTTON_ONCLICK = "autenticar();"', source)
        self.assertIn('OFVIRTUAL_PORTAL_ID -> clickExactAuthButton(', source)

    def test_badajoz_and_lleida_recipes_use_reviewed_certificate_controls(self) -> None:
        source = self.read(INSTRUMENTATION)
        for expected in (
            'BADAJOZ_PORTAL_ID = "diputacion-badajoz-portal"',
            'BADAJOZ_LOGIN_PAGE_URL =',
            'BADAJOZ_LOGIN_LINK_ID = "login"',
            'BADAJOZ_LOGIN_LINK_HREF = "javascript: abrirLogin(\'\');"',
            'BADAJOZ_CERT_BUTTON_ID = "firmar"',
            'BADAJOZ_CERT_BUTTON_LABEL = "Certificado digital"',
            'BADAJOZ_CERT_BUTTON_ONCLICK = "pulsarFirmarIdentificate();"',
            'LLEIDA_PORTAL_ID = "diputacion-lleida-sede"',
            'LLEIDA_LOGIN_PAGE_URL =',
            'LLEIDA_LOGIN_LINK_ID = "login"',
            'LLEIDA_LOGIN_LINK_HREF = "javascript: abrirLogin(\'\');"',
            'LLEIDA_CERT_BUTTON_ID = "btnValid"',
            'LLEIDA_CERT_BUTTON_ARIA_LABEL = "VALid"',
            'LLEIDA_CERT_BUTTON_ONCLICK = "javascript: pulsarLoginValid();"',
            'BADAJOZ_PORTAL_ID -> {',
            'LLEIDA_PORTAL_ID -> {',
            'waitForExpectedUrl = true',
            'LLEIDA_LOGIN_LINK_HREF',
            'private fun clickExactButton(',
            'recipeUrlMatches(',
            "element.getAttribute('aria-label') !== expectedAriaLabel",
        ):
            self.assertIn(expected, source)

    def test_auth_recipe_surfaces_terminal_navigation_failure_before_timeout(self) -> None:
        source = self.read(INSTRUMENTATION)
        helper = source[source.index('private fun clickExactAuthButton('):]
        helper = helper[:helper.index('private fun observePortal(')]
        self.assertIn('const val RECIPE_TERMINAL_GRACE_MILLIS = 5_000L', source)
        self.assertIn('hasObservedTerminalNavigationFailure()', helper)
        for event in (
            'event=NETWORK_ERROR',
            'event=SSL_ERROR_CANCELLED',
            'event=NAVIGATION_BLOCKED',
        ):
            self.assertIn(event, helper)
        self.assertIn('RECIPE_TERMINAL_GRACE_MILLIS', helper)

    def test_sevilla_recipe_clicks_only_the_exact_certificate_anchor(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('SEVILLA_PORTAL_ID = "sevilla-sede"', source)
        self.assertIn(
            '"https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"',
            source,
        )
        self.assertIn('SEVILLA_AUTH_CONTAINER_ID = "divBotonCertificado"', source)
        self.assertIn('SEVILLA_AUTH_LABEL = "Acceder"', source)
        self.assertIn('SEVILLA_AUTH_HREF = "#"', source)
        self.assertIn('SEVILLA_AUTH_ONCLICK = "doSign();"', source)
        self.assertIn('SEVILLA_PORTAL_ID -> clickExactContainedAnchor(', source)
        self.assertIn('element.innerText', source)
        self.assertIn('element.getAttribute(\'href\') !== $quotedExpectedHref', source)
        self.assertIn('element.getAttribute(\'onclick\') !== $quotedExpectedOnClick', source)
        self.assertIn('elements.length !== 1', source)
        self.assertIn("event => event.preventDefault()", source)
        self.assertIn("element.addEventListener('click', preventDefault, { once: true })", source)
        self.assertIn('element.click()', source)

    def test_diputacion_sevilla_recipe_reaches_the_reviewed_certificate_login(self) -> None:
        source = self.read(INSTRUMENTATION)
        for expected in (
            'DIPUTACION_SEVILLA_PORTAL_ID = "diputacion-sevilla-sede"',
            'DIPUTACION_SEVILLA_INDEX_URL =',
            'DIPUTACION_SEVILLA_AUTH_URL =',
            'DIPUTACION_SEVILLA_AUTH_LABEL = "Identificarse"',
            'DIPUTACION_SEVILLA_AUTH_HREF =',
            'DIPUTACION_SEVILLA_AUTH_BUTTON_LABEL = "ACCEDER"',
            'DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK = "loginClave();"',
            'DIPUTACION_SEVILLA_PORTAL_ID -> {',
            'expectedLabel = DIPUTACION_SEVILLA_AUTH_LABEL',
            'expectedHref = DIPUTACION_SEVILLA_AUTH_HREF',
            'expectedOnClick = DIPUTACION_SEVILLA_AUTH_BUTTON_ONCLICK',
        ):
            self.assertIn(expected, source)

    def test_unizar_recipe_targets_the_certificate_button_inside_its_container(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('UNIZAR_PORTAL_ID = "unizar-tramitador"', source)
        self.assertIn(
            '"https://tramita.unizar.es/tramitador/ciudadano?entrada=ciudadano&fkIdioma=es&idEntidad=ROOT&idLogica=loginComponent"',
            source,
        )
        self.assertIn('UNIZAR_AUTH_CONTAINER_ID = "capaAccesoCertificado"', source)
        self.assertIn('UNIZAR_AUTH_ELEMENT_ID = "entrar"', source)
        self.assertIn(
            'const val UNIZAR_AUTH_LABEL =\n            "Pulse para ejecutar Autofirma e identificarse con certificado."',
            source,
        )
        self.assertIn('UNIZAR_AUTH_IMAGE_ALT = "certificado login"', source)
        self.assertIn('UNIZAR_AUTH_ONCLICK = "lanza();"', source)
        self.assertIn('UNIZAR_PORTAL_ID -> clickExactContainedButton(', source)
        self.assertIn("container.querySelectorAll('button')", source)
        self.assertIn("element.getAttribute('aria-label') !== $quotedExpectedLabel", source)
        self.assertIn("element.querySelector('img')?.getAttribute('alt') !== $quotedExpectedImageAlt", source)
        self.assertIn("elements.length !== 1", source)
        self.assertIn('element.click()', source)
        helper = source[source.index('private fun clickExactContainedButton('):]
        helper = helper[:helper.index('private fun clickExactAuthButton(')]
        self.assertIn('var targetMismatchObserved = false', helper)
        self.assertIn('"2" -> targetMismatchObserved = true', helper)
        self.assertIn('if (targetMismatchObserved)', helper)
        self.assertIn('"REAL_E2E_RECIPE_TARGET_MISMATCH"', helper)

    def test_auth_sign_waits_for_bounded_post_sign_observation(self) -> None:
        source = self.read(INSTRUMENTATION)
        report = self.read(REPORT_HELPER)
        self.assertIn('const val POST_SIGN_TIMEOUT_MILLIS = 30_000L', source)
        for field in (
            "postSignNavigationObserved",
            "postSignPageFinished",
            "postSignCallbackObserved",
            "postSignHostChanged",
            "postSignPathChanged",
            "authenticatedReturnObserved",
            "signingCallbackObserved",
            "postSignPortalAuthSuccess",
        ):
            self.assertIn(field, source)
            self.assertIn(field, report)
        observation = source[source.index("private fun isObservationComplete("):]
        observation = observation[:observation.index("private fun classify(")]
        self.assertIn("postSignObservationDeadline", observation)
        self.assertLess(
            observation.index("postSignObservationDeadline ?: return false"),
            observation.index("return result.pageFinished"),
        )
        self.assertIn("PASS_REAL_CRYPTO_SIGN", source)
        self.assertIn('"PASS_REAL_CRYPTO_SIGN"', report)
        self.assertIn("const val RESULT_SCHEMA_VERSION = 2", source)
        self.assertIn("RESULT_SCHEMA_VERSION = 2", report)
        self.assertIn("result.postSignNavigationObserved", source)
        self.assertIn("result.authenticatedReturnObserved", source)
        self.assertIn("result.signingCallbackObserved", source)
        self.assertIn("result.postSignPortalAuthSuccess", source)
        delta = source[source.index("private fun recordsAddedSince("):]
        delta = delta[:delta.index("private fun updateRecordObservations(")]
        self.assertIn("current.take(previous.size) == previous", delta)
        self.assertIn("current.drop(previous.size)", delta)
        signing_wait = source[source.index("private fun waitForSigningTerminalState("):]
        signing_wait = signing_wait[:signing_wait.index("private fun updateSigningEvidence(")]
        self.assertNotIn("updatePostSignObservations", signing_wait)
        self.assertIn("updateSigningEvidence", signing_wait)
        self.assertIn("return diagnosticRecords()", signing_wait)
        self.assertIn(
            "updateSigningEvidence(completedRecords, signingEvidenceTracker, result)",
            source,
        )
        sign_branch = source[source.index("if (deepEnabled && profileId in SAFE_AUTH_SIGN_PROFILES)") :]
        sign_branch = sign_branch[:sign_branch.index("} else {")]
        self.assertLess(
            sign_branch.index("val completedRecords = waitForSigningTerminalState("),
            sign_branch.index("val tracker = PostSignTracker("),
        )
        self.assertIn("latestMainFrameNavigation(completedRecords)", sign_branch)
        classify = source[source.index("private fun classify("):]
        classify = classify[:classify.index("private fun allowedClientAuthHosts(")]
        self.assertIn("result.postSignPortalAuthSuccess", classify)
        self.assertNotIn("(result.portalAuthSuccess || result.authenticatedReturnObserved)", classify)
        self.assertIn("result.signingCallbackObserved", classify)
        navigation_regex = source[source.index("val SANITIZED_NAVIGATION_EVENT"):]
        navigation_regex = navigation_regex[:navigation_regex.index("val SAFE_AUTH_SIGN_PROFILES")]
        self.assertNotIn("NETWORK_REQUEST", navigation_regex)

    def test_runner_allows_the_post_sign_window_to_finish(self) -> None:
        runner = self.read(RUNNER)
        self.assertIn("readonly PORTAL_TIMEOUT_SECONDS=210", runner)
        self.assertIn(
            "75s portal observation + 90s signing + 30s post-sign observation + 15s margin",
            runner,
        )

    def test_real_e2e_waits_for_owned_webview_via_catalog_inspect(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('catalogSmoke(portalId, "INSPECT").contains("WEBVIEW_ACTIVE")', source)
        self.assertIn('updateCurrentHostFromRecords(records, result)', source)
        self.assertIn('SANITIZED_HOST', source)
        self.assertNotIn('activity.window.decorView', source)
        self.assertNotIn('findWebView(', source)
        self.assertNotIn('updateCurrentWebView(', source)

    def test_instrumentation_requires_explicit_opt_in_and_has_no_level_six(self) -> None:
        source = self.read(INSTRUMENTATION)
        self.assertIn('arguments.getString(REAL_E2E_ARGUMENT) == "true"', source)
        self.assertIn('arguments.getString(PORTAL_ID_ARGUMENT)', source)
        self.assertIn("ProbeClassification", source)
        self.assertNotIn("level = 6", source)
        self.assertNotIn("maxOf(result.level, 6)", source)
        self.assertIn("signingCancelledAtBoundary", source)

    def test_consequential_portals_are_classified_without_deep_automation(self) -> None:
        source = self.read(INSTRUMENTATION)
        report = self.read(REPORT_HELPER)
        self.assertIn('val CONSEQ_RECIPE_PROFILES = setOf(', source)
        self.assertIn('"reg-age-redsara"', source)
        self.assertIn('ProbeClassification.BLOCKED_CONSEQUENTIAL_ACTION', source)
        self.assertIn('result.profileId in CONSEQ_RECIPE_PROFILES', source)
        self.assertIn('"BLOCKED_CONSEQUENTIAL_ACTION"', report)

    def test_administrative_signing_profiles_are_not_deep_sign_allowlisted(self) -> None:
        source = self.read(INSTRUMENTATION)
        allowlist = source.split("val SAFE_AUTH_SIGN_PROFILES = setOf(", 1)[1].split(")", 1)[0]
        for forbidden in (
            "reg-age-redsara",
            "jccm-registro-generico",
            "airef-instancia-general",
            "ministerio-economia-instancia-generica",
            "xunta-galicia-solicitude-xenerica",
            "age-portal-de-la-transparencia",
            "caib-portafib",
        ):
            self.assertNotIn(f'"{forbidden}"', allowlist)
        for expected in (
            "junta-andalucia",
            "unizar-tramitador",
            "junta-ofvirtual",
            "aragon-siraw",
        ):
            self.assertIn(f'"{expected}"', allowlist)

    def test_report_helper_selects_exactly_the_current_catalog(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        expected = [entry["portalId"] for entry in catalog["entries"]]
        completed = self.helper("select", "--catalog", str(CATALOG))
        self.assertEqual(0, completed.returncode, completed.stderr)
        actual = completed.stdout.splitlines()
        self.assertEqual(expected, actual)
        self.assertEqual(183, len(actual))

    def test_report_helper_single_portal_filter_is_exact(self) -> None:
        selected = "junta-andalucia-carne-joven"
        completed = self.helper("select", "--catalog", str(CATALOG), "--portal", selected)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual([selected], completed.stdout.splitlines())
        rejected = self.helper("select", "--catalog", str(CATALOG), "--portal", "not-a-real-portal")
        self.assertNotEqual(0, rejected.returncode)

    def test_progress_validator_accepts_only_sanitized_checkpoint_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "progress.tsv"
            path.write_text(
                "2026-08-31T22:00:00Z\t0\t1/31\tjunta-andalucia-carne-joven\tPORTAL_START\t42\n"
                "2026-08-31T22:00:01Z\t0\t1/31\tjunta-andalucia-carne-joven\tINSTRUMENT_DONE_0\t43\n",
                encoding="ascii",
            )
            accepted = self.helper("validate-progress", "--progress", str(path))
            self.assertEqual(0, accepted.returncode, accepted.stderr)
            path.write_text(
                "2026-08-31T22:00:00Z\t0\t1/31\tjunta-andalucia-carne-joven\tPASSWORD=my-secret\t42\n",
                encoding="ascii",
            )
            rejected = self.helper("validate-progress", "--progress", str(path))
            self.assertNotEqual(0, rejected.returncode)

    def test_navigation_validator_accepts_sanitized_mini_applet_bridge_events(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "navigation.log"
            path.write_text(
                "timestamp=2026-09-01T19:38:25Z "
                "event=MINIAPPLET_BRIDGE origin=sede.dip-badajoz.es "
                "stage=SHIM_SCRIPT_INSTALLED algorithm=invalid format=invalid\n"
                "timestamp=2026-09-01T19:38:26Z "
                "event=MINIAPPLET_BRIDGE origin=sede.dip-badajoz.es "
                "stage=REJECTED algorithm=invalid format=invalid "
                "error=UNSUPPORTED_PROTOCOL\n",
                encoding="ascii",
            )
            accepted = self.helper("validate-log", "--log", str(path))
            self.assertEqual(0, accepted.returncode, accepted.stderr)

            path.write_text(
                "timestamp=2026-09-01T19:38:25Z event=UNSAFE_EVENT\n",
                encoding="ascii",
            )
            rejected = self.helper("validate-log", "--log", str(path))
            self.assertNotEqual(0, rejected.returncode)

    def test_badajoz_hook_diagnostics_are_sanitized_and_bounded(self) -> None:
        source = self.read(ROOT / "app/src/main/res/raw/afirma_shim.js")
        self.assertIn('postShimDiagnostic("BADAJOZ_LATE_REWRAP_STARTED")', source)
        self.assertIn('postShimDiagnostic("BADAJOZ_SIGN_HOOK_READY")', source)
        self.assertIn('window.setInterval(rewrapLateBadajozGlobals, 250)', source)
        self.assertIn('window.setTimeout(() => window.clearInterval(lateRewrapTimer), signTimeoutMillis)', source)
        self.assertNotIn('certificate', source[source.index('BADAJOZ_LATE_REWRAP_STARTED'):])

    def test_select_emits_no_blank_record_for_empty_filtered_shard(self) -> None:
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        portal = catalog["entries"][0]["portalId"]
        populated = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", portal,
            "--shard-index", "0", "--shard-total", "6",
        )
        self.assertEqual(0, populated.returncode, populated.stderr)
        self.assertEqual(portal + "\n", populated.stdout)
        empty = self.helper(
            "select", "--catalog", str(CATALOG), "--portal", portal,
            "--shard-index", "5", "--shard-total", "6",
        )
        self.assertEqual(0, empty.returncode, empty.stderr)
        self.assertEqual("", empty.stdout)

    def test_describe_result_emits_only_validated_safe_tokens(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic", "--catalog", str(CATALOG),
                "--portal", "junta-andalucia-carne-joven",
                "--output", str(path), "--reason", "RESULT_MISSING",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            data["infrastructureError"] = "AssertionError"
            path.write_text(json.dumps(data), encoding="ascii")
            described = self.helper(
                "describe-result", "--result", str(path),
                "--portal", "junta-andalucia-carne-joven",
            )
            self.assertEqual(0, described.returncode, described.stderr)
            self.assertEqual(
                "RESULT_DIAGNOSTIC portal=junta-andalucia-carne-joven "
                "classification=INFRASTRUCTURE_ERROR level=0 "
                "infrastructure=AssertionError signing=NONE",
                described.stdout.strip(),
            )

    def test_report_helper_rejects_secret_like_extra_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            synthetic = self.helper(
                "synthetic",
                "--catalog",
                str(CATALOG),
                "--portal",
                "junta-andalucia-carne-joven",
                "--output",
                str(path),
                "--reason",
                "RESULT_MISSING",
            )
            self.assertEqual(0, synthetic.returncode, synthetic.stderr)
            data = json.loads(path.read_text(encoding="ascii"))
            data["certificatePassword"] = "must-never-export"
            path.write_text(json.dumps(data), encoding="ascii")
            validated = self.helper(
                "validate-result",
                "--result",
                str(path),
                "--portal",
                "junta-andalucia-carne-joven",
            )
            self.assertNotEqual(0, validated.returncode)


if __name__ == "__main__":
    unittest.main()
