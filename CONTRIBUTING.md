# Contributing to Resona

English · [Русский](CONTRIBUTING.ru.md)

Hey, thanks for wanting to help! Resona is mostly a one person project, so any help really counts, whether it's a bug report, a translation or a pull request.

## Found a bug?

Tell us in [Telegram](https://t.me/resona_tg) or open an [issue](https://github.com/savo-o/resona/issues). The more of this you include, the faster it gets fixed:

- app version and whether it's a release or a canary build
- Android or iOS, and your device
- what you did, what you expected, what happened instead
- a link to the track or playlist, if the problem is with a specific one

If you're on a canary or debug build, the debug menu has logs you can copy and attach.

A lot of bugs are actually SoundCloud changing something on their side. That's fine, report them anyway.

## Translations

Translations live in a separate place: [savo-o.github.io/resona-status/translations](https://savo-o.github.io/resona-status/translations/). You don't need to build the app or know Kotlin for this.

Only edit the translation files themselves. The template is synced automatically from the latest release, so changes to it get overwritten.

You can also test your translation right away in the app: Settings, then Language, then Custom. Save the template, translate it and load your `strings.xml` back.

## Code

### Setup

You'll need Android Studio (or just the command line) and **JDK 17**. Newer JDKs won't work with this Gradle setup.

Create `local.properties` in the repo root:

```properties
sdk.dir=/path/to/your/Android/Sdk

# optional, only needed for the Telegram import
TELEGRAM_API_ID=
TELEGRAM_API_HASH=
```

Telegram keys can be taken from [my.telegram.org/apps](https://my.telegram.org/apps). Without them the app still builds, the Telegram import just won't work. You don't need the release signing passwords for anything except release builds.

Then:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug build installs next to the regular app (it has a `.debug` suffix), so you won't lose your library.

iOS needs macOS with Xcode: open `ios/iosApp/iosApp.xcodeproj` and run the `iosApp` scheme. If you don't have a Mac, that's fine, CI builds the IPA on every push.

Before you dive in, have a look at [ARCHITECTURE.md](ARCHITECTURE.md). It's short and it explains the weird parts.

### Before you start

For small fixes, just send a PR. For anything bigger, a new screen or a new setting, please ask in Telegram or in an issue first. It would be sad if you spent a weekend on something that doesn't fit.

Features land on Android first. You don't have to port your change to iOS, that's totally optional.

### Things that won't be merged

- anything that decrypts SoundCloud's DRM protected streams
- telemetry, analytics, ads, paywalls
- mass actions against SoundCloud (likes, follows, comments) without delays. They get accounts flagged as spam

### Style

- Match the code around you. If the app already has a component for something (sheets, rows, banners, switches), use it instead of a stock Material one.
- Haptics only for actions that mean something (like, skip, seek). Not for every tap.
- New strings go into `values/strings.xml` and, if you can, `values-ru/strings.xml`. In Russian we use formal "вы" and plain wording.
- Commits: small and focused, lowercase, with an area in front if it helps, like `lyrics: retry lrclib requests`. One commit for one thing makes bugs way easier to track down later.

### AI tools

Using AI to write code is totally fine, I use it too. But you're responsible for what you send, not the AI:

- **run it on a real device or an emulator** and check that the thing actually works, including the edge cases (no internet, empty library, a DRM track)
- read the diff yourself and make sure you understand every part of it
- if the AI wrote something you can't explain, it doesn't go in

PRs that clearly weren't run even once will be closed, sorry.

### Database changes

If you touch a Room entity:

1. bump the version in `AppDatabase`
2. write a migration (no destructive fallback, people's libraries live there)
3. commit the new schema JSON from `app/schemas/`
4. add a case to `AppDatabaseMigrationTest`

### Pull requests

- say what you changed and why, and how you tested it (which device or emulator, which Android or iOS version)
- screenshots or a short video for anything visual
- make sure `./gradlew testDebugUnitTest` passes

Every push to `main` builds a canary, so your change will be in people's hands pretty quickly after merging.

## License

Resona is GPLv3. By sending a PR you agree that your code is released under the same license.
