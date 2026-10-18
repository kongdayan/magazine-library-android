# Changelog

All notable changes to Magazine Library are documented here. The app follows semantic
versioning: `MAJOR.MINOR.PATCH`, with the Android `versionCode` derived as
`major * 10000 + minor * 100 + patch` (so 1.4.0 is 10400).

## 1.4.2 — 2026-10-18

### Changed

- Author information now uses only the GitHub account **@kongdayan**. The About screen, the
  READMEs and the security policy no longer contain a personal name or email address.
- The About screenshot was recaptured from the account-only build.

## 1.4.1 — 2026-10-18

### Fixed

- **Update checker.** 1.4.0 parsed the GitHub release response with the wrong JSON API, so
  *Check for updates* always failed. It now reads the release object directly. Version
  comparison moved into a small helper with unit tests for patch, minor and pre-release tags.

### Changed

- CI uses current action versions and Gradle 8.14.6.

## 1.4.0 — 2026-10-18

The first public, open-source release.

### Added

- **Five app languages** — English (default), Simplified Chinese, Japanese, Korean and
  Russian. Android 13+ uses the system per-app language picker; older versions get the same
  choice inside Settings → Language. All UI strings, including download errors and
  accessibility labels, are translated.
- **About screen** with the author (@kongdayan), source, releases, issue tracker,
  MIT licence, privacy and disclaimer notes, and a manual update check against GitHub
  Releases.
- **Adaptive launcher icon** with a monochrome layer for Android 13 themed icons, plus a
  dedicated notification icon.
- **GitHub Actions**: CI runs unit tests, lint, translation validation and release builds;
  tag releases publish universal and per-ABI APKs with a SHA-256 manifest.
- **Reproducible sideload signing** with a documented community key, so every release
  upgrades the previous install.
- English documentation with localized READMEs, architecture, build, release and privacy
  notes, issue templates and Dependabot.

### Changed

- User-visible strings now live in `res/values*/strings.xml`; Java no longer embeds Chinese
  UI text. The PDF reader, download service and file helpers report localized errors.
- Release builds ship three ABI-named APKs plus a universal APK. The app contains no native
  libraries, so every variant runs on every supported device; the universal APK is the
  recommended download.
- `versionCode` is derived from the version name; CI injects the name from the release tag.
- The download list uses a wider status column for longer translated labels.

### Fixed

- The reader no longer allocates a `RectF` on every frame.
- The browser refresh button now has an accessibility label.

## 1.3.0 — 2026-10-13

- Native Discover page built with Java views: search, categories, publication month, cover
  cards, details, same-series lookup, per-page sorting and fixed pagination. The WebView is
  only the data channel for the page the user requested.
- Browser fallback for verification pages, with an explicit "back to native" action.
- Clear download history without deleting PDFs or unfinished tasks.

## 1.2.1 — 2026-10-13

- Clearing records is idempotent across restarts and keeps unfinished downloads.
- Device regression coverage for history, deduplication and library integrity.

## 1.2.0 — 2026-10-12

- Library cover grid and list layouts, grouping by magazine, rename and grouping edits.
- Task details, pause and resume, conditional range requests and retry.
- Reader modes: single page and continuous scroll, pinch zoom and page jump.

## 1.1.0 — 2026-10-12

- Native download pipeline, MediaStore/SAF publishing, PDF validation and covers.
- Session-aware downloads: per-domain cookies and site preparation requests.

## 1.0.0 — 2026-10-12

- Initial Android client: WebView browsing, background PDF downloads, offline reader.

Historical verification notes live in [`docs/verification/`](docs/verification/).
