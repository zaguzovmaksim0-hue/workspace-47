from __future__ import annotations

import hashlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI = ROOT / ".github/workflows/ci.yml"
SECURITY = ROOT / ".github/workflows/security.yml"
DEPENDABOT = ROOT / ".github/dependabot.yml"
GITLEAKS = ROOT / ".gitleaks.toml"
VERIFY_APKS = ROOT / "scripts/ci/verify-android-artifacts.sh"
VERIFY_RELEASE = ROOT / "scripts/ci/verify-release-fail-closed.sh"
VERIFICATION_METADATA = ROOT / "gradle/verification-metadata.xml"
GRADLE_WRAPPER_PROPERTIES = ROOT / "gradle/wrapper/gradle-wrapper.properties"
GRADLE_WRAPPER_JAR = ROOT / "gradle/wrapper/gradle-wrapper.jar"
GO_MOD = ROOT / "ws024-relay/go.mod"

PINNED_ACTION = re.compile(r"^\s*-?\s*uses:\s*([\w.-]+/[\w./-]+)@([0-9a-f]{40})\s*(?:#.*)?$", re.M)
ANY_ACTION = re.compile(r"^\s*-?\s*uses:\s*([^\s#]+)", re.M)


class CiPolicyTest(unittest.TestCase):
    def read(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"required CI file is missing: {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def test_required_ci_files_exist(self) -> None:
        for path in (
            CI,
            SECURITY,
            DEPENDABOT,
            GITLEAKS,
            VERIFY_APKS,
            VERIFY_RELEASE,
            VERIFICATION_METADATA,
            GRADLE_WRAPPER_PROPERTIES,
            GRADLE_WRAPPER_JAR,
            GO_MOD,
        ):
            self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")

    def test_workflows_are_read_only_and_sha_pinned(self) -> None:
        allowed = {
            "actions/checkout",
            "actions/setup-java",
            "actions/setup-go",
            "actions/setup-python",
            "gradle/actions/setup-gradle",
        }
        for path in (CI, SECURITY):
            source = self.read(path)
            self.assertIn("permissions:\n  contents: read", source)
            self.assertNotIn("pull_request_target:", source)
            self.assertNotRegex(source, r"permissions:\s*write-all")
            all_uses = ANY_ACTION.findall(source)
            pinned = PINNED_ACTION.findall(source)
            self.assertEqual(len(all_uses), len(pinned), f"unpinned action in {path.name}")
            self.assertTrue(all_uses, f"no actions in {path.name}")
            self.assertTrue({name for name, _ in pinned}.issubset(allowed))
            self.assertIn("persist-credentials: false", source)

    def test_ci_runs_android_python_go_and_release_fail_closed_gates(self) -> None:
        source = self.read(CI)
        for required in (
            "testDebugUnitTest testQaUnitTest",
            "lintDebug lintQa",
            "assembleDebug assembleQa assembleQaAndroidTest",
            "python -m unittest discover -s tools/tests",
            "go test ./... -race -count=1",
            "go vet ./...",
            "govulncheck ./...",
            "scripts/ci/verify-android-artifacts.sh",
            "scripts/ci/verify-release-fail-closed.sh",
        ):
            self.assertIn(required, source)
        self.assertIn("timeout-minutes:", source)
        self.assertIn('GO_VERSION: "1.26.5"', source)
        self.assertIn("concurrency:", source)
        self.assertNotIn("cache-dependency-path: ws024-relay/go.sum", source)
        self.assertIn("cache: false", source)
        self.assertIn("set +o pipefail", source)
        self.assertIn("set -o pipefail", source)

    def test_security_workflow_scans_history_and_dependencies_with_pinned_tools(self) -> None:
        source = self.read(SECURITY)
        self.assertIn("fetch-depth: 0", source)
        self.assertIn("gitleaks_8.30.1_linux_x64.tar.gz", source)
        self.assertIn("551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb", source)
        self.assertIn("gitleaks git --redact --no-banner", source)
        self.assertIn("github.com/google/osv-scanner/v2/cmd/osv-scanner@v2.3.8", source)
        self.assertIn('go-version: "1.26.5"', source)
        self.assertIn(
            "osv-scanner scan source --lockfile tools/requirements.txt --lockfile ws024-relay/go.mod",
            source,
        )
        self.assertNotIn("osv-scanner scan source -r .", source)
        self.assertIn("schedule:", source)

    def test_go_module_requires_the_patched_toolchain(self) -> None:
        source = self.read(GO_MOD)
        self.assertRegex(source, r"(?m)^go 1\.26\.5$")

    def test_dependabot_covers_all_package_managers(self) -> None:
        source = self.read(DEPENDABOT)
        self.assertEqual(source.count('package-ecosystem: "gradle"'), 1)
        self.assertEqual(source.count('package-ecosystem: "gomod"'), 1)
        self.assertEqual(source.count('package-ecosystem: "github-actions"'), 1)
        self.assertIn('directory: "/ws024-relay"', source)
        self.assertGreaterEqual(source.count('interval: "weekly"'), 3)

    def test_gitleaks_allowlist_is_exact_and_does_not_disable_rules(self) -> None:
        source = self.read(GITLEAKS)
        self.assertIn("useDefault = true", source)
        self.assertNotIn("disabledRules", source)
        self.assertIn('targetRules = ["generic-api-key"]', source)
        self.assertIn('condition = "AND"', source)
        self.assertIn('regexTarget = "line"', source)
        self.assertIn("all-spanish-public-portals-inventory", source)
        self.assertNotRegex(source, r"paths\s*=\s*\[\s*['\"]\.\*['\"]")

    def test_artifact_scripts_are_fail_closed(self) -> None:
        apk = self.read(VERIFY_APKS)
        release = self.read(VERIFY_RELEASE)
        for source in (apk, release):
            self.assertTrue(source.startswith("#!/usr/bin/env bash\nset -euo pipefail\n"))
        for required in (
            "-c -p -v 4",
            "verify --verbose --print-certs",
            "Verified using v2 scheme",
            "Number of signers: 1",
            "android:allowBackup",
            "android:usesCleartextTraffic",
            "forbidden canary",
        ):
            self.assertIn(required, apk)
        self.assertIn("Private release signing is required", release)
        self.assertIn("assembleRelease", release)
        self.assertIn("app-release.apk", release)

    def test_dependency_verification_uses_sha256_and_has_no_trusted_wildcard(self) -> None:
        source = self.read(VERIFICATION_METADATA)
        self.assertIn("<verify-metadata>true</verify-metadata>", source)
        self.assertIn("<verify-signatures>false</verify-signatures>", source)
        self.assertIn("<sha256 value=", source)
        self.assertNotIn("trusted-artifacts", source)
        self.assertNotIn('regex="true"', source)

    def test_gradle_wrapper_distribution_is_checksum_pinned(self) -> None:
        source = self.read(GRADLE_WRAPPER_PROPERTIES)
        self.assertRegex(source, r"(?m)^distributionUrl=https\\://services\.gradle\.org/distributions/gradle-9\.4\.1-bin\.zip$")
        self.assertRegex(source, r"(?m)^distributionSha256Sum=[0-9a-f]{64}$")
        wrapper_sha256 = hashlib.sha256(GRADLE_WRAPPER_JAR.read_bytes()).hexdigest()
        self.assertEqual(
            wrapper_sha256,
            "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c",
        )


if __name__ == "__main__":
    unittest.main()
