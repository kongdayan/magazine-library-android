# Security policy

Magazine Library stores browsing sessions and PDFs locally. It has no accounts, no telemetry
and no server of its own, so reports usually concern the WebView session, the downloader, file
publishing or the reader.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub's private vulnerability
reporting on
[Security advisories](https://github.com/kongdayan/magazine-library-android/security/advisories/new)
or email <19552144+kongdayan@users.noreply.github.com>. Include the app version, Android version, a description and
steps to reproduce. You will get an acknowledgement as soon as possible.

## Supported versions

The latest release is supported. Older releases may receive a fix when the problem is
specific to them.

## Design notes

- The system WebView is used with its default user agent and normal JavaScript, DOM storage and
  cookies. There is no `addJavascriptInterface` bridge and no local file or content access.
- Navigation is HTTPS-only. Certificate errors cancel the connection instead of being
  ignored, and mixed content is blocked.
- Cookies are read per request URL and sent only to that host. They are never written to the
  database, exported or logged.
- Downloads are validated as PDFs (`%PDF-` plus a readable page) before being published to
  MediaStore Downloads or a user-selected folder. The app requests no broad storage permission.
- Backup and device transfer exclude all application data.
- The release signing key in `signing/community.jks` is intentionally public and only provides
  upgrade continuity for sideload builds. It must not be used for Google Play or any
  distribution where signing identity is a trust boundary.
