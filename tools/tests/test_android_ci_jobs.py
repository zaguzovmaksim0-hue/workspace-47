"""Keep independent Android checks bounded without weakening the required gate."""
import itertools
import os
from pathlib import Path
import subprocess
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]


class AndroidCiJobsTest(unittest.TestCase):
    def setUp(self):
        self.workflow = yaml.load(
            (ROOT / ".github/workflows/ci.yml").read_text(), Loader=yaml.BaseLoader
        )
        self.jobs = self.workflow["jobs"]

    def commands(self, job_id):
        return "\n".join(step.get("run", "") for step in self.jobs[job_id]["steps"])

    def test_required_check_preserves_its_name_and_requires_both_independent_jobs(self):
        gate = self.jobs["android"]
        self.assertEqual("Android unit, lint, APK", gate["name"])
        self.assertEqual({"android-variants", "android-release"}, set(gate["needs"]))
        self.assertEqual("${{ always() }}", gate["if"])
        self.assertNotIn("continue-on-error", gate)
        self.assertLessEqual(int(gate["timeout-minutes"]), 5)

    def test_aggregate_executes_failure_for_every_non_success_dependency(self):
        gate = self.jobs["android"]
        checks = [step for step in gate["steps"] if "ANDROID_VARIANTS_RESULT" in step.get("env", {})]
        self.assertEqual(1, len(checks))
        check = checks[0]
        self.assertEqual("${{ needs.android-variants.result }}", check["env"]["ANDROID_VARIANTS_RESULT"])
        self.assertEqual("${{ needs.android-release.result }}", check["env"]["ANDROID_RELEASE_RESULT"])
        for variants, release in itertools.product(
            ("success", "failure", "cancelled", "skipped", ""), repeat=2
        ):
            with self.subTest(variants=variants, release=release):
                result = subprocess.run(
                    ["bash", "-c", check["run"]],
                    env=dict(os.environ, ANDROID_VARIANTS_RESULT=variants, ANDROID_RELEASE_RESULT=release),
                    text=True, capture_output=True, timeout=5,
                )
                self.assertEqual(variants == release == "success", result.returncode == 0)

    def test_variant_and_release_gates_preserve_all_checks_and_remain_parallel(self):
        variants = self.commands("android-variants")
        release = self.commands("android-release")
        for command in (
            "verifyResolvedCoreVersion verifyPortableAapt2Configuration",
            "assembleOptimized",
            "lintDebug lintQa lintOptimized",
            "testDebugUnitTest testQaUnitTest testOptimizedUnitTest",
            "assembleDebug assembleQa assembleQaAndroidTest",
            "scripts/ci/verify-android-artifacts.sh",
        ):
            self.assertIn(command, variants)
        for command in (
            "verifyResolvedCoreVersion verifyPortableAapt2Configuration",
            "verifyRuntimeDependencyLocks",
            "tools/collect_runtime_license_evidence.py",
            "scripts/ci/verify-release-fail-closed.sh",
            "testReleaseUnitTest lintRelease assembleRelease",
            "tools/verify_legal_bundle.py --apk app/build/outputs/apk/release/app-release.apk",
        ):
            self.assertIn(command, release)
        self.assertNotIn("testReleaseUnitTest", variants)
        for job_id in ("android-variants", "android-release"):
            job = self.jobs[job_id]
            self.assertNotIn("needs", job)
            self.assertNotIn("if", job)
            self.assertNotIn("continue-on-error", job)
            self.assertNotIn("environment", job)
            self.assertNotIn("secrets.", str(job))
            self.assertLessEqual(int(job["timeout-minutes"]), 45)
            checkout = job["steps"][0]
            self.assertEqual("${{ env.VERIFY_REF }}", checkout["with"]["ref"])
            self.assertEqual("false", checkout["with"]["persist-credentials"])
            self.assertNotIn("connectedQaAndroidTest", self.commands(job_id))

    def test_split_jobs_retain_disjoint_failure_reports_and_cleanup_ci_signer(self):
        report_names = []
        for job_id in ("android-variants", "android-release"):
            reports = [
                step for step in self.jobs[job_id]["steps"]
                if step.get("if") == "always()" and "test-results/" in step.get("with", {}).get("path", "")
            ]
            self.assertEqual(1, len(reports))
            report_names.append(reports[0]["with"]["name"])
        self.assertEqual(len(report_names), len(set(report_names)))
        release = self.commands("android-release")
        self.assertIn("trap 'rm -f \"$JFM_RELEASE_STORE_FILE\"' EXIT", release)


if __name__ == "__main__":
    unittest.main()
