from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / "docs" / "compatibility" / "all-spanish-public-portals-inventory.md"
STATUSES = (
    "VERIFIED_E2E",
    "IMPLEMENTED_NOT_E2E",
    "VERIFIED_CONTRACT",
    "REQUIRES_AUTHENTICATED_RESEARCH",
    "BROWSE_ONLY",
    "UNSUPPORTED_PROTOCOL",
    "INACCESSIBLE",
    "DEPRECATED",
)


class InventorySummaryConsistencyTest(unittest.TestCase):
    def test_status_summary_matches_record_bodies(self) -> None:
        text = INVENTORY.read_text(encoding="utf-8")
        body = Counter(re.findall(r'^    inventory_status: "([^"]+)"', text, re.MULTILINE))
        summary = {}
        for status in STATUSES:
            match = re.search(rf'^\| `{re.escape(status)}` \| (\d+) \|$', text, re.MULTILINE)
            self.assertIsNotNone(match, status)
            assert match is not None
            summary[status] = int(match.group(1))
        self.assertEqual({status: body[status] for status in STATUSES}, summary)
        total_match = re.search(r'^\| \*\*Total\*\* \| \*\*(\d+)\*\* \|$', text, re.MULTILINE)
        self.assertIsNotNone(total_match)
        assert total_match is not None
        self.assertEqual(sum(body.values()), int(total_match.group(1)))


if __name__ == "__main__":
    unittest.main()
