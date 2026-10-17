# Architecture

Magazine Library is a small, dependency-free Android client. It renders the magazine site in the
system WebView, extracts the page the user asked for into native views, and downloads PDFs with
its own session-aware downloader.

```text
MainActivity
├── NativeCatalog ── catalog.js ── WebView (current response only)
│   └── CatalogImages ── same WebView session for covers
├── Library / Downloads / Settings (native views, SQLite via Store)
├── DownloadService (foreground worker)
│   └── Files (transfers, PDF validation, covers, publishing)
└── ReaderActivity (PdfRenderer)
```

## Activities and navigation

`MainActivity` owns four tabs: Discover, Library, Downloads and Settings. Discover is a
`LinearLayout` stack; the WebView is kept alive behind it because the site session and the
current response are the data source. Reader and About are separate activities.

`NativeCatalog` builds the Discover UI and asks the WebView for the page the user selected:
search, category, month, detail, previous/next page or page jump. It never crawls or
pre-fetches the site. It caches the last eight responses in memory and renders cards, covers
and PDF buttons from the current DOM.

`catalog.js` is injected with `evaluateJavascript` after a page finishes loading. It is a
read-only extractor: it returns the heading, categories, cover URL, date, description, PDF
anchors, navigation options and pagination state as JSON. No JavaScript interface is exposed
to the page.

## Downloads

`DownloadService` is a foreground service with a single worker. Tasks are persisted in
`library.db` as `queued`, `running`, `paused`, `waiting`, `auth`, `ready` or `failed`.

- Each request reads cookies for that exact URL from the WebView's `CookieManager` and stores
  `Set-Cookie` responses back. Cookies never cross hosts and are not written to SQLite.
- Redirects are followed manually (max 12 hops). VK preview pages are reported as `waiting`,
  not as a verification failure; Cloudflare challenge markers become `auth`.
- If the server provides `ETag`/`Last-Modified` and accepts `Range`, interrupted transfers
  resume. Otherwise the file restarts.
- A completed transfer is validated (`%PDF-` header plus `PdfRenderer` page count), a JPEG
  cover is rendered, and the file is published to `Downloads/Magazine Library` or the
  user-selected folder. Validation failures never publish a file.
- Notifications show progress; the app can be closed while downloads continue.

## Storage

`Store` (SQLite, schema version 4) keeps one row per download/library item: title, series,
status, byte counts, output URI, folder grant, ETag, user agent, last verification URL,
original and resolved links, reading page, page count, and whether the download record is
hidden. Cookies and page content are never stored.

`Files` uses private app storage for `.part` files, MediaStore Downloads or a SAF document for
output, and the cache directory for covers. Backup and device transfer are disabled.

## Reader

`ReaderActivity` uses `PdfRenderer` on one background thread. Page aspect ratios are measured
once so continuous mode can lay out offsets before pages render.

- Single-page mode renders at up to 8 megapixels, supports pinch zoom, double-tap reset and
  horizontal swipe.
- Continuous mode renders visible and adjacent pages only, keeping at most 8 bitmaps or 24 MB.
  It supports fling, pinch zoom and page tracking while scrolling.
- Reading progress is written to SQLite on every page change and restored on reopen.

## Localization

English is the default resource set. `values-zh-rCN`, `values-ja`, `values-ko` and
`values-ru` mirror it. On Android 13+ the app uses the platform per-app locale API and
`res/xml/locales_config.xml`; before that, `LocaleHelper` wraps the base context with the saved
locale. `T.s()` reads application-level strings for services and file helpers, so a language
change updates notifications and errors as well as activities.

## Security boundaries

- HTTPS-only navigation and downloads; certificate errors cancel the connection.
- No JavaScript bridge, no file/content access in the WebView, no mixed content.
- The user completes any site verification manually in the WebView; the app never solves a
  CAPTCHA.
- External links from the About screen open in the user's browser; the app does not load
  untrusted local files.
- The community signing key is public by design and only guarantees sideload upgrade
  continuity.

## Known limits

- The extractor depends on the site's current markup; when it changes, the Website button
  remains as a fallback.
- `blob:` downloads, password-protected PDFs, batch crawling, OCR and full-text search are not
  supported.
- Series grouping uses title prefixes plus month/issue heuristics and can be corrected by hand.
- Cloudflare verification can still appear; it is a manual step by design.
