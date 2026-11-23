# Pathblock

Pathblock blocks domains and URL paths in Chrome on Android. A rule for `example.com/news` blocks that path and its subpaths, and a rule for `example.com` blocks the whole site. Use the pause icon or long-press a rule to manage multiple rules.

## Install

1. Download the signed APK from [Releases](https://github.com/lukejcollins/pathblock/releases).
2. Open the APK in Android and follow the installation prompts.
3. Open Pathblock, tap the guard status, and enable **Pathblock for Chrome** in Android's Accessibility settings. If Android marks it as restricted, open Pathblock's **App info**, use the overflow menu to **Allow restricted settings** if available, then return to Accessibility.

## Updates

Open **Settings** in Pathblock to check for a new release. Background checks are enabled by default and run roughly daily. You can turn them off or manage notifications from Settings. When an update is available, choose **Download and install** and confirm in Android. Installing over your existing copy keeps your rules.

## Privacy

The Accessibility service reads Chrome's address bar to compare the current URL with rules stored on the device. Update checks fetch public release information from GitHub. Browsing URLs and rules are never sent to GitHub.

## Build

Install JDK 17 and Android SDK Platform 35, set `ANDROID_HOME`, and run:

```sh
./gradlew :app:assembleRelease :app:lintRelease
```

On Windows, use `gradlew.bat`. The build produces an unsigned release APK. Published APKs are built from their release commits and signed separately.
