from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AgentVerificationPolicyTest(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_agents_points_to_matt_and_github_actions(self) -> None:
        text = self.read("AGENTS.md")
        self.assertIn("Matt Pocock", text)
        self.assertIn("docs/agents/matt-pocock-workflow.md", text)
        self.assertIn("docs/agents/github-actions-verification.md", text)
        self.assertIn("GitHub Actions", text)
        self.assertIn("narrowly scoped local tests", text)
        self.assertIn("not the default path", text)
        self.assertNotIn("Every Gradle command", text)

    def test_context_uses_stable_main_pull_request_workflow(self) -> None:
        text = self.read("CONTEXT.md")
        self.assertIn("`origin/main`", text)
        self.assertIn("fresh branch", text)
        self.assertIn("pull request", text.lower())
        self.assertIn("GitHub Actions", text)
        self.assertIn("exact pushed PR-head SHA", text)
        self.assertNotIn("agent/workspace-47-autonomous-20260803", text)

    def test_github_actions_policy_is_canonical_and_exact_sha(self) -> None:
        text = self.read("docs/agents/github-actions-verification.md")
        self.assertIn("canonical broad verification environment", text)
        self.assertIn("narrow local check", text)
        self.assertIn("pull-request head SHA", text)
        self.assertIn("connectedQaAndroidTest", text)
        self.assertIn("Android emulator instrumentation", text)
        self.assertIn("Do not invoke `$HOME/bin/w47-cloud`", text)
        self.assertIn("Do not automatically fall back", text)
        for required in (
            "testDebugUnitTest",
            "testQaUnitTest",
            "lintDebug",
            "lintQa",
            "assembleDebug",
            "assembleQa",
            "assembleQaAndroidTest",
        ):
            self.assertIn(required, text)

    def test_codex_cloud_policy_is_explicitly_historical(self) -> None:
        text = self.read("docs/agents/codex-cloud-gradle.md")
        self.assertIn("historical", text.lower())
        self.assertIn("Deprecated for current development", text)
        self.assertIn("docs/agents/github-actions-verification.md", text)
        self.assertIn("only when the operator explicitly requests it", text)

    def test_active_docs_do_not_mandate_codex_cloud(self) -> None:
        active = (
            "AGENTS.md",
            "CONTEXT.md",
            "README.md",
            "docs/agents/matt-pocock-workflow.md",
            "docs/building-on-termux.md",
            "scripts/oss/run-termux-publication-gates.sh",
        )
        forbidden = re.compile(r"(?:must|required to|every .*command.*)\s+(?:use|run .*in)\s+Codex Cloud", re.I)
        for path in active:
            text = self.read(path)
            self.assertIsNone(forbidden.search(text), path)

    def test_termux_is_focused_only_and_broad_gate_is_actions(self) -> None:
        text = self.read("docs/building-on-termux.md")
        self.assertIn("Optional focused local path", text)
        self.assertIn("canonical broad candidate gate runs in GitHub Actions", text)
        self.assertIn("do not routinely run the full unit/lint/assembly matrix", text)

    def test_retired_publication_runner_points_to_actions_not_cloud(self) -> None:
        source = self.read("scripts/oss/run-termux-publication-gates.sh")
        self.assertIn("retired", source.lower())
        self.assertIn("GitHub Actions", source)
        self.assertIn("docs/agents/github-actions-verification.md", source)
        self.assertNotIn("w47-cloud full", source)
        self.assertNotIn("./gradlew", source)

    def test_current_workflow_does_not_mandate_superpowers(self) -> None:
        paths = [
            "AGENTS.md",
            "docs/agents/matt-pocock-workflow.md",
            "docs/agents/github-actions-verification.md",
            "docs/superpowers/plans/2026-08-09-portal-coverage-first-autonomous-priority.md",
            "docs/superpowers/plans/2026-08-04-workspace-47-autonomous-audit.md",
            "docs/superpowers/plans/2026-08-09-ugr-certificate-contract.md",
        ]
        forbidden = re.compile(r"(?:REQUIRED\s+SUB-SKILL|Use Superpowers skills as applicable).*superpowers", re.I | re.S)
        for path in paths:
            self.assertIsNone(forbidden.search(self.read(path)), path)


if __name__ == "__main__":
    unittest.main()
