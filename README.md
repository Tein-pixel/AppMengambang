# Floating Apps for Android

A Kotlin Android app for Android 10+ that lists installed applications and provides movable overlay controls with an optional attempt to launch the selected app in a native freeform task when the device supports it.

## Features

- Lists installed apps with their names and icons.
- Searches the installed-app list.
- Checks the Android overlay permission and links to its system settings.
- Starts a foreground service for overlay-window controls.
- Supports moving, resizing, minimizing, maximizing, and closing the app's control window.
- Attempts to open the selected application as a separate Android task; it does not embed another app's Activity inside its own view.

## Requirements and compatibility

- Minimum SDK: Android 6.0 (API 23).
- Target SDK: Android 10 (API 29).
- Compile SDK: Android 15 (API 35).
- Java/Kotlin JVM target: 17.
- Android Studio with a compatible Android Gradle Plugin and Kotlin plugin.

The project can be built on Android 10 and newer, but freeform window behavior depends on the device manufacturer and system configuration.

## First launch

1. Install and open the app.
2. Grant **Display over other apps** when prompted.
3. Return to the app and select an installed application.
4. Use the floating control panel to launch the selected app or manage the overlay.
5. Close individual windows when finished, or use the app's stop controls to end the overlay service.

Android may block overlay windows on some screens or under certain system policies. Granting the permission does not guarantee that every application can run in freeform mode.

## Important Android platform limitation

Android does not expose a public API that lets a third-party app place an arbitrary Activity from another package inside its own `WindowManager` view. This project therefore displays its own overlay controls and launches the target app as a separate task.

`ActivityOptions.setLaunchBounds()` is only a request. Android or the device manufacturer may ignore the bounds when freeform windowing is unsupported or disabled. In that case, the target app opens normally instead of appearing in a resizable window.

## Build with GitHub Actions

The repository includes `.github/workflows/android.yml`.

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Choose **Run workflow** for a manual build, or push to `main`.
4. Open the completed run.
5. Download the **FloatingApps-debug-apk** artifact.

A push to `main` also publishes a GitHub Release containing the generated debug APK.

### CI toolchain

The workflow currently uses:

- Ubuntu GitHub-hosted runner.
- Eclipse Temurin Java 17.
- Android SDK platform 35.
- Android Build Tools 35.0.0.
- Gradle 8.9.
- Gradle tasks: `clean assembleDebug`.

The artifact is retained for 30 days.

## Permissions

The manifest declares:

- `SYSTEM_ALERT_WINDOW`: display the app's overlay controls.
- `FOREGROUND_SERVICE`: keep the overlay service running while active.
- `QUERY_ALL_PACKAGES`: enumerate installed applications for the app picker.

The installed-app list is a core feature. Do not remove package visibility without replacing it with a narrower, tested discovery approach.

## Troubleshooting

**The overlay does not appear**  
Open Android Settings and confirm that Display over other apps is enabled for this application. Some system screens and vendor policies can still block overlays.

**The selected app opens full-screen**  
This is an Android/device limitation. The app cannot force freeform mode on devices that do not support or enable it.

**The app list is empty or incomplete**  
Check package visibility behavior on the Android version and device. The app relies on the declared package-query permission.

**The APK build fails**  
Inspect the first error in the GitHub Actions log, especially SDK installation and Gradle compilation steps.

## Development notes

Keep UI/service changes compatible with the documented Android API range. Test overlay permission flows, service shutdown, and freeform fallback on physical devices because emulator behavior may differ from OEM Android builds.
