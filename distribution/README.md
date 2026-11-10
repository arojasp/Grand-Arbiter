# Play Store release notes

`whatsnew/whatsnew-en-US`: the "What's new" text of the **next** release. The app is English only,
so there is one file; Play shows it for every language. Edit it on develop together with the
versionCode/versionName bump.

- Plain text, at most **500 characters** each (Play's limit; CI checks it).
- `.github/workflows/release.yml` uploads them with the AAB to the internal track.
