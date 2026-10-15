# AnDB

A user-designed, offline database app for Android, in the spirit of
[Pilot-DB](https://pilot-db.sourceforge.net/) for PalmOS: design your own
databases (fields, types, list views), then browse, sort, filter and edit
records on the phone.

- No network access, no Google Play Services — intended to be F-Droid friendly.
- Each database is a plain SQLite file.
- Licensed under the GNU GPL v3 or later (see `LICENSE`).

See [`docs/design.md`](docs/design.md) for scope and design decisions.

## Building

Requires JDK 17+ and the Android SDK (platform 36, build-tools 36). Point
`local.properties` at the SDK (`sdk.dir=/path/to/Android/Sdk`), then:

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew check                # unit tests, Android Lint, ktlint, detekt
./gradlew ktlintFormat         # auto-fix formatting
./gradlew installDebug         # install on a connected device (adb)
```
