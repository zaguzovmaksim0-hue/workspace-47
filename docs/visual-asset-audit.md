# Visual asset publication audit

**Status:** provenance remediation applied; Android resource/build verification pending
**Original source commit reviewed:** `86d644c76036eecc9cfda8617e11f31770f379d4` (`feat: apply Junta Firma visual identity`)
**Remediation commit:** `19fe276d3f62a2d6e6e427e3637877318ee18003` (`fix(oss): replace unresolved visual binaries with project vectors`)

This audit separates Android resource wiring from copyright/provenance. A binary asset being present in Git does not establish the right to redistribute or relicense it.

## Known licensed asset

- `app/src/main/res/font/bebas_neue_regular.ttf` — SIL Open Font License 1.1; license text is retained in `docs/licenses/BebasNeue-OFL.txt` and attribution is recorded in `NOTICE`.

## Original unresolved binary set

The earlier visual-identity commit introduced 21 binary image paths without source/license metadata that this audit could establish:

1. `app/src/main/res/drawable-nodpi/jfm_home_background.webp`
2. `app/src/main/res/drawable-mdpi/ic_launcher_background.png`
3. `app/src/main/res/drawable-mdpi/ic_launcher_foreground.png`
4. `app/src/main/res/drawable-hdpi/ic_launcher_background.png`
5. `app/src/main/res/drawable-hdpi/ic_launcher_foreground.png`
6. `app/src/main/res/drawable-xhdpi/ic_launcher_background.png`
7. `app/src/main/res/drawable-xhdpi/ic_launcher_foreground.png`
8. `app/src/main/res/drawable-xxhdpi/ic_launcher_background.png`
9. `app/src/main/res/drawable-xxhdpi/ic_launcher_foreground.png`
10. `app/src/main/res/drawable-xxxhdpi/ic_launcher_background.png`
11. `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`
12. `app/src/main/res/mipmap-mdpi/ic_launcher.png`
13. `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
14. `app/src/main/res/mipmap-hdpi/ic_launcher.png`
15. `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
16. `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
17. `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
18. `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
19. `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
20. `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
21. `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`

## Remediation applied

The publication branch removes all 21 unresolved binary paths and replaces the required resource names with repository-native text/XML resources created specifically for the publication remediation:

- `app/src/main/res/drawable/jfm_home_background.xml` — neutral geometric vector background;
- `app/src/main/res/drawable/ic_launcher_background.xml` — simple project color field;
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — generic document/signature geometry;
- `app/src/main/res/mipmap-anydpi/ic_launcher.xml` — fallback layer-list;
- `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` — fallback layer-list.

The Android 8+ adaptive icon resources under `mipmap-anydpi-v26/` continue to reference `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`, so the replacement preserves the existing resource contract. The app UI continues to resolve `R.drawable.jfm_home_background` without changing Kotlin wiring.

The replacement artwork was constructed as simple project-specific XML geometry during this remediation; no third-party image, government seal, official emblem or externally sourced graphic was used as its visual source.

## Policy TDD evidence

`tools/test_publication_visual_assets.py` defines the publication contract: none of the 21 unresolved binary paths may exist in the publication candidate.

The RED state was observed before replacement with the test failing on the unresolved path set. After commit `19fe276d3f62a2d6e6e427e3637877318ee18003`, the recursive Git tree contains none of the prohibited `.webp`/`.png` paths and contains the replacement XML resources. A fresh path-level execution of the same policy test against the post-remediation resource state exits successfully with one test passing.

The replacement XML documents were also parsed as well-formed XML during the verification pass.

## Remaining verification boundary

The provenance blocker for the removed binary artwork is resolved on this publication branch. This does **not** constitute an Android build/resource-link PASS.

GitHub Actions still fails before job creation, so the following remain mandatory before public release approval:

1. run the relevant Android resource/Gradle build on a working execution channel;
2. confirm `aapt2`/resource linking accepts the vector and launcher XML resources;
3. repeat the policy test and build checks after synchronizing with the final autonomous-development head;
4. visually inspect the final launcher/home presentation for usability and unofficial-project clarity.

If a future synchronization reintroduces any of the 21 original binary paths, this publication gate reopens automatically.
