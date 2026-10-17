# Privacy

Magazine Library is a local client. It has no analytics, no advertising, no accounts and no
backend operated by the project.

## What stays on the device

- Website cookies and the WebView profile, needed to keep the magazine site session.
- The SQLite database of download tasks, library metadata, folder grants and reading progress.
- Downloaded PDFs (in Downloads/Magazine Library by default, or a folder you choose) and
  cover thumbnails in the app cache.
- App settings, including the selected language.

Android backup and device transfer are disabled for this app. Uninstalling removes the
internal database, cache and pending transfers; published PDFs are ordinary files and remain
until you delete them.

## Network connections

- **magazinelib.com** and its file hosts (including VK preview pages) when you browse or
  download, using the system WebView session.
- **api.github.com** only when you tap *Check for updates* on the About screen, to read the
  latest release tag. The request includes the app version as a user agent and no identifiers.
- Opening a link from the About screen hands it to the browser or mail app you choose.

There is no project server, no crash reporting SDK and no remote configuration.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE` for browsing and downloads.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` to keep downloads running while the
  app is in the background.
- `POST_NOTIFICATIONS` (Android 13+) for download progress, requested only when the first
  download starts.

Folder access is granted per-folder through the system document picker and can be revoked at
any time in Android settings.
