#!/usr/bin/env python3
"""
Update every document the app ships, doing only what is needed, then run the tests.

  1. Rules (CR, MTR, IPG, JAR): download the official sources; rebuild the bundle ONLY if the
     extracted text changed (a rebuild bumps bundleVersion, and every installed app would download it).
  2. Release notes: add new sets found on Wizards' site (discover_release_notes.py), then
     re-download every set in the index; a set's JSON (and its SHA in the index) only changes when
     Wizards changed the article.
  3. Cards seed in the APK (--cards) and mana symbols (--symbols): opt-in. Scryfall's data changes
     every 12 hours and users already update cards from Settings, so refresh the seed before a
     release, not on every run.
  4. If anything changed: the unit tests (the real gate: bundle validator, release notes SHAs…).

Nothing is committed or published. At the end it says what changed and what to do next:
merging to main PUBLISHES the rules bundle and release notes to every installed app.

Usage (from anywhere in the repo, on your PC: Wizards and Scryfall must be reachable):
  python tools/update_all.py                 rules + release notes (+ tests if anything changed)
  python tools/update_all.py --cards         also the APK's card seed (before an app release)
  python tools/update_all.py --symbols       also the mana symbols (rare: new symbol in a set)
  python tools/update_all.py --no-tests      skip the Gradle tests
  python tools/update_all.py --only rules    just one pipeline: rules | releasenotes | cards | symbols
Exit code: 0 all good, 1 something failed (the report says what).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
RULES_SRC = ROOT / "rules" / "source"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
RN_INDEX = ASSETS / "rules" / "release-notes.json"
RN_DIR = ASSETS / "rules" / "releasenotes"
CARDS_SEED = ASSETS / "cards" / "seed.json"
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"

PDF_DOCS = ("mtr", "ipg", "jar")


# ---------------------------------------------------------------- helpers

class Report:
    def __init__(self) -> None:
        self.changed: list[str] = []      # what changed, for the summary
        self.publish = False              # rules / release notes changed: merge to main publishes them
        self.app_release = False          # cards / symbols changed: ship with the next app version
        self.warnings: list[str] = []
        self.failures: list[str] = []


def step(title: str) -> None:
    print(f"\n=== {title} " + "=" * max(0, 70 - len(title)))


def run(script: str, *args: str) -> bool:
    """Runs tools/<script> with this Python; its output goes straight to the console."""
    cmd = [sys.executable, str(TOOLS / script), *args]
    print(f"$ python tools/{script} {' '.join(args)}".rstrip())
    return subprocess.run(cmd, cwd=ROOT).returncode == 0


def sha(path: Path) -> str | None:
    """SHA-256 of a file (CRLF-insensitive for text), None if missing."""
    if not path.exists():
        return None
    data = path.read_bytes()
    if path.suffix in (".txt", ".json", ".xml", ".kt"):
        data = data.replace(b"\r\n", b"\n")
    return hashlib.sha256(data).hexdigest()


def snapshot(paths: list[Path]) -> dict[str, str | None]:
    """Hashes of these files, and of every file inside these folders."""
    out: dict[str, str | None] = {}
    for p in paths:
        files = sorted(f for f in p.rglob("*") if f.is_file()) if p.is_dir() else [p]
        for f in files:
            out[str(f.relative_to(ROOT))] = sha(f)
    return out


def diff(before: dict, after: dict) -> list[str]:
    return sorted(k for k in before.keys() | after.keys() if before.get(k) != after.get(k))


# ---------------------------------------------------------------- 1. rules

def update_rules(rep: Report) -> None:
    step("Rules documents (CR, MTR, IPG, JAR)")
    sources_json = RULES_SRC / "sources.json"
    # Bytes, not text: restoring it must not even change its line endings.
    old_sources_bytes = sources_json.read_bytes() if sources_json.exists() else None
    old = json.loads(old_sources_bytes) if old_sources_bytes else {}
    texts = [RULES_SRC / f"{d}.txt" for d in ("cr", *PDF_DOCS)] + [RULES_SRC / "ipg-infractions.json"]
    before_text = snapshot(texts)

    if not run("rules/fetch_sources.py"):
        rep.failures.append("rules: fetch_sources.py failed (see above)")
        return
    new = json.loads(sources_json.read_text(encoding="utf-8"))
    # fetch_sources.py reports a document it couldn't download but still exits 0, writing
    # sources.json without it. That is "not downloaded", never "changed": keep the previous record.
    missing = [d for d in ("cr", *PDF_DOCS) if d in old and d not in new]
    if missing:
        for d in missing:
            new[d] = old[d]
        sources_json.write_text(json.dumps(new, indent=2) + "\n", encoding="utf-8")
        rep.warnings.append(f"Couldn't download {', '.join(d.upper() for d in missing)} this time; "
                            "the installed version is kept. Re-run later (or see fetch_sources.py's message).")
    downloaded = [d for d in ("cr", *PDF_DOCS) if (new.get(d) or {}).get("sha256") != (old.get(d) or {}).get("sha256")]

    if not downloaded:
        # Same files as last time: keep sources.json byte-identical (fetchedAt alone is not a change).
        if old_sources_bytes is not None:
            sources_json.write_bytes(old_sources_bytes)
        print("Rules: the official files are the same as last time. Nothing to rebuild.")
        return

    print(f"Rules: new official files: {', '.join(d.upper() for d in downloaded)}")
    pdfs = [d for d in downloaded if d in PDF_DOCS]
    if pdfs and not run("rules/extract_pdf_docs.py", *pdfs):
        rep.failures.append(f"rules: extract_pdf_docs.py failed for {', '.join(pdfs)} (pip install pdfplumber?)")
        return
    if "ipg" in downloaded:
        rep.warnings.append("New IPG: re-check every entry of rules/source/ipg-infractions.json against the PDF "
                            "and set \"verified\": true again. The bundle build fails while they disagree.")

    text_changes = diff(before_text, snapshot(texts))
    if not text_changes:
        # A new PDF whose text is identical (metadata, re-export): no new bundle, no download for users.
        print("Rules: new files, but their text is identical. No rebuild (no bundleVersion bump).")
        rep.changed.append("rules/source/sources.json (new official files, same text)")
        return
    if not run("rules/build_bundle.py"):
        rep.failures.append("rules: build_bundle.py found errors (see rules/build-report.md); bundle NOT written")
        return
    rep.changed.append(f"rules bundle rebuilt: {', '.join(d.upper() for d in downloaded)} changed "
                       f"({', '.join(text_changes)})")
    rep.publish = True


# ---------------------------------------------------------------- 2. release notes

def update_release_notes(rep: Report) -> None:
    step("Release notes")
    before = snapshot([RN_INDEX, RN_DIR])
    codes_before = {e["code"] for e in json.loads(RN_INDEX.read_text(encoding="utf-8"))["sets"]}
    # New sets on Wizards' site: added at the top of the index, then built below with the others.
    if not run("releasenotes/discover_release_notes.py"):
        rep.failures.append("release notes: a new page couldn't be added to the index (see ERROR lines above): "
                            "add it by hand or to tools/releasenotes/ignored.json")
    new_sets = [e["code"] for e in json.loads(RN_INDEX.read_text(encoding="utf-8"))["sets"] if e["code"] not in codes_before]
    if new_sets:
        rep.changed.append(f"NEW release notes: {', '.join(new_sets)} (check code and name)")
    ok = run("releasenotes/build_release_notes.py", "--refresh")
    changes = diff(before, snapshot([RN_INDEX, RN_DIR]))
    if not ok:
        rep.failures.append("release notes: some sets were not built (see ERROR lines above); their previous "
                            "version is kept")
    sets = sorted(Path(c).stem for c in changes if c.startswith(str(RN_DIR.relative_to(ROOT))))
    if sets:
        rep.changed.append(f"release notes updated: {', '.join(sets)}")
        rep.publish = True
    elif changes:
        rep.changed.append("release notes index updated")
        rep.publish = True
    else:
        print("Release notes: no set changed.")


# ---------------------------------------------------------------- 3. cards seed / symbols (opt-in)

def scryfall_newer_than_seed() -> bool | None:
    """True if Scryfall's oracle cards are newer than the APK's seed; None if Scryfall can't be asked."""
    import urllib.request
    sys.path.insert(0, str(TOOLS / "cards"))
    try:
        from fetch_scryfall import HEADERS  # same User-Agent / Accept Scryfall requires
        req = urllib.request.Request("https://api.scryfall.com/bulk-data", headers=HEADERS)
        with urllib.request.urlopen(req, timeout=60) as r:
            catalog = json.loads(r.read())["data"]
        remote = next(e["updated_at"] for e in catalog if e["type"] == "oracle_cards")
        local = json.loads(CARDS_SEED.read_text(encoding="utf-8"))["oracleUpdatedAt"]
    except Exception as e:  # noqa: BLE001 — any failure: let the fetch script report it properly
        print(f"  (couldn't compare with Scryfall: {e})")
        return None
    print(f"  Scryfall oracle cards: {remote}   APK seed: {local}")
    return remote[:19] > local[:19]


def update_cards(rep: Report) -> None:
    step("Cards seed in the APK")
    newer = scryfall_newer_than_seed()
    if newer is False:
        print("Cards: the seed is already Scryfall's latest.")
        return
    time.sleep(0.1)
    if not run("cards/fetch_scryfall.py") or not run("cards/build_cards.py"):
        rep.failures.append("cards: fetch_scryfall.py / build_cards.py failed (see above)")
        return
    rep.changed.append("cards seed rebuilt (ships with the next app version)")
    rep.app_release = True


def update_symbols(rep: Report) -> None:
    step("Mana symbols")
    before = snapshot([DRAWABLES])
    if not run("cards/fetch_symbols.py") or not run("cards/build_symbols.py"):
        rep.failures.append("symbols: fetch_symbols.py / build_symbols.py failed (see above)")
        return
    changes = [c for c in diff(before, snapshot([DRAWABLES])) if Path(c).name.startswith("mana_")]
    if changes:
        rep.changed.append(f"mana symbols: {len(changes)} drawable(s) added or changed")
        rep.app_release = True
    else:
        print("Symbols: no new or changed symbol.")


# ---------------------------------------------------------------- 4. tests

def run_tests(rep: Report) -> None:
    step("Unit tests (the gate)")
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not gradlew.exists() or (os.name != "nt" and shutil.which("java") is None):
        rep.failures.append("tests: Gradle wrapper or Java not found; run .\\gradlew :app:testDebugUnitTest yourself")
        return
    print(f"$ {gradlew.name} :app:testDebugUnitTest")
    if subprocess.run([str(gradlew), ":app:testDebugUnitTest"], cwd=ROOT).returncode != 0:
        rep.failures.append("tests FAILED: do not commit or merge until they pass")


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="Update the app's documents, only what's needed.")
    ap.add_argument("--cards", action="store_true", help="also rebuild the APK's card seed if Scryfall is newer")
    ap.add_argument("--symbols", action="store_true", help="also refresh the mana symbols")
    ap.add_argument("--no-tests", action="store_true", help="don't run the Gradle unit tests")
    ap.add_argument("--only", choices=["rules", "releasenotes", "cards", "symbols"], help="run just one pipeline")
    args = ap.parse_args()

    dirty = subprocess.run(["git", "status", "--porcelain", "--", "rules/source", "app/src/main/assets"],
                           cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if dirty:
        print("Note: these files already had uncommitted changes (they'll be mixed with this update):\n" + dirty)

    rep = Report()
    wanted = {args.only} if args.only else {"rules", "releasenotes"} | ({"cards"} if args.cards else set()) \
        | ({"symbols"} if args.symbols else set())
    if "rules" in wanted:
        update_rules(rep)
    if "releasenotes" in wanted:
        update_release_notes(rep)
    if "cards" in wanted:
        update_cards(rep)
    if "symbols" in wanted:
        update_symbols(rep)
    if rep.changed and not args.no_tests:
        run_tests(rep)

    step("Summary")
    if rep.changed:
        print("\n".join(f"  changed: {c}" for c in rep.changed))
    elif rep.failures or rep.warnings:
        print("  Nothing changed, but not everything could be checked (see below).")
    else:
        print("  Nothing changed: everything is up to date.")
    for w in rep.warnings:
        print(f"  WARNING: {w}")
    for f in rep.failures:
        print(f"  FAILED: {f}")
    if rep.changed and not rep.failures:
        print("\nNext:")
        print("  1. Review: git diff --stat   (rules: also git diff rules/source)")
        print("  2. Commit on develop.")
        if rep.publish:
            print("  3. Merge develop -> main: that PUBLISHES the new rules / release notes to every installed app.")
        if rep.app_release:
            print("  -  Cards seed / symbols reach users with the next app version (bump versionCode).")
    return 1 if rep.failures else 0


if __name__ == "__main__":
    sys.exit(main())
