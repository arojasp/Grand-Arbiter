# Updating the app's documents

Everything here runs **on your PC** (Wizards and Scryfall must be reachable) with Python 3.10+.
Only `extract_pdf_docs.py` needs a package: `pip install pdfplumber`.

## One command

```
python tools/update_all.py
```

It updates what changed, and only that:

1. **Rules** (CR, MTR, IPG, JAR): downloads the official sources. If the files are the same as last
   time, it stops there (no changes in git). If they're new, it extracts the PDFs and rebuilds the
   bundle, but only when the *text* changed: every rebuild bumps `bundleVersion`, and every
   installed app would download it.
2. **Release notes**: re-downloads every set listed in `app/src/main/assets/rules/release-notes.json`.
   A set's JSON (and its SHA in the index) only changes when Wizards revised the article.
3. **Tests**: if anything changed, `gradlew :app:testDebugUnitTest`. This is the real gate: the rules
   bundle validator, the IPG penalties check, the release notes SHAs…

At the end it prints what changed, any warning (for example "new IPG: re-verify the penalties") and
what to do next. It never commits or publishes anything.

| Option | When |
|---|---|
| `--cards` | Before an app release: rebuilds the card seed in the APK if Scryfall has newer data. Users already update cards from Settings, so this isn't needed on every run (Scryfall changes every 12 h and the seed is several MB in git). |
| `--symbols` | When a set introduces a new mana/card symbol (rare). |
| `--only rules` / `releasenotes` / `cards` / `symbols` | Just one pipeline. |
| `--no-tests` | Skip Gradle (e.g. you'll run the tests from Android Studio). |

Exit code `0` = all good, `1` = something failed: the summary says what, and whatever failed keeps its
previous version (a failed download or build never overwrites good data).

## Every day, automatically

`.github/workflows/update-documents.yml` runs `update_all.py` (rules + release notes) on GitHub every
morning. If something changed and the tests pass, it opens a pull request against `develop` (branch
`auto/update-documents`) with the summary; if a step fails, the run fails and GitHub emails you.
It never publishes: you still review, merge into `develop`, then `develop` → `main`.
Also runnable by hand from the repo's Actions tab ("Run workflow").

## After it runs

1. Review: `git diff --stat` (for rules, also `git diff rules/source`: the `.txt` files are readable
   diffs of what changed upstream).
2. Commit on `develop`.
3. **Merge `develop` → `main` = publishing.** Installed apps download the new rules bundle and release
   notes from `main` on their next launch, without an app update. Never merge before the tests pass.

Cards seed and symbols are different: they reach users with the **next app version** (bump `versionCode`).

## Things the script can't do for you

- **A new set's release notes**: add a line at the **top** of
  `app/src/main/assets/rules/release-notes.json` (`code`, `name`, official URL on
  magic.wizards.com), then run `update_all.py`. Sets aren't discovered automatically.
- **A new IPG**: re-check every entry of `rules/source/ipg-infractions.json` against the PDF and set
  `"verified": true` again. The build fails while the PDF and that file disagree (on purpose).
- **Forcing an app update** (rare). The order matters:
  1. the new version is live on Play at 100%;
  2. `python tools/rules/build_bundle.py --manifest-only --min-version-code <versionCode>`;
  3. merge to `main`.

## The individual scripts

`update_all.py` just runs these in order. Use them directly to debug one step.

| Pipeline | Scripts | Output | Reaches users via |
|---|---|---|---|
| Rules | `rules/fetch_sources.py` → `rules/extract_pdf_docs.py` → `rules/build_bundle.py` | `assets/rules/bundle.json` + `manifest.json` | merge to `main` |
| Release notes | `releasenotes/build_release_notes.py [--download\|--refresh] [--only CODE]` | `assets/rules/releasenotes/<CODE>.json` + index | merge to `main` |
| Cards seed | `cards/fetch_scryfall.py` → `cards/build_cards.py` | `assets/cards/*.jsonl.gz` + `seed.json` | next app version |
| Symbols | `cards/fetch_symbols.py` → `cards/build_symbols.py` | `res/drawable/mana_*.xml` + `ManaSymbolDrawables.kt` | next app version |
| Branding | `branding/build_icons.py [--png]` | launcher icon, Play icon | next app version |

Details of the rules pipeline (sources, the IPG three-way penalty check): [`rules/README.md`](rules/README.md).

Downloaded inputs (`rules/source/*.pdf`, `cards/source/`, `tools/releasenotes/source/`) are
gitignored: Wizards' and Scryfall's files are never committed, only what's built from them.
