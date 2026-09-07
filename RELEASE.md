# Building and publishing Nocturne Earth

## Prerequisites

- Android Studio Meerkat (2024.3.1) or newer (or Android SDK Platform 36 + Build Tools 36.0.0)
- JDK 17
- An Android 10 / API 29 or newer device for testing

Open the repository root in Android Studio and let it install the Gradle and Android SDK
components. Then use **Build → Generate App Bundles or APKs** or run:

```bash
./gradlew assembleDebug assembleRelease bundleRelease
```

Output paths:

| Deliverable | Path |
| --- | --- |
| Installable test build | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK (unsigned without a key) | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Play Store upload bundle (unsigned without a key) | `app/build/outputs/bundle/release/app-release.aab` |

## Signing a Play Store release

Do **not** commit a signing key. Create and store an upload keystore securely, then set these
variables in your secure terminal or CI secret environment before running the release build:

```bash
export RELEASE_STORE_FILE=/absolute/path/to/your-upload-key.jks
export RELEASE_STORE_PASSWORD='your-store-password'
export RELEASE_KEY_ALIAS='your-key-alias'
export RELEASE_KEY_PASSWORD='your-key-password'
./gradlew clean assembleRelease bundleRelease
```

With all four values present, the Gradle project signs `app-release.apk` and `app-release.aab`.
Use the signed `.aab` in Google Play Console, opt into Play App Signing, and keep the upload key
private.

## Before uploading

1. Change `applicationId` and `namespace` in `app/build.gradle` if you need your own unique
   Play package ID before first publication.
2. Update `versionCode` for every Play submission and update `versionName` for the user-facing
   release label.
3. Test rotation, dark/system navigation layouts, pinch/drag performance, city tapping, no-network
   behavior, and the location permission flow on a physical Android 10+ device.
4. Keep the texture credit from `ASSET_ATTRIBUTION.md` in the app / listing, and review
   Open-Meteo's current terms for your projected traffic volume.
5. Prepare Play Console privacy disclosures: the optional coarse location permission is requested
   only when the user presses **My location**; weather coordinates are sent to Open-Meteo only for
   a selected place.

## Optional GitHub Actions build

`ci/android-build.yml.template` is a ready-to-run workflow that uploads the debug APK, unsigned
release APK, and unsigned AAB as a 30-day artifact. To enable it in a repository where your GitHub
connection has **Workflows: write** permission, copy it to `.github/workflows/android-build.yml`
and push that change. It is kept as a template here because this sandbox's GitHub App token cannot
create workflow files.
