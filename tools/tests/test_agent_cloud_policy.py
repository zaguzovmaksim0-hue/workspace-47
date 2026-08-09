from pathlib import Path
import re
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
