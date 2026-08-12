from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]

UNRESOLVED_BINARY_ASSETS = (
    "app/src/main/res/drawable-nodpi/jfm_home_background.webp",
    "app/src/main/res/drawable-mdpi/ic_launcher_background.png",
    "app/src/main/res/drawable-mdpi/ic_launcher_foreground.png",
    "app/src/main/res/drawable-hdpi/ic_launcher_background.png",
    "app/src/main/res/drawable-hdpi/ic_launcher_foreground.png",
    "app/src/main/res/drawable-xhdpi/ic_launcher_background.png",
    "app/src/main/res/drawable-xhdpi/ic_launcher_foreground.png",
    "app/src/main/res/drawable-xxhdpi/ic_launcher_background.png",
    "app/src/main/res/drawable-xxhdpi/ic_launcher_foreground.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_background.png",
    "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png",
    "app/src/main/res/mipmap-mdpi/ic_launcher.png",
    "app/src/main/res/mipmap-mdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-hdpi/ic_launcher.png",
    "app/src/main/res/mipmap-hdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xhdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
)


class PublicationVisualAssetPolicyTest(unittest.TestCase):
    def test_unresolved_visual_binaries_are_not_tracked(self) -> None:
        remaining = [
            relative_path
            for relative_path in UNRESOLVED_BINARY_ASSETS
            if (REPOSITORY_ROOT / relative_path).exists()
        ]
        self.assertEqual(
            [],
            remaining,
            "Unresolved visual binaries remain in the publication candidate:\n"
            + "\n".join(remaining),
        )


if __name__ == "__main__":
    unittest.main()
