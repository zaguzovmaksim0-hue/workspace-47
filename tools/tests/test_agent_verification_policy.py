"""Check active guidance links and the retired runner, without prose snapshots."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class AgentVerificationPolicyTest(unittest.TestCase):
    def test_active_guidance_links_exist(self):
        for relative in (
            "AGENTS.md",
            "CONTEXT.md",
            "README.md",
            "README.en.md",
            "docs/agents/matt-pocock-workflow.md",
            "docs/agents/github-actions-verification.md",
            "docs/agents/codex-cloud-gradle.md",
            "docs/building-on-termux.md",
        ):
            path = ROOT / relative
            text = path.read_text(encoding="utf-8")
            links = [(path.parent, link) for link in re.findall(r"\]\(([^)]+)\)", text)]
            links += [(ROOT, link) for link in re.findall(r"`([^`\s]+\.md)`", text)]
            for base, link in links:
                if "://" in link or link.startswith("#"):
                    continue
                target = link.split("#", 1)[0]
                self.assertTrue((base / target).exists(), (relative, link))

    def test_retired_publication_runner_points_to_actions_not_cloud(self):
        source = (ROOT / "scripts/oss/run-termux-publication-gates.sh").read_text()
        self.assertIn("retired", source.lower())
        self.assertIn("GitHub Actions", source)
        self.assertIn("docs/agents/github-actions-verification.md", source)
        self.assertNotIn("w47-cloud full", source)
        self.assertNotIn("./gradlew", source)


if __name__ == "__main__":
    unittest.main()
