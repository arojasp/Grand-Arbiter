# Rules bundle pipeline

Builds `app/src/main/assets/rules/bundle.json`, which the app imports into `rules.db` at startup
(only when the bundle's `bundleVersion` is higher than the installed one).

```
python tools/rules/fetch_sources.py      # network: official CR txt + MTR/IPG/JAR PDFs into rules/source/
python tools/rules/extract_pdf_docs.py   # PDFs -> rules/source/{mtr,ipg,jar}.txt   (pip install pdfplumber)
python tools/rules/build_bundle.py       # offline, stdlib: bundle + rules/build-report.md (bumps bundleVersion)
.\gradlew :app:testDebugUnitTest         # BundledRulesAssetTest = strict decode + validator (the gate)
```

The PDFs stay local (`rules/source/*.pdf` is gitignored): git keeps the extracted `.txt` files,
which are what the bundle is built from and what you review in diffs, plus `sources.json` with
each PDF's URL and SHA-256. To rebuild from scratch, run `fetch_sources.py` first.


| Document | Source | Notes |
|---|---|---|
| CR  | official TXT (`cr.txt`) | parsed by build_bundle.py |
| MTR | official PDF -> `mtr.txt` | headers checked against the PDF's table of contents; appendices + tables included |
| IPG | official PDF -> `ipg.txt` | same TOC check; penalties cross-checked, see below |
| JAR | official PDF -> `jar.txt` | no TOC: centred bold = section, left bold = issue |

Links: Academy Ruins is only a fallback for finding the official URLs (the WPN rules page comes
first). Its parsed data is not used: its CR keeps at most 3 examples per rule, and on 2026-10-07 its
MTR was a version behind and its /link/mtr returned 404.

## IPG penalties: three sources must agree

1. the penalty printed next to each infraction header in the PDF,
2. Appendix A (Penalty Quick Reference) in the same PDF,
3. `rules/source/ipg-infractions.json`, checked by a judge (`"verified": true`).

Any disagreement fails the build. Unverified entries are listed as warnings. When a new IPG is
published, fetch_sources.py says so: re-check every entry and set `verified` again.

## Before committing a new bundle

1. `git diff rules/source` to see what changed upstream (the .txt files are readable diffs).
2. Read `rules/build-report.md`: errors block the build; warnings need a human look.
3. Run the unit tests, then spot-check the changed sections in the app.
