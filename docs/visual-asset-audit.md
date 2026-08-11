# Visual asset publication audit

**Status:** unresolved publication blocker
**Source commit reviewed:** `86d644c76036eecc9cfda8617e11f31770f379d4` (`feat: apply Junta Firma visual identity`)

This audit separates Android resource wiring from copyright/provenance. A binary asset being present in Git does not establish the right to redistribute or relicense it.

## Known licensed asset

- `app/src/main/res/font/bebas_neue_regular.ttf` — SIL Open Font License 1.1; license text is retained in `docs/licenses/BebasNeue-OFL.txt` and attribution is recorded in `NOTICE`.

## Unresolved custom binary assets

The visual-identity commit introduced the following binary image set without source/license metadata that this audit could establish.

### Home background

1. `app/src/main/res/drawable-nodpi/jfm_home_background.webp`

### Density-specific launcher foreground/background PNGs

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

### Density-specific launcher PNGs

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

## Current Android wiring

`AndroidManifest.xml` points to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. Android 8+ adaptive launcher resources also exist under `mipmap-anydpi-v26/` and reference foreground/background resources. The app UI separately uses `jfm_home_background.webp` as a branded background.

The visible application disclosure `Cliente no oficial para uso personal` is useful for affiliation clarity but does not resolve copyright ownership of any image.

## Preferred remediation

The safest publication path is to **replace, not guess**:

1. create project-authored vector/XML launcher foreground/background resources with simple original geometry and no official seal/logo;
2. use adaptive-icon XML for modern Android and deterministic project-owned fallbacks for older Android versions;
3. replace the WebP home background with a project-authored vector/Compose gradient/geometric background, or an image whose source and compatible license are documented;
4. remove all 21 unresolved binary files from the publication candidate;
5. update `docs/provenance.md` and `NOTICE` so the replacement resources are explicitly project-authored;
6. retain the existing independent/unofficial disclosure and no-affiliation language.

## TDD gate before replacement

Production resources are not changed in this audit branch until an executable test channel is available.

The first change must be a failing resource-contract test that requires the publication candidate to avoid the unresolved binary paths (or requires their project-owned replacements). The test must be observed failing for the expected reason. Only then should resource files/wiring be changed; the same test plus the relevant Android resource/build checks must subsequently be observed passing.

GitHub Actions currently fails before job creation even for a one-line `echo` workflow, so the required RED/GREEN evidence cannot honestly be claimed yet. The replacement plan is therefore ready but intentionally not applied.

## Alternative remediation

If the maintainer can produce reliable source records showing that each unresolved image was created for this project and that the maintainer owns or is authorized to license it under the selected project license, retain the images and record that attestation/source evidence here and in `docs/provenance.md`.

Absent that evidence, publication remains blocked on these assets.
