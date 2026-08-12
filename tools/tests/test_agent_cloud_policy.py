from pathlib import Path
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AgentCloudPolicyTest(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_agents_points_to_matt_and_cloud(self) -> None:
        text = self.read("AGENTS.md")
        self.assertIn("Matt Pocock", text)
        self.assertIn("docs/agents/matt-pocock-workflow.md", text)
        self.assertIn("docs/agents/codex-cloud-gradle.md", text)
        self.assertIn("explicit operator authorization", text)

    def test_context_uses_stable_main_pull_request_workflow(self) -> None:
        text = self.read("CONTEXT.md")
        self.assertIn("`origin/main`", text)
        self.assertIn("fresh branch", text)
        self.assertIn("pull request", text.lower())
        self.assertIn("merge", text.lower())
        self.assertNotIn("agent/workspace-47-autonomous-20260803", text)

    def test_current_workflow_does_not_mandate_superpowers(self) -> None:
        paths = [
            "AGENTS.md",
            "docs/agents/matt-pocock-workflow.md",
            "docs/agents/codex-cloud-gradle.md",
            "docs/superpowers/plans/2026-08-09-portal-coverage-first-autonomous-priority.md",
            "docs/superpowers/plans/2026-08-04-workspace-47-autonomous-audit.md",
            "docs/superpowers/plans/2026-08-09-ugr-certificate-contract.md",
        ]
        forbidden = re.compile(r"(?:REQUIRED\s+SUB-SKILL|Use Superpowers skills as applicable).*superpowers", re.I | re.S)
        for path in paths:
            self.assertIsNone(forbidden.search(self.read(path)), path)

    def test_cloud_policy_covers_all_gradle(self) -> None:
        text = self.read("docs/agents/codex-cloud-gradle.md")
        self.assertIn("All agent-initiated Gradle execution", text)
        self.assertIn("focused tests", text)
        self.assertIn("w47-cloud full", text)
        self.assertIn("never automatic", text)


    def test_active_plans_never_instruct_local_gradle(self) -> None:
        for path in (
            "docs/superpowers/plans/2026-08-09-portal-coverage-first-autonomous-priority.md",
            "docs/superpowers/plans/2026-08-04-workspace-47-autonomous-audit.md",
            "docs/superpowers/plans/2026-08-09-ugr-certificate-contract.md",
        ):
            self.assertNotIn("./gradlew", self.read(path), path)


    def test_cloud_execution_layer_runs_gradle_directly_without_redelegation(self) -> None:
        policy = self.read("docs/agents/codex-cloud-gradle.md")
        launcher = self.read("tools/w47-cloud")
        self.assertIn("already inside", policy.lower())
        self.assertIn("run `./gradlew` directly", policy)
        self.assertIn("must not invoke `w47-cloud`", policy)
        self.assertIn("already running inside the Codex Cloud execution environment", launcher)
        self.assertIn("Do not invoke w47-cloud or codex cloud from inside this task", launcher)


    def test_legacy_publication_runner_is_retired_and_cloud_safe(self) -> None:
        source = self.read("scripts/oss/run-termux-publication-gates.sh")
        self.assertIn("retired", source.lower())
        self.assertIn("w47-cloud full", source)
        self.assertNotIn("./gradlew", source)
        self.assertNotIn("oss/publication-readiness-20260811", source)
        self.assertNotIn("4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb", source)

    def test_launcher_builds_cloud_prompt_without_shell_substitution(self) -> None:
        branch = "agent/test-cloud-policy"
        sha = "a" * 40
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            capture = tmp_path / "args.json"
            fake_codex = tmp_path / "codex-fake"
            fake_codex.write_text(
                f"#!{sys.executable}\n"
                "import json, os, sys\n"
                "with open(os.environ['W47_CAPTURE'], 'w', encoding='utf-8') as f:\n"
                "    json.dump(sys.argv[1:], f)\n",
                encoding="utf-8",
            )
            fake_codex.chmod(0o700)
            env = os.environ.copy()
            env.update({
                "W47_CODEX_BIN": str(fake_codex),
                "W47_CODEX_CLOUD_CACHE_DIR": str(tmp_path / "cache"),
                "W47_CAPTURE": str(capture),
            })
            result = subprocess.run(
                [
                    str(ROOT / "tools/w47-cloud"),
                    "gradle",
                    "--branch", branch,
                    "--sha", sha,
                    "verifyResolvedCoreVersion",
                    "testDebugUnitTest",
                ],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stderr, "")
            args = json.loads(capture.read_text(encoding="utf-8"))
            self.assertEqual(args[:6], [
                "cloud", "exec", "--env", "6a785cdf2c8c8191ba25607f44962899", "--branch", branch,
            ])
            prompt = args[6]
            self.assertIn("workspace-47-android", prompt)
            self.assertIn(f"`{sha}`", prompt)
            self.assertIn(
                "`./gradlew verifyResolvedCoreVersion testDebugUnitTest --no-daemon --console=plain`",
                prompt,
            )
            self.assertIn("Do not invoke w47-cloud or codex cloud from inside this task", prompt)

    def test_launcher_requires_exact_sha_and_has_full_gate(self) -> None:
        text = self.read("tools/w47-cloud")
        self.assertIn("exact 40-hex --sha is required", text)
        self.assertIn("explicit --branch is required", text)
        self.assertNotIn("DEFAULT_BRANCH=", text)
        self.assertIn("git rev-parse HEAD", text)
        self.assertIn("FULL_TASKS=(", text)
        for task in (
            "verifyResolvedCoreVersion",
            "verifyPortableAapt2Configuration",
            "testDebugUnitTest",
            "testQaUnitTest",
            "lintDebug",
            "lintQa",
            "assembleDebug",
            "assembleQa",
            "assembleQaAndroidTest",
        ):
            self.assertIn(task, text)


if __name__ == "__main__":
    unittest.main()
