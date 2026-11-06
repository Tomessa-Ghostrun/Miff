# Haze (frosted glass of the tab bar)

| | |
|---|---|
| Library | Haze, `dev.chrisbanes.haze:haze` and `dev.chrisbanes.haze:haze-blur` (and `haze-utils`, pulled in by both) |
| Version | **2.0.1** (pinned in `gradle/libs.versions.toml`; published on Maven Central on 2026-09-29) |
| Source | https://github.com/chrisbanes/haze |
| Author | Christopher Banes and the Haze project contributors |
| Licence | **Apache License 2.0** (`Apache-2.0-Haze.txt`, copy of the `LICENSE` file of the repository; the POM of each artifact also declares it) |
| NOTICE file | none in the repository (nothing to reproduce beyond the licence) |
| Used for | the blur of what is behind the tab bar (ticket TABBAR-GLASS). Modules `haze` (`hazeSource`) and `haze-blur` only: NOT `haze-glass` (refraction), NOT `haze-materials` |
| Network, permission, key | none. The library draws; it reads nothing from the phone and sends nothing |

The licence and the copyright notice above are to be shown in the "licences" screen of the app when it exists (there is none today;
the same is already true of MapLibre, BSD-2-Clause, see `android/README.md`): task for the `release-manager` with the other notices.

## Checksums of the artifacts resolved by Gradle (SHA-256, computed on 2026-11-06 from Maven Central over HTTPS)

| Artifact | Size | SHA-256 |
|---|---|---|
| `haze-android-2.0.1.aar` | 246 520 bytes | `662f8cbefc9925a1239e0e93179316d0a092425a1f24bffbe84ed42968bbb922` |
| `haze-blur-android-2.0.1.aar` | 160 036 bytes | `1838c0df08dfc5bdb671a704bf22487f91cc4e35f2b744652a980b15d5ff0982` |
| `haze-utils-android-2.0.1.aar` | 39 308 bytes | `86434e96616bbb9624d6eccdd98b4ad2d343bcf4adc641cc7e0faeb2f2d416a8` |

The project has no Gradle dependency-verification file today (`gradle/verification-metadata.xml`): turning it on covers EVERY dependency,
not only this one, so it is a separate decision. In the meantime the dependencies come from `google()` and `mavenCentral()` over HTTPS only,
and these checksums let anyone check that the version in use is the one that was reviewed.

## Signal to leave the library (plan B)

Stop of maintenance, a change of licence, an incompatibility with a new Compose BOM, or dropped frames on the OPPO Find X5 Lite that the
switch `GlassPolicy.BLUR_ALLOWED` does not solve: replace it by the home-made blur from Android 12 described in
`design/tab-bar-liquid-glass.md`, section 4.3, option B. The switch `GlassPolicy.BLUR_ALLOWED = false` already ships the bar without any blur.
