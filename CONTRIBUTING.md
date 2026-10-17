# Contributing

Thanks for helping. Bug reports, device results, translations and code changes are all
welcome.

## Ways to help

- **Report a bug** with the issue template. Include the Android version, device model, app
  version and the exact steps. Download and verification problems are usually specific to a
  site response, so screenshots of the page (without cookies or personal data) help.
- **Improve a translation.** English is the source; Simplified Chinese, Japanese, Korean and
  Russian live in `app/src/main/res/values-*/strings.xml`. Add a new locale through
  `res/xml/locales_config.xml` and the in-app language list.
- **Report device coverage.** The app targets Android 10+ and is tested on a physical device;
  reports from other OEMs and Android versions are useful.
- **Send code.** Keep the app dependency-free: no AndroidX, analytics, ads or third-party
  runtime libraries.

## Development setup

Requirements:

- JDK 17
- Android SDK with platform 36 and Build Tools 35.0.0
- Gradle is provided by the wrapper (8.11.1)

Point `local.properties` at the SDK:

```properties
sdk.dir=/path/to/Android/sdk
```

Then:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
python3 scripts/check-translations.py
```

The debug APK is `app/build/outputs/apk/debug/app-universal-debug.apk`. Release builds use the
community key in `signing/` so sideload updates stay installable; see [BUILDING](docs/BUILDING.md).

### Device tests

`app/src/androidTest` contains the device-verification harness used for the 1.3 release. It
exercises the real site, the native Discover page, downloads, the reader and the database on a
physical device, forces the app to Chinese so label assertions stay stable, and never solves a
CAPTCHA. Some assertions still describe the 1.3 wording; update them when you change those
screens. Run it only on a test device with content you may access:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode catalog com.magazinelibrary.app.test/com.magazinelibrary.app.DeviceTestRunner
```

## Code guidelines

- Java 17, four-space indentation, no wildcard imports.
- Keep user-visible text in resources. `T.s(R.string.x)` covers helpers that have no activity;
  activities use `getString`.
- Never log cookies, URLs with tokens or file contents.
- Keep HTTPS-only navigation, domain-scoped cookies and the absence of a JavaScript bridge.
- New settings must survive process death and be documented in the README or docs.

## Translation rules

1. Copy the key structure from `app/src/main/res/values/strings.xml`.
2. Keep `%1$s`/`%2$d` placeholders; reordering is fine, removing or duplicating is not.
3. Leave `translatable="false"` entries out of localized files. They fall back to English.
4. Keep product names and technical terms (Magazine Library, PDF, HTTPS, WebView, GitHub,
   MIT, WristBeam) in Latin script.
5. Run `python3 scripts/check-translations.py`; CI runs the same check.

## Pull requests

- One topic per pull request, with a short description of user-visible behaviour.
- Update `CHANGELOG.md` for user-visible changes.
- Make sure `./gradlew :app:testDebugUnitTest :app:lintDebug` and the translation check pass.
- Release tags and signing are maintainer-only; see [RELEASING](docs/RELEASING.md).

By contributing you agree that your work is released under the [MIT licence](LICENSE).
