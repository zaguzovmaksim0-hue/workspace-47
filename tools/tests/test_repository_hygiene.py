from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class RepositoryHygieneTest(unittest.TestCase):
    def test_retired_workflows_are_not_executable(self):
        self.assertFalse((ROOT / ".github/workflows/real-e2e.yml").exists())
        self.assertFalse((ROOT / ".github/workflows/kai-macos.yml").exists())
        self.assertTrue((ROOT / "docs/archive/workflows/real-e2e.yml.disabled").is_file())

    def test_root_does_not_vendor_the_unrelated_profiler(self):
        self.assertFalse(list(ROOT.glob("kai_csv_profiler-*.whl")))

    def test_instrumentation_is_explicit_manual_opt_in(self):
        source = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn("run_instrumentation:", source)
        self.assertIn("default: false", source)
        job = source.split("  android-instrumented:", 1)[1].split("    runs-on:", 1)[0]
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.run_instrumentation", job)
        self.assertNotIn("pull_request.number", job)

    def test_documentation_index_links_exist(self):
        import re
        index = ROOT / "docs/README.md"
        for link in re.findall(r"\]\(([^)]+)\)", index.read_text()):
            self.assertTrue((index.parent / link).exists(), link)
