# Play Store release notes

`whatsnew/whatsnew-<locale>`: the "What's new" text of the **next** release, one file per Play
locale (`en-US`, `es-ES`). Edit them on develop together with the versionCode/versionName bump.

- Plain text, at most **500 characters** each (Play's limit; CI checks it).
- `.github/workflows/release.yml` uploads them with the AAB to the internal track.
