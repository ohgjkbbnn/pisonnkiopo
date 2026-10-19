# How Resona works

English · [Русский](ARCHITECTURE.ru.md)

A quick map of the code, so you know where to look. It's not a full reference, just enough to find your way around and not step on the weird parts.

## The big picture

```
app/          Android app (Kotlin, Jetpack Compose)
ios/          iOS port (Kotlin Multiplatform + Compose Multiplatform)
docs/         the website on GitHub Pages
fastlane/     app description and screenshots in fastlane format
.github/      canary CI
```

Android and iOS are **two separate codebases**. The iOS port started as a copy of the Android code and lives its own life now: it gets fixes when SoundCloud breaks something, but new features usually show up on Android first. So when you fix a SoundCloud related bug, it probably exists in both places.

## Android app

Everything lives in `app/src/main/java/com/savoo/scclient/`. The usual Android stack: Compose for UI, Hilt for DI, Room for the database, Retrofit + OkHttp for the network, Media3 for playback.

Data flows the usual way:

```
screen (Compose)  ->  ViewModel  ->  repository  ->  SoundCloud API / Room / DataStore
```

### Folders

| Folder | What's inside |
|---|---|
| `ui/screens/` | one folder per screen: home, search, player, favorites, settings, recap and so on |
| `ui/components/` | shared building blocks: track rows, sheets, banners, undo. Check here before making a new one |
| `ui/navigation/` | root screen, bottom dock, deep links, SoundCloud link parsing |
| `ui/theme/`, `ui/haptics/` | colors, fonts, themes, haptic patterns |
| `data/remote/` | everything that talks to the internet |
| `data/repository/` | the logic: tracks, favorites, lyrics, playlists, stats, settings |
| `data/local/` | Room DAOs, the database and secure prefs |
| `data/model/` | data classes |
| `player/` | playback service, player controller, offline downloads, sound effects |
| `auth/` | login and tokens |
| `security/` | app lock, biometrics, duress wipe |
| `telegram/` | Telegram import through TDLib |
| `i18n/` | custom translations loaded from a user's `strings.xml` |
| `work/` | background jobs (scheduled favorites export) |
| `debug/` | logs, crash reporter, screenshot mode |

### Talking to SoundCloud

SoundCloud has no public API for apps like this, so Resona uses the same `api-v2` the website uses. That's the most fragile part of the app and it breaks from time to time.

**client_id.** Every request needs it. `ClientIdProvider` grabs it from soundcloud.com's JavaScript bundles and caches it. When SoundCloud rotates it, the app fetches a fresh one (at most once every 5 minutes, so we don't hammer the site).

**Three kinds of HTTP clients** (see `di/NetworkModule.kt`):

- the main one for `api-v2`, with `AuthInterceptor` that adds the client_id and token and catches DataDome captchas
- a plain one for LRCLIB, Genius, GitHub releases and anything else that isn't SoundCloud
- `WebViewApiBridge`, a hidden WebView that runs `fetch()` for likes, comments and profile edits

The WebView bit is not a hack for fun. SoundCloud protects those endpoints with DataDome, and DataDome checks the TLS fingerprint of the connection. OkHttp looks nothing like a browser, a WebView does. Cookies alone don't help.

**DataDome captcha.** When it shows up, `DataDomeChallenge` opens a sheet where the user solves it, and the request is retried.

**Go slow with bulk actions.** Liking hundreds of tracks quickly gets the account a 24 hour spam warning (`429 spam_warning`) and a captcha. Anything that does a lot of writes to SoundCloud has to pace itself.

### Playing a track

1. A track comes with a list of `transcodings`, the different stream formats.
2. `UnavailableTrack.kt` decides if it's playable. **If any transcoding is encrypted, the track is unplayable.** The plain mp3 entries next to it look fine but always return 404, don't fall for them. Resona doesn't decrypt DRM and won't.
3. `TrackRepository.resolvePlayableStream()` tries progressive mp3 first, then HLS.
4. `PlayerController` and `PlaybackService` (Media3) actually play it, handle the queue and the notification.

Offline downloads go through `OfflineTrackManager`, one track at a time, with the same DRM rules.

### Lyrics

`LyricsRepository` goes through sources in order:

1. local cache in Room
2. LRCLIB (synced lyrics, retried a few times if the request fails)
3. Genius as a fallback (plain text, no timing)

If the SoundCloud version of a song is longer or shorter than the one on LRCLIB (a sped up edit, a remix), the timing gets stretched by the duration ratio automatically. Users can also tweak offset and stretch per track. When lyrics drift, it's almost always that and not a player bug.

Genius annotations come from its `referents` API. Genius answers 403 unless the request looks like it came from a phone browser, that's why its client sends a browser User-Agent.

### Storage

- **Room** (`AppDatabase`): favorites, playlists, offline tracks, play history, lyrics cache, Telegram imports and more. Every version has a migration and a schema JSON in `app/schemas/`, and migrations are covered by `AppDatabaseMigrationTest`.
- **DataStore** (`SettingsRepository`): all the settings.
- **Encrypted prefs** (`SecurePrefs`): tokens and other secrets.

### Builds

| Build | App id | What for |
|---|---|---|
| debug | `com.savoo.scclient.debug` | development |
| canary | `com.savoo.scclient.canary` | built by CI on every push to `main` |
| release | `com.savoo.scclient` | GitHub releases |

All three install side by side. APKs are split per ABI because TDLib's native libraries are big.

CI (`.github/workflows/canary.yml`) runs unit tests, builds canary APKs and an unsigned IPA, and publishes them together as the `canary` pre-release.

## iOS

```
ios/shared/src/commonMain/   most of the code, same folders as on Android
ios/shared/src/iosMain/      the iOS specific bits
ios/iosApp/                  a tiny Swift entry point
```

The trick that made the port cheap is `compat/`: small stand-ins for Android APIs (OkHttp, Media3, SharedPreferences, WebView, Context and friends) written on top of multiplatform libraries. Thanks to them most of the Android code moved over almost unchanged.

`iosMain` is where things are actually different: the audio player on AVFoundation, the sound effects, biometrics, haptics, file pickers.

The IPA is unsigned and is sideloaded by users. See the [install guide](https://savo-o.github.io/resona/ios.html).

## Translations

English strings live in `app/src/main/res/values/strings.xml`, Russian in `values-ru`. Community translations are handled in a separate repo, [resona-status](https://savo-o.github.io/resona-status/translations/), which picks up the template from the latest release automatically. Users load them through Custom language in the app, nothing gets compiled in.

## When something breaks

Most of the time it's SoundCloud, not the app. Good places to start:

- nothing loads at all: client_id (`ClientIdProvider`), try a forced refresh in settings
- likes or comments fail: DataDome (`WebViewApiBridge`, `DataDomeChallenge`)
- a track won't play: transcodings (`UnavailableTrack.kt`, `TrackRepository`)
- lyrics are off: duration mismatch with LRCLIB (`LyricsRepository`)

The debug menu in debug and canary builds has logs and a few tools that help here.
