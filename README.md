# Grand Arbiter

An Android app for Magic: The Gathering judges: the Comprehensive Rules, MTR, IPG and JAR offline,
an Oracle card search, set release notes, and tournament tools (judge call timer, deck counter,
draft timers, head judge announcement, custom ban lists). Free, with no ads and no account.

[Get it on Google Play](https://play.google.com/store/apps/details?id=es.alejandro.grandarbiter) ·
[Privacy policy](PRIVACY.md)

## Building

- Android Studio (current stable) with JDK 21. `minSdk` 26.
- `./gradlew :app:assembleDebug`, or Run from Android Studio.
- Tests: `./gradlew :app:testDebugUnitTest`. Before a pull request, also
  `./gradlew :app:lintDebug :app:assembleRelease` (the release build runs R8).

Release builds are signed only in CI (`.github/workflows/release.yml`); a local release build
without `keystore.properties` is unsigned.

## Updating the rules, release notes and cards

The app's documents are built from the official sources by the Python scripts in `tools/`
(`python tools/update_all.py`); a GitHub workflow runs them every day. See
[`tools/README.md`](tools/README.md).

Installed apps download rules and release notes updates from the `main` branch, so this
repository is public and `main` is protected.

## Contributing

Issues are welcome: bugs, wrong rules text, ideas. For code, open an issue first so we can agree
on the change. Contributions are accepted under the same license as the project (GPL-3.0).

## License

GPL-3.0-or-later, with one additional term: the name "Grand Arbiter" and its icon are not licensed
for modified versions. Rules documents, card data and symbols belong to Wizards of the Coast and
Scryfall's data is used under its terms: none of that is covered. Details in
[`NOTICE.md`](NOTICE.md).

Grand Arbiter is unofficial Fan Content permitted under the Fan Content Policy. Not
approved/endorsed by Wizards. Portions of the materials used are property of Wizards of the
Coast. ©Wizards of the Coast LLC.
