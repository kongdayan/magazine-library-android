# Verification

This page records what has actually been checked and what remains unverified. The project
publishes APKs for sideloading; it does not claim store-level certification.

## 1.4.1

- Fixes the update checker JSON parsing bug found in 1.4.0; `/releases/latest` was confirmed to
  return `v1.4.0` and `VersionsTest` covers the comparison.
- `:app:testDebugUnitTest` (14 tests), `:app:lintDebug` (0 issues), the translation check and
  the signed release build pass in GitHub Actions.

## 1.4.0

Checked locally with JDK 17, Android SDK 36 and Build Tools 35.0.0:

- `:app:testDebugUnitTest` — URL routing, pagination, filename safety, series grouping,
  challenge detection and real download URL parsing.
- `:app:lintDebug` — zero errors and zero warnings with the project lint configuration.
- `:app:assembleRelease` — four signed APKs (universal, `arm64-v8a`, `armeabi-v7a`,
  `x86_64`); `apksigner verify` confirms the community certificate.
- `python3 scripts/check-translations.py` — 299 English strings and 294 translated strings per
  locale, with matching keys and placeholders for English, Simplified Chinese, Japanese,
  Korean and Russian.
- `aapt2 dump badging` — package `com.magazinelibrary.app`, version 1.4.0 (10400), minSdk 29,
  targetSdk 35, launcher and locale metadata present.

Additionally verified on an **Android 15 (API 35) arm64 emulator** with the signed universal
APK:

- Cold start shows the adaptive launcher icon, then Discover loads a real site response:
  searching "Home Networking" returned 12 cards with covers and issue dates.
- Opening a card shows the detail page with cover, categories, publication date, the PDF entry
  and related magazines.
- Settings renders every group; About shows the author card, version 1.4.0 (10400), commit and
  build time, GitHub, email, source, releases, issues, MIT and WristBeam links.
- The in-app language picker switched English → Русский → English at runtime; the whole UI,
  including the bottom navigation, followed immediately. The Russian tab labels were checked
  for overflow and the navigation was made single-line as a result.

Not verified in this release:

- **A full download and reader pass on 1.4.** The emulator session ended before a 68-page PDF
  could be downloaded; the same downloader, storage and reader code paths were verified on
  hardware in 1.3.0 and are unchanged apart from localized error strings.
- **Automatic CAPTCHA handling.** This is intentional; verification is completed manually in
  the WebView.
- **Every OEM and Android version.** The reference device is an HONOR BVL-AN20 on Android 15;
  the emulator is a Google APIs image.

## 1.3.0 and earlier

The 1.3.0 release was verified on a physical HONOR BVL-AN20, including native search, combined
category/month filters, pagination, detail pages, browser fallback, a real 68-page PDF download
over the VK redirect, PDF validation, library integrity and reading progress. Earlier releases
covered downloads, history clearing, grid/list layouts and the continuous reader.

The historical logs and screenshots are kept in [`docs/verification/`](verification/) (written
in Chinese) and the raw JSON reports were part of the local verification workspace.

| Release | Focus | Log |
| --- | --- | --- |
| 1.3.0 | Native Discover page, live PDF download | [1.3.md](verification/1.3.md) |
| 1.2.1 | Clearing download history safely | [1.2.1.md](verification/1.2.1.md) |
| 1.2.0 | Library layouts, downloads and reader | [1.2.md](verification/1.2.md) |
| 1.1.0 | Native download pipeline | [1.1.md](verification/1.1.md) |
| 1.0.0 | Initial client | [1.0.md](verification/1.0.md) |

## Reproduce a release check

```sh
python3 scripts/check-translations.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-universal-release.apk
build-tools/35.0.0/aapt2 dump badging \
  app/build/outputs/apk/release/app-universal-release.apk
```

## Scope and risks

- The extractor follows the site markup; a redesign can break Discover until `catalog.js` is
  updated. The Website button remains available.
- Verification sessions may expire or change with IP and site rules.
- PDFs that are encrypted or damaged are reported as errors, never published.
- Series grouping is heuristic and can be corrected in the library.
- Download links are short-lived; retry from the magazine page when a link expires.
