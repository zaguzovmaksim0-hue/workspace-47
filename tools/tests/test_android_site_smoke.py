from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "scripts" / "android-site-smoke.sh"


class AndroidSiteSmokeScriptTest(unittest.TestCase):
    def test_aeat_run_activates_reviewed_public_entry_once(self) -> None:
        source = RUNNER.read_text(encoding="utf-8")
        self.assertIn('activate_file="$RAW_DIR/$run_id-activate.txt"', source)
        self.assertIn('[[ "$target_id" == "aeat-sede" && "$activation_attempted" != true ]]', source)
        self.assertIn(
            'run_command "$target_kind" "$target_id" "$run_id" ACTIVATE "$activate_file"',
            source,
        )
        self.assertIn('"PUBLIC_ENTRY_ACTIVATION_REQUESTED"', source)
        self.assertIn('publicEntryActivationRequested: $activationRequested', source)


if __name__ == "__main__":
    unittest.main()
