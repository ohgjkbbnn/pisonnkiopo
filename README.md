<p align="center">
  <img src="assets/logo-git.png" width="128" alt="Resona">
</p>

<h1 align="center">Resona</h1>

<p align="center">
  English · <a href="README.ru.md">Русский</a>
</p>

<p align="center">
  An open source SoundCloud client for Android, with an iOS version in beta.<br>
  No ads. No account required.
  <a href="https://keepandroidopen.org" target="_blank" rel="noopener noreferrer">
    Resona supports #KeepAndroidOpen
</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26+-3DDC84?logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/iOS-16+-000000?logo=apple&logoColor=white">
  <img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white">
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg">
</p>

## Screenshots

<p align="center">
  <img src="screenshots/eng_main.png" width="220">
  <img src="screenshots/player_both.png" width="220">
  <img src="screenshots/eng_customization.png" width="220">
</p>

## Install

Grab the latest APK from [GitHub Releases](https://github.com/savo-o/resona/releases/latest). Resona isn't on Google Play, so you'll need to allow installs from your file manager or browser.

You can also track updates automatically with [Obtainium](https://github.com/ImranR98/Obtainium), just point it at this repo.

### iOS version (beta)

Grab the IPA from [GitHub Releases](https://github.com/savo-o/resona/releases/latest) (or a fresher build from [canary](https://github.com/savo-o/resona/releases/tag/canary)) and sideload it with [Sideloadly](https://sideloadly.io) (recommended), [AltStore](https://altstore.io) or [SideStore](https://sidestore.io). The IPA is unsigned and Resona will never be on the App Store. With a free Apple ID the signature lasts 7 days, so it has to be refreshed weekly (all three can do it automatically).

New to sideloading? Follow the [step-by-step install guide](https://savo-o.github.io/resona/ios.html).

The iOS version is a separate port of the Android app, updated less often, so its version can lag behind (e.g. `Resona-ios-1.7.ipa` attached to a 1.9 release). It gets fixes when SoundCloud breaks something, but new features land on Android first and some may never make it to iOS.

## Features

- **Open source, no ads, no telemetry.**
- No account required, log in only if you want to sync your own likes
- Offline downloads, plus a folder watcher that picks up local files automatically
- Import tracks straight from Telegram chats
- Mix that keeps discovering new stuff from artists you already like
- Lyrics for pretty much anything, even niche tracks, with a few sources chained as fallback and synced timing where it's available
- Listening stats
- Custom color themes and app icons, dynamic color that adapts to the track's artwork

## Build

Debug:

```bash
./gradlew assembleDebug
```

iOS (macOS with Xcode required): open `ios/iosApp/iosApp.xcodeproj` and run the `iosApp` scheme.

## Philosophy

Resona will always be free and open source. No subscriptions, no paywalls, no telemetry, no analytics - ever.

## Community

Questions, bugs, ideas: [t.me/resona_tg](https://t.me/resona_tg)

## Star History

<a href="https://www.star-history.com/?repos=savo-o%2Fresona&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=savo-o/resona&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=savo-o/resona&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=savo-o/resona&type=date&legend=top-left" />
 </picture>
</a>

## License

This project is licensed under the GNU General Public License v3.0.
See the `LICENSE` file for details.
