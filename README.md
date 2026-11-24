<div align="center">
  <img src="docs/pathblock.svg" width="96" height="96" alt="Pathblock shield logo">
  <h1>Pathblock</h1>
  <p>Block distracting sites and specific URL paths in Chrome for Android.</p>
  <a href="https://github.com/lukejcollins/pathblock/releases/latest">
    <img src="https://img.shields.io/github/v/release/lukejcollins/pathblock?label=Download%20APK&color=6e59a5" alt="Download the latest APK">
  </a>
  <a href="https://github.com/lukejcollins/pathblock/actions/workflows/android.yml">
    <img src="https://github.com/lukejcollins/pathblock/actions/workflows/android.yml/badge.svg" alt="Android CI">
  </a>
</div>

## What you can block

| Add this rule | Pathblock blocks |
| --- | --- |
| `example.com` | The whole site and its subdomains |
| `reddit.com/r/unitedkingdom` | That subreddit and its subpaths while leaving other subreddits accessible |

Rules are case-insensitive. Pause a rule for 5, 10, or 20 minutes after a brief countdown. Long-press a rule to pause, resume, or remove several rules together.

## Install

1. Download the signed APK from the [latest release](https://github.com/lukejcollins/pathblock/releases/latest) and open it on an Android 11 or newer device.
2. If prompted, allow your browser or file manager to install the APK.
3. Open Pathblock, tap the guard status, and enable **Pathblock for Chrome** in Android Accessibility settings. If Android labels it a restricted setting, go to Pathblock's **App info**, open the overflow menu, and choose **Allow restricted settings** if available. Then return to Accessibility.

## Update from your phone

Open **Settings** in Pathblock to check immediately. Background checks are on by default and run about once a day. Enable update notifications in Settings if you want an alert.

Tap **Download and install** when a new version is available and confirm through Android. The first time, Android may ask you to allow installations from Pathblock. An in-place update keeps your rules.

## Privacy

Pathblock's Accessibility service reads Chrome's address bar to compare it with rules stored on your phone. Update checks contact GitHub for release information. Pathblock keeps your browsing URLs and rules on the device.

## Build and test

Use JDK 17 and Android SDK Platform 35 with Build Tools 36.0.0.

```sh
./gradlew :app:assembleRelease :app:lintRelease
```

On Windows, run `gradlew.bat`. The build produces an unsigned APK. GitHub Actions runs the build, lint, rule-matching checks, and Android emulator smoke tests. Release APKs are signed separately.
