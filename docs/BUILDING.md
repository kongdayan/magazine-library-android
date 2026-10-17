# Building

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android SDK platform | 36 |
| Android Build Tools | 35.0.0 |
| Gradle | 8.11.1 (wrapper included) |
| Minimum device | Android 10 (API 29) |

Create `local.properties` in the repository root:

```properties
sdk.dir=/path/to/Android/sdk
```

## Common commands

```sh
./gradlew :app:assembleDebug          # debug APKs (universal + per ABI)
./gradlew :app:assembleRelease        # signed release APKs
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # Android lint
python3 scripts/check-translations.py # locale completeness
```

Outputs land in `app/build/outputs/apk/<variant>/`:

```text
app-universal-release.apk
app-arm64-v8a-release.apk
app-armeabi-v7a-release.apk
app-x86_64-release.apk
```

The app contains no native libraries, so every APK installs on every supported device. Use the
universal APK unless a tool insists on an ABI name.

## Versioning

`versionName` defaults to `1.4.0`; `versionCode` is derived as
`major * 10000 + minor * 100 + patch`. Override both without editing files:

```sh
./gradlew :app:assembleRelease -PversionName=1.4.1 -PversionCode=10401
```

CI derives the version name from the release tag, so a tag `v1.4.1` builds 1.4.1/10401.

## Signing

Release builds use `signing/community.jks` through `signing/keystore.properties`. This key is
public and only exists so sideload builds can upgrade each other. Do not use it for Google
Play.

To sign with your own key, override the values with environment variables (they take
precedence over the properties file):

```sh
export SIGNING_STORE_FILE=/absolute/path/my-release.jks
export SIGNING_STORE_PASSWORD=...
export SIGNING_KEY_ALIAS=...
export SIGNING_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Verify the result:

```sh
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-universal-release.apk
```

## Emulator

Any Android 10+ image works. The app needs a WebView, so use a Google APIs or Play image:

```sh
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n magazine-library -k "system-images;android-35;google_apis;arm64-v8a"
emulator -avd magazine-library
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

## Device instrumentation tests

The instrumentation runner exercises the real site on a physical device and expects a Chinese
device locale. It does not solve CAPTCHAs and should be run only against content you may access:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e mode catalog \
  com.magazinelibrary.app.test/com.magazinelibrary.app.DeviceTestRunner
```

## Troubleshooting

- **`SDK location not found`** — create `local.properties` or set `ANDROID_HOME`.
- **Unsupported class file major version** — Gradle is running on a JDK newer than 17; set
  `JAVA_HOME` to a JDK 17 installation.
- **`App not installed` after switching signing keys** — uninstall the previous copy first;
  Android refuses to replace an app signed by a different key.
- **Release APK is unsigned** — `signing/keystore.properties` is missing or the
  `SIGNING_STORE_FILE` path is wrong.
