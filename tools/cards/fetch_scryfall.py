#!/usr/bin/env python3
"""
Download Scryfall's bulk data (Oracle Cards + Rulings) into cards/source/ (git-ignored).

Scryfall requires a User-Agent and an Accept header on every API request, and asks clients to
prefer bulk data over many API calls. Bulk files are refreshed every 12 hours.

Usage: python tools/cards/fetch_scryfall.py
Then:  python tools/cards/build_cards.py
"""
from __future__ import annotations

import hashlib
import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "cards" / "source"
HEADERS = {
    "User-Agent": "GrandArbiter/1.0 (judge reference app; https://github.com/arojasp/Grand-Arbiter)",
    "Accept": "application/json;q=0.9,*/*;q=0.8",
}
WANTED = {"oracle_cards": "oracle-cards", "rulings": "rulings"}   # base names; extension depends on the format


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as r:
        return r.read()


def download(url: str, dest: Path) -> tuple[int, str]:
    """Stream to disk (these files are large), return (bytes, sha256)."""
    h, n = hashlib.sha256(), 0
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=120) as r, tmp.open("wb") as f:
        while chunk := r.read(1 << 20):
            f.write(chunk)
            h.update(chunk)
            n += len(chunk)
            print(f"\r  {dest.name}: {n / 1e6:6.1f} MB", end="", flush=True)
    tmp.replace(dest)
    print()
    return n, h.hexdigest()


# Since 2026 Scryfall serves bulk files as gzipped JSON Lines ("jsonl_download_uri", one object per
# line, ~25 MB for Oracle Cards). The older single JSON array ("download_uri") is kept as a fallback.
FORMATS = (("jsonl_download_uri", ".jsonl.gz"), ("download_uri", ".json"))


def download_url(entry: dict) -> tuple[str, str] | None:
    """(url, file extension) for the best format offered, looking in the entry and then its detail."""
    def pick(src: dict) -> tuple[str, str] | None:
        return next(((src[key], ext) for key, ext in FORMATS if src.get(key)), None)

    if (found := pick(entry)) or not entry.get("uri"):
        return found
    time.sleep(0.1)                             # only ask for the detail when the catalog lacks a link
    return pick(json.loads(get(entry["uri"])))


def main() -> int:
    SRC.mkdir(parents=True, exist_ok=True)
    catalog = json.loads(get("https://api.scryfall.com/bulk-data"))["data"]
    meta = {}
    for entry in catalog:
        name = WANTED.get(entry["type"])
        if not name:
            continue
        time.sleep(0.1)                         # Scryfall asks for 50-100 ms between requests
        found = download_url(entry)
        if not found:
            print(f"  {entry.get('type')}: no download link in the catalog entry. Fields received: "
                  f"{sorted(entry)}\n  entry: {json.dumps(entry)[:600]}", file=sys.stderr)
            return 1
        url, ext = found
        for old in SRC.glob(name + ".*"):          # don't leave a stale file in the other format
            if old.suffix != ".part":
                old.unlink()
        size, sha = download(url, SRC / (name + ext))
        meta[entry["type"]] = {"file": name + ext, "format": "jsonl.gz" if ext == ".jsonl.gz" else "json",
                               "updated_at": entry.get("updated_at"), "url": url, "bytes": size, "sha256": sha}
    missing = set(WANTED) - set(meta)
    if missing:
        print(f"Scryfall bulk catalog has no {missing}", file=sys.stderr)
        return 1
    (SRC / "scryfall-meta.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print(f"Done. Scryfall data from {meta['oracle_cards']['updated_at']}. Next: python tools/cards/build_cards.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
