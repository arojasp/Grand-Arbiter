#!/usr/bin/env python3
"""
Finds NEW sets' release notes on magic.wizards.com and adds them at the top of the index
(app/src/main/assets/rules/release-notes.json) with code, name and url. It doesn't build them:
build_release_notes.py (run next by update_all.py) downloads the article, writes <CODE>.json and
the sha.

Where it looks: Wizards' sitemap (https://magic.wizards.com/sitemap.xml), every
/en/news/feature/<slug>-release-notes page, with its lastmod date. A page is NEW when its URL is
neither in the index nor in tools/releasenotes/ignored.json (old sets we don't carry, or pages that
aren't a set's release notes).

The set: name from the article's title ("Magic: The Gathering® | Star Trek™ Release Notes" ->
"Star Trek"), code from Scryfall's set list (exact name match on a main set). If the code can't be
found for sure, nothing is added for that page and the run fails, naming it: add it by hand, or
put its URL in ignored.json.

Usage (on your PC or in GitHub Actions: Wizards and Scryfall must be reachable):
  python tools/releasenotes/discover_release_notes.py            add new sets to the index
  python tools/releasenotes/discover_release_notes.py --dry-run  only report
  python tools/releasenotes/discover_release_notes.py --seed     ONE TIME: every release notes page not
                                                                 in the index today goes to ignored.json
Exit code: 0 nothing new or all added, 1 something new couldn't be added (or Wizards/Scryfall unreachable).
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INDEX = ROOT / "app" / "src" / "main" / "assets" / "rules" / "release-notes.json"
IGNORED = Path(__file__).resolve().parent / "ignored.json"
SITEMAP = "https://magic.wizards.com/sitemap.xml"
SCRYFALL_SETS = "https://api.scryfall.com/sets"
USER_AGENT = "GrandArbiter/1.0 (release notes discovery; +https://github.com/arojasp/Grand-Arbiter)"

PAGE = re.compile(r"^https://magic\.wizards\.com/en/news/feature/[a-z0-9-]+-release-notes/?$")
# Scryfall set types that have their own release notes. Not tokens, promos, art series, memorabilia…
MAIN_SET_TYPES = {"expansion", "core", "masters", "draft_innovation", "commander", "funny", "starter", "eternal"}


# ---------------------------------------------------------------- pure helpers (no network)

def sitemap_pages(xml_text: str) -> list[tuple[str, str]]:
    """(url, lastmod) of every release notes page in a sitemap, newest first."""
    root = ET.fromstring(xml_text)
    out = []
    for url in root.iter():
        if not url.tag.endswith("}url") and url.tag != "url":
            continue
        loc = lastmod = ""
        for child in url:
            if child.tag.endswith("loc"):
                loc = (child.text or "").strip()
            elif child.tag.endswith("lastmod"):
                lastmod = (child.text or "").strip()
        if PAGE.match(loc):
            out.append((loc.rstrip("/"), lastmod))
    return sorted(out, key=lambda p: p[1], reverse=True)


def set_name_from_title(title: str) -> str:
    """'Magic: The Gathering® | Star Trek™ Release Notes' -> 'Star Trek'."""
    t = html.unescape(title)
    t = re.sub(r"[™®©]", "", t)
    t = re.sub(r"\s+", " ", t).strip()
    t = re.split(r"\s+\|\s+(?:Magic|MTG)\b", t)[0]                         # "… | Magic: The Gathering" suffix
    # "Magic: The Gathering | …", "Magic: The Gathering—…", "Magic: The Gathering Foundations" prefixes
    t = re.sub(r"^Magic: The Gathering\s*[|:—–-]?\s*", "", t, flags=re.I)
    t = re.sub(r"\s*Release Notes\s*$", "", t, flags=re.I)
    return t.strip(" -—–|:")


def normalize(name: str) -> str:
    n = re.sub(r"['’‘`]", "", name)                    # Marvel's / Marvel’s -> Marvels
    n = unicodedata.normalize("NFKD", n).encode("ascii", "ignore").decode().lower()
    n = n.replace("&", "and")
    n = re.sub(r"[^a-z0-9]+", " ", n)
    return re.sub(r"^the ", "", n.strip())


def match_set(name: str, scryfall_sets: list[dict]) -> dict | None:
    """The one main, paper set whose name is [name]; None if none or several."""
    wanted = normalize(name)
    hits = [s for s in scryfall_sets
            if normalize(s.get("name", "")) == wanted
            and s.get("set_type") in MAIN_SET_TYPES
            and not s.get("digital", False)]
    return hits[0] if len(hits) == 1 else None


def page_title(html_text: str) -> str | None:
    for pattern in (r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)',
                    r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:title',
                    r"<title[^>]*>(.*?)</title>",
                    r"<h1[^>]*>(.*?)</h1>"):
        m = re.search(pattern, html_text, re.I | re.S)
        if m:
            return re.sub(r"<[^>]+>", "", m.group(1)).strip()
    return None


# ---------------------------------------------------------------- network

def get(url: str, accept: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def load_json(path: Path, default):
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else default


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="Add new sets' release notes to the index.")
    ap.add_argument("--dry-run", action="store_true", help="report only, change nothing")
    ap.add_argument("--seed", action="store_true", help="put every release notes page not in the index into ignored.json")
    args = ap.parse_args()

    index = load_json(INDEX, None)
    if not IGNORED.exists() and not args.seed:
        # Without it every old set on Wizards' site (back to 2013) would look new.
        print(f"ERROR: {IGNORED.relative_to(ROOT)} doesn't exist. Run once with --seed first.")
        return 1
    ignored = load_json(IGNORED, {"comment": "", "urls": []})
    known = {s["url"].rstrip("/") for s in index["sets"]} | {u.rstrip("/") for u in ignored["urls"]}

    try:
        text = get(SITEMAP, "application/xml")
        if "<sitemapindex" in text:   # if Wizards ever splits it: read the English sitemaps
            children = re.findall(r"<loc>\s*(\S+?)\s*</loc>", text)
            text_pages = [sitemap_pages(get(c, "application/xml")) for c in children if "/en/" in c or c.endswith("/sitemap.xml")]
            pages = sorted({p for ps in text_pages for p in ps}, key=lambda p: p[1], reverse=True)
        else:
            pages = sitemap_pages(text)
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: couldn't read Wizards' sitemap ({e})")
        return 1
    new = [(url, lastmod) for url, lastmod in pages if url not in known]
    print(f"Sitemap: {len(pages)} release notes pages, {len(new)} not in the index or ignored.json")

    if args.seed:
        ignored["comment"] = ("Release notes pages on magic.wizards.com that discover_release_notes.py must not "
                              "propose: older sets than the app carries, or pages that aren't a set's release notes. "
                              "Add a URL here to stop it being proposed again.")
        ignored["urls"] = sorted({*ignored["urls"], *(u for u, _ in new)})
        if not args.dry_run:
            write_json(IGNORED, ignored)
        print(f"Seeded: {len(new)} page(s) added to {IGNORED.relative_to(ROOT)}")
        return 0
    if not new:
        print("No new release notes.")
        return 0

    try:
        sets = json.loads(get(SCRYFALL_SETS, "application/json"))["data"]
    except Exception as e:  # noqa: BLE001
        print(f"ERROR: couldn't read Scryfall's set list ({e})")
        return 1

    added, unresolved = [], []
    for url, lastmod in new:   # newest first
        try:
            title = page_title(get(url, "text/html")) or ""
        except Exception as e:  # noqa: BLE001
            unresolved.append(f"{url}: couldn't download it ({e})")
            continue
        name = set_name_from_title(title)
        match = match_set(name, sets) if name else None
        if match is None:
            unresolved.append(f"{url}: title {title!r} -> set {name!r}, no single Scryfall set with that name")
            continue
        added.append({"code": match["code"].upper(), "name": match["name"], "url": url})
        print(f"  NEW {match['code'].upper()} {match['name']}  ({url}, {lastmod[:10]})")

    if added and not args.dry_run:
        index["sets"] = added + index["sets"]     # newest first, like the index
        write_json(INDEX, index)
        print(f"Index: {len(added)} set(s) added at the top. build_release_notes.py builds them next.")
    for u in unresolved:
        print(f"ERROR: {u}. Add it to the index by hand, or its URL to {IGNORED.relative_to(ROOT)}.")
    return 1 if unresolved else 0


if __name__ == "__main__":
    sys.exit(main())
