<div align="center">
<img src="docs/images/icon.svg" width="88" alt="Magazine Library" />

# Magazine Library

Magazines arrive as PDFs. Your phone should let you read them offline.

[![CI](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kongdayan/magazine-library-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kongdayan/magazine-library-android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![MIT](https://img.shields.io/badge/license-MIT-14675d)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-1d2628?logo=android)](https://github.com/kongdayan/magazine-library-android/releases/latest)
[![Languages](https://img.shields.io/badge/UI-5%20languages-eab68a)](docs/ARCHITECTURE.md#localization)

[English](README.md) · [简体中文](README.cn.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Русский](README.ru.md)

</div>

**A small, local Android client for browsing a magazine site, downloading PDFs and reading them
offline.**

I wanted to stop juggling browser tabs, a download folder and a separate PDF viewer just to read
one issue on a train. Magazine Library keeps the site session in a WebView, turns the page you
asked for into native controls, downloads in the background and opens the result in a reader
with saved progress. No account, no ads, no analytics, no cloud.

- **Discover without the web clutter.** Search, categories, publication month, covers, details,
  same-series lookup, per-page sorting and pagination, all rendered with native views. Only the
  page you open is requested.
- **Downloads that survive the site.** The downloader keeps the browser session per host,
  follows the site's file-preparation step, resumes when the server allows it and validates the
  PDF before saving it to Downloads or a folder you choose.
- **A library, not a folder.** Covers, grid or list, grouping by magazine, rename, sort and
  reading progress. Files stay where you put them.
- **Read properly.** Single-page swiping or continuous scrolling, pinch zoom, page jump and a
  reading position that survives restarts.
- **Five languages, one small APK.** English, Simplified Chinese, Japanese, Korean and Russian,
  switchable in Settings or through Android's per-app language picker. No AndroidX, no runtime
  libraries, no ads.

<div align="center">
  <img src="docs/images/discover.jpg" width="250" alt="Discover with search results" />
  <img src="docs/images/detail.jpg" width="250" alt="Magazine details and PDF links" />
  <img src="docs/images/settings.jpg" width="250" alt="Settings" />
  <br />
  <img src="docs/images/language.jpg" width="250" alt="Language picker" />
</div>

Captured on an Android 15 emulator during the 1.4 verification run. The UI is available in
English, Simplified Chinese, Japanese, Korean and Russian. See
[verification scope](docs/VERIFICATION.md).

## Install

Android 10 (API 29) or newer.

| Device | Download |
| --- | --- |
| Any supported device (**recommended**) | [MagazineLibrary-universal.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-universal.apk) |
| arm64-v8a | [MagazineLibrary-arm64-v8a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-arm64-v8a.apk) |
| armeabi-v7a | [MagazineLibrary-armeabi-v7a.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-armeabi-v7a.apk) |
| x86_64 (emulators) | [MagazineLibrary-x86_64.apk](https://github.com/kongdayan/magazine-library-android/releases/latest/download/MagazineLibrary-x86_64.apk) |

The app contains no native libraries, so every APK runs on every supported device; the
universal build is the simplest choice. Releases are signed with the public
[community key](signing/keystore.properties), so each one upgrades the previous sideload
install. Check a download against `SHA256SUMS` on the release page. Not distributed through
Google Play.

## Use it

1. Open **Discover**, search or pick a category and month, then open a magazine.
2. Tap a PDF entry. The site may prepare the file for a moment; the download continues in the
   background and appears in **Downloads**.
3. The finished file lands in `Downloads/Magazine Library` or the folder you select, and the
   issue appears in **Library**.
4. Tap a cover to read. Swipe horizontally in single-page mode, scroll in continuous mode, tap
   the page number to jump. The last page is remembered.

If a site verification page appears, finish it inside the app's **Website** view; it is a
manual step by design. When the site changes its markup, the Website view remains a working
fallback while the native extractor is updated.

## How it works

The system WebView is the browser session. After a page the user requested finishes loading, a
read-only script extracts the current magazine response into JSON, and native views render it.
Nothing crawls or pre-fetches the site. Covers use the same session. The downloader reads
cookies for the exact URL, handles redirects itself, resumes with `Range` when possible, and
only publishes a file after `%PDF-` and `PdfRenderer` validation.

More detail: [architecture](docs/ARCHITECTURE.md) · [privacy](PRIVACY.md) ·
[security policy](SECURITY.md).

## Build

Requirements: JDK 17, Android SDK platform 36, Build Tools 35.0.0. Gradle 8.11.1 comes from the
wrapper.

```sh
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-translations.py
```

Release builds are signed with the community key so sideload installs keep upgrading. Set the
`SIGNING_*` environment variables to use your own key. [Building notes](docs/BUILDING.md) ·
[release process](docs/RELEASING.md).

## Languages

| Language | App UI | README |
| --- | --- | --- |
| English (default) | `values/` | this file |
| 简体中文 | `values-zh-rCN/` | [README.cn.md](README.cn.md) |
| 日本語 | `values-ja/` | [README.ja.md](README.ja.md) |
| 한국어 | `values-ko/` | [README.ko.md](README.ko.md) |
| Русский | `values-ru/` | [README.ru.md](README.ru.md) |

Translations are checked in CI: keys, placeholders and untranslatable strings must match the
English source. New languages are welcome — see [contributing](CONTRIBUTING.md#translation-rules).

## Project

- [Changelog](CHANGELOG.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Building](docs/BUILDING.md) · [Releasing](docs/RELEASING.md)
- [Verification scope](docs/VERIFICATION.md) · [historical device logs](docs/verification/)
- [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Contributing](CONTRIBUTING.md)

## Author

**kongdayan** — independent developer.

I build small tools that respect privacy and work offline. Magazine Library is one of them;
[WristBeam](https://github.com/kongdayan/wristbeam) (腕映) is a native macOS companion for Garmin
watches.

[GitHub](https://github.com/kongdayan) ·
[Issues](https://github.com/kongdayan/magazine-library-android/issues)

## Disclaimer

Magazine Library is an independent, unofficial client for `magazinelib.com` and is not
affiliated with that site. It is a browser and download manager; it does not host or index
content. Downloads are subject to the source site's terms and to copyright law. Use it only for
material you may legally access. The author is not responsible for how third-party content is
used.

## License

[MIT](LICENSE) © 2026 kongdayan and contributors.
